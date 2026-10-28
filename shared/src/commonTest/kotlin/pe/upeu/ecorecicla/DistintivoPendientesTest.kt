package pe.upeu.ecorecicla

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import pe.upeu.ecorecicla.data.repository.PesajeRepositorioOffline
import kotlin.test.Test
import kotlin.test.assertEquals

/** SC-D: el numero del distintivo lo da la base local; nadie lo cuenta a mano. */
class DistintivoPendientesTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun cambiaAlRegistrarSinRedYVuelveACeroAlSincronizar() = runTest {
        val local = baseEnMemoria()
        val pendientes = local.observarCantidadPendientes()
        assertEquals(0, pendientes.first())

        val sinRed = PesajeRepositorioOffline(local, apiSimulada { throw IOException("modo avion") })
        sinRed.registrar(pesajeNuevo())
        sinRed.registrar(pesajeNuevo(puntoId = 11))
        assertEquals(2, pendientes.first())

        var siguienteId = 100
        val conRed = PesajeRepositorioOffline(local, apiSimulada { req ->
            check(req.method == HttpMethod.Post)
            respond(JSON_PESAJE_201.replace("\"id\":100", "\"id\":${siguienteId++}"), HttpStatusCode.Created, json)
        })
        conRed.enviarPendientes()
        assertEquals(0, pendientes.first())
    }

    @Test
    fun unRechazoDelServicioNoDejaElContadorInflado() = runTest {
        val local = baseEnMemoria()
        val repo = PesajeRepositorioOffline(local, apiSimulada {
            respond("""{"status":409,"message":"La ruta esta cerrada"}""", HttpStatusCode.Conflict, json)
        })
        runCatching { repo.registrar(pesajeNuevo()) }
        assertEquals(0, local.observarCantidadPendientes().first())
    }
}
