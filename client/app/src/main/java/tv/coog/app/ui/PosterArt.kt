package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import tv.coog.app.data.MediaItem

enum class ArtKind { Poster, Backdrop }

@Composable
fun PosterArt(
    item: MediaItem,
    modifier: Modifier = Modifier,
    kind: ArtKind = ArtKind.Poster,
    badge: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = if (kind == ArtKind.Backdrop) Alignment.CenterEnd else Alignment.Center,
) {
    val server = LocalCoogServer.current
    val (top, bottom) = item.posterColors()
    var failed by remember(item.id, kind, server.url) { mutableStateOf(false) }
    val cacheKey = item.imdbId.ifBlank { "none" }
    val remote = when (kind) {
        ArtKind.Poster -> item.posterUrl
        ArtKind.Backdrop -> item.backdropUrl.ifBlank { item.posterUrl }
    }
    val url = if (remote.startsWith("http")) {
        remote
    } else {
        when (kind) {
            ArtKind.Poster -> server.posterUrl(item.id, cacheKey)
            ArtKind.Backdrop -> server.backdropUrl(item.id, cacheKey)
        }
    }
    val (decodeW, decodeH) = rememberArtPixels(kind)
    Box(modifier = modifier.background(Brush.linearGradient(listOf(top, bottom)))) {
        if (url.isNotBlank() && !failed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .size(decodeW, decodeH)
                    .scale(Scale.FILL)
                    .apply {
                        if (server.token.isNotBlank()) {
                            addHeader("Authorization", "Bearer ${server.token}")
                        }
                    }
                    .crossfade(200)
                    .build(),
                contentDescription = item.headline(),
                contentScale = contentScale,
                alignment = alignment,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (failed || server.url.isBlank()) {
            if (kind == ArtKind.Poster) {
                Text(
                    text = item.monogram(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (!badge.isNullOrBlank()) {
            Text(
                text = badge.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun rememberArtPixels(kind: ArtKind): Pair<Int, Int> {
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    return remember(kind, config.screenWidthDp, config.screenHeightDp, density) {
        when (kind) {
            ArtKind.Poster -> {
                val w = (config.screenWidthDp * 0.14f * density).toInt().coerceIn(180, 420)
                w to (w * 1.5f).toInt()
            }
            ArtKind.Backdrop -> {
                val w = (config.screenWidthDp * density).toInt().coerceIn(960, 1920)
                val h = (config.screenHeightDp * 0.62f * density).toInt().coerceIn(420, 1080)
                w to h
            }
        }
    }
}

@Composable
fun Scrim(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                    MaterialTheme.colorScheme.background,
                ),
            ),
        ),
        content = content,
    )
}
