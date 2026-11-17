package com.example.util

import com.example.data.model.StaffMember
import com.example.data.model.Visit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class TimeOfDayBucket(
  val label: String,
  val shortLabel: String,
  val startHour: Int,
  val endHourInclusive: Int,
) {
  EARLY_MORNING("Early Morning", "Before 9a", 0, 8),
  MORNING("Morning Peak", "9a – 12p", 9, 11),
  AFTERNOON("Afternoon", "12p – 4p", 12, 15),
  EVENING("Evening", "4p+", 16, 23);

  companion object {
    fun forHour(hour24: Int): TimeOfDayBucket =
      entries.firstOrNull { hour24 in it.startHour..it.endHourInclusive } ?: MORNING
  }
}

data class HourlyDepartmentTraffic(
  val hour24: Int,
  val hourLabel: String,
  val fullHourRange: String,
  val total: Int,
  val byDepartment: Map<String, Int>,
  val topReason: String,
)

data class DepartmentTrafficSummary(
  val department: String,
  val totalVisits: Int,
  val sharePercent: Int,
  val peakHourLabel: String,
  val peakPeriodLabel: String,
  val byPeriod: Map<TimeOfDayBucket, Int>,
  val topHosts: String,
)

data class PeriodTrafficSummary(
  val bucket: TimeOfDayBucket,
  val totalVisits: Int,
  val sharePercent: Int,
  val byDepartment: Map<String, Int>,
)

data class VisitorAnalyticsReport(
  val totalVisits: Int,
  val departments: List<String>,
  val hourlySeries: List<HourlyDepartmentTraffic>,
  val departmentSummaries: List<DepartmentTrafficSummary>,
  val periodSummaries: List<PeriodTrafficSummary>,
  val peakHour: HourlyDepartmentTraffic?,
  val peakPeriod: PeriodTrafficSummary?,
  val topDepartment: DepartmentTrafficSummary?,
  val busiestDayLabel: String,
  val reasonBreakdown: List<Pair<String, Int>>,
)

object VisitorAnalytics {
  val defaultOfficeTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

  fun resolveDepartment(
    visit: Visit,
    staffById: Map<String, StaffMember>,
    staffByNameLower: Map<String, StaffMember>,
  ): String {
    val byId = staffById[visit.hostStaffId]
    if (byId != null && byId.department.isNotBlank()) {
      return byId.department.trim()
    }
    val byShown = staffByNameLower[visit.hostShownAs.trim().lowercase()]
    if (byShown != null && byShown.department.isNotBlank()) {
      return byShown.department.trim()
    }
    return "General"
  }

  fun formatShortHour(hour24: Int): String {
    val h = ((hour24 % 24) + 24) % 24
    return when {
      h == 0 -> "12a"
      h < 12 -> "${h}a"
      h == 12 -> "12p"
      else -> "${h - 12}p"
    }
  }

  fun formatHourRange(hour24: Int): String {
    val h = ((hour24 % 24) + 24) % 24
    val amPm = if (h < 12) "AM" else "PM"
    val hour12 = when {
      h == 0 -> 12
      h <= 12 -> h
      else -> h - 12
    }
    return "$hour12:00 – $hour12:59 $amPm"
  }

  fun buildReport(
    visits: List<Visit>,
    staff: List<StaffMember>,
    departmentFilter: String? = null,
    timeZone: TimeZone = defaultOfficeTimeZone,
  ): VisitorAnalyticsReport {
    val staffById = staff.associateBy { it.id }
    val staffByNameLower = buildMap {
      staff.forEach { member ->
        if (member.shownAs.isNotBlank()) put(member.shownAs.trim().lowercase(), member)
        if (member.fullName.isNotBlank()) put(member.fullName.trim().lowercase(), member)
      }
    }

    val allAnnotated =
      visits.mapNotNull { visit ->
        val date = visit.arrivedAt?.toDate() ?: visit.waitStartedAt?.toDate() ?: return@mapNotNull null
        val cal = Calendar.getInstance(timeZone).apply { time = date }
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val dept = resolveDepartment(visit, staffById, staffByNameLower)
        AnnotatedVisit(
          visit = visit,
          department = dept,
          hour24 = hour24,
          period = TimeOfDayBucket.forHour(hour24),
          dayMillis = cal.timeInMillis,
        )
      }

    // All department names present in full dataset (for filter chips)
    val allDepartments =
      allAnnotated
        .groupingBy { it.department }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }

    val filtered =
      if (departmentFilter.isNullOrBlank()) {
        allAnnotated
      } else {
        allAnnotated.filter { it.department.equals(departmentFilter, ignoreCase = true) }
      }

