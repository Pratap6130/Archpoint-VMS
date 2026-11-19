package com.example.data.calendar

import com.example.data.model.Appointment
import com.example.util.DeskFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class GoogleCalendarEventItem(
  val id: String,
  val summary: String,
  val description: String = "",
  val location: String = "",
  val startMillis: Long,
  val endMillis: Long,
  val isAllDay: Boolean = false,
  val htmlLink: String = "",
  val attendeesCount: Int = 0,
  val archpointAppointmentId: String = "",
  val startDayIso: String = "",
  val startTimeLabel: String = "",
)

data class CreatedCalendarEvent(
  val eventId: String,
  val htmlLink: String,
)

sealed interface CalendarApiResult<out T> {
  data class Success<T>(val data: T) : CalendarApiResult<T>
  data object AuthExpired : CalendarApiResult<Nothing>
  data class Failure(val message: String, val code: Int = -1) : CalendarApiResult<Nothing>
}

object GoogleCalendarJson {
  private val dateTimeNoTzRegex =
    Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?$")

  fun formatRfc3339(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
    val fmt =
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        this.timeZone = timeZone
      }
    val offsetMs = timeZone.getOffset(millis)
    val suffix =
      if (offsetMs == 0) {
        "Z"
      } else {
        val sign = if (offsetMs >= 0) "+" else "-"
        val totalMinutes = abs(offsetMs) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        String.format(Locale.US, "%s%02d:%02d", sign, hours, minutes)
      }
    return fmt.format(Date(millis)) + suffix
  }

  fun parseRfc3339Millis(raw: String, fallbackZone: TimeZone = TimeZone.getDefault()): Long? {
    val clean = raw.trim()
    if (clean.isEmpty()) return null
    if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(clean)) {
      val df =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
          isLenient = false
          timeZone = fallbackZone
        }
      return runCatching { df.parse(clean)?.time }.getOrNull()
    }
    val match = dateTimeNoTzRegex.find(clean) ?: return null
    val base = match.groupValues[1]
    val tzPart = match.groupValues[2]
    val normalizedTz =
      when {
        tzPart.isEmpty() -> null
        tzPart == "Z" -> "UTC"
        tzPart.contains(":") -> "GMT$tzPart"
        else -> "GMT${tzPart.substring(0, 3)}:${tzPart.substring(3)}"
      }
    val df =
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        isLenient = false
        timeZone = normalizedTz?.let { TimeZone.getTimeZone(it) } ?: fallbackZone
      }
    return runCatching { df.parse(base)?.time }.getOrNull()
  }

  fun buildEventRequestBody(
    appointment: Appointment,
    durationMinutes: Int = 45,
    timeZone: TimeZone = TimeZone.getDefault(),
  ): String {
    val startMs =
      appointment.scheduledAtMillis.takeIf { it > 0L }
        ?: DeskFormatters.parseScheduledMillis(
          appointment.scheduledDateIso,
          appointment.scheduledTimeLabel,
        ).takeIf { it > 0L }
        ?: System.currentTimeMillis()
    val endMs = startMs + (durationMinutes.coerceIn(15, 240) * 60_000L)

    val purposeLabel = appointment.purpose.trim().ifBlank { "Meeting" }
    val summary = "${appointment.visitorName.trim()} — $purposeLabel"
    val location = appointment.location.trim().ifBlank { appointment.hostCabin.trim() }

    val descParts = mutableListOf<String>()
    appointment.hostShownAs.trim().takeIf { it.isNotBlank() }?.let {
      descParts += "Host: $it"
    }
    appointment.visitorCompany.trim().takeIf { it.isNotBlank() }?.let {
      descParts += "Company: $it"
    }
    val digits =
      appointment.visitorMobile.filter { it.isDigit() }.takeUnless { it == "919999999999" }.orEmpty()
    if (digits.length >= 6) {
      descParts += "Visitor mobile: ${appointment.visitorMobile.trim()}"
    }
    appointment.visitorEmail.trim().takeIf { it.isNotBlank() }?.let {
      descParts += "Visitor email: $it"
    }
    appointment.notes.trim().takeIf { it.isNotBlank() }?.let {
      descParts += "Notes: $it"
    }
    descParts += "Booked via ArchPoint Visitors"

    val attendeeEmails =
      listOf(appointment.hostEmail, appointment.visitorEmail)
        .map { it.trim().lowercase() }
        .filter { it.contains("@") && it.length >= 5 }
        .distinct()

    val root = JSONObject()
    root.put("summary", summary)
    if (location.isNotBlank()) {
      root.put("location", location)
    }
    root.put("description", descParts.joinToString("\n"))
    root.put(
      "start",
      JSONObject().apply {
        put("dateTime", formatRfc3339(startMs, timeZone))
        put("timeZone", timeZone.id)
      },
    )
    root.put(
      "end",
      JSONObject().apply {
        put("dateTime", formatRfc3339(endMs, timeZone))
        put("timeZone", timeZone.id)
      },
    )
    if (attendeeEmails.isNotEmpty()) {
      val attendeesArr = JSONArray()
      attendeeEmails.forEach { email ->
        attendeesArr.put(JSONObject().put("email", email))
      }
      root.put("attendees", attendeesArr)
    }
    if (appointment.id.isNotBlank()) {
      root.put(
        "extendedProperties",
        JSONObject().put(
          "private",
          JSONObject().apply {
            put("archpointAppointmentId", appointment.id)
            put("archpointSource", "archpoint_visitors")
          },
        ),
      )
    }
    return root.toString()
  }

  fun parseCreatedEventResponse(rawJson: String): CreatedCalendarEvent? =
    runCatching {
      val obj = JSONObject(rawJson)
      val id = obj.optString("id", "").trim()
      if (id.isEmpty()) return null
      CreatedCalendarEvent(
        eventId = id,
        htmlLink = obj.optString("htmlLink", "").trim(),
      )
    }.getOrNull()

  fun parseCalendarEventsResponse(
    rawJson: String,
    timeZone: TimeZone = TimeZone.getDefault(),
  ): List<GoogleCalendarEventItem> {
    val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
    val items = root.optJSONArray("items") ?: return emptyList()
    val dateOnlyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = timeZone }
    val timeFmt = SimpleDateFormat("h:mm a", Locale.US).apply { this.timeZone = timeZone }
    val results = mutableListOf<GoogleCalendarEventItem>()

    for (i in 0 until items.length()) {
      val item = items.optJSONObject(i) ?: continue
      if (item.optString("status", "").equals("cancelled", ignoreCase = true)) continue
      val id = item.optString("id", "").trim()
      if (id.isEmpty()) continue

      val startObj = item.optJSONObject("start") ?: continue
      val endObj = item.optJSONObject("end")
      val startDateTime = startObj.optString("dateTime", "").trim()
      val startDateOnly = startObj.optString("date", "").trim()
      val isAllDay = startDateTime.isEmpty() && startDateOnly.isNotEmpty()

      val startMillis =
        if (startDateTime.isNotEmpty()) {
          parseRfc3339Millis(startDateTime, timeZone)
        } else {
          parseRfc3339Millis(startDateOnly, timeZone)
        } ?: continue

      val endDateTime = endObj?.optString("dateTime", "")?.trim().orEmpty()
      val endDateOnly = endObj?.optString("date", "")?.trim().orEmpty()
      val endMillis =
        when {
          endDateTime.isNotEmpty() -> parseRfc3339Millis(endDateTime, timeZone)
          endDateOnly.isNotEmpty() -> parseRfc3339Millis(endDateOnly, timeZone)
          else -> null
        } ?: (startMillis + if (isAllDay) 86_400_000L else 45 * 60_000L)

      val privateProps =
        item.optJSONObject("extendedProperties")?.optJSONObject("private")
      val archId = privateProps?.optString("archpointAppointmentId", "")?.trim().orEmpty()

      val startDayIso =
        if (isAllDay) startDateOnly else dateOnlyFmt.format(Date(startMillis))
      val startTimeLabel =
        if (isAllDay) "All day" else timeFmt.format(Date(startMillis))

      val summary = item.optString("summary", "").trim().ifBlank { "(No title)" }
      results +=
        GoogleCalendarEventItem(
          id = id,
          summary = summary,
          description = item.optString("description", "").trim(),
          location = item.optString("location", "").trim(),
          startMillis = startMillis,
          endMillis = endMillis,
          isAllDay = isAllDay,
          htmlLink = item.optString("htmlLink", "").trim(),
          attendeesCount = item.optJSONArray("attendees")?.length() ?: 0,
          archpointAppointmentId = archId,
          startDayIso = startDayIso,
          startTimeLabel = startTimeLabel,
        )
    }

    return results.sortedBy { it.startMillis }
  }

  fun parseGoogleErrorMessage(rawBody: String, fallbackCode: Int): String =
    runCatching {
      val root = JSONObject(rawBody)
      root.optJSONObject("error")?.optString("message", "")?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull() ?: "Google Calendar request failed (HTTP $fallbackCode)"
}

