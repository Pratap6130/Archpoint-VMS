package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ArchPointSeedData
import com.example.data.model.PRCampaign
import com.example.data.model.PRCampaignStatus
import com.example.data.model.PRChannel
import com.example.data.model.PRContact
import com.example.data.model.PRContactTier
import com.example.data.model.PRFollowUpStatus
import com.example.data.model.PRGift
import com.example.data.model.PRGiftDirection
import com.example.data.model.PRGiftStatus
import com.example.data.model.StaffMember
import com.example.data.model.VisitorProfile
import com.example.ui.viewmodel.HostClientSummary
import com.example.ui.viewmodel.MainViewModel
import com.example.util.DeskFormatters
import com.example.util.StaffPhotoCodec
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PRCampaignEditorSheet(
  existing: PRCampaign?,
  assignableStaff: List<StaffMember>,
  isHostOnly: Boolean,
  currentStaffName: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    title: String,
    channel: PRChannel,
    status: PRCampaignStatus,
    targetDateLabel: String,
    location: String,
    audienceSummary: String,
    notes: String,
    ownerStaff: StaffMember?,
  ) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
  var channel by remember(existing?.id) { mutableStateOf(existing?.parsedChannel() ?: PRChannel.EVENT) }
  var status by remember(existing?.id) { mutableStateOf(existing?.parsedStatus() ?: PRCampaignStatus.PLANNED) }
  var targetDateLabel by remember(existing?.id) { mutableStateOf(existing?.targetDateLabel.orEmpty()) }
  var location by remember(existing?.id) { mutableStateOf(existing?.location.orEmpty()) }
  var audienceSummary by remember(existing?.id) { mutableStateOf(existing?.audienceSummary.orEmpty()) }
  var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }

  val initialOwner =
    remember(existing?.id, assignableStaff) {
      assignableStaff.firstOrNull {
        (existing?.ownerStaffId?.isNotBlank() == true && it.id == existing.ownerStaffId) ||
          (existing?.ownerEmail?.isNotBlank() == true &&
            it.email.equals(existing.ownerEmail, ignoreCase = true))
      } ?: assignableStaff.firstOrNull()
    }
  var selectedOwner by remember(existing?.id, assignableStaff) { mutableStateOf(initialOwner) }
  val canSave = title.trim().isNotEmpty() && !isBusy

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
      Text(
        text = if (existing == null) "New PR Campaign / Event" else "Edit PR Campaign",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Campaign or event title *") },
        placeholder = { Text("e.g. Jaipur Monograph Launch, Press Meet") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("pr_campaign_title_input"),
      )

      Text("Channel", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PRChannel.entries.forEach { option ->
          FilterChip(
            selected = channel == option,
            onClick = { channel = option },
            label = { Text(option.label) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      Text("Stage", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PRCampaignStatus.entries.forEach { st ->
          FilterChip(
            selected = status == st,
            onClick = { status = st },
            label = { Text(st.label) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = targetDateLabel,
          onValueChange = { targetDateLabel = it },
          label = { Text("Target date / day") },
          placeholder = { Text("e.g. 28 Nov") },
          singleLine = true,
          modifier = Modifier.weight(0.45f).testTag("pr_campaign_date_input"),
        )
        OutlinedTextField(
          value = location,
          onValueChange = { location = it },
          label = { Text("Venue / studio space") },
          placeholder = { Text("Studio / Conference") },
          singleLine = true,
          modifier = Modifier.weight(0.55f).testTag("pr_campaign_location_input"),
        )
      }

      val selectedPresetId =
        remember(location) {
          ArchPointSeedData.locationPresets
            .firstOrNull { preset -> preset.matchesLocationText(location) }
            ?.id
        }
      LocationPresetScrollBar(
        title = "Quick studio venue",
        selectedPresetId = selectedPresetId,
        onSelectPreset = { preset -> location = preset.chipText },
        onClearSelection = { location = "" },
        tagPrefix = "pr_campaign_loc",
      )

      if (!isHostOnly && assignableStaff.isNotEmpty()) {
        OwnerHostDropdown(
          staff = assignableStaff,
          selected = selectedOwner,
          onSelect = { selectedOwner = it },
          testTag = "pr_campaign_owner_dropdown",
        )
      } else {
        Text(
          text = "Lead host: $currentStaffName (your PR workspace)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      OutlinedTextField(
        value = audienceSummary,
        onValueChange = { audienceSummary = it },
        label = { Text("Target audience / media outlets") },
        placeholder = { Text("e.g. Architecture press, Tier-1 builders, VIP patrons") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("pr_campaign_audience_input"),
      )

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Key brief / hospitality notes") },
        minLines = 2,
        maxLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("pr_campaign_notes_input"),
      )

      SheetSaveCancelRow(
        saveLabel = if (existing == null) "Create campaign" else "Save changes",
        canSave = canSave,
        isBusy = isBusy,
        onDismiss = onDismiss,
        onSave = {
          onSave(
            title,
            channel,
            status,
            targetDateLabel,
            location,
            audienceSummary,
            notes,
            selectedOwner,
          )
          onDismiss()
        },
        saveTag = "pr_campaign_save_button",
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PRContactEditorSheet(
  existing: PRContact?,
  assignableStaff: List<StaffMember>,
  isHostOnly: Boolean,
  currentStaffName: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    organization: String,
    designation: String,
    tier: PRContactTier,
    mobile: String,
    email: String,
    followUpStatus: PRFollowUpStatus,
    nextFollowUpDate: String,
    photoUrl: String,
    dob: String,
    anniversaryDate: String,
    specialGiftPreference: String,
    cityOrAddress: String,
    notes: String,
    ownerStaff: StaffMember?,
  ) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
  var organization by remember(existing?.id) { mutableStateOf(existing?.organization.orEmpty()) }
  var designation by remember(existing?.id) { mutableStateOf(existing?.designation.orEmpty()) }
  var tier by remember(existing?.id) { mutableStateOf(existing?.parsedTier() ?: PRContactTier.VIP) }
  var mobile by remember(existing?.id) { mutableStateOf(existing?.mobile.orEmpty()) }
  var email by remember(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }
  var followUpStatus by remember(existing?.id) {
    mutableStateOf(existing?.parsedFollowUpStatus() ?: PRFollowUpStatus.WARM)
  }
  var nextFollowUpDate by remember(existing?.id) { mutableStateOf(existing?.nextFollowUpDate.orEmpty()) }
  var photoUrl by remember(existing?.id) { mutableStateOf(existing?.photoUrl.orEmpty()) }
  var isEncodingPhoto by remember { mutableStateOf(false) }
  var photoError by remember { mutableStateOf<String?>(null) }
  var dob by remember(existing?.id) { mutableStateOf(existing?.dob.orEmpty()) }
  var anniversaryDate by remember(existing?.id) { mutableStateOf(existing?.anniversaryDate.orEmpty()) }
  var specialGiftPreference by remember(existing?.id) {
    mutableStateOf(existing?.specialGiftPreference.orEmpty())
  }
  var cityOrAddress by remember(existing?.id) { mutableStateOf(existing?.cityOrAddress.orEmpty()) }
  var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
  var showDobPicker by remember { mutableStateOf(false) }

  val photoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          isEncodingPhoto = true
          photoError = null
          StaffPhotoCodec.encodePickedPhotoToDataUri(context, uri)
            .onSuccess { encoded -> photoUrl = encoded }
            .onFailure { err -> photoError = err.localizedMessage ?: "Failed to load photo" }
          isEncodingPhoto = false
        }
      }
    }

  val giftPrefPresets =
    remember { listOf("Studio Monograph", "Brass Diya Hamper", "Sugar-free treats", "Personal note") }

  val initialOwner =
    remember(existing?.id, assignableStaff) {
      assignableStaff.firstOrNull {
        (existing?.ownerStaffId?.isNotBlank() == true && it.id == existing.ownerStaffId) ||
          (existing?.ownerEmail?.isNotBlank() == true &&
            it.email.equals(existing.ownerEmail, ignoreCase = true))
      } ?: assignableStaff.firstOrNull()
    }
  var selectedOwner by remember(existing?.id, assignableStaff) { mutableStateOf(initialOwner) }
  val canSave =
    MainViewModel.validatePrContact(name = name, email = email, mobile = mobile) == null &&
      !isBusy &&
      !isEncodingPhoto

  if (showDobPicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDobPicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              dob = DeskFormatters.formatDisplayDateField(millis)
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
      Text(
        text = if (existing == null) "Add PR / Media Contact" else "Edit PR Contact",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      // Profile Photo Upload Row
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          StaffAvatar(
            name = name.ifBlank { "PR" },
            photoUrl = photoUrl,
            size = 56.dp,
            textStyle = MaterialTheme.typography.titleMedium,
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Member Profile Photo",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = if (photoUrl.isBlank()) "Upload portrait for wishes & VIP lookup" else "Photo ready",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            photoError?.let { err ->
              Text(
                text = err,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
          OutlinedButton(
            onClick = {
              photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            enabled = !isEncodingPhoto,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(40.dp).testTag("pr_contact_upload_photo_button"),
          ) {
            if (isEncodingPhoto) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(5.dp))
              Text(if (photoUrl.isBlank()) "Upload" else "Change")
            }
          }
          if (photoUrl.isNotBlank()) {
            IconButton(
              onClick = { photoUrl = "" },
              modifier = Modifier.size(40.dp).testTag("pr_contact_remove_photo_button"),
            ) {
              Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Remove profile photo",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Full name *") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth().testTag("pr_contact_name_input"),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = organization,
          onValueChange = { organization = it },
          label = { Text("Outlet / Company") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.weight(1f).testTag("pr_contact_org_input"),
        )
        OutlinedTextField(
          value = designation,
          onValueChange = { designation = it },
          label = { Text("Role / Beat") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.weight(1f).testTag("pr_contact_designation_input"),
        )
      }

      Text("Category Tier", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PRContactTier.entries.forEach { option ->
          FilterChip(
            selected = tier == option,
            onClick = { tier = option },
            label = { Text(option.label) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = mobile,
          onValueChange = { mobile = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }.take(20) },
          label = { Text("Phone / WhatsApp") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          modifier = Modifier.weight(0.46f).testTag("pr_contact_mobile_input"),
        )
        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          label = { Text("Email") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          modifier = Modifier.weight(0.54f).testTag("pr_contact_email_input"),
        )
      }

      Text("Birthdays, Wishes & Special Gifts", style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = dob,
          onValueChange = { dob = it },
          label = { Text("Birthday (DOB)") },
          placeholder = { Text("MM/dd/yyyy or 14 Nov") },
          singleLine = true,
          trailingIcon = {
            IconButton(onClick = { showDobPicker = true }, modifier = Modifier.size(36.dp)) {
              Icon(Icons.Rounded.CalendarMonth, contentDescription = "Pick birthday", modifier = Modifier.size(16.dp))
            }
          },
          modifier = Modifier.weight(1f).testTag("pr_contact_dob_input"),
        )
        OutlinedTextField(
          value = anniversaryDate,
          onValueChange = { anniversaryDate = it },
          label = { Text("Anniversary / Milestone") },
          placeholder = { Text("e.g. 12 Feb") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("pr_contact_anniversary_input"),
        )
      }

      OutlinedTextField(
        value = specialGiftPreference,
        onValueChange = { specialGiftPreference = it },
        label = { Text("Special gift preference / hamper choice") },
        placeholder = { Text("e.g. Monograph, Sugar-free sweets, Brass decor") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("pr_contact_gift_pref_input"),
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        giftPrefPresets.forEach { pref ->
          FilterChip(
            selected = specialGiftPreference.equals(pref, ignoreCase = true),
            onClick = { specialGiftPreference = pref },
            label = { Text(pref) },
          )
        }
      }

      OutlinedTextField(
        value = cityOrAddress,
        onValueChange = { cityOrAddress = it },
        label = { Text("Delivery city / courier address") },
        placeholder = { Text("e.g. C-Scheme, Jaipur") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("pr_contact_city_input"),
      )

      Text("Follow-up Stage", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PRFollowUpStatus.entries.forEach { st ->
          FilterChip(
            selected = followUpStatus == st,
            onClick = { followUpStatus = st },
            label = { Text(st.label) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      OutlinedTextField(
        value = nextFollowUpDate,
        onValueChange = { nextFollowUpDate = it },
        label = { Text("Next follow-up / reminder date") },
        placeholder = { Text("e.g. Tomorrow 4 PM, 30 Nov") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("pr_contact_followup_input"),
      )

      if (!isHostOnly && assignableStaff.isNotEmpty()) {
        OwnerHostDropdown(
          staff = assignableStaff,
          selected = selectedOwner,
          onSelect = { selectedOwner = it },
          testTag = "pr_contact_owner_dropdown",
        )
      } else {
        Text(
          text = "Relationship owner: $currentStaffName (only visible to you & desk)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Relationship / press notes") },
        minLines = 2,
        maxLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("pr_contact_notes_input"),
      )

      SheetSaveCancelRow(
        saveLabel = if (existing == null) "Save contact" else "Save changes",
        canSave = canSave,
        isBusy = isBusy,
        onDismiss = onDismiss,
        onSave = {
          onSave(
            name,
            organization,
            designation,
            tier,
            mobile,
            email,
            followUpStatus,
            nextFollowUpDate,
            photoUrl,
            dob,
            anniversaryDate,
            specialGiftPreference,
            cityOrAddress,
            notes,
            selectedOwner,
          )
          onDismiss()
        },
        saveTag = "pr_contact_save_button",
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PRGiftEditorSheet(
  existing: PRGift?,
  assignableStaff: List<StaffMember>,
  isHostOnly: Boolean,
  currentStaffName: String,
  isBusy: Boolean,
  prefillPerson: String = "",
  prefillCompany: String = "",
  prefillOccasion: String = "",
  prefillNotes: String = "",
  onDismiss: () -> Unit,
  onSave: (
    recipientOrSender: String,
    company: String,
    itemTitle: String,
    direction: PRGiftDirection,
    status: PRGiftStatus,
    occasion: String,
    dateLabel: String,
    notes: String,
    ownerStaff: StaffMember?,
  ) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var recipientOrSender by remember(existing?.id, prefillPerson) {
    mutableStateOf(existing?.recipientOrSender ?: prefillPerson)
  }
  var company by remember(existing?.id, prefillCompany) {
    mutableStateOf(existing?.company ?: prefillCompany)
  }
  var itemTitle by remember(existing?.id) { mutableStateOf(existing?.itemTitle.orEmpty()) }
  var direction by remember(existing?.id) { mutableStateOf(existing?.parsedDirection() ?: PRGiftDirection.SENT) }
  var status by remember(existing?.id) { mutableStateOf(existing?.parsedStatus() ?: PRGiftStatus.PREPARED) }
  var occasion by remember(existing?.id, prefillOccasion) {
    mutableStateOf(existing?.occasion ?: prefillOccasion)
  }
  var dateLabel by remember(existing?.id) { mutableStateOf(existing?.dateLabel.orEmpty()) }
  var notes by remember(existing?.id, prefillNotes) {
    mutableStateOf(existing?.notes ?: prefillNotes)
  }

  val initialOwner =
    remember(existing?.id, assignableStaff) {
      assignableStaff.firstOrNull {
        (existing?.ownerStaffId?.isNotBlank() == true && it.id == existing.ownerStaffId) ||
          (existing?.ownerEmail?.isNotBlank() == true &&
            it.email.equals(existing.ownerEmail, ignoreCase = true))
      } ?: assignableStaff.firstOrNull()
    }
  var selectedOwner by remember(existing?.id, assignableStaff) { mutableStateOf(initialOwner) }
  val canSave = recipientOrSender.trim().isNotEmpty() && itemTitle.trim().isNotEmpty() && !isBusy

  val occasionPresets =
    remember { listOf("Birthday Gift", "Special Gift", "Diwali gift", "Press Kit", "Monograph", "VIP Welcome") }

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
      Text(
        text = if (existing == null) "Log Gift / Press Kit" else "Edit Gift / Hospitality",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      Text("Direction", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PRGiftDirection.entries.forEach { dir ->
          FilterChip(
            selected = direction == dir,
            onClick = { direction = dir },
            label = { Text(if (dir == PRGiftDirection.SENT) "Outgoing (Sent)" else "Incoming (Received)") },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = recipientOrSender,
          onValueChange = { recipientOrSender = it },
          label = { Text(if (direction == PRGiftDirection.SENT) "Recipient name *" else "Sender name *") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.weight(1f).testTag("pr_gift_person_input"),
        )
        OutlinedTextField(
          value = company,
          onValueChange = { company = it },
          label = { Text("Company / Outlet") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.weight(1f).testTag("pr_gift_company_input"),
        )
      }

      OutlinedTextField(
        value = itemTitle,
        onValueChange = { itemTitle = it },
        label = { Text("Gift hamper / press kit *") },
        placeholder = { Text("e.g. ArchPoint Studio Monograph + Brass Diya Box") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("pr_gift_item_input"),
      )

      Text("Occasion", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        occasionPresets.forEach { preset ->
          FilterChip(
            selected = occasion.equals(preset, ignoreCase = true),
            onClick = { occasion = preset },
            label = { Text(preset) },
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = occasion,
          onValueChange = { occasion = it },
          label = { Text("Custom occasion") },
          singleLine = true,
          modifier = Modifier.weight(0.55f),
        )
        OutlinedTextField(
          value = dateLabel,
          onValueChange = { dateLabel = it },
          label = { Text("Date / Day") },
          placeholder = { Text("e.g. Today") },
          singleLine = true,
          modifier = Modifier.weight(0.45f),
        )
      }

      Text("Delivery Stage", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PRGiftStatus.entries.forEach { st ->
          FilterChip(
            selected = status == st,
            onClick = { status = st },
            label = { Text(st.label) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      if (!isHostOnly && assignableStaff.isNotEmpty()) {
        OwnerHostDropdown(
          staff = assignableStaff,
          selected = selectedOwner,
          onSelect = { selectedOwner = it },
          testTag = "pr_gift_owner_dropdown",
        )
      } else {
        Text(
          text = "Coordinator: $currentStaffName",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Dispatch / thank-you note") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().testTag("pr_gift_notes_input"),
      )

      SheetSaveCancelRow(
        saveLabel = if (existing == null) "Log gift" else "Save changes",
        canSave = canSave,
        isBusy = isBusy,
        onDismiss = onDismiss,
        onSave = {
          onSave(
            recipientOrSender,
            company,
            itemTitle,
            direction,
            status,
            occasion,
            dateLabel,
            notes,
            selectedOwner,
          )
          onDismiss()
        },
        saveTag = "pr_gift_save_button",
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportVisitorToPrSheet(
  profiles: List<VisitorProfile>,
  onDismiss: () -> Unit,
  onSelectVisitor: (VisitorProfile, PRContactTier) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var query by remember { mutableStateOf("") }
  var chosenTier by remember { mutableStateOf(PRContactTier.VIP) }

  val filtered =
    remember(profiles, query) {
      val clean = query.trim().lowercase()
      val base =
        if (clean.isEmpty()) profiles
        else {
          profiles.filter { p ->
            p.name.lowercase().contains(clean) ||
              p.company.lowercase().contains(clean) ||
              p.mobile.contains(clean) ||
              p.email.lowercase().contains(clean)
          }
        }
      base.take(30)
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
          .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Promote Visitor to PR Contact",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Choose a tier, then tap any past visitor to add them to your PR directory.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PRContactTier.entries.forEach { tier ->
          FilterChip(
            selected = chosenTier == tier,
            onClick = { chosenTier = tier },
            label = { Text("Add as ${tier.label}") },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search visitor name, org, or phone") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { query = "" }) {
              Icon(Icons.Rounded.Clear, contentDescription = "Clear visitor search")
            }
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("pr_import_visitor_search"),
      )

      if (filtered.isEmpty()) {
        Text(
          text = "No matching visitor profiles found.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 18.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(bottom = 18.dp),
        ) {
          items(filtered, key = { "${it.id}_${it.name}_${it.mobile}" }) { profile ->
            Surface(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    onSelectVisitor(profile, chosenTier)
                    onDismiss()
                  },
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = profile.name.ifBlank { "Visitor" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  val sub =
                    listOfNotNull(
                      profile.company.takeIf { it.isNotBlank() },
                      profile.mobile.takeIf { it.isNotBlank() && it != "919999999999" },
                    ).joinToString(" · ")
                  if (sub.isNotBlank()) {
                    Text(
                      text = sub,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                  imageVector = Icons.Rounded.PersonAdd,
                  contentDescription = "Promote to PR",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportHostClientToPrSheet(
  clients: List<HostClientSummary>,
  onDismiss: () -> Unit,
  onSelectClient: (HostClientSummary, PRContactTier) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var query by remember { mutableStateOf("") }
  var chosenTier by remember { mutableStateOf(PRContactTier.VIP) }

  val filtered =
    remember(clients, query) {
      val clean = query.trim().lowercase()
      val base =
        if (clean.isEmpty()) clients
        else {
          clients.filter { c ->
            c.name.lowercase().contains(clean) ||
              c.company.lowercase().contains(clean) ||
              c.mobile.contains(clean) ||
              c.email.lowercase().contains(clean)
          }
        }
      base.take(30)
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
          .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Add Your Client to PR Contacts",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Pick a tier, then tap any client from your meetings to track wishes, birthdays & gifts.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PRContactTier.entries.forEach { tier ->
          FilterChip(
            selected = chosenTier == tier,
            onClick = { chosenTier = tier },
            label = { Text("Add as ${tier.label}") },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search your client name, org, or phone") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { query = "" }) {
              Icon(Icons.Rounded.Clear, contentDescription = "Clear client search")
            }
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("pr_import_client_search"),
      )

      if (filtered.isEmpty()) {
        Text(
          text = "No matching met clients found yet.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 18.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(bottom = 18.dp),
        ) {
          items(filtered, key = { it.key }) { client ->
            Surface(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    onSelectClient(client, chosenTier)
                    onDismiss()
                  },
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = client.name.ifBlank { "Client" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  val sub =
                    listOfNotNull(
                      client.company.takeIf { it.isNotBlank() },
                      client.mobile.takeIf { it.isNotBlank() && it != "919999999999" },
                    ).joinToString(" · ")
                  if (sub.isNotBlank()) {
                    Text(
                      text = sub,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                  imageVector = Icons.Rounded.PersonAdd,
                  contentDescription = "Add to PR",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendBirthdayWishSheet(
  contact: PRContact,
  senderName: String,
  onDismiss: () -> Unit,
  onSendWish: (message: String, channel: String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var message by remember(contact.id) {
    mutableStateOf(MainViewModel.buildBirthdayWishMessage(contact, senderName))
  }
  val hasPhone = contact.mobile.filter { it.isDigit() }.length >= 7
  val hasEmail = contact.email.contains("@")

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
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StaffAvatar(
          name = contact.name,
          photoUrl = contact.photoUrl,
          size = 48.dp,
          textStyle = MaterialTheme.typography.titleMedium,
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Wish ${contact.name}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          val sub =
            listOfNotNull(
              contact.dob.takeIf { it.isNotBlank() }?.let { "Birthday: $it" },
              contact.specialGiftPreference.takeIf { it.isNotBlank() }?.let { "Pref: $it" },
            ).joinToString(" · ")
          if (sub.isNotBlank()) {
            Text(
              text = sub,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Icon(
          imageVector = Icons.Rounded.Cake,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }

      OutlinedTextField(
        value = message,
        onValueChange = { message = it },
        label = { Text("Personal wish message") },
        minLines = 3,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().testTag("pr_wish_message_input"),
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (hasPhone) {
          Button(
            onClick = {
              onSendWish(message, "whatsapp")
              onDismiss()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("pr_wish_send_whatsapp_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("WhatsApp / SMS")
          }
        }
        if (hasEmail) {
          OutlinedButton(
            onClick = {
              onSendWish(message, "email")
              onDismiss()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("pr_wish_send_email_button"),
          ) {
            Text("Email Wish")
          }
        }
        Button(
          onClick = {
            onSendWish(message, if (!hasPhone && !hasEmail) "mark" else "share")
            onDismiss()
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(if (!hasPhone && !hasEmail) 1f else 0.9f).height(48.dp).testTag("pr_wish_share_button"),
        ) {
          Text(if (!hasPhone && !hasEmail) "Mark Wished" else "Share")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerHostDropdown(
  staff: List<StaffMember>,
  selected: StaffMember?,
  onSelect: (StaffMember) -> Unit,
  testTag: String,
) {
  var expanded by remember { mutableStateOf(false) }
  val displayText =
    selected?.let { member ->
      val label = member.shownAs.ifBlank { member.fullName }
      val roleText = member.designation.ifBlank { member.parsedRole().label }
      "$label · $roleText"
    } ?: "Select owner"

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = Modifier.fillMaxWidth().testTag(testTag),
  ) {
    OutlinedTextField(
      value = displayText,
      onValueChange = {},
      readOnly = true,
      label = { Text("Lead host / owner") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      staff.forEach { member ->
        DropdownMenuItem(
          text = {
            Column {
              Text(
                text = member.shownAs.ifBlank { member.fullName },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              val sub =
                listOfNotNull(
                  member.designation.takeIf { it.isNotBlank() },
                  member.email.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
              if (sub.isNotBlank()) {
                Text(
                  text = sub,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          },
          onClick = {
            onSelect(member)
            expanded = false
          },
        )
      }
    }
  }
}

@Composable
private fun SheetSaveCancelRow(
  saveLabel: String,
  canSave: Boolean,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: () -> Unit,
  saveTag: String,
) {
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
  Row(
    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OutlinedButton(
      onClick = onDismiss,
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.weight(0.8f).height(50.dp),
    ) {
      Text("Cancel")
    }
    Button(
      onClick = onSave,
      enabled = canSave,
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
      ),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.weight(1.2f).height(50.dp).testTag(saveTag),
    ) {
      if (isBusy) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.onPrimary,
        )
      } else {
        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(saveLabel)
      }
    }
  }
}
