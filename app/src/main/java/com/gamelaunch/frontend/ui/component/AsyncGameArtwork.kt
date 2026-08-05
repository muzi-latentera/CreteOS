package com.gamelaunch.frontend.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

@Composable
fun AsyncGameArtwork(
    localPath: String?,
    remoteUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    packageName: String? = null
) {
    // Prefer a local file, falling back to the remote URL. We deliberately do NOT probe the
    // filesystem here (File.exists()/length()): that was main-thread I/O running on every tile
    // (re)composition while scrolling, and boxArtLocalPath is only written to Room after a
    // successful download, so a present-but-missing local file is a rare edge (e.g. the user
    // cleared their media dir) that the error state below handles gracefully.
    val data = remember(localPath, remoteUrl) { localPath ?: remoteUrl }

    // A stable, size-independent memory-cache key so a cover decoded once (e.g. prewarmed behind
    // the splash, or seen at a different tile size) is reused everywhere. Without this, Coil's
    // default key includes the request size, so the same art re-decodes per size and flashes the
    // grey placeholder before crossfading in.
    val context = LocalContext.current
    val request = remember(data) {
        ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey(data)
            .crossfade(true)
            .build()
    }

    // Plain AsyncImage (not SubcomposeAsyncImage): subcomposition per tile is a well-known
    // LazyGrid scroll-jank source on weak hardware. We render the loading/error visuals from a
    // lightweight onState-driven overlay instead of subcomposed slots — a single recomposition per
    // tile on load, versus subcomposing every tile every frame.
    var state by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    Box(modifier) {
        AsyncImage(
            model              = request,
            contentDescription = contentDescription,
            contentScale       = contentScale,
            modifier           = Modifier.fillMaxSize(),
            onState            = { state = it }
        )

        when (state) {
            is AsyncImagePainter.State.Success -> Unit  // art is drawn by AsyncImage
            is AsyncImagePainter.State.Error   -> ArtworkFallback(packageName)
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

/** Shown when box art fails to load: the app's launcher icon for Android apps, else a generic pad. */
@Composable
private fun ArtworkFallback(packageName: String?) {
    val context = LocalContext.current
    val appIconBitmap = remember(packageName) {
        if (packageName != null) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(width = 144, height = 144)
                    .asImageBitmap()
            }.getOrNull()
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (appIconBitmap != null) {
            Image(
                bitmap = appIconBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        } else {
            Icon(
                Icons.Default.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
