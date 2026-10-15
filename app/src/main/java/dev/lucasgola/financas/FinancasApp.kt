package dev.lucasgola.financas

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import dev.lucasgola.financas.backup.BackupRepository
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.export.Exportador
import dev.lucasgola.financas.filtro.Filtro
import dev.lucasgola.financas.filtro.FiltroSalvo
import dev.lucasgola.financas.nfce.ImportacaoRepository
import dev.lucasgola.financas.nfce.NfceService
import dev.lucasgola.financas.util.ZONA
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Container de dependências manual: o app é pequeno demais para justificar Hilt/Koin. */
class FinancasApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.criar(this) }
    val importacao: ImportacaoRepository by lazy { ImportacaoRepository(db, NfceService()) }
    val exportador: Exportador by lazy { Exportador(this, db) }
    val backup: BackupRepository by lazy { BackupRepository(this, db) }

    /** Filtro único, compartilhado por extrato, gráficos e exportação; sobrevive ao fechamento do app. */
    lateinit var filtro: MutableStateFlow<Filtro>
        private set

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("preferencias", Context.MODE_PRIVATE)
        filtro = MutableStateFlow(FiltroSalvo.decodificar(prefs.getString(CHAVE_FILTRO, null), LocalDate.now(ZONA)))
        MainScope().launch {
            filtro.drop(1).collect { f -> prefs.edit { putString(CHAVE_FILTRO, FiltroSalvo.codificar(f)) } }
        }
    }

    private companion object {
        const val CHAVE_FILTRO = "filtro"
    }
}
