package io.github.xavitje.signcoach.data.repository

import io.github.xavitje.signcoach.data.remote.CrashReportApi
import io.github.xavitje.signcoach.db.LocalDatabase
import io.github.xavitje.signcoach.db.PendingCrashReport
import io.github.xavitje.signcoach.db.SignCoachDatabase
import kotlinx.datetime.Clock

/**
 * Offline-first opslag voor crashrapporten. Elke crash wordt EERST synchroon lokaal weggeschreven
 * (zie [recordCrashLocally], aangeroepen vanuit [io.github.xavitje.signcoach.crash.CrashReporter] in de
 * globale uncaught-exception handler — dit MAG nooit falen of wachten op netwerk, het proces kan
 * elk moment beëindigd worden) en pas daarna, zodra er weer verbinding is, verstuurd naar Supabase
 * via [flushPending]. Zelfde patroon als [OfflineProgressRepository]/[io.github.xavitje.signcoach.sync.SyncManager]:
 * een rapport verdwijnt pas uit de wachtrij na een bevestigd succesvol antwoord van de server.
 */
class CrashReportRepository(
    db: SignCoachDatabase = LocalDatabase.instance(),
    private val api: CrashReportApi = CrashReportApi(),
) {
    private val queries = db.crashReportsQueries

    /** Synchroon en "fail-safe": mag zelf nooit een exception gooien, ook niet als de database
     * onverhoopt niet beschikbaar is (bv. schijf vol) — anders zou het rapporteren van een crash
     * zelf een nieuwe crash kunnen veroorzaken. */
    fun recordCrashLocally(
        userId: String?,
        platform: String,
        appVersion: String?,
        osVersion: String?,
        deviceModel: String?,
        errorType: String?,
        errorMessage: String?,
        stackTrace: String,
        isFatal: Boolean,
    ) {
        runCatching {
            queries.insertPendingCrashReport(
                userId = userId,
                platform = platform,
                appVersion = appVersion,
                osVersion = osVersion,
                deviceModel = deviceModel,
                errorType = errorType,
                errorMessage = errorMessage,
                stackTrace = stackTrace,
                isFatal = if (isFatal) 1L else 0L,
                clientCreatedAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    fun pendingCount(): Int =
        runCatching { queries.countPendingCrashReports().executeAsOne() }.getOrDefault(0L).toInt()

    /** Speelt de wachtrij in volgorde af. [accessToken] mag null zijn (bv. crash vóór inloggen of
     * een verlopen sessie) — de report wordt dan anoniem verstuurd. Stopt bij de eerste mislukking
     * zodat de rest van de wachtrij bewaard blijft voor een volgende poging. */
    suspend fun flushPending(accessToken: String?) {
        val pending = runCatching { queries.selectAllPendingCrashReports().executeAsList() }.getOrDefault(emptyList())
        for (item in pending) {
            val result = api.reportCrash(accessToken, item.toBody())
            if (result.isSuccess) {
                runCatching { queries.deletePendingCrashReportById(item.id) }
            } else {
                return
            }
        }
    }
}

private fun PendingCrashReport.toBody(): CrashReportApi.CrashReportBody = CrashReportApi.CrashReportBody(
    userId = userId,
    platform = platform,
    appVersion = appVersion,
    osVersion = osVersion,
    deviceModel = deviceModel,
    errorType = errorType,
    errorMessage = errorMessage,
    stackTrace = stackTrace,
    isFatal = isFatal == 1L,
)
