package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.coog.app.data.JobItem
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogDanger
import tv.coog.app.ui.theme.CoogTextMuted
import tv.coog.app.ui.theme.CoogType

@Composable
fun DownloadsScreen(
    jobs: List<JobItem>,
    onPlayJob: (JobItem) -> Unit,
    onPauseJob: (JobItem) -> Unit,
    onResumeJob: (JobItem) -> Unit,
    onCancelJob: (JobItem) -> Unit,
    error: String? = null,
) {
    val firstFocus = LocalBrowseContentFocus.current ?: remember { FocusRequester() }
    val railFocus = LocalRailFocus.current
    val rows = remember(jobs) {
        jobs.filter { !it.isFinished() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep)
            .padding(start = 40.dp, end = 36.dp, top = topBarHeight() + 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Downloads", style = CoogType.screenTitle)
        Text(
            if (rows.isEmpty()) "Nothing in the queue. Pick a source on a title to start one."
            else "${rows.count { it.isActive() }} in progress",
            style = CoogType.heroPlot,
            color = CoogTextMuted,
        )
        if (error != null) {
            Text(friendlyPlayError(error), color = CoogDanger)
        }
        if (rows.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(rows, key = { _, job -> job.id }) { index, job ->
                    DownloadRow(
                        job = job,
                        onPlay = { onPlayJob(job) },
                        onPause = { onPauseJob(job) },
                        onResume = { onResumeJob(job) },
                        onCancel = { onCancelJob(job) },
                        firstFocus = if (index == 0) firstFocus else null,
                        railFocus = if (index == 0) railFocus else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    job: JobItem,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    firstFocus: FocusRequester? = null,
    railFocus: FocusRequester? = null,
) {
    val progress = job.progress.toFloat().coerceIn(0f, 1f)
    val actions = buildList {
        if (job.ready || job.status == "ready") add(DownloadAction("Play", true, onPlay))
        if (job.canPause()) add(DownloadAction("Pause", false, onPause))
        if (job.canResume()) add(DownloadAction("Start", true, onResume))
        if (job.canCancel()) add(DownloadAction("Cancel", false, onCancel))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.headline(), style = CoogType.cardTitle, maxLines = 1)
            Text(job.subtitle(), style = CoogType.cardYear, maxLines = 1, color = CoogTextMuted)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.16f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(5.dp)
                    .background(
                        if (job.status == "error") CoogDanger else Color.White,
                        RoundedCornerShape(99.dp),
                    ),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, action ->
                val mod = Modifier
                    .then(if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                    .then(if (index == 0 && railFocus != null) Modifier.focusProperties { up = railFocus } else Modifier)
                if (action.primary) {
                    WhitePill(label = action.label, onClick = action.onClick, modifier = mod)
                } else {
                    GhostButton(label = action.label, onClick = action.onClick, modifier = mod)
                }
            }
        }
    }
}

private data class DownloadAction(val label: String, val primary: Boolean, val onClick: () -> Unit)
