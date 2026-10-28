package pe.upeu.ecorecicla.presentation.sesion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.repository.SesionRepository
import pe.upeu.ecorecicla.domain.usecase.CerrarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.IniciarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.RestaurarSesionUseCase
import pe.upeu.ecorecicla.presentation.aErrorApi
import pe.upeu.ecorecicla.presentation.mensaje

data class LoginUiState(
    val usuario: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null
)

class SesionViewModel(
    private val iniciar: IniciarSesionUseCase,
    restaurar: RestaurarSesionUseCase,
    private val cerrar: CerrarSesionUseCase,
    repo: SesionRepository
) : ViewModel() {

    /** null = mostrar login. Cambia tambien cuando un 401 expira la sesion desde la capa de datos. */
    val sesion: StateFlow<Sesion?> = repo.sesion

    private val _ui = MutableStateFlow(LoginUiState())
    val ui = _ui.asStateFlow()

    init {
        restaurar()                       // RF-A02: token vigente -> directo al area protegida
    }

    fun alCambiarUsuario(v: String) = _ui.update { it.copy(usuario = v, errores = it.errores - "usuario", mensaje = null) }
    fun alCambiarContrasena(v: String) = _ui.update { it.copy(contrasena = v, errores = it.errores - "contrasena", mensaje = null) }

    fun ingresar() {
        val estado = _ui.value
        if (estado.cargando) return
        _ui.update { it.copy(cargando = true, mensaje = null, errores = emptyMap()) }
        viewModelScope.launch {
            iniciar(estado.usuario, estado.contrasena)
                .onSuccess { _ui.value = LoginUiState() }
                .onFailure { e ->
                    val error = e.aErrorApi()
                    _ui.update {
                        it.copy(
                            cargando = false,
                            errores = (error as? ErrorApi.Validacion)?.porCampo.orEmpty(),
                            mensaje = when (error) {
                                is ErrorApi.Validacion -> null
                                is ErrorApi.NoAutenticado -> "Usuario o contrasena incorrectos"
                                ErrorApi.SinConexion, ErrorApi.TiempoAgotado ->
                                    "Sin conexion: no se pudo contactar a EcoAPI. Revise su red"
                                else -> error.mensaje()
                            }
                        )
                    }
                }
        }
    }

    fun cerrarSesion() = cerrar()
}
