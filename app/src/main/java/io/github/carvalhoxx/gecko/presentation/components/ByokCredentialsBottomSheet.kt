package io.github.carvalhoxx.gecko.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.domain.model.ByokProvider
import io.github.carvalhoxx.gecko.ui.theme.GeckoOnPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoOutlineVariant
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimaryContainer
import io.github.carvalhoxx.gecko.ui.theme.GeckoSurface
import io.github.carvalhoxx.gecko.ui.theme.GeckoSurfaceHigh
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextSecondary

/**
 * Especificação de formulário multi-campo para credenciais BYOK
 * ([FORM: BYOK_MULTI_INPUT(BottomSheet_or_Dialog) ! INLINE_CONCATENATED_SECRETS]).
 */
data class ByokMultiFieldSpec(
    val provider: ByokProvider,
    val title: String,
    val subtitle: String,
    val primaryFieldLabel: String,
    val primaryFieldPlaceholder: String,
    val secondaryFieldLabel: String,
    val secondaryFieldPlaceholder: String
)

fun multiFieldSpecFor(provider: ByokProvider): ByokMultiFieldSpec = when (provider) {
    ByokProvider.IGDB -> ByokMultiFieldSpec(
        provider = ByokProvider.IGDB,
        title = "IGDB / Twitch (SSOT)",
        subtitle = "Catálogo oficial, capas e metadados",
        primaryFieldLabel = "Client ID",
        primaryFieldPlaceholder = "Insira o Client ID da Twitch",
        secondaryFieldLabel = "Client Secret",
        secondaryFieldPlaceholder = "Insira o Client Secret da Twitch"
    )
    ByokProvider.STEAM -> ByokMultiFieldSpec(
        provider = ByokProvider.STEAM,
        title = "Steam (Biblioteca PC)",
        subtitle = "Jogos adquiridos e tempo via Delta Baseline",
        primaryFieldLabel = "Web API Key",
        primaryFieldPlaceholder = "Insira sua Steam Web API Key",
        secondaryFieldLabel = "SteamID64",
        secondaryFieldPlaceholder = "17 dígitos numéricos (ex.: 76561198...)"
    )
    ByokProvider.RETRO_ACHIEVEMENTS -> ByokMultiFieldSpec(
        provider = ByokProvider.RETRO_ACHIEVEMENTS,
        title = "RetroAchievements",
        subtitle = "Conquistas de consoles clássicos",
        primaryFieldLabel = "Nome de Usuário",
        primaryFieldPlaceholder = "Seu usuário no RetroAchievements",
        secondaryFieldLabel = "Web API Key",
        secondaryFieldPlaceholder = "Chave de API do RetroAchievements"
    )
    ByokProvider.PSN -> ByokMultiFieldSpec(
        provider = ByokProvider.PSN,
        title = "PlayStation Network",
        subtitle = "Sincronização via cookie npsso",
        primaryFieldLabel = "Cookie npsso (64 caracteres)",
        primaryFieldPlaceholder = "Token alfanumérico de 64 caracteres",
        secondaryFieldLabel = "",
        secondaryFieldPlaceholder = ""
    )
    ByokProvider.RAWG -> ByokMultiFieldSpec(
        provider = ByokProvider.RAWG,
        title = "RAWG Database",
        subtitle = "Fallback de artes (limite 20.000 req/mês)",
        primaryFieldLabel = "RAWG API Key",
        primaryFieldPlaceholder = "Insira sua chave da RAWG",
        secondaryFieldLabel = "",
        secondaryFieldPlaceholder = ""
    )
}

/**
 * Card resumido para integrações BYOK multi-campo (IGDB, Steam, RetroAchievements):
 * - Quando não conectado: exibe estado colapsado com botão `+ Configurar` (abre Bottom Sheet).
 * - Quando conectado: exibe badge `✓ Conectado` e botão secundário `Editar`, sem concatenar segredos inline.
 */
