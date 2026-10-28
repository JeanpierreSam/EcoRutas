package pe.upeu.ecorecicla.presentation.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleScreen(vm: DetalleViewModel, alVolver: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.aviso) {
        val aviso = ui.aviso ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(aviso.texto, actionLabel = if (aviso.reintentar) "Reintentar" else null)
        vm.avisoMostrado()
        if (r == SnackbarResult.ActionPerformed) vm.reintentarSincronizacion()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.punto?.razonSocial ?: "Punto de acopio") },
                navigationIcon = { TextButton(onClick = alVolver) { Text("Volver") } }
            )
        },
        floatingActionButton = {
            if (ui.puedeRegistrar && ui.formulario == null) {
                ExtendedFloatingActionButton(onClick = vm::nuevoPesaje) { Text("Registrar pesaje") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { relleno ->
        val punto = ui.punto
        when {
            ui.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            punto == null -> Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.Center) {
                Text("El punto ya no esta en la ruta del dia")
            }
            else -> Column(Modifier.padding(relleno).fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    Text(punto.direccion)
                    Text("Zona ${punto.zona} · material habitual ${punto.materialHabitual} · visita n.° ${punto.secuencia}",
                        style = MaterialTheme.typography.bodySmall)
                    if (ui.ruta?.cerrada == true) Text("Ruta cerrada: solo lectura", color = MaterialTheme.colorScheme.error)
                }
                ui.formulario?.let { f -> Formulario(f, vm) }
                Text("Pesajes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                if (ui.pesajes.isEmpty()) {
                    Text("Aun no hay pesajes en este punto", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        items(ui.pesajes, key = { it.id }) { p ->
                            FilaPesaje(
                                p,
                                puedeCorregir = vm.puedeCorregir(p),
                                puedeAnular = ui.puedeAnular && p.estado is EstadoPesaje.Registrado,
                                alCorregir = { vm.editar(p) },
                                alAnular = { vm.pedirAnulacion(p) }
                            )
                        }
                    }
                }
            }
        }
    }

    ui.anulando?.let { DialogoAnular(it, vm::confirmarAnulacion, vm::cancelarAnulacion) }
}

@Composable
private fun Formulario(f: FormularioPesaje, vm: DetalleViewModel) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(if (f.editando == null) "Nuevo pesaje" else "Corregir pesaje", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MATERIALES.forEach { m ->
                    FilterChip(selected = f.material == m, onClick = { vm.cambiarFormulario { it.copy(material = m, errores = it.errores - "material") } },
                        label = { Text(m) })
                }
            }
            f.errores["material"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Campo("Peso bruto (kg)", f.pesoBruto, f.errores["pesoBruto"], numerico = true) { v ->
                vm.cambiarFormulario { it.copy(pesoBruto = v, errores = it.errores - "pesoBruto") }
            }
            Campo("Tara (kg)", f.tara, f.errores["tara"], numerico = true) { v ->
                vm.cambiarFormulario { it.copy(tara = v, errores = it.errores - "tara") }
            }
            Campo("Observacion", f.observacion, f.errores["observacion"]) { v ->
                vm.cambiarFormulario { it.copy(observacion = v, errores = it.errores - "observacion") }
            }
            f.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = vm::cerrarFormulario) { Text("Cancelar") }
                TextButton(onClick = vm::guardar, enabled = !f.guardando) {
                    if (f.guardando) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Guardar")
                }
            }
        }
    }
}

@Composable
private fun Campo(etiqueta: String, valor: String, error: String?, numerico: Boolean = false, alCambiar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FilaPesaje(p: Pesaje, puedeCorregir: Boolean, puedeAnular: Boolean, alCorregir: () -> Unit, alAnular: () -> Unit) {
    val anulado = p.estado as? EstadoPesaje.Anulado
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${p.material} · neto ${p.pesoNeto} kg", fontWeight = FontWeight.SemiBold)
                when {
                    p.pendiente -> Text("PENDIENTE", color = MaterialTheme.colorScheme.tertiary)
                    anulado != null -> Text("ANULADO", color = MaterialTheme.colorScheme.error)
                }
            }
            Text("Bruto ${p.pesoBruto} · tara ${p.tara} · ${p.operario} · ${p.fechaHora.take(16).replace('T', ' ')}",
                style = MaterialTheme.typography.bodySmall)
            if (p.id < 0) Text("Id local provisional: ${p.id}", style = MaterialTheme.typography.bodySmall)
            if (p.observacion.isNotBlank()) Text(p.observacion, style = MaterialTheme.typography.bodySmall)
            anulado?.let { Text("Motivo: ${it.motivo}", style = MaterialTheme.typography.bodySmall) }
            Row {
                if (puedeCorregir) TextButton(onClick = alCorregir) { Text("Corregir") }
                if (puedeAnular) TextButton(onClick = alAnular) { Text("Anular") }
            }
        }
    }
}

@Composable
private fun DialogoAnular(p: Pesaje, alConfirmar: (String) -> Unit, alCancelar: () -> Unit) {
    var motivo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Anular pesaje ${p.id}") },
        text = {
            OutlinedTextField(value = motivo, onValueChange = { motivo = it }, label = { Text("Motivo") })
        },
        confirmButton = { TextButton(onClick = { alConfirmar(motivo) }, enabled = motivo.isNotBlank()) { Text("Anular") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}
