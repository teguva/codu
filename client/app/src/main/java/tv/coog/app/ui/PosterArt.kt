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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    val url = when (kind) {
        ArtKind.Poster -> server.posterUrl(item.id, cacheKey)
        ArtKind.Backdrop -> server.backdropUrl(item.id, cacheKey)
    }
    Box(modifier = modifier.background(Brush.linearGradient(listOf(top, bottom)))) {
        if (server.url.isNotBlank() && !failed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .apply {
                        if (server.token.isNotBlank()) {
                            addHeader("Authorization", "Bearer ${server.token}")
                        }
                    }
                    .crossfade(true)
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
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
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
