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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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
    val recommended = remember(trendingMovies, trendingSeries, movies) {
        (trendingMovies + trendingSeries).ifEmpty { movies }
    }
    val continueWatching = remember(movies, recommended) {
        overlayCatalog(movies.filter { it.isLocal() }.ifEmpty { movies }, recommended)
    }

    val hasContent = when (tab) {
        BrowseTab.Series -> shows.isNotEmpty() || trendingSeries.isNotEmpty()
        BrowseTab.Folders -> folders.isNotEmpty()
        BrowseTab.Movies -> movies.isNotEmpty() || trendingMovies.isNotEmpty()
        else -> recommended.isNotEmpty() || shows.isNotEmpty() || continueWatching.isNotEmpty()
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
            val homeRows = remember(recommended, continueWatching) {
                buildList {
                    if (recommended.isNotEmpty()) add("Recommended for you" to recommended)
                    if (continueWatching.isNotEmpty()) add("Continue watching" to continueWatching)
                }
            }
            val movieRows = remember(trendingMovies, movies) {
                buildList {
                    val library = overlayCatalog(movies, trendingMovies)
                    if (library.isNotEmpty()) add("Movies" to library)
                    else if (trendingMovies.isNotEmpty()) add("Recommended movies" to trendingMovies)
                    if (library.isNotEmpty() && trendingMovies.isNotEmpty()) {
                        add("Recommended movies" to trendingMovies)
                    }
                }
            }
            val seriesRows = remember(trendingSeries, shows) {
                buildList {
                    val library = overlayCatalog(shows.map { it.asFeaturedItem() }, trendingSeries)
                    if (library.isNotEmpty()) add("Series" to library)
                    else if (trendingSeries.isNotEmpty()) add("Recommended series" to trendingSeries)
                    if (library.isNotEmpty() && trendingSeries.isNotEmpty()) {
                        add("Recommended series" to trendingSeries)
                    }
                }
            }
            when (tab) {
                BrowseTab.Home -> BillboardShelves(
                    rows = homeRows,
                    firstFocus = firstFocus,
                    inset = inset,
                    onOpen = onOpenMovie,
                )
                BrowseTab.Movies -> BillboardShelves(
                    rows = movieRows,
                    firstFocus = firstFocus,
                    inset = inset,
                    onOpen = onOpenMovie,
                )
                BrowseTab.Series -> BillboardShelves(
                    rows = seriesRows,
                    firstFocus = firstFocus,
                    inset = inset,
                    onOpen = onOpenMovie,
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CoogBgDeep)
                            .padding(top = topBarHeight())
                            .focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
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
                }
            }
        }
    }
}

@Composable
private fun BillboardShelves(
    rows: List<Pair<String, List<MediaItem>>>,
    firstFocus: FocusRequester,
    inset: Dp,
    onOpen: (MediaItem) -> Unit,
) {
    var focusedRow by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep)
            .padding(top = topBarHeight(), bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEachIndexed { i, (title, rowItems) ->
            val weight = if (focusedRow == i) 1.18f else 0.60f
            FeaturedCarousel(
                items = rowItems,
                label = title,
                onOpen = onOpen,
                expanded = focusedRow == i,
                onRowFocused = { focusedRow = i },
                firstFocus = if (i == 0) firstFocus else null,
                exitUp = i == 0,
                insetStart = inset,
                modifier = Modifier.weight(weight).fillMaxWidth(),
            )
        }
    }
}
