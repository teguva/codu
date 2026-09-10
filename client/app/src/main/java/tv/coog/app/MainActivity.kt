package tv.coog.app

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import tv.coog.app.ui.CoogApp
import tv.coog.app.ui.theme.CoogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoogTheme {
                CoogApp()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mapped = mapToDpad(event) ?: event
        if (isDpad(mapped.keyCode) && currentFocus?.isInTouchMode == true) {
            currentFocus?.isFocusableInTouchMode = false
        }
        return super.dispatchKeyEvent(mapped)
    }

    private fun isDpad(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> true
        else -> false
    }

    private fun mapToDpad(event: KeyEvent): KeyEvent? {
        val code = when (event.keyCode) {
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> KeyEvent.KEYCODE_DPAD_UP
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_NUMPAD_ENTER -> KeyEvent.KEYCODE_DPAD_CENTER
            else -> return null
        }
        return KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            code,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source or InputDevice.SOURCE_DPAD,
        )
    }
}
