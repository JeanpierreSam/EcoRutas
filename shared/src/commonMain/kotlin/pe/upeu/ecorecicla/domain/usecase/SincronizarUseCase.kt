package pe.upeu.ecorecicla.domain.usecase

import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion
import pe.upeu.ecorecicla.domain.repository.PesajeRepository
import pe.upeu.ecorecicla.domain.repository.RutaRepository

/**
 * Primero sube lo pendiente y despues baja lo actualizado. Al reves, la bajada
 * llegaria antes que los pesajes hechos sin red y el servidor no los veria a tiempo.
 */
class SincronizarUseCase(
    private val pesajes: PesajeRepository,
    private val rutas: RutaRepository
) {
    suspend operator fun invoke(): Result<ResumenSincronizacion> = runCatching {
        val (enviados, rechazados) = pesajes.enviarPendientes()
        val recibidos = rutas.descargar()
        ResumenSincronizacion(enviados, rechazados, recibidos)
    }
}
