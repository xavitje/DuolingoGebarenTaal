package com.example.signcoach.data.repository

import com.example.signcoach.data.model.Handedness
import com.example.signcoach.data.model.HandPose
import com.example.signcoach.data.model.SignTemplate
import com.example.signcoach.data.remote.GestureContributionApi

/**
 * Combineert de gebundelde basistemplates (templates.json, altijd aanwezig, ook zonder
 * internet) met goedgekeurde referentievarianten uit de cloud-database.
 *
 * Gedrag bij opstarten ([syncAndLoad]):
 * - Netwerk beschikbaar: haalt alle samples met status=approved op, voegt ze per gebaar toe
 *   aan `referenceVariants`, en cachet dit resultaat lokaal via [TemplateCache].
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
    suspend fun syncAndLoad(): List<SignTemplate> {
        val base = bundled.load().getOrElse { emptyList() }
        val approved = api.fetchApprovedSamples().getOrNull()

        if (approved == null) {
            // Geen (werkende) verbinding: laatst gesynchroniseerde cache, anders de basisset.
            return TemplateCache.load() ?: base
        }

        val byGesture = approved.groupBy { it.gestureId }
        val merged = base.map { template ->
            val extraVariants = byGesture[template.id].orEmpty().map { dto ->
                HandPose(
                    landmarks = dto.landmarks,
                    timestampMs = 0,
                    handedness = runCatching { Handedness.valueOf(dto.handedness) }.getOrDefault(Handedness.UNKNOWN),
                )
            }
            template.copy(referenceVariants = template.referenceVariants + extraVariants)
        }
        TemplateCache.save(merged)
        return merged
    }
}
