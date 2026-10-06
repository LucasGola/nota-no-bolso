package dev.lucasgola.financas.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.lucasgola.financas.FinancasApp
import dev.lucasgola.financas.R
import dev.lucasgola.financas.ui.categorias.CategoriasScreen
import dev.lucasgola.financas.ui.extrato.ExtratoScreen
import dev.lucasgola.financas.ui.graficos.GraficosScreen
import dev.lucasgola.financas.ui.lancamento.LancamentoFormScreen

private enum class Aba(val rota: String, val titulo: String, @DrawableRes val icone: Int) {
    EXTRATO("extrato", "Extrato", R.drawable.ic_extrato),
    GRAFICOS("graficos", "Gráficos", R.drawable.ic_grafico),
    CATEGORIAS("categorias", "Categorias", R.drawable.ic_categoria),
}

private const val ROTA_LANCAMENTO = "lancamento?id={id}"
private fun rotaLancamento(id: Long?) = "lancamento?id=${id ?: -1}"

@Composable
fun AppNav() {
    val app = LocalContext.current.applicationContext as FinancasApp
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route

    Scaffold(
        bottomBar = {
            if (Aba.entries.any { it.rota == rotaAtual }) {
                NavigationBar {
                    Aba.entries.forEach { aba ->
                        NavigationBarItem(
                            selected = rotaAtual == aba.rota,
                            onClick = {
                                nav.navigate(aba.rota) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(aba.icone), contentDescription = null) },
                            label = { Text(aba.titulo) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Aba.EXTRATO.rota, modifier = Modifier.padding(padding)) {
            composable(Aba.EXTRATO.rota) {
                ExtratoScreen(
                    db = app.db,
                    onNovo = { nav.navigate(rotaLancamento(null)) },
                    onAbrir = { nav.navigate(rotaLancamento(it)) },
                )
            }
            composable(Aba.GRAFICOS.rota) { GraficosScreen() }
            composable(Aba.CATEGORIAS.rota) { CategoriasScreen(db = app.db) }
            composable(
                ROTA_LANCAMENTO,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { back ->
                val id = back.arguments?.getLong("id")?.takeIf { it > 0 }
                LancamentoFormScreen(db = app.db, lancamentoId = id, onFechar = { nav.popBackStack() })
            }
        }
    }
}
