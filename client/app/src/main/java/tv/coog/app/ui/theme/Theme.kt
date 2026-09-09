package tv.coog.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val CoogDark = darkColorScheme(
    primary = Color(0xFF6B9BFF),
    onPrimary = Color(0xFF001A41),
    surface = Color(0xFF10141C),
    onSurface = Color(0xFFE8EEF8),
    background = Color(0xFF10141C),
    onBackground = Color(0xFFE8EEF8),
)

@Composable
fun CoogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CoogDark, content = content)
}
