package tv.coog.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.compose.PlayerSurface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.coog.app.data.CoogApi
import tv.coog.app.data.PlaybackSession
import tv.coog.app.player.PlayerTrack
import tv.coog.app.player.PlayerViewModel
import tv.coog.app.ui.theme.CoogTextMuted
import tv.coog.app.ui.theme.CoogType

private enum class PlayerMenu { None, Audio, Subtitles }

@Composable
fun PlayerScreen(
    session: PlaybackSession,
    title: String,
    token: String,
    serverUrl: String,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val player = playerViewModel.player
    val playError by playerViewModel.error.collectAsState()
    val audioTracks by playerViewModel.audioTracks.collectAsState()
    val textTracks by playerViewModel.textTracks.collectAsState()
    val textOff by playerViewModel.textOff.collectAsState()
    var hudVisible by remember { mutableStateOf(true) }
    var hudExpanded by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(PlayerMenu.None) }
    var settingsFocused by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(session.expectedDurationMs) }
    var playing by remember { mutableStateOf(true) }
    var hudNonce by remember { mutableStateOf(0) }
    var jobBuffered by remember { mutableLongStateOf(session.bufferedMs) }
    var jobExpected by remember { mutableLongStateOf(session.expectedDurationMs) }
    var jobDownloading by remember { mutableStateOf(session.method == "progressive") }
    val rootFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }

    fun seekLimit(): Long {
        val caps = listOf(jobBuffered, buffered, duration).filter { it > 0 }
        return caps.minOrNull() ?: 0L
    }

    fun showHud(expanded: Boolean = hudExpanded) {
        hudVisible = true
        hudExpanded = expanded
        hudNonce++
    }

    fun hideHud() {
        menu = PlayerMenu.None
        hudExpanded = false
        hudVisible = false
        runCatching { rootFocus.requestFocus() }
    }

    fun togglePlay() {
        playerViewModel.togglePlay()
        showHud()
    }

    fun seekBy(deltaMs: Long) {
        playerViewModel.seekBy(deltaMs, seekLimit())
        showHud(expanded = false)
        menu = PlayerMenu.None
    }

    BackHandler {
        when {
            menu != PlayerMenu.None -> menu = PlayerMenu.None
            hudExpanded -> hideHud()
            else -> {
                player.pause()
                onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { rootFocus.requestFocus() }
    }
    LaunchedEffect(hudExpanded) {
        if (hudExpanded) {
            delay(60)
            runCatching { settingsFocus.requestFocus() }
        }
    }
    LaunchedEffect(session.url) {
        playerViewModel.play(session.url, token)
    }
    LaunchedEffect(playError, serverUrl, token, session.id) {
        val message = playError ?: return@LaunchedEffect
        runCatching {
            CoogApi(serverUrl, token).reportEvent(
                type = "player.error",
                message = listOfNotNull(
                    message,
                    session.method.takeIf { it.isNotBlank() }?.let { "method $it" },
                ).joinToString(" · "),
                mediaId = session.mediaId,
                jobId = session.jobId,
                sessionId = session.id,
            )
        }
    }
    LaunchedEffect(session.jobId, serverUrl, token) {
        if (session.jobId.isBlank()) {
            playerViewModel.suppressEnded = false
            return@LaunchedEffect
        }
        val api = CoogApi(serverUrl, token)
        while (true) {
            try {
                val job = api.job(session.jobId)
                jobBuffered = job.bufferedMs
                if (job.expectedDurationMs > 0) {
                    jobExpected = job.expectedDurationMs
                    duration = job.expectedDurationMs
                }
                jobDownloading = job.status == "downloading" || job.status == "ready" || job.status == "queued"
                playerViewModel.suppressEnded = jobDownloading
                if (job.status == "finished" || job.status == "error") {
                    playerViewModel.suppressEnded = false
                    break
                }
            } catch (_: Exception) {
            }
            delay(1500)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            playing = player.isPlaying
            duration = playbackDurationMs(
                exoDuration = player.duration,
                expectedMs = maxOf(session.expectedDurationMs, jobExpected),
                bufferedMs = maxOf(jobBuffered, buffered),
                streaming = jobDownloading || session.method == "progressive",
            )
            delay(250)
        }
    }
    LaunchedEffect(hudNonce, playing, hudExpanded, menu) {
        if (!playing || hudExpanded || menu != PlayerMenu.None) return@LaunchedEffect
        delay(4000)
        hudVisible = false
    }

    val playbackKeys = hudExpanded && settingsFocused && menu == PlayerMenu.None

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val playPause = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter ||
                    event.key == Key.MediaPlayPause ||
                    event.key == Key.MediaPlay ||
                    event.key == Key.MediaPause
                val left = event.key == Key.DirectionLeft || event.key == Key.MediaRewind
                val right = event.key == Key.DirectionRight || event.key == Key.MediaFastForward
                val down = event.key == Key.DirectionDown
                val up = event.key == Key.DirectionUp
                val handled = playPause || left || right || down || up
                if (!handled) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent true
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    down -> {
                        menu = PlayerMenu.None
                        showHud(expanded = true)
                        true
                    }
                    up -> {
                        if (menu != PlayerMenu.None) {
                            menu = PlayerMenu.None
                        } else {
                            hideHud()
                        }
                        true
                    }
                    playbackKeys && (left || right || playPause) -> false
                    playPause -> {
                        togglePlay()
                        true
                    }
                    left -> {
                        seekBy(-10_000)
                        true
                    }
                    right -> {
                        seekBy(10_000)
                        true
                    }
                    else -> false
                }
            },
    ) {
        PlayerSurface(
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false },
        )
        playError?.let { message ->
            Text(
                message,
                style = CoogType.heroTagline,
                color = Color(0xFFFF8B8B),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
            )
        }
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Hud(
                title = title,
                positionMs = position,
                durationMs = duration,
                bufferedMs = when {
                    session.method == "direct" && duration > 0 && !jobDownloading -> duration
                    else -> maxOf(jobBuffered, buffered)
                },
                remainingMs = if (jobDownloading && duration > 0) (duration - maxOf(jobBuffered, buffered)).coerceAtLeast(0L) else 0L,
                playing = playing,
                expanded = hudExpanded,
                menu = menu,
                audioTracks = audioTracks,
                textTracks = textTracks,
                textOff = textOff,
                settingsFocus = settingsFocus,
                onSettingsFocus = { settingsFocused = it },
                onTogglePlay = { togglePlay() },
                onOpenAudio = {
                    menu = if (menu == PlayerMenu.Audio) PlayerMenu.None else PlayerMenu.Audio
                    showHud(expanded = true)
                },
                onOpenSubtitles = {
                    menu = if (menu == PlayerMenu.Subtitles) PlayerMenu.None else PlayerMenu.Subtitles
                    showHud(expanded = true)
                },
                onSelectAudio = {
                    playerViewModel.selectTrack(it)
                    menu = PlayerMenu.None
                    showHud(expanded = true)
                },
                onSelectSubtitle = {
                    playerViewModel.selectTrack(it)
                    menu = PlayerMenu.None
                    showHud(expanded = true)
                },
                onSubtitlesOff = {
                    playerViewModel.setTextOff()
                    menu = PlayerMenu.None
                    showHud(expanded = true)
                },
            )
        }
    }
}