    val total = filtered.size
    if (total == 0) {
      val emptyHours =
        (8..19).map { h ->
          HourlyDepartmentTraffic(
            hour24 = h,
            hourLabel = formatShortHour(h),
            fullHourRange = formatHourRange(h),
            total = 0,
            byDepartment = emptyMap(),
            topReason = "",
          )
        }
      return VisitorAnalyticsReport(
        totalVisits = 0,
        departments = allDepartments,
        hourlySeries = emptyHours,
        departmentSummaries = emptyList(),
        periodSummaries =
          TimeOfDayBucket.entries.map { bucket ->
            PeriodTrafficSummary(bucket = bucket, totalVisits = 0, sharePercent = 0, byDepartment = emptyMap())
          },
        peakHour = null,
        peakPeriod = null,
        topDepartment = null,
        busiestDayLabel = "—",
        reasonBreakdown = emptyList(),
      )
    }

    val minActiveHour = filtered.minOf { it.hour24 }.coerceAtMost(8)
    val maxActiveHour = filtered.maxOf { it.hour24 }.coerceAtLeast(18)
    val hourSpan = minActiveHour..maxActiveHour

    val activeDepartments =
      filtered
        .groupingBy { it.department }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }

    val hourlySeries =
      hourSpan.map { hour ->
        val inHour = filtered.filter { it.hour24 == hour }
        val deptMap =
          inHour
            .groupingBy { it.department }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }
        val topReason =
          inHour
            .mapNotNull { it.visit.reason.trim().takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
        HourlyDepartmentTraffic(
          hour24 = hour,
          hourLabel = formatShortHour(hour),
          fullHourRange = formatHourRange(hour),
          total = inHour.size,
          byDepartment = deptMap,
          topReason = topReason,
        )
      }

    val departmentSummaries =
      activeDepartments.map { dept ->
        val deptVisits = filtered.filter { it.department == dept }
        val byPeriod =
          TimeOfDayBucket.entries.associateWith { bucket ->
            deptVisits.count { it.period == bucket }
          }
        val peakHourInDept =
          deptVisits
            .groupingBy { it.hour24 }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val peakBucket = byPeriod.maxByOrNull { it.value }?.key
        val topHosts =
          deptVisits
            .groupingBy { it.visit.hostShownAs.ifBlank { "Host" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(2)
            .joinToString(", ") { "${it.key} (${it.value})" }
        DepartmentTrafficSummary(
          department = dept,
          totalVisits = deptVisits.size,
          sharePercent = ((deptVisits.size * 100f) / total).roundToInt(),
          peakHourLabel = peakHourInDept?.let { formatHourRange(it) } ?: "—",
          peakPeriodLabel = peakBucket?.shortLabel ?: "—",
          byPeriod = byPeriod,
          topHosts = topHosts,
        )
      }

    val periodSummaries =
      TimeOfDayBucket.entries.map { bucket ->
        val inPeriod = filtered.filter { it.period == bucket }
        val byDept =
          inPeriod
            .groupingBy { it.department }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }
        PeriodTrafficSummary(
          bucket = bucket,
          totalVisits = inPeriod.size,
          sharePercent = ((inPeriod.size * 100f) / total).roundToInt(),
          byDepartment = byDept,
        )
      }

    val dayFormat = SimpleDateFormat("dd MMM", Locale.getDefault()).apply { this.timeZone = timeZone }
    val busiestDay =
      filtered
        .groupingBy { dayFormat.format(it.dayMillis) }
        .eachCount()
        .maxByOrNull { it.value }
    val busiestDayLabel =
      busiestDay?.let { "${it.key} · ${it.value} visits" } ?: "—"

    val reasonBreakdown =
      filtered
        .map { it.visit.reason.trim().ifBlank { "Meeting" } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(5)
        .map { it.key to it.value }

    return VisitorAnalyticsReport(
      totalVisits = total,
      departments = allDepartments,
      hourlySeries = hourlySeries,
      departmentSummaries = departmentSummaries,
      periodSummaries = periodSummaries,
      peakHour = hourlySeries.maxByOrNull { it.total }?.takeIf { it.total > 0 },
      peakPeriod = periodSummaries.maxByOrNull { it.totalVisits }?.takeIf { it.totalVisits > 0 },
      topDepartment = departmentSummaries.firstOrNull(),
      busiestDayLabel = busiestDayLabel,
      reasonBreakdown = reasonBreakdown,
    )
  }

  private data class AnnotatedVisit(
    val visit: Visit,
    val department: String,
    val hour24: Int,
    val period: TimeOfDayBucket,
    val dayMillis: Long,
  )
}
