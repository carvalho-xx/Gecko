# Plano Mestre de Criação — Projeto Gecko (Android)

> **Propósito:** Roteiro executável, sequencial e atômico para construir integralmente o aplicativo nativo **Gecko** (Kotlin, Jetpack Compose, Room/KSP, Hilt, WorkManager, BYOK Offline-First), derivado de toda a documentação técnica e de design do repositório.
> **Estrutura obrigatória:** Cada etapa atômica contém **Planejamento**, **Implementação** e **Teste**, respeitando a ordem de dependências e a Matriz MoSCoW (**MVP v1.0 → Expansão v1.1 → Evolução v2.0**).

---

## 0. Governança, Invariantes e Referências Documentais

### Invariantes de Engenharia (ANIR — Não Negociáveis)
1. **Pureza do Domínio (`[ARCH: CLEAN(MVVM) ! ANDROID_SDK_IN_DOMAIN]`):** Proibido importar `android.*`, `androidx.*`, `Context` ou Room dentro de `domain/`. Toda lógica de domínio compila em JVM pura.
2. **Restrição Tipológica KSP (`[DB: ROOM_KSP_PRIMITIVE(Long) ! VALUE_CLASS(Duration)]` — RNF07):** Proibido usar `kotlin.time.Duration` ou `@TypeConverter` de value classes em `@Entity`. Todas as durações e timestamps são persistidos como `Long` (milissegundos absolutos), com formatação estética exclusiva na ViewModel/UI.
3. **Delta Baseline (`[SYNC: DELTA_BASELINE ! OVERWRITE]` — RF07):** Manter `platform_reported_time_ms` e `local_logged_time_ms` em colunas separadas; nunca sobrescrever tempo local com API remota; soma consolidada somente em memória na apresentação.
4. **Segurança BYOK (`[API: BYOK_MODEL ! HARDCODED_SECRETS]` — RNF03):** Todas as chaves e o cookie `npsso` residem exclusivamente em `EncryptedSharedPreferences` / Android Keystore; nunca imprimir em logs ou texto plano.
5. **Backlog Visível vs. Backlog Elegível (`RN-BKL-01`):**
   - `BacklogPolicy.VISIBLE = {BACKLOG, PAUSED, ABANDONED}`: somente exibição/contagem na `LibraryScreen` e busca local.
   - `BacklogPolicy.ELIGIBLE = {BACKLOG}`: fonte exclusiva para toda sugestão e planejamento (RF15 Cenários A/B, RF16, `CalculateSmartScheduleUseCase`, widgets), obtida apenas via `GetEligibleBacklogGamesUseCase` com filtro SQL explícito `status = 'BACKLOG'` (proibido `!=` ou `NOT IN`).
6. **Escopo RF18 Atualizado:** O módulo `AnalyticsHubScreen` (`Year in Review`) é 100% interno/offline, **sem** geração de cartão 9:16 para Status/Stories e **sem** compartilhamento via Android Sharesheet.

### Invariantes de UI/UX (Material Design 3 OLED Dark)
- **Superfícies:** Background `#0D1210`–`#121212`, cards `#18201C`–`#242E28`, **Tonal Elevation** (proibido *hard drop shadows*), destaque verde esmeralda (`#00E676` / `#2ECC71`), bordas inativas obrigatórias (`1dp` `#2A3530`/`#424242`).
- **Acessibilidade e Viewport (`360dp`):** Alvos de toque mínimos `48x48dp`; grade de `8pt`; em 3 colunas horizontais, título máximo de 1 palavra (≤ 10 caracteres) e subtítulo ≤ 14 caracteres.
- **UDF Visual Exclusivo:** Nunca exibir cursor `|` junto com `✓`; nunca exibir botão `Validar` junto com estado `✓ Conectado`; credenciais multi-campo (IGDB, Steam, RetroAchievements) em Modal Bottom Sheet; apenas chave única (`npsso`, RAWG) inline.
- **Testabilidade:** `Modifier.testTag(...)` nos nós acionáveis; campos BYOK com `PasswordVisualTransformation()` testados via `onNodeWithTag` + `performTextInput()` (proibido `onNodeWithText` para segredos).

### Protocolo Obrigatório de Ferramentas MCP (`room-sqlite-inspector` e `android-build-mcp`)
1. **`room-sqlite-inspector` (Obrigatório em toda etapa com Room/SQLite/DAOs/Migrations):**
   - Sempre validar schema real, integridade de colunas temporais `INTEGER` (`Long` ms), queries explícitas (ex.: `RN-BKL-01`) e planos de execução (`EXPLAIN QUERY PLAN`) via `schema`, `table_info`, `query` e `explain`.
   - Toda mutação em tabelas pai com filhos `ForeignKey.CASCADE` (`GAME`, `PLAYTHROUGH`, `PLATFORM`, `CUSTOM_LIST`) deve usar `@Upsert` ou `@Insert(ABORT)` + `@Update` (proibido `OnConflictStrategy.REPLACE` em tabelas pai) e ser validada contra SQLite real em teste instrumentado.
2. **`android-build-mcp` (Obrigatório em builds, testes e validação em dispositivo/emulador):**
   - Usar `list_devices`, `build_app`, `test_app`, `test_instrumented` e `lint` para compilar e executar testes unitários e instrumentados no dispositivo conectado.
   - A partir da **Fase 2 (FGS/WorkManager)** e **Fase 3 (UI Jetpack Compose)**, usar também `install_app`, `launch_app`, `snapshot_ui`, `screenshot`, `get_logs` e `get_crash_logs` para inspecionar telas, estados visuais e ausência de crashes diretamente no aparelho.

### Fontes Documentais Consolidadas
- Requisitos: `docs/requirements/SRS_Gecko_Android_v2.1_Padrao_Engenharia.docx` e `docs/requirements/Planejamento Ágil e Backlog - Projeto Gecko.docx`
- Arquitetura e Dados: `docs/architecture/DOCUMENTO DE ARQUITETURA DE SOFTWARE (DAS).docx`, `docs/architecture/DER.txt`, `docs/architecture/Diagrama de Máquina de Estados.txt`
- Integrações: `docs/integrations/Pesquisa de Integrações de API.docx`, `docs/integrations/Fluxo de Handshake Anti-Bot do HLTB.txt`, `docs/integrations/Fluxo de Renovação Silenciosa de Sessão da PSN.txt`
- UI/UX e Protótipo: `docs/ui_ux/Mapeamento_Telas_e_Fluxos_UI_UX.md` e `docs/ui_ux/DashboardScreen_Prototype.figcraft` (26 frames)
- Qualidade e Regras: `docs/qa/Plano de Testes de Software - Projeto Gecko.pdf`, `AGENTS.md`, `.agents/rules/*` e `.agents/skills/ui-prototyping/SKILL.md`

---

## Fase 0 — Fundação Arquitetural e Persistência Base (MVP v1.0)

### [x] Etapa 0.1 — Setup Inicial Clean Architecture, Room/KSP e Entidade `Game` *(Concluído — commit `fcb5a32`)*
- **Rastreabilidade:** `US1.1`, `US1.3`, `RF07`, `RNF01`, `RNF07`
- **Planejamento:**
  - Definir estrutura de pacotes (`core`, `data/{local,remote,repository,sync}`, `di`, `domain/{model,repository,usecase}`, `presentation`).
  - Configurar Room com KSP, exportação de schema (`app/schemas/`) e tipagem primitiva `Long` para durações e timestamps.
- **Implementação:**
  - Dependências base em `gradle/libs.versions.toml` e `app/build.gradle.kts` (Room, Hilt, Coroutines, Security Crypto, Compose).
  - `Game.kt` (com índices únicos `steam_app_id` e `psn_title_id`, e colunas isoladas `platform_reported_time_ms` e `local_logged_time_ms`), `GameDao.kt` e `GeckoDatabase.kt` v1 + schema `1.json`.
- **Teste:**
  - `GameDaoTest.kt` (unitário in-memory para durações > 24h e isolamento Delta Baseline) e `GameDaoInstrumentedTest.kt` (Room in-memory).

