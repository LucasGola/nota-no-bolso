# App de Controle Financeiro Pessoal — Plano, Critérios de Aceite e Milestones

> Status: rascunho para validação · 2026-10-06

## 1. Objetivo

App Android pessoal para **ver com o que e quanto estou gastando**. A entrada principal de dados é a leitura do QR Code de NFC-e (cupom fiscal eletrônico). Também é possível lançar entradas e saídas manualmente. Consulta por extrato, gráficos e exportação (CSV/PDF), todos com filtros.

Usuário único, um dispositivo, baixo volume (estimativa: < 100 notas/mês, < 5 mil itens/ano).

---

## 2. Decisões técnicas (propostas)

### 2.1 Onde salvar os dados → **local no aparelho (SQLite via Room) + backup**

**Recomendação:** não usar Neon nem outro Postgres remoto.

| Opção | Prós | Contras |
|---|---|---|
| **Room (SQLite local)** ✅ | Grátis, offline, zero infra, rápido, sem credenciais | Perde-se o dado se perder o celular **sem backup** |
| Neon (Postgres) | Grátis, SQL completo | App Android **não deve** conectar direto no Postgres (credencial embutida no APK = qualquer um com o APK acessa o banco). Exigiria uma API intermediária → infra para manter, sem ganho real para 1 usuário |
| Supabase / Firebase | SDK pronto para mobile, auth, sync | Dependência externa e conta em nuvem para um problema que não exige multi-dispositivo; free tier do Supabase pausa projetos inativos |

**Por quê:** com um usuário e um dispositivo, um banco remoto só resolve um problema — **backup** — e há formas mais simples de resolver isso:
- **Android Auto Backup** (Google Drive da conta, grátis, automático, até 25 MB — sobra para esse volume).
- **Export/Import manual** de backup (arquivo `.json` ou o próprio `.db`) via Storage Access Framework (salvar no Drive, e-mail, etc.).

**Quando reavaliar:** se surgir necessidade real de acessar os dados em outro dispositivo/web ou compartilhar com outra pessoa. A camada de repositório será isolada para permitir adicionar sync depois (ex.: Supabase) sem reescrever telas.

### 2.2 Obtenção dos dados da NFC-e

**Fatos:**
- O QR Code contém uma URL da SEFAZ do estado com o parâmetro `p` no formato `chave|versãoQR|ambiente[|...]`.
  - Exemplo: `p=35261058891504001796650020000075601259531450|3|1`
  - Chave (44 dígitos) decomposta: `35` (UF=SP) · `2610` (out/2026) · `58891504001796` (CNPJ emitente) · `65` (modelo NFC-e) · `002` (série) · `000007560` (número) · `1` (tipo emissão) · `25953145` (código numérico) · `0` (DV).
- **Somente da chave** já se obtêm: UF, ano/mês, CNPJ do emitente, número/série. **Não** se obtêm: valor, itens, data exata, forma de pagamento.
- Não existe API pública e gratuita da SEFAZ que entregue o XML da NFC-e ao consumidor. O webservice `NFeDistribuicaoDFe` exige certificado digital e não atende esse caso. Existem APIs pagas de terceiros.

**Inferência (confiança média, validar no M0):** a página `ConsultaQRCode.aspx` da SEFAZ-SP, acessada pela URL completa do QR, devolve o DANFE NFC-e em HTML (emitente, itens, qtd, valor unitário, total, pagamento) sem captcha. A consulta só pela chave (sem a URL do QR) costuma exigir captcha.

**Abordagem proposta:** o app faz GET na URL do QR e faz o parse do HTML (Jsoup).
- Parser por UF (interface `NfceParser`), começando **só por SP**.
- Se o fetch/parse falhar (sem internet, layout mudou, SEFAZ fora): salva a nota como **pendente**, com chave + URL + dados extraídos da chave, e permite reprocessar depois ou completar manualmente.
- Fallback B (se GET direto for bloqueado): carregar a página num `WebView` oculto e extrair o DOM renderizado.
- O HTML bruto **não** é armazenado por padrão (só os dados parseados + URL), para não inflar o banco.

**Principal risco do projeto:** essa scraping é frágil — depende de layout HTML que a SEFAZ pode mudar sem aviso. Por isso o **M0 é um spike de go/no-go** antes de qualquer outra coisa.

**Formato do QR:** o parser de URL deve aceitar a versão 2 (`chave|2|tpAmb|cIdToken|hash` e variante offline) e a versão 3 (`chave|3|tpAmb`), extraindo sempre a chave e validando o DV (módulo 11).

