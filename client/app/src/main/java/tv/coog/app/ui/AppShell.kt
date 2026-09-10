package tv.coog.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.coog.app.ui.theme.CoogBgDeep

enum class BrowseTab { Home, Movies, Series, Folders, Settings }

val RailWidth = 76.dp
private val RailExpandedWidth = 220.dp

@Composable
fun AppShell(
    tab: BrowseTab,
    onTab: (BrowseTab) -> Unit,
    content: @Composable () -> Unit,
) {
    var railFocused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (railFocused) RailExpandedWidth else RailWidth, label = "rail")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .zIndex(2f)
                .onFocusChanged { railFocused = it.hasFocus }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF00E0E10), Color(0xF508080A), Color(0xF7050506)),
                    ),
                )
                .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "TV",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 10.dp, bottom = 14.dp),
            )
            RailIcon(BrowseTab.Home, Icons.Filled.Home, "Home", tab, onTab, railFocused)
            RailIcon(BrowseTab.Movies, Icons.Filled.Movie, "Movies", tab, onTab, railFocused)
            RailIcon(BrowseTab.Series, Icons.Filled.Tv, "Series", tab, onTab, railFocused)
            RailIcon(BrowseTab.Folders, Icons.Filled.Folder, "Library", tab, onTab, railFocused)
            Spacer(Modifier.weight(1f))
            RailIcon(BrowseTab.Settings, Icons.Filled.Settings, "Settings", tab, onTab, railFocused)
        }
    }
}

@Composable
private fun RailIcon(
    value: BrowseTab,
    icon: ImageVector,
    label: String,
    selected: BrowseTab,
    onTab: (BrowseTab) -> Unit,
    expanded: Boolean,
) {
    val active = selected == value
    Surface(
        onClick = { onTab(value) },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (active) Color.White else Color.White.copy(alpha = 0.52f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF121214),
            pressedContainerColor = Color.White.copy(alpha = 0.92f),
            pressedContentColor = Color(0xFF121214),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = LocalContentColor.current,
                modifier = Modifier.size(22.dp),
            )
            if (expanded) {
                Text(
                    label,
                    color = LocalContentColor.current,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }
    }
}
