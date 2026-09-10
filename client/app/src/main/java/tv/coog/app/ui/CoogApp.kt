package tv.coog.app.ui

import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import tv.coog.app.data.ApiException
import tv.coog.app.data.CoogApi
import tv.coog.app.data.JobItem
import tv.coog.app.data.MediaItem
import tv.coog.app.data.PlaybackSession
import tv.coog.app.data.PersonSummary
import tv.coog.app.data.SettingsRepository
import tv.coog.app.data.StreamCandidate
import tv.coog.app.update.AppUpdater

private sealed interface Screen {
    data object Browse : Screen
    data class Movie(val item: MediaItem) : Screen
    data class Show(val show: ShowRow) : Screen
    data class Folder(val folder: FolderRow) : Screen
    data class Streams(val item: MediaItem) : Screen
    data class Person(val person: PersonSummary) : Screen
    data class Player(val session: PlaybackSession, val title: String) : Screen
}

@Composable
fun CoogApp() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.applicationContext) }
    val serverUrl by settings.serverUrl.collectAsState(initial = "")
    val token by settings.token.collectAsState(initial = "")
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Browse)) }
    var tab by remember { mutableStateOf(BrowseTab.Home) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var jobs by remember { mutableStateOf<List<JobItem>>(emptyList()) }
    var trendingMovies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var trendingSeries by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playError by remember { mutableStateOf<String?>(null) }
    var queueMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val updater = remember { AppUpdater(context.applicationContext) }
    val updateState by updater.state.collectAsState()
    val current = stack.last()

    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        playError = null
        if (stack.size > 1) {
            stack = stack.dropLast(1)
            return
        }
        if (tab != BrowseTab.Home) {
            tab = BrowseTab.Home
        }
    }

    LaunchedEffect(Unit) {
        updater.check()
    }

    LaunchedEffect(serverUrl, token) {
        if (serverUrl.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        try {
            val api = CoogApi(serverUrl, token)
            api.health()
            items = api.library()
            jobs = runCatching { api.jobs() }.getOrDefault(emptyList())
            val home = runCatching { api.catalogHome() }.getOrNull()
            trendingMovies = home?.trendingMovies.orEmpty()
            trendingSeries = home?.trendingSeries.orEmpty()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            items = emptyList()
            trendingMovies = emptyList()
            trendingSeries = emptyList()
            error = e.message ?: "Could not reach $serverUrl"
        } finally {
            loading = false
        }
        while (true) {
            delay(2000)
            try {
                val api = CoogApi(serverUrl, token)
                val nextJobs = api.jobs()
                if (nextJobs != jobs) {
                    jobs = nextJobs
                }
                if (nextJobs.any { it.status == "finished" }) {
                    items = api.library()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun reportClient(
        type: String,
        message: String,
        mediaId: String = "",
        jobId: String = "",
        sessionId: String = "",
    ) {
        val url = serverUrl
        val tok = token
        scope.launch {
            runCatching {
                CoogApi(url, tok).reportEvent(
                    type = type,
                    message = message,
                    mediaId = mediaId,
                    jobId = jobId,
                    sessionId = sessionId,
                )
            }
        }
    }

    fun playJob(job: JobItem) {
        scope.launch {
            playError = null
            if (!job.ready && job.status != "finished") {
                return@launch
            }
            try {
                val session = CoogApi(serverUrl, token).playbackSession(
                    mediaId = job.mediaId,
                    jobId = job.id,
                )
                if (session.error.isNotBlank() && session.url.isBlank()) {
                    playError = session.error
                    reportClient("session.error", session.error, mediaId = job.mediaId, jobId = job.id)
                    return@launch
                }
                push(Screen.Player(session, job.headline()))
            } catch (e: ApiException) {
                playError = e.message
                reportClient("session.error", e.message ?: "playback failed", mediaId = job.mediaId, jobId = job.id)
            } catch (e: Exception) {
                playError = e.message
                reportClient("play.error", e.message ?: "playback failed", mediaId = job.mediaId, jobId = job.id)
            }
        }
    }

    fun waitForJob(api: CoogApi, jobId: String, mediaId: String = "") {
        scope.launch {
            playError = "Server is buffering this title…"
            repeat(180) {
                delay(1500)
                val job = api.job(jobId)
                if (job.status == "error" || job.status == "cancelled") {
                    val msg = friendlyPlayError(job.error.ifBlank { "Download failed" })
                    playError = msg
                    reportClient("play.error", msg, mediaId = mediaId, jobId = job.id)
                    return@launch
                }
                if (job.ready || job.status == "finished") {
                    playError = null
                    playJob(job)
                    return@launch
                }
            }
        }
    }

    fun playLocal(item: MediaItem) {
        scope.launch {
            playError = null
            try {
                val api = CoogApi(serverUrl, token)
                val session = api.playbackSession(mediaId = item.playableId())
                if (session.error.isNotBlank()) {
                    playError = session.error
                    reportClient("session.error", session.error, mediaId = item.id, jobId = session.jobId, sessionId = session.id)
                    return@launch
                }
                val playerTitle = if (item.kind == "episode") {
                    "${item.seriesName()}  ·  ${item.episodeHeadline()}"
                } else {
                    item.headline()
                }
                push(Screen.Player(session, playerTitle))
            } catch (e: ApiException) {
                playError = e.message
                reportClient("session.error", e.message ?: "playback failed", mediaId = item.id)
            } catch (e: Exception) {
                playError = e.message
                reportClient("play.error", e.message ?: "playback failed", mediaId = item.id)
            }
        }
    }

    fun playOrPick(item: MediaItem) {
        playError = null
        if (item.playBlocked()) {
            playError = "This title is not released yet."
            return
        }
        if (item.isLocal() && (item.path.isNotBlank() || item.libraryId.isNotBlank())) {
            playLocal(item)
            return
        }
        if (item.imdbId.isBlank()) {
            playError = "No IMDB id for this title, so sources cannot be listed."
            return
        }
        push(Screen.Streams(item))
    }

    fun pickStream(item: MediaItem, candidate: StreamCandidate) {
        scope.launch {
            playError = null
            try {
                val api = CoogApi(serverUrl, token)
                val job = api.enqueueDebrid(
                    imdbId = item.imdbId,
                    infoHash = candidate.infoHash,
                    title = item.headline().ifBlank { candidate.title },
                    kind = item.kind.ifBlank { "movie" },
                    season = item.season,
                    episode = item.episode,
                    year = item.year,
                )
                if (job.ready || job.status == "finished") {
                    playJob(job)
                } else {
                    waitForJob(api, job.id, item.id)
                }
            } catch (e: Exception) {
                val msg = friendlyPlayError(e.message ?: "Could not start download")
                playError = msg
                reportClient("play.error", msg, mediaId = item.id)
            }
        }
    }

    suspend fun fetchCatalogShow(show: ShowRow): ShowRow? {
        val api = CoogApi(serverUrl, token)
        var seed = show.header ?: show.cover
        if (seed.imdbId.isBlank()) {
            seed = show.episodes.firstOrNull { it.imdbId.isNotBlank() } ?: seed
        }
        if (seed.imdbId.isBlank() && seed.tmdbId != 0) {
            seed = api.catalogTmdb("series", seed.tmdbId)
        }
        if (seed.imdbId.isBlank()) {
            return if (show.episodes.isNotEmpty()) show else null
        }
        val remote = api.catalogShow(seed.imdbId)
        val local = (show.episodes + items.filter {
            it.kind == "episode" && it.imdbId.equals(seed.imdbId, ignoreCase = true)
        }).distinctBy { "${it.season}:${it.episode}:${it.playableId()}" }
        val episodes = mergeShowEpisodes(remote.episodes, local).ifEmpty { local.ifEmpty { remote.episodes } }
        if (episodes.isEmpty()) return null
        return ShowRow(
            name = remote.item.title.ifBlank { show.name },
            episodes = episodes,
            header = remote.item,
        )
    }

    fun openShow(show: ShowRow) {
        playError = null
        val localName = show.name
        if (show.episodes.isNotEmpty()) {
            push(Screen.Show(show))
        }
        scope.launch {
            try {
                val full = fetchCatalogShow(show) ?: return@launch
                val last = stack.lastOrNull()
                if (last is Screen.Show && last.show.name.equals(localName, ignoreCase = true)) {
                    stack = stack.dropLast(1) + Screen.Show(full)
                } else if (last is Screen.Browse && show.episodes.isEmpty()) {
                    push(Screen.Show(full))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (show.episodes.isEmpty()) {
                    playError = e.message
                    reportClient("play.error", e.message ?: "could not load series", mediaId = show.cover.id)
                    push(Screen.Movie(show.cover))
                }
            }
        }
    }

    fun openTitle(item: MediaItem) {
        if (item.kind == "movie") {
            val localShow = items.showRows().firstOrNull { matchesShowTitle(item.headline(), it.name) }
            if (localShow != null) {
                openShow(localShow)
                return
            }
            push(Screen.Movie(item))
            return
        }
        if (item.kind == "series" || item.kind == "episode") {
            val local = items.showRows().firstOrNull { row ->
                showMatches(row, item)
            }
            openShow(
                ShowRow(
                    name = local?.name ?: item.headline().ifBlank { item.seriesName() },
                    episodes = local?.episodes.orEmpty(),
                    header = when {
                        item.kind == "series" -> item
                        local?.header != null -> local.header
                        else -> item
                    },
                ),
            )
            return
        }
        push(Screen.Movie(item))
    }

    BackHandler(enabled = current !is Screen.Player) { pop() }

    CompositionLocalProvider(LocalCoogServer provides CoogServer(serverUrl, token)) {
        when (val screen = current) {
            Screen.Browse -> AppShell(tab = tab, onTab = { tab = it }) {
                when (tab) {
                    BrowseTab.Settings -> SettingsScreen(
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
                        queueMessage = queueMessage,
                        onQueueDownload = { source ->
                            scope.launch {
                                queueMessage = null
                                try {
                                    CoogApi(serverUrl, token).enqueueJob(source)
                                    queueMessage = null
                                    tab = BrowseTab.Home
                                } catch (e: Exception) {
                                    queueMessage = e.message ?: "Could not queue download"
                                }
                            }
                        },
                        onBack = { pop() },
                    )
                    BrowseTab.Search -> SearchScreen(
                        jobs = jobs,
                        onOpenTitle = { openTitle(it) },
                        onOpenPerson = { push(Screen.Person(it)) },
                    )
                    BrowseTab.Downloads -> DownloadsScreen(
                        jobs = jobs,
                        error = playError,
                        onPlayJob = { playJob(it) },
                        onPauseJob = { job ->
                            scope.launch {
                                runCatching { CoogApi(serverUrl, token).pauseJob(job.id) }
                                    .onSuccess { jobs = CoogApi(serverUrl, token).jobs() }
                                    .onFailure { playError = it.message }
                            }
                        },
                        onResumeJob = { job ->
                            scope.launch {
                                runCatching { CoogApi(serverUrl, token).retryJob(job.id) }
                                    .onSuccess { jobs = CoogApi(serverUrl, token).jobs() }
                                    .onFailure { playError = it.message }
                            }
                        },
                        onCancelJob = { job ->
                            scope.launch {
                                runCatching { CoogApi(serverUrl, token).cancelJob(job.id) }
                                    .onSuccess { jobs = CoogApi(serverUrl, token).jobs() }
                                    .onFailure { playError = it.message }
                            }
                        },
                    )
                    else -> HomeScreen(
                        tab = tab,
                        loading = loading,
                        error = error,
                        items = items,
                        jobs = jobs,
                        trendingMovies = trendingMovies,
                        trendingSeries = trendingSeries,
                        onOpenMovie = { openTitle(it) },
                        onOpenShow = { openShow(it) },
                        onOpenFolder = { push(Screen.Folder(it)) },
                        onPlayJob = { playJob(it) },
                        onPlay = { item ->
                            if (item.kind == "series" || item.kind == "episode") openTitle(item)
                            else playOrPick(item)
                        },
                        onSources = { playOrPick(it.copy(path = "", inLibrary = false, libraryId = "")) },
                        onOpenPerson = { push(Screen.Person(it)) },
                    )
                }
            }
            is Screen.Movie -> MovieDetailsScreen(
                item = screen.item,
                playError = playError,
                onBack = { pop() },
                onPlay = { playOrPick(it) },
                onSources = { playOrPick(it.copy(path = "", inLibrary = false, libraryId = "")) },
                onOpenPerson = { push(Screen.Person(it)) },
                onOpenSimilar = { openTitle(it) },
            )
            is Screen.Show -> ShowDetailsScreen(
                show = screen.show,
                playError = playError,
                onBack = { pop() },
                onPlay = { playOrPick(it) },
                onSources = { playOrPick(it.copy(path = "", inLibrary = false, libraryId = "")) },
                onOpenPerson = { push(Screen.Person(it)) },
                onOpenSimilar = { openTitle(it) },
            )
            is Screen.Folder -> FolderBrowseScreen(
                folder = screen.folder,
                playError = playError,
                onBack = { pop() },
                onOpen = { push(Screen.Movie(it)) },
            )
            is Screen.Streams -> StreamsScreen(
                item = screen.item,
                playError = playError,
                onBack = { pop() },
                onPick = { pickStream(screen.item, it) },
            )
            is Screen.Person -> PersonScreen(
                person = screen.person,
                jobs = jobs,
                onBack = { pop() },
                onOpenTitle = { openTitle(it) },
            )
            is Screen.Player -> PlayerScreen(
                session = screen.session,
                title = screen.title,
                token = token,
                serverUrl = serverUrl,
                onBack = { pop() },
            )
        }
    }
}

private fun showMatches(row: ShowRow, item: MediaItem): Boolean {
    val imdb = item.imdbId.trim()
    if (imdb.isNotBlank()) {
        if (row.cover.imdbId.equals(imdb, ignoreCase = true)) return true
        if (row.episodes.any { it.imdbId.equals(imdb, ignoreCase = true) }) return true
    }
    val name = item.seriesName().ifBlank { item.headline() }
    return name.isNotBlank() && row.name.equals(name, ignoreCase = true)
}
