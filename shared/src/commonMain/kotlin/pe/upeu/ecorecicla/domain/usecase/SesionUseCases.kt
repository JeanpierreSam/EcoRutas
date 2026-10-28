package pe.upeu.ecorecicla.domain.usecase

import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.ErrorApiException
import pe.upeu.ecorecicla.domain.repository.SesionRepository

class IniciarSesionUseCase(private val repo: SesionRepository) {
    suspend operator fun invoke(usuario: String, contrasena: String) = runCatching {
        if (usuario.isBlank() || contrasena.isBlank()) {
            throw ErrorApiException(ErrorApi.Validacion(buildMap {
                if (usuario.isBlank()) put("usuario", "Ingrese su usuario")
                if (contrasena.isBlank()) put("contrasena", "Ingrese su contrasena")
            }))
        }
        repo.iniciar(usuario.trim(), contrasena)
    }
}

class RestaurarSesionUseCase(private val repo: SesionRepository) {
    operator fun invoke() = repo.restaurar()
}

class CerrarSesionUseCase(private val repo: SesionRepository) {
    operator fun invoke() = repo.cerrar()
}
