package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tv.coog.app.data.CastMember
import tv.coog.app.data.JobItem
import tv.coog.app.data.MediaItem
import tv.coog.app.data.PersonSummary
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogCached
import tv.coog.app.ui.theme.CoogTextMuted
import tv.coog.app.ui.theme.CoogTextSecondary
import tv.coog.app.ui.theme.CoogType

@Composable
fun TitleOverview(
    item: MediaItem,
    jobs: List<JobItem> = emptyList(),
    onPlay: (MediaItem) -> Unit,
    onSources: ((MediaItem) -> Unit)? = null,
    onOpenPerson: (PersonSummary) -> Unit = {},
    onBack: (() -> Unit)? = null,
    playFocus: FocusRequester? = null,
    pinPlayLeftToRail: Boolean = false,
    similar: List<MediaItem> = emptyList(),
    similarLabel: String = "Similar movies",
    onOpenSimilar: ((MediaItem) -> Unit)? = null,
    playError: String? = null,
    bottomShelf: (@Composable () -> Unit)? = null,
    extraShelf: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val railFocus = LocalRailFocus.current
    val genres = item.heroGenres()
    val meta = item.heroMetaLine()
    val plot = item.heroDescription()
    val showSources = onSources != null && (!item.isLocal() || item.imdbId.isNotBlank()) &&
        (!item.playBlocked() || item.isLocal())
    val shelf = bottomShelf ?: if (similar.isNotEmpty() && onOpenSimilar != null) {
        {
            CatalogRow(
                label = similarLabel,
                items = similar,
                onOpen = onOpenSimilar,
                jobs = jobs,
                insetStart = 72.dp,
                compact = true,
            )
        }
    } else {
        null
    }
    Box(modifier = modifier.fillMaxSize().background(CoogBgDeep)) {
        PosterArt(
            item = item,
            kind = ArtKind.Backdrop,
            badge = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.00f to CoogBgDeep.copy(alpha = 0.88f),
                    0.28f to CoogBgDeep.copy(alpha = 0.42f),
                    0.58f to Color.Transparent,
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1.00f to CoogBgDeep.copy(alpha = 0.78f),
                ),
            ),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val heroHeight = if (shelf != null || extraShelf != null) maxHeight * 0.70f else maxHeight
            val posterHeight = minOf(342.dp, (heroHeight - 48.dp) * 0.90f)
            val posterWidth = posterHeight * (228f / 342f)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "hero") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            .padding(start = 72.dp, end = 48.dp, top = 36.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(posterWidth)
                                .height(posterHeight)
                                .clip(RoundedCornerShape(16.dp)),
                        ) {
                            PosterArt(
                                item = item,
                                kind = ArtKind.Poster,
                                badge = item.posterBadgeLabel(jobs),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 18.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (genres.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 12.dp),
                                ) {
                                    genres.forEach { chip ->
                                        Text(
                                            chip,
                                            style = CoogType.chip,
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                                                .padding(horizontal = 12.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                            TitleLockup(
                                item,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    style = CoogType.heroTagline,
                                    color = CoogTextSecondary,
                                    maxLines = 1,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                            if (plot.isNotBlank()) {
                                Text(
                                    plot,
                                    style = CoogType.heroPlot.copy(fontSize = 16.sp, lineHeight = 24.sp),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 22.dp).fillMaxWidth(0.92f),
                                )
                            }
                            if (playError != null) {
                                Text(
                                    friendlyPlayError(playError),
                                    color = Color(0xFFFF8B8B),
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            } else if (item.playBlocked()) {
                                Text(
                                    "Not released yet. Play is available when it comes out, or if you already have a local file.",
                                    color = Color(0xFFFF8B8B),
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                WhitePill(
                                    label = "Play",
                                    icon = Icons.Filled.PlayArrow,
                                    onClick = { onPlay(item) },
                                    modifier = Modifier
                                        .then(if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier)
                                        .then(
                                            if (pinPlayLeftToRail && railFocus != null) {
                                                Modifier.focusProperties { left = railFocus }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                                if (showSources) {
                                    GhostButton(label = "Sources", onClick = { onSources?.invoke(item) })
                                }
                            }
                        }
                        OverviewSideCard(item = item, onOpenPerson = onOpenPerson)
                    }
                }
                if (shelf != null) {
                    item(key = "shelf") {
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            shelf()
                        }
                    }
                }
                if (extraShelf != null) {
                    item(key = "extra-shelf") {
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                            extraShelf()
                        }
                    }
                }
            }
        }
        if (onBack != null) {
            GlassCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Back",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 20.dp),
            )
        }
    }
}

@Composable
private fun OverviewSideCard(item: MediaItem, onOpenPerson: (PersonSummary) -> Unit) {
    if (!item.hasOfficialMeta()) return
    val people = item.cast.filter { it.name.isNotBlank() }
    if (item.rating <= 0 && item.director.name.isBlank() && people.isEmpty()) return
    Column(
        modifier = Modifier
            .width(252.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (item.rating > 0) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format("%.1f", item.rating),
                    color = CoogCached,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 48.sp,
                )
                Text(
                    "/10",
                    style = CoogType.heroTagline,
                    color = CoogTextMuted,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                )
            }
        }
        if (item.director.name.isNotBlank() || people.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xD91C1C22))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (item.director.name.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DIRECTOR", style = CoogType.cardYear, color = CoogTextMuted)
                        CastMini(person = item.director, onClick = {
                            onOpenPerson(
                                PersonSummary(
                                    tmdbId = item.director.tmdbId,
                                    name = item.director.name,
                                    profileUrl = item.director.profileUrl,
                                ),
                            )
                        })
                    }
                }
                if (people.isNotEmpty()) {
                    Text("CAST", style = CoogType.cardYear, color = CoogTextMuted)
                    people.forEach { person ->
                        CastMini(person = person, onClick = {
                            onOpenPerson(
                                PersonSummary(
                                    tmdbId = person.tmdbId,
                                    name = person.name,
                                    profileUrl = person.profileUrl,
                                ),
                            )
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun CastMini(person: CastMember, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                if (person.profileUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(person.profileUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = person.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(person.name, style = CoogType.cardTitle.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF121214),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .size(44.dp)
            .then(if (!focused) Modifier.border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape) else Modifier)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = LocalContentColor.current, modifier = Modifier.size(20.dp))
        }
    }
}
