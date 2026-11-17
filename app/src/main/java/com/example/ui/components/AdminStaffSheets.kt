package com.example.ui.components

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.ui.theme.archPointStatusColors
import com.example.util.StaffPhotoCodec
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StaffEditorBottomSheet(
  existingMember: StaffMember?,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onCreateUser: (
    fullName: String,
    shownAs: String,
    email: String,
    designation: String,
    department: String,
    floor: String,
    cabin: String,
    otherLocation: String,
    requiredThings: String,
    notes: String,
    role: StaffRole,
    hostListed: Boolean,
    photoUrl: String,
  ) -> Unit,
  onSaveUser: (StaffMember) -> Unit,
) {
  val isEditing = existingMember != null
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val status = archPointStatusColors()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var fullName by remember(existingMember?.id) { mutableStateOf(existingMember?.fullName.orEmpty()) }
  var shownAs by remember(existingMember?.id) { mutableStateOf(existingMember?.shownAs.orEmpty()) }
  var email by remember(existingMember?.id) { mutableStateOf(existingMember?.email.orEmpty()) }
  var designation by remember(existingMember?.id) { mutableStateOf(existingMember?.designation.orEmpty()) }
  var department by remember(existingMember?.id) { mutableStateOf(existingMember?.department.orEmpty()) }
  var floor by remember(existingMember?.id) { mutableStateOf(existingMember?.floor.orEmpty()) }
  var cabin by remember(existingMember?.id) { mutableStateOf(existingMember?.cabin.orEmpty()) }
  var otherLocation by remember(existingMember?.id) { mutableStateOf(existingMember?.otherLocation.orEmpty()) }
  var requiredThings by remember(existingMember?.id) { mutableStateOf(existingMember?.requiredThings.orEmpty()) }
  var notes by remember(existingMember?.id) { mutableStateOf(existingMember?.notes.orEmpty()) }
  var listOrderStr by remember(existingMember?.id) {
    mutableStateOf(existingMember?.listOrder?.toString() ?: "")
  }
  var role by remember(existingMember?.id) {
    mutableStateOf(existingMember?.parsedRole() ?: StaffRole.HOST)
  }
  var hostListed by remember(existingMember?.id) {
    mutableStateOf(existingMember?.hostListed ?: true)
  }
  var activeAccount by remember(existingMember?.id) {
    mutableStateOf(existingMember?.activeAccount ?: true)
  }
  var photoUrl by remember(existingMember?.id) {
    mutableStateOf(existingMember?.photoUrl.orEmpty())
  }
  var isEncodingPhoto by remember { mutableStateOf(false) }
  var photoError by remember { mutableStateOf<String?>(null) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
      if (uri != null) {
        isEncodingPhoto = true
        photoError = null
        scope.launch {
          val encoded = StaffPhotoCodec.encodePickedPhotoToDataUri(context, uri)
          isEncodingPhoto = false
          encoded
            .onSuccess { dataUri -> photoUrl = dataUri }
            .onFailure { err -> photoError = err.localizedMessage ?: "Failed to load image" }
        }
      }
    }

  val canSubmit =
    !isBusy &&
      !isEncodingPhoto &&
      fullName.isNotBlank() &&
      (!isEditing || shownAs.isNotBlank()) &&
      (email.isBlank() || (email.trim().contains("@") && email.trim().length >= 5))

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (isEditing) "Edit staff member" else "Add new user",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              if (isEditing) {
                "Update profile photo, designation, seating, email, and portal access."
              } else {
                "Add a user with an assigned email and portal access role."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (existingMember != null) {
          StatusPill(
            text = if (activeAccount) "Active" else "Off",
            containerColor = if (activeAccount) status.freeContainer else status.deniedContainer,
            contentColor = if (activeAccount) status.freeOnContainer else status.deniedOnContainer,
          )
        }
      }

      // Photo upload section
      GlassInnerSurface(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          StaffAvatar(
            name = shownAs.ifBlank { fullName.ifBlank { "New" } },
            photoUrl = photoUrl,
            size = 64.dp,
            textStyle = MaterialTheme.typography.titleMedium,
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "Profile photo",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedButton(
                onClick = {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                enabled = !isEncodingPhoto && !isBusy,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("staff_editor_upload_photo_button"),
              ) {
                if (isEncodingPhoto) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                  Spacer(Modifier.width(6.dp))
                  Text("Processing...", style = MaterialTheme.typography.labelMedium)
                } else {
                  Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = if (photoUrl.isBlank()) "Upload image" else "Change image",
                    style = MaterialTheme.typography.labelMedium,
                  )
                }
              }
              if (photoUrl.isNotBlank() && !isEncodingPhoto) {
                TextButton(
                  onClick = { photoUrl = "" },
                  modifier = Modifier.heightIn(min = 48.dp).testTag("staff_editor_remove_photo_button"),
                ) {
                  Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove photo",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
            if (photoError != null) {
              Text(
                text = photoError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }

      // Identity & Designation
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it },
          label = { Text("Full name") },
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("staff_editor_name_input"),
        )
        OutlinedTextField(
          value = shownAs,
          onValueChange = { shownAs = it },
          label = { Text("Shown as") },
          placeholder = { Text("Display name") },
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("staff_editor_shown_input"),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = designation,
          onValueChange = { designation = it },
          label = { Text("Designation") },
          placeholder = { Text("e.g. Principal Architect") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("staff_editor_designation_input"),
        )
        OutlinedTextField(
          value = department,
          onValueChange = { department = it },
          label = { Text("Department") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("staff_editor_department_input"),
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

      // Portal Email & Access Control
      Text(
        text = "PORTAL EMAIL & ACCESS",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Google Email (portal login)") },
        placeholder = { Text("colleague@gmail.com") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("staff_editor_email_input"),
      )

      if (existingMember != null) {
        val loginStatusText =
          when {
            !activeAccount -> "Portal login disabled"
            email.isBlank() -> "No login email set yet"
            existingMember.uid.isNotBlank() -> "Google Sign-In verified (${existingMember.email})"
            else -> "Access ready · waiting for Google Sign-In"
          }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              imageVector = Icons.Rounded.VerifiedUser,
              contentDescription = null,
              tint =
                if (existingMember.uid.isNotBlank() && activeAccount) status.freeText
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = loginStatusText,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (email.trim().contains("@") && activeAccount) {
            OutlinedButton(
              onClick = {
                val cleanMail = email.trim().lowercase()
                val msg =
                  "Hi ${shownAs.ifBlank { fullName }}, your ArchPoint Visitors (${role.label}) portal access is ready.\n" +
                    "Open ArchPoint Visitors and sign in with Google using: $cleanMail"
                val shareIntent =
                  Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, msg)
                  }
                context.startActivity(Intent.createChooser(shareIntent, "Share Login Invite"))
              },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier.height(36.dp).testTag("staff_editor_invite_button"),
            ) {
              Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
              Text("Invite", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Portal Role", style = MaterialTheme.typography.labelMedium)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          StaffRole.entries.forEach { option ->
            val selected = role == option
            FilterChip(
              selected = selected,
              onClick = { role = option },
              label = { Text(option.label) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
              modifier = Modifier.testTag("staff_editor_role_${option.wireValue}"),
            )
          }
        }
        Text(
          text =
            when (role) {
              StaffRole.ADMIN -> "Admin: Sees all company logs & analytics and manages all users and portal access."
              StaffRole.RECEPTION -> "Reception: Checks in visitors & manages today's desk queue only."
              StaffRole.HOST -> "Host: Sees ONLY their own visits, clients, and meeting notifications."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      GlassInnerSurface(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
          if (isEditing) {
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clickable { activeAccount = !activeAccount },
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Portal login enabled", style = MaterialTheme.typography.bodyMedium)
                Text(
                  "Allow this person to sign in with their Google email",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = activeAccount,
                onCheckedChange = { activeAccount = it },
                modifier = Modifier.testTag("staff_editor_active_switch"),
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
          }

          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { hostListed = !hostListed },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Visitors can ask for them", style = MaterialTheme.typography.bodyMedium)
              Text(
                "Show in Reception host list for visitor check-ins",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = hostListed,
              onCheckedChange = { hostListed = it },
              modifier = Modifier.testTag("staff_editor_host_listed_switch"),
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

      // Seating & Requirements (Horizontal Scroll Bar Pickers)
      var showCustomSeatingFields by remember(existingMember?.id) {
        val hasCustomPrimary =
          (existingMember?.cabin.orEmpty().isNotBlank() || existingMember?.floor.orEmpty().isNotBlank()) &&
            com.example.data.model.ArchPointSeedData.locationPresets.none {
              it.matchesSeating(existingMember?.floor.orEmpty(), existingMember?.cabin.orEmpty())
            }
        val hasCustomAlt =
          existingMember?.otherLocation.orEmpty().isNotBlank() &&
            com.example.data.model.ArchPointSeedData.locationPresets.none {
              it.matchesLocationText(existingMember?.otherLocation.orEmpty())
            }
        mutableStateOf(hasCustomPrimary || hasCustomAlt)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "SEATING & LOCATIONS",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
          onClick = { showCustomSeatingFields = !showCustomSeatingFields },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          modifier = Modifier.heightIn(min = 36.dp).testTag("staff_editor_custom_location_toggle"),
        ) {
          Text(
            text = if (showCustomSeatingFields) "Hide custom text" else "Custom text",
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }

      val selectedPrimaryPresetId =
        remember(floor, cabin) {
          com.example.data.model.ArchPointSeedData.locationPresets
            .firstOrNull { it.matchesSeating(floor, cabin) }
            ?.id
        }
      LocationPresetScrollBar(
        title = "Primary seating (scroll & tap)",
        selectedPresetId = selectedPrimaryPresetId,
        onSelectPreset = { preset ->
          floor = preset.floor
          cabin = preset.cabin
        },
        onClearSelection = {
          floor = ""
          cabin = ""
        },
        tagPrefix = "staff_editor_primary_loc",
      )

      val selectedAltPresetId =
        remember(otherLocation) {
          com.example.data.model.ArchPointSeedData.locationPresets
            .firstOrNull { it.matchesLocationText(otherLocation) }
            ?.id
        }
      LocationPresetScrollBar(
        title = "Alternate / meeting location (optional)",
        selectedPresetId = selectedAltPresetId,
        onSelectPreset = { preset ->
          otherLocation = preset.chipText
        },
        onClearSelection = {
          otherLocation = ""
        },
        tagPrefix = "staff_editor_alt_loc",
      )

      if (showCustomSeatingFields) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = floor,
            onValueChange = { floor = it },
            label = { Text("Floor") },
            placeholder = { Text("Ground Floor, 2nd Floor") },
            singleLine = true,
            modifier = Modifier.weight(0.42f).testTag("staff_editor_floor_input"),
          )
          OutlinedTextField(
            value = cabin,
            onValueChange = { cabin = it },
            label = { Text("Cabin / room") },
            placeholder = { Text("e.g. Amit Cabin") },
            singleLine = true,
            modifier = Modifier.weight(0.58f).testTag("staff_editor_cabin_input"),
          )
        }

        OutlinedTextField(
          value = otherLocation,
          onValueChange = { otherLocation = it },
          label = { Text("Other location (optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("staff_editor_other_location_input"),
        )
      }

      OutlinedTextField(
        value = requiredThings,
        onValueChange = { requiredThings = it },
        label = { Text("Required things (optional)") },
        placeholder = { Text("e.g. NDA pad, blueprints, badge") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("staff_editor_required_input"),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (optional)") },
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("staff_editor_notes_input"),
        )
        if (isEditing) {
          OutlinedTextField(
            value = listOrderStr,
            onValueChange = { listOrderStr = it.filter { ch -> ch.isDigit() }.take(4) },
            label = { Text("Order") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(84.dp),
          )
        }
      }

      Spacer(Modifier.height(4.dp))

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
          onClick = {
            if (existingMember != null) {
              onSaveUser(
                existingMember.copy(
                  fullName = fullName.trim(),
                  shownAs = shownAs.trim(),
                  email = email.trim().lowercase(),
                  designation = designation.trim(),
                  department = department.trim(),
                  floor = floor.trim(),
                  cabin = cabin.trim(),
                  otherLocation = otherLocation.trim(),
                  requiredThings = requiredThings.trim(),
                  notes = notes.trim(),
                  listOrder = listOrderStr.toLongOrNull() ?: existingMember.listOrder,
                  role = role.wireValue,
                  hostListed = hostListed,
                  activeAccount = activeAccount,
                  photoUrl = photoUrl.trim(),
                )
              )
            } else {
              onCreateUser(
                fullName.trim(),
                shownAs.trim(),
                email.trim().lowercase(),
                designation.trim(),
                department.trim(),
                floor.trim(),
                cabin.trim(),
                otherLocation.trim(),
                requiredThings.trim(),
                notes.trim(),
                role,
                hostListed,
                photoUrl.trim(),
              )
            }
            onDismiss()
          },
          enabled = canSubmit,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1.2f).height(50.dp).testTag("staff_editor_save_button"),
        ) {
          Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(if (isEditing) "Save changes" else "Create user")
        }
      }
    }
  }
}
