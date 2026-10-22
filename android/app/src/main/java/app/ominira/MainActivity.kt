package app.ominira

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
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
        // The screen stays on while the app is open: a customer listening or counting pulses is not touching it.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    // Volume down starts listening once the app has its microphone; volume up still sets the volume.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val mic = session.volumeMic
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && mic != null) {
            if (event.repeatCount == 0) mic()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        session.watchIncoming()
    }

    override fun onPause() {
        session.stopWatching()
        super.onPause()
    }

    override fun onDestroy() {
        // Session outlives the activity; drop the old window.
        session.announcer = null
        super.onDestroy()
    }
}
