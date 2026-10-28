package pe.upeu.ecorecicla

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import org.koin.android.ext.koin.androidContext
import pe.upeu.ecorecicla.di.iniciarKoin
import pe.upeu.ecorecicla.presentation.navigation.App
import pe.upeu.ecorecicla.presentation.navigation.Navegador

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        iniciarKoin(platformModule) { androidContext(this@MainApplication) }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val navegador = remember { Navegador() }
            BackHandler(enabled = navegador.puedeVolver) { navegador.volver() }
            App(navegador)
        }
    }
}
