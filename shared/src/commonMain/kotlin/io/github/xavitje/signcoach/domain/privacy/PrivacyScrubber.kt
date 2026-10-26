package io.github.xavitje.signcoach.domain.privacy

/**
 * Haalt persoonsgegevens uit vrije tekst voordat die de app verlaat (crashrapporten).
 * Foutmeldingen bevatten soms een e-mailadres, een gebruikers-ID of een token, bijvoorbeeld
 * in een URL of een serverantwoord. Volgens de afspraken met scholen (privacybijsluiter, fase 4)
 * mag crashrapportage geen persoonsgegevens bevatten.
 */
object PrivacyScrubber {
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val uuid = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")
    private val jwt = Regex("""\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
    private val bearer = Regex("""(?i)(bearer|token|apikey|password|pin)(["'=:\s]+)[^\s"',&}]+""")
    private val queryValue = Regex("""([?&][A-Za-z_]+=)[^&\s"']+""")

    fun scrub(text: String?): String? = text?.let(::scrubNonNull)

    fun scrubNonNull(text: String): String = text
        .replace(jwt, "[token]")
        .replace(email, "[e-mail]")
        .replace(uuid, "[id]")
        .replace(bearer) { "${it.groupValues[1]}${it.groupValues[2]}[verwijderd]" }
        .replace(queryValue) { "${it.groupValues[1]}[verwijderd]" }
}
