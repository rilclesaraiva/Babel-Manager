# CLAUDE.md

## Projeto
Babel Manager — gerenciador de senhas para Android. O cofre fica só no aparelho, criptografado; a pessoa escolhe onde guardar a cópia de backup (pasta local, Google Drive ou outro provedor de documentos do Android).
- Tipo: público · Público-alvo: global
- Nome nos arquivos: `babelmanager` (ex: babelmanager_v1.2.0.apk; título da Release: "Babel Manager v1.2.0")

## Mantenedor
- Nome: rilclesaraiva · Contato: issues e saraivarilcle@gmail.com
- Apoio: https://buymeacoffee.com/saraivarilcle (só no README; no app nunca citar doação) · Licença: GPL-3.0
- Segredos ficam em: Google Drive/Babel Manager/
- País (assinatura, `C=`): BR

## Stack
- Stack: Kotlin + Jetpack Compose + Material 3 Expressive (`compose-bom` e demais versões em `gradle/libs.versions.toml`)
- Build: `./gradlew assembleRelease` · Testes: `./gradlew testDebugUnitTest`
- Mapa: core → `app/src/main/java/com/babelmanager/app/core` · data → `.../data` · features → `.../features`
- Versão: `app/build.gradle.kts` (`versionName`) · lida no CI por `VERSION_CMD`
- Versionamento: SemVer (regra; o número é calculado pelo [Unreleased], nunca escolhido)
- Release: `./gradlew assembleRelease` → APK assinado (um `.apk` por Release)
- Pacote (Android): `com.babelmanager.app`, fixo · R8 ativo no release (sem ofuscação), nunca desligar
- Ícone: `app/src/main/res/drawable/ic_launcher_foreground.xml` (+ `_monochrome` e fundo), só muda se o mantenedor pedir
- Sobre: entrada própria nos ajustes (versão lida do build, código-fonte, novidades,
  issues, legal e apoio). Visual em cartões agrupados (`core/ui/Grouped.kt`: `GroupLabel`, `GroupedItems`), igual ao dos outros apps do mantenedor (BoomListener, Joule Arc); Atualizações e Sobre nos Ajustes usam o mesmo estilo. Licenças em tela própria (`LicensesScreen`, lista fixa: atualizar ao mudar dependências).
- Atualizações: sim, via Releases públicas. Repositório em `babel.repo` (`gradle.properties`);
  enquanto `babel.repo` for `OWNER/REPO` o aviso fica desligado (hoje: `rilclesaraiva/Babel-Manager`) e os links do Sobre ficam ocultos.
  Consulta ao abrir e no máximo a cada 10 min ao voltar, com ETag; "Agora não" só na
  sessão; sino adia 1 semana / 1 mês ou pula a versão; controles em Ajustes → Sistema

## Arquitetura
- Cofre = um arquivo JSON em `filesDir/vault.babel`: parâmetros de KDF no cabeçalho + conteúdo cifrado (AES-256-GCM; chave por PBKDF2-HMAC-SHA256, 600 mil iterações). A chave só existe em memória com o cofre aberto.
- Biometria: o Keystore (chave exigindo biometria forte) cifra a chave do cofre; o resultado fica nas preferências. Trocar a senha mestra ou restaurar backup desliga a biometria.
- Backup: cópia do arquivo **cifrado** no local escolhido via Storage Access Framework (`BackupRepository`). `allowBackup=false`: o Android não copia o cofre por conta própria.
- Autofill: `BabelAutofillService` (entrada, raiz do pacote). Cofre trancado: só "Preencher com o Babel Manager", que abre `AutofillActivity` (desbloqueio; se houver uma única sugestão, preenche sem mostrar lista). Cofre aberto (depois de `AutoLock.checkExpired()`): oferece direto as entradas que combinam (título e usuário, **nunca a senha em texto de apresentação**), em lista suspensa e inline no teclado (`AutofillResponses.kt`), mais "Outras entradas…" (autenticação de dataset → lista completa). **Toda autenticação é de dataset** (o app devolve um `Dataset`, que o Android preenche direto); autenticação da resposta inteira devolveria uma lista para tocar de novo. Campos: `data/autofill/FieldParser.kt`; sugestões por domínio/pacote: `EntryMatcher.kt` (testado). Não salva senhas novas.
- Entrada ligada a app: o campo `url` guarda `androidapp://<pacote>` (`AppUri`, em `EntryMatcher.kt`); seletor em `features/vault/AppUi.kt` e leitura em `data/apps/InstalledApps.kt`. Visibilidade por `<queries>` de apps com ícone de lançador, **sem** `QUERY_ALL_PACKAGES`. Entrada de app nunca combina com site.
- Sem servidor. Chamadas de rede (todas ao GitHub): última Release (`UpdateRepository`) e foto do mantenedor no Sobre (`AvatarStore`). Nada do cofre vai nelas.
- Segurança de tela: `FLAG_SECURE` ligado por padrão; trava automática por tempo em segundo plano (`AutoLock`); área de transferência marcada como sensível e limpa depois de um tempo.