### 2.3 Stack

| Item | Escolha | Motivo |
|---|---|---|
| Linguagem/UI | Kotlin + Jetpack Compose | Nativo, padrão atual do Android, app só Android |
| Persistência | Room | Padrão oficial, migrações, queries tipadas, Flow reativo |
| Leitura de QR | Google Code Scanner (`play-services-code-scanner`, ML Kit por baixo) | Tela de câmera pronta, sem permissão de câmera no app, sem código de câmera para manter. Exige Google Play services. Plano B, se a tela não atender: CameraX + ML Kit |
| HTTP + parse | OkHttp + Jsoup | Maduros, simples |
| Gráficos | Vico (Compose) | Nativo Compose, mantido. Alternativa: MPAndroidChart (mais antigo, via `AndroidView`) |
| PDF | `android.graphics.pdf.PdfDocument` (nativo) | Sem dependência extra; relatório tabular simples |
| CSV | Escrita manual (RFC 4180, `;` separador, UTF-8 com BOM) | Abre corretamente no Excel pt-BR |
| Arquitetura | MVVM simples (ViewModel + Repository + Room) | Sem Clean Architecture em camadas — overkill para o escopo |
| minSdk | 26 (Android 8) | Cobre praticamente todos os aparelhos atuais |
| CI | GitHub Actions (`ubuntu-latest`, JDK 17, Gradle wrapper) | Grátis em repositório público; em repo privado a cota gratuita (2.000 min/mês) sobra para esse projeto |

**Regra de corretude:** valores monetários armazenados como `Long` em **centavos**. Nunca `Double`/`Float`. Única exceção: quantidade e valor unitário dos itens, que usam `BigDecimal` (ver seção 3).

---

## 3. Modelo de dados (inicial)

```
Categoria(id, nome, cor, icone, tipo: ENTRADA|SAIDA|AMBOS, ativa)

Estabelecimento(id, cnpj UNIQUE, razaoSocial, nomeFantasia, endereco, categoriaPadraoId?)

Lancamento(
  id, tipo: ENTRADA|SAIDA, valorCentavos, dataHora, descricao,
  categoriaId, origem: MANUAL|NFCE, formaPagamento?,
  estabelecimentoId?, observacao?, criadoEm, atualizadoEm
)

NotaFiscal(
  id, chaveAcesso UNIQUE (44), urlQr, uf, numero, serie, dataEmissao,
  valorBrutoCentavos?, descontoCentavos, valorTotalCentavos?,
  status: PENDENTE|IMPORTADA|ERRO, erroMsg?,
  lancamentoId? (FK ON DELETE CASCADE), importadaEm
)
  -- A nota aponta para o lançamento (e não o contrário): excluir o lançamento apaga
  --   nota + itens em cascata. Nota PENDENTE tem lancamentoId nulo.

ItemNota(id, notaId, ordem, codigo, descricao, quantidade, unidade,
         valorUnitario, descontoCentavos, valorTotalCentavos, categoriaId?)
  -- quantidade e valorUnitario: BigDecimal salvo como TEXT, sem arredondar.
  --   Motivo: quantidade pode ser fracionária (0,432 kg) e o valor unitário pode ter
  --   mais de 2 casas (combustível a R$ 5,899/L). Em centavos, essas casas se perderiam.
  -- valorTotalCentavos: valor do item como impresso na nota (é o que soma no total).
```

Uma `NotaFiscal` importada gera **um** `Lancamento` de SAIDA (valor = total pago). Os itens ficam vinculados à nota para detalhamento.

---

## 4. Filtros (modelo único, compartilhado)

Um mesmo objeto `Filtro` é usado em **Extrato, Gráficos e Exportação** (exportar = "exportar o que estou vendo"):

- Período: mês atual (padrão), mês anterior, últimos 30/90 dias, ano, intervalo customizado
- Tipo: entradas, saídas, ambos
- Categorias (multi-seleção)
- Estabelecimentos (multi-seleção)
- Origem: manual, NFC-e, ambos
- Faixa de valor (mín/máx)
- Busca textual (descrição do lançamento e descrição de itens)

---

## 5. Critérios de aceite

### CA-01 — Leitura de QR Code de NFC-e
- [ ] A partir da tela inicial, com no máximo 1 toque, abre a câmera para leitura.
- [ ] Lê o QR de um cupom real em < 3 s em condições normais de luz; há botão de lanterna.
- [ ] Se o QR não for uma URL de NFC-e válida (chave ausente ou DV inválido), exibe mensagem clara e não salva nada.
- [ ] Ao ler uma chave já cadastrada, avisa "nota já importada" e abre a nota existente (sem duplicar).
- [ ] Permite digitar a chave de acesso manualmente (cupom com QR ilegível).

