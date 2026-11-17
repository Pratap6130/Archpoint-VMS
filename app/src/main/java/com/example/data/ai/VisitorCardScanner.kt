package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class ScannedVisitorCard(
  val name: String = "",
  val company: String = "",
  val mobile: String = "",
  val email: String = "",
  val jobProfile: String = "",
  val remark: String = "",
) {
  val hasAnyDetails: Boolean
    get() =
      name.isNotBlank() ||
        company.isNotBlank() ||
        mobile.isNotBlank() ||
        email.isNotBlank() ||
        jobProfile.isNotBlank() ||
        remark.isNotBlank()

  fun resolvedRemark(): String {
    val parts =
      listOfNotNull(
        jobProfile.takeIf { it.isNotBlank() },
        remark.takeIf { it.isNotBlank() && !it.equals(jobProfile, ignoreCase = true) },
      )
    return parts.joinToString(" · ").take(300)
  }
}

class VisitorCardScanner {
  private val cardSchema =
    Schema.obj(
      properties =
        mapOf(
          "name" to
            Schema.string(
              description = "Full name of the person on the visiting/business card, or empty string if absent",
            ),
          "company" to
            Schema.string(
              description = "Company, firm, or organization name on the card, or empty string if absent",
            ),
          "mobile" to
            Schema.string(
              description = "Primary mobile or phone number from the card (digits and optional leading +), or empty string if absent",
            ),
          "email" to
            Schema.string(
              description = "Email address on the card, or empty string if absent",
            ),
          "jobProfile" to
            Schema.string(
              description = "Job title, role, or designation (e.g. Architect, Director, Sales Manager), or empty string if absent",
            ),
          "remark" to
            Schema.string(
              description = "Short helpful context such as city, office branch, or website if clearly printed, or empty string",
            ),
        ),
      optionalProperties = listOf("company", "mobile", "email", "jobProfile", "remark"),
    )

  private val model by lazy {
    val config = generationConfig {
      responseMimeType = "application/json"
      responseSchema = cardSchema
      temperature = 0.1f
    }
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = config,
    )
  }

  suspend fun loadScaledCardBitmap(
    context: Context,
    imageUri: Uri,
    maxDimensionPx: Int = 1440,
  ): Bitmap =
    withContext(Dispatchers.IO) {
      val appContext = context.applicationContext
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(appContext.contentResolver, imageUri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          decoder.isMutableRequired = false
          val width = info.size.width
          val height = info.size.height
          val maxSide = maxOf(width, height)
          if (maxSide > maxDimensionPx && maxSide > 0) {
            val scale = maxDimensionPx.toFloat() / maxSide.toFloat()
            decoder.setTargetSize(
              (width * scale).toInt().coerceAtLeast(1),
              (height * scale).toInt().coerceAtLeast(1),
            )
          }
        }
      } else {
        val boundsOpts =
          BitmapFactory.Options().apply {
            inJustDecodeBounds = true
          }
        appContext.contentResolver.openInputStream(imageUri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        var sampleSize = 1
        var maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        while (maxSide / sampleSize > maxDimensionPx) {
          sampleSize *= 2
        }
        val decodeOpts =
          BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
          }
        appContext.contentResolver.openInputStream(imageUri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalStateException("Could not open the selected card image.")
      }
    }

  suspend fun extractFromBitmap(bitmap: Bitmap): Result<ScannedVisitorCard> =
    try {
      val response =
        withTimeout(60_000L) {
          model.generateContent(
            content {
              image(bitmap)
              text(
                "Extract the visitor's contact details from this business/visiting card image. " +
                  "Return accurate values from what is visibly printed on the card. " +
                  "Use empty strings for any field not clearly present on the card."
              )
            }
          )
        }
      val jsonString =
        response.text?.takeIf { it.isNotBlank() }
          ?: throw IllegalStateException("Card scanner returned an empty response.")
      val parsed = parseCardExtractionJson(jsonString)
      if (!parsed.hasAnyDetails) {
        Result.failure(
          IllegalStateException("No readable visitor details found on this image. Please try a clearer photo.")
        )
      } else {
        Result.success(parsed)
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Visitor card scan timed out", e)
      Result.failure(IllegalStateException("Card scan timed out after 60 seconds. Please try again."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Visitor card scan failed", e)
      Result.failure(e)
    }

  companion object {
    private const val TAG = "VisitorCardScanner"

    fun parseCardExtractionJson(jsonString: String): ScannedVisitorCard {
      val json = JSONObject(jsonString)
      return ScannedVisitorCard(
        name = json.optString("name", "").trim().take(100),
        company = json.optString("company", "").trim().take(100),
        mobile = normalizeScannedPhone(json.optString("mobile", "")),
        email = json.optString("email", "").trim().take(120),
        jobProfile = json.optString("jobProfile", "").trim().take(100),
        remark = json.optString("remark", "").trim().take(200),
      )
    }

    fun normalizeScannedPhone(raw: String): String {
      val digits = raw.filter { it.isDigit() }
      if (digits.isEmpty()) return ""
      val stripped =
        when {
          digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
          digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
          else -> digits
        }
      return stripped.take(15)
    }
  }
}