## Regras de estrutura
- Feature não importa outra feature; o compartilhado sobe para core/data.
- Navegação entre features passa pela entrada (`AppRoot.kt`, rotas registradas lá).
- Banco, APIs e arquivos só via data. Core sem regra de negócio.
- Seguir as convenções da stack. Nenhuma dependência nova sem justificar.
  - `androidx.biometric`: único jeito suportado de usar o BiometricPrompt com CryptoObject.
  - `material-icons-extended`: ícones; o R8 remove os não usados.
  - `androidx.fragment`: fixada explicitamente porque `biometric` 1.1.0 puxa fragment 1.2.5, que lança `IllegalArgumentException` (código de requisição) ao abrir os seletores de arquivo via Activity Result API. Não remover enquanto o `biometric` não exigir fragment ≥ 1.3.
  - `androidx.autofill`: só para montar as sugestões embutidas no teclado (`InlineSuggestionUi`); sem ela o Android só mostraria a lista suspensa.
  - KDF: PBKDF2 da plataforma (zero dependência). Os parâmetros ficam no arquivo do cofre, então trocar para Argon2id no futuro não quebra cofres existentes.

## Regras de segurança do produto
- Nunca registrar (log) senha, chave ou conteúdo do cofre. Nunca enviar nada do cofre para fora do aparelho, exceto a cópia cifrada que a pessoa mandou gravar no local de backup que ela escolheu.
- `INTERNET` só serve para o aviso de atualização e para a foto pública do mantenedor na tela Sobre (`AvatarStore`, github.com/<usuário>.png, cache de 7 dias). Qualquer outra chamada de rede muda a política de privacidade no mesmo commit.
- O arquivo em disco nunca contém texto aberto. Exportar CSV é sempre uma ação explícita, com aviso.

## Fluxo de trabalho
- Uma tarefa por vez, mudanças pequenas. Não refatorar fora do escopo.
- Tudo direto na `main`: sem branches (`wip/`, `feature/`…) e sem PR. Cada push
  gera o build para vistoria. Se o ambiente exigir commit ao parar, é na `main`.
- Rodar build e testes antes do push quando o ambiente permitir. Se a sessão não
  consegue compilar (ex: nuvem sem SDK ou sem acesso às dependências), o CI é a
  única prova, e a IA diz isso em vez de afirmar que testou.
- Só dizer que terminou quando o CI daquele commit passar no GitHub.
  CI vermelho é corrigido antes de qualquer tarefa nova.
- Registrar toda mudança em [Unreleased] no CHANGELOG.
- Preferir remover a acumular: o que não é usado sai.
- Na dúvida, perguntar. Nunca inventar API.
- Bloqueio de segurança do ambiente (proxy, permissão, leitura de credencial) nunca
  é contornado: o passo vai para o CI ou para o mantenedor.

## Release (regra absoluta)
- Só com confirmação explícita do mantenedor citando a versão
  ("confirmo release v1.2.0"), na conversa.
  "Ok", "beleza" ou silêncio não contam.