### [x] Etapa 0.2 — Infraestrutura Hilt, Dependências de Plataforma, Feature Flags e Manifesto Android 14+
- **Rastreabilidade:** `RNF01`, `RF21`, `.agents/rules/android_notifications.md`, Seção 2 do Mapeamento UI/UX
- **Planejamento:**
  - Mapear todas as dependências pendentes no `libs.versions.toml`: `navigation-compose`, `hilt-navigation-compose`, `work-runtime-ktx`, `hilt-work`, `datastore-preferences`, `okhttp`, `retrofit`/`ktor`, `kotlinx-serialization-json`, `coil-compose`, `mockk`, `turbine`, `room-testing`, `work-testing`.
  - Definir objeto central `ReleaseFeatureFlags` (`ENABLE_V1_1_FEATURES`, `ENABLE_V2_FEATURES`) para governança MoSCoW sem *scope creep*.
  - Revisar permissões obrigatórias no `AndroidManifest.xml` para Android 14+ (API 34+): `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` e propriedade `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
- **Implementação:**
  - Atualizar `gradle/libs.versions.toml` e `app/build.gradle.kts` (incluindo plugin `kotlinx-serialization`).
  - Criar `GeckoApplication` anotada com `@HiltAndroidApp` e integrando `Configuration.Provider` para `HiltWorkerFactory`.
  - Criar `core/ReleaseFeatureFlags.kt` e módulo Hilt `di/CoreModule.kt` (injetando `CoroutineDispatchers` e `ReleaseFeatureFlags`).
  - Anotar `MainActivity` com `@AndroidEntryPoint` e declarar permissões/application no `AndroidManifest.xml`.
- **Teste:**
  - Teste unitário `ReleaseFeatureFlagsTest` validando presets `MVP_V1_0`, `EXPANSION_V1_1` e `EVOLUTION_V2_0`.
  - Validação de build (`./gradlew assembleDebug testDebugUnitTest`) garantindo compilação KSP + Hilt sem erros.

### [x] Etapa 0.3 — Esquema Relacional Completo do Room (DER), Migrations e Fallback HLTB
- **Rastreabilidade:** `DER.txt`, `RF01.1`, `RF05`, `RF19`, `RF23`, `RF23.1`, `RF27`, `RNF07`, `TI-01`, `TI-03`
- **Planejamento:**
  - Projetar todas as entidades do DER mantendo estritamente `Long` para tempos/datas e `ForeignKey.CASCADE` em todos os vínculos filhos:
    - `PlaythroughEntity` (`id`, `game_id`, `start_timestamp_ms`, `end_timestamp_ms`, `current_session_start_ms`, `rating`, `notes_archived`)
    - `JournalEntryEntity` (`id`, `playthrough_id`, `timestamp_ms`, `text_content`, `accumulated_playtime_ms`)
    - `PlatformEntity` (`id`, `name`) e `GamePlatformCrossRef` (`game_id`, `platform_id`)
    - `CustomListEntity` (`id`, `name`, `is_franchise`, `external_franchise_id`) e `GameCustomListCrossRef` (`game_id`, `custom_list_id`)
    - `HltbFallbackEntity` (catálogo estático local embutido no APK com medianas históricas)
    - `AnnualGoalEntity` (`year`, `target_games`)
    - Extensões necessárias em `Game` (metadados SSOT: `cover_url`, `backdrop_url`, `synopsis`, `developer`, `publisher`, `genres_csv`, `subscription_expiry_ms`, `status_updated_at_ms`, `release_precision`, `retro_achievements_id`).
  - Definir estratégia de migração versionada (`Migration(1, 2)`) com exportação de schema `2.json` e queries explícitas no `GameDao` (incluindo `SELECT * FROM GAME WHERE status = 'BACKLOG'` para `RN-BKL-01`).
- **Implementação:**
  - Criar entidades e DAOs (`PlaythroughDao`, `JournalEntryDao`, `PlatformDao`, `CustomListDao`, `HltbFallbackDao`, `AnnualGoalDao`) em `data/local/`.
  - Atualizar `Game.kt`, `GameDao.kt` (query explícita de elegíveis + contagem por estado) e `GeckoDatabase.kt` (v2 + `Migration_1_2` + callback de pré-população do fallback HLTB via asset JSON/SQL).
  - Criar `di/DatabaseModule.kt` provendo `GeckoDatabase` e todos os DAOs.
- **Teste:**
  - **`TI-01` (Instrumentado Room In-Memory):** Deletar `GAME` e comprovar exclusão automática em cascata de `PLAYTHROUGH`, `JOURNAL_ENTRY`, `GAME_PLATFORM_REF` e `GAME_CUSTOM_LIST_REF`.
  - **`TI-03` (Validação de Schema KSP):** Verificar `2.json` gerado pelo KSP comprovando ausência de `kotlin.time.Duration` e uso exclusivo de `INTEGER`/`Long` em todas as colunas temporais, além de teste de `MigrationTestHelper` v1→v2.
  - Teste unitário/instrumentado de pré-população da tabela `HltbFallbackEntity`.

### [x] Etapa 0.4 — Cofre Criptográfico BYOK e Preferências Locais do Usuário
- **Rastreabilidade:** `US1.1`, `RF01`, `RF02`, `RNF03`
- **Planejamento:**
  - Separar dados sensíveis (chaves de API e tokens OAuth2) em `EncryptedSharedPreferences` (MasterKey AES256-GCM no Android Keystore) de preferências não sensíveis (nome do perfil, URI local do avatar, disponibilidade diária em `Long` ms, estilo padrão, `isFirstLaunchCompleted`, `enableFinanceModule`, `calibrationFactor`) em `Preferences DataStore`.
  - Projetar validadores sintáticos puros (ex.: `npsso` com exatamente 64 caracteres alfanuméricos, `SteamID64` numérico de 17 dígitos, disponibilidade diária `> 0`).
  - Garantir que nenhum segredo seja emitido em `toString()`, exceções ou logs.
- **Implementação:**
  - Criar `data/local/SecureCredentialsStorage.kt` encapsulando `EncryptedSharedPreferences` para IGDB (`clientId`, `clientSecret`), RAWG (`apiKey`), Steam (`apiKey`, `steamId64`), PSN (`npsso`, `accessToken`, `refreshToken`, `expiresAtMs`), RetroAchievements (`username`, `apiKey`).
  - Criar `data/local/UserPreferencesDataStore.kt` com `Flow<UserPreferences>`.
  - Criar `di/SecurityModule.kt` e `di/PreferencesModule.kt`.
- **Teste:**
  - Testes unitários de validação sintática de credenciais (`ByokCredentialValidatorTest`: `npsso` válido/inválido, espaços, tamanho ≠ 64, `SteamID64`, disponibilidade ≤ 0).
  - Teste instrumentado de leitura/escrita/remoção em `SecureCredentialsStorage` e `UserPreferencesDataStore`, verificando que `toString()` dos modelos de credenciais mascara valores.

---

## Fase 1 — Camada de Domínio Pura (JVM) — Núcleo MVP v1.0

### [x] Etapa 1.1 — Modelos de Domínio Puros, Enums e `BacklogPolicy` (`RN-BKL-01`)
- **Rastreabilidade:** `RF06`, `RF10`, `RF20`, `RN-BKL-01`, DAS Seção 2.1
- **Planejamento:**
  - Modelar em `domain/model/` sem nenhuma importação Android: `GameItem`, `GameStatus` (`WISHLIST`, `BACKLOG`, `PLAYING`, `COMPLETED`, `PAUSED`, `ABANDONED`), `LicenseType` (`OWNED`, `SUBSCRIPTION`, `WISHLIST`), `PlayStyle` (`RUSH`, `NORMAL`, `PLATINUM`), `Playthrough`, `JournalEntry`, `UserProfile`, `UserPaceSettings`, `ByokProviderStatus`.
  - Definir `domain/model/BacklogPolicy.kt`:
    - `val VISIBLE = setOf(GameStatus.BACKLOG, GameStatus.PAUSED, GameStatus.ABANDONED)`
    - `val ELIGIBLE = setOf(GameStatus.BACKLOG)`
  - Definir contratos de repositórios em `domain/repository/` (`GameRepository`, `PlaythroughRepository`, `UserPreferencesRepository`, `CredentialsRepository`, `HltbRepository`, `CatalogSyncRepository`).
- **Implementação:**
  - Criar modelos de domínio, `BacklogPolicy` e interfaces em `domain/model/` e `domain/repository/`.
  - Criar mappers bidirecionais `Entity <-> Domain` em `data/repository/mapper/` e implementações iniciais `GameRepositoryImpl` e `UserPreferencesRepositoryImpl`.
- **Teste:**
  - Testes unitários JVM verificando mapeamento completo `Entity <-> Domain`, preservação de `Long` > 24h e invariantes de `BacklogPolicy.VISIBLE` vs `BacklogPolicy.ELIGIBLE`.
  - Verificação estática (teste/regra de arquitetura) garantindo zero imports `android.*` / `androidx.*` em `domain/`.

### [x] Etapa 1.2 — Máquina de Estados Baseada em Eventos, Backlog Elegível e Delta Baseline
- **Rastreabilidade:** `US1.3`, `US2.1`, `RF07`, `RF10`, `RF20`, `RN-BKL-01`, `Diagrama de Máquina de Estados.txt`
- **Planejamento:**
  - Mapear todas as transições permitidas e efeitos colaterais:
    - `WISHLIST -> BACKLOG` (exige seleção de licença `OWNED` ou `SUBSCRIPTION`).
    - `BACKLOG -> PLAYING` (abre/vincula `Playthrough` com `start_timestamp_ms`).
    - `PLAYING -> COMPLETED` (encerra sessão ativa, grava `end_timestamp_ms`, registra timestamp de transição).
    - `PLAYING -> PAUSED | ABANDONED` (sai automaticamente das sugestões via `RN-BKL-01`).
    - `PAUSED | ABANDONED -> PLAYING | BACKLOG` (reentrada manual explícita).
  - Projetar `GetEligibleBacklogGamesUseCase` consumindo exclusivamente query explícita `status = 'BACKLOG'` + licença ativa (`OWNED` ou `SUBSCRIPTION`, nunca `WISHLIST`).
  - Projetar `MergeDeltaBaselineUseCase` somando `platform_reported_time_ms + local_logged_time_ms` somente em memória.
- **Implementação:**
  - Criar `TransitionGameStatusUseCase`, `GetEligibleBacklogGamesUseCase`, `GetVisibleBacklogGamesUseCase`, `MergeDeltaBaselineUseCase` e `UpdatePlayStyleUseCase` em `domain/usecase/`.
- **Teste:**
  - **`GetEligibleBacklogGamesUseCaseTest` (obrigatório por `RN-BKL-01`):** Banco/fake com jogos `BACKLOG`, `PAUSED`, `ABANDONED`, `WISHLIST`, `PLAYING` e `COMPLETED` deve retornar exclusivamente `BACKLOG` com licença elegível.
  - Testes unitários de todas as transições válidas/inválidas da máquina de estados, registro de timestamps e `MergeDeltaBaselineUseCase` (incluindo valores > 24h e sync remoto menor que o baseline existente).

### [x] Etapa 1.3 — Motor Preditivo Smart Scheduler (Peso de Atenção, Hora Extra e Trava Unreleased)
- **Rastreabilidade:** `US3.1`, `US3.2`, `RF02`, `RF06`, `RF13`, `RF15`, `RF15.1`, `RN-BKL-01`, `TU-01`, `TU-02`
- **Planejamento:**
  - Formalizar fórmulas matemáticas em milissegundos (`Long`) e aritmética segura (zero números negativos, zero divisão por zero):
    - `Cota_Efetiva = Disponibilidade_Diaria / N` (`N` = total em `Jogando Atualmente`).
    - Seleção da meta HLTB conforme `PlayStyle` (`RUSH -> hltb_main_story_ms`, `NORMAL -> hltb_main_sides_ms`, `PLATINUM -> hltb_completionist_ms`).
    - `Tempo_Total = platform_reported_time_ms + local_logged_time_ms`.
    - `Tempo_Restante = max(0L, (Tempo_HLTB * Fator_Calibracao) - Tempo_Total)`.
    - **Modo Hora Extra (RF15.1):** Quando `Tempo_Total >= Tempo_HLTB`, ativar flag `isOvertime = true`, sem dias negativos, projetando janela incremental.
    - **Trava Unreleased (RF15.1):** Lançamentos `Day One` sem dados HLTB atuam apenas como âncoras de data limite.
    - **Cenário A (Conflito Day One):** Data projetada > data do próximo `Day One` → gerar ações sugeridas (mudar para `Rushar`, pausar concorrente, elevar cota diária).
    - **Cenário B (Folga até o Day One):** Calcular saldo de horas livres e sugerir **exclusivamente** candidatos de `GetEligibleBacklogGamesUseCase` (`RN-BKL-01`).
- **Implementação:**
  - Criar `domain/model/ScheduleProjection.kt` (`GameScheduleItem`, `DayOneConflict`, `FreeWindowRecommendation`).
  - Criar `domain/usecase/CalculateSmartScheduleUseCase.kt` (injetando `GetEligibleBacklogGamesUseCase` e clock injetável para determinismo).
- **Teste:**
  - **`TU-01`:** 2h/dia, `N = 2` jogos simultâneos, 10h restantes → `Cota_Efetiva = 1h/dia/jogo` e prazo exato de 10 dias.
  - **`TU-02`:** `Tempo_Total > Tempo_HLTB` → não retornar dias negativos; ativar `isOvertime = true` com projeção estendida.
  - Testes adicionais: Cenário A (conflito Day One), Cenário B (sugestão exclui estritamente `PAUSED`, `ABANDONED` e `WISHLIST`), Trava Unreleased (Day One com `hltb = 0L`), alternância de estilo (`Rushar`/`Normal`/`Platina`).

### [x] Etapa 1.4 — Escala Avaliativa `0.00–11.00`, Desempate Determinístico e KPIs do Dashboard
- **Rastreabilidade:** `US2.3`, `US5.1`, `RF17`, `RF22`
- **Planejamento:**
  - Definir regra de validação de notas: `[0.00f..10.00f]` (2 casas decimais) ou valor honorário exato `11.00f` ("Jogo da Vida"); rejeitar valores fora da faixa (ex.: `10.50f`, `-1.0f`, `12.0f`).
  - Definir comparador determinístico de ranking: `1º maior nota` → `2º maior tempo total consolidado (Delta Baseline)` → `3º data de conclusão mais recente`.
  - Projetar agregador de KPIs do Dashboard (Total de Jogos Zerados, Horas Totais Acumuladas, Média Geral de Avaliações — omitindo cartões punitivos de jogo mais longo/rápido).
- **Implementação:**
  - Criar `ValidateAndSaveRatingUseCase`, `SortGamesWithTieBreakerUseCase` e `GetDashboardKpisUseCase` em `domain/usecase/`.
- **Teste:**
  - Testes unitários cobrindo limites de nota (`0.00`, `8.75`, `10.00`, `11.00`, inválidos `10.25`/`-0.1`/`11.5`), desempate em 3 níveis e cálculo de KPIs com biblioteca vazia e populada.

### [x] Etapa 1.5 — Motor de Ingestão e Sanitização CSV Resiliente (RFC 4180 + Regex)
- **Rastreabilidade:** `US4.1`, `RF09`, `RNF07`, `RNF08`, `TU-03`, `TU-04`, `docs/assets/jogos zerados.csv`
- **Planejamento:**
  - Projetar parser de máquina de estados RFC 4180 em Kotlin puro (proibido `String.split(",")`) com remoção de BOM UTF-8, suporte a vírgulas internas entre aspas e aspas dobradas (`""`).
  - Projetar conversor Regex de durações arbitrárias `HHH:MM:SS(.mmm)` e `HHH:MM` (incluindo `95:30:00`, `21:05:27.000`, `0:35:00`) diretamente para `Long` ms sem truncamento de 24h.
  - Projetar normalizador de notas (`"9,25"` e `"9.25"` → `9.25f`) e filtro heurístico de blocos flutuantes fora da 1FN (como estatísticas laterais em `jogos zerados.csv`: `ATUALIZAÇÃO`, `TOP 10`, `DESAFIOS`), emitindo relatório de linhas válidas/ignoradas sem jamais lançar `IndexOutOfBoundsException`.
  - Definir modelo de mapeamento visual de colunas (`CsvColumnMapping`) e caso de uso de ingestão assíncrona via Coroutines (`Dispatchers.Default` para parsing + `Dispatchers.IO` para persistência em lote).
- **Implementação:**
  - Criar `core/csv/CsvSanitizerParser.kt`, `core/csv/DurationRegexParser.kt` e `core/csv/RatingNormalizer.kt`.
  - Criar `domain/usecase/PreviewCsvImportUseCase.kt` e `domain/usecase/ImportCsvGamesUseCase.kt`.
- **Teste:**
  - **`TU-03`:** `"95:30:00"` → `343_800_000L` ms e `"21:05:27.000"` sem truncamento para 23h.
  - **`TU-04`:** Título com vírgula interna entre aspas + notas mistas (`"8,5"` e `"8.5"`) + linhas curtas/blocos flutuantes fora da 1FN → zero `IndexOutOfBoundsException`, matriz correta.
  - Teste de integração de parsing utilizando o arquivo real `docs/assets/jogos zerados.csv` como fixture de teste.

---

## Fase 2 — Serviços Nativos, Rede BYOK e Sincronização Resiliente (MVP v1.0)

### [ ] Etapa 2.1 — Cronômetro Ativo via Foreground Service Singleton (`specialUse`)
- **Rastreabilidade:** `US2.2`, `RF07`, `RF21`, `TI-02`, `.agents/rules/android_notifications.md`, Frame `SystemSurface FGS` e `MODAL_04_SessionConflict`
- **Planejamento:**
  - Projetar `ActiveSessionForegroundService` estritamente **Singleton** (1 sessão cronometrada ativa por vez, mesmo havendo `N >= 2` jogos em `Jogando Atualmente`).
  - Ao iniciar: gravar imediatamente `current_session_start_ms` no Room (garantindo recuperação exata caso o LMK encerre o processo).
  - Construir notificação persistente via `NotificationCompat.Builder` com canal dedicado (`CHANNEL_ID`), exibindo nome do jogo, tempo decorrido vivo e 2 ações via `PendingIntent.getService(..., PendingIntent.FLAG_IMMUTABLE)`: **`Pausar / Retomar`** (sem abrir Activity) e **`Concluir Sessão`** (soma delta exclusivamente em `local_logged_time_ms` e limpa `current_session_start_ms`). Toque no corpo abre `GameDetailScreen`.
  - Projetar protocolo de colisão (`ActiveTimerCoordinator`): ao tentar iniciar Jogo B com Jogo A rodando/pausado, expor estado `SessionConflict` para a UI exibir `SessionConflictDialog`; ao confirmar, consolidar Jogo A atomicamente e transferir FGS para Jogo B.
- **Implementação:**
  - Declarar `<service>` no `AndroidManifest.xml` com `android:foregroundServiceType="specialUse"` e `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>`.
  - Criar `data/sync/ActiveSessionForegroundService.kt`, `data/sync/SessionNotificationBuilder.kt` e `domain/usecase/SessionTimerController.kt` + implementação em `data/`.
- **Teste:**
  - **`TI-02` (Instrumentado):** Ciclo iniciar → pausar → retomar → concluir sessão + simulação de sync remoto simultâneo comprovando que `local_logged_time_ms` é incrementado sem sofrer sobrescrita por `platform_reported_time_ms`.
  - Testes unitários de `ActiveTimerCoordinator`: detecção de conflito Jogo A vs Jogo B, troca atômica preservando saldo do Jogo A e recuperação de sessão interrompida a partir de `current_session_start_ms`.

### [ ] Etapa 2.2 — Cliente HTTP Resiliente HowLongToBeat (HLTB) com Circuit Breaker e Fallback Local
- **Rastreabilidade:** `US1.2`, `RF01.1`, `RF05`, `RNF01`, `Fluxo de Handshake Anti-Bot do HLTB.txt`, `Pesquisa de Integrações de API.docx`
- **Planejamento:**
  - Implementar fluxo de 2 etapas:
    1. `GET /api/find/init` com `User-Agent` de navegador Desktop consistente e `Referer: https://howlongtobeat.com/`.
    2. Parsing adaptativo (AST/Regex no JSON) para extrair par rotativo `hpKey`/`hpVal` e token de sessão (TTL ~15 min).
    3. `POST /api/find` injetando `hpKey`/`hpVal` **simultaneamente** nos cabeçalhos HTTP e na raiz do payload JSON, mantendo exatamente o mesmo `User-Agent`.
  - Implementar **Circuit Breaker + Degradação Graciosa**: em HTTP 403/404/erro de rede, agendar `HltbSyncWorker` no `WorkManager` com `BackoffPolicy.EXPONENTIAL` e retornar imediatamente as medianas históricas da tabela `HltbFallbackEntity` no Room; em sucesso, atualizar cache perpétuo no Room em `Long` ms.
