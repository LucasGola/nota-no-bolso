package dev.lucasgola.financas.ui.categorias

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.ui.metodos.MetodosPagamentoScreen

/** Aba "Cadastros": categorias e métodos de pagamento, cada um com a sua lista. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadastrosScreen(db: AppDatabase) {
    var aba by rememberSaveable { mutableIntStateOf(0) }
    val topo: @Composable () -> Unit = {
        Column {
            TopAppBar(title = { Text("Cadastros") })
            PrimaryTabRow(selectedTabIndex = aba) {
                Tab(selected = aba == 0, onClick = { aba = 0 }, text = { Text("Categorias") })
                Tab(selected = aba == 1, onClick = { aba = 1 }, text = { Text("Pagamento") })
            }
        }
    }
    if (aba == 0) CategoriasScreen(db, topo) else MetodosPagamentoScreen(db.metodoPagamentoDao(), topo)
}
