package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.compose.PlayerSurface
import androidx.tv.material3.Card
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.coog.app.data.ApiException
import tv.coog.app.data.CoogApi
import tv.coog.app.data.MediaItem
import tv.coog.app.data.PlaybackSession
import tv.coog.app.data.SettingsRepository
import tv.coog.app.player.PlayerViewModel

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Details(val item: MediaItem) : Screen
    data class Player(val session: PlaybackSession) : Screen
}

@Composable
fun CoogApp() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.applicationContext) }
    val serverUrl by settings.serverUrl.collectAsState(initial = "http://10.0.2.2:8090")
    val token by settings.token.collectAsState(initial = "")
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var status by remember { mutableStateOf("Connecting…") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(serverUrl, token) {
        error = null
        status = "Connecting…"
        try {
            val api = CoogApi(serverUrl, token)
            val health = api.health()
            items = api.library()
            status = "Coog ${health.version} · ${items.size} titles"
        } catch (e: Exception) {
            status = "Unreachable"
            error = e.message ?: "Could not reach $serverUrl"
        }
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            status = status,
            error = error,
            items = items,
            onOpenSettings = { screen = Screen.Settings },
            onOpen = { screen = Screen.Details(it) },
        )
        Screen.Settings -> SettingsScreen(
            serverUrl = serverUrl,
            token = token,
            onSave = { url, tok ->
                scope.launch {
                    settings.setServerUrl(url)
                    settings.setToken(tok)
                    screen = Screen.Home
                }
            },
            onBack = { screen = Screen.Home },
        )
        is Screen.Details -> DetailsScreen(
            item = current.item,
            onBack = { screen = Screen.Home },
            onPlay = {
                scope.launch {
                    try {
                        val session = CoogApi(serverUrl, token).playbackSession(current.item.id)
                        if (session.error.isNotBlank()) {
                            error = session.error
                        } else {
                            screen = Screen.Player(session)
                        }
                    } catch (e: ApiException) {
                        error = e.message
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
        )
        is Screen.Player -> PlayerScreen(
            session = current.session,
            token = token,
            onBack = { screen = Screen.Home },
        )
    }
}

@Composable
private fun HomeScreen(
    status: String,
    error: String?,
    items: List<MediaItem>,
    onOpenSettings: () -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    val movies = items.filter { it.kind == "movie" }
    val series = items.filter { it.kind == "episode" }
    val other = items.filter { it.kind != "movie" && it.kind != "episode" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Text("Coog", style = MaterialTheme.typography.displaySmall)
            Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Surface(
                onClick = onOpenSettings,
                modifier = Modifier.padding(top = 12.dp),
                scale = ClickableSurfaceDefaults.scale(),
            ) {
                Text("Settings", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
        if (movies.isNotEmpty()) {
            item { MediaRow("Movies", movies, onOpen) }
        }
        if (series.isNotEmpty()) {
            item { MediaRow("Series", series, onOpen) }
        }
        if (other.isNotEmpty()) {
            item { MediaRow("Library", other, onOpen) }
        }
        if (items.isEmpty()) {
            item {
                Text("No titles yet. Scan a Videos/Movies + Videos/Series folder on the server, then open Settings if the URL is wrong.")
            }
        }
    }
}

@Composable
private fun MediaRow(label: String, items: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 48.dp),
        ) {
            items(items, key = { it.id }) { item ->
                Card(onClick = { onOpen(item) }, modifier = Modifier.width(280.dp).height(140.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.showTitle.ifBlank { item.title }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        if (item.showTitle.isNotBlank()) {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                        Text(
                            listOfNotNull(
                                item.codecVideo.ifBlank { null },
                                item.hdr.ifBlank { null },
                                if (item.width > 0) "${item.width}p" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsScreen(item: MediaItem, onBack: () -> Unit, onPlay: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onBack()
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.displaySmall)
        if (item.showTitle.isNotBlank()) {
            Text(item.showTitle + seasonEpisode(item), style = MaterialTheme.typography.titleLarge)
        }
        Text(
            listOfNotNull(
                item.codecVideo.ifBlank { null },
                item.codecAudio.ifBlank { null },
                if (item.width > 0) "${item.width}×${item.height}" else null,
                item.hdr.ifBlank { null },
            ).joinToString(" · "),
        )
        Surface(onClick = onPlay) {
            Text("Play", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        }
        Surface(onClick = onBack) {
            Text("Back", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        }
    }
}

private fun seasonEpisode(item: MediaItem): String {
    if (item.season <= 0 && item.episode <= 0) return ""
    return "  S${item.season.toString().padStart(2, '0')}E${item.episode.toString().padStart(2, '0')}"
}

@Composable
private fun SettingsScreen(
    serverUrl: String,
    token: String,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember(serverUrl) { mutableStateOf(serverUrl) }
    var tok by remember(token) { mutableStateOf(token) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall)
        Text("Server URL")
        BasicTextField(
            value = url,
            onValueChange = { url = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(16.dp),
        )
        Text("Bearer token")
        BasicTextField(
            value = tok,
            onValueChange = { tok = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(16.dp),
        )
        Surface(onClick = { onSave(url, tok) }) {
            Text("Save", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        }
        Surface(onClick = onBack) {
            Text("Back", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun PlayerScreen(
    session: PlaybackSession,
    token: String,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val player = playerViewModel.player
    var hudVisible by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var buffered by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(session.expectedDurationMs) }

    LaunchedEffect(session.url) {
        playerViewModel.play(session.url, token)
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            val playerDuration = player.duration
            duration = when {
                session.expectedDurationMs > 0 -> session.expectedDurationMs
                playerDuration > 0 -> playerDuration
                else -> duration
            }
            kotlinx.coroutines.delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back -> {
                        player.pause()
                        onBack()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (player.isPlaying) player.pause() else player.play()
                        hudVisible = true
                        true
                    }
                    Key.DirectionLeft -> {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        hudVisible = true
                        true
                    }
                    Key.DirectionRight -> {
                        val max = if (buffered > 0) buffered else duration
                        val target = player.currentPosition + 10_000
                        player.seekTo(if (max > 0) target.coerceAtMost(max) else target)
                        hudVisible = true
                        true
                    }
                    Key.DirectionDown -> {
                        hudVisible = true
                        true
                    }
                    Key.DirectionUp -> {
                        hudVisible = false
                        true
                    }
                    else -> false
                }
            },
    ) {
        PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
        if (hudVisible) {
            Hud(
                positionMs = position,
                durationMs = duration,
                bufferedMs = if (session.method == "direct" && duration > 0) duration else buffered,
                method = session.method,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun Hud(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    method: String,
    modifier: Modifier = Modifier,
) {
    val dur = durationMs.coerceAtLeast(1L)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${formatMs(positionMs)} / ${formatMs(durationMs)}  ·  $method")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (bufferedMs.toFloat() / dur).coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (positionMs.toFloat() / dur).coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text("OK play/pause · Left/Right ±10s · Up hides HUD", style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
