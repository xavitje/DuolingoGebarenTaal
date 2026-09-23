package io.github.xavitje.signcoach.data.repository

import io.github.xavitje.signcoach.data.model.CurriculumGesture
import io.github.xavitje.signcoach.data.model.CurriculumUnit
import io.github.xavitje.signcoach.data.model.SignLanguage
import io.github.xavitje.signcoach.data.model.SignTemplate
import io.github.xavitje.signcoach.data.remote.CurriculumApi

/**
 * Bouwt de leerpad-structuur ([List<CurriculumUnit>]) door:
 * 1. Curriculum-metadata op te halen via [CurriculumApi] (gestures-tabel), gefilterd op de
 *    actieve [SignLanguage].
 * 2. Als dat mislukt: valt terug op de taal-specifieke offline fallback
 *    ([NgtFallbackCurriculum]/[AslFallbackCurriculum]).
 * 3. Verrijkt elke [CurriculumGesture] met [hasTemplate] op basis van de beschikbare
 *    [SignTemplate]s (die via [SyncingTemplateRepository] zijn geladen).
 *
 * Zo is het leerpad altijd zichtbaar, ook zonder netwerk of zonder dat alle referentie-opnames
 * al zijn opgenomen en goedgekeurd. De set units verschilt per taal: NGT heeft het volledige
 * alfabet + cijfers, ASL start met alleen een basiswoorden-unit (zie plan: het ASL-vingeralfabet
 * heeft nog geen bevestigd bruikbare bron).
 */
class CurriculumRepository(
    private val api: CurriculumApi = CurriculumApi(),
) {
    /**
     * Laadt het volledige leerpad voor [language]. [availableTemplates] is de set
     * herkenningstemplates die al beschikbaar zijn — gebruikt om
     * [CurriculumGesture.hasTemplate] in te stellen.
     */
    suspend fun load(availableTemplates: List<SignTemplate>, language: SignLanguage): List<CurriculumUnit> {
        val templateIds = availableTemplates.map { it.id }.toSet()

        // Probeer de curriculum-metadata van de server te halen.
        val gestureDtos = api.fetchGestures(language.code)
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: fallbackGestures(language)  // offline fallback

        // Groepeer op unitId en bouw CurriculumUnit-objecten.
        val byUnit = gestureDtos.groupBy { it.unitId }
        return unitIds(language)
            .mapNotNull { unitId ->
                val unit = unitMeta(language, unitId) ?: return@mapNotNull null
                val gestures = byUnit[unitId].orEmpty()
                    .sortedBy { it.orderIndex }
                    .map { dto ->
                        CurriculumGesture(
                            id = dto.id,
                            name = dto.name,
                            category = dto.category,
                            orderIndex = dto.orderIndex,
                            unitId = dto.unitId,
                            hasTemplate = dto.id in templateIds,
                            demoVideoUrl = dto.demoVideoUrl,
                            language = language.code,
                        )
                    }
                unit.copy(gestures = gestures)
            }
    }

    private fun unitIds(language: SignLanguage): List<String> = when (language) {
        SignLanguage.NGT -> listOf("alphabet", "numbers")
        SignLanguage.ASL -> listOf("core_words")
        SignLanguage.BSL -> emptyList()
    }

    private fun unitMeta(language: SignLanguage, unitId: String): CurriculumUnit? = when (language) {
        SignLanguage.NGT -> when (unitId) {
            "alphabet" -> CurriculumUnit("alphabet", "Alfabet", "🔤", emptyList())
            "numbers"  -> CurriculumUnit("numbers",  "Cijfers", "🔢", emptyList())
            else        -> null
        }
        SignLanguage.ASL -> when (unitId) {
            "core_words" -> CurriculumUnit("core_words", "Basiswoorden", "🗣️", emptyList())
            else           -> null
        }
        SignLanguage.BSL -> null
    }

    private fun fallbackGestures(language: SignLanguage): List<CurriculumApi.GestureDto> = when (language) {
        SignLanguage.NGT -> NgtFallbackCurriculum.gestures
        SignLanguage.ASL -> AslFallbackCurriculum.gestures
        SignLanguage.BSL -> emptyList()
    }
}

// ─── Offline fallback: NGT ────────────────────────────────────────────────────

/**
 * Hardcoded lijst van alle 36 NGT-handalfabet-gebaren (A-Z + 0-9).
 * Wordt gebruikt als de gestures-tabel niet bereikbaar is bij app-start.
 */
private object NgtFallbackCurriculum {
    val gestures: List<CurriculumApi.GestureDto> = buildList {
        // ── Alfabet (A-Z) ──
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEachIndexed { i, c ->
            add(CurriculumApi.GestureDto(
                id = "letter_${c.lowercaseChar()}",
                name = "$c",
                category = "letter",
                orderIndex = i,
                unitId = "alphabet",
            ))
        }
        // ── Cijfers (0-9) ──
        (0..9).forEachIndexed { i, n ->
            add(CurriculumApi.GestureDto(
                id = "number_$n",
                name = "$n",
                category = "number",
                orderIndex = i,
                unitId = "numbers",
            ))
        }
    }
}

// ─── Offline fallback: ASL ────────────────────────────────────────────────────

/**
 * Eerste subset van ASL-basiswoorden (alledaagse woordenschat, geen vingeralfabet — zie het
 * open punt in het plan). Deze lijst is bewust klein: het is de curriculum-metadata die vast
 * staat zodra de content-pijplijn (taak #55) de bijbehorende PopSign-video's heeft verwerkt tot
 * goedgekeurde `gesture_samples`. Tot die tijd is `hasTemplate` voor deze woorden `false` en
 * toont de UI ze als "nog niet beschikbaar" — de curriculumstructuur zelf is al wel compleet.
 */
private object AslFallbackCurriculum {
    val gestures: List<CurriculumApi.GestureDto> = listOf(
        "hello", "please", "thankyou", "sorry", "yes", "no", "help",
        "more", "again", "finished", "eat", "drink", "water",
        "want", "like", "love", "happy", "sad", "friend", "play",
    ).mapIndexed { i, word ->
        CurriculumApi.GestureDto(
            id = "asl_$word",
            name = word.replaceFirstChar { it.uppercase() },
            category = "word",
            orderIndex = i,
            unitId = "core_words",
        )
    }
}
