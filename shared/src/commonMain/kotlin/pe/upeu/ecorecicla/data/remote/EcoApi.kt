package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import pe.upeu.ecorecicla.data.remote.dto.CorreccionRequestDto
import pe.upeu.ecorecicla.data.remote.dto.LoginRequestDto
import pe.upeu.ecorecicla.data.remote.dto.LoginResponseDto
import pe.upeu.ecorecicla.data.remote.dto.PaginaDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeRequestDto
import pe.upeu.ecorecicla.data.remote.dto.RutaDto

/** Endpoints de EcoAPI (rutas relativas a ApiConfig). Las fallas las traduce EjecutorApi. */
class EcoApi(private val http: HttpClient, private val ejecutar: EjecutorApi) {

    suspend fun login(usuario: String, clave: String): LoginResponseDto = ejecutar {
        http.post("auth/login") { setBody(LoginRequestDto(usuario, clave)) }.body()
    }

    suspend fun rutaDelDia(): RutaDto = ejecutar { http.get("rutas/del-dia").body() }

    suspend fun cerrarRuta(id: Long): RutaDto = ejecutar { http.post("rutas/$id/cierre").body() }

    suspend fun pesajesDePunto(puntoId: Long): PaginaDto<PesajeDto> = ejecutar {
        http.get("puntos/$puntoId/pesajes") {
            parameter("pagina", 0)
            parameter("tamanio", ApiConfig.TAMANIO_PAGINA)
        }.body()
    }

    /** 201 con el id definitivo y el neto calculado por el servicio. */
    suspend fun registrar(p: PesajeRequestDto): PesajeDto = ejecutar { http.post("pesajes") { setBody(p) }.body() }

    suspend fun corregir(id: Long, p: CorreccionRequestDto): PesajeDto =
        ejecutar { http.put("pesajes/$id") { setBody(p) }.body() }

    /** 204 sin cuerpo: solo se comprueba el codigo (expectSuccess), no se intenta leer nada. */
    suspend fun anular(id: Long, motivo: String) {
        ejecutar { http.delete("pesajes/$id") { parameter("motivo", motivo) } }
    }
}
