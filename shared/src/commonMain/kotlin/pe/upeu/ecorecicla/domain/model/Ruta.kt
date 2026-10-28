package pe.upeu.ecorecicla.domain.model

data class Ruta(
    val id: Long,
    val fecha: String,
    val cerrada: Boolean
)

/** SC-A: criterio de la ruta del dia; se combina en la consulta local. */
data class FiltroPuntos(
    val texto: String = "",
    val soloPendientes: Boolean = false
)

data class ResumenSincronizacion(
    val enviados: Int,
    val rechazados: Int,
    val recibidos: Int
)
