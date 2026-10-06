package dev.lucasgola.financas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Cores semânticas fixas (não variam com o tema dinâmico): verde = entrada, vermelho = saída. */
object CoresValor {
    val entrada = Color(0xFF2E7D32)
    val saida = Color(0xFFC62828)
}

private val claro = lightColorScheme(primary = Color(0xFF1B5E20), secondary = Color(0xFF4E6352))
private val escuro = darkColorScheme(primary = Color(0xFF8BD58F), secondary = Color(0xFFB5CCB8))

@Composable
fun FinancasTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val esquema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> escuro
        else -> claro
    }
    MaterialTheme(colorScheme = esquema, content = content)
}
