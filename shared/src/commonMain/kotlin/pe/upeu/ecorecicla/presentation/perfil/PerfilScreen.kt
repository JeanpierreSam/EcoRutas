package pe.upeu.ecorecicla.presentation.perfil

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.upeu.ecorecicla.domain.model.Sesion

/** RF-A07. [pendientes] avisa antes de cerrar: cerrar sesion limpia la base local del equipo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(sesion: Sesion, pendientes: Long, alCerrarSesion: () -> Unit, alVolver: () -> Unit) {
    var confirmar by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Perfil") }, navigationIcon = { TextButton(onClick = alVolver) { Text("Volver") } })
        }
    ) { relleno ->
        Column(Modifier.padding(relleno).padding(16.dp).fillMaxSize()) {
            Text(sesion.nombre, style = MaterialTheme.typography.headlineSmall)
            Text("Usuario: ${sesion.usuario}")
            Text("Rol: ${sesion.rol}")
            if (sesion.soloLectura) Text("Acceso de solo lectura", color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(12.dp))
            Text("Privilegios", style = MaterialTheme.typography.titleMedium)
            sesion.privilegios.sorted().forEach { Text("• $it") }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (pendientes > 0) confirmar = true else alCerrarSesion() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cerrar sesion") }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Hay pesajes sin enviar") },
            text = { Text("Tiene $pendientes pesaje(s) pendiente(s). Si cierra sesion se borraran del equipo. Sincronice antes.") },
            confirmButton = { TextButton(onClick = alCerrarSesion) { Text("Cerrar igual") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}
