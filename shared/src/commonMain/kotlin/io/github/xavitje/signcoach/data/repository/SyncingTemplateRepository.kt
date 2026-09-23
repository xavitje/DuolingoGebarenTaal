package io.github.xavitje.signcoach.data.repository

import io.github.xavitje.signcoach.data.model.GestureRule
import io.github.xavitje.signcoach.data.model.Handedness
import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.data.model.RuleType
import io.github.xavitje.signcoach.data.model.SignLanguage
import io.github.xavitje.signcoach.data.model.SignTemplate
import io.github.xavitje.signcoach.data.remote.GestureContributionApi

/**
 * Combineert de gebundelde basistemplates (templates.json, altijd aanwezig, ook zonder
 * internet) met goedgekeurde referentievarianten uit de cloud-database.
 *
 * Gedrag bij opstarten ([syncAndLoad]):
 * - Netwerk beschikbaar: haalt alle samples met status=approved op. Voor een gebaar dat al een
 *   gebundeld basistemplate heeft, worden de goedgekeurde samples toegevoegd aan
 *   `referenceVariants`. Voor een gebaar dat NOG GEEN gebundeld basistemplate heeft (dat zijn
 *   de meeste — `templates.json` bevat alleen de MVP-set) maar wel al minstens één goedgekeurde
 *   bijdrage, wordt er automatisch een nieuw [SignTemplate] gesynthetiseerd op basis van die
 *   bijdrage(n) alleen. Zo wordt letterlijk elk goedgekeurd gebaar bruikbaar voor herkenning
 *   zodra het door een beheerder is goedgekeurd — niet alleen de handvol die vooraf is
 *   meegebundeld. (Voorheen werden samples voor niet-gebundelde gebaren hier stilzwijgend
 *   genegeerd, ook al stonden ze al als 'approved' in de database.)
 * - Geen netwerk / request faalt: valt terug op de laatst gecachete gesynchroniseerde versie
 *   (mogelijk iets ouder dan de huidige serverstatus); is er nog nooit gesynchroniseerd, dan
 *   de kale gebundelde basisset.
 *
 * De app blijft dus altijd bruikbaar — alleen de "versheid" van de referentieset verschilt.
 */
class SyncingTemplateRepository(
    private val bundled: TemplateRepository,
    private val api: GestureContributionApi = GestureContributionApi(),
) {
    suspend fun syncAndLoad(language: SignLanguage): List<SignTemplate> {
        // De gebundelde templates.json bevat uitsluitend NGT-content; andere talen starten
        // zonder gebundelde basisset en bouwen alles op uit goedgekeurde cloud-samples.
        val base = if (language == SignLanguage.NGT) {
            bundled.load().getOrElse { emptyList() }
        } else {
            emptyList()
        }
        val approved = api.fetchApprovedSamples(language.code).getOrNull()

        if (approved == null) {
            // Geen (werkende) verbinding: laatst gesynchroniseerde cache, anders de basisset.
            return TemplateCache.load(language) ?: base
        }

        val byGesture = approved.groupBy { it.gestureId }
        val baseIds = base.map { it.id }.toSet()

        val enrichedBase = base.map { template ->
            val extraVariants = byGesture[template.id].orEmpty().map { it.toHandPose() }
            template.copy(referenceVariants = template.referenceVariants + extraVariants)
        }

        val synthesized = byGesture
            .filterKeys { it !in baseIds }
            .mapNotNull synth@{ (gestureId, samples) ->
                val poses = samples.map { it.toHandPose() }
                if (poses.isEmpty()) return@synth null
                // Echte categorie/unit uit de PostgREST-embed (gestures-tabel via FK), met een
                // veilige fallback als de embed om wat voor reden dan ook leeg is.
                val meta = samples.firstNotNullOfOrNull { it.gestures }
                SignTemplate(
                    id = gestureId,
                    name = gestureId.substringAfter('_').uppercase(),
                    frames = listOf(poses.first()),
                    rules = listOf(GestureRule(type = RuleType.MIN_STABLE_FRAMES, targetValue = 5f)),
                    referenceVariants = poses.drop(1),
                    category = meta?.category ?: "letter",
                    unitId = meta?.unitId ?: "alphabet",
                    language = language.code,
                )
            }

        val merged = enrichedBase + synthesized
        TemplateCache.save(language, merged)
        return merged
    }

    private fun GestureContributionApi.ApprovedSampleDto.toHandPose() = HandPose(
        landmarks = landmarks,
        timestampMs = 0,
        handedness = runCatching { Handedness.valueOf(handedness) }.getOrDefault(Handedness.UNKNOWN),
    )
}
