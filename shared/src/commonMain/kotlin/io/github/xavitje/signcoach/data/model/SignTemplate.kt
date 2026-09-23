package io.github.xavitje.signcoach.data.model

import kotlinx.serialization.Serializable

/**
 * [frames]: voor statische gebaren de hoofdreferentiepose (frames.first()); voor bewegende
 * gebaren (MOTION_DIRECTION) het volledige bewegingstraject in volgorde.
 *
 * [referenceVariants]: optionele extra geldige referentieposes voor hetzelfde statische
 * gebaar (bv. een andere persoon, links- i.p.v. rechtshandig, een net iets andere hoek).
 * De scorer vergelijkt de live pose met elke variant (frames.first() + referenceVariants)
 * en gebruikt de best passende. Leeg = alleen frames.first() als referentie, zoals in de
 * MVP. Dit veld is de opstap naar een echte, groeiende referentiedataset per gebaar zonder
 * dat het opslagformaat opnieuw hoeft te veranderen.
 *
 * [category]: curriculumcategorie — "letter" of "number". Determines which unit this gesture
 * belongs to. Defaults to "letter" for backward-compatibility.
 * [orderIndex]: volgorde binnen de unit (0-based). Gebaar met index 0 is als eerste ontgrendeld.
 * [unitId]: groepeert gebaren in units — "alphabet" of "numbers" voor v1.
 * [language]: welke gebarentaal dit teken hoort ([SignLanguage.code], bv. "ngt"/"asl"). Samen
 * met [id] vormt dit de echte identiteit van een teken — "letter_a" bestaat afzonderlijk per
 * taal. Default "ngt" voor compatibiliteit met het bestaande, ongewijzigde templates.json (dat
 * uitsluitend NGT bevat en dit veld dus niet serialiseert).
 */
@Serializable
data class SignTemplate(
    val id: String,
    val name: String,
    val frames: List<HandPose>,
    val rules: List<GestureRule>,
    val referenceVariants: List<HandPose> = emptyList(),
    val category: String = "letter",
    val orderIndex: Int = 0,
    val unitId: String = "alphabet",
    val language: String = "ngt",
)
