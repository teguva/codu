package tv.coog.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.coog.app.R
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.coog.app.ui.theme.CoogBgDeep

enum class BrowseTab { Home, Search, Movies, Series, Folders, Downloads, Settings }

val RailWidth = 32.dp

@Composable
fun catalogInset(): Dp {
    val w = LocalConfiguration.current.screenWidthDp
    return (w * 0.054f).dp
}

private val PillTabs = listOf(
    BrowseTab.Home,
    BrowseTab.Movies,
    BrowseTab.Series,
    BrowseTab.Folders,
    BrowseTab.Downloads,
)
private val IconTabs = listOf(BrowseTab.Search, BrowseTab.Settings)
private val RailOrder = PillTabs + IconTabs
private val CircleBtn = Color.White.copy(alpha = 0.10f)
/** Google TV launcher tab / search / profile control height at xhdpi. */
private val NavItemHeight = 32.dp
private val NavBarPadTop = 8.dp
private val NavBarPadBottom = 6.dp

@Composable
fun topBarHeight(): Dp = NavBarPadTop + NavItemHeight + NavBarPadBottom

@Composable
fun AppShell(
    tab: BrowseTab,
    onTab: (BrowseTab) -> Unit,
    content: @Composable () -> Unit,
) {
    var railFocused by remember { mutableStateOf(false) }
    var railIndex by remember { mutableIntStateOf(0) }
    val contentFocus = remember { FocusRequester() }
    val focusCount = RailOrder.size + 1
    val railRequesters = remember { List(focusCount) { FocusRequester() } }
    val currentRail = railRequesters[RailOrder.indexOf(tab).coerceAtLeast(0)]
    val barHeight = topBarHeight()
    val itemHeight = NavItemHeight
    val railFocusedState = rememberUpdatedState(railFocused)

    fun showTab(next: BrowseTab) {
        onTab(next)
    }

    fun enterTab(next: BrowseTab) {
        onTab(next)
        runCatching { contentFocus.requestFocus() }
    }

    fun leaveRail() {
        railFocused = false
        runCatching { contentFocus.requestFocus() }
    }

    BackHandler(enabled = railFocused) {
        leaveRail()
    }

    LaunchedEffect(tab) {
        val idx = RailOrder.indexOf(tab)
        if (idx >= 0) railIndex = idx
    }

    LaunchedEffect(Unit) {
        repeat(40) {
            delay(80)
            if (railFocusedState.value) return@LaunchedEffect
            if (runCatching { contentFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalBrowseContentFocus provides contentFocus,
                LocalRailFocus provides currentRail,
                LocalNavBarFocused provides railFocused,
            ) {
                content()
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .zIndex(2f)
                .onFocusChanged { if (it.hasFocus) railFocused = true }
                .onPreviewKeyEvent { event ->
                    val dpad = event.key == Key.DirectionRight ||
                        event.key == Key.DirectionLeft ||
                        event.key == Key.DirectionDown ||
                        event.key == Key.DirectionUp
                    if (!dpad) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> leaveRail()
                            Key.DirectionRight -> {
                                val next = (railIndex + 1).coerceAtMost(focusCount - 1)
                                runCatching { railRequesters[next].requestFocus() }
                            }
                            Key.DirectionLeft -> {
                                val prev = (railIndex - 1).coerceAtLeast(0)
                                runCatching { railRequesters[prev].requestFocus() }
                            }
                            else -> {}
                        }
                    }
                    event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp
                }
                .padding(
                    start = catalogInset(),
                    end = catalogInset(),
                    top = NavBarPadTop,
                    bottom = NavBarPadBottom,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_coog_logo),
                contentDescription = "Coog",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(itemHeight),
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillTabs.forEachIndexed { index, value ->
                    NavPill(
                        value = value,
                        icon = tabIcon(value, selected = tab == value),
                        label = tabLabel(value),
                        selected = tab,
                        onTab = ::enterTab,
                        requester = railRequesters[index],
                        onFocused = {
                            railFocused = true
                            railIndex = index
                            showTab(value)
                        },
                        height = itemHeight,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconTabs.forEachIndexed { offset, value ->
                    val index = PillTabs.size + offset
                    NavIcon(
                        value = value,
                        icon = tabIcon(value, selected = tab == value),
                        label = tabLabel(value),
                        selected = tab,
                        onTab = ::enterTab,
                        requester = railRequesters[index],
                        onFocused = {
                            railFocused = true
                            railIndex = index
                            showTab(value)
                        },
                        size = itemHeight,
                    )
                }
                NavAvatar(
                    size = itemHeight,
                    requester = railRequesters.last(),
                    onFocused = {
                        railFocused = true
                        railIndex = focusCount - 1
                        showTab(BrowseTab.Settings)
                    },
                    onClick = { enterTab(BrowseTab.Settings) },
                )
            }
        }
    }
}

private fun tabIcon(tab: BrowseTab, selected: Boolean): ImageVector = when (tab) {
    BrowseTab.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    BrowseTab.Movies -> Icons.Outlined.Movie
    BrowseTab.Series -> Icons.Outlined.Tv
    BrowseTab.Folders -> Icons.Outlined.Folder
    BrowseTab.Downloads -> Icons.Outlined.Download
    BrowseTab.Search -> Icons.Outlined.Search
    BrowseTab.Settings -> Icons.Outlined.Settings
}

private fun tabLabel(tab: BrowseTab): String = when (tab) {
    BrowseTab.Home -> "Home"
    BrowseTab.Movies -> "Movies"
    BrowseTab.Series -> "Series"
    BrowseTab.Folders -> "Library"
    BrowseTab.Downloads -> "Downloads"
    BrowseTab.Search -> "Search"
    BrowseTab.Settings -> "Settings"
}

@Composable
private fun NavPill(
    value: BrowseTab,
    icon: ImageVector,
    label: String,
    selected: BrowseTab,
    onTab: (BrowseTab) -> Unit,
    requester: FocusRequester,
    onFocused: () -> Unit,
    height: Dp,
) {
    val active = selected == value
    Surface(
        onClick = { onTab(value) },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) Color.White else CircleBtn,
            contentColor = if (active) Color(0xFF121214) else Color.White.copy(alpha = 0.78f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF121214),
            pressedContainerColor = Color.White.copy(alpha = 0.92f),
            pressedContentColor = Color(0xFF121214),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .height(height)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = label, tint = LocalContentColor.current, modifier = Modifier.size(16.dp))
            Text(label, color = LocalContentColor.current, fontSize = 14.sp)
        }
    }
}

@Composable
private fun NavIcon(
    value: BrowseTab,
    icon: ImageVector,
    label: String,
    selected: BrowseTab,
    onTab: (BrowseTab) -> Unit,
    requester: FocusRequester,
    onFocused: () -> Unit,
    size: Dp,
) {
    val active = selected == value
    Surface(
        onClick = { onTab(value) },
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) Color.White.copy(alpha = 0.18f) else CircleBtn,
            contentColor = if (active) Color.White else Color.White.copy(alpha = 0.82f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF121214),
            pressedContainerColor = Color.White.copy(alpha = 0.92f),
            pressedContentColor = Color(0xFF121214),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .size(size)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = LocalContentColor.current, modifier = Modifier.size(size * 0.48f))
        }
    }
}

@Composable
private fun NavAvatar(
    size: Dp,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF3A3A40),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF121214),
            pressedContainerColor = Color.White.copy(alpha = 0.92f),
            pressedContentColor = Color(0xFF121214),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .size(size)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Person,
                contentDescription = "Profile",
                tint = LocalContentColor.current,
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}
