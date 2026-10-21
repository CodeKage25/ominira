package app.ominira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import app.ominira.ui.OminiraApp
import app.ominira.ui.theme.Look

/** The one activity: edge to edge, hosting the Compose app. */
class MainActivity : ComponentActivity() {

    private val session: Session by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // With TalkBack on, the app's lines go to TalkBack rather than a second voice.
        session.announcer = { line -> window.decorView.announceForAccessibility(line) }
        setContent {
            // Bar icons follow the app's look, not the phone's dark setting.
            val lightBars = session.look == Look.Paper
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
            OminiraApp(session)
        }
    }

    override fun onDestroy() {
        // The view model outlives this activity; it must not keep the old window.
        session.announcer = null
        super.onDestroy()
    }
}
