package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object StaffPhotoCodec {
  const val MAX_PHOTO_DATA_URI_CHARS = 120_000
  private const val DATA_URI_PREFIX = "data:image/jpeg;base64,"
  private const val TARGET_MAX_DIMENSION = 240

  suspend fun encodePickedPhotoToDataUri(
    context: Context,
    imageUri: Uri,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: error("Could not read selected image.")

        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        require(rawW > 0 && rawH > 0) { "Invalid image file." }

        var sampleSize = 1
        while (max(rawW / sampleSize, rawH / sampleSize) > TARGET_MAX_DIMENSION * 2) {
          sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded =
          context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
          } ?: error("Could not decode selected image.")

        val croppedSquare = cropCenterSquareAndScale(decoded, TARGET_MAX_DIMENSION)
        if (croppedSquare !== decoded) {
          decoded.recycle()
        }

        val encoded = compressToBoundedDataUri(croppedSquare)
        croppedSquare.recycle()
        encoded
      }
    }

  private fun cropCenterSquareAndScale(source: Bitmap, maxDim: Int): Bitmap {
    val side = min(source.width, source.height).coerceAtLeast(1)
    val left = (source.width - side) / 2
    val top = (source.height - side) / 2
    val square =
      if (source.width == side && source.height == side) {
        source
      } else {
        Bitmap.createBitmap(source, left, top, side, side)
      }
    if (square.width <= maxDim) return square
    val scaled = Bitmap.createScaledBitmap(square, maxDim, maxDim, true)
    if (scaled !== square && square !== source) {
      square.recycle()
    }
    return scaled
  }

  private fun compressToBoundedDataUri(bitmap: Bitmap): String {
    val qualities = intArrayOf(80, 68, 56, 42, 30)
    for (quality in qualities) {
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
      val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
      val dataUri = DATA_URI_PREFIX + base64
      if (dataUri.length <= MAX_PHOTO_DATA_URI_CHARS) {
        return dataUri
      }
    }
    error("Selected photo is too large even after compression.")
  }

  fun decodeDataUriToImageBitmap(photoUrl: String): ImageBitmap? {
    val clean = photoUrl.trim()
    if (!clean.startsWith("data:image/") || !clean.contains(";base64,")) return null
    val base64Part = clean.substringAfter(";base64,", "")
    if (base64Part.isBlank()) return null
    return runCatching {
      val bytes = Base64.decode(base64Part, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
  }
}
