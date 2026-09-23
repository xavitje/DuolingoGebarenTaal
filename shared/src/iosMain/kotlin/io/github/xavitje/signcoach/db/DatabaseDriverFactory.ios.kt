package io.github.xavitje.signcoach.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual object DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(SignCoachDatabase.Schema, "signcoach.db")
    }
}
