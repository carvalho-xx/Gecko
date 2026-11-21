---
name: ui-prototyping
description: Diretrizes estritas de Material Design 3, Dark Mode e prevenção de erros de layout para geração de protótipos e ecrãs Jetpack Compose. Carregue sempre que desenhar ou modificar interfaces.
---

# UI/UX & Prototyping Rules (Material Design 3 Dark)

## 1. Paleta de Cores e Superfícies (Strict Dark Mode)
- **Background Principal:** `#0D1210` a `#121212` (verde/cinza abissal escuro, nunca preto puro `#000000` nem néon saturado).
- **Superfícies / Cards:** `#18201C` a `#242E28` para elevação subtil.
- **Borda Inativa (Outline Variant):** `1dp` em `#2A3530` ou `#424242`. Todos os chips, botões secundários e cards interativos não selecionados DEVEM ter borda visível. É proibido deixar opções inativas como texto solto.
- **Cor de Destaque (Primary Accent):** Verde esmeralda suave (`#00E676` / `#2ECC71` com superfície ativa translúcida e texto de alto contraste).
- **Texto:** Primário em `#E0E0E0` (off-white); Secundário em `#9E9E9E`.

## 2. Grelha de 8pt e Geometria
- **Espaçamento:** Use estritamente múltiplos de 8 (`8dp`, `16dp`, `24dp`, `32dp`, `48dp`). Padding interno de cards: `16dp`; margem lateral do ecrã: `24dp`.
- **Border Radius:** `12dp` para cards e inputs; `8dp` ou formato pill consistente para chips de seleção.
- **Alvos de Toque (Touch Targets):** Qualquer área clicável (incluindo ícones de editar avatar, botões de limpar e chips) deve ter no mínimo `48x48dp`.

## 3. Orçamento de Caracteres e Prevenção de Overflow (Viewport 360dp)
- **Limite em 3 Colunas:** Em linhas com 3 cards ou chips lado a lado, o título deve ter no máximo 1 palavra (até 10 caracteres) e o subtítulo no máximo 14 caracteres (ex.: usar `100% / Tudo` em vez de `100% / Completista`).
- **Densidade Vertical:** No máximo 3 grupos de componentes por ecrã sem scroll explícito, garantindo no mínimo `24dp` de respiro acima da barra de navegação inferior.

## 4. Máquina de Estados Visuais (Exclusividade UDF)
- **Inputs de Texto:** Rótulos ficam imediatamente acima do input. NUNCA exiba cursor ativo (`|`) em simultâneo com ícone de validação (`✓`).
  - *Em foco:* Borda primária + cursor (`|`) + ícone limpar (`X`).
  - *Preenchido/Sem foco:* Borda subtil + sem cursor + ícone de sucesso (`✓`).
- **Cards de Integração (APIs):** NUNCA exiba o botão de ação (`Validar`) se o card já mostra estado ativo (`✓ Conectado` ou `Renovação ativa`).
- **Credenciais BYOK Múltiplas:** Integrações que exigem 2+ chaves (ex.: IGDB Client ID + Secret, Steam API Key + SteamID) nunca devem concatenar chaves num único input inline. Use estado colapsado com botão `+ Configurar` (que abre um Bottom Sheet com campos separados) e, após configurado, mostre apenas o badge `✓ Conectado`. Apenas credenciais de chave única (ex.: `npsso` da PSN) podem ter input inline.

## 5. Invariantes de Navegação (Wizards / Onboarding)
- **Cabeçalho Fixo:** Manter barra de progresso à esquerda e contador `Passo X de N` à direita em todos os passos.
- **Rodapé Assimétrico (Passo 2 em diante):** Botão secundário `← Voltar` à esquerda (peso 1) + Botão primário à direita (peso 2). Nunca oculte o botão `← Voltar` no último passo.
- **Microcopy de CTA:** Máximo de 3 palavras com verbo direto (`Continuar →`, `Concluir configuração →`).

## 6. Execução Técnica (MCP / FigCraft)
- Caso utilize servidor MCP de design (FigCraft), execute os layouts através de chamadas diretas de ferramentas JSON para a porta de controlo ativa, sem gerar scripts Python intermediários.