package io.github.xavitje.signcoach.data.model

/**
 * Rol van een gebruiker (zie implementatieplan fase 2). De rol bepaalt wat de app TOONT; de echte
 * beveiliging zit in Supabase (RLS + `public.my_role()`), zodat een omzeilde UI niets oplevert.
 * [code] is de waarde in de database (`public.user_role`-enum) — nooit de enum-naam opslaan.
 */
enum class UserRole(val code: String) {
    /** Kind in een klas: leerpad, oefenen, eigen voortgang. */
    STUDENT("student"),
    /** Docent: klasoverzicht (fase 3). */
    TEACHER("teacher"),
    /** ICT-coördinator/directie: klassen, docenten en licentie (fase 3). */
    SCHOOL_ADMIN("school_admin"),
    /** Gebarentaaldocent/tolk die content levert via "Draag een gebaar bij". */
    PROFESSIONAL("professional"),
    /** Beheer van de hele app: bijdragen goedkeuren, scholen en licenties. */
    ADMIN("admin"),
    ;

    /** Alleen vakmensen en admins mogen gebaren opnemen en indienen. */
    val canContribute: Boolean get() = this == PROFESSIONAL || this == ADMIN

    companion object {
        /** Onbekend of niet op te halen = leerling: de rol met de minste rechten. */
        fun fromCode(code: String?): UserRole = entries.firstOrNull { it.code == code } ?: STUDENT
    }
}
