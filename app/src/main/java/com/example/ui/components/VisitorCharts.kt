package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.StaffMember
import com.example.data.model.Visit
import com.example.ui.theme.ArchAmber
import com.example.ui.theme.ArchAmberBright
import com.example.ui.theme.FreeGreen
import com.example.ui.theme.FreeGreenBright
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MeetingBlueBright
import com.example.ui.theme.PrimaryActionBlue
import com.example.util.DepartmentTrafficSummary
import com.example.util.HourlyDepartmentTraffic
import com.example.util.PeriodTrafficSummary
import com.example.util.TimeOfDayBucket
import com.example.util.VisitorAnalytics
import com.example.util.VisitorAnalyticsReport
import kotlin.math.roundToInt

private enum class HourlyChartStyle(val label: String) {
  AREA("Area"),
  STACKED_BARS("Bars"),
}

@Composable
fun departmentChartColor(department: String): Color {
  val isDark = LocalIsDarkTheme.current
  val key = department.trim().lowercase()
  return when {
    "management" in key -> if (isDark) MeetingBlueBright else PrimaryActionBlue
    "arch point group" in key -> if (isDark) ArchAmberBright else Color(0xFFD97706)
    "operations" in key -> if (isDark) FreeGreenBright else FreeGreen
    "design" in key -> if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
    "hr" in key -> if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
    "wellness" in key -> if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
    "technology" in key -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    "front" in key -> if (isDark) Color(0xFFFDBA74) else Color(0xFFEA580C)
    else -> {
      val palette =
        if (isDark) {
          listOf(Color(0xFF818CF8), Color(0xFF34D399), Color(0xFFF472B6), Color(0xFFFBBF24))
        } else {
          listOf(Color(0xFF4F46E5), Color(0xFF059669), Color(0xFFDB2777), Color(0xFFD97706))
        }
      palette[(department.hashCode().and(0x7FFFFFFF)) % palette.size]
    }
  }
}

