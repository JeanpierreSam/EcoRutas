package pe.upeu.ecorecicla

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import pe.upeu.ecorecicla.db.EcoDb

/** JVM: SQLite en memoria con el driver JDBC; el esquema se crea a mano. */
actual fun crearDriverDePrueba(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EcoDb.Schema.create(it) }
