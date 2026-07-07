package com.gamelaunch.frontend.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Renders [content] as a QR code (white background so it scans in dark mode too). */
@Composable
fun QrCode(content: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? = remember(content) { generateQr(content, 512) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Pairing QR code",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(8.dp)
        )
    }
}

private fun generateQr(content: String, px: Int): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    for (x in 0 until px) {
        for (y in 0 until px) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bmp.asImageBitmap()
}.getOrNull()
