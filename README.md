# Finanças — controle de gastos pessoal (Android)

Lê o QR Code da NFC-e, importa os itens da nota pela consulta pública da SEFAZ-SP e guarda tudo localmente (Room/SQLite).
Escopo, critérios de aceite e milestones: [PLANO.md](PLANO.md).

## Build local

Requisitos: JDK 25 (definido em `gradle/gradle-daemon-jvm.properties`; o Gradle baixa se faltar) e Android SDK (platform 37; o Gradle baixa automaticamente se faltar). Com o Android Studio instalado, basta abrir a pasta.

```sh
./gradlew assembleDebug        # APK em app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # testes unitários (parser da NFC-e, chave, valores)
./gradlew lintDebug
```

## CI (GitHub Actions)

`.github/workflows/android.yml`:

- **Todo push / PR:** build debug + testes + lint. O APK fica em *Actions → execução → Artifacts* por 14 dias.
- **Tag `v*`** (ex.: `git tag v0.1.0 && git push --tags`): gera APK release assinado e cria uma GitHub Release.

### Assinatura

O app é assinado com uma única chave, guardada **fora do repositório**. Ela é a `debug.keystore` do computador onde os primeiros APKs foram gerados, com a senha trocada. Assim, os APKs já instalados aceitam as atualizações sem desinstalar.

No GitHub (*Settings → Secrets and variables → Actions*):

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | keystore em base64 (`base64 -w0 financas-release.jks`) |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | `androiddebugkey` |
| `KEY_PASSWORD` | mesma senha do keystore (PKCS12) |

Com os secrets configurados, tanto o APK de teste de cada push quanto o release de cada tag `v*` saem com essa chave. Sem eles (ex.: PR de fork), o build de teste usa uma chave descartável e o release falha de propósito.

**Se perder a chave, o Android não aceita atualizar o app instalado**: será preciso desinstalar, e isso apaga os dados. Guarde o `.jks` e a senha em dois lugares.

**Distribua só pelas Releases do GitHub.** O `versionCode` do CI é o número da execução, e o de um build local é sempre 1. Por isso, um APK gerado localmente não instala por cima de um release (o Android recusa versão menor).

## Backup

- **Auto Backup do Android:** o banco vai para a conta Google do aparelho (se o backup estiver ativado nas configurações) e volta ao reinstalar o app ou configurar um celular novo. Regras em `res/xml/regras_backup.xml` e `regras_extracao_dados.xml`.
- **Arquivo `.json`:** *Extrato → ⋮ → Backup e restauração*. Contém todas as tabelas, com os ids preservados; restaurar substitui todos os dados numa única transação (se algo falhar, nada muda). Backups de versões mais novas do app são recusados.

## Fixtures de NFC-e

`app/src/test/resources/nfce/` guarda HTMLs reais da SEFAZ usados nos testes do parser. Quando a SEFAZ mudar o layout, esses testes quebram primeiro. Ao adicionar cupons, confira se não há CPF do consumidor no HTML.
