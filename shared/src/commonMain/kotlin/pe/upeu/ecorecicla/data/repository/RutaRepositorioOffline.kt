package pe.upeu.ecorecicla.data.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.data.local.DatosLocales
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.repository.RutaRepository

/** La base local es la fuente de verdad; el servicio solo la mantiene al dia. */
class RutaRepositorioOffline(
    private val local: DatosLocales,
    private val api: EcoApi,
    private val reloj: () -> Long
) : RutaRepository {

    override fun observarRuta() = local.observarRuta()
    override fun observarPuntos() = local.observarPuntos()
    override fun observarPunto(id: Long) = local.observarPunto(id)
    override fun observarUltimaSincronizacion(): Flow<Long?> = local.observarUltimaSincronizacion()

    override suspend fun descargar(): Int {
        val ruta = try {
            api.rutaDelDia()
        } catch (e: ErrorApiException) {
            if (e.error !is ErrorApi.NoEncontrado) throw e
            null                                            // sin ruta asignada hoy: lista vacia
        }
        val pesajes = ruta?.puntos.orEmpty().flatMap { api.pesajesDePunto(it.id).contenido }.map { it.aDominio() }
        local.reemplazar(
            ruta = ruta?.aDominio(),
            puntos = ruta?.puntos.orEmpty().map { it.aDominio() },
            omitidos = ruta?.puntos.orEmpty().filter { it.motivoOmision != null }.map { it.id }.toSet(),
            pesajes = pesajes,
            instante = reloj()
        )
        return pesajes.size
    }

    override suspend fun cerrar(rutaId: Long) {
        local.cerrarRuta(api.cerrarRuta(rutaId).aDominio())
    }
}
