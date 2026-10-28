package pe.upeu.ecorecicla

import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.data.remote.dto.PaginaDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeRequestDto
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.data.repository.PesajeRepositorioOffline
import pe.upeu.ecorecicla.data.repository.RutaRepositorioOffline
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val json = headersOf(HttpHeaders.ContentType, "application/json")

class EcoRutasTest {

    /** 1. Conversion de DTO: envoltorio de paginacion, clave desconocida tolerada y anulado -> EstadoPesaje.Anulado. */
    @Test
    fun conversionDeDtoAlDominio() {
        val cuerpo = """{"contenido":[{"id":15,"rutaId":2,"puntoId":9,"fechaHora":"2026-10-28T09:22","material":"PAPEL",
            "pesoBruto":55.0,"tara":5.0,"pesoNeto":50.0,"operario":"operario1","anulado":true,
            "motivoAnulacion":"Duplicado","claveNueva":"x"}],"pagina":0,"tamanio":20,"totalElementos":1,"totalPaginas":1,"ultima":true}"""
        val pagina = Json { ignoreUnknownKeys = true }.decodeFromString<PaginaDto<PesajeDto>>(cuerpo)
        val pesaje = pagina.contenido.single().aDominio()

        assertEquals(15, pesaje.id)
        assertEquals("operario1", pesaje.operario)
        assertEquals(50.0, pesaje.pesoNeto)
        assertEquals(EstadoPesaje.Anulado("Duplicado"), pesaje.estado)
        assertFalse(pesaje.pendiente)
    }

    /** 2. Traduccion de errores: 400 trae el detalle por campo; 403 no cierra sesion; 401 si. */
    @Test
    fun traduccionDeErroresHttp() = runTest {
        var expiro = false
        val api400 = apiSimulada({ expiro = true }) {
            respond("""{"status":400,"message":"Datos invalidos","validationErrors":{"tara":"La tara debe ser menor"}}""",
                HttpStatusCode.BadRequest, json)
        }
        val e400 = assertFailsWith<ErrorApiException> { api400.registrar(PesajeRequestDto(2, 10, "X", 1.0, 2.0, "")) }
        assertEquals(ErrorApi.Validacion(mapOf("tara" to "La tara debe ser menor"), "Datos invalidos"), e400.error)

        val api403 = apiSimulada({ expiro = true }) { respond("""{"status":403,"message":"Sin privilegio"}""", HttpStatusCode.Forbidden, json) }
        assertIs<ErrorApi.SinPrivilegio>(assertFailsWith<ErrorApiException> { api403.anular(1, "x") }.error)
        assertFalse(expiro, "un 403 mantiene la sesion")

        val api401 = apiSimulada({ expiro = true }) { respondError(HttpStatusCode.Unauthorized) }
        assertIs<ErrorApi.NoAutenticado>(assertFailsWith<ErrorApiException> { api401.rutaDelDia() }.error)
        assertTrue(expiro, "un 401 borra la sesion")
    }

    /** 3. Registro sin conexion: queda en la base local, pendiente y con id provisional negativo. */
    @Test
    fun registroSinConexionQuedaPendiente() = runTest {
        val local = baseEnMemoria()
        val repo = PesajeRepositorioOffline(local, apiSimulada { throw IOException("modo avion") })

        val primero = repo.registrar(pesajeNuevo())
        val segundo = repo.registrar(pesajeNuevo())

        assertEquals(-1, primero.id)
        assertEquals(-2, segundo.id)
        assertTrue(primero.pendiente)
        assertEquals(listOf(-1L, -2L), local.pendientes().map { it.id })
    }

    /** 4. Orden de la sincronizacion: primero sube lo pendiente y despues baja; sin duplicar ni perder filas. */
    @Test
    fun sincronizacionSubeAntesDeBajar() = runTest {
        val local = baseEnMemoria()
        PesajeRepositorioOffline(local, apiSimulada { throw IOException("sin red") }).registrar(pesajeNuevo())

        val llamadas = mutableListOf<String>()
        val api = apiSimulada { req ->
            llamadas += "${req.method.value} ${req.url.encodedPath}"
            when {
                req.method == HttpMethod.Post -> respond(JSON_PESAJE_201, HttpStatusCode.Created, json)
                req.url.encodedPath.endsWith("rutas/del-dia") -> respond(
                    """{"id":2,"fecha":"2026-10-28","estado":"ABIERTA","puntos":[{"id":10,"razonSocial":"Restaurante",
                    "direccion":"Av. Brasil","zona":"CENTRO","materialHabitual":"PLASTICO","secuencia":4,"atendido":true}]}""",
                    HttpStatusCode.OK, json)
                else -> respond("""{"contenido":[$JSON_PESAJE_201],"pagina":0,"tamanio":50,"totalElementos":1,
                    "totalPaginas":1,"ultima":true}""", HttpStatusCode.OK, json)
            }
        }
        val resumen = SincronizarUseCase(PesajeRepositorioOffline(local, api), RutaRepositorioOffline(local, api) { 1L })()
            .getOrThrow()

        assertEquals("POST /api/v1/pesajes", llamadas.first())
        assertEquals("GET /api/v1/rutas/del-dia", llamadas[1])
        assertEquals(1, resumen.enviados)
        assertTrue(local.pendientes().isEmpty())
        assertEquals(100, local.obtenerPesaje(100)?.id)
        assertEquals(null, local.obtenerPesaje(-1), "la fila provisional se reemplazo, no se duplico")
    }

    /** 5. La consulta del siguiente id local funciona con la tabla vacia. */
    @Test
    fun siguienteIdLocalConTablaVacia() = runTest {
        val local = baseEnMemoria()
        assertEquals(-1, local.guardarPendiente(pesajeNuevo()).id)
    }
}
