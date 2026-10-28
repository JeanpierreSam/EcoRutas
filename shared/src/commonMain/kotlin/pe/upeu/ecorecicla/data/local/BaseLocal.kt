package pe.upeu.ecorecicla.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.upeu.ecorecicla.db.EcoDb
import pe.upeu.ecorecicla.db.ListarPuntos
import pe.upeu.ecorecicla.db.ObtenerPunto
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.FiltroPuntos
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta

/** Cada plataforma aporta su driver de SQLite (Android: BaseLocalAndroid). */
fun interface BaseLocal {
    fun crearDriver(): SqlDriver
}

/**
 * Acceso a la base local. Las consultas de listado devuelven Flow: cuando una
 * escritura toca la tabla, SQLDelight vuelve a emitir y la pantalla se actualiza sola.
 */
class DatosLocales(private val db: EcoDb) {

    private val io = Dispatchers.IO

    fun observarRuta(): Flow<Ruta?> = db.rutaQueries.obtenerRuta { id, fecha, cerrada -> Ruta(id, fecha, cerrada == 1L) }
        .asFlow().mapToOneOrNull(io)

    fun observarPuntos(filtro: FiltroPuntos = FiltroPuntos()): Flow<List<PuntoAcopio>> =
        db.puntoQueries.listarPuntos(filtro.texto.trim(), if (filtro.soloPendientes) 1L else 0L).asFlow().mapToList(io)
        .map { filas -> filas.map { it.aDominio() } }

    fun observarPunto(id: Long): Flow<PuntoAcopio?> = db.puntoQueries.obtenerPunto(id).asFlow().mapToOneOrNull(io)
        .map { it?.aDominio() }

    fun observarPesajes(puntoId: Long): Flow<List<Pesaje>> =
        db.pesajeQueries.listarDePunto(puntoId, ::filaAPesaje).asFlow().mapToList(io)

    fun observarCantidadPendientes(): Flow<Long> = db.pesajeQueries.contarPendientes().asFlow().mapToOne(io)

    fun observarUltimaSincronizacion(): Flow<Long?> = db.sincronizacionQueries.leerUltima().asFlow().mapToOneOrNull(io)

    fun ruta(): Ruta? = db.rutaQueries.obtenerRuta { id, fecha, cerrada -> Ruta(id, fecha, cerrada == 1L) }.executeAsOneOrNull()

    fun pendientes(): List<Pesaje> = db.pesajeQueries.listarPendientes(::filaAPesaje).executeAsList()

    fun obtenerPesaje(id: Long): Pesaje? = db.pesajeQueries.obtenerPesaje(id, ::filaAPesaje).executeAsOneOrNull()

    /** Guarda un pesaje nuevo con identificador provisional negativo y marca de pendiente. */
    fun guardarPendiente(p: Pesaje): Pesaje = db.transactionWithResult {
        val id = db.pesajeQueries.siguienteIdLocal().executeAsOne().expr ?: -1L
        p.copy(id = id, pendiente = true).also(::guardar)
    }

    fun guardar(p: Pesaje) = db.pesajeQueries.guardarPesaje(
        p.id, p.rutaId, p.puntoId, p.fechaHora, p.material, p.pesoBruto, p.tara, p.pesoNeto, p.operario,
        p.observacion, if (p.estado is EstadoPesaje.Anulado) 1L else 0L,
        (p.estado as? EstadoPesaje.Anulado)?.motivo, if (p.pendiente) 1L else 0L
    )

    /** La fila provisional se sustituye por la definitiva en la misma transaccion: ni duplicada ni perdida. */
    fun confirmarEnvio(idLocal: Long, definitivo: Pesaje) = db.transaction {
        db.pesajeQueries.borrarPesaje(idLocal)
        guardar(definitivo.copy(pendiente = false))
    }

    fun borrarPesaje(id: Long) = db.pesajeQueries.borrarPesaje(id)

    fun cerrarRuta(ruta: Ruta) = db.rutaQueries.guardarRuta(ruta.id, ruta.fecha, 1L)

    /**
     * Reemplaza lo que ya conoce el servicio por su version actual. Lo pendiente
     * sobrevive: borrarSincronizados no lo toca y guardarSiNoExiste no lo pisa.
     */
    fun reemplazar(ruta: Ruta?, puntos: List<PuntoAcopio>, omitidos: Set<Long>, pesajes: List<Pesaje>, instante: Long) =
        db.transaction {
            db.rutaQueries.borrarRutas()
            db.puntoQueries.borrarPuntos()
            db.pesajeQueries.borrarSincronizados()
            if (ruta != null) {
                db.rutaQueries.guardarRuta(ruta.id, ruta.fecha, if (ruta.cerrada) 1L else 0L)
                puntos.forEach {
                    db.puntoQueries.insertarPunto(it.id, ruta.id, it.razonSocial, it.direccion, it.zona,
                        it.materialHabitual, it.secuencia.toLong(), if (it.id in omitidos) 1L else 0L)
                }
            }
            pesajes.forEach { p ->
                db.pesajeQueries.guardarSiNoExiste(p.id, p.rutaId, p.puntoId, p.fechaHora, p.material, p.pesoBruto,
                    p.tara, p.pesoNeto, p.operario, p.observacion, if (p.estado is EstadoPesaje.Anulado) 1L else 0L,
                    (p.estado as? EstadoPesaje.Anulado)?.motivo)
            }
            db.sincronizacionQueries.guardarUltima(instante)
        }

    /** Turno siguiente: el equipo queda sin datos del usuario anterior. */
    fun limpiarTodo() = db.transaction {
        db.rutaQueries.borrarRutas()
        db.puntoQueries.borrarPuntos()
        db.pesajeQueries.borrarPesajes()
        db.sincronizacionQueries.borrarSincronizacion()
    }
}

private fun filaAPesaje(
    id: Long, rutaId: Long, puntoId: Long, fechaHora: String, material: String, pesoBruto: Double, tara: Double,
    pesoNeto: Double, operario: String, observacion: String, anulado: Long, motivo: String?, pendiente: Long
) = Pesaje(
    id = id, puntoId = puntoId, fechaHora = fechaHora, material = material, pesoBruto = pesoBruto, tara = tara,
    pesoNeto = pesoNeto, operario = operario,
    estado = if (anulado == 1L) EstadoPesaje.Anulado(motivo.orEmpty()) else EstadoPesaje.Registrado,
    pendiente = pendiente == 1L, rutaId = rutaId, observacion = observacion
)

private fun ListarPuntos.aDominio() =
    PuntoAcopio(id, razon_social, direccion, zona, material_habitual, secuencia.toInt(), atendido == true)

private fun ObtenerPunto.aDominio() =
    PuntoAcopio(id, razon_social, direccion, zona, material_habitual, secuencia.toInt(), atendido == true)