### CA-02 — Importação dos dados da NFC-e (SP)
- [ ] Com internet, extrai: emitente (razão social, CNPJ, endereço), data/hora de emissão, itens (descrição, qtd, unidade, valor unitário, valor total), descontos, valor total e forma de pagamento.
- [ ] Soma dos itens − descontos confere com o total da nota; se não conferir, a nota é salva com alerta visível.
- [ ] Antes de salvar, exibe tela de revisão onde posso ajustar categoria, data e descrição.
- [ ] A categoria é pré-preenchida com a última usada para aquele CNPJ.
- [ ] Sem internet ou com falha de parse: nota salva como **PENDENTE** com dados da chave (CNPJ, mês, número). Há ação "Tentar novamente" individual e "Reprocessar pendentes".
  - "Tentar novamente" passa pela tela de revisão. "Reprocessar pendentes" importa direto, sem revisão: usa a última categoria do CNPJ (ou "Outros") e a razão social como descrição; tudo continua editável no extrato.
  - Pendente que nunca for consultada pode receber o valor à mão: vira lançamento sem itens, com status `MANUAL`.
- [ ] Nota cancelada ou denegada é recusada com mensagem clara e não vira pendente (tentar de novo não adiantaria).
- [ ] Chave digitada é consultada montando a URL no formato do QR v3 (`chave|3|1`). **Não verificado** para notas emitidas com QR v2.
- [ ] Notas pendentes **não** entram nos totais até serem importadas ou completadas manualmente (valor informado à mão).
- [ ] Testado com ≥ 10 cupons reais de pelo menos 5 estabelecimentos diferentes de SP.

### CA-03 — Lançamento manual
- [ ] Criar entrada ou saída com: valor (obrigatório, > 0), data (padrão hoje), categoria (obrigatória), descrição, forma de pagamento, observação.
- [ ] Editar e excluir qualquer lançamento (manual ou NFC-e). Exclusão pede confirmação.
- [ ] Excluir um lançamento de NFC-e remove a nota e os itens (e permite reimportar a mesma chave depois).
- [ ] Campo de valor aceita formato pt-BR (`1.234,56`) e nunca gera erro de arredondamento.

### CA-04 — Categorias
- [ ] Vem com categorias padrão (ex.: Mercado, Alimentação fora, Transporte, Saúde, Casa, Lazer, Salário, Outros).
- [ ] Criar, renomear, mudar cor e desativar categorias. Categoria em uso não pode ser apagada, apenas desativada.

### CA-05 — Extrato
- [ ] Lista lançamentos ordenados por data (mais recente primeiro), agrupados por dia, com totais do dia.
- [ ] Cabeçalho mostra, para o filtro ativo: total de entradas, total de saídas e saldo.
- [ ] Todos os filtros da seção 4 funcionam e podem ser combinados; o filtro ativo fica visível (chips) e pode ser limpo com 1 toque.
- [ ] Tocar num lançamento de NFC-e abre o detalhe da nota: emitente, data/hora, forma de pagamento e **todos os itens** com código, descrição, quantidade, unidade, valor unitário, desconto (se houver) e valor total, além de subtotal, descontos e total pago.
- [ ] A busca textual encontra notas pelo nome de um item (ex.: "leite" lista todas as notas que tenham leite).
- [ ] Rolagem fluida com 5.000 lançamentos. **Não medido ainda**: a filtragem é feita em SQL e a lista é preguiçosa, mas falta testar com volume real.
- Notas do M3:
  - O período padrão é o mês atual, com setas para navegar entre meses. O filtro é único no app e vale também para gráficos e exportação.
  - A busca não diferencia maiúsculas de minúsculas, mas **diferencia acentos** ("pao" não encontra "PÃO"), porque é uma limitação do `LIKE` do SQLite. Se incomodar no uso real, a solução é gravar uma coluna normalizada (sem acentos) para busca.
  - O filtro não sobrevive ao fechamento do app: ao reabrir, volta para o mês atual sem restrições.

