package com.example

import com.example.data.calendar.GoogleCalendarJson
import com.example.data.model.Appointment
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class GoogleCalendarSyncTest {
  @Test
  fun formatAndParseRfc3339RoundTripsUtcAndAsiaKolkata() {
    val ist = TimeZone.getTimeZone("Asia/Kolkata")
    val utc = TimeZone.getTimeZone("UTC")
    val sampleMillis = 1_776_245_400_000L // 2026-04-15T09:30:00Z

    val formattedUtc = GoogleCalendarJson.formatRfc3339(sampleMillis, utc)
    assertEquals("2026-04-15T09:30:00Z", formattedUtc)
    assertEquals(sampleMillis, GoogleCalendarJson.parseRfc3339Millis(formattedUtc, utc))

    val formattedIst = GoogleCalendarJson.formatRfc3339(sampleMillis, ist)
    assertTrue(formattedIst.endsWith("+05:30"))
    assertEquals(sampleMillis, GoogleCalendarJson.parseRfc3339Millis(formattedIst, ist))

    val fractionalMillis = GoogleCalendarJson.parseRfc3339Millis("2026-04-15T09:30:00.250Z", utc)
    assertEquals(sampleMillis, fractionalMillis)
  }

  @Test
  fun buildEventRequestBodyIncludesSummaryLocationAttendeesAndPrivateMetadata() {
    val tz = TimeZone.getTimeZone("Asia/Kolkata")
    val startMs = 1_776_245_400_000L
    val appt =
      Appointment(
        id = "appt_123abc",
        visitorName = "Neha Kapoor",
        visitorMobile = "9876543210",
        visitorCompany = "Kapoor Interiors",
        visitorEmail = "Neha@Kapoor.in",
        purpose = "Site Review",
        location = "Studio (3rd Floor)",
        scheduledDateIso = "2026-04-15",
        scheduledTimeLabel = "03:00 PM",
        scheduledAtMillis = startMs,
        notes = "Bring marble swatches",
        hostStaffId = "s_amit",
        hostShownAs = "Amit",
        hostEmail = "amit@archpoint.in",
      )

    val json = JSONObject(GoogleCalendarJson.buildEventRequestBody(appt, durationMinutes = 45, timeZone = tz))
    assertEquals("Neha Kapoor — Site Review", json.getString("summary"))
    assertEquals("Studio (3rd Floor)", json.getString("location"))
    assertTrue(json.getString("description").contains("Host: Amit"))
    assertTrue(json.getString("description").contains("9876543210"))
    assertTrue(json.getString("description").contains("Bring marble swatches"))

    val startObj = json.getJSONObject("start")
    val endObj = json.getJSONObject("end")
    assertEquals("Asia/Kolkata", startObj.getString("timeZone"))
    val parsedStart = GoogleCalendarJson.parseRfc3339Millis(startObj.getString("dateTime"), tz)!!
    val parsedEnd = GoogleCalendarJson.parseRfc3339Millis(endObj.getString("dateTime"), tz)!!
    assertEquals(45 * 60_000L, parsedEnd - parsedStart)

    val attendees = json.getJSONArray("attendees")
    assertEquals(2, attendees.length())
    assertEquals("amit@archpoint.in", attendees.getJSONObject(0).getString("email"))
    assertEquals("neha@kapoor.in", attendees.getJSONObject(1).getString("email"))

    val privateProps = json.getJSONObject("extendedProperties").getJSONObject("private")
    assertEquals("appt_123abc", privateProps.getString("archpointAppointmentId"))
  }

  @Test
  fun buildEventRequestBodyOmitsPlaceholderMobileAndEmptyAttendees() {
    val appt =
      Appointment(
        id = "",
        visitorName = "Walk-in Client",
        visitorMobile = "919999999999",
        visitorEmail = "",
        hostEmail = "",
        hostCabin = "Conference (2nd Floor)",
        scheduledDateIso = "2026-04-15",
        scheduledTimeLabel = "11:00 AM",
      )

    val json = JSONObject(GoogleCalendarJson.buildEventRequestBody(appt))
    assertFalse(json.has("attendees"))
    assertFalse(json.has("extendedProperties"))
    assertFalse(json.getString("description").contains("919999999999"))
    assertEquals("Conference (2nd Floor)", json.getString("location"))
  }

  @Test
  fun parseCalendarEventsResponseFiltersCancelledSortsAndParsesAllDay() {
    val utc = TimeZone.getTimeZone("UTC")
    val raw =
      """
      {
        "items": [
          {
            "id": "ev_late",
            "status": "confirmed",
            "summary": "Design Walkthrough",
            "location": "2nd Floor",
            "htmlLink": "https://calendar.google.com/event?eid=late",
            "start": { "dateTime": "2026-04-16T14:00:00Z" },
            "end": { "dateTime": "2026-04-16T15:00:00Z" },
            "attendees": [{ "email": "a@archpoint.in" }, { "email": "b@client.com" }],
            "extendedProperties": { "private": { "archpointAppointmentId": "appt_99" } }
          },
          {
            "id": "ev_cancelled",
            "status": "cancelled",
            "summary": "Cancelled Event",
            "start": { "dateTime": "2026-04-15T10:00:00Z" },
            "end": { "dateTime": "2026-04-15T11:00:00Z" }
          },
          {
            "id": "ev_allday",
            "status": "confirmed",
            "summary": "Site Expo",
            "start": { "date": "2026-04-15" },
            "end": { "date": "2026-04-16" }
          }
        ]
      }
      """.trimIndent()

    val events = GoogleCalendarJson.parseCalendarEventsResponse(raw, utc)
    assertEquals(2, events.size)
    assertEquals("ev_allday", events[0].id)
    assertTrue(events[0].isAllDay)
    assertEquals("All day", events[0].startTimeLabel)
    assertEquals("2026-04-15", events[0].startDayIso)

    assertEquals("ev_late", events[1].id)
    assertFalse(events[1].isAllDay)
    assertEquals("appt_99", events[1].archpointAppointmentId)
    assertEquals(2, events[1].attendeesCount)
    assertEquals("https://calendar.google.com/event?eid=late", events[1].htmlLink)
  }

  @Test
  fun parseCreatedEventResponseExtractsIdAndHtmlLink() {
    val parsed =
      GoogleCalendarJson.parseCreatedEventResponse(
        """{"id":"gcal_ev_42","htmlLink":"https://calendar.google.com/calendar/event?eid=42"}""",
      )
    assertNotNull(parsed)
    assertEquals("gcal_ev_42", parsed!!.eventId)
    assertEquals("https://calendar.google.com/calendar/event?eid=42", parsed.htmlLink)

    assertNull(GoogleCalendarJson.parseCreatedEventResponse("""{"summary":"missing id"}"""))
  }
}
