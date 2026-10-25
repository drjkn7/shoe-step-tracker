package io.github.drjkn7.shoestep.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.ui.theme.SilverSpacing
import io.github.drjkn7.shoestep.ui.theme.SilverTheme
import io.github.drjkn7.shoestep.ui.theme.backdrop

/** The round button at the start of a screen's top bar. */
enum class NavIcon { BACK, CLOSE }

/**
 * Every screen's frame (PNGs 01, 05, 08, 21): the sky-to-steel backdrop edge to edge, then a top bar
 * with an optional round Back or Close button, a small title and round action buttons at the end.
 * [floatingButton] sits at the bottom end above the navigation bar (Home's "Add pair").
 * [content] fills the rest; use [ScreenColumn] for a scrolling page of cards.
 */
@Composable
fun SilverScreen(
    modifier: Modifier = Modifier,
    title: String? = null,
    navIcon: NavIcon? = null,
    onNavigate: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingButton: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().backdrop(SilverTheme.colors)) {
        // The backdrop fills the screen; on tablets and in landscape the content keeps a phone-like
        // width in the middle instead of stretching edge to edge. Phones are narrower than the cap.
        Box(Modifier.fillMaxHeight().widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().align(Alignment.TopCenter)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                TopBar(title, navIcon, onNavigate, actions)
                Box(Modifier.fillMaxWidth().weight(1f), content = content)
            }
            floatingButton?.let {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(end = SilverSpacing.screen, bottom = SilverSpacing.m),
                ) { it() }
            }
        }
    }
}

/** Widest the content of a screen gets (tablets, landscape); the backdrop still fills the screen. */
private val MAX_CONTENT_WIDTH = 640.dp

@Composable
private fun TopBar(title: String?, navIcon: NavIcon?, onNavigate: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = SilverSpacing.m, vertical = SilverSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SilverSpacing.m),
    ) {
        when (navIcon) {
            NavIcon.BACK -> IconCircleButton(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.nav_back), onNavigate)
            NavIcon.CLOSE -> IconCircleButton(painterResource(R.drawable.ic_close), stringResource(R.string.nav_close), onNavigate)
            null -> Unit
        }
        Text(
            title.orEmpty(),
            style = SilverTheme.type.section.copy(fontSize = SilverTheme.type.section.fontSize * 1.1f),
            color = SilverTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(if (title != null) Modifier.semantics { heading() } else Modifier),
        )
        actions()
    }
}

/**
 * A scrolling page of cards with the screen's side padding, 16 dp between items, and room at the
 * bottom for the navigation bar plus [bottomSpace] (for a floating button).
 */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    bottomSpace: Dp = SilverSpacing.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenContentPadding(bottomSpace)),
        verticalArrangement = Arrangement.spacedBy(SilverSpacing.m),
        content = content,
    )
}

/** Side padding plus the navigation bar and [bottomSpace] below, for scrolling screen content. */
@Composable
fun screenContentPadding(bottomSpace: Dp = SilverSpacing.l, top: Dp = SilverSpacing.xs): PaddingValues {
    val nav = WindowInsets.navigationBars.asPaddingValues()
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = SilverSpacing.screen + nav.calculateStartPadding(direction),
        end = SilverSpacing.screen + nav.calculateEndPadding(direction),
        top = top,
        bottom = nav.calculateBottomPadding() + bottomSpace,
    )
}

/** Shown while a screen's data loads from the database (usually a moment). */
@Composable
fun BoxScope.ScreenLoading() {
    CircularProgressIndicator(Modifier.align(Alignment.Center), color = SilverTheme.colors.accentInk)
}

/** The pair a screen was opened for no longer exists (deleted elsewhere, or archived for a form that needs it). */
@Composable
fun PairNotFound() {
    ScreenColumn {
        SilverCard { Text(stringResource(R.string.form_not_found), style = SilverTheme.type.body, color = SilverTheme.colors.textPrimary) }
    }
}
