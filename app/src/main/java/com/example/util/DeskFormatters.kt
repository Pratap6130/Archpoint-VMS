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

  fun formatIsoDate(millis: Long): String = isoDateFormat.format(Date(millis))

  fun currentYear(nowMillis: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return cal.get(Calendar.YEAR)
  }

  fun parseScheduledMillis(dateIso: String, timeLabel: String): Long {
    val cleanDate = dateIso.trim()
    val parsedDate = runCatching { isoDateFormat.parse(cleanDate) }.getOrNull() ?: return 0L
    val cal = Calendar.getInstance().apply {
      time = parsedDate
      set(Calendar.HOUR_OF_DAY, 10)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val cleanTime = timeLabel.trim().uppercase(Locale.US)
    val match = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM)?$").find(cleanTime)
    if (match != null) {
      var hour = match.groupValues[1].toIntOrNull() ?: 10
      val minute = match.groupValues[2].toIntOrNull() ?: 0
      val amPm = match.groupValues[3]
      if (amPm == "PM" && hour in 1..11) hour += 12
      if (amPm == "AM" && hour == 12) hour = 0
      cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
      cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
    }
    return cal.timeInMillis
  }

  fun formatAppointmentDayLabel(dateIso: String, nowMillis: Long = System.currentTimeMillis()): String {
    val parsed = runCatching { isoDateFormat.parse(dateIso.trim()) }.getOrNull() ?: return dateIso
    val todayIso = formatIsoDate(startOfTodayMillis(nowMillis))
    val tomorrowCal = Calendar.getInstance().apply {
      timeInMillis = startOfTodayMillis(nowMillis)
      add(Calendar.DAY_OF_YEAR, 1)
    }
    val tomorrowIso = formatIsoDate(tomorrowCal.timeInMillis)
    return when (dateIso.trim()) {
      todayIso -> "Today"
      tomorrowIso -> "Tomorrow"
      else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(parsed)
    }
  }

  fun parseMonthDayFromDob(rawDob: String): Pair<Int, Int>? {
    val clean = rawDob.trim()
    if (clean.isEmpty()) return null
    val slashMatch = Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?$").find(clean)
    if (slashMatch != null) {
      val first = slashMatch.groupValues[1].toIntOrNull() ?: return null
      val second = slashMatch.groupValues[2].toIntOrNull() ?: return null
      // Handles MM/dd/yyyy (from DatePicker) and dd/MM/yyyy if first > 12
      return if (first > 12 && second in 1..12) {
        second to first
      } else if (first in 1..12 && second in 1..31) {
        first to second
      } else {
        null
      }
    }
    val isoMatch = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(clean)
    if (isoMatch != null) {
      val m = isoMatch.groupValues[2].toIntOrNull() ?: return null
      val d = isoMatch.groupValues[3].toIntOrNull() ?: return null
      return if (m in 1..12 && d in 1..31) m to d else null
    }
    val textPatterns = listOf("dd MMM yyyy", "dd MMM", "MMM dd yyyy", "MMM dd")
    for (pattern in textPatterns) {
      val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(clean) }.getOrNull()
      if (parsed != null) {
        val cal = Calendar.getInstance().apply { time = parsed }
        return (cal.get(Calendar.MONTH) + 1) to cal.get(Calendar.DAY_OF_MONTH)
      }
    }
    return null
  }

  fun daysUntilBirthday(rawDob: String, nowMillis: Long = System.currentTimeMillis()): Int? {
    val (month1, day) = parseMonthDayFromDob(rawDob) ?: return null
    val todayCal = Calendar.getInstance().apply {
      timeInMillis = startOfTodayMillis(nowMillis)
    }
    val nextCal = Calendar.getInstance().apply {
      timeInMillis = todayCal.timeInMillis
      set(Calendar.MONTH, month1 - 1)
      set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
    }
    if (nextCal.timeInMillis < todayCal.timeInMillis) {
      nextCal.add(Calendar.YEAR, 1)
      nextCal.set(Calendar.MONTH, month1 - 1)
      nextCal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(nextCal.getActualMaximum(Calendar.DAY_OF_MONTH)))
    }
    return ((nextCal.timeInMillis - todayCal.timeInMillis) / 86_400_000L).toInt().coerceAtLeast(0)
  }

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
