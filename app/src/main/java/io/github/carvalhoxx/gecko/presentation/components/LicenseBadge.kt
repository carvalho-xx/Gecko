package io.github.carvalhoxx.gecko.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.carvalhoxx.gecko.domain.model.LicenseType
import io.github.carvalhoxx.gecko.ui.theme.GeckoLicenseOwnedBg
import io.github.carvalhoxx.gecko.ui.theme.GeckoLicenseOwnedBorder
import io.github.carvalhoxx.gecko.ui.theme.GeckoLicenseOwnedText
import io.github.carvalhoxx.gecko.ui.theme.GeckoLicenseSaasBg
import io.github.carvalhoxx.gecko.ui.theme.GeckoLicenseSaasBorder
import io.github.carvalhoxx.gecko.ui.theme.GeckoLicenseSaasText
import io.github.carvalhoxx.gecko.ui.theme.GeckoLicenseWishlistBg
import io.github.carvalhoxx.gecko.ui.theme.GeckoLicenseWishlistBorder
import io.github.carvalhoxx.gecko.ui.theme.GeckoLicenseWishlistText

/**
 * Badge visual inequívoco para diferenciação de categoria de licença (RF10 / RNF04 / TUI-01):
 * - OWNED -> "Possuo"
 * - SUBSCRIPTION -> "SaaS" (Assinatura Temporária)
 * - WISHLIST -> "Quero Comprar"
 */
@Composable
fun LicenseBadge(
    licenseType: LicenseType,
    modifier: Modifier = Modifier,
    testTag: String = "license_badge_${licenseType.name}"
) {
    val (label, bgColor, borderColor, textColor) = when (licenseType) {
        LicenseType.OWNED -> LicenseVisualSpec(
            label = "Possuo",
            backgroundColor = GeckoLicenseOwnedBg,
            borderColor = GeckoLicenseOwnedBorder,
            textColor = GeckoLicenseOwnedText
        )
        LicenseType.SUBSCRIPTION -> LicenseVisualSpec(
            label = "SaaS",
            backgroundColor = GeckoLicenseSaasBg,
            borderColor = GeckoLicenseSaasBorder,
            textColor = GeckoLicenseSaasText
        )
        LicenseType.WISHLIST -> LicenseVisualSpec(
            label = "Quero Comprar",
            backgroundColor = GeckoLicenseWishlistBg,
            borderColor = GeckoLicenseWishlistBorder,
            textColor = GeckoLicenseWishlistText
        )
    }

    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private data class LicenseVisualSpec(
    val label: String,
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val borderColor: androidx.compose.ui.graphics.Color,
    val textColor: androidx.compose.ui.graphics.Color
)
