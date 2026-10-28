package pe.upeu.ecorecicla.domain.model

data class Pesaje(
    val id: Long,                // negativo mientras este pendiente de envio
    val puntoId: Long,
    val fechaHora: String,       // ISO-8601
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,        // lo calcula el servicio
    val operario: String,
    val estado: EstadoPesaje,
    val pendiente: Boolean,
    // Agregados (la seccion 4.5 lo permite): el servicio necesita la ruta y la observacion.
    val rutaId: Long = 0,
    val observacion: String = "",
    /** SC-C: porcentaje estimado de humedad (0 a 100). */
    val humedadEstimada: Double = 0.0
)
