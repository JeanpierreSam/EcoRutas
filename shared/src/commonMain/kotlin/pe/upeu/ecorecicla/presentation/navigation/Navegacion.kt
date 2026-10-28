package pe.upeu.ecorecicla.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.upeu.ecorecicla.domain.model.Privilegio
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesUseCase
import pe.upeu.ecorecicla.presentation.detalle.DetalleScreen
import pe.upeu.ecorecicla.presentation.listado.RutaScreen
import pe.upeu.ecorecicla.presentation.perfil.PerfilScreen
import pe.upeu.ecorecicla.presentation.sesion.LoginScreen
import pe.upeu.ecorecicla.presentation.sesion.SesionViewModel
import pe.upeu.ecorecicla.presentation.theme.EcoTema

sealed interface Pantalla {
    data object Ruta : Pantalla
    data class Detalle(val puntoId: Long) : Pantalla
    data object Perfil : Pantalla
}

/** Pila de pantallas del area protegida. MainActivity conecta el boton atras con [volver]. */
class Navegador {
    private val pila = mutableStateListOf<Pantalla>(Pantalla.Ruta)
    val actual: Pantalla get() = pila.last()
    val puedeVolver: Boolean get() = pila.size > 1
    fun ir(p: Pantalla) { pila.add(p) }
    fun volver() { if (puedeVolver) pila.removeAt(pila.lastIndex) }
    fun reiniciar() { pila.clear(); pila.add(Pantalla.Ruta) }
}

@Composable
fun App(navegador: Navegador) {
    EcoTema {
        val sesionVm = koinViewModel<SesionViewModel>()
        val sesion by sesionVm.sesion.collectAsStateWithLifecycle()
        val actual = sesion
        // Sin sesion (logout o 401) se vuelve al login y la pila empieza de cero.
        LaunchedEffect(actual == null) { if (actual == null) navegador.reiniciar() }
        if (actual == null) {
            LoginScreen(sesionVm)
            return@EcoTema
        }
        when (val p = navegador.actual) {
            Pantalla.Ruta -> RutaScreen(
                vm = koinViewModel(),
                puedeCerrarRuta = actual.puede(Privilegio.RUTA_CERRAR),
                soloLectura = actual.soloLectura,
                alAbrirPunto = { navegador.ir(Pantalla.Detalle(it)) },
                alAbrirPerfil = { navegador.ir(Pantalla.Perfil) }
            )
            is Pantalla.Detalle -> DetalleScreen(
                vm = koinViewModel(key = "detalle-${p.puntoId}") { parametersOf(p.puntoId) },
                alVolver = navegador::volver
            )
            Pantalla.Perfil -> {
                val pendientes by koinInject<ObservarPesajesUseCase>().pendientes().collectAsStateWithLifecycle(0L)
                PerfilScreen(actual, pendientes, alCerrarSesion = sesionVm::cerrarSesion, alVolver = navegador::volver)
            }
        }
    }
}
