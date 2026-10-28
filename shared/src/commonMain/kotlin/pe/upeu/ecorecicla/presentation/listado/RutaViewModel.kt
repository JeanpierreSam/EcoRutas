package pe.upeu.ecorecicla.presentation.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.FiltroPuntos
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.repository.MonitorRed
import pe.upeu.ecorecicla.domain.repository.SesionRepository
import pe.upeu.ecorecicla.domain.usecase.CerrarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import pe.upeu.ecorecicla.presentation.Aviso
import pe.upeu.ecorecicla.presentation.aErrorApi
import pe.upeu.ecorecicla.presentation.aviso

data class RutaUiState(
    val cargando: Boolean = true,
    val ruta: Ruta? = null,
    val puntos: List<PuntoAcopio> = emptyList(),
    val ultimaSincronizacion: Long? = null,
    val pendientes: Long = 0,
    val sincronizando: Boolean = false,
    val conectado: Boolean = true,
    val aviso: Aviso? = null,
    val filtro: FiltroPuntos = FiltroPuntos()
)

private data class Transitorio(
    val sincronizando: Boolean = false,
    val conectado: Boolean = true,
    val aviso: Aviso? = null,
    val filtro: FiltroPuntos = FiltroPuntos()
)

/** La lista sale SIEMPRE de la base local (Flow de SQLDelight); la red solo la actualiza. */
class RutaViewModel(
    observar: ObservarRutaUseCase,
    pesajes: ObservarPesajesUseCase,
    private val sincronizar: SincronizarUseCase,
    private val cerrarRuta: CerrarRutaUseCase,
    red: MonitorRed,
    sesiones: SesionRepository
) : ViewModel() {

    private val transitorio = MutableStateFlow(Transitorio())
    private var primeraEmision = true

    // SC-A: cada cambio de filtro abre una nueva consulta local; el ViewModel no filtra la lista.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val puntos = transitorio.map { it.filtro }.distinctUntilChanged().flatMapLatest { observar.puntos(it) }

    val ui: StateFlow<RutaUiState> = combine(
        observar.ruta(), puntos, observar.ultimaSincronizacion(), pesajes.pendientes(), transitorio
    ) { ruta, puntos, ultima, pendientes, t ->
        RutaUiState(
            // Carga solo mientras no hay nada local y la primera sincronizacion sigue en curso.
            cargando = puntos.isEmpty() && ultima == null && (t.sincronizando || primeraEmision),
            ruta = ruta, puntos = puntos, ultimaSincronizacion = ultima, pendientes = pendientes,
            sincronizando = t.sincronizando, conectado = t.conectado, aviso = t.aviso, filtro = t.filtro
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RutaUiState())

    init {
        // RF-A06: al abrir, al iniciar cada turno y cada vez que vuelve la senal, se sincroniza solo.
        viewModelScope.launch {
            combine(red.conectado, sesiones.sesion.map { it?.usuario }) { hay, usuario -> hay to usuario }
                .distinctUntilChanged()
                .collect { (hay, usuario) ->
                    transitorio.update { it.copy(conectado = hay) }
                    if (hay && usuario != null) sincronizarAhora()
                    primeraEmision = false
                }
        }
    }

    fun sincronizarAhora() {
        if (transitorio.value.sincronizando) return
        transitorio.update { it.copy(sincronizando = true, aviso = null) }
        viewModelScope.launch {
            sincronizar()
                .onSuccess { r ->
                    val rechazo = if (r.rechazados > 0) " · ${r.rechazados} rechazados (revise el pendiente)" else ""
                    transitorio.update {
                        it.copy(sincronizando = false, aviso = Aviso("Enviados: ${r.enviados} · Recibidos: ${r.recibidos}$rechazo"))
                    }
                }
                .onFailure { e -> transitorio.update { it.copy(sincronizando = false, aviso = e.aErrorApi().aviso()) } }
        }
    }

    fun cerrar() {
        val ruta = ui.value.ruta ?: return
        viewModelScope.launch {
            cerrarRuta(ruta.id)
                .onSuccess { transitorio.update { it.copy(aviso = Aviso("Ruta cerrada")) } }
                .onFailure { e -> transitorio.update { it.copy(aviso = e.aErrorApi().aviso()) } }
        }
    }

    fun buscar(texto: String) = transitorio.update { it.copy(filtro = it.filtro.copy(texto = texto)) }

    fun alternarSoloPendientes() =
        transitorio.update { it.copy(filtro = it.filtro.copy(soloPendientes = !it.filtro.soloPendientes)) }

    fun avisoMostrado() = transitorio.update { it.copy(aviso = null) }
}
