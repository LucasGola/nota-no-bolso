package dev.lucasgola.financas.nfce

import androidx.room.withTransaction
import dev.lucasgola.financas.data.AppDatabase
import dev.lucasgola.financas.data.Estabelecimento
import dev.lucasgola.financas.data.Lancamento
import dev.lucasgola.financas.data.NotaFiscal
import dev.lucasgola.financas.data.OrigemLancamento
import dev.lucasgola.financas.data.StatusNota
import dev.lucasgola.financas.data.TipoLancamento
import dev.lucasgola.financas.util.formatarCnpj
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant

sealed interface Preparo {
    data class Invalida(val mensagem: String) : Preparo
    data class JaImportada(val lancamentoId: Long) : Preparo
    /** Consulta OK; aguardando revisão do usuário antes de gravar. */
    data class Pronta(val nota: NotaImportada, val urlQr: String, val categoriaSugeridaId: Long?) : Preparo
    /** Consulta falhou; a nota foi salva como PENDENTE. */
    data class Falhou(val mensagem: String) : Preparo
}

data class ResultadoReprocesso(val importadas: Int, val falhas: Int)

/** Orquestra a importação de NFC-e: consulta na SEFAZ, deduplicação por chave e gravação transacional. */
class ImportacaoRepository(private val db: AppDatabase, private val service: NfceService) {

    private val notaDao = db.notaDao()
    private val estabelecimentoDao = db.estabelecimentoDao()
    private val lancamentoDao = db.lancamentoDao()

    suspend fun preparar(conteudoQr: String): Preparo {
        val chave = ChaveAcesso.doQrCode(conteudoQr)
            ?: return Preparo.Invalida("Este QR Code não é de uma NFC-e.")
        if (chave.uf != ChaveAcesso.UF_SP) return Preparo.Invalida("Por enquanto só notas de SP são suportadas.")

        val existente = notaDao.buscarPorChave(chave.valor)
        if (existente != null && existente.status != StatusNota.PENDENTE && existente.lancamentoId != null) {
            return Preparo.JaImportada(existente.lancamentoId)
        }

        return try {
            val nota = service.consultar(conteudoQr)
            val sugerida = estabelecimentoDao.buscarPorCnpj(nota.emitente.cnpj)?.categoriaPadraoId
            Preparo.Pronta(nota, conteudoQr.trim(), sugerida)
        } catch (e: NfceParseException) {
            if (e.permanente) return Preparo.Invalida(e.message.orEmpty())
            registrarPendente(chave, conteudoQr.trim(), existente, "Não foi possível ler a nota: ${e.message}")
        } catch (e: IOException) {
            registrarPendente(chave, conteudoQr.trim(), existente, "Falha ao consultar a SEFAZ: ${e.message ?: "sem conexão"}")
        }
    }

    private suspend fun registrarPendente(chave: ChaveAcesso, url: String, existente: NotaFiscal?, erro: String): Preparo {
        if (existente == null) notaDao.inserir(notaPendente(chave, url, erro))
        else notaDao.atualizar(existente.copy(erroMsg = erro))
        return Preparo.Falhou(erro)
    }

    /** Grava lançamento + nota + itens e lembra a categoria escolhida para o CNPJ. Retorna o id do lançamento. */
    suspend fun confirmar(
        nota: NotaImportada,
        urlQr: String,
        categoriaId: Long,
        descricao: String,
        dataHora: Instant,
    ): Long = db.withTransaction {
        val existente = notaDao.buscarPorChave(nota.chave.valor)
        check(existente == null || existente.status == StatusNota.PENDENTE) { "Nota já importada" }

        val estabelecimentoId = salvarEstabelecimento(
            cnpj = nota.emitente.cnpj,
            razaoSocial = nota.emitente.razaoSocial,
            endereco = nota.emitente.endereco,
            categoriaId = categoriaId,
        )
        val lancamentoId = lancamentoDao.inserir(nota.paraLancamento(categoriaId, descricao, dataHora, estabelecimentoId))
        val notaId = if (existente == null) {
            notaDao.inserir(nota.paraNotaFiscal(urlQr, lancamentoId))
        } else {
            notaDao.atualizar(nota.paraNotaFiscal(urlQr, lancamentoId, id = existente.id))
            notaDao.excluirItens(existente.id)
            existente.id
        }
        notaDao.inserirItens(nota.paraItens(notaId))
        lancamentoId
    }

    /** Pendente que nunca será consultada com sucesso: vira lançamento com valor digitado, sem itens. */
    suspend fun completarManual(
        pendente: NotaFiscal,
        valorCentavos: Long,
        categoriaId: Long,
        descricao: String,
        dataHora: Instant,
    ): Long = db.withTransaction {
        val chave = requireNotNull(ChaveAcesso.of(pendente.chaveAcesso))
        val estabelecimentoId = salvarEstabelecimento(
            cnpj = chave.cnpjEmitente,
            razaoSocial = null,
            endereco = null,
            categoriaId = categoriaId,
        )
        val lancamentoId = lancamentoDao.inserir(
            Lancamento(
                tipo = TipoLancamento.SAIDA,
                valorCentavos = valorCentavos,
                dataHora = dataHora,
                descricao = descricao,
                categoriaId = categoriaId,
                origem = OrigemLancamento.NFCE,
                estabelecimentoId = estabelecimentoId,
            )
        )
        notaDao.atualizar(
            pendente.copy(
                status = StatusNota.MANUAL,
                valorTotalCentavos = valorCentavos,
                dataEmissao = dataHora,
                erroMsg = null,
                lancamentoId = lancamentoId,
            )
        )
        lancamentoId
    }

    /**
     * Tenta de novo todas as pendentes, sem a tela de revisão: usa a última categoria do CNPJ
     * (ou "Outros") e a razão social como descrição. Tudo continua editável no extrato.
     */
    suspend fun reprocessarPendentes(pendentes: List<NotaFiscal>): ResultadoReprocesso {
        var importadas = 0
        var falhas = 0
        for (p in pendentes) {
            try {
                val nota = service.consultar(p.urlQr)
                val categoriaId = estabelecimentoDao.buscarPorCnpj(nota.emitente.cnpj)?.categoriaPadraoId
                    ?: db.categoriaDao().categoriaPadraoSaida()?.id
                    ?: error("Nenhuma categoria de saída ativa")
                confirmar(nota, p.urlQr, categoriaId, nota.emitente.razaoSocial, nota.dataEmissaoInstant)
                importadas++
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notaDao.atualizar(p.copy(erroMsg = "Falha ao reprocessar: ${e.message}"))
                falhas++
            }
        }
        return ResultadoReprocesso(importadas, falhas)
    }

    suspend fun descartarPendente(pendente: NotaFiscal) = notaDao.excluir(pendente)

    private suspend fun salvarEstabelecimento(
        cnpj: String,
        razaoSocial: String?,
        endereco: String?,
        categoriaId: Long,
    ): Long {
        val atual = estabelecimentoDao.buscarPorCnpj(cnpj)
        return if (atual == null) {
            estabelecimentoDao.inserir(
                Estabelecimento(
                    cnpj = cnpj,
                    razaoSocial = razaoSocial ?: formatarCnpj(cnpj),
                    endereco = endereco.orEmpty(),
                    categoriaPadraoId = categoriaId,
                )
            )
        } else {
            estabelecimentoDao.atualizar(
                atual.copy(
                    razaoSocial = razaoSocial ?: atual.razaoSocial,
                    endereco = endereco ?: atual.endereco,
                    categoriaPadraoId = categoriaId,
                )
            )
            atual.id
        }
    }
}
