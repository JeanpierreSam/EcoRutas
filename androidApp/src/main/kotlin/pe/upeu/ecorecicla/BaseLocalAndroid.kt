package pe.upeu.ecorecicla

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.db.EcoDb

/** Driver de SQLDelight para Android: archivo ecorecicla.db en el almacenamiento interno de la app. */
class BaseLocalAndroid(private val contexto: Context) : BaseLocal {
    override fun crearDriver(): SqlDriver = AndroidSqliteDriver(EcoDb.Schema, contexto, "ecorecicla.db")
}
