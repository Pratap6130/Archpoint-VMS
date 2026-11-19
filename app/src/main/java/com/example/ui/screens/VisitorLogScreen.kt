package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.StaffMember
import com.example.data.model.Visit
import com.example.data.model.VisitStatus
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassInnerSurface
import com.example.ui.components.GlassSegmentedControl
import com.example.ui.components.ManagementAnalyticsDashboard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.archPointStatusColors
import com.example.util.DeskFormatters
import java.io.File
import java.util.Date

private enum class LogViewMode {
  RECORDS,
  ANALYTICS,
}

private enum class LogDatePreset {
  LAST_7D,
  LAST_30D,
  ALL,
}

fun filterVisitorLogVisits(
  visits: List<Visit>,
  fromMillis: Long,
  toMillis: Long,
  searchQuery: String,
  nowMillis: Long,
): List<Visit> {
  val q = searchQuery.trim().lowercase()
  val endInclusive =
    if (toMillis <= 0L) Long.MAX_VALUE else DeskFormatters.endOfDayMillis(toMillis)
  return visits.filter { visit ->
    val arrivedMs = visit.arrivedAt?.toDate()?.time ?: nowMillis
    val inRange = arrivedMs in fromMillis..endInclusive
    val matchesQuery =
      q.isEmpty() ||
        visit.visitorName.lowercase().contains(q) ||
        visit.visitorCompany.lowercase().contains(q) ||
        visit.visitorMobile.lowercase().contains(q) ||
        visit.visitorJobProfile.lowercase().contains(q) ||
        visit.reason.lowercase().contains(q) ||
        visit.remark.lowercase().contains(q) ||
        visit.hostShownAs.lowercase().contains(q)
    inRange && matchesQuery
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorLogScreen(
  visits: List<Visit>,
  staff: List<StaffMember>,
  nowMillis: Long,
  isBusy: Boolean = false,
  onSyncArchPointData: (() -> Unit)? = null,
  onToastMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  var viewMode by remember { mutableStateOf(LogViewMode.RECORDS) }
  var selectedPreset by remember { mutableStateOf<LogDatePreset?>(LogDatePreset.ALL) }
  var fromMillis by remember { mutableLongStateOf(0L) }
  var toMillis by remember { mutableLongStateOf(0L) }
  var searchQuery by remember { mutableStateOf("") }
  var pickingFrom by remember { mutableStateOf(false) }
  var pickingTo by remember { mutableStateOf(false) }

  val filteredVisits =
    remember(visits, fromMillis, toMillis, searchQuery, nowMillis) {
      filterVisitorLogVisits(
        visits = visits,
        fromMillis = fromMillis,
        toMillis = toMillis,
        searchQuery = searchQuery,
        nowMillis = nowMillis,
      )
    }

  val totalVisits = filteredVisits.size
  val uniqueVisitors =
    remember(filteredVisits) {
      filteredVisits
        .map {
          it.visitorMobile.takeIf { m -> m.isNotBlank() && m != "919999999999" }
            ?: it.visitorName
        }
        .distinct()
        .size
    }
  val meetingsHeld =
    remember(filteredVisits) {
      filteredVisits.count {
        val st = it.parsedStatus()
        st == VisitStatus.IN_MEETING || st == VisitStatus.DONE
      }
    }
  val avgWaitMin =
    remember(filteredVisits, nowMillis) {
      if (filteredVisits.isEmpty()) {
        0L
      } else {
        val totalWait =
          filteredVisits.sumOf { v ->
            val start = v.waitStartedAt?.toDate()?.time ?: v.arrivedAt?.toDate()?.time ?: nowMillis
            val end = v.meetingStartedAt?.toDate()?.time ?: v.endedAt?.toDate()?.time ?: nowMillis
            DeskFormatters.minutesBetween(start, end)
          }
        totalWait / filteredVisits.size
      }
    }

  val csvSaverLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      if (uri != null) {
        try {
          val csvText = DeskFormatters.buildSafeVisitorCsv(filteredVisits)
          context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(csvText.toByteArray(Charsets.UTF_8))
          }
          onToastMessage("Saved CSV (${filteredVisits.size} rows)")
        } catch (e: Exception) {
          onToastMessage("Could not save CSV: ${e.localizedMessage}")
        }
      }
    }

  if (pickingFrom) {
    val pickerState =
      rememberDatePickerState(
        initialSelectedDateMillis = if (fromMillis > 0L) fromMillis else nowMillis,
      )
    DatePickerDialog(
      onDismissRequest = { pickingFrom = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let {
              fromMillis = DeskFormatters.startOfTodayMillis(it)
              selectedPreset = null
            }
            pickingFrom = false
          }
        ) {
          Text("OK")
        }
      },
      dismissButton = { TextButton(onClick = { pickingFrom = false }) { Text("Cancel") } },
    ) {
      DatePicker(state = pickerState)
    }
  }

  if (pickingTo) {
    val pickerState =
      rememberDatePickerState(
        initialSelectedDateMillis = if (toMillis > 0L) toMillis else nowMillis,
      )
    DatePickerDialog(
      onDismissRequest = { pickingTo = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let {
              toMillis = DeskFormatters.endOfDayMillis(it)
              selectedPreset = null
            }
            pickingTo = false
          }
        ) {
          Text("OK")
        }
      },
      dismissButton = { TextButton(onClick = { pickingTo = false }) { Text("Cancel") } },
    ) {
      DatePicker(state = pickerState)
    }
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    GlassSegmentedControl(
      options =
        listOf(
          LogViewMode.RECORDS to "Records (${filteredVisits.size})",
          LogViewMode.ANALYTICS to "Analytics",
        ),
      selected = viewMode,
      onSelect = { viewMode = it },
      tagPrefix = "log_view",
    )

    if (visits.isEmpty() && onSyncArchPointData != null) {
      GlassCard(contentPadding = PaddingValues(16.dp)) {
        Text(
          text = "No visitor records synced yet",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "Tap below to load all 55 ArchPoint visitor records (your scheduled appointments stay safe).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
          onClick = onSyncArchPointData,
          enabled = !isBusy,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("log_import_archpoint_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text("Loading Records...")
          } else {
            Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Load 55 ArchPoint Visitor Records")
          }
        }
      }
    }

    // 4 KPI summary cards (2x2 on phones)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      LogKpiCard(totalVisits.toString(), "visits", Modifier.weight(1f))
      LogKpiCard(uniqueVisitors.toString(), "different people", Modifier.weight(1f))
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      LogKpiCard(meetingsHeld.toString(), "meetings held", Modifier.weight(1f))
      LogKpiCard("${avgWaitMin}m", "average wait", Modifier.weight(1f))
    }

    // Shared date range & search filter card
    GlassCard(contentPadding = PaddingValues(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DatePresetButton(
          label = "Last 7d",
          selected = selectedPreset == LogDatePreset.LAST_7D,
          onClick = {
            selectedPreset = LogDatePreset.LAST_7D
            fromMillis = DeskFormatters.daysAgoStartMillis(6, nowMillis)
            toMillis = DeskFormatters.endOfDayMillis(nowMillis)
          },
          modifier = Modifier.weight(1f).testTag("log_preset_7d"),
        )
        DatePresetButton(
          label = "Last 30d",
          selected = selectedPreset == LogDatePreset.LAST_30D,
          onClick = {
            selectedPreset = LogDatePreset.LAST_30D
            fromMillis = DeskFormatters.daysAgoStartMillis(29, nowMillis)
            toMillis = DeskFormatters.endOfDayMillis(nowMillis)
          },
          modifier = Modifier.weight(1f).testTag("log_preset_30d"),
        )
        DatePresetButton(
          label = "All",
          selected = selectedPreset == LogDatePreset.ALL,
          onClick = {
            selectedPreset = LogDatePreset.ALL
            fromMillis = 0L
            toMillis = 0L
          },
          modifier = Modifier.weight(1f).testTag("log_preset_all"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = { pickingFrom = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("log_from_date_button"),
        ) {
          Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            if (fromMillis == 0L) "From Earliest" else "From ${DeskFormatters.formatDisplayDateField(fromMillis)}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
          )
        }
        OutlinedButton(
          onClick = { pickingTo = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("log_to_date_button"),
        ) {
          Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            if (toMillis <= 0L) "To Latest" else "To ${DeskFormatters.formatDisplayDateField(toMillis)}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
          )
        }
      }

      if (viewMode == LogViewMode.RECORDS) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search name, company, mobile, profile, remark...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("log_search_input"),
        )
      }
    }

    if (viewMode == LogViewMode.ANALYTICS) {
      ManagementAnalyticsDashboard(
        visits = filteredVisits,
        staff = staff,
      )
    } else {
      GlassCard(contentPadding = PaddingValues(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = {
              val fileName = "archpoint_visitors_${DeskFormatters.formatCsvDate(Date())}.csv"
              csvSaverLauncher.launch(fileName)
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("download_csv_button"),
          ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download CSV")
          }

          OutlinedButton(
            onClick = {
              try {
                val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file =
                  File(exportsDir, "archpoint_visitors_${DeskFormatters.formatCsvDate(Date())}.csv")
                file.writeText(DeskFormatters.buildSafeVisitorCsv(filteredVisits), Charsets.UTF_8)
                val uri =
                  FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent =
                  Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                context.startActivity(Intent.createChooser(shareIntent, "Share Visitor Log CSV"))
              } catch (e: Exception) {
                onToastMessage("Could not share CSV: ${e.localizedMessage}")
              }
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(48.dp).testTag("share_csv_button"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = "Share CSV", modifier = Modifier.size(18.dp))
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

        if (filteredVisits.isEmpty()) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text =
                if (visits.isEmpty()) {
                  "No visitor records found in this workspace yet."
                } else {
                  "No visitor records match this date range or search."
                },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (visits.isNotEmpty()) {
              OutlinedButton(
                onClick = {
                  selectedPreset = LogDatePreset.ALL
                  fromMillis = 0L
                  toMillis = 0L
                  searchQuery = ""
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("log_reset_filters_button"),
              ) {
                Text("Show All ${visits.size} Visits")
              }
            }
          }
        } else {
          Text(
            text = "${filteredVisits.size} records · Swipe table sideways for mobile, company, remark & times",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          val hScroll = rememberScrollState()
          Column(modifier = Modifier.horizontalScroll(hScroll)) {
            LogHeaderRow()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            filteredVisits.forEach { visit ->
              LogDataRow(visit = visit)
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
          }
        }
      }
    }

    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun DatePresetButton(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (selected) {
    Button(
      onClick = onClick,
      shape = RoundedCornerShape(10.dp),
      contentPadding = PaddingValues(horizontal = 10.dp),
      modifier = modifier.heightIn(min = 48.dp),
    ) {
      Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
  } else {
    OutlinedButton(
      onClick = onClick,
      shape = RoundedCornerShape(10.dp),
      contentPadding = PaddingValues(horizontal = 10.dp),
      modifier = modifier.heightIn(min = 48.dp),
    ) {
      Text(label, style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable
private fun LogKpiCard(value: String, label: String, modifier: Modifier = Modifier) {
  GlassInnerSurface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFamily),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun LogHeaderRow() {
  Row(
    modifier = Modifier.padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    HeaderCell("DATE", 98.dp)
    HeaderCell("VISITOR", 140.dp)
    HeaderCell("ORG / COMPANY", 140.dp)
    HeaderCell("PROFILE", 110.dp)
    HeaderCell("MOBILE", 118.dp)
    HeaderCell("MET", 120.dp)
    HeaderCell("TYPE / REASON", 115.dp)
    HeaderCell("REMARK", 160.dp)
    HeaderCell("IN", 78.dp)
    HeaderCell("OUT", 78.dp)
    HeaderCell("STATUS", 96.dp)
  }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.width(width).padding(end = 8.dp),
  )
}

@Composable
private fun LogDataRow(visit: Visit) {
  val status = archPointStatusColors()
  val arrived = visit.arrivedAt?.toDate()
  val inDate = visit.meetingStartedAt?.toDate() ?: arrived
  val outDate = visit.endedAt?.toDate()
  val (pillBg, pillFg) =
    when (visit.parsedStatus()) {
      VisitStatus.WAITING -> status.waitingContainer to status.waitingOnContainer
      VisitStatus.IN_MEETING -> status.meetingContainer to status.meetingOnContainer
      VisitStatus.DONE -> status.freeContainer to status.freeOnContainer
      VisitStatus.DENIED -> status.deniedContainer to status.deniedOnContainer
    }

  Row(
    modifier = Modifier.padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    DataCell(DeskFormatters.formatLogDate(arrived), 98.dp, bold = false)
    DataCell(visit.visitorName, 140.dp, bold = true)
    DataCell(visit.visitorCompany.ifBlank { "—" }, 140.dp, bold = false)
    DataCell(visit.visitorJobProfile.ifBlank { "—" }, 110.dp, bold = false)
    Text(
      text = visit.visitorMobile.ifBlank { "—" },
      style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
      modifier = Modifier.width(118.dp).padding(end = 8.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    DataCell(visit.hostShownAs, 120.dp, bold = false)
    DataCell(visit.reason, 115.dp, bold = false)
    DataCell(visit.remark.ifBlank { "—" }, 160.dp, bold = false)
    DataCell(DeskFormatters.formatTime(inDate), 78.dp, bold = false)
    DataCell(DeskFormatters.formatTime(outDate), 78.dp, bold = false)
    Box(modifier = Modifier.width(96.dp)) {
      StatusPill(text = visit.status, containerColor = pillBg, contentColor = pillFg)
    }
  }
}

@Composable
private fun DataCell(text: String, width: Dp, bold: Boolean) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.width(width).padding(end = 8.dp),
  )
}
