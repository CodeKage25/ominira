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

class MainActivity : ComponentActivity() {

    private val session: Session by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // TalkBack on: lines go to TalkBack, never a second voice.
        session.announcer = { line -> window.decorView.announceForAccessibility(line) }
        setContent {
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
        // Session outlives the activity; drop the old window.
        session.announcer = null
        super.onDestroy()
    }
}
