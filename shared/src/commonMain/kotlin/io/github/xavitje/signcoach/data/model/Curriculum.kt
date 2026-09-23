package io.github.xavitje.signcoach.data.model

/**
 * Een unit in het leerpad — groepeert gebaren op categorie, binnen één gebarentaal.
 * NGT heeft de originele twee units (Alfabet, Cijfers); andere talen hebben hun eigen
 * unit-set (zie CurriculumRepository.unitMeta()).
 */
data class CurriculumUnit(
    val id: String,           // "alphabet" | "numbers" | "core_words" | ...
    val title: String,        // "Alfabet" | "Cijfers" | "Basiswoorden" | ...
    val emoji: String,        // decoratief icoon
    val gestures: List<CurriculumGesture>,
    val language: String = "ngt",
)

/**
 * Eén gebaar binnen het leerpad — bevat alleen de metadata die de UI nodig heeft.
 * De herkenningstemplates (landmarks, rules) staan in [SignTemplate] en worden apart geladen.
 */
data class CurriculumGesture(
    val id: String,
    val name: String,
    val category: String,
    val orderIndex: Int,
    val unitId: String,
    /** Of er een bruikbare SignTemplate is die herkenning mogelijk maakt. */
    val hasTemplate: Boolean = false,
    /** Publieke URL naar een referentievideo (bv. Corpus NGT/Global Signbank) die vóór het
     * oefenen getoond wordt, zodat de gebruiker eerst ziet hoe het gebaar hoort te gaan.
     * Null = nog geen demo beschikbaar; het gebaar is dan gewoon direct te oefenen. */
    val demoVideoUrl: String? = null,
    /** [SignLanguage.code] — welke gebarentaal dit teken hoort. */
    val language: String = "ngt",
)