- **Implementação:**
  - Criar `di/NetworkModule.kt` (OkHttpClient com timeouts, sem logar segredos), `data/remote/hltb/HltbApiClient.kt`, `data/remote/hltb/HltbHoneypotExtractor.kt`, `data/repository/HltbRepositoryImpl.kt` e `data/sync/HltbSyncWorker.kt`.
- **Teste:**
  - Testes unitários com `MockWebServer`: (a) handshake 200 `/init` → `/find` valida headers idênticos + `hpKey/hpVal` no header e no body; (b) HTTP 403/404 dispara fallback imediato para `HltbFallbackDao` e agenda retry exponencial no WorkManager; (c) conversão de horas decimais HLTB para `Long` ms.

### [ ] Etapa 2.3 — Clientes HTTP BYOK de Catálogo e Sincronização MVP (IGDB, Steam, PSN Inicial e RAWG)
- **Rastreabilidade:** `US1.1`, `RF01`, `RF01.1`, `RF07`, `RNF01`, `RNF03`
- **Planejamento:**
  - Implementar clientes descentralizados lendo credenciais exclusivamente do `SecureCredentialsStorage` (e operando em Modo Sem Chaves sem exceções quando ausentes):
    - **IGDB/Twitch:** Troca `Client ID` + `Client Secret` por token OAuth2 de app, busca de jogos por nome (capas, sinopse, desenvolvedora, gêneros, data de lançamento, plataformas) respeitando limite base de concorrência.
    - **Steam:** `IPlayerService/GetOwnedGames` via `Web API Key` + `SteamID64`, convertendo minutos jogados para `Long` ms em `platform_reported_time_ms` (Delta Baseline).
    - **PSN (MVP):** Validação do cookie `npsso` (64 chars), troca inicial por `Access Token` e ping de conectividade + sync básico via Delta Baseline (automação de renovação 60d e Token Bucket entram na v1.1 — Etapa 4.1).
    - **RAWG:** Fallback opcional de artes/capas respeitando cota de 20.000 req/mês.
  - Criar caso de uso `TestByokProviderConnectionUseCase` para feedback individual por provedor na tela de Onboarding e Configurações.
