package io.github.carvalhoxx.gecko.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.carvalhoxx.gecko.domain.model.GameStatus
import io.github.carvalhoxx.gecko.ui.theme.GeckoOutlineVariant
import io.github.carvalhoxx.gecko.ui.theme.GeckoStatusAbandoned
import io.github.carvalhoxx.gecko.ui.theme.GeckoStatusBacklog
import io.github.carvalhoxx.gecko.ui.theme.GeckoStatusCompleted
import io.github.carvalhoxx.gecko.ui.theme.GeckoStatusPaused
import io.github.carvalhoxx.gecko.ui.theme.GeckoStatusPlaying
import io.github.carvalhoxx.gecko.ui.theme.GeckoStatusWishlist
import io.github.carvalhoxx.gecko.ui.theme.GeckoSurfaceVariant
import io.github.carvalhoxx.gecko.ui.theme.GeckoTextPrimary

/**
 * Badge de estado do jogo com indicador cromático semântico (RF20 / RN-BKL-01):
 * diferencia visualmente Pausado (#F59E0B) e Abandonado (cinza dessaturado)
 * de Backlog / Jogando / Zerado.
 */
@Composable
fun StatusDotBadge(
    status: GameStatus,
    modifier: Modifier = Modifier,
    testTag: String = "status_dot_badge_${status.name}"
) {
    val (label, dotColor) = statusVisualFor(status)

    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        shape = RoundedCornerShape(8.dp),
        color = GeckoSurfaceVariant,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, GeckoOutlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = dotColor, shape = CircleShape)
            )
            Text(
                text = label,
                color = GeckoTextPrimary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

fun statusVisualFor(status: GameStatus): Pair<String, Color> = when (status) {
    GameStatus.PLAYING -> "Jogando" to GeckoStatusPlaying
    GameStatus.BACKLOG -> "Backlog" to GeckoStatusBacklog
    GameStatus.COMPLETED -> "Zerado" to GeckoStatusCompleted
    GameStatus.PAUSED -> "Pausado" to GeckoStatusPaused
    GameStatus.ABANDONED -> "Abandonado" to GeckoStatusAbandoned
    GameStatus.WISHLIST -> "Quero Comprar" to GeckoStatusWishlist
}
