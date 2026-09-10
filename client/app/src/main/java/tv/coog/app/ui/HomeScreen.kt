package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.coog.app.data.MediaItem
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogType

@Composable
fun HomeScreen(
    tab: BrowseTab,
    loading: Boolean,
    error: String?,
    items: List<MediaItem>,
    onOpenMovie: (MediaItem) -> Unit,
    onOpenShow: (ShowRow) -> Unit,
    onOpenFolder: (FolderRow) -> Unit,
) {
    val movies = remember(items) { items.movieItems() }
    val shows = remember(items) { items.showRows() }
    val folders = remember(items) { items.folderRows() }
    val firstFocus = remember { FocusRequester() }
    var selected by remember(items, tab) {
        mutableStateOf(
            when (tab) {
                BrowseTab.Series -> shows.firstOrNull()?.cover
                BrowseTab.Folders -> folders.firstOrNull()?.cover
                else -> movies.firstOrNull() ?: shows.firstOrNull()?.cover ?: folders.firstOrNull()?.cover
            },
        )
    }
    LaunchedEffect(movies.firstOrNull()?.id, shows.firstOrNull()?.name, error, loading, tab) {
        if (!loading && error == null && (movies.isNotEmpty() || shows.isNotEmpty() || folders.isNotEmpty())) {
            delay(80)
            runCatching { firstFocus.requestFocus() }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep),
    ) {
        val heroHeight = maxHeight * 0.55f
        HeroBanner(
            item = selected,
            rowLabel = when (tab) {
                BrowseTab.Series -> "Series"
                BrowseTab.Folders -> "Library"
                BrowseTab.Movies -> "Movies"
                else -> if (selected?.kind == "episode") "Series" else "Movies"
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = maxHeight * 0.44f)
                .focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                error != null -> {
                    Column(
                        modifier = Modifier.padding(start = RailWidth + 18.dp, end = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Text(
                            "On this emulator use http://10.0.2.2:8090. On a TV, use your server's LAN address.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                loading -> {
                    Text(
                        "One moment.",
                        style = CoogType.heroTagline,
                        modifier = Modifier.padding(start = RailWidth + 18.dp, top = 24.dp),
                    )
                }
                items.isEmpty() -> {
                    Column(
                        modifier = Modifier.padding(start = RailWidth + 18.dp, end = 28.dp, top = 24.dp),
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
                    if (tab == BrowseTab.Home || tab == BrowseTab.Movies) {
                        if (movies.isNotEmpty()) {
                            CatalogRow(
                                label = if (tab == BrowseTab.Home) "Movies" else "All movies",
                                items = movies,
                                onOpen = onOpenMovie,
                                onFocused = { selected = it },
                                firstFocus = firstFocus,
                            )
                        }
                    }
                    if (tab == BrowseTab.Home || tab == BrowseTab.Series) {
                        if (shows.isNotEmpty()) {
                            ShowCatalogRow(
                                label = if (tab == BrowseTab.Home) "Series" else "All series",
                                shows = shows,
                                onOpen = onOpenShow,
                                onFocused = { selected = it },
                                firstFocus = if (tab == BrowseTab.Series) firstFocus else null,
                            )
                        }
                    }
                    if (tab == BrowseTab.Home || tab == BrowseTab.Folders) {
                        if (folders.isNotEmpty()) {
                            FolderCatalogRow(
                                label = if (tab == BrowseTab.Home) "Library" else "Folders",
                                folders = folders,
                                onOpen = onOpenFolder,
                                onFocused = { selected = it },
                                firstFocus = if (tab == BrowseTab.Folders) firstFocus else null,
                            )
                        }
                    }
                }
            }
        }
    }
}
