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

### Configurar a assinatura do release (uma vez)

```sh
keytool -genkeypair -v -keystore release.jks -alias financas -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > release.jks.b64
```

Em *Settings → Secrets and variables → Actions*, crie:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | conteúdo de `release.jks.b64` |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | `financas` |
| `KEY_PASSWORD` | senha da chave |

**Guarde o `release.jks` e as senhas fora do repositório.** Se perder o keystore, o Android não aceita atualizar o app instalado: será preciso desinstalar, e isso apaga os dados locais.

**Trocar o APK debug pelo release apaga os dados.** Os dois têm o mesmo `applicationId`, mas assinaturas diferentes, então o Android recusa instalar um por cima do outro. Antes de trocar: *Extrato → ⋮ → Backup e restauração → Salvar backup…*, desinstale, instale o release e restaure o arquivo.

## Backup

- **Auto Backup do Android:** o banco vai para a conta Google do aparelho (se o backup estiver ativado nas configurações) e volta ao reinstalar o app ou configurar um celular novo. Regras em `res/xml/regras_backup.xml` e `regras_extracao_dados.xml`.
- **Arquivo `.json`:** *Extrato → ⋮ → Backup e restauração*. Contém todas as tabelas, com os ids preservados; restaurar substitui todos os dados numa única transação (se algo falhar, nada muda). Backups de versões mais novas do app são recusados.

## Fixtures de NFC-e

`app/src/test/resources/nfce/` guarda HTMLs reais da SEFAZ usados nos testes do parser. Quando a SEFAZ mudar o layout, esses testes quebram primeiro. Ao adicionar cupons, confira se não há CPF do consumidor no HTML.
