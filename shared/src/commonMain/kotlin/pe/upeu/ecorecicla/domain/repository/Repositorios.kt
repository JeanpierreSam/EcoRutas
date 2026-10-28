package pe.upeu.ecorecicla.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import pe.upeu.ecorecicla.domain.model.FiltroPuntos
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.model.Sesion

interface SesionRepository {
    /** null = sin sesion: la app muestra el inicio de sesion. */
    val sesion: StateFlow<Sesion?>
    suspend fun iniciar(usuario: String, contrasena: String): Sesion
    /** Recupera el token guardado si sigue vigente; si vencio, lo borra. */
    fun restaurar(): Sesion?
    /** Cierre de sesion: borra el token y los datos locales para el siguiente turno. */
    fun cerrar()
    /** 401 del servicio: se borra el token y se vuelve al login, conservando lo pendiente. */
    fun expirar()
}

interface RutaRepository {
    fun observarRuta(): Flow<Ruta?>
    fun observarPuntos(filtro: FiltroPuntos = FiltroPuntos()): Flow<List<PuntoAcopio>>
    fun observarPunto(id: Long): Flow<PuntoAcopio?>
    fun observarUltimaSincronizacion(): Flow<Long?>
    /** Baja la ruta del dia y los pesajes de sus puntos. Devuelve cuantos pesajes recibio. */
    suspend fun descargar(): Int
    suspend fun cerrar(rutaId: Long)
}

interface PesajeRepository {
    fun observarDePunto(puntoId: Long): Flow<List<Pesaje>>
    fun observarCantidadPendientes(): Flow<Long>
    suspend fun registrar(pesaje: Pesaje): Pesaje
    suspend fun corregir(pesaje: Pesaje): Pesaje
    suspend fun anular(id: Long, motivo: String)
    /** Envia lo pendiente. Devuelve (enviados, rechazados). */
    suspend fun enviarPendientes(): Pair<Int, Int>
}
