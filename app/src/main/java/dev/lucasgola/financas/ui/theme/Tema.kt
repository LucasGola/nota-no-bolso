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

/**
 * Cores dos gráficos, validadas para daltonismo (script de validação da skill de dataviz):
 * azul × laranja passa em claro e escuro (ΔE CVD ≥ 24). Verde × vermelho falhou (ΔE 4,2 deutan),
 * por isso não é usado para distinguir séries nos gráficos.
 */
data class CoresGrafico(
    val entradas: Color,
    val saidas: Color,
    /** Série única (ex.: top estabelecimentos). */
    val serie: Color,
    val grade: Color,
    val eixo: Color,
    val textoMudo: Color,
    val destaque: Color,
)

val coresGraficoClaro = CoresGrafico(
    entradas = Color(0xFF2A78D6),
    saidas = Color(0xFFEB6834),
    serie = Color(0xFF2A78D6),
    grade = Color(0xFFE1E0D9),
    eixo = Color(0xFFC3C2B7),
    textoMudo = Color(0xFF898781),
    destaque = Color(0x0F000000),
)

val coresGraficoEscuro = CoresGrafico(
    entradas = Color(0xFF3987E5),
    saidas = Color(0xFFD95926),
    serie = Color(0xFF3987E5),
    grade = Color(0xFF2C2C2A),
    eixo = Color(0xFF383835),
    textoMudo = Color(0xFF898781),
    destaque = Color(0x14FFFFFF),
)

@Composable
fun coresGrafico(): CoresGrafico = if (isSystemInDarkTheme()) coresGraficoEscuro else coresGraficoClaro
