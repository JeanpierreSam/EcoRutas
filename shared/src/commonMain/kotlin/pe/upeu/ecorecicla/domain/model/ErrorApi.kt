package pe.upeu.ecorecicla.domain.model

/** Cada respuesta del servicio tiene su propia conducta en la interfaz (seccion 3.5). */
sealed interface ErrorApi {
    /** 400: mensaje por campo, con las mismas claves que el formulario. */
    data class Validacion(val porCampo: Map<String, String>, val mensaje: String = "Datos invalidos") : ErrorApi
    /** 401: token ausente, alterado o vencido: se cierra la sesion. */
    data class NoAutenticado(val mensaje: String) : ErrorApi
    /** 403: autenticado pero sin el privilegio; la sesion se mantiene. */
    data class SinPrivilegio(val mensaje: String) : ErrorApi
    data class NoEncontrado(val mensaje: String) : ErrorApi
    /** 409: regla de negocio del servicio; se muestra su motivo. */
    data class Conflicto(val mensaje: String) : ErrorApi
    data object Servidor : ErrorApi
    data object SinConexion : ErrorApi
    data object TiempoAgotado : ErrorApi
}

class ErrorApiException(val error: ErrorApi) : Exception(error.toString())
