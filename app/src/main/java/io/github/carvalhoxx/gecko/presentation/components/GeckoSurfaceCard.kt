package io.github.carvalhoxx.gecko.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.carvalhoxx.gecko.ui.theme.GeckoOutlineVariant
import io.github.carvalhoxx.gecko.ui.theme.GeckoSurface

/**
 * Card padrão do Design System Gecko utilizando estritamente Tonal Elevation M3
 * (shadowElevation = 0.dp, sem hard drop shadows), border radius de 12.dp,
 * padding interno de 16.dp (grelha 8pt) e borda sutil.
 */
@Composable
fun GeckoSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = GeckoSurface,
    borderColor: Color = GeckoOutlineVariant,
    tonalElevation: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val taggedModifier = if (testTag != null) {
        modifier.testTag(testTag)
    } else {
        modifier
    }

    val border = BorderStroke(width = 1.dp, color = borderColor)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = taggedModifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            shape = shape,
            color = containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = 0.dp,
            border = border
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    } else {
        Surface(
            modifier = taggedModifier,
            shape = shape,
            color = containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = 0.dp,
            border = border
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}
