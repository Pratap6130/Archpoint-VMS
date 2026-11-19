package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.StaffMember
import com.example.data.model.Visit
import com.example.data.model.VisitStatus
import com.example.data.model.VisitorProfile
import com.example.ui.components.BookAppointmentBottomSheet
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassInnerSurface
import com.example.ui.components.GlassSegmentedControl
import com.example.ui.components.RecentVisitorsCard
import com.example.ui.components.RecentVisitorsStripCard
import com.example.ui.components.StaffAvatar
import com.example.ui.components.StatusDotLabel
import com.example.ui.components.StatusPill
import com.example.ui.components.VisitorCardScanAction
import com.example.ui.components.VisitorCardScanStatusStrip
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.CardScanState
import com.example.ui.viewmodel.DeskSnapshot
import com.example.ui.viewmodel.HostBoardItem
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ReceptionFormState
import com.example.ui.viewmodel.VisitorSearchState
import com.example.util.DeskFormatters

private enum class ReceptionPane {
  CHECK_IN,
  DESK_QUEUE,
  APPOINTMENTS,
}

private enum class TodaySubTab {
  WAITING,
  IN_MEETING,
  DONE,
}

@Composable
fun ReceptionScreen(
  snapshot: DeskSnapshot,
  form: ReceptionFormState,
  searchState: VisitorSearchState,
  cardScanState: CardScanState,
  hostBoard: List<HostBoardItem>,
  nowMillis: Long,
  appointments: List<Appointment> = emptyList(),
  isBusy: Boolean = false,
  onSearchQueryChanged: (String) -> Unit,
  onTriggerSearch: () -> Unit,
  onClearSearch: () -> Unit,
  onScanCardUri: (Uri) -> Unit,
  onClearCardScan: () -> Unit,
  onMobileChanged: (String) -> Unit,
  onNameChanged: (String) -> Unit,
  onCompanyChanged: (String) -> Unit,
  onEmailChanged: (String) -> Unit,
  onDobChanged: (String) -> Unit,
  onRemarkChanged: (String) -> Unit,
  onReasonSelected: (String) -> Unit,
  onHostSelected: (String) -> Unit,
  onApplySuggestion: (VisitorProfile) -> Unit,
  onClearReturningMatch: () -> Unit,
  onClearForm: () -> Unit,
  onCheckIn: () -> Unit,
  onRemind: (Visit) -> Unit,
  onSendIn: (Visit) -> Unit,
  onMarkDone: (Visit) -> Unit,
  onSaveAppointment: (
    existing: Appointment?,
    visitorName: String,
    visitorMobile: String,
    visitorCompany: String,
    visitorEmail: String,
    purpose: String,
    location: String,
    scheduledDateIso: String,
    scheduledTimeLabel: String,
    notes: String,
    assignedHostStaff: StaffMember?,
  ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
  onCheckInAppointment: (Appointment) -> Unit = {},
  onUpdateAppointmentStatus: (Appointment, AppointmentStatus) -> Unit = { _, _ -> },
) {
  val todayStart = remember(nowMillis / 60_000L) { DeskFormatters.startOfTodayMillis(nowMillis) }
  val todaysVisits =
    remember(snapshot.visits, todayStart) {
      snapshot.visits.filter { (it.arrivedAt?.toDate()?.time ?: nowMillis) >= todayStart }
    }
  val activeQueueCount =
    remember(todaysVisits) {
      todaysVisits.count {
        val st = it.parsedStatus()
        st == VisitStatus.WAITING || st == VisitStatus.IN_MEETING
      }
    }
  val recentVisitors =
    remember(snapshot.visitorProfiles, snapshot.visits) {
      MainViewModel.buildRecentVisitors(
        profiles = snapshot.visitorProfiles,
        visits = snapshot.visits,
      )
    }
  val upcomingAppointments =
    remember(appointments, nowMillis / 60_000L) {
      MainViewModel.upcomingScheduledAppointments(appointments, nowMillis)
    }
  val assignableHosts = remember(hostBoard) { hostBoard.map { it.staff } }

  var showAppointmentSheet by remember { mutableStateOf(false) }
  var editingAppointment by remember { mutableStateOf<Appointment?>(null) }

  if (showAppointmentSheet) {
    BookAppointmentBottomSheet(
      existing = editingAppointment,
      assignableHosts = assignableHosts,
      isHostOnly = false,
      currentStaff = snapshot.currentStaff,
      isBusy = isBusy,
      onDismiss = {
        showAppointmentSheet = false
        editingAppointment = null
      },
      onSave = { vName, vMob, vComp, vEmail, purpose, loc, dateIso, timeLbl, notes, host ->
        onSaveAppointment(
          editingAppointment,
          vName,
          vMob,
          vComp,
          vEmail,
          purpose,
          loc,
          dateIso,
          timeLbl,
          notes,
          host,
        )
      },
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWide = maxWidth >= 860.dp
    if (isWide) {
      Row(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Box(modifier = Modifier.weight(1.1f)) {
          VisitorEntryCard(
            snapshot = snapshot,
            form = form,
            searchState = searchState,
            cardScanState = cardScanState,
            hostBoard = hostBoard,
            onSearchQueryChanged = onSearchQueryChanged,
            onTriggerSearch = onTriggerSearch,
            onClearSearch = onClearSearch,
            onScanCardUri = onScanCardUri,
            onClearCardScan = onClearCardScan,
            onMobileChanged = onMobileChanged,
            onNameChanged = onNameChanged,
            onCompanyChanged = onCompanyChanged,
            onEmailChanged = onEmailChanged,
            onDobChanged = onDobChanged,
            onRemarkChanged = onRemarkChanged,
            onReasonSelected = onReasonSelected,
            onHostSelected = onHostSelected,
            onApplySuggestion = onApplySuggestion,
            onClearReturningMatch = onClearReturningMatch,
            onClearForm = onClearForm,
            onCheckIn = onCheckIn,
            onBookAppointment = {
              editingAppointment = null
              showAppointmentSheet = true
            },
          )
        }
        Column(
          modifier = Modifier.weight(0.95f),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          WhoIsFreeBoardCard(hostBoard = hostBoard, onMarkDone = onMarkDone)
          UpcomingAppointmentsCard(
            appointments = upcomingAppointments,
            nowMillis = nowMillis,
            isBusy = isBusy,
            onBookNew = {
              editingAppointment = null
              showAppointmentSheet = true
            },
            onCheckInAppointment = onCheckInAppointment,
            onEditAppointment = { appt ->
              editingAppointment = appt
              showAppointmentSheet = true
            },
            onCancelAppointment = { appt ->
              onUpdateAppointmentStatus(appt, AppointmentStatus.CANCELLED)
            },
          )
          RecentVisitorsCard(
            visitors = recentVisitors,
            form = form,
            onSelectVisitor = onApplySuggestion,
          )
          TodayAtOfficeCard(
            visits = todaysVisits,
            nowMillis = nowMillis,
            onRemind = onRemind,
            onSendIn = onSendIn,
            onMarkDone = onMarkDone,
          )
        }
      }
    } else {
      var activePane by remember { mutableStateOf(ReceptionPane.CHECK_IN) }
      Column(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        GlassSegmentedControl(
          options =
            listOf(
              ReceptionPane.CHECK_IN to "Check-in",
              ReceptionPane.DESK_QUEUE to
                if (activeQueueCount > 0) "Queue ($activeQueueCount)" else "Queue",
              ReceptionPane.APPOINTMENTS to
                if (upcomingAppointments.isNotEmpty()) "Appts (${upcomingAppointments.size})" else "Appts",
            ),
          selected = activePane,
          onSelect = { activePane = it },
          tagPrefix = "reception_pane",
        )

        if (activePane == ReceptionPane.CHECK_IN) {
          FreeHostsQuickStrip(
            hostBoard = hostBoard,
            selectedHostId = form.selectedHostId,
            onSelectHost = onHostSelected,
          )
          if (upcomingAppointments.isNotEmpty()) {
            val nextAppt = upcomingAppointments.first()
            val dayLbl = DeskFormatters.formatAppointmentDayLabel(nextAppt.scheduledDateIso, nowMillis)
            GlassInnerSurface(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { activePane = ReceptionPane.APPOINTMENTS }
                  .testTag("reception_upcoming_banner"),
              borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(
                    Icons.Rounded.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(8.dp))
                  Text(
                    text = "${upcomingAppointments.size} upcoming · Next: ${nextAppt.visitorName} for ${nextAppt.hostShownAs} ($dayLbl ${nextAppt.scheduledTimeLabel})",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                  text = "View",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
          RecentVisitorsStripCard(
            visitors = recentVisitors,
            form = form,
            onSelectVisitor = onApplySuggestion,
          )
          VisitorEntryCard(
            snapshot = snapshot,
            form = form,
            searchState = searchState,
            cardScanState = cardScanState,
            hostBoard = hostBoard,
            onSearchQueryChanged = onSearchQueryChanged,
            onTriggerSearch = onTriggerSearch,
            onClearSearch = onClearSearch,
            onScanCardUri = onScanCardUri,
            onClearCardScan = onClearCardScan,
            onMobileChanged = onMobileChanged,
            onNameChanged = onNameChanged,
            onCompanyChanged = onCompanyChanged,
            onEmailChanged = onEmailChanged,
            onDobChanged = onDobChanged,
            onRemarkChanged = onRemarkChanged,
            onReasonSelected = onReasonSelected,
            onHostSelected = onHostSelected,
            onApplySuggestion = onApplySuggestion,
            onClearReturningMatch = onClearReturningMatch,
            onClearForm = onClearForm,
            onCheckIn = onCheckIn,
            onBookAppointment = {
              editingAppointment = null
              showAppointmentSheet = true
            },
          )
        } else if (activePane == ReceptionPane.DESK_QUEUE) {
          WhoIsFreeBoardCard(hostBoard = hostBoard, onMarkDone = onMarkDone)
          TodayAtOfficeCard(
            visits = todaysVisits,
            nowMillis = nowMillis,
            onRemind = onRemind,
            onSendIn = onSendIn,
            onMarkDone = onMarkDone,
          )
          RecentVisitorsCard(
            visitors = recentVisitors,
            form = form,
            onSelectVisitor = { profile ->
              onApplySuggestion(profile)
              activePane = ReceptionPane.CHECK_IN
            },
          )
        } else {
          UpcomingAppointmentsCard(
            appointments = upcomingAppointments,
            nowMillis = nowMillis,
            isBusy = isBusy,
            onBookNew = {
              editingAppointment = null
              showAppointmentSheet = true
            },
            onCheckInAppointment = onCheckInAppointment,
            onEditAppointment = { appt ->
              editingAppointment = appt
              showAppointmentSheet = true
            },
            onCancelAppointment = { appt ->
              onUpdateAppointmentStatus(appt, AppointmentStatus.CANCELLED)
            },
          )
        }
        Spacer(Modifier.height(10.dp))
      }
    }
  }
}

@Composable
private fun FreeHostsQuickStrip(
  hostBoard: List<HostBoardItem>,
  selectedHostId: String,
  onSelectHost: (String) -> Unit,
) {
  if (hostBoard.isEmpty()) return
  val status = archPointStatusColors()
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    hostBoard.forEach { item ->
      val (statusWord, tone) = MainViewModel.resolveHostAvailabilityVisual(item)
      val dotColor = status.dotColor(tone)
      val selected = item.staff.id == selectedHostId
      GlassInnerSurface(
        modifier =
          Modifier.clip(RoundedCornerShape(20.dp))
            .clickable { onSelectHost(item.staff.id) }
            .testTag("host_tile_${item.staff.id}"),
        shape = RoundedCornerShape(20.dp),
        containerColor =
          if (selected) MaterialTheme.colorScheme.primaryContainer
          else null,
        borderColor =
          if (selected) MaterialTheme.colorScheme.primary
          else dotColor.copy(alpha = 0.4f),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
          Spacer(Modifier.width(6.dp))
          Text(
            text =
              buildString {
                append(item.staff.shownAs.ifBlank { item.staff.fullName })
                if (item.inMeetingVisit != null) {
                  append(" · ${item.meetingMinutes}m")
                } else {
                  append(" · $statusWord")
                }
              },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color =
              if (selected) MaterialTheme.colorScheme.onPrimaryContainer
              else MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VisitorEntryCard(
  snapshot: DeskSnapshot,
  form: ReceptionFormState,
  searchState: VisitorSearchState,
  cardScanState: CardScanState,
  hostBoard: List<HostBoardItem>,
  onSearchQueryChanged: (String) -> Unit,
  onTriggerSearch: () -> Unit,
  onClearSearch: () -> Unit,
  onScanCardUri: (Uri) -> Unit,
  onClearCardScan: () -> Unit,
  onMobileChanged: (String) -> Unit,
  onNameChanged: (String) -> Unit,
  onCompanyChanged: (String) -> Unit,
  onEmailChanged: (String) -> Unit,
  onDobChanged: (String) -> Unit,
  onRemarkChanged: (String) -> Unit,
  onReasonSelected: (String) -> Unit,
  onHostSelected: (String) -> Unit,
  onApplySuggestion: (VisitorProfile) -> Unit,
  onClearReturningMatch: () -> Unit,
  onClearForm: () -> Unit,
  onCheckIn: () -> Unit,
  onBookAppointment: () -> Unit,
) {
  val status = archPointStatusColors()
  var showDobPicker by remember { mutableStateOf(false) }
  var hostDropdownExpanded by remember { mutableStateOf(false) }
  var reasonDropdownExpanded by remember { mutableStateOf(false) }

  val shownReasons = remember(snapshot.reasons) { snapshot.reasons.filter { it.shown } }
  val nameSuggestions =
    remember(form.name, snapshot.visitorProfiles, form.matchedProfile) {
      val q = form.name.trim()
      if (q.length >= 3 && form.matchedProfile == null) {
        snapshot.visitorProfiles
          .filter { it.name.contains(q, ignoreCase = true) || it.company.contains(q, ignoreCase = true) }
          .take(4)
      } else {
        emptyList()
      }
    }
  val selectedHostItem = hostBoard.firstOrNull { it.staff.id == form.selectedHostId }
  val selectedHost = selectedHostItem?.staff

  if (showDobPicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDobPicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              onDobChanged(DeskFormatters.formatDisplayDateField(millis))
            }
            showDobPicker = false
          }
        ) {
          Text("OK")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDobPicker = false }) { Text("Cancel") }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  GlassCard(contentPadding = PaddingValues(14.dp)) {
    // 1. PARALLEL ROW: Search returning visitor + Compact Scan Card action
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = searchState.query,
        onValueChange = onSearchQueryChanged,
        label = { Text("Search visitor") },
        placeholder = { Text("Name / phone...") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search returning visitors",
            modifier = Modifier.size(18.dp),
          )
        },
        trailingIcon = {
          if (searchState.isSearching) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
            )
          } else if (searchState.query.isNotEmpty()) {
            IconButton(onClick = onClearSearch) {
              Icon(Icons.Rounded.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
            }
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onTriggerSearch() }),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("returning_visitor_search_input"),
      )

      VisitorCardScanAction(
        scanState = cardScanState,
        onScanCardUri = onScanCardUri,
      )
    }

    // Expandable card-scan status / preview (zero height when Idle)
    VisitorCardScanStatusStrip(
      scanState = cardScanState,
      onClearScan = onClearCardScan,
    )

    if (searchState.errorMessage != null) {
      Text(
        text = searchState.errorMessage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    } else if (searchState.hasSearched && searchState.query.trim().length >= 2) {
      GlassInnerSurface {
        Column(
          modifier = Modifier.fillMaxWidth().padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (searchState.results.isEmpty()) {
            Text(
              text = "No returning visitors matched \"${searchState.query.trim()}\".",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Text(
              text = "Tap a returning visitor to auto-fill",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            searchState.results.forEach { profile ->
              GlassInnerSurface(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onApplySuggestion(profile) }
                    .testTag("search_result_${profile.mobile}"),
                shape = RoundedCornerShape(10.dp),
                containerColor = MaterialTheme.colorScheme.surface,
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = profile.name,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    val details =
                      listOfNotNull(
                        profile.company.takeIf { it.isNotBlank() },
                        profile.mobile.takeIf { it.isNotBlank() && it != "919999999999" },
                        profile.email.takeIf { it.isNotBlank() },
                      ).joinToString(" · ")
                    if (details.isNotBlank()) {
                      Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                  Spacer(Modifier.width(8.dp))
                  StatusPill(
                    text = DeskFormatters.ordinalVisit(profile.visitCount),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }
            }
          }
        }
      }
    }

    form.matchedProfile?.let { match ->
      val nextVisitLabel = DeskFormatters.ordinalVisit(match.visitCount + 1L)
      GlassInnerSurface(
        shape = RoundedCornerShape(10.dp),
        containerColor = status.freeContainer,
        borderColor = status.freeText.copy(alpha = 0.4f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = status.freeText,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Returning · $nextVisitLabel visit",
              style = MaterialTheme.typography.labelMedium,
              color = status.freeOnContainer,
              fontWeight = FontWeight.SemiBold,
            )
          }
          TextButton(
            onClick = onClearReturningMatch,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.heightIn(min = 32.dp).testTag("not_them_button"),
          ) {
            Text("Not them?", style = MaterialTheme.typography.labelSmall, color = status.freeText, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 2. COMPACT ZERO-SCROLL 2-COLUMN ROWS
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = form.name,
        onValueChange = onNameChanged,
        label = { Text("Name") },
        placeholder = { Text("Full name") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("visitor_name_input"),
      )
      OutlinedTextField(
        value = form.company,
        onValueChange = onCompanyChanged,
        label = { Text("Org / Company") },
        placeholder = { Text("Optional") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("visitor_company_input"),
      )
    }

    if (nameSuggestions.isNotEmpty()) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        nameSuggestions.forEach { profile ->
          FilterChip(
            selected = false,
            onClick = { onApplySuggestion(profile) },
            leadingIcon = {
              Icon(Icons.Rounded.PersonSearch, contentDescription = null, modifier = Modifier.size(15.dp))
            },
            label = {
              val comp = profile.company.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
              Text("${profile.name}$comp", style = MaterialTheme.typography.labelSmall)
            },
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = form.mobile,
        onValueChange = onMobileChanged,
        label = { Text("Phone (10 digits)") },
        placeholder = { Text("9829012345") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
        modifier = Modifier.weight(1f).testTag("visitor_mobile_input"),
      )
      OutlinedTextField(
        value = form.email,
        onValueChange = onEmailChanged,
        label = { Text("Email (opt.)") },
        placeholder = { Text("name@co.com") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("visitor_email_input"),
      )
    }

    // 3. HOST DROPDOWN & REASON DROPDOWN SIDE BY SIDE
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ExposedDropdownMenuBox(
        expanded = hostDropdownExpanded,
        onExpandedChange = { if (hostBoard.isNotEmpty()) hostDropdownExpanded = !hostDropdownExpanded },
        modifier = Modifier.weight(1.15f),
      ) {
        val selectedLabel =
          selectedHost?.let { it.shownAs.ifBlank { it.fullName } } ?: "Select host"
        val selectedTone =
          selectedHostItem?.let { MainViewModel.resolveHostAvailabilityVisual(it).second }

        OutlinedTextField(
          value = selectedLabel,
          onValueChange = {},
          readOnly = true,
          label = { Text("Person to meet") },
          leadingIcon =
            selectedTone?.let { tone ->
              {
                Box(
                  modifier =
                    Modifier.size(10.dp)
                      .clip(CircleShape)
                      .background(status.dotColor(tone))
                )
              }
            },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostDropdownExpanded) },
          singleLine = true,
          colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
          modifier =
            Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth()
              .testTag("host_dropdown_field"),
        )

        ExposedDropdownMenu(
          expanded = hostDropdownExpanded,
          onDismissRequest = { hostDropdownExpanded = false },
        ) {
          if (hostBoard.isEmpty()) {
            DropdownMenuItem(
              text = { Text("No hosts enabled in Admin") },
              onClick = { hostDropdownExpanded = false },
            )
          } else {
            hostBoard.forEach { item ->
              val (availLabel, availTone) = MainViewModel.resolveHostAvailabilityVisual(item)
              val hostName = item.staff.shownAs.ifBlank { item.staff.fullName }
              val sub =
                listOfNotNull(
                  item.staff.designation.takeIf { it.isNotBlank() },
                  item.staff.formattedSeating().ifBlank { item.staff.cabin }.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
              DropdownMenuItem(
                leadingIcon = {
                  StaffAvatar(
                    name = hostName,
                    photoUrl = item.staff.photoUrl,
                    size = 30.dp,
                    textStyle = MaterialTheme.typography.labelSmall,
                  )
                },
                text = {
                  Column {
                    Text(
                      text = hostName,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    if (sub.isNotBlank()) {
                      Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                },
                trailingIcon = {
                  StatusDotLabel(tone = availTone, text = availLabel)
                },
                onClick = {
                  onHostSelected(item.staff.id)
                  hostDropdownExpanded = false
                },
                modifier = Modifier.testTag("host_dropdown_item_${item.staff.id}"),
              )
            }
          }
        }
      }

      ExposedDropdownMenuBox(
        expanded = reasonDropdownExpanded,
        onExpandedChange = { reasonDropdownExpanded = !reasonDropdownExpanded },
        modifier = Modifier.weight(0.85f),
      ) {
        OutlinedTextField(
          value = form.selectedReason.ifBlank { "Reason" },
          onValueChange = {},
          readOnly = true,
          label = { Text("Reason") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonDropdownExpanded) },
          singleLine = true,
          colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
          modifier =
            Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth()
              .testTag("reason_dropdown_field"),
        )

        ExposedDropdownMenu(
          expanded = reasonDropdownExpanded,
          onDismissRequest = { reasonDropdownExpanded = false },
        ) {
          shownReasons.forEach { reason ->
            DropdownMenuItem(
              text = {
                Text(
                  text = reason.label,
                  fontWeight =
                    if (reason.label == form.selectedReason) FontWeight.Bold
                    else FontWeight.Normal,
                )
              },
              onClick = {
                onReasonSelected(reason.label)
                reasonDropdownExpanded = false
              },
              modifier = Modifier.testTag("reason_chip_${reason.label}"),
            )
          }
        }
      }
    }

    selectedHostItem?.let { item ->
      val host = item.staff
      val (availText, tone) = MainViewModel.resolveHostAvailabilityVisual(item)
      val seating = host.formattedSeating()
      val extraParts =
        listOfNotNull(
          seating.takeIf { it.isNotBlank() },
          host.requiredThings.takeIf { it.isNotBlank() }?.let { "Req: $it" },
          host.notes.takeIf { it.isNotBlank() }?.let { "Note: $it" },
        )
      if (extraParts.isNotEmpty()) {
        GlassInnerSurface(
          shape = RoundedCornerShape(10.dp),
          borderColor = status.dotColor(tone).copy(alpha = 0.35f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            StatusDotLabel(tone = tone, text = availText)
            Text(
              text = extraParts.joinToString(" · "),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }

    // 4. REMARK + DOB COMPACT ROW
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = form.remark,
        onValueChange = onRemarkChanged,
        label = { Text("Remark (opt.)") },
        placeholder = { Text("e.g. 4 PM, parcel") },
        singleLine = true,
        modifier = Modifier.weight(1.1f).testTag("visitor_remark_input"),
      )
      OutlinedTextField(
        value = form.dob,
        onValueChange = onDobChanged,
        label = { Text("DOB (opt.)") },
        placeholder = { Text("mm/dd/yyyy") },
        singleLine = true,
        trailingIcon = {
          IconButton(onClick = { showDobPicker = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.CalendarToday, contentDescription = "Pick date of birth", modifier = Modifier.size(16.dp))
          }
        },
        modifier = Modifier.weight(0.9f).testTag("visitor_dob_input"),
      )
    }

    val ctaLabel =
      buildString {
        append("Check in ")
        append(form.name.trim().ifBlank { "visitor" })
        selectedHost?.let {
          append(" · ")
          append(it.shownAs.ifBlank { it.fullName })
        }
      }

    // 5. ACTION BAR: Check in + Clear
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(
        onClick = onCheckIn,
        enabled = !form.isSubmitting && hostBoard.isNotEmpty(),
        modifier = Modifier.weight(1f).height(50.dp).testTag("check_in_button"),
        shape = RoundedCornerShape(12.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
      ) {
        if (form.isSubmitting) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Text(ctaLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      OutlinedButton(
        onClick = onClearForm,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.height(50.dp).testTag("clear_form_button"),
      ) {
        Text("Clear", style = MaterialTheme.typography.labelMedium)
      }

      OutlinedButton(
        onClick = onBookAppointment,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.height(50.dp).testTag("reception_book_appt_button"),
      ) {
        Icon(Icons.Rounded.EventAvailable, contentDescription = "Book appointment", modifier = Modifier.size(18.dp))
      }
    }
  }
}

@Composable
private fun WhoIsFreeBoardCard(
  hostBoard: List<HostBoardItem>,
  onMarkDone: (Visit) -> Unit,
) {
  val status = archPointStatusColors()
  GlassCard(contentPadding = PaddingValues(16.dp)) {
    Text(
      "WHO IS FREE",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (hostBoard.isEmpty()) {
      Text(
        "No hosts configured yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      val rows = hostBoard.chunked(2)
      rows.forEach { pair ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          pair.forEach { item ->
            val (statusLabel, tone) = MainViewModel.resolveHostAvailabilityVisual(item)
            val toneAccent = status.dotColor(tone)
            GlassInnerSurface(
              modifier = Modifier.weight(1f),
              borderColor = toneAccent.copy(alpha = 0.35f),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  StaffAvatar(
                    name = item.staff.shownAs.ifBlank { item.staff.fullName },
                    photoUrl = item.staff.photoUrl,
                    size = 24.dp,
                    textStyle = MaterialTheme.typography.labelSmall,
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = item.staff.shownAs.ifBlank { item.staff.fullName },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                val seating = item.staff.formattedSeating().ifBlank { item.staff.cabin }
                if (seating.isNotBlank()) {
                  Text(
                    text = seating,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                StatusDotLabel(tone = tone, text = statusLabel)
                if (item.inMeetingVisit != null) {
                  Text(
                    text = "with ${item.inMeetingVisit.visitorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(Modifier.height(2.dp))
                  OutlinedButton(
                    onClick = { onMarkDone(item.inMeetingVisit) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp).testTag("board_mark_done_${item.inMeetingVisit.id}"),
                  ) {
                    Text("Mark done", style = MaterialTheme.typography.labelSmall)
                  }
                } else {
                  Text(
                    text =
                      if (item.waitingCount > 0) "${item.waitingCount} waiting" else "nobody waiting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
          if (pair.size == 1) {
            Spacer(Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun TodayAtOfficeCard(
  visits: List<Visit>,
  nowMillis: Long,
  onRemind: (Visit) -> Unit,
  onSendIn: (Visit) -> Unit,
  onMarkDone: (Visit) -> Unit,
) {
  var subTab by remember { mutableStateOf(TodaySubTab.WAITING) }

  val waiting =
    remember(visits) {
      visits.filter { it.parsedStatus() == VisitStatus.WAITING }
        .sortedBy { it.waitStartedAt?.toDate()?.time ?: nowMillis }
    }
  val inMeeting =
    remember(visits) {
      visits.filter { it.parsedStatus() == VisitStatus.IN_MEETING }
        .sortedByDescending { it.meetingStartedAt?.toDate()?.time ?: nowMillis }
    }
  val doneAndDenied =
    remember(visits) {
      visits.filter {
        val st = it.parsedStatus()
        st == VisitStatus.DONE || st == VisitStatus.DENIED
      }
    }

  GlassCard(contentPadding = PaddingValues(16.dp)) {
    Text(
      "TODAY AT THE OFFICE",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TodayTabPill(
        label = "Waiting ${waiting.size}",
        selected = subTab == TodaySubTab.WAITING,
        onClick = { subTab = TodaySubTab.WAITING },
      )
      TodayTabPill(
        label = "In meeting ${inMeeting.size}",
        selected = subTab == TodaySubTab.IN_MEETING,
        onClick = { subTab = TodaySubTab.IN_MEETING },
      )
      TodayTabPill(
        label = "Done ${doneAndDenied.size}",
        selected = subTab == TodaySubTab.DONE,
        onClick = { subTab = TodaySubTab.DONE },
      )
    }

    val activeList =
      when (subTab) {
        TodaySubTab.WAITING -> waiting
        TodaySubTab.IN_MEETING -> inMeeting
        TodaySubTab.DONE -> doneAndDenied
      }

    if (activeList.isEmpty()) {
      Text(
        text =
          when (subTab) {
            TodaySubTab.WAITING -> "No visitors waiting right now."
            TodaySubTab.IN_MEETING -> "No active meetings right now."
            TodaySubTab.DONE -> "No completed visits yet today."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        activeList.forEach { visit ->
          when (subTab) {
            TodaySubTab.WAITING ->
              TodayWaitingRow(
                visit = visit,
                nowMillis = nowMillis,
                onRemind = { onRemind(visit) },
                onSendIn = { onSendIn(visit) },
              )
            TodaySubTab.IN_MEETING ->
              TodayInMeetingRow(
                visit = visit,
                nowMillis = nowMillis,
                onMarkDone = { onMarkDone(visit) },
              )
            TodaySubTab.DONE -> TodayDoneRow(visit = visit)
          }
        }
      }
    }
  }
}

@Composable
private fun TodayTabPill(label: String, selected: Boolean, onClick: () -> Unit) {
  Surface(
    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    shape = RoundedCornerShape(20.dp),
    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color =
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
    )
  }
}

@Composable
private fun TodayWaitingRow(
  visit: Visit,
  nowMillis: Long,
  onRemind: () -> Unit,
  onSendIn: () -> Unit,
) {
  val status = archPointStatusColors()
  val waitMin = DeskFormatters.minutesBetween(visit.waitStartedAt?.toDate()?.time, nowMillis)
  val isLongWait = waitMin >= 15L
  val subtitleColor = if (isLongWait) status.warningAccent else MaterialTheme.colorScheme.onSurfaceVariant

  GlassInnerSurface(
    borderColor = if (isLongWait) status.warningAccent.copy(alpha = 0.55f) else null,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(36.dp)
              .clip(CircleShape)
              .background(
                if (isLongWait) status.warningAccent.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.primaryContainer
              ),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = DeskFormatters.initials(visit.visitorName),
            style = MaterialTheme.typography.labelMedium,
            color =
              if (isLongWait) status.warningAccent
              else MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
          )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = visit.visitorName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          val waitStatus =
            buildString {
              append("for ${visit.hostShownAs} · ")
              if (visit.waitNote.isNotBlank()) {
                append("${visit.waitNote} · ")
              }
              append("waiting ${waitMin} min · ${visit.reason}")
            }
          Text(
            text = waitStatus,
            style = MaterialTheme.typography.bodySmall,
            color = subtitleColor,
            fontWeight = if (isLongWait) FontWeight.SemiBold else FontWeight.Normal,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (isLongWait) {
          Button(
            onClick = onRemind,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(40.dp).testTag("remind_button_${visit.id}"),
          ) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Remind")
          }
        } else {
          OutlinedButton(
            onClick = onRemind,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(40.dp).testTag("remind_button_${visit.id}"),
          ) {
            Text("Remind")
          }
        }

        OutlinedButton(
          onClick = onSendIn,
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier.height(40.dp).testTag("sent_in_button_${visit.id}"),
        ) {
          Text("Sent in")
        }
      }
    }
  }
}

@Composable
private fun TodayInMeetingRow(
  visit: Visit,
  nowMillis: Long,
  onMarkDone: () -> Unit,
) {
  val status = archPointStatusColors()
  val meetingMin = DeskFormatters.minutesBetween(visit.meetingStartedAt?.toDate()?.time, nowMillis)
  GlassInnerSurface {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(visit.visitorName, style = MaterialTheme.typography.titleSmall)
        Text(
          "with ${visit.hostShownAs} · ${meetingMin} min · ${visit.reason}",
          style = MaterialTheme.typography.bodySmall,
          color = status.meetingText,
        )
      }
      OutlinedButton(
        onClick = onMarkDone,
        modifier = Modifier.height(40.dp).testTag("meeting_done_button_${visit.id}"),
      ) {
        Text("Meeting done")
      }
    }
  }
}

@Composable
private fun TodayDoneRow(visit: Visit) {
  val status = archPointStatusColors()
  val isDenied = visit.parsedStatus() == VisitStatus.DENIED
  val inTime = DeskFormatters.formatTime(visit.meetingStartedAt?.toDate() ?: visit.arrivedAt?.toDate())
  val outTime = DeskFormatters.formatTime(visit.endedAt?.toDate())

  GlassInnerSurface {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(visit.visitorName, style = MaterialTheme.typography.titleSmall)
        Text(
          if (isDenied) {
            "${visit.hostShownAs} · can't meet today"
          } else {
            "met ${visit.hostShownAs} · $inTime – $outTime"
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      StatusPill(
        text = visit.status,
        containerColor = if (isDenied) status.deniedContainer else status.freeContainer,
        contentColor = if (isDenied) status.deniedOnContainer else status.freeOnContainer,
      )
    }
  }
}

@Composable
private fun UpcomingAppointmentsCard(
  appointments: List<Appointment>,
  nowMillis: Long,
  isBusy: Boolean,
  onBookNew: () -> Unit,
  onCheckInAppointment: (Appointment) -> Unit,
  onEditAppointment: (Appointment) -> Unit,
  onCancelAppointment: (Appointment) -> Unit,
) {
  GlassCard(contentPadding = PaddingValues(16.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "UPCOMING APPOINTMENTS (${appointments.size})",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Pre-booked guests ready for 1-tap check-in",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.width(8.dp))
      OutlinedButton(
        onClick = onBookNew,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(38.dp).testTag("reception_upcoming_book_button"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Book")
      }
    }

    if (appointments.isEmpty()) {
      Text(
        text = "No scheduled appointments yet. Hosts or Reception can book upcoming visits anytime.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        appointments.forEach { appt ->
          val dayLabel = DeskFormatters.formatAppointmentDayLabel(appt.scheduledDateIso, nowMillis)
          val isToday = dayLabel == "Today"
          GlassInnerSurface(
            modifier = Modifier.fillMaxWidth().testTag("reception_appt_row_${appt.id}"),
            borderColor =
              if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
              else null,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  StaffAvatar(
                    name = appt.hostShownAs.ifBlank { "Host" },
                    photoUrl = "",
                    size = 34.dp,
                    textStyle = MaterialTheme.typography.labelSmall,
                  )
                  Spacer(Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = appt.visitorName,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    val meta =
                      listOfNotNull(
                        appt.visitorCompany.takeIf { it.isNotBlank() },
                        "for ${appt.hostShownAs}".takeIf { appt.hostShownAs.isNotBlank() },
                        appt.purpose.takeIf { it.isNotBlank() },
                      ).joinToString(" · ")
                    Text(
                      text = meta,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(
                  text = "$dayLabel · ${appt.scheduledTimeLabel}",
                  containerColor =
                    if (isToday) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                  contentColor =
                    if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              if (appt.location.isNotBlank() || appt.notes.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (appt.location.isNotBlank()) {
                    Icon(
                      Icons.Rounded.LocationOn,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                  }
                  Text(
                    text =
                      listOfNotNull(
                        appt.location.takeIf { it.isNotBlank() },
                        appt.notes.takeIf { it.isNotBlank() },
                      ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Button(
                  onClick = { onCheckInAppointment(appt) },
                  enabled = !isBusy,
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier.weight(1.2f).height(40.dp).testTag("reception_appt_checkin_${appt.id}"),
                ) {
                  Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Check in", maxLines = 1)
                }
                OutlinedButton(
                  onClick = { onEditAppointment(appt) },
                  enabled = !isBusy,
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                  modifier = Modifier.weight(0.75f).height(40.dp).testTag("reception_appt_edit_${appt.id}"),
                ) {
                  Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Edit", maxLines = 1)
                }
                OutlinedButton(
                  onClick = { onCancelAppointment(appt) },
                  enabled = !isBusy,
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                  modifier = Modifier.weight(0.8f).height(40.dp).testTag("reception_appt_cancel_${appt.id}"),
                ) {
                  Text("Cancel", maxLines = 1)
                }
              }
            }
          }
        }
      }
    }
  }
}