@Composable
fun ByokMultiInputProviderCard(
    provider: ByokProvider,
    connectionState: ByokConnectionState,
    onOpenSheetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = multiFieldSpecFor(provider)
    val isConnected = connectionState == ByokConnectionState.CONNECTED

    GeckoSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        testTag = "byok_provider_card_${provider.name}"
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
                    text = spec.title,
                    color = GeckoTextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = spec.subtitle,
                    color = GeckoTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isConnected) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.testTag("byok_connected_badge_${provider.name}"),
                        shape = RoundedCornerShape(8.dp),
                        color = GeckoPrimaryContainer,
                        border = BorderStroke(1.dp, GeckoPrimary)
                    ) {
                        Text(
                            text = "✓ Conectado",
                            color = GeckoPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenSheetClick,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("byok_edit_button_${provider.name}"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GeckoOutlineVariant)
                    ) {
                        Text(
                            text = "Editar",
                            color = GeckoTextPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onOpenSheetClick,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("byok_configure_button_${provider.name}"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GeckoOutlineVariant)
                ) {
                    Text(
                        text = "+ Configurar",
                        color = GeckoPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * Conteúdo do Bottom Sheet de credenciais multi-campo com inputs separados e mascarados.
 */
@Composable
fun ByokCredentialsSheetContent(
    provider: ByokProvider,
    initialPrimaryValue: String = "",
    initialSecondaryValue: String = "",
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSaveAndValidate: (primaryValue: String, secondaryValue: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = multiFieldSpecFor(provider)
    var primaryValue by remember(provider, initialPrimaryValue) { mutableStateOf(initialPrimaryValue) }
    var secondaryValue by remember(provider, initialSecondaryValue) { mutableStateOf(initialSecondaryValue) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("byok_bottom_sheet_${provider.name}"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configurar ${spec.title}",
            color = GeckoTextPrimary,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Suas chaves são criptografadas via Android Keystore e nunca saem do aparelho.",
            color = GeckoTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )

        ByokSecretTextField(
            label = spec.primaryFieldLabel,
            value = primaryValue,
            onValueChange = { primaryValue = it },
            placeholder = spec.primaryFieldPlaceholder,
            isValidated = primaryValue.isNotBlank(),
            testTag = "byok_sheet_primary_field_${provider.name}"
        )

        if (spec.secondaryFieldLabel.isNotEmpty()) {
            ByokSecretTextField(
                label = spec.secondaryFieldLabel,
                value = secondaryValue,
                onValueChange = { secondaryValue = it },
                placeholder = spec.secondaryFieldPlaceholder,
                isValidated = secondaryValue.isNotBlank(),
                errorMessage = errorMessage,
                testTag = "byok_sheet_secondary_field_${provider.name}"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("byok_sheet_cancel_${provider.name}"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GeckoOutlineVariant)
            ) {
                Text(
                    text = "Cancelar",
                    color = GeckoTextPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Button(
                onClick = { onSaveAndValidate(primaryValue.trim(), secondaryValue.trim()) },
                enabled = primaryValue.isNotBlank() &&
                    (spec.secondaryFieldLabel.isEmpty() || secondaryValue.isNotBlank()),
                modifier = Modifier
                    .weight(2f)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("byok_sheet_save_${provider.name}"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GeckoPrimary,
                    contentColor = GeckoOnPrimary,
                    disabledContainerColor = GeckoSurface,
                    disabledContentColor = GeckoTextSecondary
                )
            ) {
                Text(
                    text = "Salvar chaves →",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByokCredentialsBottomSheet(
    provider: ByokProvider,
    initialPrimaryValue: String = "",
    initialSecondaryValue: String = "",
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSaveAndValidate: (primaryValue: String, secondaryValue: String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GeckoSurfaceHigh,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        ByokCredentialsSheetContent(
            provider = provider,
            initialPrimaryValue = initialPrimaryValue,
            initialSecondaryValue = initialSecondaryValue,
            errorMessage = errorMessage,
            onDismiss = onDismiss,
            onSaveAndValidate = onSaveAndValidate
        )
    }
}
