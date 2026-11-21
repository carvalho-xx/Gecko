package io.github.carvalhoxx.gecko.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.carvalhoxx.gecko.domain.model.ByokConnectionState
import io.github.carvalhoxx.gecko.ui.theme.GeckoError
import io.github.carvalhoxx.gecko.ui.theme.GeckoOnPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoOutlineVariant
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimaryContainer
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimarySoft
import io.github.carvalhoxx.gecko.ui.theme.GeckoSurface
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextSecondary

/**
 * Estado visual determinístico e mutuamente exclusivo (UDF) para campos de segredo BYOK.
 * Invariante [STATE: EXCLUSIVE_VISUAL_STATE ! (CURSOR_AND_SUCCESS | ACTION_AND_ACTIVE)]:
 * - Em foco: mostra botão limpar (X) quando há texto; NUNCA mostra ícone de validação (✓).
 * - Sem foco e validado: mostra ícone de validação (✓); NUNCA mostra botão limpar (X) ou cursor.
 */
enum class ByokFieldTrailingVisualState {
    NONE,
    FOCUSED_CLEARABLE,
    UNFOCUSED_VALIDATED
}

fun resolveByokFieldTrailingState(
    isFocused: Boolean,
    value: String,
    isValidated: Boolean
): ByokFieldTrailingVisualState = when {
    isFocused && value.isNotEmpty() -> ByokFieldTrailingVisualState.FOCUSED_CLEARABLE
    !isFocused && isValidated && value.isNotBlank() -> ByokFieldTrailingVisualState.UNFOCUSED_VALIDATED
    else -> ByokFieldTrailingVisualState.NONE
}

/**
 * Campo de credencial BYOK (TUI-02 / RNF03):
 * - Rótulo imediatamente acima do campo de input.
 * - Mascaramento obrigatório com [PasswordVisualTransformation].
 * - Exclusividade estrita entre estado de foco (limpar `X`) e estado validado sem foco (`✓`).
 * - Exclusividade estrita entre botão de ação (`Validar`) e badge de status (`✓ Conectado`).
 */
@Composable
fun ByokSecretTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isValidated: Boolean = false,
    errorMessage: String? = null,
    connectionState: ByokConnectionState = ByokConnectionState.NOT_CONFIGURED,
    onValidateClick: (() -> Unit)? = null,
    testTag: String = "byok_token_field"
) {
    var isFocused by remember { mutableStateOf(false) }
    val trailingState = resolveByokFieldTrailingState(
        isFocused = isFocused,
        value = value,
        isValidated = isValidated
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = GeckoTextPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag("${testTag}_label")
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                .testTag(testTag),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = errorMessage != null,
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                if (placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = GeckoTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            trailingIcon = {
                when (trailingState) {
                    ByokFieldTrailingVisualState.FOCUSED_CLEARABLE -> {
                        Surface(
                            onClick = { onValueChange("") },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("${testTag}_clear_button"),
                            color = GeckoSurface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "✕",
                                    color = GeckoTextSecondary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    ByokFieldTrailingVisualState.UNFOCUSED_VALIDATED -> {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("${testTag}_valid_icon"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                color = GeckoPrimarySoft,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    ByokFieldTrailingVisualState.NONE -> Unit
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GeckoSurface,
                unfocusedContainerColor = GeckoSurface,
                focusedBorderColor = GeckoPrimary,
                unfocusedBorderColor = GeckoOutlineVariant,
                errorBorderColor = GeckoError,
                focusedTextColor = GeckoTextPrimary,
                unfocusedTextColor = GeckoTextPrimary,
                cursorColor = GeckoPrimary
            )
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = GeckoError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("${testTag}_error")
            )
        }

        if (onValidateClick != null || connectionState == ByokConnectionState.CONNECTED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (connectionState == ByokConnectionState.CONNECTED) {
                    Surface(
                        modifier = Modifier.testTag("${testTag}_connected_badge"),
                        shape = RoundedCornerShape(8.dp),
                        color = GeckoPrimaryContainer,
                        border = BorderStroke(1.dp, GeckoPrimary)
                    ) {
                        Text(
                            text = "✓ Conectado",
                            color = GeckoPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                } else if (onValidateClick != null) {
                    Button(
                        onClick = onValidateClick,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("${testTag}_validate_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeckoPrimary,
                            contentColor = GeckoOnPrimary
                        )
                    ) {
                        Text(
                            text = "Validar",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