- **Implementação:**
  - Criar pacotes `data/remote/{igdb,steam,psn,rawg}/`, `data/repository/CatalogSyncRepositoryImpl.kt` e `data/sync/LibraryDeltaSyncWorker.kt`.
  - Criar `domain/usecase/SearchRemoteCatalogUseCase.kt` e `domain/usecase/TestByokProviderConnectionUseCase.kt`.
- **Teste:**
  - Testes unitários com `MockWebServer` para cada cliente: sucesso, erro 401/403, ausência de chaves (degradação graciosa sem crash), e garantia de que `LibraryDeltaSyncWorker` atualiza apenas `platform_reported_time_ms` (nunca `local_logged_time_ms`).

---

## Fase 3 — Design System M3 OLED e Telas do MVP v1.0

### [ ] Etapa 3.1 — Design System Gecko M3 OLED Dark, Componentes Base e Navegação Global
- **Rastreabilidade:** `RNF04`, `.agents/rules/ui_ux_design.md`, `.agents/skills/ui-prototyping/SKILL.md`, `Mapeamento_Telas_e_Fluxos_UI_UX.md` Seção 1
- **Planejamento:**
  - Alinhar `ui/theme/Color.kt`, `Theme.kt` e `Type.kt` à paleta OLED Dark (`#0D1210` background, `#18201C`–`#242E28` superfícies Tonal Elevation, `#00E676`/`#2ECC71` primary accent, `#2A3530` outlineVariant, `#E0E0E0`/`#9E9E9E` textos).
  - Projetar biblioteca de componentes reutilizáveis em `presentation/components/`: `GeckoSurfaceCard` (Tonal Elevation, sem drop shadow), `LicenseBadge` (`Possuo` vs `SaaS`), `StatusDotBadge`, `SelectableChip` (borda inativa obrigatória + `48dp` min touch target), `ByokSecretTextField` (`PasswordVisualTransformation` + estado exclusivo foco `|`+`X` vs validado `✓`), `ByokCredentialsBottomSheet` (para integrações multi-campo).
  - Projetar grafo de navegação (`GeckoNavHost`) com Bottom Navigation Bar adaptativa (4 abas no MVP v1.0: Início, Biblioteca, Agenda, Configurações; expandindo para 5 abas na v1.1 com Análises).
- **Implementação:**
  - Atualizar `ui/theme/` e criar componentes base em `presentation/components/` com `Modifier.testTag(...)` padronizados.
  - Criar `presentation/navigation/GeckoRoutes.kt` e `GeckoNavHost.kt` integrando `ReleaseFeatureFlags`.
- **Teste:**
  - Testes Compose (`createComposeRule`) validando renderização de `LicenseBadge` (`Possuo` vs `SaaS`), exclusividade de estado visual do `ByokSecretTextField` (foco vs validado) e alvos de toque mínimos (`>= 48dp`).

### [ ] Etapa 3.2 — Fluxo de Boas-Vindas e Configuração Inicial (`WelcomeOnboardingScreen`)
- **Rastreabilidade:** `RF01`, `RF01.1`, `RF02`, `RF06`, `RF09`, `RNF03`, `TUI-02`, Frames `ONB_01_Welcome`, `ONB_02_Pace`, `ONB_03_Platforms`
- **Planejamento:**
  - Implementar Wizard de 3 passos com `HorizontalPager`, cabeçalho fixo (`barra de progresso` à esquerda + `Passo X de 3` à direita) e rodapé assimétrico obrigatório a partir do Passo 2 (`← Voltar` peso 1 + CTA ≤ 3 palavras peso 2):
    - **Passo 1 (`ONB_01_Welcome`):** Animação pulsante esmeralda (`InfiniteTransition`), `AvatarPhotoPicker` (`ActivityResultContracts.PickVisualMedia`, toque ≥ `48dp`), input `"Seu nome ou apelido"` com rótulo imediatamente acima do campo e selo de privacidade local.
    - **Passo 2 (`ONB_02_Pace`):** Seletor de ritmo diário (`−`/`+` + chips), simulador dinâmico em linguagem natural (`"Nesse ritmo, um jogo de 20h leva ~10 dias"`), 3 cartões de estilo padrão (`Rushar` / `Normal` / `Platina` respeitando orçamento de caracteres `360dp`: título 1 palavra, subtítulo ≤ 14 chars). Meta anual exibida apenas quando `ENABLE_V1_1_FEATURES = true`.
    - **Passo 3 (`ONB_03_Platforms`):** Ação `"Pular por enquanto ›"` (Modo Offline Puro), cards colapsados com `+ Configurar` abrindo Bottom Sheet multi-campo para IGDB/Steam/RetroAchievements e input inline mascarado para PSN `npsso` (nunca exibindo `Validar` junto com `✓ Conectado`), atalho para `CsvImportWizardScreen`, CTA final salvando credenciais no Keystore e definindo `isFirstLaunchCompleted = true`.
