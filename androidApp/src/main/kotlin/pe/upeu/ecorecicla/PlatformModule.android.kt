package pe.upeu.ecorecicla

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.remote.ApiConfig
import pe.upeu.ecorecicla.di.ES_DEPURACION
import pe.upeu.ecorecicla.di.URL_BASE
import pe.upeu.ecorecicla.domain.repository.MonitorRed

/** Motor HTTP, almacen seguro, driver de SQLite y monitor de red de Android. */
val platformModule = module {
    single<HttpClientEngine> { OkHttp.create() }
    single(named(URL_BASE)) { ApiConfig.URL_BASE_EMULADOR }
    single(named(ES_DEPURACION)) { BuildConfig.DEBUG }
    single<AlmacenSeguro> { AlmacenSeguroAndroid(androidContext()) }
    single<BaseLocal> { BaseLocalAndroid(androidContext()) }
    single<MonitorRed> { MonitorRedAndroid(androidContext()) }
}

class MonitorRedAndroid(contexto: Context) : MonitorRed {
    private val cm = contexto.getSystemService(ConnectivityManager::class.java)

    override val conectado: Flow<Boolean> = callbackFlow {
        fun hayRed() = cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        // Red por defecto: al perderla (modo avion) llega onLost; si otra la reemplaza, llega onAvailable.
        // Consultar activeNetwork dentro de onLost no sirve: en ese instante aun reporta la red que se va.
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
        }
        trySend(hayRed())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }
}
