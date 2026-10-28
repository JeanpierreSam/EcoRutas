package pe.upeu.ecorecicla.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val colores = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFE65100),
    surfaceVariant = Color(0xFFF1F4EF)
)

@Composable
fun EcoTema(contenido: @Composable () -> Unit) = MaterialTheme(colorScheme = colores, content = contenido)

/** «hace 3 min» a partir de un epoch en milisegundos. */
@OptIn(ExperimentalTime::class)
fun haceCuanto(epochMs: Long): String {
    val minutos = (Clock.System.now().toEpochMilliseconds() - epochMs) / 60_000
    return when {
        minutos < 1 -> "hace instantes"
        minutos < 60 -> "hace $minutos min"
        else -> "hace ${minutos / 60} h ${minutos % 60} min"
    }
}
