package dev.lucasgola.financas.backup

import dev.lucasgola.financas.data.Categoria
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.data.ItemNota
import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.NotaFiscal
import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.StatusNota
import dev.lucasgola.financas.data.TipoCategoria
import dev.lucasgola.financas.data.TipoLancamento
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant

/** Cópia completa do banco. Os ids são preservados, então a restauração reproduz exatamente os mesmos dados. */
data class Backup(
    val geradoEm: Instant,
    val categorias: List<Categoria>,
    val estabelecimentos: List<Estabelecimento>,
    val lancamentos: List<Lancamento>,
    val notas: List<NotaFiscal>,
    val itens: List<ItemNota>,
)

class BackupInvalidoException(mensagem: String, causa: Throwable? = null) : Exception(mensagem, causa)

/**
 * Formato do arquivo: JSON legível, com um campo de versão para permitir migrar backups antigos
 * quando o schema mudar. Datas em epoch millis (UTC), dinheiro em centavos, decimais como texto.
 */
object BackupJson {
    const val MIME = "application/json"
    private const val FORMATO = "financas-backup"
    const val VERSAO = 1

    fun gerar(b: Backup): String = JSONObject()
        .put("formato", FORMATO)
        .put("versao", VERSAO)
        .put("geradoEm", b.geradoEm.toEpochMilli())
        .put("categorias", b.categorias.toJson(::categoria))
        .put("estabelecimentos", b.estabelecimentos.toJson(::estabelecimento))
        .put("lancamentos", b.lancamentos.toJson(::lancamento))
        .put("notas", b.notas.toJson(::nota))
        .put("itens", b.itens.toJson(::item))
        .toString(1)

    fun ler(texto: String): Backup {
        val raiz = try {
            JSONObject(texto)
        } catch (e: JSONException) {
            throw BackupInvalidoException("O arquivo não é um backup do Finanças.", e)
        }
        if (raiz.optString("formato") != FORMATO) throw BackupInvalidoException("O arquivo não é um backup do Finanças.")
        val versao = raiz.optInt("versao", -1)
        if (versao > VERSAO) throw BackupInvalidoException("Backup feito por uma versão mais nova do app. Atualize o app antes de restaurar.")
        if (versao < 1) throw BackupInvalidoException("Versão de backup desconhecida: $versao.")
        return try {
            Backup(
                geradoEm = Instant.ofEpochMilli(raiz.getLong("geradoEm")),
                categorias = raiz.getJSONArray("categorias").lista(::categoria),
                estabelecimentos = raiz.getJSONArray("estabelecimentos").lista(::estabelecimento),
                lancamentos = raiz.getJSONArray("lancamentos").lista(::lancamento),
                notas = raiz.getJSONArray("notas").lista(::nota),
                itens = raiz.getJSONArray("itens").lista(::item),
            )
        } catch (e: JSONException) {
            throw BackupInvalidoException("Backup corrompido: ${e.message}", e)
        } catch (e: IllegalArgumentException) { // enum ou decimal inválido
            throw BackupInvalidoException("Backup corrompido: ${e.message}", e)
        }
    }

    // ---- Entidade → JSON ----

    private fun categoria(c: Categoria) = JSONObject()
        .put("id", c.id).put("nome", c.nome).put("cor", c.cor).put("tipo", c.tipo.name).put("ativa", c.ativa)

    private fun estabelecimento(e: Estabelecimento) = JSONObject()
        .put("id", e.id).put("cnpj", e.cnpj).put("razaoSocial", e.razaoSocial).put("endereco", e.endereco)
        .put("categoriaPadraoId", e.categoriaPadraoId.orNull())

    private fun lancamento(l: Lancamento) = JSONObject()
        .put("id", l.id).put("tipo", l.tipo.name).put("valorCentavos", l.valorCentavos)
        .put("dataHora", l.dataHora.toEpochMilli()).put("descricao", l.descricao).put("categoriaId", l.categoriaId)
        .put("origem", l.origem.name).put("formaPagamento", l.formaPagamento.orNull())
        .put("estabelecimentoId", l.estabelecimentoId.orNull()).put("observacao", l.observacao.orNull())
        .put("criadoEm", l.criadoEm.toEpochMilli()).put("atualizadoEm", l.atualizadoEm.toEpochMilli())

