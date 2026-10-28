package pe.upeu.ecorecicla.presentation

import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException

fun Throwable.aErrorApi(): ErrorApi = (this as? ErrorApiException)?.error ?: ErrorApi.Servidor

/** Texto para el usuario de cada error. La conducta (cerrar sesion, reintentar...) la decide cada pantalla. */
fun ErrorApi.mensaje(): String = when (this) {
    is ErrorApi.Validacion -> mensaje
    is ErrorApi.NoAutenticado -> mensaje
    is ErrorApi.SinPrivilegio -> "Operacion no autorizada: $mensaje"
    is ErrorApi.NoEncontrado -> mensaje
    is ErrorApi.Conflicto -> mensaje
    ErrorApi.Servidor -> "El servidor tuvo un problema"
    ErrorApi.SinConexion -> "Sin conexion con el servidor"
    ErrorApi.TiempoAgotado -> "El servidor no respondio a tiempo"
}

/** Mensaje de pantalla; [reintentar] solo para el 500 y la falta de red. */
data class Aviso(val texto: String, val reintentar: Boolean = false)

fun ErrorApi.aviso() = Aviso(
    mensaje(),
    reintentar = this == ErrorApi.Servidor || this == ErrorApi.SinConexion || this == ErrorApi.TiempoAgotado
)
