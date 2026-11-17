package com.example.util

import com.example.data.model.Visit
import com.example.data.model.VisitStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DeskFormatters {
  private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
  private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  private val displayDateFieldFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
  private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

  fun formatLogDate(date: Date?): String = date?.let { dateFormat.format(it) } ?: "—"

  fun formatDisplayDateField(millis: Long): String = displayDateFieldFormat.format(Date(millis))

  fun formatTime(date: Date?): String = date?.let { timeFormat.format(it).lowercase(Locale.getDefault()) } ?: "—"

  fun formatCsvDate(date: Date?): String = date?.let { isoDateFormat.format(it) } ?: ""

  fun minutesBetween(startMillis: Long?, endMillis: Long): Long {
    if (startMillis == null || startMillis <= 0L) return 0L
    return ((endMillis - startMillis) / 60_000L).coerceAtLeast(0L)
  }

  fun startOfTodayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  fun endOfDayMillis(dayMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = dayMillis
      set(Calendar.HOUR_OF_DAY, 23)
      set(Calendar.MINUTE, 59)
      set(Calendar.SECOND, 59)
      set(Calendar.MILLISECOND, 999)
    }
    return cal.timeInMillis
  }

  fun daysAgoStartMillis(daysAgo: Int, nowMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      add(Calendar.DAY_OF_YEAR, -daysAgo)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
      parts.isEmpty() -> "?"
      parts.size == 1 -> parts.first().take(2).uppercase(Locale.getDefault())
      else -> "${parts.first().first()}${parts.last().first()}".uppercase(Locale.getDefault())
    }
  }

  fun ordinalVisit(visitNumber: Long): String {
    val n = visitNumber.coerceAtLeast(1L)
    val mod100 = n % 100L
    val suffix =
      if (mod100 in 11L..13L) {
        "th"
      } else {
        when (n % 10L) {
          1L -> "st"
          2L -> "nd"
          3L -> "rd"
          else -> "th"
        }
      }
    return "${n}${suffix} visit"
  }

  fun greetingForHour(hour24: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String =
    when {
      hour24 < 12 -> "Good morning"
      hour24 < 17 -> "Good afternoon"
      else -> "Good evening"
    }

  /**
   * Neutralizes spreadsheet formula injection for Excel / Google Sheets:
   * prefixes any cell starting with =, +, -, @, tab, or CR with an apostrophe (').
   */
  fun sanitizeCsvCell(raw: String): String {
    val trimmed = raw.replace("\r", " ").replace("\n", " ")
    val safe =
      if (trimmed.isNotEmpty() && trimmed.first() in charArrayOf('=', '+', '-', '@', '\t')) {
        "'$trimmed"
      } else {
        trimmed
      }
    val escaped = safe.replace("\"", "\"\"")
    return "\"$escaped\""
  }

  /**
   * Builds CSV strictly without Date of Birth (DOB is write-only and never included).
   */
  fun buildSafeVisitorCsv(visits: List<Visit>): String {
    val sb = StringBuilder()
    sb.appendLine("Date,Visitor,Company,Profile,Mobile,Met,Reason,Remark,In,Out,Status")
    visits.forEach { visit ->
      val arrived = visit.arrivedAt?.toDate()
      val inTime = visit.meetingStartedAt?.toDate() ?: arrived
      val outTime = visit.endedAt?.toDate()
      val row =
        listOf(
          formatCsvDate(arrived),
          visit.visitorName,
          visit.visitorCompany,
          visit.visitorJobProfile,
          visit.visitorMobile,
          visit.hostShownAs,
          visit.reason,
          visit.remark,
          if (visit.parsedStatus() == VisitStatus.WAITING) formatTime(arrived) else formatTime(inTime),
          formatTime(outTime),
          visit.status,
        ).joinToString(",") { sanitizeCsvCell(it) }
      sb.appendLine(row)
    }
    return sb.toString()
  }
}
