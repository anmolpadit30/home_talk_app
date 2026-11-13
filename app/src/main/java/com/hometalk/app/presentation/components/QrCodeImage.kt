package com.hometalk.app.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QrCodeImage(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, payload) {
        value = withContext(Dispatchers.Default) {
            encodeQrBitmap(payload, pixelSize = 256)
        }
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Pairing QR code",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
            else -> CircularProgressIndicator()
        }
    }
}

private fun encodeQrBitmap(payload: String, pixelSize: Int): Bitmap? {
    return try {
        val bits = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            pixelSize,
            pixelSize,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(pixelSize * pixelSize)
        for (y in 0 until pixelSize) {
            val offset = y * pixelSize
            for (x in 0 until pixelSize) {
                pixels[offset + x] = if (bits[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.RGB_565).also { bitmap ->
            bitmap.setPixels(pixels, 0, pixelSize, 0, 0, pixelSize, pixelSize)
        }
    } catch (error: Throwable) {
        // Catch Error too (e.g. OutOfMemoryError on low-RAM devices).
        null
    }
}
