package pe.upeu.ecorecicla.presentation.listado

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.presentation.theme.haceCuanto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RutaScreen(
    vm: RutaViewModel,
    puedeCerrarRuta: Boolean,
    soloLectura: Boolean,
    alAbrirPunto: (Long) -> Unit,
    alAbrirPerfil: () -> Unit
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.aviso) {
        val aviso = ui.aviso ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(aviso.texto, actionLabel = if (aviso.reintentar) "Reintentar" else null)
        vm.avisoMostrado()
        if (r == SnackbarResult.ActionPerformed) vm.sincronizarAhora()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ruta del dia")
                        ui.ruta?.let {
                            Text("${it.fecha} · ${if (it.cerrada) "CERRADA" else "ABIERTA"}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = vm::sincronizarAhora, enabled = !ui.sincronizando) { Text("Sincronizar") }
                    TextButton(onClick = alAbrirPerfil) { Text("Perfil") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            if (ui.sincronizando) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (soloLectura) {
                Text(
                    "Acceso de solo lectura: puede consultar la ruta y los pesajes, no modificarlos",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Ultima sincronizacion: " + (ui.ultimaSincronizacion?.let(::haceCuanto) ?: "nunca"),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (ui.conectado) "En linea" else "Sin conexion",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui.conectado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            if (ui.pendientes > 0) {
                Text(
                    "${ui.pendientes} pesaje(s) pendiente(s) de envio",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            when {
                ui.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                ui.puntos.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No tiene una ruta asignada para hoy o aun no se descargo. Pulse Sincronizar.")
                }
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(ui.puntos, key = { it.id }) { punto -> FilaPunto(punto) { alAbrirPunto(punto.id) } }
                }
            }
            val ruta = ui.ruta
            if (puedeCerrarRuta && ruta != null && !ruta.cerrada) {
                Button(onClick = vm::cerrar, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Cerrar ruta") }
            }
        }
    }
}

@Composable
private fun FilaPunto(punto: PuntoAcopio, alPulsar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = alPulsar),
        colors = CardDefaults.cardColors(
            containerColor = if (punto.atendido) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${punto.secuencia}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(punto.razonSocial, fontWeight = FontWeight.SemiBold)
                Text(punto.direccion, style = MaterialTheme.typography.bodySmall)
                Text("${punto.zona} · ${punto.materialHabitual}", style = MaterialTheme.typography.bodySmall)
            }
            AssistChip(onClick = alPulsar, label = { Text(if (punto.atendido) "Pesado" else "Pendiente") })
        }
    }
}
