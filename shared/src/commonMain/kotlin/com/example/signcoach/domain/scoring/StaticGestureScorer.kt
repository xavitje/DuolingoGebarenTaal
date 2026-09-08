package com.example.signcoach.domain.scoring

import com.example.signcoach.data.model.FeedbackResult
import com.example.signcoach.data.model.HandPose
import com.example.signcoach.data.model.LandmarkPoint
import com.example.signcoach.data.model.RuleType
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.domain.normalize.LandmarkNormalizer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Vergelijkt live landmarks met de eerste frame van een template.
 *
 * Uitgebreid t.o.v. de MVP-versie met twee non-manual/tweehandige signalen:
 * - een tweede hand wordt meegewogen als het template er een verwacht (TWO_HAND_PRESENCE-regel
 *   en/of secondaryHandLandmarks op de template-frame);
 * - FACE_BLENDSHAPE-regels vergelijken een blendshape-score (bv. mondbeeld) met een streefwaarde.
 *
 * De gewichten (zie companion object) zijn net als de rest van de template-kalibratie
 * MVP-startwaarden: bedoeld om aan te passen zodra er echte trainings-/testdata is
 * (zie PLAN.md fase 2).
 */
class StaticGestureScorer(
    private val maxDistance: Float = DEFAULT_MAX_DISTANCE,
    private val correctThreshold: Float = DEFAULT_CORRECT_THRESHOLD,
    private val nonManualWeight: Float = DEFAULT_NON_MANUAL_WEIGHT,
) : GestureScorer {

    /** Resultaat van het vergelijken van de live pose met één referentievariant. */
    private data class CandidateResult(
        val score: Float,
        val primaryDistance: Float,
        val secondaryDistance: Float?,
        val faceScore: Float?,
    )

    override fun score(input: List<HandPose>, template: SignTemplate): FeedbackResult {
        val requiredFrames = template.rules
            .firstOrNull { it.type == RuleType.MIN_STABLE_FRAMES }
            ?.targetValue
            ?.toInt()
            ?.coerceAtLeast(1)
            ?: 1
        if (input.size < requiredFrames) {
            return FeedbackResult(
                score = 0f,
                isCorrect = false,
                feedbackMessages = listOf("Houd de handvorm nog even stabiel."),
            )
        }

        val actualFrames = input.takeLast(requiredFrames)
        val actualPrimaryNormalized = actualFrames.mapNotNull { LandmarkNormalizer.normalize(it) }
        if (actualPrimaryNormalized.size != requiredFrames) return invalidPoseFeedback()

        // Alle geldige referenties voor dit gebaar: de hoofdreferentie plus eventuele
        // extra varianten (andere hand, andere hoek, andere persoon). We scoren tegen
        // elk van deze en gebruiken de best passende — zo hoeft een gebruiker niet
        // exact op dezelfde manier te gebaren als de ene keer dat de template is
        // opgenomen.
        val candidates = listOf(template.frames.firstOrNull() ?: return invalidPoseFeedback()) + template.referenceVariants

        val requiresTwoHands = template.rules.any { it.type == RuleType.TWO_HAND_PRESENCE } ||
            candidates.any { it.secondaryHandLandmarks != null }
        if (requiresTwoHands && actualFrames.any { it.secondaryHandLandmarks == null }) {
            return FeedbackResult(
                score = 0f,
                isCorrect = false,
                feedbackMessages = listOf("Gebruik twee handen voor dit gebaar."),
            )
        }

        val results = candidates.mapNotNull { candidate ->
            scoreAgainst(candidate, actualFrames, actualPrimaryNormalized, template)
        }
        val best = results.maxByOrNull { it.score } ?: return invalidPoseFeedback()
        val isCorrect = best.score >= correctThreshold

        return FeedbackResult(
            score = best.score,
            isCorrect = isCorrect,
            feedbackMessages = when {
                isCorrect -> listOf("Goed gedaan! Houd deze positie even vast.")
                requiresTwoHands && best.secondaryDistance != null && best.secondaryDistance > best.primaryDistance * TWO_HAND_IMBALANCE_FACTOR ->
                    listOf("Let op de positie van je andere hand.")
                best.faceScore != null && best.faceScore < FACE_FEEDBACK_THRESHOLD -> listOf("Let ook op je mondbeeld bij dit gebaar.")
                best.score >= 0.5f -> listOf("Bijna goed. Kijk nog eens naar de stand van je vingers.")
                else -> listOf("Breng je hele hand in beeld en vorm het gebaar opnieuw.")
            },
        )
    }

    /** Scoort de live pose tegen één referentievariant [expected]. Null als de variant zelf niet normaliseerbaar is. */
    private fun scoreAgainst(
        expected: HandPose,
        actualFrames: List<HandPose>,
        actualPrimaryNormalized: List<com.example.signcoach.domain.normalize.CanonicalPose>,
        template: SignTemplate,
    ): CandidateResult? {
        val expectedPrimary = LandmarkNormalizer.normalize(expected) ?: return null

        val primaryDistance = actualPrimaryNormalized
            .flatMap { actual -> actual.landmarks.zip(expectedPrimary.landmarks).map { (a, b) -> euclideanDistance(a, b) } }
            .average()
            .toFloat()

        val expectedSecondary = expected.secondaryHandLandmarks?.let {
            LandmarkNormalizer.normalize(HandPose(landmarks = it, timestampMs = expected.timestampMs))
        }
        val secondaryDistance = if (expectedSecondary != null) {
            val actualSecondaryNormalized = actualFrames.mapNotNull { pose ->
                pose.secondaryHandLandmarks?.let {
                    LandmarkNormalizer.normalize(HandPose(landmarks = it, timestampMs = pose.timestampMs))
                }
            }
            if (actualSecondaryNormalized.size != actualFrames.size) {
                null
            } else {
                actualSecondaryNormalized
                    .flatMap { actual -> actual.landmarks.zip(expectedSecondary.landmarks).map { (a, b) -> euclideanDistance(a, b) } }
                    .average()
                    .toFloat()
            }
        } else {
            null
        }

        val handMeanDistance = if (secondaryDistance != null) (primaryDistance + secondaryDistance) / 2f else primaryDistance
        val positionScore = scoreFromDistance(handMeanDistance, maxDistance)

        // Puur landmarkafstand middelt over alle 21 punten, inclusief pols en knokkels die
        // nauwelijks bewegen tussen een open hand en een vuist. Daardoor verdwijnt het signaal
        // van gekromde vs. gestrekte vingers in de ruis (een volledig open hand scoorde zo
        // onterecht ~54% tegen de A-vuist-template). fingerCurlRatios() meet per vinger apart
        // hoe gestrekt hij is (1.0 = kaarsrecht, lager = gekromd), onafhankelijk van
        // handgrootte/afstand/rotatie, en telt dus zwaarder mee voor precies het verschil dat
        // ertoe doet: welke vingers gebogen zijn.
        val actualCurl = averageCurl(actualFrames)
        val expectedCurl = fingerCurlRatios(expected.landmarks)
        val curlDistance = actualCurl.indices.map { abs(actualCurl[it] - expectedCurl[it]) }.average().toFloat()
        val curlScore = (1f - curlDistance / CURL_MAX_DISTANCE).coerceIn(0f, 1f)

        val handScore = ((1f - CURL_WEIGHT) * positionScore + CURL_WEIGHT * curlScore).coerceIn(0f, 1f)

        val faceRules = template.rules.filter { it.type == RuleType.FACE_BLENDSHAPE && !it.blendshapeName.isNullOrBlank() }
        val faceScore = if (faceRules.isEmpty()) {
            null
        } else {
            val latestPose = actualFrames.last()
            faceRules
                .map { rule ->
                    val target = rule.targetValue ?: 0f
                    val tolerance = (rule.tolerance ?: DEFAULT_BLENDSHAPE_TOLERANCE).coerceAtLeast(MIN_TOLERANCE)
                    val actualValue = latestPose.blendshapeScore(rule.blendshapeName!!)
                    (1f - abs(actualValue - target) / tolerance).coerceIn(0f, 1f)
                }
                .average()
                .toFloat()
        }

        val score = if (faceScore != null) {
            ((1f - nonManualWeight) * handScore + nonManualWeight * faceScore).coerceIn(0f, 1f)
        } else {
            handScore
        }

        return CandidateResult(score, primaryDistance, secondaryDistance, faceScore)
    }

    private fun euclideanDistance(first: LandmarkPoint, second: LandmarkPoint): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        val dz = first.z - second.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Gemiddelde curl-ratio per vinger over meerdere frames (zie [fingerCurlRatios]). */
    private fun averageCurl(frames: List<HandPose>): List<Float> {
        val perFrame = frames.map { fingerCurlRatios(it.landmarks) }
        return List(FINGER_CHAINS.size) { i -> perFrame.map { it[i] }.average().toFloat() }
    }

    /**
     * Per vinger (duim, wijs-, middel-, ring-, pink) de verhouding tussen de rechte afstand
     * MCP->TIP en de opgetelde lengte van de vingersegmenten. Bij een kaarsrechte vinger ligt
     * dit dicht bij 1.0; bij een volledig gekromde vinger (vuist) is dit duidelijk lager. De
     * ratio is een pure geometrische verhouding, dus al schaal-, positie- en rotatie-onafhankelijk
     * zonder dat de landmarks eerst genormaliseerd hoeven te worden.
     */
    private fun fingerCurlRatios(landmarks: List<LandmarkPoint>): List<Float> =
        FINGER_CHAINS.map { chain ->
            val mcp = landmarks[chain[0]]
            val tip = landmarks[chain[3]]
            val straight = euclideanDistance(mcp, tip)
            var chainLength = 0f
            for (i in 0 until chain.size - 1) {
                chainLength += euclideanDistance(landmarks[chain[i]], landmarks[chain[i + 1]])
            }
            if (chainLength < MIN_CHAIN_LENGTH) 1f else (straight / chainLength).coerceIn(0f, 1.2f)
        }

    private fun invalidPoseFeedback() = FeedbackResult(
        score = 0f,
        isCorrect = false,
        feedbackMessages = listOf("Houd \u00e9\u00e9n hand volledig in beeld."),
    )

    private companion object {
        const val DEFAULT_MAX_DISTANCE = 1.25f
        const val DEFAULT_CORRECT_THRESHOLD = 0.75f
        // Hoeveel de non-manual (mondbeeld) score meetelt naast de handvorm-score, zodra het
        // template FACE_BLENDSHAPE-regels heeft. 0.2 = 80% handvorm / 20% mondbeeld; dit is een
        // MVP-startwaarde, zie PLAN.md sectie 2/6 voor kalibratie op echte data.
        const val DEFAULT_NON_MANUAL_WEIGHT = 0.2f
        const val DEFAULT_BLENDSHAPE_TOLERANCE = 0.3f
        const val MIN_TOLERANCE = 0.05f
        const val TWO_HAND_IMBALANCE_FACTOR = 1.3f
        const val FACE_FEEDBACK_THRESHOLD = 0.5f

        // Landmark-indices per vinger: [MCP, PIP, DIP, TIP]. Voor de duim zijn dit CMC/MCP/IP/TIP,
        // maar de curl-ratio-berekening werkt hetzelfde (rechte afstand eerste->laatste punt
        // t.o.v. de opgetelde segmentlengte).
        val FINGER_CHAINS = listOf(
            intArrayOf(1, 2, 3, 4), // duim
            intArrayOf(5, 6, 7, 8), // wijsvinger
            intArrayOf(9, 10, 11, 12), // middelvinger
            intArrayOf(13, 14, 15, 16), // ringvinger
            intArrayOf(17, 18, 19, 20), // pink
        )
        const val MIN_CHAIN_LENGTH = 0.0001f
        // Maximaal verwachte gemiddelde curl-afwijking (ratio-eenheden) tussen live hand en
        // referentie; 0.5 komt ongeveer overeen met "volledig open" vs. "volledig gebald".
        const val CURL_MAX_DISTANCE = 0.5f
        // Hoeveel de curl-score (vingerstand: gestrekt vs. gekromd) meeweegt naast de
        // positionele landmarkafstand binnen de handvorm-score. 0.5 = gelijk gewicht; curl is
        // het signaal dat "vuist vs. open hand" onderscheidt, positie vangt bv. duimplaatsing en
        // vingerspreiding.
        const val CURL_WEIGHT = 0.5f
    }
}
