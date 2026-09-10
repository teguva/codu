package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.coog.app.data.JobItem
import tv.coog.app.data.MediaItem
import tv.coog.app.data.PersonSummary
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogType

@Composable
fun HomeScreen(
    tab: BrowseTab,
    loading: Boolean,
    error: String?,
    items: List<MediaItem>,
    jobs: List<JobItem>,
    trendingMovies: List<MediaItem>,
    trendingSeries: List<MediaItem>,
    onOpenMovie: (MediaItem) -> Unit,
    onOpenShow: (ShowRow) -> Unit,
    onOpenFolder: (FolderRow) -> Unit,
    onPlayJob: (JobItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onSources: (MediaItem) -> Unit,
    onOpenPerson: (PersonSummary) -> Unit,
) {
    val movies = remember(items) { items.movieItems() }
    val shows = remember(items) { items.showRows() }
    val folders = remember(items) { items.folderRows() }
    val firstFocus = LocalBrowseContentFocus.current ?: remember { FocusRequester() }
    val navBarFocused = LocalNavBarFocused.current
    val recommended = trendingMovies.ifEmpty { movies }
    val continueWatching = movies.filter { it.isLocal() }.ifEmpty { movies }

    val hasContent = when (tab) {
        BrowseTab.Series -> shows.isNotEmpty() || trendingSeries.isNotEmpty()
        BrowseTab.Folders -> folders.isNotEmpty()
        BrowseTab.Movies -> movies.isNotEmpty() || trendingMovies.isNotEmpty()
        else -> recommended.isNotEmpty() || shows.isNotEmpty() || continueWatching.isNotEmpty()
    }
    LaunchedEffect(tab, recommended.firstOrNull()?.id, movies.firstOrNull()?.id, shows.firstOrNull()?.name, error, loading, navBarFocused) {
        if (!navBarFocused && !loading && error == null && hasContent) {
            delay(80)
            runCatching { firstFocus.requestFocus() }
        }
    }

    val inset = catalogInset()
    val pad = Modifier.padding(start = inset, top = topBarHeight() + 6.dp, end = inset)
    when {
        error != null && !hasContent -> {
            Column(
                modifier = Modifier.fillMaxSize().background(CoogBgDeep).then(pad),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Text(
                    "Open Settings and set the server to this PC's LAN IP on port 8090.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        loading -> {
            Text(
                "One moment.",
                style = CoogType.heroTagline,
                modifier = Modifier.fillMaxSize().background(CoogBgDeep).then(pad),
            )
        }
        !hasContent -> {
            Column(
                modifier = Modifier.fillMaxSize().background(CoogBgDeep).then(pad),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Your library is empty", style = CoogType.heroTitle)
                Text(
                    "Put films in Videos/Movies and shows in Videos/Series.",
                    style = CoogType.heroPlot,
                )
            }
        }
        else -> {
            if (tab == BrowseTab.Home) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CoogBgDeep)
                        .padding(top = topBarHeight(), bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (recommended.isNotEmpty()) {
                        FeaturedCarousel(
                            items = recommended,
                            onPlay = onPlay,
                            onMoreInfo = onOpenMovie,
                            firstFocus = firstFocus,
                            insetStart = inset,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                    if (continueWatching.isNotEmpty()) {
                        CatalogRow(
                            label = "Continue watching",
                            items = continueWatching,
                            onOpen = onOpenMovie,
                            jobs = jobs,
                            insetStart = inset,
                            featured = true,
                            showBadge = false,
                        )
                    }
                }
            } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CoogBgDeep)
                    .padding(top = topBarHeight())
                    .focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                when (tab) {
                    BrowseTab.Movies -> {
                        val row = trendingMovies.ifEmpty { movies }
                        item(key = "movies") {
                            CatalogRow(
                                label = if (trendingMovies.isNotEmpty()) "Recommended for you" else "Movies",
                                items = row,
                                onOpen = onOpenMovie,
                                firstFocus = firstFocus,
                                jobs = jobs,
                                insetStart = inset,
                                featured = true,
                                exitUp = true,
                            )
                        }
                        if (trendingMovies.isNotEmpty() && movies.isNotEmpty()) {
                            item(key = "local-movies") {
                                CatalogRow(
                                    label = "In your library",
                                    items = movies,
                                    onOpen = onOpenMovie,
                                    jobs = jobs,
                                    insetStart = inset,
                                    featured = true,
                                )
                            }
                        }
                    }
                    BrowseTab.Series -> {
                        if (trendingSeries.isNotEmpty()) {
                            item(key = "trending-series") {
                                CatalogRow(
                                    label = "Recommended for you",
                                    items = trendingSeries,
                                    onOpen = onOpenMovie,
                                    firstFocus = firstFocus,
                                    jobs = jobs,
                                    insetStart = inset,
                                    featured = true,
                                    exitUp = true,
                                )
                            }
                        }
                        if (shows.isNotEmpty()) {
                            item(key = "local-series") {
                                ShowCatalogRow(
                                    label = if (trendingSeries.isEmpty()) "Series" else "In your library",
                                    shows = shows,
                                    onOpen = onOpenShow,
                                    firstFocus = if (trendingSeries.isEmpty()) firstFocus else null,
                                    jobs = jobs,
                                    insetStart = inset,
                                    featured = true,
                                    exitUp = trendingSeries.isEmpty(),
                                )
                            }
                        }
                    }
                    BrowseTab.Folders -> {
                        item(key = "folders") {
                            FolderCatalogRow(
                                label = "Library",
                                folders = folders,
                                onOpen = onOpenFolder,
                                firstFocus = firstFocus,
                                insetStart = inset,
                                featured = true,
                                exitUp = true,
                            )
                        }
                    }
                    else -> {}
                }
            }
            }
        }
    }
}
