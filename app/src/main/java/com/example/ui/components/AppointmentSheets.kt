package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Appointment
import com.example.data.model.ArchPointSeedData
import com.example.data.model.StaffMember
import com.example.ui.viewmodel.MainViewModel
import com.example.util.DeskFormatters
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookAppointmentBottomSheet(
  existing: Appointment?,
  assignableHosts: List<StaffMember>,
  isHostOnly: Boolean,
  currentStaff: StaffMember?,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
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
  ) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val todayIso = remember { DeskFormatters.formatIsoDate(System.currentTimeMillis()) }
  val tomorrowIso =
    remember {
      val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
      DeskFormatters.formatIsoDate(cal.timeInMillis)
    }

  var visitorName by remember(existing?.id) { mutableStateOf(existing?.visitorName.orEmpty()) }
  var visitorMobile by remember(existing?.id) { mutableStateOf(existing?.visitorMobile.orEmpty()) }
  var visitorCompany by remember(existing?.id) { mutableStateOf(existing?.visitorCompany.orEmpty()) }
  var visitorEmail by remember(existing?.id) { mutableStateOf(existing?.visitorEmail.orEmpty()) }
  var purpose by remember(existing?.id) { mutableStateOf(existing?.purpose?.ifBlank { "Meeting" } ?: "Meeting") }
  var location by remember(existing?.id) {
    mutableStateOf(
      existing?.location?.ifBlank {
        currentStaff?.formattedSeating()?.ifBlank { currentStaff.cabin }.orEmpty()
      } ?: if (isHostOnly) {
        currentStaff?.formattedSeating()?.ifBlank { currentStaff.cabin }.orEmpty()
      } else {
        ""
      }
    )
  }
  var scheduledDateIso by remember(existing?.id) {
    mutableStateOf(existing?.scheduledDateIso?.ifBlank { todayIso } ?: todayIso)
  }
  var scheduledTimeLabel by remember(existing?.id) {
    mutableStateOf(existing?.scheduledTimeLabel?.ifBlank { "11:30 AM" } ?: "11:30 AM")
  }
  var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }

  val initialHost =
    remember(existing?.id, assignableHosts, currentStaff?.id, isHostOnly) {
      if (isHostOnly) currentStaff
      else {
        assignableHosts.firstOrNull {
          (existing?.hostStaffId?.isNotBlank() == true && it.id == existing.hostStaffId) ||
            (existing?.hostEmail?.isNotBlank() == true &&
              it.email.equals(existing.hostEmail, ignoreCase = true))
        } ?: assignableHosts.firstOrNull() ?: currentStaff
      }
    }
  var selectedHost by remember(existing?.id, assignableHosts, currentStaff?.id) {
    mutableStateOf(initialHost)
  }
  var hostDropdownExpanded by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }

  val purposePresets =
    remember { listOf("Meeting", "Site Review", "Client Brief", "VIP / PR", "Vendor") }
  val timePresets =
    remember { listOf("10:00 AM", "11:30 AM", "01:00 PM", "03:00 PM", "04:30 PM", "06:00 PM") }

  val targetHostId =
    if (isHostOnly) currentStaff?.id.orEmpty()
    else selectedHost?.id ?: existing?.hostStaffId.orEmpty()
  val canSave =
    MainViewModel.validateAppointment(
      visitorName = visitorName,
      scheduledDateIso = scheduledDateIso,
      scheduledTimeLabel = scheduledTimeLabel,
      hostStaffId = targetHostId,
    ) == null && !isBusy

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              scheduledDateIso = DeskFormatters.formatIsoDate(millis)
            }
            showDatePicker = false
          }
        ) {
          Text("OK")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Rounded.EventAvailable,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = if (existing == null) "Book Appointment" else "Reschedule Appointment",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      }

      if (isHostOnly) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Booked for ${currentStaff?.shownAs?.ifBlank { currentStaff.fullName } ?: "you"} · visible only to you & reception",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      } else if (assignableHosts.isNotEmpty()) {
        ExposedDropdownMenuBox(
          expanded = hostDropdownExpanded,
          onExpandedChange = { hostDropdownExpanded = !hostDropdownExpanded },
          modifier = Modifier.fillMaxWidth().testTag("appt_host_dropdown"),
        ) {
          val label =
            selectedHost?.let { host ->
              val name = host.shownAs.ifBlank { host.fullName }
              val seat = host.formattedSeating().ifBlank { host.cabin }
              if (seat.isNotBlank()) "$name · $seat" else name
            } ?: "Select host *"
          OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Person to meet *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostDropdownExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
          )
          ExposedDropdownMenu(
            expanded = hostDropdownExpanded,
            onDismissRequest = { hostDropdownExpanded = false },
          ) {
            assignableHosts.forEach { host ->
              DropdownMenuItem(
                leadingIcon = {
                  StaffAvatar(
                    name = host.shownAs.ifBlank { host.fullName },
                    photoUrl = host.photoUrl,
                    size = 28.dp,
                    textStyle = MaterialTheme.typography.labelSmall,
                  )
                },
                text = {
                  Column {
                    Text(
                      text = host.shownAs.ifBlank { host.fullName },
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                    )
                    val seat = host.formattedSeating().ifBlank { host.cabin }
                    if (seat.isNotBlank()) {
                      Text(
                        text = seat,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                },
                onClick = {
                  selectedHost = host
                  if (location.isBlank()) {
                    location = host.formattedSeating().ifBlank { host.cabin }
                  }
                  hostDropdownExpanded = false
                },
              )
            }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = visitorName,
          onValueChange = { visitorName = it },
          label = { Text("Visitor / client name *") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.weight(1f).testTag("appt_visitor_name_input"),
        )
        OutlinedTextField(
          value = visitorCompany,
          onValueChange = { visitorCompany = it },
          label = { Text("Org / Company") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.weight(1f).testTag("appt_visitor_company_input"),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = visitorMobile,
          onValueChange = { visitorMobile = it.filter { ch -> ch.isDigit() || ch == '+' }.take(15) },
          label = { Text("Mobile (opt.)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          modifier = Modifier.weight(0.46f).testTag("appt_visitor_mobile_input"),
        )
        OutlinedTextField(
          value = visitorEmail,
          onValueChange = { visitorEmail = it },
          label = { Text("Email (opt.)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          modifier = Modifier.weight(0.54f).testTag("appt_visitor_email_input"),
        )
      }

      Text("Date", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilterChip(
          selected = scheduledDateIso == todayIso,
          onClick = { scheduledDateIso = todayIso },
          label = { Text("Today") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
          modifier = Modifier.testTag("appt_date_today_chip"),
        )
        FilterChip(
          selected = scheduledDateIso == tomorrowIso,
          onClick = { scheduledDateIso = tomorrowIso },
          label = { Text("Tomorrow") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
          modifier = Modifier.testTag("appt_date_tomorrow_chip"),
        )
        OutlinedButton(
          onClick = { showDatePicker = true },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.height(34.dp).testTag("appt_pick_date_button"),
        ) {
          Icon(Icons.Rounded.CalendarMonth, contentDescription = "Pick appointment date", modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(5.dp))
          Text(
            text = DeskFormatters.formatAppointmentDayLabel(scheduledDateIso),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = scheduledDateIso,
          onValueChange = { scheduledDateIso = it },
          label = { Text("Date (yyyy-MM-dd) *") },
          singleLine = true,
          trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
              Icon(Icons.Rounded.CalendarMonth, contentDescription = "Calendar")
            }
          },
          modifier = Modifier.weight(1f).testTag("appt_date_input"),
        )
        OutlinedTextField(
          value = scheduledTimeLabel,
          onValueChange = { scheduledTimeLabel = it },
          label = { Text("Time slot *") },
          placeholder = { Text("11:30 AM") },
          singleLine = true,
          modifier = Modifier.weight(0.9f).testTag("appt_time_input"),
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        timePresets.forEach { slot ->
          FilterChip(
            selected = scheduledTimeLabel.equals(slot, ignoreCase = true),
            onClick = { scheduledTimeLabel = slot },
            label = { Text(slot) },
          )
        }
      }

      Text("Purpose", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        purposePresets.forEach { preset ->
          FilterChip(
            selected = purpose.equals(preset, ignoreCase = true),
            onClick = { purpose = preset },
            label = { Text(preset) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      OutlinedTextField(
        value = location,
        onValueChange = { location = it },
        label = { Text("Meeting room / cabin") },
        placeholder = { Text("Conference (2nd Floor), Studio...") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("appt_location_input"),
      )

      val selectedPresetId =
        remember(location) {
          ArchPointSeedData.locationPresets
            .firstOrNull { preset -> preset.matchesLocationText(location) }
            ?.id
        }
      LocationPresetScrollBar(
        title = "Quick studio room / cabin",
        selectedPresetId = selectedPresetId,
        onSelectPreset = { preset -> location = preset.chipText },
        onClearSelection = { location = "" },
        tagPrefix = "appt_loc",
      )

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Agenda / preparation notes") },
        minLines = 2,
        maxLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("appt_notes_input"),
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.weight(0.8f).height(50.dp).testTag("appt_cancel_button"),
        ) {
          Text("Cancel")
        }
        Button(
          onClick = {
            onSave(
              visitorName,
              visitorMobile,
              visitorCompany,
              visitorEmail,
              purpose,
              location,
              scheduledDateIso,
              scheduledTimeLabel,
              notes,
              selectedHost,
            )
            onDismiss()
          },
          enabled = canSave,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1.2f).height(50.dp).testTag("appt_save_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (existing == null) "Confirm booking" else "Save changes")
          }
        }
      }
    }
  }
}
