package io.github.xavitje.signcoach.db

/** Eén gedeelde databaseverbinding voor de hele app (zie DatabaseDriverFactory). */
object LocalDatabase {
    private var instance: SignCoachDatabase? = null

    @Synchronized
    fun instance(): SignCoachDatabase {
        return instance ?: SignCoachDatabase(DatabaseDriverFactory.createDriver()).also { instance = it }
    }
}