class GoogleCalendarService(
  private val client: OkHttpClient = OkHttpClient(),
) {
  suspend fun fetchUpcomingEvents(
    accessToken: String,
    timeMinMillis: Long = DeskFormatters.startOfTodayMillis(),
    timeMaxMillis: Long = timeMinMillis + 21L * 86_400_000L,
    maxResults: Int = 25,
  ): CalendarApiResult<List<GoogleCalendarEventItem>> =
    withContext(Dispatchers.IO) {
      try {
        val tz = TimeZone.getDefault()
        val url =
          EVENTS_BASE_URL
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("timeMin", GoogleCalendarJson.formatRfc3339(timeMinMillis, tz))
            .addQueryParameter("timeMax", GoogleCalendarJson.formatRfc3339(timeMaxMillis, tz))
            .addQueryParameter("singleEvents", "true")
            .addQueryParameter("orderBy", "startTime")
            .addQueryParameter("maxResults", maxResults.coerceIn(1, 50).toString())
            .build()

        val request =
          Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
          val bodyStr = response.body?.string().orEmpty()
          when {
            response.code == 401 -> CalendarApiResult.AuthExpired
            !response.isSuccessful ->
              CalendarApiResult.Failure(
                message = GoogleCalendarJson.parseGoogleErrorMessage(bodyStr, response.code),
                code = response.code,
              )
            else ->
              CalendarApiResult.Success(
                GoogleCalendarJson.parseCalendarEventsResponse(bodyStr, tz),
              )
          }
        }
      } catch (e: Exception) {
        CalendarApiResult.Failure(e.localizedMessage ?: "Failed to load Google Calendar events")
      }
    }

  suspend fun upsertAppointmentEvent(
    accessToken: String,
    appointment: Appointment,
    existingEventId: String = appointment.calendarEventId,
  ): CalendarApiResult<CreatedCalendarEvent> =
    withContext(Dispatchers.IO) {
      try {
        val jsonBody = GoogleCalendarJson.buildEventRequestBody(appointment)
        val cleanExisting = existingEventId.trim()
        if (cleanExisting.isNotBlank()) {
          val patchUrl =
            "$EVENTS_BASE_URL/$cleanExisting"
              .toHttpUrl()
              .newBuilder()
              .addQueryParameter("sendUpdates", "all")
              .build()
          val patchReq =
            Request.Builder()
              .url(patchUrl)
              .header("Authorization", "Bearer $accessToken")
              .patch(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
              .build()
          client.newCall(patchReq).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            when {
              response.code == 401 -> return@withContext CalendarApiResult.AuthExpired
              response.isSuccessful -> {
                val created =
                  GoogleCalendarJson.parseCreatedEventResponse(bodyStr)
                    ?: CreatedCalendarEvent(cleanExisting, appointment.calendarHtmlLink)
                return@withContext CalendarApiResult.Success(created)
              }
              response.code == 404 || response.code == 410 -> {
                // Event deleted remotely on Google Calendar — create a fresh one below
              }
              else ->
                return@withContext CalendarApiResult.Failure(
                  message = GoogleCalendarJson.parseGoogleErrorMessage(bodyStr, response.code),
                  code = response.code,
                )
            }
          }
        }

        val createUrl =
          EVENTS_BASE_URL
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("sendUpdates", "all")
            .build()
        val createReq =
          Request.Builder()
            .url(createUrl)
            .header("Authorization", "Bearer $accessToken")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(createReq).execute().use { response ->
          val bodyStr = response.body?.string().orEmpty()
          when {
            response.code == 401 -> CalendarApiResult.AuthExpired
            !response.isSuccessful ->
              CalendarApiResult.Failure(
                message = GoogleCalendarJson.parseGoogleErrorMessage(bodyStr, response.code),
                code = response.code,
              )
            else -> {
              val created = GoogleCalendarJson.parseCreatedEventResponse(bodyStr)
              if (created != null) {
                CalendarApiResult.Success(created)
              } else {
                CalendarApiResult.Failure("Calendar event created, but missing event ID in response.")
              }
            }
          }
        }
      } catch (e: Exception) {
        CalendarApiResult.Failure(e.localizedMessage ?: "Failed to sync appointment to Google Calendar")
      }
    }

  suspend fun cancelEvent(
    accessToken: String,
    eventId: String,
  ): CalendarApiResult<Unit> =
    withContext(Dispatchers.IO) {
      val cleanId = eventId.trim()
      if (cleanId.isEmpty()) return@withContext CalendarApiResult.Success(Unit)
      try {
        val url =
          "$EVENTS_BASE_URL/$cleanId"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("sendUpdates", "all")
            .build()
        val request =
          Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
          val bodyStr = response.body?.string().orEmpty()
          when {
            response.code == 401 -> CalendarApiResult.AuthExpired
            response.isSuccessful || response.code == 404 || response.code == 410 ->
              CalendarApiResult.Success(Unit)
            else ->
              CalendarApiResult.Failure(
                message = GoogleCalendarJson.parseGoogleErrorMessage(bodyStr, response.code),
                code = response.code,
              )
          }
        }
      } catch (e: Exception) {
        CalendarApiResult.Failure(e.localizedMessage ?: "Failed to cancel Google Calendar event")
      }
    }

  companion object {
    private const val EVENTS_BASE_URL =
      "https://www.googleapis.com/calendar/v3/calendars/primary/events"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }
}