- **Implementação:**
  - Criar `presentation/onboarding/OnboardingViewModel.kt`, `OnboardingUiState.kt` e telas `WelcomeOnboardingScreen.kt`, `OnboardingProfileStep.kt`, `OnboardingPaceStep.kt`, `OnboardingConnectionsStep.kt`.
- **Teste:**
  - Teste unitário de `OnboardingViewModel` (navegação entre passos, validação de nome/ritmo, modo pular offline, persistência BYOK).
  - Teste Compose UI cobrindo fluxo completo 3 passos, consistência de cabeçalho/rodapé assimétrico, abertura do Bottom Sheet multi-campo e mascaramento `PasswordVisualTransformation` (`TUI-02`).

### [ ] Etapa 3.3 — Dashboard Principal (`DashboardScreen`) e Diálogo de Conflito FGS
- **Rastreabilidade:** `US5.1`, `RF02`, `RF15`, `RF15.1`, `RF17`, `RF21`, `TUI-01`, `TUI-03`, Frames `HOME_01_Dashboard`, `HOME_02_EmptyState`, `MODAL_04_SessionConflict`
- **Planejamento:**
  - Implementar os 3 estados reativos via `StateFlow`:
    - **Estado A (`totalGamesInDb == 0` — `HOME_02_EmptyState`):** `EmptyLibraryOnboardingCard` com 3 CTAs (Importar CSV, Buscar Jogo, Configurar APIs) e seletor rápido de Disponibilidade Diária.
    - **Estado B (`N >= 1` ativos — `HOME_01_Dashboard`):** Banner condicional de Conflito Day One (com ações rápidas), cards de `Jogando Atualmente` (capa, badges de plataforma e licença `Possuo`/`SaaS`, estilo, tempo Delta Baseline, barra de progresso/Hora Extra, data projetada e botão `Play/Pause` direto), grid de 3 KPIs essenciais (Zerados, Horas Totais, Nota Média — sem cartões punitivos) e controle rápido de Disponibilidade Diária (`< 200ms`).
    - **Estado C (`N == 0`, biblioteca > 0):** Card compacto `"Escolher jogo do Backlog"` no MVP v1.0 (o card RF16 entra sob flag na v1.1).
  - Implementar `SessionConflictDialog` (`MODAL_04_SessionConflict`) ao tocar `Play` com outro jogo já rodando no FGS.
- **Implementação:**
  - Criar `presentation/dashboard/DashboardViewModel.kt`, `DashboardUiState.kt`, `DashboardScreen.kt` e `presentation/components/SessionConflictDialog.kt`.
- **Teste:**
  - **`TUI-01` (Compose Rule):** Emitir lista mista (`Possuo`, `SaaS`, `Quero Comprar`) e validar badges distintos para `Possuo` vs `SaaS` e exclusão de `Quero Comprar` da fila preditiva.
  - **`TUI-03` (Compose Rule):** Alterar "Disponibilidade Diária" e comprovar atualização reativa imediata das datas projetadas sem recriar a hierarquia da tela.
  - Teste Compose de `EmptyState`, troca de sessão via `SessionConflictDialog` e teste unitário de `DashboardViewModel` (Turbine + Coroutines Test).

### [ ] Etapa 3.4 — Biblioteca de Jogos (`LibraryScreen` — MVP v1.0)
- **Rastreabilidade:** `RF10`, `RF20`, `RF22`, `RN-BKL-01`, Frame `LIB_01_GamesGrid`
- **Planejamento:**
  - Implementar 4 cartões fixos de status com contagem (`Todos`, `Jogando`, `Backlog`, `Zerados`), garantindo que o cartão `Backlog` exiba **Backlog Visível (`BACKLOG + PAUSED + ABANDONED`)** com ícones diferenciados (`Pausado` laranja `#F59E0B`, `Abandonado` cinza dessaturado) e gaveta `Filtros` permitindo isolar `Pausado` e `Abandonado`, plataforma e gênero.
  - Implementar alternância de visualização (**Grade 3/4 colunas**, **Lista Densa**, **Tabela Detalhada**), busca local instantânea, ordenação (Nota com desempate RF22, Tempo Jogado, Ano, A–Z), `Long Press` para Bottom Sheet de ação rápida e FAB `+` abrindo `GlobalSearchAndAddScreen`.
- **Implementação:**
  - Criar `presentation/library/LibraryViewModel.kt`, `LibraryUiState.kt`, `LibraryScreen.kt` e componentes de card/lista/tabela.
- **Teste:**
  - Teste unitário de `LibraryViewModel` provando que `Backlog` contabiliza e lista `BACKLOG + PAUSED + ABANDONED`, enquanto filtro secundário isola cada subestado.
  - Teste Compose verificando troca de layout, badges de licença/nota/status e estado vazio de filtro com botão `"Limpar filtro"`.

### [ ] Etapa 3.5 — Busca Global e Cadastro (`GlobalSearchAndAddScreen`)
- **Rastreabilidade:** `RF01.1`, `RF05`, `RF10`, `RF13`, Frame `SEARCH_01_AddGame`
- **Planejamento:**
  - Implementar busca com debounce em IGDB (quando configurado) + pesquisa no catálogo local + botão `"Cadastrar Jogo Manualmente"` sempre acessível no Modo Sem Chaves/Offline.
  - Ao selecionar título, disparar busca silenciosa das 3 métricas HLTB (com fallback local), permitir escolher **Categoria de Licença** (`Possuo`, `Assinatura Temporária`, `Quero Comprar`), **Estado Inicial** e toggle **`Day One`** (quando lançamento futuro), salvando no Room e navegando para `GameDetailScreen`.
- **Implementação:**
  - Criar `presentation/search/GlobalSearchViewModel.kt`, `GlobalSearchUiState.kt` e `GlobalSearchAndAddScreen.kt`.
- **Teste:**
  - Teste unitário de `GlobalSearchViewModel` (modo conectado IGDB, modo offline sem chaves, fallback HLTB, persistência com licença/Day One).
  - Teste Compose do fluxo de cadastro manual completo até navegação para `GameDetailScreen`.

### [ ] Etapa 3.6 — Página Detalhada do Jogo, Cronômetro Integrado e Conclusão (`GameDetailScreen`)
- **Rastreabilidade:** `US2.1`, `US2.2`, `US2.3`, `RF05`–`RF07`, `RF10`, `RF13`, `RF20`–`RF22`, Frames `DETAIL_01_GameHub`, `TIMER_01_ActiveSession`, `MODAL_02_CompleteGame`
- **Planejamento:**
  - Implementar cabeçalho imersivo com backdrop, capa, metadados SSOT, toggle `Day One`, exclusão com diálogo de confirmação (`CASCADE`).
  - Implementar barra horizontal da **Máquina de Estados** (6 estados), painel de **Smart Scheduler & Cronômetro** (exibido em `Jogando Atualmente`: chips `Rushar`/`Normal`/`Platina`, abertura transparente do Delta Baseline `platform_reported_time_ms` + `local_logged_time_ms`, projeção individual e botão dinâmico do FGS com `TIMER_01_ActiveSession` e `SessionConflictDialog`).
  - Implementar seção **"Minha Avaliação"** (`0.00–10.00` + toggle dourado `11.00 — Jogo da Vida`) e modal de conclusão (`MODAL_02_CompleteGame`) ao transicionar para `Zerado` (encerrando FGS e somando em `local_logged_time_ms`).
- **Implementação:**
  - Criar `presentation/detail/GameDetailViewModel.kt`, `GameDetailUiState.kt`, `GameDetailScreen.kt`, `ActiveSessionTimerSheet.kt` e `CompleteGameModal.kt`.
- **Teste:**
  - Teste unitário de `GameDetailViewModel`: soma Delta Baseline em memória, troca de estilo recalculando previsão, transição para `Zerado` encerrando sessão e validação de nota `0.00–11.00`.
  - Teste Compose verificando exibição condicional do painel de cronômetro, botões de estado e fluxo de conclusão.

