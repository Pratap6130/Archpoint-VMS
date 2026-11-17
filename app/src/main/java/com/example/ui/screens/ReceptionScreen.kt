package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.data.model.Visit
import com.example.data.model.VisitStatus
import com.example.data.model.VisitorProfile
import com.example.ui.components.FreeDotLabel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassInnerSurface
import com.example.ui.components.GlassSegmentedControl
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.DeskSnapshot
import com.example.ui.viewmodel.HostBoardItem
import com.example.ui.viewmodel.ReceptionFormState
import com.example.ui.viewmodel.VisitorSearchState
import com.example.util.DeskFormatters

private enum class ReceptionPane {
  CHECK_IN,
  DESK_QUEUE,
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
  hostBoard: List<HostBoardItem>,
  nowMillis: Long,
  onSearchQueryChanged: (String) -> Unit,
  onTriggerSearch: () -> Unit,
  onClearSearch: () -> Unit,
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
            hostBoard = hostBoard,
            onSearchQueryChanged = onSearchQueryChanged,
            onTriggerSearch = onTriggerSearch,
            onClearSearch = onClearSearch,
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
          )
        }
        Column(
          modifier = Modifier.weight(0.95f),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          WhoIsFreeBoardCard(hostBoard = hostBoard, onMarkDone = onMarkDone)
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        GlassSegmentedControl(
          options =
            listOf(
              ReceptionPane.CHECK_IN to "New check-in",
              ReceptionPane.DESK_QUEUE to
                if (activeQueueCount > 0) "Desk queue ($activeQueueCount)" else "Who's free & queue",
            ),
          selected = activePane,
          onSelect = { activePane = it },
          tagPrefix = "reception_pane",
        )

        if (activePane == ReceptionPane.CHECK_IN) {
          FreeHostsQuickStrip(hostBoard = hostBoard, onSelectHost = onHostSelected)
          VisitorEntryCard(
            snapshot = snapshot,
            form = form,
            searchState = searchState,
            hostBoard = hostBoard,
            onSearchQueryChanged = onSearchQueryChanged,
            onTriggerSearch = onTriggerSearch,
            onClearSearch = onClearSearch,
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
          )
        } else {
          WhoIsFreeBoardCard(hostBoard = hostBoard, onMarkDone = onMarkDone)
          TodayAtOfficeCard(
            visits = todaysVisits,
            nowMillis = nowMillis,
            onRemind = onRemind,
            onSendIn = onSendIn,
            onMarkDone = onMarkDone,
          )
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun FreeHostsQuickStrip(
  hostBoard: List<HostBoardItem>,
  onSelectHost: (String) -> Unit,
) {
  if (hostBoard.isEmpty()) return
  val status = archPointStatusColors()
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    hostBoard.forEach { item ->
      val isFree = item.inMeetingVisit == null
      val dotColor = if (isFree) status.freeText else status.meetingText
      GlassInnerSurface(
        modifier =
          Modifier.clip(RoundedCornerShape(20.dp))
            .clickable { onSelectHost(item.staff.id) },
        shape = RoundedCornerShape(20.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
          Spacer(Modifier.width(6.dp))
          Text(
            text =
              buildString {
                append(item.staff.shownAs.ifBlank { item.staff.fullName })
                if (item.staff.cabin.isNotBlank()) {
                  append(" · ${item.staff.cabin}")
                }
              },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
  hostBoard: List<HostBoardItem>,
  onSearchQueryChanged: (String) -> Unit,
  onTriggerSearch: () -> Unit,
  onClearSearch: () -> Unit,
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
) {
  val status = archPointStatusColors()
  var showDobPicker by remember { mutableStateOf(false) }
  var hostFilterQuery by remember { mutableStateOf("") }
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
  val filteredHosts =
    remember(hostBoard, hostFilterQuery) {
      val q = hostFilterQuery.trim().lowercase()
      if (q.isEmpty()) {
        hostBoard
      } else {
        hostBoard.filter {
          it.staff.shownAs.lowercase().contains(q) ||
            it.staff.fullName.lowercase().contains(q) ||
            it.staff.cabin.lowercase().contains(q) ||
            it.staff.floor.lowercase().contains(q) ||
            it.staff.designation.lowercase().contains(q) ||
            it.staff.department.lowercase().contains(q)
        }
      }
    }
  val selectedHost = hostBoard.firstOrNull { it.staff.id == form.selectedHostId }?.staff

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

  GlassCard {
    // 1. SEARCH BAR AT THE VERY TOP
    OutlinedTextField(
      value = searchState.query,
      onValueChange = onSearchQueryChanged,
      label = { Text("Search returning visitor") },
      placeholder = { Text("Name, phone, or email to auto-fill...") },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = "Search returning visitors",
        )
      },
      trailingIcon = {
        if (searchState.isSearching) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
          )
        } else if (searchState.query.isNotEmpty()) {
          IconButton(onClick = onClearSearch) {
            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
          }
        }
      },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { onTriggerSearch() }),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("returning_visitor_search_input"),
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
                        profile.visitorJobProfile.takeIf { it.isNotBlank() },
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
        shape = RoundedCornerShape(12.dp),
        containerColor = status.freeContainer,
        borderColor = status.freeText.copy(alpha = 0.4f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = status.freeText,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Returning · $nextVisitLabel visit",
              style = MaterialTheme.typography.bodySmall,
              color = status.freeOnContainer,
              fontWeight = FontWeight.SemiBold,
            )
          }
          TextButton(
            onClick = onClearReturningMatch,
            modifier = Modifier.testTag("not_them_button"),
          ) {
            Text("Not them?", color = status.freeText, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    // 2. EXACT ORDER: Name -> Org -> Reason to meet -> DOB -> Phone -> Email -> Person to meet
    OutlinedTextField(
      value = form.name,
      onValueChange = onNameChanged,
      label = { Text("Name") },
      placeholder = { Text("Visitor full name") },
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("visitor_name_input"),
    )

    if (nameSuggestions.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Rounded.PersonSearch,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
          Spacer(Modifier.width(6.dp))
          Text(
            "Matching \"${form.name.trim()}\"",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          nameSuggestions.forEach { profile ->
            FilterChip(
              selected = false,
              onClick = { onApplySuggestion(profile) },
              label = {
                val comp = profile.company.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                Text("${profile.name}$comp")
              },
            )
          }
        }
      }
    }

    OutlinedTextField(
      value = form.company,
      onValueChange = onCompanyChanged,
      label = { Text("Org") },
      placeholder = { Text("Company or organization (optional)") },
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("visitor_company_input"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Reason to meet", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        shownReasons.forEach { reason ->
          val selected = form.selectedReason == reason.label
          FilterChip(
            selected = selected,
            onClick = { onReasonSelected(reason.label) },
            label = { Text(reason.label) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.testTag("reason_chip_${reason.label}"),
          )
        }
      }
    }

    OutlinedTextField(
      value = form.dob,
      onValueChange = onDobChanged,
      label = { Text("DOB (optional)") },
      placeholder = { Text("mm/dd/yyyy") },
      singleLine = true,
      trailingIcon = {
        Row {
          if (form.dob.isNotEmpty()) {
            IconButton(onClick = { onDobChanged("") }) {
              Icon(Icons.Rounded.Clear, contentDescription = "Clear date of birth")
            }
          }
          IconButton(onClick = { showDobPicker = true }) {
            Icon(Icons.Rounded.CalendarToday, contentDescription = "Pick date of birth")
          }
        }
      },
      modifier = Modifier.fillMaxWidth().testTag("visitor_dob_input"),
    )

    OutlinedTextField(
      value = form.mobile,
      onValueChange = onMobileChanged,
      label = { Text("Phone (10 digits)") },
      placeholder = { Text("9829012345") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFamily),
      modifier = Modifier.fillMaxWidth().testTag("visitor_mobile_input"),
    )

    OutlinedTextField(
      value = form.email,
      onValueChange = onEmailChanged,
      label = { Text("Email (optional)") },
      placeholder = { Text("visitor@company.com") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("visitor_email_input"),
    )

    OutlinedTextField(
      value = form.remark,
      onValueChange = onRemarkChanged,
      label = { Text("Remark / note (optional)") },
      placeholder = { Text("e.g. Gift, 3rd floor, scheduled for 4 PM") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("visitor_remark_input"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Person to meet", style = MaterialTheme.typography.labelLarge)
      if (hostBoard.size > 4) {
        OutlinedTextField(
          value = hostFilterQuery,
          onValueChange = { hostFilterQuery = it },
          placeholder = { Text("Filter host, floor, or cabin...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("host_filter_input"),
        )
      }
      if (filteredHosts.isEmpty()) {
        Text(
          if (hostBoard.isEmpty()) {
            "No hosts listed yet. Enable \"Visitors can ask for them\" in Admin."
          } else {
            "No matching hosts."
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        val rows = filteredHosts.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          rows.forEach { pair ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              pair.forEach { item ->
                HostSelectTile(
                  item = item,
                  selected = item.staff.id == form.selectedHostId,
                  onClick = { onHostSelected(item.staff.id) },
                  modifier = Modifier.weight(1f),
                )
              }
              if (pair.size == 1) {
                Spacer(Modifier.weight(1f))
              }
            }
          }
        }
      }

      selectedHost?.let { host ->
        val seating = host.formattedSeating()
        val extraParts =
          listOfNotNull(
            host.designation.takeIf { it.isNotBlank() }?.let { d ->
              if (host.department.isNotBlank()) "$d (${host.department})" else d
            },
            seating.takeIf { it.isNotBlank() },
            host.otherLocation.takeIf { it.isNotBlank() }?.let { "Alt: $it" },
            host.requiredThings.takeIf { it.isNotBlank() }?.let { "Required: $it" },
            host.notes.takeIf { it.isNotBlank() }?.let { "Note: $it" },
          )
        if (extraParts.isNotEmpty()) {
          GlassInnerSurface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = extraParts.joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }
    }

    val ctaLabel =
      buildString {
        append("Check in ")
        append(form.name.trim().ifBlank { "visitor" })
        selectedHost?.let {
          append(" for ")
          append(it.shownAs.ifBlank { it.fullName })
        }
      }

    Button(
      onClick = onCheckIn,
      enabled = !form.isSubmitting && hostBoard.isNotEmpty(),
      modifier = Modifier.fillMaxWidth().height(54.dp).testTag("check_in_button"),
      shape = RoundedCornerShape(14.dp),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
      if (form.isSubmitting) {
        CircularProgressIndicator(
          modifier = Modifier.size(22.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          strokeWidth = 2.dp,
        )
      } else {
        Text(ctaLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      TextButton(
        onClick = onClearForm,
        modifier = Modifier.testTag("clear_form_button"),
      ) {
        Text("Clear the form", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun HostSelectTile(
  item: HostBoardItem,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bgColor =
    if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
  val titleColor =
    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
  val subColor =
    if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
    else MaterialTheme.colorScheme.onSurfaceVariant

  val statusText =
    buildString {
      if (item.inMeetingVisit != null) {
        append("In meeting · ${item.meetingMinutes}m")
      } else {
        append("Free")
      }
      if (item.waitingCount > 0) {
        append(" · ${item.waitingCount} waiting")
      }
    }

  Surface(
    modifier =
      modifier
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick)
        .testTag("host_tile_${item.staff.id}"),
    shape = RoundedCornerShape(12.dp),
    color = bgColor,
    border =
      BorderStroke(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Text(
        text = item.staff.shownAs.ifBlank { item.staff.fullName },
        style = MaterialTheme.typography.titleSmall,
        color = titleColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val seatingText = item.staff.formattedSeating().ifBlank { item.staff.cabin }
      if (seatingText.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Rounded.Chair,
            contentDescription = null,
            tint = subColor,
            modifier = Modifier.size(12.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = seatingText,
            style = MaterialTheme.typography.labelSmall,
            color = subColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Spacer(Modifier.height(2.dp))
      Text(
        text = statusText,
        style = MaterialTheme.typography.bodySmall,
        color = subColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun WhoIsFreeBoardCard(
  hostBoard: List<HostBoardItem>,
  onMarkDone: (Visit) -> Unit,
) {
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
            GlassInnerSurface(modifier = Modifier.weight(1f)) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = item.staff.shownAs.ifBlank { item.staff.fullName },
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
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
                if (item.inMeetingVisit != null) {
                  FreeDotLabel(
                    isFree = false,
                    text = "In a meeting · ${item.meetingMinutes} min",
                  )
                  Text(
                    text = "with ${item.inMeetingVisit.visitorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(Modifier.height(4.dp))
                  OutlinedButton(
                    onClick = { onMarkDone(item.inMeetingVisit) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp).testTag("board_mark_done_${item.inMeetingVisit.id}"),
                  ) {
                    Text("Mark done", style = MaterialTheme.typography.labelSmall)
                  }
                } else {
                  FreeDotLabel(isFree = true, text = "Free")
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
