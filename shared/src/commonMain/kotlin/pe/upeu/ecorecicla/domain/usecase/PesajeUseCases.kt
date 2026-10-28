package pe.upeu.ecorecicla.domain.usecase

import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.repository.PesajeRepository

class ObservarPesajesUseCase(private val repo: PesajeRepository) {
    operator fun invoke(puntoId: Long) = repo.observarDePunto(puntoId)
    fun pendientes() = repo.observarCantidadPendientes()
}

/**
 * Validacion local para que el formulario funcione sin red. Es solo ayuda al usuario:
 * el servicio vuelve a validar y calcula el neto (RN-03).
 */
fun validarPesaje(p: Pesaje): Map<String, String> = buildMap {
    if (p.material.isBlank()) put("material", "Elija el material")
    if (p.pesoBruto <= 0) put("pesoBruto", "El peso bruto debe ser mayor que cero")
    if (p.tara < 0) put("tara", "La tara no puede ser negativa")
    else if (p.pesoBruto > 0 && p.pesoBruto - p.tara <= 0) put("tara", "La tara debe ser menor que el peso bruto")
    if (p.humedadEstimada !in 0.0..100.0) put("humedadEstimada", "La humedad debe estar entre 0 y 100 %")
    if (p.observacion.length > 250) put("observacion", "Maximo 250 caracteres")
}

class RegistrarPesajeUseCase(private val repo: PesajeRepository) {
    suspend operator fun invoke(p: Pesaje) = runCatching {
        validarPesaje(p).takeIf { it.isNotEmpty() }?.let { throw ErrorApiException(ErrorApi.Validacion(it)) }
        repo.registrar(p)
    }
}

class CorregirPesajeUseCase(private val repo: PesajeRepository) {
    suspend operator fun invoke(p: Pesaje) = runCatching {
        validarPesaje(p).takeIf { it.isNotEmpty() }?.let { throw ErrorApiException(ErrorApi.Validacion(it)) }
        repo.corregir(p)
    }
}

class AnularPesajeUseCase(private val repo: PesajeRepository) {
    suspend operator fun invoke(id: Long, motivo: String) = runCatching {
        if (motivo.isBlank()) throw ErrorApiException(ErrorApi.Validacion(mapOf("motivo" to "Indique el motivo")))
        repo.anular(id, motivo.trim())
    }
}
