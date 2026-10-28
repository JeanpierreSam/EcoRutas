package pe.upeu.ecorecicla.data.repository

import pe.upeu.ecorecicla.data.local.DatosLocales
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.dto.aCorreccion
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.data.remote.dto.aPeticion
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.repository.PesajeRepository

/**
 * Offline-first: todo cambio se escribe primero en la base local (la pantalla ya lo ve)
 * y luego se intenta enviar. Sin red, la fila queda marcada como pendiente.
 */
class PesajeRepositorioOffline(
    private val local: DatosLocales,
    private val api: EcoApi
) : PesajeRepository {

    override fun observarDePunto(puntoId: Long) = local.observarPesajes(puntoId)
    override fun observarCantidadPendientes() = local.observarCantidadPendientes()

    override suspend fun registrar(pesaje: Pesaje): Pesaje {
        val pendiente = local.guardarPendiente(pesaje)          // id negativo, pendiente = 1
        return try {
            crearEnServidor(pendiente)
        } catch (e: ErrorApiException) {
            if (!e.error.esperaConexion()) {
                // El servicio lo rechazo (400, 403, 409): no se guarda y el formulario muestra el motivo.
                local.borrarPesaje(pendiente.id)
                throw e
            }
            pendiente                                           // se enviara al sincronizar
        }
    }

    override suspend fun corregir(pesaje: Pesaje): Pesaje {
        if (pesaje.id < 0) {                                    // aun no existe en el servicio
            return pesaje.copy(pendiente = true).also(local::guardar)
        }
        val anterior = local.obtenerPesaje(pesaje.id)
        local.guardar(pesaje.copy(pendiente = true))
        return try {
            api.corregir(pesaje.id, pesaje.aCorreccion()).aDominio().also(local::guardar)
        } catch (e: ErrorApiException) {
            if (!e.error.esperaConexion()) {
                anterior?.let(local::guardar)                   // se deshace la correccion rechazada
                throw e
            }
            pesaje.copy(pendiente = true)
        }
    }

    /** La anulacion exige red: es una decision del supervisor que el servicio debe validar en el momento. */
    override suspend fun anular(id: Long, motivo: String) {
        if (id < 0) {                                           // nunca llego al servicio
            local.borrarPesaje(id)
            return
        }
        api.anular(id, motivo)
        local.obtenerPesaje(id)?.let { local.guardar(it.copy(estado = EstadoPesaje.Anulado(motivo), pendiente = false)) }
    }

    override suspend fun enviarPendientes(): Pair<Int, Int> {
        var enviados = 0
        var rechazados = 0
        for (p in local.pendientes()) {
            try {
                if (p.id < 0) crearEnServidor(p)
                else local.guardar(api.corregir(p.id, p.aCorreccion()).aDominio())
                enviados++
            } catch (e: ErrorApiException) {
                // Sin red a mitad de camino: se corta y lo que falta se reintenta despues.
                if (e.error.esperaConexion()) throw e
                // 400/409: queda pendiente para que el usuario lo corrija; no bloquea al resto.
                rechazados++
            }
        }
        return enviados to rechazados
    }

    private suspend fun crearEnServidor(p: Pesaje): Pesaje =
        api.registrar(p.aPeticion()).aDominio().also { local.confirmarEnvio(p.id, it) }

    /** Fallos de conectividad o de sesion: el dato es valido, solo hay que esperar. */
    private fun ErrorApi.esperaConexion() = when (this) {
        ErrorApi.SinConexion, ErrorApi.TiempoAgotado, ErrorApi.Servidor, is ErrorApi.NoAutenticado -> true
        is ErrorApi.Validacion, is ErrorApi.SinPrivilegio, is ErrorApi.NoEncontrado, is ErrorApi.Conflicto -> false
    }
}