### [ ] Etapa 3.7 — Agenda Preditiva e Wishlist Base (`SmartSchedulerScreen` — MVP v1.0)
- **Rastreabilidade:** `US3.1`, `US3.2`, `RF10`, `RF13`, `RF15`, `RF15.1`, `RN-BKL-01`, Frames `AGENDA_01_Playing`, `AGENDA_02_ReleasesAndWishlist`
- **Planejamento:**
  - Implementar tela com 3 abas superiores:
    - **Aba 1 (`Smart Queue` — `AGENDA_01_Playing`):** Disponibilidade Diária editável, linha do tempo ponderada (`Disponibilidade / N`), indicador de Hora Extra (RF15.1) e recomendações de janela livre usando **exclusivamente** `GetEligibleBacklogGamesUseCase` (`RN-BKL-01`).
    - **Aba 2 (`Day One & Releases` — `AGENDA_02_ReleasesAndWishlist`):** Linha do tempo de lançamentos com toggle `Day One` recalculando conflitos na hora.
    - **Aba 3 (`Wishlist`):** Grade de `Quero Comprar` estritamente sem notas/estrelas de prioridade (RF12), com botão `"Marcar como Adquirido"` (seletor `Possuo` vs `Assinatura Temporária`).
- **Implementação:**
  - Criar `presentation/scheduler/SmartSchedulerViewModel.kt`, `SmartSchedulerUiState.kt` e `SmartSchedulerScreen.kt`.
- **Teste:**
  - Teste unitário de `SmartSchedulerViewModel` provando que `PAUSED`, `ABANDONED` e `WISHLIST` nunca entram nas recomendações de janela livre e que o toggle `Day One` atualiza conflitos reativamente.
  - Teste Compose verificando ausência de escala subjetiva de prioridade na Wishlist e transição `"Marcar como Adquirido"`.

### [ ] Etapa 3.8 — Configurações, Cofre BYOK e Assistente de Importação CSV
- **Rastreabilidade:** `US1.1`, `US4.1`, `RF01`, `RF01.1`, `RF02`, `RF09`, `RNF03`, `RNF08`, `TUI-02`, Frames `SETTINGS_01_Main`, `CSV_01_Select`, `CSV_02_MapColumns`, `CSV_03_Reconcile`
- **Planejamento:**
  - **`SettingsAndByokScreen` (`SETTINGS_01_Main`):** Perfil local editável, indicador `Modo Offline Puro` vs `Modo Conectado BYOK`, cofre BYOK seguindo invariante de container (Bottom Sheet para IGDB/Steam, inline mascarado para PSN/RAWG, status HLTB fallback, estado exclusivo `Validar` vs `✓ Conectado`), Disponibilidade Diária, ação `"Sincronizar Agora"` e entrada para importação CSV.
  - **`CsvImportWizardScreen` (3 passos — `CSV_01_Select`, `CSV_02_MapColumns`, `CSV_03_Reconcile`):** Seleção SAF de `.csv`, mapeamento visual de colunas com preview de sanitização Regex (durações > 24h, notas mistas, linhas fora da 1FN descartadas) e ingestão não-bloqueante em Coroutines levando à `LibraryScreen`.
- **Implementação:**
  - Criar `presentation/settings/SettingsViewModel.kt`, `SettingsAndByokScreen.kt`.
  - Criar `presentation/csv/CsvImportViewModel.kt` e `CsvImportWizardScreen.kt` (passos 1–3 com cabeçalho fixo e rodapé assimétrico).
- **Teste:**
  - **`TUI-02` (Compose Rule):** Inserir token `npsso` de 64 caracteres via `onNodeWithTag("byok_token_field")` + `performTextInput()`, comprovar mascaramento na Semantics Tree (`PasswordVisualTransformation`) e ocultação total após salvamento.
  - Testes Compose e unitários do Wizard CSV (incluindo preview com `jogos zerados.csv` e confirmação de persistência no Room).

### [ ] Etapa 3.9 — Gate de Qualidade e Fechamento do MVP v1.0
- **Rastreabilidade:** Todos os 15 RF + 4 RNF Must Have da Matriz MoSCoW
- **Planejamento:**
  - Auditar conformidade integral com ANIR, RN-BKL-01, M3 OLED e `ReleaseFeatureFlags` (`v1.1` e `v2.0` devidamente ocultas).
  - Validar operação 100% em Modo Avião / Sem Chaves (Perfil A) e Modo Conectado BYOK (Perfil B).
- **Implementação:**
  - Corrigir eventuais ajustes de navegação, strings, `testTag`s ausentes ou lints encontrados na auditoria.
- **Teste:**
  - Executar suíte completa: `./gradlew testDebugUnitTest connectedDebugAndroidTest` comprovando 100% de passagem de `TU-01..04`, `TI-01..03`, `TUI-01..03` e `GetEligibleBacklogGamesUseCaseTest`.

---

## Fase 4 — Release Expansão v1.1 (*Should Have*)

### [ ] Etapa 4.1 — Renovação Silenciosa de Tokens PSN (60 dias), Token Bucket (`300/15min`) e Diagnóstico de Privacidade
- **Rastreabilidade:** `RF01.2`, `RNF09`, `TI-04`, `Fluxo de Renovação Silenciosa de Sessão da PSN.txt`
- **Planejamento:**
  - Implementar `PsnTokenBucketRateLimiter` thread-safe em Kotlin (`maxTokens = 300`, `refillRate = 300 / 900s`, `AtomicInteger`, `@Volatile lastRefillTime`) + leitura do cabeçalho `Retry-After` em HTTP 429.
  - Implementar `PsnTokenRefreshWorker` (`PeriodicWorkRequest` com `NetworkType.CONNECTED` e `BackoffPolicy.EXPONENTIAL`) trocando `npsso`/`Refresh Token` antes dos 60 dias e regravando no `SecureCredentialsStorage`; notificar usuário somente na expiração definitiva.
  - Tratar respostas opacas de privacidade PSN (HTTP 200 com arrays nulos, erro `2281077`, HTTP 500 de telemetria) convertendo em banner orientativo em `SettingsAndByokScreen` sem falha crítica.
  - Fragmentar sincronizações extensas em lotes com persistência intermediária Delta Baseline (resiliência ao Doze Mode).
- **Implementação:**
  - Criar `data/sync/PsnTokenBucketRateLimiter.kt`, `data/sync/PsnTokenRefreshWorker.kt`, `data/sync/PsnLibrarySyncWorker.kt` e `PsnPrivacyDiagnosticBanner` na `SettingsAndByokScreen`.
- **Teste:**
  - **`TI-04` (WorkManager Testing):** Simular restrição de rede e estouro de limite PSN (HTTP 429 + `Retry-After`) comprovando retorno `Result.retry()` com `BackoffPolicy.EXPONENTIAL`.
  - Testes unitários de concorrência do `PsnTokenBucketRateLimiter`, renovação OAuth2 e parsing seguro de payloads nulos / código `2281077`.

### [ ] Etapa 4.2 — Resolução de Entidades em Lote no IGDB (`/external_games` + `/multiquery`) e Trava Oficial de Plataformas
- **Rastreabilidade:** `RF04`, `RNF05`, `Pesquisa de Integrações de API.docx` Seção 5
- **Planejamento:**
  - Construir gerador de queries APICalypse (`ApicalypseQueryBuilder`) usando `/v4/external_games` (`WHERE external_game.category = 1 & external_game.uid = (...)` para Steam e categoria equivalente para PSN) e `/v4/multiquery` (até 10 subqueries por chamada, respeitando payload < 10 MB e teto estrito de `<= 4 req/s` / `<= 8` concorrentes).
  - Orquestrar fila pós-importação CSV e Steam via `IgdbEntityResolutionWorker` (WorkManager).
  - Atualizar `GlobalSearchAndAddScreen` e `GameDetailScreen` para restringir seleção de plataformas às oficialmente vinculadas ao jogo no IGDB (mantendo seleção livre apenas no cadastro manual offline).
- **Implementação:**
  - Criar `data/remote/igdb/ApicalypseQueryBuilder.kt`, `data/sync/IgdbRateLimiter.kt`, `data/sync/IgdbEntityResolutionWorker.kt`.
  - Integrar trava de plataformas (`RF04`) no fluxo de cadastro e edição.
- **Teste:**
  - Testes unitários de `ApicalypseQueryBuilder` (lotes, limites de 10 queries, proteção < 10 MB), `IgdbRateLimiter` (`<= 4 req/s`) e `MockWebServer` com HTTP 429.
  - Teste Compose validando lista fechada de plataformas para jogo IGDB vs lista livre em cadastro manual.

### [ ] Etapa 4.3 — Múltiplos Playthroughs (`1:N`) e Diário Narrativo (`Journals` + FAB Contextual)
- **Rastreabilidade:** `RF23`, `RF23.1`, `TI-01`, `.agents/rules/ui_ux_design.md`, Frames `DETAIL_02_EditAndJournal`, `MODAL_01_NewNote`
- **Planejamento:**
  - Implementar criação de novo `Playthrough` (`StartNewPlaythroughUseCase` — Replay / New Game+) em jogo `Zerado`, preservando intactas jornadas, datas e notas de anos anteriores.
  - Implementar `AddJournalEntryUseCase` gravando `timestamp_ms`, texto formatado e snapshot `accumulated_playtime_ms` vinculados ao `playthrough_id` ativo; bloquear edição (somente-leitura) ao concluir o `Playthrough`.
  - Na `GameDetailScreen` (`DETAIL_02_EditAndJournal`), exibir linha do tempo de jornadas/notas e **FAB contextual `"Registrar no Diário"` visível exclusivamente quando `status == PLAYING`**, abrindo `JournalEntryBottomSheet` (`MODAL_01_NewNote`).
