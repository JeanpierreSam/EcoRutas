package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.data.local.AlmacenSeguro

/**
 * Unica instancia de HttpClient de la app (Koin la registra como single).
 * La cabecera Authorization se agrega AQUI, en un solo punto, para todas las peticiones:
 * se lee el token en cada una, asi el login y el cierre de sesion surten efecto al instante.
 */
fun crearClienteHttp(
    engine: HttpClientEngine,
    urlBase: String,
    almacen: AlmacenSeguro,
    esDepuracion: Boolean
): HttpClient = HttpClient(engine) {
    expectSuccess = true                       // 4xx/5xx llegan como excepcion a MapeoErrores
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(createClientPlugin("TokenJwt") {
        onRequest { peticion, _ ->
            if (!peticion.url.buildString().endsWith("auth/login")) {
                almacen.leerToken()?.let { peticion.headers[HttpHeaders.Authorization] = "Bearer $it" }
            }
        }
    })
    install(HttpTimeout) {
        connectTimeoutMillis = ApiConfig.TIEMPO_CONEXION_MS
        requestTimeoutMillis = ApiConfig.TIEMPO_PETICION_MS
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) = println("EcoAPI: $message")
        }
        // Registro reducido: cabeceras solo en depuracion, nada en produccion; el token nunca se imprime.
        level = if (esDepuracion) LogLevel.HEADERS else LogLevel.NONE
        sanitizeHeader { it == HttpHeaders.Authorization }
    }
    defaultRequest {
        url(urlBase)
        contentType(ContentType.Application.Json)
    }
}
