# System Patterns — Projeto Gecko (Android)

Referência concisa de padrões arquiteturais herdada por `.agents/rules.json`.

## 1. Arquitetura e Camadas
- **Clean Architecture + MVVM + Repository Pattern**, paradigma **Offline-First** (Room/SQLite como SSOT).
- `domain/` em JVM pura: proibido importar `android.*`, `androidx.*`, `Context` ou Room.
- Estado reativo unidirecional (UDF) via `StateFlow` nas `ViewModels` e `Flow` nos DAOs.

## 2. Persistência Room / KSP e Delta Baseline
- Todas as durações e timestamps em `@Entity` usam estritamente primitivo `Long` (milissegundos); proibido `kotlin.time.Duration` em colunas persistidas (`RNF07`).
- `platform_reported_time_ms` e `local_logged_time_ms` permanecem isolados em `GAME` (`RF07`); consolidação somente em memória na apresentação.
- Relações `GAME -> PLAYTHROUGH -> JOURNAL_ENTRY` e tabelas `N:N` usam `ForeignKey.CASCADE`.

## 3. Regra Transversal de Backlog (`RN-BKL-01`)
- `BacklogPolicy.VISIBLE = {BACKLOG, PAUSED, ABANDONED}`: apenas exibição/contagem na `LibraryScreen`.
- `BacklogPolicy.ELIGIBLE = {BACKLOG}`: única fonte de sugestões/planejamento (`RF15`, `RF16`, `CalculateSmartScheduleUseCase`, widgets), sempre via `GetEligibleBacklogGamesUseCase` com `status = 'BACKLOG'` explícito.

## 4. Segurança BYOK, Background e UI/UX
- Chaves/tokens (`IGDB`, `RAWG`, `Steam`, `PSN npsso/OAuth2`, `RetroAchievements`) residem exclusivamente em `EncryptedSharedPreferences` / Android Keystore (`RNF03`), mascarados via `PasswordVisualTransformation()`.
- Cronômetro ativo via `ActiveSessionForegroundService` Singleton (`foregroundServiceType="specialUse"`, Android 14+) com `PendingIntent.getService(..., FLAG_IMMUTABLE)`.
- Sincronizações externas via `WorkManager` com `BackoffPolicy.EXPONENTIAL`, Token Bucket PSN (`300 req/15min`), batching IGDB (`/external_games`, `<= 4 req/s`) e fallback local HLTB.
- Material Design 3 OLED Dark com Tonal Elevation, alvos de toque mínimos `48dp` e `Modifier.testTag(...)` para testes Compose.
