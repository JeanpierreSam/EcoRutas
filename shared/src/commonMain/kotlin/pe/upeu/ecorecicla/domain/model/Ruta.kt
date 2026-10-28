package pe.upeu.ecorecicla.domain.model

data class Ruta(
    val id: Long,
    val fecha: String,
    val cerrada: Boolean
)

data class ResumenSincronizacion(
    val enviados: Int,
    val rechazados: Int,
    val recibidos: Int
)
