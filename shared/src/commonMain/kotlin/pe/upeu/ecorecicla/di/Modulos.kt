package pe.upeu.ecorecicla.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.core.context.startKoin
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.local.DatosLocales
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.EjecutorApi
import pe.upeu.ecorecicla.data.remote.crearClienteHttp
import pe.upeu.ecorecicla.data.repository.PesajeRepositorioOffline
import pe.upeu.ecorecicla.data.repository.RutaRepositorioOffline
import pe.upeu.ecorecicla.data.repository.SesionRepositorioImpl
import pe.upeu.ecorecicla.db.EcoDb
import pe.upeu.ecorecicla.domain.repository.PesajeRepository
import pe.upeu.ecorecicla.domain.repository.RutaRepository
import pe.upeu.ecorecicla.domain.repository.SesionRepository
import pe.upeu.ecorecicla.domain.usecase.AnularPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.CerrarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.CerrarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.CorregirPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.IniciarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.RegistrarPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.RestaurarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import pe.upeu.ecorecicla.presentation.detalle.DetalleViewModel
import pe.upeu.ecorecicla.presentation.listado.RutaViewModel
import pe.upeu.ecorecicla.presentation.sesion.SesionViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Nombres con que platformModule registra la URL base y si es compilacion de depuracion. */
const val URL_BASE = "urlBase"
const val ES_DEPURACION = "esDepuracion"
const val RELOJ = "reloj"

val redModule = module {
    // 401 en cualquier llamada -> se expira la sesion. Se resuelve al fallar, no al crear: sin ciclo.
    single { EjecutorApi { get<SesionRepository>().expirar() } }
    single { crearClienteHttp(get(), get(named(URL_BASE)), get(), get(named(ES_DEPURACION))) }
    single { EcoApi(get(), get()) }
}

@OptIn(ExperimentalTime::class)
val dataModule = module {
    single<() -> Long>(named(RELOJ)) { { Clock.System.now().toEpochMilliseconds() } }
    single(createdAtStart = true) { EcoDb(get<BaseLocal>().crearDriver()) }
    single { DatosLocales(get()) }
    single<SesionRepository> { SesionRepositorioImpl(get(), get(), get(), get(named(RELOJ))) }
    single<RutaRepository> { RutaRepositorioOffline(get(), get(), get(named(RELOJ))) }
    single<PesajeRepository> { PesajeRepositorioOffline(get(), get()) }
}

val domainModule = module {
    factoryOf(::IniciarSesionUseCase)
    factoryOf(::RestaurarSesionUseCase)
    factoryOf(::CerrarSesionUseCase)
    factoryOf(::ObservarRutaUseCase)
    factoryOf(::CerrarRutaUseCase)
    factoryOf(::ObservarPesajesUseCase)
    factoryOf(::RegistrarPesajeUseCase)
    factoryOf(::CorregirPesajeUseCase)
    factoryOf(::AnularPesajeUseCase)
    factoryOf(::SincronizarUseCase)
}

val presentationModule = module {
    viewModelOf(::SesionViewModel)
    viewModelOf(::RutaViewModel)
    viewModelOf(::DetalleViewModel)
}

/** [platformModule] lo aporta cada plataforma (en Android: PlatformModule.android.kt). */
fun iniciarKoin(platformModule: Module, config: KoinAppDeclaration = {}) = startKoin {
    config()
    modules(platformModule, redModule, dataModule, domainModule, presentationModule)
}