- **Implementação:**
  - Criar `domain/usecase/StartNewPlaythroughUseCase.kt`, `AddJournalEntryUseCase.kt`, `ObserveGameHistoryUseCase.kt`.
  - Criar `presentation/detail/JournalEntryBottomSheet.kt` e atualizar `GameDetailScreen.kt`.
- **Teste:**
  - Testes unitários garantindo criação de 2ª jornada sem sobrescrever a 1ª, contabilização anual por `end_timestamp_ms` e bloqueio somente-leitura após conclusão.
  - Teste Compose garantindo que o FAB de diário existe **somente** em `Jogando Atualmente` e some nos outros 5 estados; revalidar `TI-01` (`CASCADE`).

### [ ] Etapa 4.4 — Metas Anuais, Sugestão para Usuário Ocioso (`N = 0`), Isolamento e Filtros Dinâmicos da Wishlist
- **Rastreabilidade:** `RF03`, `RF11`, `RF12`, `RF12.1`, `RF16`, `RNF02`, `RN-BKL-01`
- **Planejamento:**
  - Implementar `SetAnnualGoalUseCase` e `ObserveAnnualGoalProgressUseCase` (contabilizando `Playthrough`s concluídos no ano civil) + card de Meta Anual no `DashboardScreen`.
  - Implementar `GetIdleWindowRecommendationsUseCase` (`RF16`): quando `N == 0`, calcular horas livres até o próximo `Day One` e sugerir **exclusivamente** jogos de `GetEligibleBacklogGamesUseCase` (`status = 'BACKLOG'`, excluindo `Quero Comprar` RF11 e excluindo `Pausado`/`Abandonado` RN-BKL-01).
  - Implementar `WishlistFilterSheet` (`ModalBottomSheet`) na aba Wishlist da `SmartSchedulerScreen` filtrando simultaneamente por **Plataforma**, **Faixa HLTB** (`< 10h`, `10–30h`, `> 30h`) e **Vínculo com Franquias Ativas**, sem persistir prioridades subjetivas após fechar (RF12) e executando em `< 200ms` (RNF02).
- **Implementação:**
  - Criar use cases em `domain/usecase/` e componentes `AnnualGoalCard`, `IdleDayOneWindowCard` (Dashboard) e `WishlistFilterSheet` (SmartScheduler).
- **Teste:**
  - Teste obrigatório `RN-BKL-01` no motor `RF16`: provar que um jogo `Pausado` ou `Abandonado` com duração compatível **nunca** é sugerido na janela livre.
  - Teste unitário de filtros combinados da Wishlist (sem persistência após fechamento) e benchmark JVM de `CalculateSmartScheduleUseCase` + filtros Wishlist com 2.000 títulos (`< 200ms` — `RNF02`).

### [ ] Etapa 4.5 — Datas Imprecisas (`Q3`/`Ano`/`TBA`) e Vitrine de Lançamentos (`VitrinePreviewCard` + `VitrineScreen`)
- **Rastreabilidade:** `RF14`, `RF14.1`, `RF14.2`, Frames `HOME_01_Dashboard` (card Vitrine) e `HOME_03_Vitrine`
- **Planejamento:**
  - Implementar `ImpreciseDateResolver` (domínio puro): `'Q1..Q4 YYYY'` → último dia do trimestre (`30/09/2026` para Q3), `'YYYY'` → `31/12/YYYY`, `'TBA'` → `null` seguro sem exceção no calendário ou no Smart Scheduler.
  - Implementar `VitrinePreviewCard` no `DashboardScreen` (4 capas em miniatura com contagem regressiva e borda esmeralda para `Day One`; toque na capa → `GameDetailScreen`; toque no cabeçalho → `VitrineScreen`).
  - Implementar `VitrineScreen` (`HOME_03_Vitrine`): busca interna, atualização manual IGDB, filtros rápidos (`Todos`, `Meus Day One`, `PS5`, `PC / Steam`, `Switch`), lista de `ReleaseDetailCard` com selo `✓`, contagem regressiva, data formatada/inferida, sinopse e chips de gêneros.
- **Implementação:**
  - Criar `domain/usecase/ResolveImpreciseReleaseDateUseCase.kt`, `ObserveUpcomingReleasesUseCase.kt`.
  - Criar `presentation/vitrine/VitrineViewModel.kt`, `VitrineScreen.kt` e `VitrinePreviewCard.kt`.
- **Teste:**
  - Testes unitários de `ResolveImpreciseReleaseDateUseCase` (`Q1`–`Q4`, ano fechado, `TBA`, data exata) e ordenação cronológica com `TBA` no fim.
  - Teste Compose de navegação `Dashboard (VitrinePreviewCard)` → `VitrineScreen` e toggle rápido de `Day One`.

### [ ] Etapa 4.6 — Coleções, Desafios de Franquias, Listas Customizadas (`N:N`) e RetroAchievements
- **Rastreabilidade:** `RF08`, `RF19`, `RF27`, Frames `LIB_02_Collections`, `LIB_03_CollectionDetail`, `MODAL_03_AuthRetro`
- **Planejamento:**
  - Habilitar aba **"Coleções & Pastas"** (`LIB_02_Collections`) na `LibraryScreen` (Anos Civis, Franquias, Listas Customizadas, Steam, RetroAchievements) e tela `CollectionDetailScreen` (`LIB_03_CollectionDetail`) com barra de progresso `0%–100%` (`zerados / total` — RF19).
  - Implementar CRUD completo de Listas Customizadas `N:N` e Franquias (`CreateCustomListUseCase`, `ToggleGameInListUseCase`, `CalculateFranchiseProgressUseCase`).
  - Implementar cliente e worker `RetroAchievementsSyncWorker` com cache local e agrupamento temporal (status `Mastered`/`Beaten` + pontos) + modal de autenticação `MODAL_03_AuthRetro`.
- **Implementação:**
  - Criar `data/remote/retroachievements/`, `data/sync/RetroAchievementsSyncWorker.kt`, `domain/usecase/` de listas/franquias e telas `CollectionsTab.kt`, `CollectionDetailScreen.kt`, `RetroAchievementsAuthModal.kt`.
- **Teste:**
  - Testes unitários de cálculo de progresso de franquia (`0%`, parcial, `100%`, divisão por zero com lista vazia), vínculo `N:N` em Room in-memory e cliente RetroAchievements (`MockWebServer` + cache).
  - Teste Compose de criação de lista customizada e navegação para `CollectionDetailScreen`.

### [ ] Etapa 4.7 — Hub de Estatísticas e Year in Review (`AnalyticsHubScreen`)
- **Rastreabilidade:** `RF03`, `RF18` *(sem cartão 9:16/Stories)*, `RF19`, `RF22`, Frames `STATS_01_Overview`, `STATS_02_CostAndSagas`
- **Planejamento:**
  - Habilitar a 5ª aba (`Análises`) na Bottom Navigation Bar e rota `analytics`.
  - Implementar `GetYearInReviewUseCase` (domínio puro): filtro por ano civil (`2026`, `2025`, ..., `Todo o Período`), total de playthroughs concluídos, horas dedicadas, gênero predominante, plataforma mais utilizada, fitas mensais, destaques (`Favorito da Vida 11.0` / `Menor Nota`), rankings Top 10 Melhores e Top 5 Piores aplicando desempate determinístico RF22.
  - Implementar sub-abas `Visão Geral` (`STATS_01_Overview`) e `Custo & Sagas` (`STATS_02_CostAndSagas`, exibindo Progresso de Sagas/Franquias na v1.1 e reservando seção financeira para flag v2.0). **Sem** botão de compartilhar e **sem** cartão 9:16 para Stories.
- **Implementação:**
  - Criar `domain/usecase/GetYearInReviewUseCase.kt`, `presentation/analytics/AnalyticsViewModel.kt`, `AnalyticsUiState.kt` e `AnalyticsHubScreen.kt`.
- **Teste:**
  - Testes unitários de `GetYearInReviewUseCase`: agregação anual por `Playthrough.end_timestamp_ms`, desempate RF22, período vazio e gênero/plataforma predominante.
  - Teste Compose validando troca de período, sub-abas `Visão Geral` ↔ `Custo & Sagas`, navegação para `GameDetailScreen` e `CollectionDetailScreen`, e ausência de ações de compartilhamento Stories.

