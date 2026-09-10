package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
    onBack: () -> Unit,
) {
    var url by remember(serverUrl) { mutableStateOf(serverUrl) }
    var tok by remember(token) { mutableStateOf(token) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep)
            .verticalScroll(rememberScrollState())
            .padding(start = RailWidth + 28.dp, top = 48.dp, end = 56.dp, bottom = 56.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onBack()
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Settings", style = CoogType.heroTitle)
        Text(
            "Tell the TV where Coog is running. Emulator: http://10.0.2.2:8090. A real Google TV needs the server LAN IP.",
            style = CoogType.heroPlot,
            modifier = Modifier.widthIn(max = 900.dp),
        )
        FieldLabel("Server URL")
        SettingField(value = url, onValueChange = { url = it })
        FieldLabel("Bearer token")
        SettingField(value = tok, onValueChange = { tok = it }, placeholder = "Optional unless the server requires one")
        WhitePill(label = "Save", onClick = { onSave(url, tok) })

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

@Composable
private fun SettingField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
) {
    Surface(onClick = {}, modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotBlank()) {
                    Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            },
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp).fillMaxWidth(),
        )
    }
}