- A versão é calculada pela regra de versionamento. Número pedido fora da regra:
  explicar o número certo e esperar a confirmação com ele.
- Sem confirmação: nada de bump de versão ou mover [Unreleased].
- A IA nunca cria tag nem Release: o commit `chore(release): vX.Y.Z` na main basta,
  e o CI cria a tag e publica quando vê versão nova com seção no CHANGELOG.
- Sempre dar ao mantenedor o link do run do CI **daquele commit** (`https://github.com/rilclesaraiva/Babel-Manager/actions/runs/<id>`, seção Artifacts), conferindo que o id é do commit entregue; nunca link de outro run, de artefato de conversa ou do repositório.
- Toda atualização vai para os artefatos do Actions (build para vistoria) e a IA
  avisa com o link. O mantenedor vistoria e só então pede o release público.
- Entre a vistoria e o commit de release, só mudam versão e CHANGELOG; mudou código, nova vistoria.

## Segredos
- Nunca commitar chaves, keystores, senhas ou .env.
- Secrets de assinatura: a IA gera e envia os arquivos direto
  ao mantenedor, sem nunca ler ou exibir o conteúdo. O mantenedor guarda no local
  definido em *Mantenedor*. Nunca no repo, nunca escritos no texto do chat.
- Cadastrar secrets no GitHub é sempre tarefa manual do mantenedor.

## Idioma
- README e CHANGELOG: inglês (global). O app tem textos em inglês (padrão) e português do Brasil (`values-pt-rBR`); os dois arquivos de strings são gerados em paridade, então toda string nova entra nos dois.

## Projeto público
- README com a seção de transparência sobre IA (nunca remover) e, no fim, a de
  apoio e contato com os dados de *Mantenedor*.
- No app: nunca citar doação ou pagamento. Só "Quer apoiar o projeto? Entre no
  repositório do projeto" com link para o repo.
- Páginas legais em `docs/` (privacidade, termos), publicadas
  no GitHub Pages e linkadas no README e no app. Sem cookies: não há `cookies.md`.
- Coleta nova (SDK, permissão, chamada de rede, cookie) → atualizar a política
  no mesmo commit.
- Nenhuma afirmação sem respaldo: o que README, loja e páginas legais dizem
  precisa ser verdade no código. Na dúvida, não afirmar.

## Contexto atual
- Foco: primeira versão (0.0.0 → primeiro release a calcular pelo [Unreleased]).
- Problemas conhecidos:
  - A sessão que criou o projeto não tinha Android SDK nem acesso ao Maven: **nada foi compilado pela sessão** (o CI compilou e passou depois). Versões de plugins e bibliotecas (`libs.versions.toml`) e as APIs do Material 3 Expressive foram escritas sem verificação; se o primeiro CI falhar, corrigir é a prioridade.
  - Versões das actions do `ci.yml` seguem as do documento de estrutura e não foram conferidas.
  - Autofill só preenche (sem `onSaveRequest`) e não usa o Credential Manager (passkeys). Heurística de campos pode falhar em telas incomuns.
  - A lista de palavras do gerador é própria e pequena (572 palavras, ≈ 9,2 bits por palavra); a entropia mostrada é calculada a partir dela. Candidato: trocar por uma lista maior.
  - O check-up só encontra senhas repetidas e fracas; não verifica vazamentos.

## Nota de build (M3 Expressive)
- As APIs Expressive (`MaterialExpressiveTheme`, `ToggleButton`, `LoadingIndicator`, `LargeFlexibleTopAppBar`) só são públicas no `material3` **1.5.0-alpha**; na 1.4.0 são internas. Versão fixada em `libs.versions.toml` (`material3 = "1.5.0-alpha10"`), fora do BOM.
- Alphas mais novas (ex.: alpha29) exigem `compileSdk` 37 e AGP mais novo. Subir só junto com o AGP.
- Primeira rodada do CI verde com esta combinação (build, testes e release pulado por falta de seção 0.0.0).
