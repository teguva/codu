package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogType
import tv.coog.app.update.UpdateUiState

@Composable
fun SettingsScreen(
    serverUrl: String,
    token: String,
    update: UpdateUiState,
    onSave: (String, String) -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onQueueDownload: (String) -> Unit,
    queueMessage: String?,
    onBack: () -> Unit,
) {
    var url by remember(serverUrl) { mutableStateOf(serverUrl) }
    var tok by remember(token) { mutableStateOf(token) }
    var sourceUrl by remember { mutableStateOf("") }
    val firstFocus = LocalBrowseContentFocus.current ?: remember { FocusRequester() }
    val railFocus = LocalRailFocus.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep)
            .verticalScroll(rememberScrollState())
            .padding(start = 40.dp, top = topBarHeight() + 6.dp, end = 56.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = CoogType.screenTitle)
        Text(
            "Tell the TV where Coog is running. Emulator: http://10.0.2.2:8090. A real Google TV needs the server LAN IP.",
            style = CoogType.heroPlot,
            modifier = Modifier.widthIn(max = 900.dp),
        )
        FieldLabel("Server URL")
        TvTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = "http://192.168.1.10:8090",
            uri = true,
            modifier = Modifier
                .focusRequester(firstFocus)
                .then(if (railFocus != null) Modifier.focusProperties { up = railFocus } else Modifier),
        )
        FieldLabel("Bearer token")
        TvTextField(value = tok, onValueChange = { tok = it }, placeholder = "Optional unless the server requires one")
        WhitePill(label = "Save", onClick = { onSave(url, tok) })

        Text("Download", style = MaterialTheme.typography.titleMedium)
        Text(
            "Paste a YouTube or other yt-dlp URL. It shows up on Home under Downloading and can play before the file finishes.",
            style = CoogType.heroPlot,
            modifier = Modifier.widthIn(max = 900.dp),
        )
        FieldLabel("Source URL")
        TvTextField(value = sourceUrl, onValueChange = { sourceUrl = it }, placeholder = "https://…", uri = true)
        queueMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(message, style = CoogType.heroPlot, modifier = Modifier.widthIn(max = 900.dp))
        }
        WhitePill(label = "Queue download", onClick = {
            if (sourceUrl.isNotBlank()) onQueueDownload(sourceUrl)
        })

        Text("App updates", style = MaterialTheme.typography.titleMedium)
        Text(
            "Installed Coog ${update.currentVersion} (${update.currentCode}). New APKs come from GitHub Releases.",
            style = CoogType.heroPlot,
            modifier = Modifier.widthIn(max = 900.dp),
        )
        val status = update.error ?: update.message
        if (status.isNotBlank()) {
            Text(status, style = CoogType.heroPlot, modifier = Modifier.widthIn(max = 900.dp))
        }
        if (update.available != null) {
            WhitePill(
                label = if (update.installing) {
                    val pct = update.progress?.let { "${(it * 100).toInt()}%" }
                    if (pct != null) "Updating $pct" else "Updating…"
                } else {
                    "Update to ${update.available.versionName}"
                },
                onClick = { if (!update.installing) onInstallUpdate() },
            )
        }
        GhostButton(
            label = if (update.checking) "Checking…" else "Check for updates",
            onClick = { if (!update.checking && !update.installing) onCheckUpdate() },
        )
        GhostButton(label = "Back", onClick = onBack)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
