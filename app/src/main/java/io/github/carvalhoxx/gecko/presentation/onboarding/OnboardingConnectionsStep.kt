package io.github.carvalhoxx.gecko.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.presentation.components.ByokMultiInputProviderCard
import io.github.carvalhoxx.gecko.presentation.components.ByokSecretTextField
import io.github.carvalhoxx.gecko.ui.theme.GeckoOutlineVariant
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimaryContainer

private val PlatformCardBg = Color(0xFF111815)
private val PlatformCardBorder = Color(0xFF2A3530)
private val CsvCardBg = Color(0xFF101D17)
private val CsvCardBorder = Color(0xFF1E3A2F)
private val PlatformSubColor = Color(0xFF8E9E96)
private val OfflineHintColor = Color(0xFF6E8077)
private val EmeraldSoft = Color(0xFF6EE7B7)

/**
 * Passo 3 de 3 (`ONB_03_Platforms`, id 411 no protótipo FigCraft):
 * - Ação `"Pular por enquanto ›"` para Modo Offline Puro (Perfil A — RF01.1).
 * - Cards multi-campo colapsados (`+ Configurar` -> Bottom Sheet) para IGDB, Steam e RetroAchievements/RAWG.
 * - Card PSN (`Provider_PSN_Expanded`) com campo único mascarado (`npsso`) e exclusividade UDF
 *   entre botão `"Validar"` e estado `"✓ Conectado"`.
 * - Atalho rápido para importação de planilha CSV (`CsvQuickImportRow`).
 */
@Composable
fun OnboardingConnectionsStep(
    uiState: OnboardingUiState,
    onPsnNpssoChange: (String) -> Unit,
    onValidatePsnClick: () -> Unit,
    onOpenProviderSheet: (ByokProvider) -> Unit,
    onNavigateToCsvImport: () -> Unit,
    onSkipNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val psnState = uiState.connectionStateFor(ByokProvider.PSN)
    val isPsnConnected = psnState == ByokConnectionState.CONNECTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .testTag("onboarding_step_3_connections"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Conectar suas plataformas",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Suas chaves ficam protegidas apenas no seu celular.",
                    color = PlatformSubColor,
                    fontSize = 12.sp
                )
            }

            TextButton(
                onClick = onSkipNowClick,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("onboarding_skip_now_button")
            ) {
                Text(
                    text = "Pular por enquanto ›",
                    color = EmeraldSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 1. IGDB (Multi-campo via Bottom Sheet)
        ByokMultiInputProviderCard(
            provider = ByokProvider.IGDB,
            connectionState = uiState.connectionStateFor(ByokProvider.IGDB),
            onOpenSheetClick = { onOpenProviderSheet(ByokProvider.IGDB) }
        )

        // 2. PlayStation Network (Campo único inline mascarado: cookie npsso 64 chars)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("byok_provider_card_PSN"),
            shape = RoundedCornerShape(20.dp),
            color = PlatformCardBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, PlatformCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PlayStation Network (PSN)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Importa sua biblioteca e horas jogadas automaticamente.",
                            color = PlatformSubColor,
                            fontSize = 11.sp
                        )
                    }

                    Surface(
                        modifier = Modifier.testTag("byok_psn_header_status_pill"),
                        shape = RoundedCornerShape(11.dp),
                        color = if (isPsnConnected) GeckoPrimaryContainer else Color(0xFF1A221E),
                        border = BorderStroke(
                            1.dp,
                            if (isPsnConnected) GeckoPrimary else GeckoOutlineVariant
                        )
                    ) {
                        Text(
                            text = if (isPsnConnected) "✓ Conectado" else "Não conectado",
                            color = if (isPsnConnected) GeckoPrimary else PlatformSubColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                ByokSecretTextField(
                    label = "Cookie npsso (64 caracteres)",
                    value = uiState.psnNpssoInput,
                    onValueChange = onPsnNpssoChange,
                    placeholder = "npsso: ••••••••••••••••",
                    isValidated = uiState.isPsnFormatValid,
                    errorMessage = uiState.psnValidationError,
                    connectionState = psnState,
                    onValidateClick = onValidatePsnClick,
                    testTag = "byok_token_field"
                )

                Text(
                    text = "🔒 Sessão renovada automaticamente por 60 dias após validar.",
                    color = Color(0xFF9E9E9E),
                    fontSize = 10.sp
                )
            }
        }

        // 3. Steam (Multi-campo via Bottom Sheet)
        ByokMultiInputProviderCard(
            provider = ByokProvider.STEAM,
            connectionState = uiState.connectionStateFor(ByokProvider.STEAM),
            onOpenSheetClick = { onOpenProviderSheet(ByokProvider.STEAM) }
        )

        // 4. RetroAchievements & RAWG (Opcional — id 441)
        val optionalConnected =
            uiState.connectionStateFor(ByokProvider.RETRO_ACHIEVEMENTS) == ByokConnectionState.CONNECTED ||
                uiState.connectionStateFor(ByokProvider.RAWG) == ByokConnectionState.CONNECTED
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("byok_provider_card_OPTIONAL"),
            shape = RoundedCornerShape(18.dp),
            color = PlatformCardBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color(0xFF1C2823))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RetroAchievements & RAWG",
                    color = Color(0xFFD5E2DC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                OutlinedButton(
                    onClick = { onOpenProviderSheet(ByokProvider.RETRO_ACHIEVEMENTS) },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("byok_configure_button_RETRO_ACHIEVEMENTS"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GeckoOutlineVariant)
                ) {
                    Text(
                        text = if (optionalConnected) "✓ Conectado" else "+ Configurar",
                        color = GeckoPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 5. Atalho de Migração Rápida CSV (CsvQuickImportRow — id 444)
        Surface(
            onClick = onNavigateToCsvImport,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 62.dp)
                .testTag("onboarding_csv_import_card"),
            shape = RoundedCornerShape(18.dp),
            color = CsvCardBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, CsvCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "📄 Tem uma planilha de jogos zerados?",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Importar arquivo CSV agora ›",
                    color = EmeraldSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 6. Aviso Modo Offline Puro (id 447)
        Text(
            text = "💡 Sem chaves? O Gecko funciona 100% offline no modo manual.",
            color = OfflineHintColor,
            fontSize = 11.sp,
            modifier = Modifier.testTag("onboarding_offline_assurance")
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
