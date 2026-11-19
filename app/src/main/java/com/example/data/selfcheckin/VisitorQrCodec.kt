package com.example.data.selfcheckin

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.ai.VisitorCardScanner
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URI
import java.net.URLDecoder
import org.json.JSONObject

data class SelfCheckInPayload(
  val name: String = "",
  val mobile: String = "",
  val company: String = "",
  val email: String = "",
  val dob: String = "",
  val reason: String = "",
  val hostId: String = "",
  val hostName: String = "",
  val remark: String = "",
  val source: String = "QR",
) {
  val hasUsableDetails: Boolean
    get() =
      name.isNotBlank() ||
        mobile.isNotBlank() ||
        company.isNotBlank() ||
        email.isNotBlank()
}

object VisitorQrCodec {
  const val PASS_PREFIX = "ARCHPOINT_VISITOR:"

  fun encodeArchPointPass(payload: SelfCheckInPayload): String {
    val json =
      JSONObject().apply {
        if (payload.name.isNotBlank()) put("name", payload.name.trim().take(100))
        if (payload.mobile.isNotBlank()) {
          put("mobile", VisitorCardScanner.normalizeScannedPhone(payload.mobile))
        }
        if (payload.company.isNotBlank()) put("company", payload.company.trim().take(100))
        if (payload.email.isNotBlank()) put("email", payload.email.trim().take(120))
        if (payload.dob.isNotBlank()) put("dob", payload.dob.trim().take(20))
        if (payload.reason.isNotBlank()) put("reason", payload.reason.trim().take(60))
        if (payload.hostId.isNotBlank()) put("hostId", payload.hostId.trim().take(64))
        if (payload.hostName.isNotBlank()) put("host", payload.hostName.trim().take(80))
        if (payload.remark.isNotBlank()) put("remark", payload.remark.trim().take(300))
      }
    return "$PASS_PREFIX$json"
  }

