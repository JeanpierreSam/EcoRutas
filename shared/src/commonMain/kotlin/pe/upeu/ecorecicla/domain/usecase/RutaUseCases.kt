package pe.upeu.ecorecicla.domain.usecase

import pe.upeu.ecorecicla.domain.repository.RutaRepository

class ObservarRutaUseCase(private val repo: RutaRepository) {
    fun ruta() = repo.observarRuta()
    fun puntos() = repo.observarPuntos()
    fun punto(id: Long) = repo.observarPunto(id)
    fun ultimaSincronizacion() = repo.observarUltimaSincronizacion()
}

/** RN-04 la decide el servicio: aqui solo se pide el cierre y se propaga su motivo. */
class CerrarRutaUseCase(private val repo: RutaRepository) {
    suspend operator fun invoke(rutaId: Long) = runCatching { repo.cerrar(rutaId) }
}
