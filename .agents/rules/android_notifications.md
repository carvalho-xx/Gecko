---
trigger: model_decision
description: "Obrigações arquiteturais para Foreground Services (FGS), WorkManager e Android 14+."
---

# Foreground Services e Background Work (Android 14+ / API 34+)

1. **Cronômetro Ativo via FGS (`specialUse`):**
   - Para evitar aniquilação pelo *Low Memory Killer*, o cronômetro executa em Foreground Service.
   - No `AndroidManifest.xml`, declare obrigatoriamente:
     - `android:foregroundServiceType="specialUse"`
     - Permissão `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
     - Metadado de justificativa do Play Console