### CA-06 — Gráficos
- [ ] **Gastos por categoria** (barras horizontais ordenadas, com valor e %) no período.
- [ ] **Evolução mensal** de entradas × saídas (barras ou linhas) nos últimos N meses.
- [ ] **Top estabelecimentos** por valor gasto.
- [ ] Todos respeitam o mesmo filtro do extrato.
- [ ] Tocar num elemento do gráfico (ex.: categoria) abre o extrato já filtrado por ele.
- [ ] Estado vazio claro quando o filtro não retorna dados.
- Notas do M4:
  - Gráficos desenhados em Compose puro, sem biblioteca. São três formas simples, e evita depender de uma API que muda muito entre versões (Vico).
  - Cores das séries validadas para daltonismo: entradas em azul `#2A78D6` e saídas em laranja `#EB6834` (tema escuro: `#3987E5` / `#D95926`). Verde × vermelho foi reprovado (ΔE 4,2 para deuteranopia) e não é usado para distinguir séries.
  - A evolução mensal usa as mesmas restrições do filtro, mas uma janela própria: período de um mês vira os 6 meses até ele; "todo o período" vira os últimos 12 meses; outros períodos usam os meses que cobrem, no máximo 24.
  - Filtrando só entradas, o gráfico por categoria passa a mostrar entradas.
  - Verificado visualmente no emulador com 70 lançamentos de exemplo: os três gráficos e o toque que abre o extrato filtrado.

### CA-07 — Exportação CSV
- [ ] Exporta os lançamentos do filtro ativo.
- [ ] Opção de incluir itens das notas (uma linha por item, repetindo dados da nota).
- [ ] Arquivo abre corretamente no Excel pt-BR: separador `;`, decimal `,`, UTF-8 com BOM, acentos preservados.
- [ ] Usuário escolhe onde salvar ou compartilha (Drive, e-mail, WhatsApp) via share sheet.

### CA-08 — Exportação PDF
- [ ] Relatório do filtro ativo contendo: período e filtros aplicados, resumo (entradas, saídas, saldo), total por categoria e tabela de lançamentos.
- [ ] Opção de incluir os itens de cada nota (descrição, qtd, unidade, valor unitário, valor total) abaixo do lançamento correspondente.
- [ ] Paginação correta (cabeçalho da tabela repetido, número de página).
- [ ] Mesmas opções de salvar/compartilhar do CSV.
- Notas do M5:
  - Exportação pelo menu ⋮ do extrato. Usa o filtro atual e oferece "Compartilhar" (share sheet) ou "Salvar no aparelho…" (seletor de arquivos do sistema).
  - CSV: o valor sai com sinal (saída negativa), para a soma da coluna dar o saldo. Com itens, cada item vira uma linha que repete o lançamento; nesse modo, some a coluna "Valor do item". Textos que começam com `=`, `+`, `-` ou `@` recebem um apóstrofo na frente, para o Excel não executar fórmula vinda da descrição da nota (CSV injection).
  - PDF: A4 com o `PdfDocument` nativo. A paginação repete o cabeçalho da tabela e nunca deixa um título de seção sozinho no pé da página (testado com 200 lançamentos).
  - Verificado no emulador: o PDF com itens foi gerado, aberto e conferido, e o compartilhamento via FileProvider abriu a share sheet. **Não verificado:** "Salvar no aparelho" até o fim. O seletor abriu com o nome sugerido, mas o app Arquivos do emulador travou antes de concluir.

### CA-09 — Backup e restauração
- [ ] Android Auto Backup habilitado para o banco.
- [ ] Exportar backup completo para arquivo e restaurar a partir dele (com confirmação, substitui os dados atuais).
- [ ] Restaurar um backup num aparelho limpo reproduz exatamente os mesmos totais.

### CA-10 — CI/CD (GitHub Actions)
- [ ] Todo push (qualquer branch) e PR executa: build `assembleDebug`, testes unitários e Android Lint.
- [ ] Falha em qualquer etapa marca o commit como vermelho.
- [ ] O APK debug fica disponível como artifact do workflow (retenção de 14 dias).
- [ ] Push de tag `v*` gera APK **release assinado** e publica numa GitHub Release.
- [ ] Keystore e senhas ficam **só** em GitHub Secrets (keystore em base64); nada sensível versionado no repositório.
- [ ] `versionCode` derivado do número da execução (`GITHUB_RUN_NUMBER`), para que o APK novo sempre instale por cima do anterior.
- [ ] Cache de Gradle habilitado; build completo em < 10 min.

### CA-11 — Não funcionais
- [ ] Funciona offline (exceto a importação da NFC-e, que fica pendente).
- [ ] Nenhum dado sai do aparelho além da consulta à SEFAZ e dos backups iniciados/configurados por mim.
- [ ] Valores monetários sempre em centavos (`Long`); testes unitários cobrem parse de valor pt-BR, validação de chave (DV) e parser de HTML da SEFAZ-SP (com HTMLs reais salvos como fixtures).
- [ ] Datas armazenadas em UTC, exibidas no fuso `America/Sao_Paulo`.