### [ ] Etapa 4.8 — Exportação Completa de Dados via SAF (CSV e JSON)
- **Rastreabilidade:** `RF28`, `SettingsAndByokScreen`
- **Planejamento:**
  - Projetar serializadores puros de exportação (`ExportLibraryToCsvUseCase` compatível com RFC 4180 e `ExportLibraryToJsonUseCase` com versão de schema) incluindo jogos, `Playthrough`s, `JournalEntry`s, plataformas e listas customizadas.
  - Integrar com `ActivityResultContracts.CreateDocument` (Android Storage Access Framework — SAF) em `SettingsAndByokScreen`, gravando em `Dispatchers.IO` sem exigir permissões legadas de armazenamento.
- **Implementação:**
  - Criar `core/export/LibraryCsvExporter.kt`, `core/export/LibraryJsonExporter.kt`, `domain/usecase/ExportBackupUseCase.kt` e conectar ações na `SettingsAndByokScreen`.
- **Teste:**
  - Testes unitários de ida e volta (*round-trip*): exportar biblioteca com vírgulas/aspas/durações > 24h para CSV e JSON, validar sintaxe JSON e reimportar o CSV gerado pelo parser da Etapa 1.5 sem perda de dados.

### [ ] Etapa 4.9 — Gate de Qualidade e Fechamento da Release v1.1
- **Rastreabilidade:** Todos os 15 RF + 4 RNF Should Have da Matriz MoSCoW (`RNF02`, `RNF04`, `RNF05`, `RNF09`)
- **Planejamento:**
  - Ativar `ENABLE_V1_1_FEATURES = true` (mantendo `ENABLE_V2_FEATURES = false`) e revisar regressão de todas as telas e fluxos v1.0 + v1.1.
- **Implementação:**
  - Ajustes finais de performance, acessibilidade e flags.
- **Teste:**
  - Suíte completa unitária + instrumentada + UI + benchmark `RNF02` (`< 200ms` com 2.000 títulos) e validação `TI-04` (WorkManager PSN).

---

## Fase 5 — Release Evolução v2.0 (*Could Have*)

### [ ] Etapa 5.1 — Calibração Comportamental Automática do Ritmo de Jogo
- **Rastreabilidade:** `RF24`, `RF15`, `SettingsAndByokScreen`, `SmartSchedulerScreen`
- **Planejamento:**
  - Projetar `CalculatePersonalCalibrationUseCase`: analisar `Playthrough`s concluídos com métrica HLTB válida; exigir amostra mínima `>= 5` jogos para calcular razão mediana/média suavizada (com limites de segurança ex.: `[0.50x..2.00x]`).
  - Agendar recálculo assíncrono em background após cada conclusão (`CalibrationWorker`), aplicar silenciosamente o multiplicador em `CalculateSmartScheduleUseCase`, exibir indicador na `SmartSchedulerScreen` e oferecer botão `"Redefinir para 1.0x"` na `SettingsAndByokScreen`.
- **Implementação:**
  - Criar `domain/usecase/CalculatePersonalCalibrationUseCase.kt`, `data/sync/CalibrationWorker.kt` e atualizar `CalculateSmartScheduleUseCase`, `SmartSchedulerScreen` e `SettingsAndByokScreen`.
- **Teste:**
  - Testes unitários: amostra `< 5` mantém `1.0x`; jogador 20% mais rápido produz `~0.80x` e reduz dias projetados proporcionalmente; jogador 25% mais lento produz `~1.25x`; reset manual restaura `1.0x`.

### [ ] Etapa 5.2 — Monitoramento Proativo de Expiração de Catálogos SaaS
- **Rastreabilidade:** `RF25`, `RF10`, `DashboardScreen`, `SmartSchedulerScreen`, `GameDetailScreen`
- **Planejamento:**
  - Implementar cálculo de urgência SaaS: `horas_diarias_necessarias = tempo_restante / dias_ate_expiracao` (com tratamento seguro para `dias <= 0`).
  - Permitir registrar/atualizar data prevista de saída de catálogo em jogos com licença `Assinatura Temporária`.
  - Criar `SaasExpirationCheckWorker` periódico no `WorkManager` emitindo notificação Push (respeitando `POST_NOTIFICATIONS`) e alimentando banner proativo no `DashboardScreen` e seção dedicada na `SmartSchedulerScreen`.
- **Implementação:**
  - Criar `domain/usecase/CalculateSaasUrgencyUseCase.kt`, `data/sync/SaasExpirationCheckWorker.kt` e integrar alertas In-App no Dashboard, Agenda e Detalhes do Jogo.
- **Teste:**
  - Testes unitários da fórmula de urgência (incluindo janela crítica, expirado hoje, jogo já zerado ignorado) e teste WorkManager verificando disparo condicional de alerta apenas para jogos `SUBSCRIPTION` ativos/backlog elegível.

### [ ] Etapa 5.3 — Módulo Financeiro Opt-in (Custo por Hora `R$/h`)
- **Rastreabilidade:** `RF26`, `GameDetailScreen`, `AnalyticsHubScreen` (`STATS_02_CostAndSagas`), `SettingsAndByokScreen`
- **Planejamento:**
  - Garantir `enableFinanceModule = false` por padrão (opt-in explícito em `SettingsAndByokScreen`).
  - Implementar `CalculateCostPerHourUseCase`: `valor_pago / horas_totais_consolidadas` por título, por trimestre e consolidado anual, tratando divisão por zero (`tempo == 0L`) e jogos gratuitos (`valor == 0f`).
  - Exibir campo `Valor Pago (R$)` no cadastro/edição, card financeiro na `GameDetailScreen` e painel **"VALEU A PENA? (CUSTO POR HORA JOGADA)"** em `STATS_02_CostAndSagas` somente quando opt-in estiver ativo.
- **Implementação:**
  - Criar `domain/usecase/CalculateCostPerHourUseCase.kt` e integrar UI condicional em `SettingsAndByokScreen`, `GlobalSearchAndAddScreen`, `GameDetailScreen` e `AnalyticsHubScreen`.
- **Teste:**
  - Testes unitários de cálculo por jogo, trimestre, anual, divisão por zero e jogos gratuitos.
  - Teste Compose comprovando que o módulo financeiro permanece 100% oculto por padrão e aparece imediatamente ao ativar o toggle opt-in.

### [ ] Etapa 5.4 — Widgets de Tela Inicial via Jetpack Glance
- **Rastreabilidade:** `RNF06`, `RN-BKL-01`, `Mapeamento_Telas_e_Fluxos_UI_UX.md` Seção 3.7.E
- **Planejamento:**
  - Adicionar dependências `androidx.glance:glance-appwidget` e `glance-material3`.
  - Projetar 2 widgets sincronizados via `PreferencesGlanceStateDefinition` (`DataStore`) e atualizados por `GlanceWidgetSyncWorker` (`updateAppWidgetState`):
    1. **`ActiveTimerGlanceWidget`:** Capa/título do jogo ativo + botão `Play/Pause` conectado ao `ActiveSessionForegroundService` + deep link para `GameDetailScreen`.
    2. **`DayOneCountdownGlanceWidget`:** Contagem regressiva em dias para o próximo `Day One` + status da agenda (`"Em dia"` / `"Em risco"`, consumindo apenas Backlog Elegível `RN-BKL-01`) + deep link para `SmartSchedulerScreen`.
- **Implementação:**
  - Criar `presentation/widgets/ActiveTimerGlanceWidget.kt`, `ActiveTimerWidgetReceiver.kt`, `DayOneCountdownGlanceWidget.kt`, `DayOneWidgetReceiver.kt`, `data/sync/GlanceWidgetSyncWorker.kt`, layouts/metadados XML `res/xml/*_widget_info.xml` e registro no `AndroidManifest.xml`.
- **Teste:**
  - Testes unitários de serialização do estado Glance (`DataStore` preferences) e atualização via `GlanceWidgetSyncWorker`, além de teste de ação `Play/Pause` e deep links.

### [ ] Etapa 5.5 — Fechamento Final v2.0, Auditoria Completa e Release Candidate
- **Rastreabilidade:** Escopo integral SRS v2.1 (31 RF + 9 RNF) e Plano de Testes de Software
- **Planejamento:**
  - Ativar `ENABLE_V2_FEATURES = true`, revisar configurações de release (`ProGuard`/`R8` keep rules em `app/src/main/keepRules/rules.keep` para Room, Hilt, Serialization e Retrofit/OkHttp) e justificativa Play Console para `FOREGROUND_SERVICE_SPECIAL_USE`.
  - Conferir aderência da Pirâmide de Testes (~60% Unit, ~30% Integration/Instrumented, ~10% UI Compose) e ausência de violações ANIR/M3.
- **Implementação:**
  - Ajustar `rules.keep`, `buildTypes.release` e checklist final de empacotamento AAB/APK.
- **Teste:**
  - Executar bateria integral (`./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleRelease`), validando compilação release sem regressões, migrações de banco v1→v2, modo offline puro e todos os cenários `TU`, `TI` e `TUI`.
