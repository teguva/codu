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
import tv.coog.app.data.ServerStats
import tv.coog.app.data.SettingsRepository
import tv.coog.app.data.StreamingSettings
import tv.coog.app.data.StreamCandidate
import tv.coog.app.update.AppUpdater

private sealed interface Screen {
    data object Browse : Screen
    data class Movie(val item: MediaItem) : Screen
    data class Show(val show: ShowRow) : Screen
    data class Folder(val folder: FolderRow) : Screen
    data class Streams(val item: MediaItem) : Screen
    data class Person(val person: PersonSummary) : Screen
    data class Player(
        val session: PlaybackSession?,
        val title: String,
        val item: MediaItem? = null,
        val nextItem: MediaItem? = null,
        val previousItem: MediaItem? = null,
    ) : Screen
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
    var continueWatching by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var forYou by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var tasteColdStart by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(StreamingSettings()) }
    var serverStats by remember { mutableStateOf<ServerStats?>(null) }
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

    fun replaceTop(screen: Screen) {
        stack = if (stack.isEmpty()) listOf(screen) else stack.dropLast(1) + screen
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
        while (true) {
            delay(12_000)
            serverStats = runCatching { CoogApi(serverUrl, token).serverStats() }.getOrNull()
            streaming = runCatching { CoogApi(serverUrl, token).streamingSettings() }.getOrDefault(streaming)
        }
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
            forYou = home?.forYou.orEmpty()
            tasteColdStart = home?.coldStart == true
            continueWatching = runCatching { api.catalogContinue() }.getOrDefault(emptyList())
            streaming = runCatching { api.streamingSettings() }.getOrDefault(StreamingSettings())
            serverStats = runCatching { api.serverStats() }.getOrNull()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            items = emptyList()
            trendingMovies = emptyList()
            trendingSeries = emptyList()
            forYou = emptyList()
            continueWatching = emptyList()
            serverStats = null
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
                    val removed = jobs.any { old -> nextJobs.none { it.id == old.id } }
                    jobs = nextJobs
                    if (removed) {
                        items = api.library()
                    }
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

    fun episodeNeighbors(item: MediaItem): Pair<MediaItem?, MediaItem?> {
        if (item.kind != "episode") return null to null
        val fromShow = stack.filterIsInstance<Screen.Show>().lastOrNull()?.show?.episodes.orEmpty()
        val eps = fromShow.ifEmpty {
            items.filter {
                it.kind == "episode" && (
                    (item.imdbId.isNotBlank() && it.imdbId.equals(item.imdbId, true)) ||
                        (item.showTitle.isNotBlank() && it.showTitle.equals(item.showTitle, true))
                    )
            }
        }.sortedWith(compareBy({ it.season }, { it.episode }))
        if (eps.isEmpty()) return null to null
        val idx = eps.indexOfFirst {
            it.id == item.id ||
                (it.season == item.season && it.episode == item.episode && it.season > 0)
        }
        if (idx < 0) return null to null
        val prev = eps.getOrNull(idx - 1)
        val next = eps.getOrNull(idx + 1)
        return prev to next
    }

    fun playerScreen(session: PlaybackSession?, title: String, item: MediaItem?): Screen.Player {
        val (prev, next) = item?.let { episodeNeighbors(it) } ?: (null to null)
        return Screen.Player(session, title, item, nextItem = next, previousItem = prev)
    }

    fun playJob(job: JobItem, art: MediaItem? = null) {
        val item = art ?: items.firstOrNull { it.playableId() == job.mediaId || it.id == job.mediaId }
            ?: job.asArtItem()
        val title = job.headline()
        if (stack.lastOrNull() !is Screen.Player) {
            push(playerScreen(null, title, item))
        }
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
                    if (stack.lastOrNull() is Screen.Player) pop()
                    return@launch
                }
                replaceTop(playerScreen(session, title, item))
            } catch (e: ApiException) {
                playError = e.message
                reportClient("session.error", e.message ?: "playback failed", mediaId = job.mediaId, jobId = job.id)
                if (stack.lastOrNull() is Screen.Player) pop()
            } catch (e: Exception) {
                playError = e.message
                reportClient("play.error", e.message ?: "playback failed", mediaId = job.mediaId, jobId = job.id)
                if (stack.lastOrNull() is Screen.Player) pop()
            }
        }
    }

    fun playLocal(item: MediaItem) {
        val playerTitle = if (item.kind == "episode") {
            "${item.seriesName()}  ·  ${item.episodeHeadline()}"
        } else {
            item.headline()
        }
        push(playerScreen(null, playerTitle, item))
        scope.launch {
            playError = null
            try {
                val api = CoogApi(serverUrl, token)
                val session = api.playbackSession(
                    mediaId = item.diskMediaId(),
                    imdbId = item.imdbId,
                    kind = item.kind,
                    title = item.headline().ifBlank { item.seriesName() },
                    year = item.year,
                    season = item.season,
                    episode = item.episode,
                )
                if (session.error.isNotBlank()) {
                    playError = session.error
                    reportClient("session.error", session.error, mediaId = item.id, jobId = session.jobId, sessionId = session.id)
                    if (stack.lastOrNull() is Screen.Player) pop()
                    return@launch
                }
                replaceTop(playerScreen(session, playerTitle, item))
            } catch (e: ApiException) {
                playError = e.message
                reportClient("session.error", e.message ?: "playback failed", mediaId = item.id)
                if (stack.lastOrNull() is Screen.Player) pop()
            } catch (e: Exception) {
                playError = e.message
                reportClient("play.error", e.message ?: "playback failed", mediaId = item.id)
                if (stack.lastOrNull() is Screen.Player) pop()
            }
        }
    }

    fun waitForJob(api: CoogApi, jobId: String, item: MediaItem) {
        scope.launch {
            playError = null
            if (stack.lastOrNull() !is Screen.Player) {
                push(playerScreen(null, item.headline(), item))
            }
            repeat(180) {
                delay(1500)
                val job = runCatching { api.job(jobId) }.getOrNull()
                if (job == null) {
                    // Cancel deletes the job; finish may too after library ingest.
                    val library = runCatching { api.library() }.getOrDefault(items)
                    items = library
                    val local = library.firstOrNull {
                        it.imdbId.isNotBlank() && it.imdbId.equals(item.imdbId, true) &&
                            (item.season <= 0 || it.season == item.season) &&
                            (item.episode <= 0 || it.episode == item.episode)
                    }
                    if (local != null) {
                        playLocal(local)
                    } else {
                        playError = "Download was cancelled"
                        if (stack.lastOrNull() is Screen.Player) pop()
                    }
                    return@launch
                }
                if (job.status == "error" || job.status == "cancelled") {
                    val msg = friendlyPlayError(job.error.ifBlank { "Download failed" })
                    playError = msg
                    reportClient("play.error", msg, mediaId = item.id, jobId = job.id)
                    if (stack.lastOrNull() is Screen.Player) pop()
                    return@launch
                }
                if (job.ready || job.status == "finished") {
                    playError = null
                    playJob(job, item)
                    return@launch
                }
            }
        }
    }


    suspend fun startStreamJob(item: MediaItem, candidate: StreamCandidate) {
        playError = null
        try {
            val api = CoogApi(serverUrl, token)
            val title = item.headline().ifBlank { candidate.title }
            val taggedTitle = if (item.season > 0 && item.episode > 0) {
                "%s S%02dE%02d".format(title, item.season, item.episode)
            } else {
                title
            }
            val job = when {
                candidate.kind.equals("web", ignoreCase = true) ||
                    candidate.source.equals("web", ignoreCase = true) -> api.enqueueWeb(
                    url = candidate.url,
                    title = taggedTitle.ifBlank { candidate.name },
                    imdbId = item.imdbId,
                    kind = item.kind.ifBlank { "movie" },
                    season = item.season,
                    episode = item.episode,
                    year = item.year,
                )
                candidate.cached -> api.enqueueDebrid(
                    imdbId = item.imdbId,
                    infoHash = candidate.infoHash,
                    title = title,
                    kind = item.kind.ifBlank { "movie" },
                    season = item.season,
                    episode = item.episode,
                    year = item.year,
                )
                else -> api.enqueueTorrent(
                    imdbId = item.imdbId,
                    infoHash = candidate.infoHash,
                    title = title,
                    kind = item.kind.ifBlank { "movie" },
                    season = item.season,
                    episode = item.episode,
                    year = item.year,
                )
            }
            if (job.ready || job.status == "finished") {
                playJob(job, item)
            } else {
                waitForJob(api, job.id, item)
            }
        } catch (e: Exception) {
            val msg = friendlyPlayError(e.message ?: "Could not start download")
            playError = msg
            reportClient("play.error", msg, mediaId = item.id)
            if (stack.lastOrNull() is Screen.Player) pop()
        }
    }

    fun pickStream(item: MediaItem, candidate: StreamCandidate) {
        if (stack.lastOrNull() !is Screen.Player) {
            push(playerScreen(null, item.headline(), item))
        }
        scope.launch { startStreamJob(item, candidate) }
    }

    fun playOrPick(item: MediaItem, preferSources: Boolean = false) {
        playError = null
        if (item.playBlocked()) {
            playError = "This title is not released yet."
            return
        }
        if (item.diskMediaId().isNotBlank()) {
            playLocal(item)
            return
        }
        if (item.imdbId.isBlank()) {
            playError = "No IMDB id for this title, so sources cannot be listed."
            return
        }
        if (preferSources) {
            push(Screen.Streams(item))
            return
        }
        // Smart Play: try best cached RD (or strong torrent) before opening Sources.
        push(playerScreen(null, item.headline(), item))
        scope.launch {
            try {
                val api = CoogApi(serverUrl, token)
                val streams = api.catalogStreams(
                    imdbId = item.imdbId,
                    kind = item.kind.ifBlank { "movie" },
                    season = item.season,
                    episode = item.episode,
                )
                val best = pickSmartStream(streams)
                if (best == null) {
                    if (stack.lastOrNull() is Screen.Player) pop()
                    push(Screen.Streams(item))
                    return@launch
                }
                startStreamJob(item, best)
            } catch (e: Exception) {
                if (stack.lastOrNull() is Screen.Player) pop()
                push(Screen.Streams(item))
            }
        }
    }

    fun prefetchItem(item: MediaItem) {
        if (item.imdbId.isBlank()) return
        scope.launch {
            runCatching {
                CoogApi(serverUrl, token).prefetchNextEpisode(
                    imdbId = item.imdbId,
                    season = item.season,
                    episode = item.episode,
                    title = item.seriesName().ifBlank { item.headline() },
                    year = item.year,
                )
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
        }).filter { it.diskMediaId().isNotBlank() || it.path.isNotBlank() }
            .distinctBy { "${it.season}:${it.episode}:${it.playableId()}" }
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

    val extraFolders = remember(items) { items.folderRows() }
    LaunchedEffect(extraFolders.isNotEmpty(), tab) {
        if (tab == BrowseTab.Folders && extraFolders.isEmpty()) {
            tab = BrowseTab.Home
        }
    }
    LaunchedEffect(current, serverUrl, token) {
        if (current !is Screen.Browse || serverUrl.isBlank()) return@LaunchedEffect
        continueWatching = runCatching { CoogApi(serverUrl, token).catalogContinue() }.getOrDefault(continueWatching)
    }

    BackHandler(enabled = current !is Screen.Player) { pop() }

    CompositionLocalProvider(LocalCoogServer provides CoogServer(serverUrl, token)) {
        when (val screen = current) {
            Screen.Browse -> AppShell(
                tab = tab,
                onTab = { tab = it },
                showFolders = extraFolders.isNotEmpty(),
            ) {
                when (tab) {
                    BrowseTab.Settings -> SettingsScreen(
                        serverUrl = serverUrl,
                        token = token,
                        update = updateState,
                        health = serverHealthLine(serverStats),
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
                    )
                    BrowseTab.Search -> SearchScreen(
                        jobs = jobs,
                        library = items,
                        onOpenTitle = { openTitle(it) },
                        onOpenPerson = { push(Screen.Person(it)) },
                    )
                    BrowseTab.Downloads -> DownloadsScreen(
                        jobs = jobs,
                        library = items,
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
                    BrowseTab.Movies -> CatalogBrowseScreen(
                        kind = "movie",
                        jobs = jobs,
                        library = items,
                        onOpen = { openTitle(it) },
                    )
                    BrowseTab.Series -> CatalogBrowseScreen(
                        kind = "series",
                        jobs = jobs,
                        library = items,
                        onOpen = { openTitle(it) },
                    )
                    else -> HomeScreen(
                        tab = tab,
                        loading = loading,
                        error = error,
                        items = items,
                        jobs = jobs,
                        continueWatching = continueWatching,
                        forYou = forYou,
                        tasteColdStart = tasteColdStart,
                        trendingMovies = trendingMovies,
                        trendingSeries = trendingSeries,
                        onOpenMovie = { openTitle(it) },
                        onOpenFolder = { push(Screen.Folder(it)) },
                        onPlayContinue = { playOrPick(it) },
                        onClearContinue = { item ->
                            continueWatching = continueWatching.filterNot { other ->
                                continueSame(other, item)
                            }
                            scope.launch {
                                runCatching { CoogApi(serverUrl, token).clearContinue(item) }
                                continueWatching = runCatching {
                                    CoogApi(serverUrl, token).catalogContinue()
                                }.getOrDefault(continueWatching)
                            }
                        },
                    )
                }
            }
            is Screen.Movie -> MovieDetailsScreen(
                item = screen.item,
                playError = playError,
                onBack = { pop() },
                onPlay = { playOrPick(it) },
                onSources = { playOrPick(it.copy(path = "", inLibrary = false, libraryId = ""), preferSources = true) },
                onOpenPerson = { push(Screen.Person(it)) },
                onOpenSimilar = { openTitle(it) },
            )
            is Screen.Show -> ShowDetailsScreen(
                show = overlayContinueProgress(
                    overlayShowLibrary(screen.show, items),
                    continueWatching,
                ),
                playError = playError,
                jobs = jobs,
                onBack = { pop() },
                onPlay = { playOrPick(it) },
                onSources = { playOrPick(it.copy(path = "", inLibrary = false, libraryId = ""), preferSources = true) },
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
                library = items,
                onBack = { pop() },
                onOpenTitle = { openTitle(it) },
            )
            is Screen.Player -> PlayerScreen(
                session = screen.session,
                title = screen.title,
                item = screen.item,
                nextItem = screen.nextItem,
                previousItem = screen.previousItem,
                autoplayNext = streaming.autoplayNextEpisode,
                prefetchNext = streaming.autoDownloadNextEpisode,
                prefetchBeforeEndMinutes = streaming.prefetchBeforeEndMinutes,
                token = token,
                serverUrl = serverUrl,
                onBack = { pop() },
                onPlayNeighbor = { neighbor ->
                    if (stack.lastOrNull() is Screen.Player) pop()
                    playOrPick(neighbor)
                },
                onPrefetchNeighbor = { neighbor ->
                    if (streaming.autoDownloadNextEpisode) prefetchItem(neighbor)
                },
            )
        }
    }
}

private fun continueSame(a: MediaItem, b: MediaItem): Boolean {
    val imdb = a.imdbId.trim()
    if (imdb.isNotBlank() && imdb.equals(b.imdbId.trim(), ignoreCase = true)) return true
    if (a.tmdbId != 0 && a.tmdbId == b.tmdbId) {
        val aKind = if (a.kind == "episode") "series" else a.kind
        val bKind = if (b.kind == "episode") "series" else b.kind
        if (aKind.equals(bKind, ignoreCase = true)) return true
    }
    val aMedia = a.diskMediaId()
    val bMedia = b.diskMediaId()
    if (aMedia.isNotBlank() && aMedia == bMedia) return true
    return a.id == b.id
}

private fun overlayShowLibrary(show: ShowRow, library: List<MediaItem>): ShowRow {
    val local = library.filter { it.kind == "episode" && showMatches(show, it) }
    if (local.isEmpty()) return show
    val episodes = mergeShowEpisodes(show.episodes, local)
    if (episodes == show.episodes) return show
    return show.copy(episodes = episodes)
}

private fun overlayContinueProgress(show: ShowRow, continueWatching: List<MediaItem>): ShowRow {
    val imdb = show.cover.imdbId.ifBlank { show.header?.imdbId.orEmpty() }
    val hit = continueWatching.firstOrNull { cw ->
        imdb.isNotBlank() && cw.imdbId.equals(imdb, ignoreCase = true) &&
            (cw.season > 0 || cw.episode > 0) && cw.positionMs > 0
    } ?: show.header?.takeIf { it.positionMs > 0 && (it.season > 0 || it.episode > 0) }
    if (hit == null) return show
    val episodes = show.episodes.map { ep ->
        if (ep.season == hit.season && ep.episode == hit.episode) {
            ep.copy(
                positionMs = hit.positionMs,
                durationMs = hit.durationMs.takeIf { it > 0 } ?: ep.durationMs,
            )
        } else {
            ep
        }
    }
    return show.copy(episodes = episodes)
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

private fun JobItem.asArtItem(): MediaItem = MediaItem(
    id = mediaId.ifBlank { id },
    kind = "movie",
    title = title,
    imdbId = imdbId,
)


private fun pickSmartStream(items: List<StreamCandidate>): StreamCandidate? {
    if (items.isEmpty()) return null
    fun qualityRank(q: String): Int {
        val s = q.lowercase()
        return when {
            "2160" in s || "4k" in s || "uhd" in s -> 4
            "1080" in s -> 3
            "720" in s -> 2
            "480" in s -> 1
            else -> 0
        }
    }
    val scored = items.sortedWith(
        compareByDescending<StreamCandidate> { it.cached }
            .thenByDescending { qualityRank(it.quality.ifBlank { it.title }) }
            .thenByDescending { it.seeders }
            .thenByDescending { it.size },
    )
    return scored.firstOrNull { it.cached }
        ?: scored.firstOrNull { it.seeders >= 5 || it.kind.equals("web", ignoreCase = true) }
        ?: scored.firstOrNull()
}

private fun serverHealthLine(stats: ServerStats?): String? {
    if (stats == null) return null
    val parts = mutableListOf<String>()
    if (stats.worker.stale) parts += "Worker offline"
    when {
        !stats.realDebrid.configured -> parts += "Real-Debrid not configured"
        stats.realDebrid.error.isNotBlank() -> parts += "Real-Debrid: ${stats.realDebrid.error}"
        stats.realDebrid.configured && !stats.realDebrid.premium -> parts += "Real-Debrid not premium"
    }
    val free = stats.disk?.freeBytes ?: 0L
    val total = stats.disk?.totalBytes ?: 0L
    if (total > 0 && free > 0 && free.toDouble() / total.toDouble() < 0.08) {
        parts += "Disk low"
    }
    if (stats.catalogError.isNotBlank()) parts += "Catalog: ${stats.catalogError}"
    return parts.joinToString(" · ").ifBlank { "Server healthy · ${stats.mediaCount} titles" }
}
