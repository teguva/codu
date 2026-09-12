package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

enum class ArtKind { Poster, Backdrop, Still }

@Composable
fun PosterArt(
    item: MediaItem,
    modifier: Modifier = Modifier,
    kind: ArtKind = ArtKind.Poster,
    mark: CardMark? = null,
    matchPercent: Int? = null,
    showMatch: Boolean = false,
    marksSize: MarkSize = MarkSize.Compact,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = if (kind == ArtKind.Backdrop) Alignment.CenterEnd else Alignment.Center,
    serverFallback: Boolean = true,
) {
    val server = LocalCoogServer.current
    val (top, bottom) = item.posterColors()
    var failed by remember(item.id, kind, server.url, item.posterUrl, item.backdropUrl) { mutableStateOf(false) }
    val cacheKey = item.imdbId.ifBlank { "none" }
    val remote = when (kind) {
        ArtKind.Poster -> item.posterUrl
        ArtKind.Backdrop, ArtKind.Still -> item.backdropUrl.ifBlank { item.posterUrl }
    }
    val localArt = remote.contains("/api/v1/media/") && (
        remote.contains("/poster") || remote.contains("/backdrop") || remote.contains("/artwork")
    )
    val url = when {
        remote.startsWith("http") && !localArt -> remote
        !serverFallback -> if (remote.startsWith("http")) remote else ""
        kind == ArtKind.Poster -> server.posterUrl(item.id, cacheKey)
        else -> server.backdropUrl(item.id, cacheKey)
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
        CardMarks(
            mark = mark,
            matchPercent = matchPercent,
            size = marksSize,
            showMatch = showMatch,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
        )
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
            ArtKind.Still -> {
                val w = (config.screenWidthDp * 0.28f * density).toInt().coerceIn(320, 780)
                w to (w * 9 / 16)
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
