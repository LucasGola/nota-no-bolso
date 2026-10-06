package dev.lucasgola.financas

import android.app.Application
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.nfce.ImportacaoRepository
import dev.lucasgola.financas.nfce.NfceService

/** Container de dependências manual: o app é pequeno demais para justificar Hilt/Koin. */
class FinancasApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.criar(this) }
    val importacao: ImportacaoRepository by lazy { ImportacaoRepository(db, NfceService()) }
}
