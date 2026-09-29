package com.jhani.measurear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.jhani.measurear.measurement.MeasureMode
import com.jhani.measurear.presentation.ARScreen
import com.jhani.measurear.presentation.AppLanguage
import com.jhani.measurear.presentation.StampKeys
import com.jhani.measurear.ui.theme.MeasureARTheme

class MainActivity : ComponentActivity() {

    /** Mode asked for by a launcher shortcut, until the screen picks it up. */
    private val requestedMode = mutableStateOf<MeasureMode?>(null)

    private fun readMode(intent: Intent?) {
        val name = intent?.getStringExtra("mode") ?: return
        requestedMode.value = MeasureMode.values().firstOrNull { it.name == name }
    }

    // Apply the in-app language choice to everything this activity shows
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readMode(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isVolume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        // Stamp once per press (ignore auto-repeat while held)
        if (isVolume && event.repeatCount == 0 && StampKeys.onVolumeKey()) return true
        if (isVolume && StampKeys.enabled) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the display awake while measuring
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readMode(intent)
        setContent {
            MeasureARTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ARScreen(
                        requestedMode = requestedMode.value,
                        onModeRequestHandled = { requestedMode.value = null }
                    )
                }
            }
        }
    }
}
