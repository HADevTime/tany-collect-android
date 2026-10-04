package ma.tany.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.TanyDimens
import ma.tany.core.designsystem.theme.TanyTheme

/**
 * The ONLY way to show a product / Asset image (permanent TANY rule): a WHITE studio zone in BOTH Light and Dark
 * mode, image fitted (never cropped), never tinted or recolored (no color filter). Images have transparent or white
 * backgrounds server-side; the app provides the surface.
 *
 * @param url absolute URL (resolve the backend's relative path first) — `catalogImageUrl ?? image`.
 */
@Composable
fun ProductImageSurface(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    shape: Shape = TanyTheme.radii.large,
    padding: Dp = 12.dp,
) {
    val colors = TanyTheme.colors
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(colors.media)
            .border(TanyDimens.BorderWidth, colors.mediaBorder, shape)
            .testTag(ProductImageSurfaceTestTag),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null || failed) {
            Icon(
                painterResource(R.drawable.ic_tany_image),
                contentDescription = stringResource(R.string.tany_cd_image_unavailable),
                tint = PlaceholderOnWhite,
                modifier = Modifier.size(32.dp),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription ?: stringResource(R.string.tany_cd_product_image),
                contentScale = ContentScale.Fit,
                colorFilter = null,
                onError = { failed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

const val ProductImageSurfaceTestTag = "ProductImageSurface"

/** Placeholder glyph tuned for the white studio (identical in both themes). */
private val PlaceholderOnWhite = Color(0xFFB8B8BC)