@Composable
fun periodChartColor(bucket: TimeOfDayBucket): Color {
  val isDark = LocalIsDarkTheme.current
  return when (bucket) {
    TimeOfDayBucket.EARLY_MORNING -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    TimeOfDayBucket.MORNING -> if (isDark) ArchAmberBright else Color(0xFFD97706)
    TimeOfDayBucket.AFTERNOON -> if (isDark) MeetingBlueBright else PrimaryActionBlue
    TimeOfDayBucket.EVENING -> if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManagementAnalyticsDashboard(
  visits: List<Visit>,
  staff: List<StaffMember>,
) {
  var selectedDepartment by remember { mutableStateOf<String?>(null) }

  val report =
    remember(visits, staff, selectedDepartment) {
      VisitorAnalytics.buildReport(
        visits = visits,
        staff = staff,
        departmentFilter = selectedDepartment,
      )
    }

  Column(
    modifier = Modifier.fillMaxWidth().testTag("management_analytics_dashboard"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (report.departments.isNotEmpty()) {
      GlassCard(contentPadding = PaddingValues(14.dp)) {
        Text(
          text = "Filter by department",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DepartmentFilterChip(
            label = "All Depts",
            selected = selectedDepartment == null,
            dotColor = MaterialTheme.colorScheme.primary,
            onClick = { selectedDepartment = null },
            tag = "dept_filter_all",
          )
          report.departments.forEach { dept ->
            DepartmentFilterChip(
              label = dept,
              selected = selectedDepartment.equals(dept, ignoreCase = true),
              dotColor = departmentChartColor(dept),
              onClick = {
                selectedDepartment =
                  if (selectedDepartment.equals(dept, ignoreCase = true)) null else dept
              },
              tag = "dept_filter_${dept.lowercase().replace(" ", "_")}",
            )
          }
        }
      }
    }

    if (report.totalVisits == 0) {
      GlassCard {
        Column(
          modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Insights,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(38.dp),
          )
          Text(
            text = "No visitor traffic in this window",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Tap Last 30d or All above to analyze ArchPoint peak hours and department traffic.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
      return@Column
    }

    // Peak Activity KPI Cards (2x2)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      PeakInsightKpiCard(
        icon = Icons.Rounded.AccessTime,
        title = "Peak Hour",
        value = report.peakHour?.fullHourRange ?: "—",
        subtitle = "${report.peakHour?.total ?: 0} visits",
        accent = if (LocalIsDarkTheme.current) ArchAmberBright else Color(0xFFD97706),
        modifier = Modifier.weight(1f),
      )
      PeakInsightKpiCard(
        icon = Icons.Rounded.TrendingUp,
        title = "Peak Window",
        value = report.peakPeriod?.bucket?.label ?: "—",
        subtitle = "${report.peakPeriod?.sharePercent ?: 0}% of traffic (${report.peakPeriod?.totalVisits ?: 0})",
        accent = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      PeakInsightKpiCard(
        icon = Icons.Rounded.Business,
        title = if (selectedDepartment == null) "Top Department" else "Selected Dept",
        value = report.topDepartment?.department ?: "—",
        subtitle = "${report.topDepartment?.totalVisits ?: 0} visits (${report.topDepartment?.sharePercent ?: 0}%)",
        accent = departmentChartColor(report.topDepartment?.department ?: "Management"),
        modifier = Modifier.weight(1f),
      )
      PeakInsightKpiCard(
        icon = Icons.Rounded.CalendarMonth,
        title = "Busiest Day",
        value = report.busiestDayLabel,
        subtitle = "${report.totalVisits} total analyzed",
        accent = if (LocalIsDarkTheme.current) FreeGreenBright else FreeGreen,
        modifier = Modifier.weight(1f),
      )
    }

    // 1. Hourly Visitor Traffic Trend Chart (Area + Stacked Bars with interactive Tooltip)
    HourlyTrafficTrendCard(
      report = report,
      selectedDepartment = selectedDepartment,
    )

    // 2. Time-of-Day Peak Windows + Department x Time Heatmap
    TimeOfDayAndHeatmapCard(report = report)

    // 3. Visitor Traffic by Department (Stacked by Time of Day)
    if (report.departmentSummaries.isNotEmpty()) {
      DepartmentStackedBreakdownCard(
        report = report,
        selectedDepartment = selectedDepartment,
        onSelectDepartment = { dept ->
          selectedDepartment =
            if (selectedDepartment.equals(dept, ignoreCase = true)) null else dept
        },
      )
    }

    // 4. Top Reasons / Visitor Types Mix
    if (report.reasonBreakdown.isNotEmpty()) {
      ReasonDistributionCard(report = report)
    }
  }
}

@Composable
private fun DepartmentFilterChip(
  label: String,
  selected: Boolean,
  dotColor: Color,
  onClick: () -> Unit,
  tag: String,
) {
  Surface(
    modifier =
      Modifier.clip(RoundedCornerShape(20.dp))
        .clickable(onClick = onClick)
        .testTag(tag),
    shape = RoundedCornerShape(20.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    border =
      BorderStroke(
        1.dp,
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      ),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier =
          Modifier.size(8.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.onPrimary else dotColor)
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun PeakInsightKpiCard(
  icon: ImageVector,
  title: String,
  value: String,
  subtitle: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  GlassInnerSurface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier =
            Modifier.size(26.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(accent.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp),
          )
        }
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = value,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = accent,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HourlyTrafficTrendCard(
  report: VisitorAnalyticsReport,
  selectedDepartment: String?,
) {
  var chartStyle by remember { mutableStateOf(HourlyChartStyle.AREA) }
  val series = report.hourlySeries
  val defaultSelectedIndex =
    remember(series) {
      val peak = series.maxByOrNull { it.total }
      if (peak != null) series.indexOf(peak).coerceAtLeast(0) else 0
    }
  var selectedIndex by remember(series) { mutableIntStateOf(defaultSelectedIndex) }
  val safeIndex = selectedIndex.coerceIn(0, (series.size - 1).coerceAtLeast(0))
  val activePoint = series.getOrNull(safeIndex)

  val animProgress by
    animateFloatAsState(
      targetValue = if (series.isNotEmpty()) 1f else 0f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f),
      label = "hourly_chart_anim",
    )

  val primaryLineColor =
    if (selectedDepartment != null) departmentChartColor(selectedDepartment)
    else MaterialTheme.colorScheme.primary
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
  val guidelineColor = if (LocalIsDarkTheme.current) ArchAmberBright else ArchAmber
  val surfaceBgColor = MaterialTheme.colorScheme.surface
  val deptColors = report.departments.associateWith { departmentChartColor(it) }

  GlassCard(contentPadding = PaddingValues(16.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Visitor Traffic by Time of Day",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = "Tap or slide across hours to inspect peak breakdown",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HourlyChartStyle.entries.forEach { style ->
          val active = chartStyle == style
          Surface(
            modifier =
              Modifier.clip(RoundedCornerShape(10.dp))
                .clickable { chartStyle = style }
                .testTag("chart_style_${style.name.lowercase()}"),
            shape = RoundedCornerShape(10.dp),
            color =
              if (active) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector =
                  if (style == HourlyChartStyle.AREA) Icons.Rounded.ShowChart
                  else Icons.Rounded.BarChart,
                contentDescription = style.label,
                tint =
                  if (active) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (active) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    // Recharts-style Interactive Tooltip Banner
    if (activePoint != null) {
      GlassInnerSurface(
        shape = RoundedCornerShape(14.dp),
        borderColor = primaryLineColor.copy(alpha = 0.45f),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = activePoint.fullHourRange,
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
            )
            StatusPill(
              text = "${activePoint.total} ${if (activePoint.total == 1) "visitor" else "visitors"}",
              containerColor = primaryLineColor.copy(alpha = 0.18f),
              contentColor = primaryLineColor,
            )
          }
          if (activePoint.byDepartment.isNotEmpty()) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              activePoint.byDepartment.forEach { (dept, count) ->
                val color = deptColors[dept] ?: primaryLineColor
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Box(
                    modifier =
                      Modifier.size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                  )
                  Text(
                    text = "$dept: $count",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
              if (activePoint.topReason.isNotBlank()) {
                Text(
                  text = "· Top: ${activePoint.topReason}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            Text(
              text = "No check-ins recorded during this hour",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Chart Canvas + Y/X Axes
    val maxVal = (series.maxOfOrNull { it.total } ?: 1).coerceAtLeast(2)
    val midVal = (maxVal + 1) / 2

    Row(
      modifier = Modifier.fillMaxWidth().height(190.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Y-Axis Ticks
      Column(
        modifier = Modifier.width(26.dp).fillMaxHeight().padding(top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
      ) {
        Text(
          text = maxVal.toString(),
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = midVal.toString(),
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "0",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.width(6.dp))

      Canvas(
        modifier =
          Modifier.weight(1f)
            .fillMaxHeight()
            .pointerInput(series.size) {
              detectTapGestures { offset ->
                if (series.isNotEmpty() && size.width > 0) {
                  val idx = ((offset.x / size.width) * series.size).toInt().coerceIn(0, series.lastIndex)
                  selectedIndex = idx
                }
              }
            }
            .pointerInput(series.size) {
              detectHorizontalDragGestures { change, _ ->
                change.consume()
                if (series.isNotEmpty() && size.width > 0) {
                  val idx =
                    ((change.position.x / size.width) * series.size).toInt().coerceIn(0, series.lastIndex)
                  selectedIndex = idx
                }
              }
            },
      ) {
        if (series.isEmpty()) return@Canvas
        val topPad = 12.dp.toPx()
        val bottomPad = 10.dp.toPx()
        val usableH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

        // CartesianGrid horizontal lines (0%, 50%, 100%)
        listOf(0f, 0.5f, 1f).forEach { frac ->
          val y = topPad + usableH * frac
          drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = if (frac < 1f) dash else null,
          )
        }

        val count = series.size
        val stepX = size.width / count.toFloat()
        fun xForIndex(i: Int): Float = stepX * i + stepX * 0.5f
        fun yForValue(v: Float): Float = topPad + usableH * (1f - ((v / maxVal.toFloat()) * animProgress).coerceIn(0f, 1f))

        // Vertical ReferenceLine at active index
        val activeX = xForIndex(safeIndex)
        drawLine(
          color = guidelineColor.copy(alpha = 0.75f),
          start = Offset(activeX, topPad),
          end = Offset(activeX, topPad + usableH),
          strokeWidth = 1.5.dp.toPx(),
          pathEffect = dash,
        )

        if (chartStyle == HourlyChartStyle.AREA) {
          val points = series.mapIndexed { idx, pt -> Offset(xForIndex(idx), yForValue(pt.total.toFloat())) }
          val strokePath = Path()
          val fillPath = Path()
          points.forEachIndexed { idx, pt ->
            if (idx == 0) {
              strokePath.moveTo(pt.x, pt.y)
              fillPath.moveTo(pt.x, topPad + usableH)
              fillPath.lineTo(pt.x, pt.y)
            } else {
              val prev = points[idx - 1]
              val ctrlX = (prev.x + pt.x) * 0.5f
              strokePath.cubicTo(ctrlX, prev.y, ctrlX, pt.y, pt.x, pt.y)
              fillPath.cubicTo(ctrlX, prev.y, ctrlX, pt.y, pt.x, pt.y)
            }
          }
          if (points.isNotEmpty()) {
            fillPath.lineTo(points.last().x, topPad + usableH)
            fillPath.close()
          }

          drawPath(
            path = fillPath,
            brush =
              Brush.verticalGradient(
                colors = listOf(primaryLineColor.copy(alpha = 0.36f), primaryLineColor.copy(alpha = 0.02f)),
                startY = topPad,
                endY = topPad + usableH,
              ),
          )
          drawPath(
            path = strokePath,
            color = primaryLineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )

          points.forEachIndexed { idx, pt ->
            val isSelected = idx == safeIndex
            val hasTraffic = series[idx].total > 0
            if (hasTraffic || isSelected) {
              drawCircle(
                color = surfaceBgColor,
                radius = if (isSelected) 6.5.dp.toPx() else 4.dp.toPx(),
                center = pt,
              )
              drawCircle(
                color = if (isSelected) guidelineColor else primaryLineColor,
                radius = if (isSelected) 4.5.dp.toPx() else 2.8.dp.toPx(),
                center = pt,
              )
            }
          }
        } else {
          // Stacked Bars by Department
          val barWidth = (stepX * 0.62f).coerceIn(8.dp.toPx(), 26.dp.toPx())
          val orderedDepts = report.departmentSummaries.map { it.department }.reversed()
          series.forEachIndexed { idx, pt ->
            val barLeft = xForIndex(idx) - barWidth * 0.5f
            val isSelected = idx == safeIndex
            var currentBottomY = topPad + usableH
            val nonZeroDepts = orderedDepts.filter { (pt.byDepartment[it] ?: 0) > 0 }
            nonZeroDepts.forEachIndexed { deptIdx, dept ->
              val v = (pt.byDepartment[dept] ?: 0).toFloat()
              val segH = (v / maxVal.toFloat()) * usableH * animProgress
              val segTop = currentBottomY - segH
              val segColor = deptColors[dept] ?: primaryLineColor
              val isTopSegment = deptIdx == nonZeroDepts.lastIndex
              val corner = if (isTopSegment) CornerRadius(4.dp.toPx(), 4.dp.toPx()) else CornerRadius.Zero
              drawRoundRect(
                color = if (isSelected) segColor else segColor.copy(alpha = 0.85f),
                topLeft = Offset(barLeft, segTop),
                size = Size(barWidth, segH.coerceAtLeast(1f)),
                cornerRadius = corner,
              )
              currentBottomY = segTop
            }
            if (isSelected) {
              drawRoundRect(
                color = guidelineColor,
                topLeft = Offset(barLeft - 1.5.dp.toPx(), currentBottomY - 1.5.dp.toPx()),
                size = Size(barWidth + 3.dp.toPx(), (topPad + usableH - currentBottomY + 3.dp.toPx()).coerceAtLeast(4.dp.toPx())),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx()),
              )
            }
          }
        }
      }
    }

    // X-Axis Hour Labels
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      val labelStep = if (series.size <= 9) 1 else 2
      series.forEachIndexed { idx, pt ->
        val showLabel = idx % labelStep == 0 || idx == safeIndex || idx == series.lastIndex
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          if (showLabel) {
            Text(
              text = pt.hourLabel,
              style =
                MaterialTheme.typography.labelSmall.copy(
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = if (idx == safeIndex) FontWeight.Bold else FontWeight.Normal,
                ),
              color =
                if (idx == safeIndex) primaryLineColor
                else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        }
      }
    }

    // Department Color Legend
    if (report.departmentSummaries.isNotEmpty()) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        report.departmentSummaries.forEach { summary ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier =
                Modifier.size(9.dp)
                  .clip(CircleShape)
                  .background(departmentChartColor(summary.department))
            )
            Text(
              text = "${summary.department} (${summary.totalVisits})",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TimeOfDayAndHeatmapCard(report: VisitorAnalyticsReport) {
  val peakBucket = report.peakPeriod?.bucket
  val maxCellValue =
    remember(report) {
      report.departmentSummaries
        .flatMap { it.byPeriod.values }
        .maxOrNull()
        ?.coerceAtLeast(1) ?: 1
    }

  GlassCard(contentPadding = PaddingValues(16.dp)) {
    Text(
      text = "Peak Activity Windows & Department Matrix",
      style = MaterialTheme.typography.titleMedium,
    )
    Text(
      text = "Traffic intensity across morning, afternoon, and evening shifts",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // 4 Time-of-Day Summary Tiles (2x2 grid)
    val chunks = report.periodSummaries.chunked(2)
    chunks.forEach { rowItems ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        rowItems.forEach { period ->
          TimeBucketTile(
            period = period,
            isPeak = period.bucket == peakBucket && period.totalVisits > 0,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    if (report.departmentSummaries.isNotEmpty()) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

      // Matrix Header
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "DEPARTMENT",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1.45f),
        )
        TimeOfDayBucket.entries.forEach { bucket ->
          Text(
            text = bucket.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(0.85f),
          )
        }
      }

      // Matrix Rows
      report.departmentSummaries.forEach { deptSummary ->
        val deptColor = departmentChartColor(deptSummary.department)
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.weight(1.45f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier = Modifier.size(8.dp).clip(CircleShape).background(deptColor)
            )
            Text(
              text = deptSummary.department,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          TimeOfDayBucket.entries.forEach { bucket ->
            val cellCount = deptSummary.byPeriod[bucket] ?: 0
            val intensity = (cellCount.toFloat() / maxCellValue.toFloat()).coerceIn(0f, 1f)
            val cellBg =
              if (cellCount == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
              else deptColor.copy(alpha = 0.18f + intensity * 0.58f)
            Box(
              modifier =
                Modifier.weight(0.85f)
                  .height(34.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(cellBg),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = cellCount.toString(),
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = if (cellCount > 0) FontWeight.Bold else FontWeight.Normal,
                  ),
                color =
                  if (cellCount == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TimeBucketTile(
  period: PeriodTrafficSummary,
  isPeak: Boolean,
  modifier: Modifier = Modifier,
) {
  val accent = periodChartColor(period.bucket)
  GlassInnerSurface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    borderColor = if (isPeak) accent.copy(alpha = 0.7f) else null,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = period.bucket.shortLabel,
          style = MaterialTheme.typography.labelMedium,
          color = accent,
          fontWeight = FontWeight.SemiBold,
        )
        if (isPeak) {
          StatusPill(
            text = "Peak",
            containerColor = accent.copy(alpha = 0.2f),
            contentColor = accent,
          )
        }
      }
      Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = period.totalVisits.toString(),
          style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "${period.sharePercent}%",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 3.dp),
        )
      }
      Text(
        text = period.bucket.label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DepartmentStackedBreakdownCard(
  report: VisitorAnalyticsReport,
  selectedDepartment: String?,
  onSelectDepartment: (String) -> Unit,
) {
  val maxDeptVisits =
    (report.departmentSummaries.maxOfOrNull { it.totalVisits } ?: 1).coerceAtLeast(1)

  GlassCard(contentPadding = PaddingValues(16.dp)) {
    Text(
      text = "Department Volume & Shift Breakdown",
      style = MaterialTheme.typography.titleMedium,
    )
    Text(
      text = "Tap a department to isolate its hourly trend above",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Period Legend
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      TimeOfDayBucket.entries.forEach { bucket ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(periodChartColor(bucket))
          )
          Text(
            text = bucket.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    report.departmentSummaries.forEach { dept ->
      DepartmentStackedRow(
        summary = dept,
        maxDeptVisits = maxDeptVisits,
        isSelected = selectedDepartment.equals(dept.department, ignoreCase = true),
        onClick = { onSelectDepartment(dept.department) },
      )
    }
  }
}

@Composable
private fun DepartmentStackedRow(
  summary: DepartmentTrafficSummary,
  maxDeptVisits: Int,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val deptColor = departmentChartColor(summary.department)
  val totalFraction by
    animateFloatAsState(
      targetValue = (summary.totalVisits.toFloat() / maxDeptVisits.toFloat()).coerceIn(0.06f, 1f),
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "dept_bar_${summary.department}",
    )

  GlassInnerSurface(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag("dept_row_${summary.department.lowercase().replace(" ", "_")}"),
    shape = RoundedCornerShape(14.dp),
    borderColor = if (isSelected) deptColor else null,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier.size(10.dp).clip(CircleShape).background(deptColor)
          )
          Text(
            text = summary.department,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = "${summary.totalVisits} · ${summary.sharePercent}%",
          style =
            MaterialTheme.typography.labelLarge.copy(
              fontFamily = JetBrainsMonoFamily,
              fontWeight = FontWeight.Bold,
            ),
          color = deptColor,
        )
      }

      // Stacked horizontal progress bar split by TimeOfDayBucket
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(totalFraction).fillMaxHeight(),
        ) {
          TimeOfDayBucket.entries.forEach { bucket ->
            val part = summary.byPeriod[bucket] ?: 0
            if (part > 0) {
              Box(
                modifier =
                  Modifier.weight(part.toFloat())
                    .fillMaxHeight()
                    .background(periodChartColor(bucket)),
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Peak: ${summary.peakHourLabel}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(visible = summary.topHosts.isNotBlank()) {
          Text(
            text = summary.topHosts,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun ReasonDistributionCard(report: VisitorAnalyticsReport) {
  val total = report.totalVisits.coerceAtLeast(1)
  GlassCard(contentPadding = PaddingValues(16.dp)) {
    Text(
      text = "Top Visitor Purposes",
      style = MaterialTheme.typography.titleMedium,
    )
    report.reasonBreakdown.forEachIndexed { idx, (reason, count) ->
      val pct = ((count * 100f) / total).roundToInt()
      val barColor =
        when (idx) {
          0 -> MaterialTheme.colorScheme.primary
          1 -> if (LocalIsDarkTheme.current) ArchAmberBright else Color(0xFFD97706)
          2 -> if (LocalIsDarkTheme.current) FreeGreenBright else FreeGreen
          3 -> if (LocalIsDarkTheme.current) Color(0xFFA78BFA) else Color(0xFF7C3AED)
          else -> if (LocalIsDarkTheme.current) Color(0xFF38BDF8) else Color(0xFF0284C7)
        }
      val fraction by
        animateFloatAsState(
          targetValue = (count.toFloat() / total.toFloat()).coerceIn(0.05f, 1f),
          animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
          label = "reason_$idx",
        )
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
          )
          Text(
            text = "$count ($pct%)",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(barColor),
          )
        }
      }
    }
  }
}