@Composable
private fun Hud(
    title: String,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    remainingMs: Long,
    playing: Boolean,
    expanded: Boolean,
    menu: PlayerMenu,
    audioTracks: List<PlayerTrack>,
    textTracks: List<PlayerTrack>,
    textOff: Boolean,
    settingsFocus: FocusRequester,
    onSettingsFocus: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onSelectAudio: (PlayerTrack) -> Unit,
    onSelectSubtitle: (PlayerTrack) -> Unit,
    onSubtitlesOff: () -> Unit,
) {
    val dur = durationMs.coerceAtLeast(1L)
    val audioLabel = audioTracks.firstOrNull { it.selected }?.label ?: "Audio"
    val subLabel = when {
        textOff || textTracks.isEmpty() -> "Off"
        else -> textTracks.firstOrNull { it.selected }?.label ?: "Subtitles"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f)),
                ),
            )
            .padding(horizontal = 48.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = CoogType.heroTagline)
        Text(
            buildString {
                append(if (playing) "Playing" else "Paused")
                append("   ")
                append(formatClock(positionMs))
                append(" / ")
                append(if (durationMs > 0) formatClock(durationMs) else "—")
                if (remainingMs > 0 && remainingMs < durationMs) {
                    append("   ·   ")
                    append(formatClock(bufferedMs))
                    append(" ready")
                    formatDuration(remainingMs)?.let {
                        append("   ·   ")
                        append(it)
                        append(" left")
                    }
                }
            },
            style = CoogType.cardYear,
            color = CoogTextMuted,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(99.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (bufferedMs.toFloat() / dur).coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.32f), RoundedCornerShape(99.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (positionMs.toFloat() / dur).coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(Color.White, RoundedCornerShape(99.dp)),
            )
        }
        if (expanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .onFocusChanged { onSettingsFocus(it.hasFocus) },
            ) {
                WhitePill(
                    label = if (playing) "Pause" else "Play",
                    onClick = onTogglePlay,
                    modifier = Modifier.focusRequester(settingsFocus),
                )
                GhostButton(
                    label = "Audio · $audioLabel",
                    onClick = onOpenAudio,
                )
                GhostButton(
                    label = "Subtitles · $subLabel",
                    onClick = onOpenSubtitles,
                )
            }
            if (menu == PlayerMenu.Audio) {
                TrackMenu(
                    title = "Audio",
                    items = audioTracks,
                    onPick = onSelectAudio,
                )
            }
            if (menu == PlayerMenu.Subtitles) {
                TrackMenu(
                    title = "Subtitles",
                    items = textTracks,
                    extraOff = true,
                    offSelected = textOff,
                    onPick = onSelectSubtitle,
                    onOff = onSubtitlesOff,
                )
            }
        }
    }
}

@Composable
private fun TrackMenu(
    title: String,
    items: List<PlayerTrack>,
    extraOff: Boolean = false,
    offSelected: Boolean = false,
    onPick: (PlayerTrack) -> Unit,
    onOff: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .widthIn(max = 560.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = CoogType.shelfTitle)
        if (items.isEmpty() && !extraOff) {
            Text("No tracks in this file.", style = CoogType.cardYear, color = CoogTextMuted)
        }
        if (extraOff) {
            if (offSelected) {
                WhitePill(label = "Off", onClick = { onOff?.invoke() })
            } else {
                GhostButton(label = "Off", onClick = { onOff?.invoke() })
            }
        }
        items.forEach { track ->
            if (track.selected && !offSelected) {
                WhitePill(label = track.label, onClick = { onPick(track) })
            } else {
                GhostButton(label = track.label, onClick = { onPick(track) })
            }
        }
    }
}
