package io.github.xavitje.signcoach.data.model

/**
 * Ondersteunde gebarentalen. [code] is de wire-/opslagrepresentatie (Supabase `sign_language`-
 * kolommen, [io.github.xavitje.signcoach.data.local.SessionStore]) — gebruik nooit de enum-naam
 * zelf voor persistente of netwerkwaarden, zodat het enum-lid hernoemen bestaande data niet breekt.
 *
 * Elk teken (`gestureId`) is alleen samen met een [SignLanguage] uniek: "letter_a" betekent iets
 * anders in NGT dan in ASL. Gamification (streak/XP/badges) is bewust taal-onafhankelijk — zie
 * de sign_language-migratie voor die productbeslissing.
 */
enum class SignLanguage(val code: String) {
    NGT("ngt"),
    ASL("asl"),
    BSL("bsl"),
    ;

    companion object {
        val DEFAULT: SignLanguage = NGT

        /** Talen die nu echt content hebben en dus in de taalkiezer getoond worden. BSL bestaat
         * hier al als concept (databasekolommen, dit enum-lid) zodat de rest van de architectuur
         * er klaar voor is, maar heeft nog geen bruikbare referentiedatabron — zie het
         * architectuurplan voor meertalige ondersteuning. Verborgen tot die er is. */
        val SELECTABLE: List<SignLanguage> = listOf(NGT, ASL)

        fun fromCode(code: String?): SignLanguage = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}