    private fun nota(n: NotaFiscal) = JSONObject()
        .put("id", n.id).put("chaveAcesso", n.chaveAcesso).put("urlQr", n.urlQr).put("uf", n.uf)
        .put("numero", n.numero).put("serie", n.serie).put("dataEmissao", n.dataEmissao?.toEpochMilli().orNull())
        .put("valorBrutoCentavos", n.valorBrutoCentavos.orNull()).put("descontoCentavos", n.descontoCentavos)
        .put("valorTotalCentavos", n.valorTotalCentavos.orNull()).put("status", n.status.name)
        .put("erroMsg", n.erroMsg.orNull()).put("lancamentoId", n.lancamentoId.orNull())
        .put("importadaEm", n.importadaEm.toEpochMilli())

    private fun item(i: ItemNota) = JSONObject()
        .put("id", i.id).put("notaId", i.notaId).put("ordem", i.ordem).put("codigo", i.codigo)
        .put("descricao", i.descricao).put("quantidade", i.quantidade.toPlainString()).put("unidade", i.unidade)
        .put("valorUnitario", i.valorUnitario.toPlainString()).put("descontoCentavos", i.descontoCentavos)
        .put("valorTotalCentavos", i.valorTotalCentavos).put("categoriaId", i.categoriaId.orNull())

    // ---- JSON → entidade ----

    private fun categoria(o: JSONObject) = Categoria(
        id = o.getLong("id"), nome = o.getString("nome"), cor = o.getLong("cor"),
        tipo = TipoCategoria.valueOf(o.getString("tipo")), ativa = o.getBoolean("ativa"),
    )

    private fun estabelecimento(o: JSONObject) = Estabelecimento(
        id = o.getLong("id"), cnpj = o.getString("cnpj"), razaoSocial = o.getString("razaoSocial"),
        endereco = o.getString("endereco"), categoriaPadraoId = o.longOuNull("categoriaPadraoId"),
    )

    private fun lancamento(o: JSONObject) = Lancamento(
        id = o.getLong("id"), tipo = TipoLancamento.valueOf(o.getString("tipo")), valorCentavos = o.getLong("valorCentavos"),
        dataHora = o.instant("dataHora"), descricao = o.getString("descricao"), categoriaId = o.getLong("categoriaId"),
        origem = OrigemLancamento.valueOf(o.getString("origem")), formaPagamento = o.textoOuNull("formaPagamento"),
        estabelecimentoId = o.longOuNull("estabelecimentoId"), observacao = o.textoOuNull("observacao"),
        criadoEm = o.instant("criadoEm"), atualizadoEm = o.instant("atualizadoEm"),
    )

    private fun nota(o: JSONObject) = NotaFiscal(
        id = o.getLong("id"), chaveAcesso = o.getString("chaveAcesso"), urlQr = o.getString("urlQr"), uf = o.getString("uf"),
        numero = o.getLong("numero"), serie = o.getInt("serie"), dataEmissao = o.longOuNull("dataEmissao")?.let(Instant::ofEpochMilli),
        valorBrutoCentavos = o.longOuNull("valorBrutoCentavos"), descontoCentavos = o.getLong("descontoCentavos"),
        valorTotalCentavos = o.longOuNull("valorTotalCentavos"), status = StatusNota.valueOf(o.getString("status")),
        erroMsg = o.textoOuNull("erroMsg"), lancamentoId = o.longOuNull("lancamentoId"), importadaEm = o.instant("importadaEm"),
    )

    private fun item(o: JSONObject) = ItemNota(
        id = o.getLong("id"), notaId = o.getLong("notaId"), ordem = o.getInt("ordem"), codigo = o.getString("codigo"),
        descricao = o.getString("descricao"), quantidade = BigDecimal(o.getString("quantidade")), unidade = o.getString("unidade"),
        valorUnitario = BigDecimal(o.getString("valorUnitario")), descontoCentavos = o.getLong("descontoCentavos"),
        valorTotalCentavos = o.getLong("valorTotalCentavos"), categoriaId = o.longOuNull("categoriaId"),
    )

    // ---- Auxiliares ----

    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    // optString devolveria "null" (texto) para JSON null; isNull cobre ausente e null.
    private fun JSONObject.textoOuNull(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.longOuNull(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.instant(k: String): Instant = Instant.ofEpochMilli(getLong(k))

    private fun <T> List<T>.toJson(f: (T) -> JSONObject) = JSONArray().also { a -> forEach { a.put(f(it)) } }
    private fun <T> JSONArray.lista(f: (JSONObject) -> T): List<T> = List(length()) { f(getJSONObject(it)) }
}
