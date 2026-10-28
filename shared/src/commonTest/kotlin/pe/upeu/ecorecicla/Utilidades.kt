package pe.upeu.ecorecicla

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.DatosLocales
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.EjecutorApi
import pe.upeu.ecorecicla.data.remote.crearClienteHttp
import pe.upeu.ecorecicla.db.EcoDb
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje

/** Driver en memoria: cada plataforma de prueba aporta el suyo (JVM: JdbcSqliteDriver). */
expect fun crearDriverDePrueba(): SqlDriver

/** Base NUEVA por prueba, para que ninguna herede datos de otra. */
fun baseEnMemoria() = DatosLocales(EcoDb(crearDriverDePrueba()))

class AlmacenEnMemoria(var token: String? = "token-de-prueba") : AlmacenSeguro {
    override fun guardarToken(token: String) { this.token = token }
    override fun leerToken() = token
    override fun borrarToken() { token = null }
}

/** EcoApi real sobre el motor HTTP simulado: se prueban DTO, cabecera y traduccion de errores. */
fun apiSimulada(alNoAutenticado: () -> Unit = {}, manejador: MockRequestHandler) = EcoApi(
    crearClienteHttp(MockEngine(manejador), "http://eco.test/api/v1/", AlmacenEnMemoria(), esDepuracion = false),
    EjecutorApi(alNoAutenticado)
)

fun pesajeNuevo(puntoId: Long = 10) = Pesaje(
    id = 0, puntoId = puntoId, fechaHora = "2026-10-28T10:00:00Z", material = "CARTON", pesoBruto = 50.0,
    tara = 5.0, pesoNeto = 45.0, operario = "operario1", estado = EstadoPesaje.Registrado, pendiente = true, rutaId = 2
)

const val JSON_PESAJE_201 = """{"id":100,"rutaId":2,"puntoId":10,"fechaHora":"2026-10-28T10:00:05","material":"CARTON",
"pesoBruto":50.0,"tara":5.0,"pesoNeto":45.0,"operario":"operario1","observacion":null,"anulado":false,"motivoAnulacion":null}"""
