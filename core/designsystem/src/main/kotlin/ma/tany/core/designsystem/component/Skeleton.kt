package ma.tany.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.TanyTheme

/** Shimmering placeholder fill (fast 1.1 s sweep, low contrast — reads as « content is coming », not as noise). */
fun Modifier.tanyShimmer(shape: Shape? = null): Modifier = composed {
    val colors = TanyTheme.colors
    val transition = rememberInfiniteTransition(label = "tany-shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing), RepeatMode.Restart),
        label = "tany-shimmer-progress",
    )
    val brush = Brush.linearGradient(
        colors = listOf(colors.skeleton, colors.skeletonHighlight, colors.skeleton),
        start = Offset(progress * 600f, 0f),
        end = Offset(progress * 600f + 600f, 300f),
    )
    (if (shape != null) clip(shape) else this).background(brush)
}

/** One skeleton bone. */
@Composable
fun TanySkeletonBlock(modifier: Modifier = Modifier, height: Dp = 14.dp, shape: Shape = TanyTheme.radii.small) {
    Box(
        modifier
            .height(height)
            .tanyShimmer(shape),
    )
}

/** Skeleton of one list card (optional media tile, two text lines, a chip). */
@Composable
fun TanyCardSkeleton(modifier: Modifier = Modifier, withMedia: Boolean = true) {
    TanyCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (withMedia) TanySkeletonBlock(Modifier.size(64.dp), height = 64.dp, shape = TanyTheme.radii.large)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TanySkeletonBlock(Modifier.fillMaxWidth(0.35f), height = 20.dp, shape = TanyTheme.radii.pill)
                TanySkeletonBlock(Modifier.fillMaxWidth(0.8f), height = 16.dp)
                TanySkeletonBlock(Modifier.fillMaxWidth(0.55f), height = 12.dp)
            }
        }
    }
}

/**
 * Operational screen skeleton: optional header block (metrics / hero), then [rows] cards. Announced once as
 * « Chargement… » to TalkBack (the bones themselves are silent).
 */
@Composable
fun TanyListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    withMedia: Boolean = true,
    header: Boolean = false,
) {
    val description = stringResource(R.string.tany_state_loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header) {
            TanySkeletonBlock(Modifier.fillMaxWidth(), height = 132.dp, shape = TanyTheme.radii.cardShape)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { TanySkeletonBlock(Modifier.weight(1f), height = 72.dp, shape = TanyTheme.radii.large) }
            }
            TanySkeletonBlock(Modifier.fillMaxWidth(0.3f).padding(top = 8.dp), height = 14.dp)
        }
        repeat(rows) { TanyCardSkeleton(withMedia = withMedia) }
    }
}

/** Skeleton for a detail screen (hero media, title, two cards). */
@Composable
fun TanyDetailSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.tany_state_loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = description }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TanySkeletonBlock(Modifier.fillMaxWidth(), height = 120.dp, shape = TanyTheme.radii.cardShape)
        TanySkeletonBlock(Modifier.fillMaxWidth(0.6f), height = 24.dp)
        TanySkeletonBlock(Modifier.fillMaxWidth(0.4f), height = 14.dp)
        TanySkeletonBlock(Modifier.fillMaxWidth(), height = 140.dp, shape = TanyTheme.radii.cardShape)
        TanySkeletonBlock(Modifier.fillMaxWidth(), height = 220.dp, shape = TanyTheme.radii.cardShape)
    }
}
