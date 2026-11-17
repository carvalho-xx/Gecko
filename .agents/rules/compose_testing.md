---
trigger: glob
globs: "**/*Test.kt, **/*Screen.kt"
description: "Padrões de testes unitários e instrumentados para Jetpack Compose via Semantics Tree."
---

# Validação e Testes em Jetpack Compose

1. **Introspecção via Semantics Tree:**
   - Não utilize hierarquias visuais físicas para asserções de teste.
   - Injete identificadores declarativos utilizando `Modifier.testTag("tag_identificadora")` nos nós acionáveis.
   - Instancie testes com `createComposeRule()` com dependência de depuração `debugImplementation("androidx.compose.ui:ui-test-manifest")`.

2. **Mascaramento BYOK (PasswordVisualTransformation):**
   - Os campos de inserção de credenciais (como o cookie `npsso` de 64 caracteres) utilizam `visualTransformation = PasswordVisualTransformation()`.
   - Essa transformação mascara o texto na Árvore Semântica em asteriscos (`*`).
   - É proibido realizar buscas de teste via `onNodeWithText("chave")` nesses campos. Valide através de `onNodeWithTag("byok_token_field")` e utilize `performTextInput()`.