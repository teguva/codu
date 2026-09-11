package tv.coog.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

data class CoogServer(
    val url: String,
    val token: String,
) {
    fun mediaUrl(id: String, kind: String): String {
        val base = url.trimEnd('/')
        return "$base/api/v1/media/$id/$kind"
    }

    fun artworkUrl(id: String): String = mediaUrl(id, "backdrop")

    fun posterUrl(id: String, cacheKey: String = ""): String =
        mediaUrl(id, "poster").let { if (cacheKey.isBlank()) it else "$it?v=$cacheKey" }

    fun backdropUrl(id: String, cacheKey: String = ""): String =
        mediaUrl(id, "backdrop").let { if (cacheKey.isBlank()) it else "$it?v=$cacheKey" }

    fun logoUrl(id: String, cacheKey: String = ""): String =
        mediaUrl(id, "logo").let { if (cacheKey.isBlank()) it else "$it?v=$cacheKey" }

    fun trailerUrl(id: String): String = mediaUrl(id, "trailer")

    fun streamUrl(id: String): String = mediaUrl(id, "stream")
}

val LocalCoogServer = staticCompositionLocalOf { CoogServer("", "") }

val LocalBrowseContentFocus = staticCompositionLocalOf<FocusRequester?> { null }

val LocalRailFocus = staticCompositionLocalOf<FocusRequester?> { null }

val LocalNavBarFocused = staticCompositionLocalOf { false }

val LocalEnterRail = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun Modifier.exitToRailOnUp(enabled: Boolean = true, location: String = "exitToRailOnUp"): Modifier {
    val enterRail = LocalEnterRail.current
    if (!enabled) return this
    return onPreviewKeyEvent { event ->
        if (event.key != Key.DirectionUp) return@onPreviewKeyEvent false
        // #region agent log
        coogDebug(
            "F",
            location,
            "up to rail",
            mapOf("type" to event.type.toString()),
            runId = "post-fix",
        )
        // #endregion
        if (event.type == KeyEventType.KeyDown) enterRail()
        event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp
    }
}
