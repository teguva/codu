package tv.coog.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import tv.coog.app.data.ApiException
import tv.coog.app.data.CoogApi
import tv.coog.app.data.MediaItem
import tv.coog.app.data.PlaybackSession
import tv.coog.app.data.SettingsRepository
import tv.coog.app.update.AppUpdater

private sealed interface Screen {
    data object Browse : Screen
    data class Movie(val item: MediaItem, val fromFolder: FolderRow? = null) : Screen
    data class Show(val show: ShowRow) : Screen
    data class Folder(val folder: FolderRow) : Screen
    data class Player(val session: PlaybackSession, val title: String) : Screen
}

@Composable
fun CoogApp() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.applicationContext) }
    val serverUrl by settings.serverUrl.collectAsState(initial = "http://10.0.2.2:8090")
    val token by settings.token.collectAsState(initial = "")
    var screen by remember { mutableStateOf<Screen>(Screen.Browse) }
    var tab by remember { mutableStateOf(BrowseTab.Home) }
    var returnTo by remember { mutableStateOf<Screen>(Screen.Browse) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val updater = remember { AppUpdater(context.applicationContext) }
    val updateState by updater.state.collectAsState()

    LaunchedEffect(Unit) {
        updater.check()
    }

    LaunchedEffect(serverUrl, token) {
        loading = true
        error = null
        try {
            val api = CoogApi(serverUrl, token)
            api.health()
            items = api.library()
            error = null
        } catch (e: Exception) {
            items = emptyList()
            error = e.message ?: "Could not reach $serverUrl"
        } finally {
            loading = false
        }
    }

    fun playItem(item: MediaItem, stay: Screen) {
        scope.launch {
            playError = null
            try {
                val session = CoogApi(serverUrl, token).playbackSession(item.id)
                if (session.error.isNotBlank()) {
                    playError = session.error
                    screen = stay
                } else {
                    returnTo = stay
                    val playerTitle = if (item.kind == "episode") {
                        "${item.seriesName()}  ·  ${item.episodeHeadline()}"
                    } else {
                        item.headline()
                    }
                    screen = Screen.Player(session, playerTitle)
                }
            } catch (e: ApiException) {
                playError = e.message
                screen = stay
            } catch (e: Exception) {
                playError = e.message
                screen = stay
            }
        }
    }

    CompositionLocalProvider(LocalCoogServer provides CoogServer(serverUrl, token)) {
        when (val current = screen) {
            Screen.Browse -> AppShell(tab = tab, onTab = { tab = it }) {
                if (tab == BrowseTab.Settings) {
                    SettingsScreen(
                        serverUrl = serverUrl,
                        token = token,
                        update = updateState,
                        onSave = { url, tok ->
                            scope.launch {
                                settings.setServerUrl(url)
                                settings.setToken(tok)
                                tab = BrowseTab.Home
                            }
                        },
                        onCheckUpdate = { scope.launch { updater.check() } },
                        onInstallUpdate = { scope.launch { updater.installLatest() } },
                        onBack = { tab = BrowseTab.Home },
                    )
                } else {
                    HomeScreen(
                        tab = tab,
                        loading = loading,
                        error = error,
                        items = items,
                        onOpenMovie = { screen = Screen.Movie(it) },
                        onOpenShow = { screen = Screen.Show(it) },
                        onOpenFolder = { screen = Screen.Folder(it) },
                    )
                }
            }
            is Screen.Movie -> MovieDetailsScreen(
                item = current.item,
                playError = playError,
                onBack = {
                    playError = null
                    screen = current.fromFolder?.let { Screen.Folder(it) } ?: Screen.Browse
                },
                onPlay = { playItem(current.item, current) },
            )
            is Screen.Show -> ShowDetailsScreen(
                show = current.show,
                playError = playError,
                onBack = {
                    playError = null
                    screen = Screen.Browse
                },
                onPlay = { playItem(it, current) },
            )
            is Screen.Folder -> FolderBrowseScreen(
                folder = current.folder,
                playError = playError,
                onBack = {
                    playError = null
                    screen = Screen.Browse
                },
                onOpen = { screen = Screen.Movie(it, current.folder) },
            )
            is Screen.Player -> PlayerScreen(
                session = current.session,
                title = current.title,
                token = token,
                onBack = { screen = returnTo },
            )
        }
    }
}
