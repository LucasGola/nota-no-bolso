package dev.lucasgola.financas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.math.BigDecimal
import java.time.Instant

class Conversores {
    @TypeConverter fun instantParaLong(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun longParaInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)

    // TEXT preserva a escala exata; REAL perderia precisão.
    @TypeConverter fun decimalParaTexto(v: BigDecimal?): String? = v?.toPlainString()
    @TypeConverter fun textoParaDecimal(v: String?): BigDecimal? = v?.let(::BigDecimal)
}

@Database(
    entities = [
        Categoria::class, Estabelecimento::class, Lancamento::class, NotaFiscal::class, ItemNota::class,
        MetodoPagamento::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Conversores::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoriaDao(): CategoriaDao
    abstract fun lancamentoDao(): LancamentoDao
    abstract fun estabelecimentoDao(): EstabelecimentoDao
    abstract fun notaDao(): NotaDao
    abstract fun metodoPagamentoDao(): MetodoPagamentoDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NOME_ARQUIVO = "financas.db"

        fun criar(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NOME_ARQUIVO)
                .addCallback(SementeCategorias)
                .addMigrations(Migracoes.DE_1_PARA_2, Migracoes.DE_2_PARA_3)
                .build()
    }
}

private object SementeCategorias : RoomDatabase.Callback() {
    private val padrao = listOf(
        Triple("Mercado", 0xFF2E7D32, TipoCategoria.SAIDA),
        Triple("Alimentação fora", 0xFFEF6C00, TipoCategoria.SAIDA),
        Triple("Transporte", 0xFF1565C0, TipoCategoria.SAIDA),
        Triple("Saúde", 0xFFC62828, TipoCategoria.SAIDA),
        Triple("Casa", 0xFF6D4C41, TipoCategoria.SAIDA),
        Triple("Lazer", 0xFF8E24AA, TipoCategoria.SAIDA),
        Triple("Salário", 0xFF00897B, TipoCategoria.ENTRADA),
        Triple("Outros", 0xFF757575, TipoCategoria.AMBOS),
    )

    override fun onCreate(db: SupportSQLiteDatabase) {
        padrao.forEach { (nome, cor, tipo) ->
            db.execSQL(
                "INSERT INTO categoria (nome, cor, tipo, ativa) VALUES (?, ?, ?, 1)",
                arrayOf<Any>(nome, cor, tipo.name),
            )
        }
    }
}

/**
 * Migrações escritas à mão, sem recriar tabelas: os dados existentes não são copiados nem tocados.
 * O SQL fica exposto para o teste na JVM, que confere o banco migrado contra o schema exportado.
 * Sem fallbackToDestructiveMigration: se uma migração falhar, o app para em vez de apagar dados.
 */
object Migracoes {
    /** v2: métodos de pagamento cadastrados pelo usuário e o vínculo opcional no lançamento. */
    val SQL_1_PARA_2 = listOf(
        "CREATE TABLE IF NOT EXISTS `metodo_pagamento` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`nome` TEXT NOT NULL, `descricao` TEXT, `ativo` INTEGER NOT NULL)",
        "ALTER TABLE `lancamento` ADD COLUMN `metodoPagamentoId` INTEGER " +
            "REFERENCES `metodo_pagamento`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        "CREATE INDEX IF NOT EXISTS `index_lancamento_metodoPagamentoId` ON `lancamento` (`metodoPagamentoId`)",
    )

    val DE_1_PARA_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) = SQL_1_PARA_2.forEach(db::execSQL)
    }

    /** v3: o estabelecimento lembra o último método de pagamento usado. */
    val SQL_2_PARA_3 = listOf(
        "ALTER TABLE `estabelecimento` ADD COLUMN `metodoPagamentoPadraoId` INTEGER " +
            "REFERENCES `metodo_pagamento`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        "CREATE INDEX IF NOT EXISTS `index_estabelecimento_metodoPagamentoPadraoId` " +
            "ON `estabelecimento` (`metodoPagamentoPadraoId`)",
    )

    val DE_2_PARA_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) = SQL_2_PARA_3.forEach(db::execSQL)
    }
}
