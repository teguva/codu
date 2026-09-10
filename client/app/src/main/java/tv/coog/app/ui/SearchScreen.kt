package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import tv.coog.app.data.CoogApi
import tv.coog.app.data.JobItem
import tv.coog.app.data.MediaItem
import tv.coog.app.data.PersonSummary
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogTextMuted
import tv.coog.app.ui.theme.CoogTextSecondary
import tv.coog.app.ui.theme.CoogType

@Composable
fun SearchScreen(
    jobs: List<JobItem>,
    onOpenTitle: (MediaItem) -> Unit,
    onOpenPerson: (PersonSummary) -> Unit,
) {
    val server = LocalCoogServer.current
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<tv.coog.app.data.SearchResponse?>(null) }
    val fieldFocus = LocalBrowseContentFocus.current ?: remember { FocusRequester() }
    val railFocus = LocalRailFocus.current
    LaunchedEffect(query, server.url, server.token) {
        val q = query.trim()
        if (q.length < 2) {
            result = null
            error = null
            loading = false
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        error = null
        try {
            val got = CoogApi(server.url, server.token).catalogSearch(q)
            result = got
            error = got.error.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            result = null
            error = e.message ?: "Search failed"
        } finally {
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep)
            .padding(start = 40.dp, top = topBarHeight() + 6.dp, end = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Search", style = CoogType.screenTitle)
                Text(
                    "Movies, series, and people. Type with the TV keyboard.",
                    style = CoogType.heroPlot,
                    color = CoogTextSecondary,
                )
            }
        }
        item {
            TvTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search titles or actors",
                modifier = Modifier
                    .focusRequester(fieldFocus)
                    .then(
                        if (railFocus != null) Modifier.focusProperties { up = railFocus } else Modifier,
                    ),
            )
        }
        if (query.trim().length < 2 && result == null) {
            item {
                Text("Start typing a title or actor name.", style = CoogType.heroPlot, color = CoogTextSecondary)
            }
        }
        when {
            loading -> item { Text("Searching…", style = CoogType.heroPlot) }
            error != null && result == null -> item { Text(error ?: "", color = Color(0xFFFF8B8B)) }
            result != null -> {
                val movies = result?.movies.orEmpty()
                val series = result?.series.orEmpty()
                val people = result?.people.orEmpty()
                if (error != null) {
                    item { Text(error ?: "", color = Color(0xFFFF8B8B)) }
                }
                if (movies.isEmpty() && series.isEmpty() && people.isEmpty() && error == null) {
                    item { Text("No matches.", style = CoogType.heroPlot) }
                }
                if (movies.isNotEmpty()) {
                    item {
                        CatalogRow(label = "Movies", items = movies, onOpen = onOpenTitle, jobs = jobs, insetStart = 0.dp)
                    }
                }
                if (series.isNotEmpty()) {
                    item {
                        CatalogRow(label = "Series", items = series, onOpen = onOpenTitle, jobs = jobs, insetStart = 0.dp)
                    }
                }
                if (people.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("People", style = CoogType.shelfTitle)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                itemsIndexed(people, key = { index, person -> "${person.tmdbId}-$index" }) { index, person ->
                                    PersonChip(
                                        person = person,
                                        onClick = { onOpenPerson(person) },
                                        modifier = if (index == 0 && railFocus != null) {
                                            Modifier.focusProperties { up = railFocus }
                                        } else {
                                            Modifier
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonScreen(
    person: PersonSummary,
    jobs: List<JobItem>,
    onBack: () -> Unit,
    onOpenTitle: (MediaItem) -> Unit,
) {
    val server = LocalCoogServer.current
    var details by remember(person.tmdbId) { mutableStateOf(person) }
    var error by remember(person.tmdbId) { mutableStateOf<String?>(null) }
    var loading by remember(person.tmdbId) { mutableStateOf(person.credits.isEmpty()) }
    val posterFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val movies = remember(details.credits) {
        details.credits.filter { it.kind != "series" && it.kind != "episode" }
            .sortedByDescending { it.year }
    }
    val series = remember(details.credits) {
        details.credits.filter { it.kind == "series" || it.kind == "episode" }
            .sortedByDescending { it.year }
    }
    val knownFor = remember(details.credits) { details.credits.take(8) }
    val hero = knownFor.firstOrNull { it.backdropUrl.isNotBlank() } ?: knownFor.firstOrNull()

    LaunchedEffect(person.tmdbId, server.url) {
        if (person.tmdbId == 0) return@LaunchedEffect
        loading = details.credits.isEmpty()
        try {
            details = CoogApi(server.url, server.token).catalogPerson(person.tmdbId)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Could not load credits"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(knownFor.firstOrNull()?.id, loading, error) {
        if (knownFor.isNotEmpty()) {
            runCatching { posterFocus.requestFocus() }
        } else {
            runCatching { backFocus.requestFocus() }
        }
    }

    val dept = details.knownForDepartment.ifBlank { person.knownForDepartment }
    val born = personBornLine(details.birthday, details.placeOfBirth)
    val counts = listOfNotNull(
        movies.size.takeIf { it > 0 }?.let { if (it == 1) "1 movie" else "$it movies" },
        series.size.takeIf { it > 0 }?.let { if (it == 1) "1 series" else "$it series" },
    ).joinToString("  ·  ")

    Box(modifier = Modifier.fillMaxSize().background(CoogBgDeep)) {
        if (hero != null) {
            PosterArt(item = hero, kind = ArtKind.Backdrop, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to CoogBgDeep.copy(alpha = 0.96f),
                    0.48f to CoogBgDeep.copy(alpha = 0.78f),
                    0.82f to CoogBgDeep.copy(alpha = 0.42f),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to CoogBgDeep.copy(alpha = 0.18f),
                    0.55f to Color.Transparent,
                    1f to CoogBgDeep,
                ),
            ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 48.dp, end = 40.dp, top = 36.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 248.dp, height = 372.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    ) {
                        val photo = details.profileUrl.ifBlank { person.profileUrl }
                        if (photo.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photo)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = details.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                details.name.take(1).uppercase(),
                                style = CoogType.heroTitle,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(0.78f).padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(details.name.ifBlank { person.name }, style = CoogType.heroTitle, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (dept.isNotBlank()) {
                                Text(
                                    dept,
                                    style = CoogType.chip,
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                            if (counts.isNotBlank()) {
                                Text(counts, style = CoogType.heroTagline, color = CoogTextSecondary)
                            }
                        }
                        if (born.isNotBlank()) {
                            Text(born, style = CoogType.cardYear, color = CoogTextMuted)
                        }
                        if (details.biography.isNotBlank()) {
                            Text(
                                details.biography,
                                style = CoogType.heroPlot,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (loading) {
                            Text("Loading filmography…", style = CoogType.heroPlot, color = CoogTextMuted)
                        }
                        if (error != null) {
                            Text(error ?: "", color = Color(0xFFFF8B8B))
                        }
                        GhostButton(
                            label = "Back",
                            onClick = onBack,
                            modifier = Modifier.focusRequester(backFocus),
                        )
                    }
                }
            }
            if (knownFor.isNotEmpty()) {
                item {
                    CatalogRow(
                        label = "Known for",
                        items = knownFor,
                        onOpen = onOpenTitle,
                        jobs = jobs,
                        firstFocus = posterFocus,
                        insetStart = 0.dp,
                    )
                }
            }
            if (movies.isNotEmpty()) {
                item {
                    CatalogRow(
                        label = "Movies",
                        items = movies,
                        onOpen = onOpenTitle,
                        jobs = jobs,
                        insetStart = 0.dp,
                    )
                }
            }
            if (series.isNotEmpty()) {
                item {
                    CatalogRow(
                        label = "Series",
                        items = series,
                        onOpen = onOpenTitle,
                        jobs = jobs,
                        insetStart = 0.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonChip(
    person: PersonSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF121214),
        ),
        modifier = modifier.width(132.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(person.profileUrl.ifBlank { null })
                    .crossfade(true)
                    .build(),
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(108.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
            )
            Text(person.name, style = CoogType.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun personBornLine(birthday: String, place: String): String {
    val date = formatPersonBirthday(birthday)
    return listOfNotNull(
        date?.let { "Born $it" },
        place.trim().takeIf { it.isNotBlank() },
    ).joinToString("  ·  ")
}

private fun formatPersonBirthday(raw: String): String? {
    val parts = raw.trim().split("-")
    if (parts.size != 3) return raw.trim().takeIf { it.isNotBlank() }
    val year = parts[0]
    val month = parts[1].toIntOrNull() ?: return raw
    val day = parts[2].toIntOrNull() ?: return raw
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val label = months.getOrNull(month - 1) ?: return raw
    return "$day $label $year"
}