  fun parseQrContent(rawInput: String): SelfCheckInPayload? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith(PASS_PREFIX, ignoreCase = true)) {
      val jsonPart = trimmed.substring(PASS_PREFIX.length).trim()
      return parseJsonPayload(jsonPart, source = "ArchPoint QR Pass")
    }

    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      parseJsonPayload(trimmed, source = "QR JSON")?.let { return it }
    }

    if (trimmed.contains("BEGIN:VCARD", ignoreCase = true)) {
      parseVCard(trimmed)?.let { return it }
    }

    if (trimmed.startsWith("MECARD:", ignoreCase = true)) {
      parseMeCard(trimmed)?.let { return it }
    }

    if (trimmed.startsWith("http://", ignoreCase = true) ||
      trimmed.startsWith("https://", ignoreCase = true) ||
      trimmed.startsWith("archpoint://", ignoreCase = true)
    ) {
      parseUrlQueryPayload(trimmed)?.let { return it }
    }

    val normalizedPhone = VisitorCardScanner.normalizeScannedPhone(trimmed)
    if (normalizedPhone.length >= 10 && trimmed.count { it.isLetter() } <= 3) {
      return SelfCheckInPayload(mobile = normalizedPhone, source = "Phone QR")
    }

    return null
  }

  fun parseJsonPayload(jsonText: String, source: String = "Self Check-In"): SelfCheckInPayload? =
    runCatching {
      val obj = JSONObject(jsonText)
      val name =
        firstNonBlank(
          obj.optString("name"),
          obj.optString("visitorName"),
          obj.optString("fullName"),
        ).take(100)
      val mobile =
        VisitorCardScanner.normalizeScannedPhone(
          firstNonBlank(
            obj.optString("mobile"),
            obj.optString("phone"),
            obj.optString("visitorMobile"),
          )
        )
      val company =
        firstNonBlank(
          obj.optString("company"),
          obj.optString("organization"),
          obj.optString("org"),
          obj.optString("visitorCompany"),
        ).take(100)
      val email =
        firstNonBlank(
          obj.optString("email"),
          obj.optString("visitorEmail"),
        ).take(120)
      val dob = obj.optString("dob", "").trim().take(20)
      val reason =
        firstNonBlank(
          obj.optString("reason"),
          obj.optString("purpose"),
          obj.optString("visitorType"),
        ).take(60)
      val hostId =
        firstNonBlank(
          obj.optString("hostId"),
          obj.optString("hostStaffId"),
        ).take(64)
      val hostName =
        firstNonBlank(
          obj.optString("host"),
          obj.optString("hostName"),
          obj.optString("personToMeet"),
        ).take(80)
      val jobProfile =
        firstNonBlank(
          obj.optString("profile"),
          obj.optString("jobProfile"),
          obj.optString("designation"),
        ).take(100)
      val rawRemark =
        firstNonBlank(
          obj.optString("remark"),
          obj.optString("notes"),
          obj.optString("note"),
        ).take(300)
      val remark =
        listOfNotNull(
          jobProfile.takeIf { it.isNotBlank() },
          rawRemark.takeIf { it.isNotBlank() && !it.equals(jobProfile, ignoreCase = true) },
        ).joinToString(" · ").take(300)

      SelfCheckInPayload(
        name = name,
        mobile = mobile,
        company = company,
        email = email,
        dob = dob,
        reason = reason,
        hostId = hostId,
        hostName = hostName,
        remark = remark,
        source = source,
      ).takeIf { it.hasUsableDetails }
    }.getOrNull()

  private fun parseVCard(raw: String): SelfCheckInPayload? {
    val unfolded =
      raw
        .replace("\r\n ", "")
        .replace("\n ", "")
        .replace("\r\n\t", "")
        .replace("\n\t", "")
    val lines = unfolded.lines()

    var fullName = ""
    var structuredName = ""
    var company = ""
    var mobile = ""
    var email = ""
    var title = ""
    var dob = ""
    var note = ""

    for (line in lines) {
      val colonIndex = line.indexOf(':')
      if (colonIndex <= 0) continue
      val keyPart = line.substring(0, colonIndex).uppercase()
      val baseKey = keyPart.substringBefore(';')
      val value = unescapeVCardValue(line.substring(colonIndex + 1).trim())
      if (value.isBlank()) continue

      when (baseKey) {
        "FN" -> if (fullName.isBlank()) fullName = value
        "N" -> if (structuredName.isBlank()) {
          val parts = value.split(';').map { it.trim() }.filter { it.isNotBlank() }
          structuredName =
            if (parts.size >= 2) "${parts[1]} ${parts[0]}".trim() else parts.firstOrNull().orEmpty()
        }
        "ORG" -> if (company.isBlank()) company = value.replace(';', ' ').trim()
        "TEL" -> {
          val cleaned = VisitorCardScanner.normalizeScannedPhone(value)
          if (cleaned.isNotBlank() && (mobile.isBlank() || keyPart.contains("CELL") || keyPart.contains("MOB"))) {
            mobile = cleaned
          }
        }
        "EMAIL" -> if (email.isBlank()) email = value
        "TITLE" -> if (title.isBlank()) title = value
        "BDAY" -> if (dob.isBlank()) dob = value
        "NOTE" -> if (note.isBlank()) note = value
      }
    }

    val resolvedName = fullName.ifBlank { structuredName }.take(100)
    val resolvedRemark =
      listOfNotNull(
        title.takeIf { it.isNotBlank() },
        note.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) },
      ).joinToString(" · ").take(300)

    return SelfCheckInPayload(
      name = resolvedName,
      mobile = mobile,
      company = company.take(100),
      email = email.take(120),
      dob = dob.take(20),
      remark = resolvedRemark,
      source = "vCard QR",
    ).takeIf { it.hasUsableDetails }
  }

  private fun parseMeCard(raw: String): SelfCheckInPayload? {
    val body = raw.removePrefix("MECARD:").removePrefix("mecard:")
    val fields = body.split(';')
    var name = ""
    var company = ""
    var mobile = ""
    var email = ""
    var dob = ""
    var note = ""

    for (entry in fields) {
      val idx = entry.indexOf(':')
      if (idx <= 0) continue
      val key = entry.substring(0, idx).trim().uppercase()
      val value = entry.substring(idx + 1).trim()
      if (value.isBlank()) continue
      when (key) {
        "N" -> name = value.split(',').reversed().joinToString(" ").trim()
        "ORG" -> company = value
        "TEL" -> if (mobile.isBlank()) mobile = VisitorCardScanner.normalizeScannedPhone(value)
        "EMAIL" -> if (email.isBlank()) email = value
        "BDAY" -> if (dob.isBlank()) dob = value
        "NOTE" -> if (note.isBlank()) note = value
      }
    }

    return SelfCheckInPayload(
      name = name.take(100),
      mobile = mobile,
      company = company.take(100),
      email = email.take(120),
      dob = dob.take(20),
      remark = note.take(300),
      source = "MECARD QR",
    ).takeIf { it.hasUsableDetails }
  }

  private fun parseUrlQueryPayload(url: String): SelfCheckInPayload? =
    runCatching {
      val uri = URI(url)
      val rawQuery = uri.rawQuery ?: return null
      val params = linkedMapOf<String, String>()
      rawQuery.split('&').forEach { pair ->
        val eq = pair.indexOf('=')
        if (eq > 0) {
          val k = URLDecoder.decode(pair.substring(0, eq), "UTF-8").trim().lowercase()
          val v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8").trim()
          if (k.isNotEmpty() && v.isNotEmpty()) {
            params[k] = v
          }
        }
      }
      val name = firstNonBlank(params["name"], params["visitorname"], params["fullname"]).take(100)
      val mobile =
        VisitorCardScanner.normalizeScannedPhone(
          firstNonBlank(params["mobile"], params["phone"], params["tel"])
        )
      val company = firstNonBlank(params["company"], params["org"], params["organization"]).take(100)
      val email = firstNonBlank(params["email"], params["mail"]).take(120)
      val dob = firstNonBlank(params["dob"], params["birthday"]).take(20)
      val reason = firstNonBlank(params["reason"], params["purpose"], params["type"]).take(60)
      val hostId = firstNonBlank(params["hostid"], params["staffid"]).take(64)
      val hostName = firstNonBlank(params["host"], params["hostname"], params["persontomeet"]).take(80)
      val remark = firstNonBlank(params["remark"], params["note"], params["notes"]).take(300)

      SelfCheckInPayload(
        name = name,
        mobile = mobile,
        company = company,
        email = email,
        dob = dob,
        reason = reason,
        hostId = hostId,
        hostName = hostName,
        remark = remark,
        source = "URL QR",
      ).takeIf { it.hasUsableDetails }
    }.getOrNull()

  fun renderQrBitmap(
    content: String,
    sizePx: Int = 512,
    darkColor: Int = Color.parseColor("#1B2128"),
    lightColor: Int = Color.WHITE,
  ): Bitmap? {
    if (content.isBlank() || sizePx <= 0) return null
    return runCatching {
      val hints =
        mapOf(
          EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
          EncodeHintType.MARGIN to 2,
          EncodeHintType.CHARACTER_SET to "UTF-8",
        )
      val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val pixels = IntArray(sizePx * sizePx)
      for (y in 0 until sizePx) {
        val offset = y * sizePx
        for (x in 0 until sizePx) {
          pixels[offset + x] = if (matrix.get(x, y)) darkColor else lightColor
        }
      }
      Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
      }
    }.getOrNull()
  }

  private fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

  private fun unescapeVCardValue(value: String): String =
    value
      .replace("\\n", " ")
      .replace("\\N", " ")
      .replace("\\,", ",")
      .replace("\\;", ";")
      .replace("\\\\", "\\")
}
