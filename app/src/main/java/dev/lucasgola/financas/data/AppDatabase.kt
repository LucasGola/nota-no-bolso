package dev.lucasgola.financas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    entities = [Categoria::class, Estabelecimento::class, Lancamento::class, NotaFiscal::class, ItemNota::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Conversores::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoriaDao(): CategoriaDao
    abstract fun lancamentoDao(): LancamentoDao
    abstract fun estabelecimentoDao(): EstabelecimentoDao
    abstract fun notaDao(): NotaDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NOME_ARQUIVO = "financas.db"

        fun criar(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NOME_ARQUIVO)
                .addCallback(SementeCategorias)
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
