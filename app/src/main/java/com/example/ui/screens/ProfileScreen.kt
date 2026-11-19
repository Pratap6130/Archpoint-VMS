package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemeMode
import com.example.data.model.ArchPointSeedData
import com.example.data.model.AvailabilityTone
import com.example.data.model.HostAvailability
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.VisitStatus
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassInnerSurface
import com.example.ui.components.GlassSegmentedControl
import com.example.ui.components.LocationPresetScrollBar
import com.example.ui.components.StaffAvatar
import com.example.ui.components.StatusDotLabel
import com.example.ui.components.StatusPill
import com.example.ui.components.matchesLocationText
import com.example.ui.components.matchesSeating
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.DeskSnapshot
import com.example.util.StaffPhotoCodec
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
  snapshot: DeskSnapshot,
  userEmail: String,
  themeMode: AppThemeMode,
  notificationsEnabled: Boolean,
  backgroundServiceRunning: Boolean,
  isBusy: Boolean,
  onSelectTheme: (AppThemeMode) -> Unit,
  onSaveOwnProfile: (
    fullName: String,
    shownAs: String,
    designation: String,
    department: String,
    photoUrl: String,
    floor: String,
    cabin: String,
    otherLocation: String,
    requiredThings: String,
    notes: String,
  ) -> Unit,
  onOpenSettings: () -> Unit,
  onTurnOnAlerts: () -> Unit,
  onToggleBackgroundAlerts: (Boolean) -> Unit,
  onOpenBatterySettings: () -> Unit,
  onSendTestAlert: () -> Unit,
  onSignOut: () -> Unit,
) {
  val currentMember =
    snapshot.currentStaff
      ?: snapshot.staff.firstOrNull { it.email.equals(userEmail, ignoreCase = true) }

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
        Column(
          modifier = Modifier.weight(0.95f),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          MyProfileSeatingCard(
            member = currentMember,
            officeName = snapshot.office?.name.orEmpty(),
            userEmail = userEmail,
            isBusy = isBusy,
            onOpenSettings = onOpenSettings,
            onSaveOwnProfile = onSaveOwnProfile,
          )
          AppearanceAndAlertsCard(
            themeMode = themeMode,
            notificationsEnabled = notificationsEnabled,
            backgroundServiceRunning = backgroundServiceRunning,
            onSelectTheme = onSelectTheme,
            onTurnOnAlerts = onTurnOnAlerts,
            onToggleBackgroundAlerts = onToggleBackgroundAlerts,
            onOpenBatterySettings = onOpenBatterySettings,
            onSendTestAlert = onSendTestAlert,
            onSignOut = onSignOut,
          )
        }
        Box(modifier = Modifier.weight(1.05f)) {
          if (snapshot.activeRole == StaffRole.HOST) {
            HostAccessBoundaryCard(member = currentMember)
          } else {
            MembersDirectoryCard(snapshot = snapshot)
          }
        }
      }
    } else {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        MyProfileSeatingCard(
          member = currentMember,
          officeName = snapshot.office?.name.orEmpty(),
          userEmail = userEmail,
          isBusy = isBusy,
          onOpenSettings = onOpenSettings,
          onSaveOwnProfile = onSaveOwnProfile,
        )
        AppearanceAndAlertsCard(
          themeMode = themeMode,
          notificationsEnabled = notificationsEnabled,
          backgroundServiceRunning = backgroundServiceRunning,
          onSelectTheme = onSelectTheme,
          onTurnOnAlerts = onTurnOnAlerts,
          onToggleBackgroundAlerts = onToggleBackgroundAlerts,
          onOpenBatterySettings = onOpenBatterySettings,
          onSendTestAlert = onSendTestAlert,
          onSignOut = onSignOut,
        )
        if (snapshot.activeRole == StaffRole.HOST) {
          HostAccessBoundaryCard(member = currentMember)
        } else {
          MembersDirectoryCard(snapshot = snapshot)
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun MyProfileSeatingCard(
  member: StaffMember?,
  officeName: String,
  userEmail: String,
  isBusy: Boolean,
  onOpenSettings: () -> Unit,
  onSaveOwnProfile: (
    fullName: String,
    shownAs: String,
    designation: String,
    department: String,
    photoUrl: String,
    floor: String,
    cabin: String,
    otherLocation: String,
    requiredThings: String,
    notes: String,
  ) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val status = archPointStatusColors()

  var fullName by remember(member?.id, member?.fullName) {
    mutableStateOf(member?.fullName.orEmpty())
  }
  var shownAs by remember(member?.id, member?.shownAs) {
    mutableStateOf(member?.shownAs.orEmpty())
  }
  var designation by remember(member?.id, member?.designation) {
    mutableStateOf(member?.designation.orEmpty())
  }
  var department by remember(member?.id, member?.department) {
    mutableStateOf(member?.department.orEmpty())
  }
  var photoUrl by remember(member?.id, member?.photoUrl) {
    mutableStateOf(member?.photoUrl.orEmpty())
  }
  var floor by remember(member?.id, member?.floor) { mutableStateOf(member?.floor.orEmpty()) }
  var cabin by remember(member?.id, member?.cabin) { mutableStateOf(member?.cabin.orEmpty()) }
  var otherLocation by remember(member?.id, member?.otherLocation) {
    mutableStateOf(member?.otherLocation.orEmpty())
  }
  var requiredThings by remember(member?.id, member?.requiredThings) {
    mutableStateOf(member?.requiredThings.orEmpty())
  }
  var notes by remember(member?.id, member?.notes) { mutableStateOf(member?.notes.orEmpty()) }

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

  val displayName = fullName.trim().ifBlank { member?.shownAs ?: userEmail.substringBefore("@") }
  val hasChanges =
    member != null &&
      fullName.trim().isNotEmpty() &&
      (fullName.trim() != member.fullName ||
        shownAs.trim() != member.shownAs ||
        designation.trim() != member.designation ||
        department.trim() != member.department ||
        photoUrl.trim() != member.photoUrl ||
        floor.trim() != member.floor ||
        cabin.trim() != member.cabin ||
        otherLocation.trim() != member.otherLocation ||
        requiredThings.trim() != member.requiredThings ||
        notes.trim() != member.notes)

  GlassCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        StaffAvatar(
          name = shownAs.ifBlank { displayName },
          photoUrl = photoUrl,
          size = 56.dp,
          textStyle = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(12.dp))
        Column {
          Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          val roleDept =
            listOfNotNull(
              designation.trim().takeIf { it.isNotBlank() },
              department.trim().takeIf { it.isNotBlank() },
            ).joinToString(" · ")
          if (roleDept.isNotBlank()) {
            Text(
              text = roleDept,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text = member?.email?.ifBlank { userEmail } ?: userEmail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (officeName.isNotBlank()) {
            Text(
              text = officeName,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (member != null) {
          StatusPill(
            text = member.parsedRole().label,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        OutlinedButton(
          onClick = onOpenSettings,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.heightIn(min = 40.dp).testTag("profile_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = "Open Settings",
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text("Settings", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    // Photo controls
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        enabled = !isBusy && !isEncodingPhoto && member != null,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.heightIn(min = 42.dp).testTag("profile_upload_photo_button"),
      ) {
        if (isEncodingPhoto) {
          CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(6.dp))
          Text("Processing...", style = MaterialTheme.typography.labelMedium)
        } else {
          Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (photoUrl.isBlank()) "Upload profile photo" else "Change photo",
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
      if (photoUrl.isNotBlank() && !isEncodingPhoto && member != null) {
        IconButton(
          onClick = { photoUrl = "" },
          modifier = Modifier.size(40.dp).testTag("profile_remove_photo_button"),
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
    if (photoError != null) {
      Text(
        text = photoError.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    Text("My profile & designation", style = MaterialTheme.typography.titleSmall)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = { Text("Full name") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("my_full_name_input"),
      )
      OutlinedTextField(
        value = shownAs,
        onValueChange = { shownAs = it },
        label = { Text("Shown as") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("my_shown_as_input"),
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = designation,
        onValueChange = { designation = it },
        label = { Text("Designation") },
        placeholder = { Text("e.g. Architect") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("my_designation_input"),
      )
      OutlinedTextField(
        value = department,
        onValueChange = { department = it },
        label = { Text("Department") },
        placeholder = { Text("e.g. Design, Front Desk") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("my_department_input"),
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    var showCustomSeating by remember(member?.id) {
      val hasCustomPrimary =
        (member?.cabin.orEmpty().isNotBlank() || member?.floor.orEmpty().isNotBlank()) &&
          ArchPointSeedData.locationPresets.none {
            it.matchesSeating(member?.floor.orEmpty(), member?.cabin.orEmpty())
          }
      val hasCustomAlt =
        member?.otherLocation.orEmpty().isNotBlank() &&
          ArchPointSeedData.locationPresets.none {
            it.matchesLocationText(member?.otherLocation.orEmpty())
          }
      mutableStateOf(hasCustomPrimary || hasCustomAlt)
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        "My seating & required items",
        style = MaterialTheme.typography.titleSmall,
      )
      TextButton(
        onClick = { showCustomSeating = !showCustomSeating },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.heightIn(min = 36.dp).testTag("my_custom_seating_toggle"),
      ) {
        Text(
          text = if (showCustomSeating) "Hide custom text" else "Custom text",
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }

    val selectedPrimaryPresetId =
      remember(floor, cabin) {
        ArchPointSeedData.locationPresets.firstOrNull { it.matchesSeating(floor, cabin) }?.id
      }
    LocationPresetScrollBar(
      title = "My primary seating (scroll & tap)",
      selectedPresetId = selectedPrimaryPresetId,
      onSelectPreset = { preset ->
        floor = preset.floor
        cabin = preset.cabin
      },
      onClearSelection = {
        floor = ""
        cabin = ""
      },
      tagPrefix = "my_primary_loc",
    )

    val selectedAltPresetId =
      remember(otherLocation) {
        ArchPointSeedData.locationPresets.firstOrNull { it.matchesLocationText(otherLocation) }?.id
      }
    LocationPresetScrollBar(
      title = "Alternate / meeting room (optional)",
      selectedPresetId = selectedAltPresetId,
      onSelectPreset = { preset ->
        otherLocation = preset.chipText
      },
      onClearSelection = {
        otherLocation = ""
      },
      tagPrefix = "my_alt_loc",
    )

    if (showCustomSeating) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = floor,
          onValueChange = { floor = it },
          label = { Text("Floor") },
          placeholder = { Text("Ground Floor, 2nd Floor") },
          singleLine = true,
          modifier = Modifier.weight(0.42f).testTag("my_floor_input"),
        )
        OutlinedTextField(
          value = cabin,
          onValueChange = { cabin = it },
          label = { Text("Cabin / room") },
          placeholder = { Text("e.g. Amit Cabin") },
          leadingIcon = {
            Icon(Icons.Rounded.Chair, contentDescription = null, modifier = Modifier.size(18.dp))
          },
          singleLine = true,
          modifier = Modifier.weight(0.58f).testTag("my_seating_input"),
        )
      }

      OutlinedTextField(
        value = otherLocation,
        onValueChange = { otherLocation = it },
        label = { Text("Other / studio location (optional)") },
        placeholder = { Text("e.g. Studio (3rd Floor)") },
        leadingIcon = {
          Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("my_other_location_input"),
      )
    }

    OutlinedTextField(
      value = requiredThings,
      onValueChange = { requiredThings = it },
      label = { Text("Required things") },
      placeholder = { Text("e.g. NDA pad, site blueprints, visitor badge") },
      leadingIcon = {
        Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
      },
      minLines = 2,
      maxLines = 3,
      modifier = Modifier.fillMaxWidth().testTag("my_required_things_input"),
    )

    OutlinedTextField(
      value = notes,
      onValueChange = { notes = it },
      label = { Text("Notes (optional)") },
      placeholder = { Text("Client/vendor days, preferences...") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("my_notes_input"),
    )

    if (member != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        StatusPill(
          text = if (member.hostListed) "Listed for visitors" else "Internal staff",
          containerColor = if (member.hostListed) status.freeContainer else MaterialTheme.colorScheme.surfaceVariant,
          contentColor = if (member.hostListed) status.freeOnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
          onClick = {
            onSaveOwnProfile(
              fullName,
              shownAs,
              designation,
              department,
              photoUrl,
              floor,
              cabin,
              otherLocation,
              requiredThings,
              notes,
            )
          },
          enabled = !isBusy && !isEncodingPhoto && hasChanges,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(46.dp).testTag("save_my_seating_button"),
        ) {
          Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Save profile")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsBottomSheet(
  themeMode: AppThemeMode,
  notificationsEnabled: Boolean,
  backgroundServiceRunning: Boolean,
  userEmail: String,
  roleLabel: String,
  onDismiss: () -> Unit,
  onSelectTheme: (AppThemeMode) -> Unit,
  onTurnOnAlerts: () -> Unit,
  onToggleBackgroundAlerts: (Boolean) -> Unit,
  onOpenBatterySettings: () -> Unit,
  onSendTestAlert: () -> Unit,
  onSignOut: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
          .padding(bottom = 24.dp)
          .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = "App Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "$userEmail · $roleLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(
          text = roleLabel,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      AppearanceAndAlertsContent(
        themeMode = themeMode,
        notificationsEnabled = notificationsEnabled,
        backgroundServiceRunning = backgroundServiceRunning,
        onSelectTheme = onSelectTheme,
        onTurnOnAlerts = onTurnOnAlerts,
        onToggleBackgroundAlerts = onToggleBackgroundAlerts,
        onOpenBatterySettings = onOpenBatterySettings,
        onSendTestAlert = onSendTestAlert,
        onSignOut = {
          onDismiss()
          onSignOut()
        },
      )
    }
  }
}

@Composable
private fun AppearanceAndAlertsCard(
  themeMode: AppThemeMode,
  notificationsEnabled: Boolean,
  backgroundServiceRunning: Boolean,
  onSelectTheme: (AppThemeMode) -> Unit,
  onTurnOnAlerts: () -> Unit,
  onToggleBackgroundAlerts: (Boolean) -> Unit,
  onOpenBatterySettings: () -> Unit,
  onSendTestAlert: () -> Unit,
  onSignOut: () -> Unit,
) {
  GlassCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Rounded.DarkMode,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(8.dp))
      Text("Theme, Alerts & Account", style = MaterialTheme.typography.titleMedium)
    }

    AppearanceAndAlertsContent(
      themeMode = themeMode,
      notificationsEnabled = notificationsEnabled,
      backgroundServiceRunning = backgroundServiceRunning,
      onSelectTheme = onSelectTheme,
      onTurnOnAlerts = onTurnOnAlerts,
      onToggleBackgroundAlerts = onToggleBackgroundAlerts,
      onOpenBatterySettings = onOpenBatterySettings,
      onSendTestAlert = onSendTestAlert,
      onSignOut = onSignOut,
    )
  }
}

@Composable
private fun AppearanceAndAlertsContent(
  themeMode: AppThemeMode,
  notificationsEnabled: Boolean,
  backgroundServiceRunning: Boolean,
  onSelectTheme: (AppThemeMode) -> Unit,
  onTurnOnAlerts: () -> Unit,
  onToggleBackgroundAlerts: (Boolean) -> Unit,
  onOpenBatterySettings: () -> Unit,
  onSendTestAlert: () -> Unit,
  onSignOut: () -> Unit,
) {
  val status = archPointStatusColors()
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      "Choose Light, Dark, or System glass theme",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    GlassSegmentedControl(
      options =
        listOf(
          AppThemeMode.LIGHT to AppThemeMode.LIGHT.label,
          AppThemeMode.DARK to AppThemeMode.DARK.label,
          AppThemeMode.SYSTEM to AppThemeMode.SYSTEM.label,
        ),
      selected = themeMode,
      onSelect = onSelectTheme,
      tagPrefix = "theme_option",
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    if (!notificationsEnabled) {
      GlassInnerSurface(
        containerColor = status.waitingContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              Icons.Rounded.NotificationsActive,
              contentDescription = null,
              tint = status.waitingOnContainer,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              "Enable phone ring & lock-screen visitor alerts",
              style = MaterialTheme.typography.bodySmall,
              color = status.waitingOnContainer,
            )
          }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = onTurnOnAlerts,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(40.dp).testTag("profile_enable_alerts_button"),
          ) {
            Text("Enable")
          }
        }
      }
    } else {
      GlassInnerSurface(modifier = Modifier.fillMaxWidth()) {
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
              Box(
                modifier =
                  Modifier.size(8.dp)
                    .clip(CircleShape)
                    .background(if (backgroundServiceRunning) status.freeText else status.warningAccent)
              )
              Spacer(Modifier.width(8.dp))
              Column {
                Text(
                  text = if (backgroundServiceRunning) "Lock-screen alerts active" else "Foreground alerts only",
                  style = MaterialTheme.typography.labelMedium,
                )
                Text(
                  text = "Listen in background",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Switch(
              checked = backgroundServiceRunning,
              onCheckedChange = onToggleBackgroundAlerts,
              modifier = Modifier.testTag("background_alerts_switch"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(
              onClick = onOpenBatterySettings,
              contentPadding = PaddingValues(horizontal = 10.dp),
              modifier = Modifier.testTag("battery_settings_button"),
            ) {
              Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Battery settings", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(
              onClick = onSendTestAlert,
              contentPadding = PaddingValues(horizontal = 10.dp),
              modifier = Modifier.testTag("test_alert_button"),
            ) {
              Text("Send test alert", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }

    OutlinedButton(
      onClick = onSignOut,
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = status.deniedText),
      border = BorderStroke(1.dp, status.deniedText.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
    ) {
      Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Sign out")
    }
  }
}

@Composable
private fun HostAccessBoundaryCard(member: StaffMember?) {
  GlassCard(modifier = Modifier.testTag("host_access_boundary_card")) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Rounded.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(8.dp))
      Text("Host Access & Privacy", style = MaterialTheme.typography.titleMedium)
    }
    Text(
      text =
        "Your account is signed in with Host permissions (${member?.shownAs ?: "Host"}). You can edit your own profile photo, name, designation, seating, alert settings, and visitor notifications in My desk.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    GlassInnerSurface(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = "Restricted: Global visitor logs, other hosts' visitor queues, Reception desk controls, and Admin settings are disabled for Host accounts.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp),
      )
    }
  }
}

@Composable
private fun MembersDirectoryCard(snapshot: DeskSnapshot) {
  var query by remember { mutableStateOf("") }
  val status = archPointStatusColors()
  val isAdminViewer = snapshot.activeRole == StaffRole.ADMIN
  val filteredStaff =
    remember(snapshot.staff, query, isAdminViewer) {
      val q = query.trim().lowercase()
      if (q.isEmpty()) {
        snapshot.staff
      } else {
        snapshot.staff.filter {
          it.fullName.lowercase().contains(q) ||
            it.shownAs.lowercase().contains(q) ||
            it.designation.lowercase().contains(q) ||
            it.department.lowercase().contains(q) ||
            it.floor.lowercase().contains(q) ||
            it.cabin.lowercase().contains(q) ||
            it.otherLocation.lowercase().contains(q) ||
            it.requiredThings.lowercase().contains(q) ||
            it.notes.lowercase().contains(q) ||
            (isAdminViewer && it.email.lowercase().contains(q))
        }
      }
    }

  GlassCard {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Team seating & required things", style = MaterialTheme.typography.titleLarge)
      Text(
        "Seating floor, cabin, alternate rooms, required items, and live status for every team member.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      placeholder = { Text("Filter member, department, floor, or items...") },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("member_directory_search_input"),
    )

    if (filteredStaff.isEmpty()) {
      Text(
        "No matching staff members found.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      filteredStaff.forEach { member ->
        val inMeetingVisit =
          snapshot.visits.firstOrNull {
            it.hostStaffId == member.id && it.parsedStatus() == VisitStatus.IN_MEETING
          }
        val waitingCount =
          snapshot.visits.count {
            it.hostStaffId == member.id && it.parsedStatus() == VisitStatus.WAITING
          }
        val avail = member.parsedAvailability()
        val tone = HostAvailability.resolveTone(avail, inMeeting = inMeetingVisit != null)

        GlassInnerSurface(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                StaffAvatar(
                  name = member.fullName,
                  photoUrl = member.photoUrl,
                  size = 38.dp,
                  textStyle = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(
                    text = member.shownAs.ifBlank { member.fullName },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  val subtitle =
                    listOfNotNull(
                      member.fullName.takeIf { it.isNotBlank() },
                      member.designation.takeIf { it.isNotBlank() },
                      member.department.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                  Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }

              StatusPill(
                text = if (!member.activeAccount) "Off" else member.parsedRole().label,
                containerColor =
                  if (!member.activeAccount) status.deniedContainer
                  else MaterialTheme.colorScheme.primaryContainer,
                contentColor =
                  if (!member.activeAccount) status.deniedOnContainer
                  else MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }

            if (member.activeAccount && member.hostListed) {
              if (inMeetingVisit != null) {
                StatusDotLabel(
                  tone = AvailabilityTone.ORANGE,
                  text = "In meeting with ${inMeetingVisit.visitorName}",
                )
              } else {
                val baseText = avail.receptionLabel
                StatusDotLabel(
                  tone = tone,
                  text = if (waitingCount > 0) "$baseText · $waitingCount waiting" else "$baseText · Visitors can ask",
                )
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.Chair,
                contentDescription = "Seating",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Seating: ${member.formattedSeating().ifBlank { "Not assigned yet" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }

            if (member.otherLocation.isNotBlank()) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.LocationOn,
                  contentDescription = "Other location",
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = "Other location: ${member.otherLocation}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Row(verticalAlignment = Alignment.Top) {
              Icon(
                imageVector = Icons.Rounded.Checklist,
                contentDescription = "Required things",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Required things: ${member.requiredThings.ifBlank { "No special items listed" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            if (member.notes.isNotBlank()) {
              Row(verticalAlignment = Alignment.Top) {
                Icon(
                  imageVector = Icons.Rounded.Info,
                  contentDescription = "Notes",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(15.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = member.notes,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            if (isAdminViewer) {
              Text(
                text = member.email.ifBlank { "Google login email not assigned yet (add in Admin)" },
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (member.email.isNotBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                  else status.warningAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}
