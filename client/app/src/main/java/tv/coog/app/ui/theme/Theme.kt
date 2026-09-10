package tv.coog.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val CoogBgDeep = Color(0xFF070708)
val CoogBgMid = Color(0xFF0E0E10)
val CoogBgSoft = Color(0xFF161618)
val CoogOnSurface = Color(0xFFFFFFFF)
val CoogMuted = Color(0xFF7A7A80)
val CoogGlass = Color(0x14FFFFFF)
val CoogFocus = Color(0xFFFFFFFF)
val CoogText = Color.White
val CoogTextSecondary = Color.White.copy(alpha = 0.72f)
val CoogTextMuted = Color.White.copy(alpha = 0.48f)

object CoogType {
    val heroTitle = TextStyle(
        color = CoogText,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
        lineHeight = 38.sp,
    )
    val heroTagline = TextStyle(
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
    )
    val heroPlot = TextStyle(
        color = CoogTextSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
    )
    val chip = TextStyle(
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
    val shelfTitle = TextStyle(
        color = CoogTextMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
    val cardTitle = TextStyle(
        color = CoogText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
    )
    val cardYear = TextStyle(
        color = CoogTextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    )
    val clock = TextStyle(
        color = CoogTextSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
}

private val CoogDark = darkColorScheme(
    primary = CoogFocus,
    onPrimary = Color(0xFF121214),
    secondary = Color(0xFFD0D0D4),
    surface = CoogBgMid,
    onSurface = CoogOnSurface,
    surfaceVariant = CoogBgSoft,
    onSurfaceVariant = Color(0xB8FFFFFF),
    background = CoogBgDeep,
    onBackground = CoogOnSurface,
    error = Color(0xFFFF8B8B),
    border = Color(0x1FFFFFFF),
)

@Composable
fun CoogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CoogDark, content = content)
}
