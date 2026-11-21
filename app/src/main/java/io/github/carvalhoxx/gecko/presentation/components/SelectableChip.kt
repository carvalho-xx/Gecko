package io.github.carvalhoxx.gecko.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.carvalhoxx.gecko.ui.theme.GeckoOutlineVariant
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoPrimaryContainer
import io.github.carvalhoxx.gecko.ui.theme.GeckoSurface
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextPrimary
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextSecondary

/**
 * Chip/Cartão selecionável seguindo as invariantes M3 e Acessibilidade:
 * - [A11Y: TOUCH_TARGET_MIN(48dp) & EXPLICIT_INACTIVE_BORDERS]:
 *   minWidth = 48.dp, minHeight = 48.dp, borda explícita de 1.dp em GeckoOutlineVariant (#2A3530) quando inativo.
 * - [UI: VIEWPORT_BUDGET(360dp_width) ! TEXT_OVERFLOW_3_COLS]:
 *   adapta dinamicamente o tamanho da fonte em diferentes escalas de fonte e zoom de tela para evitar truncamento ("...").
 */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val containerColor = if (selected) GeckoPrimaryContainer else GeckoSurface
    val borderColor = if (selected) GeckoPrimary else GeckoOutlineVariant
    val labelColor = if (selected) GeckoPrimary else GeckoTextPrimary
    val subtitleColor = if (selected) GeckoTextPrimary else GeckoTextSecondary

    val baseModifier = if (testTag != null) {
        modifier.testTag(testTag)
    } else {
        modifier
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = baseModifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                this.selected = selected
                this.role = Role.Tab
            },
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        tonalElevation = if (selected) 4.dp else 1.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(width = 1.dp, color = borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AutoFitSingleLineText(
                text = label,
                color = labelColor,
                baseStyle = MaterialTheme.typography.labelLarge,
                minFontSize = 9.sp
            )
            if (!subtitle.isNullOrBlank()) {
                AutoFitSingleLineText(
                    text = subtitle,
                    color = subtitleColor,
                    baseStyle = MaterialTheme.typography.labelSmall,
                    minFontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun AutoFitSingleLineText(
    text: String,
    color: Color,
    baseStyle: TextStyle,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val initialSize = if (baseStyle.fontSize != TextUnit.Unspecified) baseStyle.fontSize else 12.sp
    var currentFontSize by remember(text, initialSize) { mutableStateOf(initialSize) }
    var currentLetterSpacing by remember(text, baseStyle.letterSpacing) {
        mutableStateOf(baseStyle.letterSpacing)
    }

    Text(
        text = text,
        color = color,
        style = baseStyle.copy(
            fontSize = currentFontSize,
            lineHeight = (currentFontSize.value * 1.2f).sp,
            letterSpacing = currentLetterSpacing
        ),
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { layoutResult ->
            if (layoutResult.didOverflowWidth && currentFontSize.value > minFontSize.value) {
                val nextValue = (currentFontSize.value * 0.9f).coerceAtLeast(minFontSize.value)
                currentFontSize = nextValue.sp
                currentLetterSpacing = 0.sp
            }
        }
    )
}
