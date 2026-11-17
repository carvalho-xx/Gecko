---
trigger: globs
globs: "**/preentation/**/*.kt, **/ui/**/*.kt"
description: "Diretrizes de Material Design 3, Tonal Elevation para OLED e elementos dinâmicos."
---

# UI/UX e Material Design 3 (OLED Dark Theme)

1. **Tonal Elevation vs. Sombreamento Clássico:**
   - É proibido o uso de *hard drop shadows* baseadas em canal alfa escuro.
   - Aplique o conceito de **Tonal Elevation** do Material Design 3: componentes elevados (`Surface`, `Card`, `BottomSheet`) recebem uma leve lavagem cromática de superfície (*surface tint*), preservando pretos absolutos nos diodos OLED.

2. **Diferenciação Visual de Licença:**
   - Utilize badges ou chips visuais nítidos para distinguir jogos de posse perpétua ("Possuo") de títulos sob serviço de catálogo temporário ("Assinatura Temporária - SaaS").

3. **FAB Contextual para Diário (Journals):**
   - O Floating Action Button para registro de anotações no Diário Narrativo deve ser estritamente condicional: só aparece na tela quando o jogo estiver no estado "Jogando Atualmente".