---
trigger: glob
globs: "**/*.kt"
description: "Aplica restrições para Room/KSP, tipagem primitiva Long e o padrão temporal Delta Baseline."
---

# Diretrizes de Persistência Room e KSP

## 1. Eliminação de Value Classes no Room (`@Entity`)

* O compilador KSP não reconhece adequadamente `TypeConverters` para `kotlin.time.Duration` devido a *type erasure*.
* **Regra:** Todas as colunas de tempo (timestamps e durações) devem ser declaradas estritamente como `Long` (milissegundos) mapeando para `INTEGER` no SQLite. Suporta durações $> 24\text{h}$ sem overflow.
* Configure no `build.gradle.kts` o schema export: `arg("room.schemaLocation", "$projectDir/schemas")`.

## 2. Padrão Delta Baseline

* A tabela `GAME` exige obrigatoriamente duas colunas de tempo:
  * `platform_reported_time_ms: Long` (atualizado pelas APIs externas via `WorkManager`).
  * `local_logged_time_ms: Long` (incrementado pelo cronômetro embutido via `Foreground Service`).
* Nunca emita consultas SQL que sobrescrevam o tempo local com o tempo da plataforma. A unificação das horas jogadas ocorre apenas em memória nas `ViewModels`.