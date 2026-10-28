package pe.upeu.ecorecicla.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta

/** Envoltorio de todos los listados: por eso un listado no se deserializa como List. */
@Serializable
data class PaginaDto<T>(
    val contenido: List<T>,
    val pagina: Int,
    val tamanio: Int,
    val totalElementos: Long,
    val totalPaginas: Int,
    val ultima: Boolean
)

@Serializable
data class ErrorDto(
    val status: Int = 0,
    val message: String? = null,
    val path: String? = null,
    val validationErrors: Map<String, String>? = null
)

@Serializable
data class LoginRequestDto(
    val usuario: String,
    @SerialName("contrasena") val clave: String
)

@Serializable
data class LoginResponseDto(
    val token: String,
    val usuario: String,
    val nombre: String,
    val rol: String,
    val privilegios: List<String>,
    val expiraEnSegundos: Long = 3600
)

@Serializable
data class PuntoRutaDto(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val secuencia: Int,
    val atendido: Boolean,
    val motivoOmision: String? = null
)

@Serializable
data class RutaDto(
    val id: Long,
    val fecha: String,
    val estado: String,
    val operario: String = "",
    val puntos: List<PuntoRutaDto> = emptyList()
)

@Serializable
data class PesajeDto(
    val id: Long,
    val rutaId: Long,
    val puntoId: Long,
    val fechaHora: String,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,
    @SerialName("operario") val usuario: String,
    val observacion: String? = null,
    val humedadEstimada: Double = 0.0,
    val anulado: Boolean = false,
    val motivoAnulacion: String? = null
)

/** Sin peso neto: lo calcula el servicio (RN-03). */
@Serializable
data class PesajeRequestDto(
    val rutaId: Long,
    val puntoId: Long,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val observacion: String,
    val humedadEstimada: Double
)

@Serializable
data class CorreccionRequestDto(
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val observacion: String,
    val humedadEstimada: Double
)

// ---- Conversion al dominio: el dominio no conoce @Serializable ----

fun RutaDto.aDominio() = Ruta(id = id, fecha = fecha, cerrada = estado == "CERRADA")

fun PuntoRutaDto.aDominio() = PuntoAcopio(id, razonSocial, direccion, zona, materialHabitual, secuencia, atendido)

fun PesajeDto.aDominio() = Pesaje(
    id = id,
    puntoId = puntoId,
    fechaHora = fechaHora,
    material = material,
    pesoBruto = pesoBruto,
    tara = tara,
    pesoNeto = pesoNeto,
    operario = usuario,
    estado = if (anulado) EstadoPesaje.Anulado(motivoAnulacion.orEmpty()) else EstadoPesaje.Registrado,
    pendiente = false,
    rutaId = rutaId,
    observacion = observacion.orEmpty(),
    humedadEstimada = humedadEstimada
)

fun Pesaje.aPeticion() = PesajeRequestDto(rutaId, puntoId, material, pesoBruto, tara, observacion, humedadEstimada)

fun Pesaje.aCorreccion() = CorreccionRequestDto(material, pesoBruto, tara, observacion, humedadEstimada)
