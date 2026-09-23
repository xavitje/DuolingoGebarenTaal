package io.github.xavitje.signcoach.db

import app.cash.sqldelight.db.SqlDriver

/** Zero Trust / offline-first: platform-specifieke SQLite-driver voor de lokale database die
 * als single source of truth dient voor XP/streak (zie OfflineProgressRepository). */
expect object DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
