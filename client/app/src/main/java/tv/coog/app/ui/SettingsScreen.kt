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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogTextMuted
import tv.coog.app.ui.theme.CoogType
import tv.coog.app.update.UpdateUiState

@Composable
fun SettingsScreen(
    serverUrl: String,
    token: String,
    update: UpdateUiState,
    health: String? = null,
    onSave: (String, String) -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onQueueDownload: (String) -> Unit,
    queueMessage: String?,
) {
    var url by remember(serverUrl) { mutableStateOf(serverUrl) }
    var tok by remember(token) { mutableStateOf(token) }
    var sourceUrl by remember { mutableStateOf("") }
    val firstFocus = LocalBrowseContentFocus.current ?: remember { FocusRequester() }
    val inset = catalogInset()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep)
            .verticalScroll(rememberScrollState())
            .padding(start = inset, top = topBarHeight() + 6.dp, end = inset, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("Settings", style = CoogType.screenTitle)

        if (!health.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Server status", style = CoogType.shelfTitle)
                Text(health, style = CoogType.heroPlot, color = CoogTextMuted, modifier = Modifier.widthIn(max = 900.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connection", style = CoogType.shelfTitle)
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
                exitUp = true,
                modifier = Modifier.focusRequester(firstFocus),
            )
            FieldLabel("Bearer token")
            TvTextField(
                value = tok,
                onValueChange = { tok = it },
                placeholder = "Required if the server sets COOG_AUTH_TOKEN",
                password = true,
            )
            WhitePill(label = "Save", onClick = { onSave(url, tok) })
            val connected = serverUrl.isNotBlank()
            Text(
                if (connected) "Saved · $serverUrl" else "Not connected yet",
                style = CoogType.heroPlot,
                color = CoogTextMuted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add download", style = CoogType.shelfTitle)
            Text(
                "Paste a YouTube or other yt-dlp URL. It appears in Downloads and can play before the file finishes.",
                style = CoogType.heroPlot,
                modifier = Modifier.widthIn(max = 900.dp),
            )
            FieldLabel("Source URL")
            TvTextField(value = sourceUrl, onValueChange = { sourceUrl = it }, placeholder = "https://…", uri = true)
            queueMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(message, style = CoogType.heroPlot, modifier = Modifier.widthIn(max = 900.dp))
            }
            WhitePill(label = "Queue", onClick = {
                if (sourceUrl.isNotBlank()) onQueueDownload(sourceUrl)
            })
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("App", style = CoogType.shelfTitle)
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
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = CoogType.chip, color = CoogTextMuted)
}
