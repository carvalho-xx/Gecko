package io.github.carvalhoxx.gecko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.carvalhoxx.gecko.core.ReleaseFeatureFlags
import io.github.carvalhoxx.gecko.data.sync.SessionNotificationBuilder
import io.github.carvalhoxx.gecko.presentation.navigation.GeckoAppScaffold
import io.github.carvalhoxx.gecko.ui.theme.GeckoTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var releaseFeatureFlags: ReleaseFeatureFlags

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialNavigateGameId = intent
            ?.getLongExtra(SessionNotificationBuilder.EXTRA_NAVIGATE_GAME_ID, -1L)
            ?.takeIf { it > 0L }

        val activeFlags = if (::releaseFeatureFlags.isInitialized) {
            releaseFeatureFlags
        } else {
            ReleaseFeatureFlags.DEFAULT
        }

        setContent {
            GeckoTheme {
                GeckoAppScaffold(
                    featureFlags = activeFlags,
                    initialNavigateGameId = initialNavigateGameId
                )
            }
        }
    }
}
