package pe.upeu.ecorecicla.presentation.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.Privilegio
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.repository.SesionRepository
import pe.upeu.ecorecicla.domain.usecase.AnularPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.CorregirPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.RegistrarPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import pe.upeu.ecorecicla.presentation.Aviso
import pe.upeu.ecorecicla.presentation.aErrorApi
import pe.upeu.ecorecicla.presentation.aviso
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val MATERIALES = listOf("CARTON", "PLASTICO", "PAPEL", "VIDRIO", "METAL")

data class FormularioPesaje(
    val editando: Pesaje? = null,
    val material: String = "",
    val pesoBruto: String = "",
    val tara: String = "",
    val observacion: String = "",
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null,
    val guardando: Boolean = false
)

data class DetalleUiState(
    val cargando: Boolean = true,
    val punto: PuntoAcopio? = null,
    val ruta: Ruta? = null,
    val pesajes: List<Pesaje> = emptyList(),
    val formulario: FormularioPesaje? = null,
    val anulando: Pesaje? = null,
    val aviso: Aviso? = null,
    // Lo que la interfaz ofrece segun los privilegios del token (el servicio igual los verifica).
    val puedeRegistrar: Boolean = false,
    val puedeAnular: Boolean = false
)

private data class Local(val formulario: FormularioPesaje? = null, val anulando: Pesaje? = null, val aviso: Aviso? = null)

class DetalleViewModel(
    private val puntoId: Long,
    observarRuta: ObservarRutaUseCase,
    observarPesajes: ObservarPesajesUseCase,
    private val registrar: RegistrarPesajeUseCase,
    private val corregir: CorregirPesajeUseCase,
    private val anular: AnularPesajeUseCase,
    private val sincronizar: SincronizarUseCase,
    private val sesiones: SesionRepository
) : ViewModel() {

    private val local = MutableStateFlow(Local())
    private val sesion get() = sesiones.sesion.value

    val ui: StateFlow<DetalleUiState> = combine(
        observarRuta.punto(puntoId), observarRuta.ruta(), observarPesajes(puntoId), local
    ) { punto, ruta, pesajes, l ->
        DetalleUiState(
            cargando = false, punto = punto, ruta = ruta, pesajes = pesajes,
            formulario = l.formulario, anulando = l.anulando, aviso = l.aviso,
            puedeRegistrar = sesion?.puede(Privilegio.PESAJE_CREAR) == true && ruta?.cerrada == false,
            puedeAnular = sesion?.puede(Privilegio.PESAJE_ANULAR) == true && ruta?.cerrada == false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetalleUiState())

    /** RN-02 como ayuda de interfaz: el servicio responde 403 si igual se intenta. */
    fun puedeCorregir(p: Pesaje): Boolean {
        val s = sesion ?: return false
        if (p.estado is EstadoPesaje.Anulado || ui.value.ruta?.cerrada == true) return false
        return s.puede(Privilegio.PESAJE_EDITAR_TODOS) || (s.puede(Privilegio.PESAJE_EDITAR) && p.operario == s.usuario)
    }

    fun nuevoPesaje() = local.update {
        it.copy(formulario = FormularioPesaje(material = ui.value.punto?.materialHabitual.orEmpty()))
    }

    fun editar(p: Pesaje) = local.update {
        it.copy(formulario = FormularioPesaje(p, p.material, p.pesoBruto.toString(), p.tara.toString(), p.observacion))
    }

    fun cambiarFormulario(cambio: (FormularioPesaje) -> FormularioPesaje) =
        local.update { l -> l.copy(formulario = l.formulario?.let { cambio(it).copy(mensaje = null) }) }

    fun cerrarFormulario() = local.update { it.copy(formulario = null) }

    @OptIn(ExperimentalTime::class)
    fun guardar() {
        val f = local.value.formulario ?: return
        val bruto = f.pesoBruto.replace(',', '.').toDoubleOrNull()
        val tara = f.tara.replace(',', '.').toDoubleOrNull()
        if (bruto == null || tara == null) {
            cambiarFormulario {
                it.copy(errores = buildMap {
                    if (bruto == null) put("pesoBruto", "Ingrese un numero")
                    if (tara == null) put("tara", "Ingrese un numero")
                })
            }
            return
        }
        val base = f.editando ?: Pesaje(
            id = 0, puntoId = puntoId, fechaHora = Clock.System.now().toString(), material = "", pesoBruto = 0.0,
            tara = 0.0, pesoNeto = 0.0, operario = sesion?.usuario.orEmpty(), estado = EstadoPesaje.Registrado,
            pendiente = true, rutaId = ui.value.ruta?.id ?: 0
        )
        // Neto provisional solo para mostrar mientras esta pendiente; el definitivo lo calcula el servicio.
        val pesaje = base.copy(material = f.material, pesoBruto = bruto, tara = tara, pesoNeto = bruto - tara,
            observacion = f.observacion.trim())
        local.update { it.copy(formulario = f.copy(guardando = true, errores = emptyMap())) }
        viewModelScope.launch {
            val resultado = if (f.editando == null) registrar(pesaje) else corregir(pesaje)
            resultado
                .onSuccess { guardado ->
                    val texto = if (guardado.pendiente) "Guardado sin conexion: se enviara al sincronizar"
                    else "Pesaje guardado · neto ${guardado.pesoNeto} kg"
                    local.update { it.copy(formulario = null, aviso = Aviso(texto)) }
                }
                .onFailure { e -> manejarError(e.aErrorApi()) }
        }
    }

    private fun manejarError(error: ErrorApi) {
        when (error) {
            // 400: cada mensaje bajo su campo
            is ErrorApi.Validacion -> cambiarFormulario { it.copy(guardando = false, errores = error.porCampo) }
            // 409: el motivo del servicio, dentro del formulario
            is ErrorApi.Conflicto ->
                local.update { l -> l.copy(formulario = l.formulario?.copy(guardando = false, mensaje = error.mensaje)) }
            // 404: se informa y se refresca; sin reintentar
            is ErrorApi.NoEncontrado -> {
                local.update { it.copy(formulario = null, aviso = Aviso(error.mensaje)) }
                viewModelScope.launch { sincronizar() }
            }
            // 403 y el resto: aviso; la sesion se mantiene (el 401 ya la cerro en la capa de datos)
            else -> local.update { it.copy(formulario = null, aviso = error.aviso()) }
        }
    }

    fun pedirAnulacion(p: Pesaje) = local.update { it.copy(anulando = p) }
    fun cancelarAnulacion() = local.update { it.copy(anulando = null) }

    fun confirmarAnulacion(motivo: String) {
        val p = local.value.anulando ?: return
        viewModelScope.launch {
            anular(p.id, motivo)
                .onSuccess { local.update { it.copy(anulando = null, aviso = Aviso("Pesaje anulado")) } }
                .onFailure { e ->
                    val error = e.aErrorApi()
                    local.update { it.copy(anulando = null, aviso = error.aviso()) }
                    if (error is ErrorApi.NoEncontrado) sincronizar()
                }
        }
    }

    fun reintentarSincronizacion() {
        viewModelScope.launch {
            sincronizar().onFailure { e -> local.update { it.copy(aviso = e.aErrorApi().aviso()) } }
        }
    }

    fun avisoMostrado() = local.update { it.copy(aviso = null) }
}
