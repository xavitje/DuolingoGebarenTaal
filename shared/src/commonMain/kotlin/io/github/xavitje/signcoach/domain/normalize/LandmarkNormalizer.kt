package io.github.xavitje.signcoach.domain.normalize

import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.data.model.LandmarkPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Normaliseert een hand-pose naar een similarity-invariante ("canonieke") vorm, zodat
 * vergelijking met een template werkt in meerdere situaties in plaats van alleen exact
 * dezelfde handgrootte/afstand/kanteling als toen de template werd opgenomen:
 * - Translatie: pols (landmark 0) wordt de oorsprong.
 * - Schaal: gedeeld door de afstand pols -> middelvinger-MCP (landmark 9), zodat
 *   handgrootte en afstand tot de camera er niet toe doen.
 * - Rotatie: het xy-vlak wordt gedraaid zodat de pols->MCP-as altijd in dezelfde
 *   canonieke richting wijst, zodat een iets gekantelde pols niet als een andere
 *   handvorm wordt gezien.
 *
 * Zowel de live pose als elke opgeslagen template-referentie gaan door dezelfde
 * normalisatie, dus de vergelijking blijft intern consistent.
 */
object LandmarkNormalizer {
    private const val LANDMARK_COUNT = 21
    private const val WRIST = 0
    private const val MIDDLE_FINGER_MCP = 9
    private const val MIN_SCALE = 0.0001f

    // Canonieke richting: pols->MCP wijst recht "omhoog" in beeldcoördinaten (y neemt af
    // naar boven), dus hoek -90 graden.
    private val CANONICAL_ANGLE = -(kotlin.math.PI / 2.0).toFloat()

    fun normalize(pose: HandPose, mirrored: Boolean = false): CanonicalPose? {
        if (pose.landmarks.size != LANDMARK_COUNT || pose.timestampMs < 0) return null
        if (pose.landmarks.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) return null

        val oriented = if (mirrored) {
            pose.landmarks.map { it.copy(x = 1f - it.x) }
        } else {
            pose.landmarks
        }
        val wrist = oriented[WRIST]
        val middleMcp = oriented[MIDDLE_FINGER_MCP]
        val dx = middleMcp.x - wrist.x
        val dy = middleMcp.y - wrist.y
        val scale = sqrt(dx * dx + dy * dy)
        if (!scale.isFinite() || scale < MIN_SCALE) return null

        val rotation = CANONICAL_ANGLE - atan2(dy, dx)
        val cosR = cos(rotation)
        val sinR = sin(rotation)

        return CanonicalPose(
            landmarks = oriented.map { point ->
                val tx = (point.x - wrist.x) / scale
                val ty = (point.y - wrist.y) / scale
                LandmarkPoint(
                    x = tx * cosR - ty * sinR,
                    y = tx * sinR + ty * cosR,
                    z = (point.z - wrist.z) / scale,
                )
            },
            timestampMs = pose.timestampMs,
        )
    }
}
