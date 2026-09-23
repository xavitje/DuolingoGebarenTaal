package io.github.xavitje.signcoach.domain.progress

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Pure (geen I/O) client-side kopie van de XP/streak-regels uit de `record_practice()`-functie in
 * Supabase — zie [io.github.xavitje.signcoach.data.repository.OfflineProgressRepository], dat deze
 * functie gebruikt voor de directe, lokale UI-feedback vóórdat er gesynchroniseerd is. Bewust
 * losgetrokken van SQLDelight/IO zodat de regels zelf (streak, freezes, XP) los getest kunnen
 * worden zonder een database nodig te hebben.
 */
object StreakCalculator {

    data class Input(
        val today: LocalDate,
        val alreadyPracticedToday: Boolean,
        val currentStreak: Int,
        val longestStreak: Int,
        val totalXp: Int,
        /** XP die vandaag al verdiend is, of 0 als [todayDate] niet vandaag is. */
        val todayXp: Int,
        val todayDate: String?,
        val lastPracticeDate: LocalDate?,
        val streakFreezesAvailable: Int,
        val streakFreezesResetAt: LocalDate?,
    )

    data class Result(
        val xpEarned: Int,
        val newStreak: Int,
        val newLongestStreak: Int,
        val newTotalXp: Int,
        val newTodayXp: Int,
        val newFreezesAvailable: Int,
        val newFreezesResetAt: LocalDate,
    )

    fun compute(input: Input): Result {
        val t = input.today
        val xpEarned = if (input.alreadyPracticedToday) 2 else 15

        // Eén gratis streak-freeze per kalendermaand: reset zodra we in een nieuwe maand zitten
        // t.o.v. de laatst bekende reset-datum (of als er nog nooit een reset is geweest).
        val currentMonth = LocalDate(t.year, t.monthNumber, 1)
        var freezesAvailable = input.streakFreezesAvailable
        var freezesResetAt = input.streakFreezesResetAt
        if (freezesResetAt == null || freezesResetAt < currentMonth) {
            freezesAvailable = 1
            freezesResetAt = currentMonth
        }

        val yesterday = t.minus(DatePeriod(days = 1))
        val twoDaysAgo = t.minus(DatePeriod(days = 2))
        val newStreak = when {
            input.lastPracticeDate == null -> 1
            input.lastPracticeDate == t -> input.currentStreak
            input.lastPracticeDate == yesterday -> input.currentStreak + 1
            input.lastPracticeDate == twoDaysAgo && freezesAvailable > 0 -> {
                freezesAvailable -= 1
                input.currentStreak + 1
            }
            else -> 1
        }
        val newLongest = maxOf(input.longestStreak, newStreak)

        val todayXpBase = if (input.todayDate == t.toString()) input.todayXp else 0
        val newTodayXp = todayXpBase + xpEarned
        val newTotalXp = input.totalXp + xpEarned

        return Result(
            xpEarned = xpEarned,
            newStreak = newStreak,
            newLongestStreak = newLongest,
            newTotalXp = newTotalXp,
            newTodayXp = newTodayXp,
            newFreezesAvailable = freezesAvailable,
            newFreezesResetAt = freezesResetAt,
        )
    }
}
