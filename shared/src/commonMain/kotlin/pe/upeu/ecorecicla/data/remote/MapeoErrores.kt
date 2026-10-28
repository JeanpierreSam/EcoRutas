package pe.upeu.ecorecicla.data.remote

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import pe.upeu.ecorecicla.data.remote.dto.ErrorDto
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException

/**
 * Unico lugar donde una falla HTTP se convierte en ErrorApi. Ante un 401 avisa a
 * [alNoAutenticado] (borra el token y vuelve al login) antes de propagar el error.
 */
class EjecutorApi(private val alNoAutenticado: () -> Unit) {

    suspend operator fun <T> invoke(bloque: suspend () -> T): T = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val error = traducir(e)
        if (error is ErrorApi.NoAutenticado) alNoAutenticado()
        throw ErrorApiException(error)
    }
}

suspend fun traducir(e: Throwable): ErrorApi = when (e) {
    is ErrorApiException -> e.error
    is ClientRequestException -> {
        val cuerpo = runCatching { e.response.body<ErrorDto>() }.getOrNull()
        val mensaje = cuerpo?.message
        when (e.response.status.value) {
            400 -> ErrorApi.Validacion(cuerpo?.validationErrors.orEmpty(), mensaje ?: "Datos invalidos")
            401 -> ErrorApi.NoAutenticado(mensaje ?: "Su sesion vencio. Inicie sesion nuevamente")
            403 -> ErrorApi.SinPrivilegio(mensaje ?: "No tiene el privilegio para esta operacion")
            404 -> ErrorApi.NoEncontrado(mensaje ?: "El recurso no existe")
            409 -> ErrorApi.Conflicto(mensaje ?: "Operacion no permitida")
            else -> ErrorApi.Servidor
        }
    }
    is ServerResponseException -> ErrorApi.Servidor
    is HttpRequestTimeoutException -> ErrorApi.TiempoAgotado
    is IOException -> ErrorApi.SinConexion            // sin red, servicio apagado, DNS...
    is ContentConvertException, is SerializationException -> ErrorApi.Servidor
    else -> ErrorApi.Servidor
}
