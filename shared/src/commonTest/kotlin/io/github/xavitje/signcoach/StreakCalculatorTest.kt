package io.github.xavitje.signcoach

import io.github.xavitje.signcoach.domain.progress.StreakCalculator
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dekt de XP/streak-edge cases van [StreakCalculator] — de client-side kopie van de
 * `record_practice()`-regels uit Supabase, verantwoordelijk voor directe offline UI-feedback
 * (zie OfflineProgressRepository). Elke test gebruikt een vaste "vandaag" zodat de tests
 * deterministisch zijn, onafhankelijk van de daadwerkelijke systeemklok.
 */
class StreakCalculatorTest {

    private val today = LocalDate(2026, 9, 9)

    private fun baseInput(
        alreadyPracticedToday: Boolean = false,
        currentStreak: Int = 0,
        longestStreak: Int = 0,
        totalXp: Int = 0,
        todayXp: Int = 0,
        todayDate: String? = null,
        lastPracticeDate: LocalDate? = null,
        streakFreezesAvailable: Int = 1,
        streakFreezesResetAt: LocalDate? = LocalDate(2026, 9, 1),
    ) = StreakCalculator.Input(
        today = today,
        alreadyPracticedToday = alreadyPracticedToday,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        totalXp = totalXp,
        todayXp = todayXp,
        todayDate = todayDate,
        lastPracticeDate = lastPracticeDate,
        streakFreezesAvailable = streakFreezesAvailable,
        streakFreezesResetAt = streakFreezesResetAt,
    )

    @Test
    fun firstEverPractice_startsStreakAtOneAndAwardsFullXp() {
        val result = StreakCalculator.compute(baseInput(lastPracticeDate = null))
        assertEquals(1, result.newStreak)
        assertEquals(1, result.newLongestStreak)
        assertEquals(15, result.xpEarned)
        assertEquals(15, result.newTotalXp)
        assertEquals(15, result.newTodayXp)
    }

    @Test
    fun samedayRepeatPractice_keepsStreakAndAwardsReducedXp() {
        val result = StreakCalculator.compute(
            baseInput(
                alreadyPracticedToday = true,
                currentStreak = 3,
                longestStreak = 5,
                totalXp = 100,
                todayXp = 15,
                todayDate = today.toString(),
                lastPracticeDate = today,
            ),
        )
        assertEquals(3, result.newStreak, "streak mag niet veranderen bij een 2e oefening op dezelfde dag")
        assertEquals(2, result.xpEarned, "2e (of latere) oefening op dezelfde dag levert maar 2 XP op")
        assertEquals(102, result.newTotalXp)
        assertEquals(17, result.newTodayXp, "todayXp moet optellen bovenop wat vandaag al verdiend was")
    }

    @Test
    fun consecutiveDayPractice_incrementsStreak() {
        val yesterday = today.minus(DatePeriod(days = 1))
        val result = StreakCalculator.compute(
            baseInput(currentStreak = 4, longestStreak = 4, lastPracticeDate = yesterday),
        )
        assertEquals(5, result.newStreak)
        assertEquals(5, result.newLongestStreak)
        assertEquals(15, result.xpEarned)
    }

    @Test
    fun missedOneDayWithFreezeAvailable_preservesStreakAndConsumesFreeze() {
        val twoDaysAgo = today.minus(DatePeriod(days = 2))
        val result = StreakCalculator.compute(
            baseInput(
                currentStreak = 7,
                longestStreak = 7,
                lastPracticeDate = twoDaysAgo,
                streakFreezesAvailable = 1,
                streakFreezesResetAt = LocalDate(2026, 9, 1),
            ),
        )
        assertEquals(8, result.newStreak, "een gemiste dag met een freeze beschikbaar mag de streak niet breken")
        assertEquals(0, result.newFreezesAvailable, "de freeze moet verbruikt worden")
    }

    @Test
    fun missedOneDayWithoutFreezeAvailable_resetsStreak() {
        val twoDaysAgo = today.minus(DatePeriod(days = 2))
        val result = StreakCalculator.compute(
            baseInput(
                currentStreak = 7,
                longestStreak = 7,
                lastPracticeDate = twoDaysAgo,
                streakFreezesAvailable = 0,
                streakFreezesResetAt = LocalDate(2026, 9, 1),
            ),
        )
        assertEquals(1, result.newStreak, "zonder freeze breekt een gemiste dag de streak")
        assertEquals(7, result.newLongestStreak, "longestStreak moet het record blijven bewaren")
    }

    @Test
    fun missedMultipleDays_alwaysResetsStreakRegardlessOfFreeze() {
        val threeDaysAgo = today.minus(DatePeriod(days = 3))
        val result = StreakCalculator.compute(
            baseInput(
                currentStreak = 10,
                longestStreak = 10,
                lastPracticeDate = threeDaysAgo,
                streakFreezesAvailable = 1,
                streakFreezesResetAt = LocalDate(2026, 9, 1),
            ),
        )
        assertEquals(1, result.newStreak, "een freeze dekt maar precies 1 gemiste dag, niet meer")
    }

    @Test
    fun monthlyFreezeReset_grantsFreshFreezeInNewCalendarMonth() {
        // Laatste reset was in augustus; "vandaag" (9 september) zit dus al in een nieuwe maand,
        // ook al is de freeze van vorige maand niet gebruikt (0 resterend).
        val result = StreakCalculator.compute(
            baseInput(
                lastPracticeDate = null,
                streakFreezesAvailable = 0,
                streakFreezesResetAt = LocalDate(2026, 8, 1),
            ),
        )
        assertEquals(1, result.newFreezesAvailable, "nieuwe kalendermaand moet de freeze weer op 1 zetten")
        assertEquals(LocalDate(2026, 9, 1), result.newFreezesResetAt)
    }

    @Test
    fun noFreezeResetNeeded_whenAlreadyResetThisMonth() {
        val result = StreakCalculator.compute(
            baseInput(
                lastPracticeDate = null,
                streakFreezesAvailable = 0,
                streakFreezesResetAt = LocalDate(2026, 9, 1),
            ),
        )
        assertEquals(0, result.newFreezesAvailable, "binnen dezelfde maand mag een verbruikte freeze niet terugkomen")
        assertEquals(LocalDate(2026, 9, 1), result.newFreezesResetAt)
    }

    @Test
    fun neverResetBefore_grantsInitialFreeze() {
        val result = StreakCalculator.compute(
            baseInput(lastPracticeDate = null, streakFreezesAvailable = 0, streakFreezesResetAt = null),
        )
        assertEquals(1, result.newFreezesAvailable)
        assertEquals(LocalDate(2026, 9, 1), result.newFreezesResetAt)
    }

    @Test
    fun todayXpResetsWhenLastCachedDayWasNotToday() {
        val result = StreakCalculator.compute(
            baseInput(
                currentStreak = 2,
                lastPracticeDate = today.minus(DatePeriod(days = 1)),
                todayXp = 50,
                todayDate = "2026-09-08",
            ),
        )
        assertEquals(15, result.newTodayXp, "todayXp uit een vorige dag mag niet blijven meetellen")
    }
}