---

## 6. Fora de escopo (v1)

- Sincronização em nuvem / múltiplos dispositivos / versão web
- NFC-e de outros estados (arquitetura preparada, implementação só SP)
- NF-e modelo 55, notas de serviço, leitura de boleto, integração bancária / Open Finance
- Orçamentos/metas, alertas, recorrências
- Categorização automática por item (ex.: "LEITE" → Mercado/Laticínios) — avaliar na v2
- Login/autenticação (proteção opcional por biometria pode entrar como melhoria)

---

## 7. Milestones

Ordem definida por **risco primeiro**: o que pode inviabilizar o projeto é validado antes de construir telas.

### M0 — Spike de viabilidade da NFC-e (go/no-go)
- Coletar ≥ 10 URLs de QR reais de SP (estabelecimentos diferentes).
- Teste fora do app (script ou teste JVM): GET na URL + parse com Jsoup.
- Salvar os HTMLs como fixtures de teste.
- **Saída:** decisão documentada — (a) GET + Jsoup funciona, (b) precisa de WebView, ou (c) inviável → plano B (lançamento manual com dados da chave + valor digitado).
- **Aceite:** extração correta de emitente, data, itens e total em ≥ 90% dos cupons coletados.

**Resultado parcial (2026-10-06), 1 cupom:**
- GET simples na URL do QR (sem cookies, sem captcha) devolve HTTP 200 com o DANFE completo. Abordagem (a) confirmada.
- Campos disponíveis: razão social, CNPJ, endereço, itens (descrição, código, qtd, unidade, valor unitário, valor total), qtd de itens, valor a pagar, formas de pagamento com valor, número, série, data/hora de emissão, chave.
- Problemas de qualidade da própria SEFAZ: forma de pagamento aparece como "Outros" (o layout não mapeia todos os códigos, provavelmente PIX/vale); "Troco" exibe `NaN`. O parser tolera os dois.
- Parser implementado em `NfceParserSp`, com o HTML real como fixture de teste.
- **Pendente:** cupons com desconto, item a granel (kg), mais de uma forma de pagamento nomeada e nota cancelada, para fechar os ≥ 10 cupons do critério.

### M1 — Fundação + lançamento manual
- Projeto Kotlin/Compose, Room com schema da seção 3, navegação (Extrato · Gráficos · + · Config).
- Workflow do GitHub Actions (build + testes + lint + APK como artifact) — configurado **já no primeiro commit**, para que tudo a partir daí seja validado.
- CRUD de categorias e lançamentos manuais.
- **Aceite:** CA-03, CA-04, CA-10 (parte debug).

### M2 — Leitura de QR e importação de NFC-e
- CameraX + ML Kit, parser da URL/chave, `NfceParser` SP, tela de revisão, fila de pendentes.
- **Aceite:** CA-01, CA-02.

### M3 — Extrato com filtros
- Modelo `Filtro` compartilhado, tela de extrato, detalhe da nota.
- **Aceite:** CA-05.

### M4 — Gráficos
- Os três gráficos com filtro e drill-down para o extrato.
- **Aceite:** CA-06.

### M5 — Exportação
- CSV e PDF a partir do filtro ativo, share sheet.
- **Aceite:** CA-07, CA-08.

### M6 — Backup, polimento e release pessoal
- Auto Backup + export/import, revisão de estados vazios/erro.
- Release assinado via tag no GitHub Actions.
- **Aceite:** CA-09, CA-10 (parte release), CA-11 e regressão de todos os anteriores.

> M1 e M0 podem andar em paralelo; M2 depende do resultado do M0.
> Após M3 o app já é usável no dia a dia (registrar + consultar). M4–M6 agregam visualização e segurança dos dados.

---

## 8. Decisões em aberto

1. **Armazenamento local + backup** (recomendado) vs. nuvem — confirmar.
2. **Categorizar por nota** (v1) vs. por item. Os itens são **sempre** salvos com todos os detalhes (requisito confirmado); a decisão em aberto é só se cada item recebe uma categoria própria. Por item dá visão mais fina ("quanto gastei com carne?"), mas exige classificar dezenas de itens por cupom. Proposta: deixar o campo no schema e decidir na v2 com base no uso real.
3. Saldo: apenas fluxo do período, ou saldo acumulado com valor inicial? (proposta: só fluxo do período na v1).
