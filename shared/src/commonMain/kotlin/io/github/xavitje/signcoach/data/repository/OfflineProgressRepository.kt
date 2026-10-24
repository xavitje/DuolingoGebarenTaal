package io.github.xavitje.signcoach.data.repository

import io.github.xavitje.signcoach.data.remote.ProfileApi
import io.github.xavitje.signcoach.domain.progress.StreakCalculator
import io.github.xavitje.signcoach.db.LocalDatabase
import io.github.xavitje.signcoach.db.LocalProfile
import io.github.xavitje.signcoach.db.SignCoachDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Offline-first opslag voor voortgang (Zero Trust — zie gesprek: "de app moet offline werken en
 * pas bij verbinding updaten met de Supabase db, dus je xp en reeks moeten ook offline opgeslagen
 * kunnen worden"). Elke afgeronde oefening wordt EERST hier lokaal weggeschreven — dit werkt
 * altijd, ook zonder netwerk — en pas daarna, via [io.github.xavitje.signcoach.sync.SyncManager], naar
 * Supabase gestuurd. De UI leest nooit rechtstreeks van het netwerk voor XP/streak: deze klasse is
 * de enige waarheid voor de UI, [io.github.xavitje.signcoach.sync.SyncManager] is de enige die het
 * lokale profiel overschrijft met de authoritatieve staat van de server.
 *
 * De streak/XP-regels hieronder zijn een client-side kopie van de `record_practice()`-functie in
 * Supabase, puur voor directe feedback in de UI. Kleine afwijkingen (bv. een dag-grens net anders
 * geïnterpreteerd dan de server door tijdzoneverschillen) herstellen zichzelf vanzelf zodra de
 * volgende sync de server-waarheid terugschrijft.
 */
class OfflineProgressRepository(
    db: SignCoachDatabase = LocalDatabase.instance(),
) {
    private val queries = db.progressQueries

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    /** Lokaal gecachet profiel, of null als er nog nooit iets lokaal is opgeslagen (bv. eerste
     * keer openen zonder internet — dan is er geen enkele waarheid om te tonen). */
    fun localProfileOrNull(userId: String): ProfileApi.UserProfile? =
        runCatching { queries.selectProfile(userId).executeAsOneOrNull() }.getOrNull()?.toUserProfile()

    fun localTodayXp(userId: String): Int {
        val row = runCatching { queries.selectProfile(userId).executeAsOneOrNull() }.getOrNull() ?: return 0
        return if (row.todayDate == today().toString()) row.todayXp.toInt() else 0
    }

    fun pendingCountForUser(userId: String): Int =
        runCatching { queries.countPendingPracticeForUser(userId).executeAsOne() }.getOrDefault(0L).toInt()

    fun pendingForUser(userId: String) =
        runCatching { queries.selectPendingPracticeForUser(userId).executeAsList() }.getOrDefault(emptyList())

    fun removePending(id: Long) {
        runCatching { queries.deletePendingPracticeById(id) }
    }

    /** Schrijft de server-waarheid weg als lokale cache — na een succesvolle sync, of bij de
     * allereerste online load van een net ingelogde gebruiker. */
    fun cacheServerProfile(userId: String, profile: ProfileApi.UserProfile, serverTodayXp: Int) {
        runCatching {
            queries.upsertProfile(
                id = userId,
                avatarEmoji = profile.avatarEmoji,
                currentStreak = profile.currentStreak.toLong(),
                longestStreak = profile.longestStreak.toLong(),
                totalXp = profile.totalXp.toLong(),
                dailyGoalXp = profile.dailyGoalXp.toLong(),
                streakFreezesAvailable = profile.streakFreezesAvailable.toLong(),
                lastPracticeDate = profile.lastPracticeDate,
                streakFreezesResetAt = profile.streakFreezesResetAt,
                todayXp = serverTodayXp.toLong(),
                todayDate = today().toString(),
                lastSyncedAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    /**
     * Verwerkt een afgeronde oefening volledig lokaal (werkt altijd, ook zonder netwerk):
     * berekent XP en streak met dezelfde regels als de server, slaat het resultaat direct op
     * zodat de UI meteen kan bijwerken, en zet de oefening in de wachtrij voor synchronisatie.
     * Geeft het aantal verdiende XP terug (voor een korte "+X XP"-melding in de UI), of null als
     * het lokaal opslaan onverhoopt mislukte.
     */
    fun recordPracticeOffline(userId: String, gestureId: String, score: Float, signLanguage: String = "ngt"): Int? {
        return runCatching {
            val t = today()
            val tStr = t.toString()
            val current = runCatching { queries.selectProfile(userId).executeAsOneOrNull() }.getOrNull()
                ?: defaultProfileRow(userId)

            val alreadyPracticedToday = queries.hasPracticedToday(gestureId, signLanguage, tStr).executeAsOne() > 0

            // De eigenlijke streak/freeze/XP-regels staan in StreakCalculator (pure functie, apart
            // getest in StreakCalculatorTest) — hier alleen het ophalen van de huidige staat en het
            // wegschrijven van het resultaat.
            val result = StreakCalculator.compute(
                StreakCalculator.Input(
                    today = t,
                    alreadyPracticedToday = alreadyPracticedToday,
                    currentStreak = current.currentStreak.toInt(),
                    longestStreak = current.longestStreak.toInt(),
                    totalXp = current.totalXp.toInt(),
                    todayXp = current.todayXp.toInt(),
                    todayDate = current.todayDate,
                    lastPracticeDate = current.lastPracticeDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    streakFreezesAvailable = current.streakFreezesAvailable.toInt(),
                    streakFreezesResetAt = current.streakFreezesResetAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                ),
            )

            queries.transaction {
                queries.upsertProfile(
                    id = userId,
                    avatarEmoji = current.avatarEmoji,
                    currentStreak = result.newStreak.toLong(),
                    longestStreak = result.newLongestStreak.toLong(),
                    totalXp = result.newTotalXp.toLong(),
                    dailyGoalXp = current.dailyGoalXp,
                    streakFreezesAvailable = result.newFreezesAvailable.toLong(),
                    lastPracticeDate = tStr,
                    streakFreezesResetAt = result.newFreezesResetAt.toString(),
                    todayXp = result.newTodayXp.toLong(),
                    todayDate = tStr,
                    lastSyncedAtMillis = current.lastSyncedAtMillis,
                )
                queries.markPracticedToday(gestureId, signLanguage, tStr)
                queries.insertPendingPractice(userId, gestureId, signLanguage, score.toDouble(), Clock.System.now().toEpochMilliseconds())
                queries.markGestureCompleted(userId, gestureId, signLanguage)
            }
            queries.clearPracticedBefore(tStr)
            result.xpEarned
        }.getOrNull()
    }

    /** Lessen die deze gebruiker in deze gebarentaal al gehaald heeft (lokale waarheid). */
    fun completedGestureIds(userId: String, signLanguage: String): Set<String> =
        runCatching { queries.selectCompletedGestureIds(userId, signLanguage).executeAsList().toSet() }
            .getOrDefault(emptySet())

    /** Neemt gehaalde lessen over die de server kent (bv. van een ander apparaat). Alleen
     * toevoegen, nooit weghalen: een lokaal gehaalde les die nog in de sync-wachtrij staat mag
     * niet verdwijnen omdat de server hem nog niet kent. */
    fun mergeServerCompleted(userId: String, signLanguage: String, gestureIds: Collection<String>) {
        runCatching {
            queries.transaction {
                gestureIds.forEach { queries.markGestureCompleted(userId, it, signLanguage) }
            }
        }
    }

    private fun defaultProfileRow(userId: String): LocalProfile = LocalProfile(
        id = userId,
        avatarEmoji = "🧑",
        currentStreak = 0,
        longestStreak = 0,
        totalXp = 0,
        dailyGoalXp = 35,
        streakFreezesAvailable = 1,
        lastPracticeDate = null,
        streakFreezesResetAt = null,
        todayXp = 0,
        todayDate = today().toString(),
        lastSyncedAtMillis = 0L,
    )
}

private fun LocalProfile.toUserProfile(): ProfileApi.UserProfile = ProfileApi.UserProfile(
    id = id,
    displayName = null,
    avatarEmoji = avatarEmoji,
    currentStreak = currentStreak.toInt(),
    longestStreak = longestStreak.toInt(),
    totalXp = totalXp.toInt(),
    dailyGoalXp = dailyGoalXp.toInt(),
    streakFreezesAvailable = streakFreezesAvailable.toInt(),
    lastPracticeDate = lastPracticeDate,
    streakFreezesResetAt = streakFreezesResetAt,
)
