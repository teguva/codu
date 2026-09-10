package tv.coog.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import tv.coog.app.data.PlaybackSession
import tv.coog.app.player.PlayerViewModel
import tv.coog.app.ui.theme.CoogTextMuted
import tv.coog.app.ui.theme.CoogType

@Composable
fun PlayerScreen(
    session: PlaybackSession,
    title: String,
    token: String,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val player = playerViewModel.player
    var hudVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(session.expectedDurationMs) }
    var playing by remember { mutableStateOf(true) }
    var hudNonce by remember { mutableStateOf(0) }

    fun bumpHud() {
        hudVisible = true
        hudNonce++
    }

    LaunchedEffect(session.url) {
        playerViewModel.play(session.url, token)
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            playing = player.isPlaying
            val playerDuration = player.duration
            duration = when {
                session.expectedDurationMs > 0 -> session.expectedDurationMs
                playerDuration > 0 -> playerDuration
                else -> duration
            }
            delay(250)
        }
    }
    LaunchedEffect(hudNonce, playing) {
        if (playing) {
            delay(4000)
            hudVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                        bumpHud()
                        true
                    }
                    Key.DirectionLeft -> {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        bumpHud()
                        true
                    }
                    Key.DirectionRight -> {
                        val max = if (buffered > 0) buffered else duration
                        val target = player.currentPosition + 10_000
                        player.seekTo(if (max > 0) target.coerceAtMost(max) else target)
                        bumpHud()
                        true
                    }
                    Key.DirectionDown -> {
                        bumpHud()
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
                bufferedMs = if (session.method == "direct" && duration > 0) duration else buffered,
                playing = playing,
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
    playing: Boolean,
) {
    val dur = durationMs.coerceAtLeast(1L)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                ),
            )
            .padding(horizontal = 48.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = CoogType.heroTagline)
        Text(
            "${if (playing) "Playing" else "Paused"}   ${formatClock(positionMs)} / ${formatClock(durationMs)}",
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
    }
}
