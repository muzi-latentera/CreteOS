package com.gamelaunch.frontend.pocket.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Default accent colour when no artwork is available or palette extraction fails. */
val DefaultAccentColor = Color(0xFF4D7FFF)

/**
 * Extract the dominant colour from an image using the Palette API.
 * Returns [DefaultAccentColor] if extraction fails.
 */
suspend fun extractDominantColor(bitmap: Bitmap): Color = withContext(Dispatchers.Default) {
    try {
        val palette = Palette.from(bitmap).generate()
        // Prefer vibrant, then muted, then dominant swatch
        val swatch = palette.vibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
        swatch?.rgb?.let { Color(it) } ?: DefaultAccentColor
    } catch (e: Exception) {
        DefaultAccentColor
    }
}

/**
 * Load an image from a path or URL and extract its dominant colour.
 */
suspend fun extractDominantColorFromPath(
    context: Context,
    path: String?
): Color {
    if (path.isNullOrBlank()) return DefaultAccentColor
    
    return withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val data = if (path.startsWith("http")) path else File(path)
            val request = ImageRequest.Builder(context)
                .data(data)
                .allowHardware(false) // Required for Palette extraction
                .build()
            
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    extractDominantColor(bitmap)
                } else {
                    DefaultAccentColor
                }
            } else {
                DefaultAccentColor
            }
        } catch (e: Exception) {
            DefaultAccentColor
        }
    }
}

/**
 * Composable that extracts and remembers the dominant colour from artwork.
 * Automatically updates when [artworkPath] changes.
 */
@Composable
fun rememberDominantColor(artworkPath: String?): Color {
    val context = LocalContext.current
    var color by remember { mutableStateOf(DefaultAccentColor) }
    
    LaunchedEffect(artworkPath) {
        color = extractDominantColorFromPath(context, artworkPath)
    }
    
    return color
}
