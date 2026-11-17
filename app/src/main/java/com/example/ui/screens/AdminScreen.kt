package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.VisitReason
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassInnerSurface
import com.example.ui.components.StatusPill
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.DeskSnapshot
import com.example.util.DeskFormatters

@Composable
fun AdminScreen(
  snapshot: DeskSnapshot,
  isBusy: Boolean,
  onSaveStaff: (StaffMember) -> Unit,
  onAddStaff: (
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
  ) -> Unit,
  onToggleReason: (VisitReason) -> Unit,
  onAddReason: (String) -> Unit,
  onSyncArchPointData: () -> Unit,
) {
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
          StaffDirectoryCard(
            staffList = snapshot.staff,
            isBusy = isBusy,
            onSaveStaff = onSaveStaff,
          )
        }
        Column(
          modifier = Modifier.weight(0.9f),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          ArchPointSyncCard(
            snapshot = snapshot,
            isBusy = isBusy,
            onSyncArchPointData = onSyncArchPointData,
          )
          AddStaffCard(isBusy = isBusy, onAddStaff = onAddStaff)
          ReasonsToVisitCard(
            reasons = snapshot.reasons,
            onToggleReason = onToggleReason,
            onAddReason = onAddReason,
          )
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
        ArchPointSyncCard(
          snapshot = snapshot,
          isBusy = isBusy,
          onSyncArchPointData = onSyncArchPointData,
        )
        StaffDirectoryCard(
          staffList = snapshot.staff,
          isBusy = isBusy,
          onSaveStaff = onSaveStaff,
        )
        AddStaffCard(isBusy = isBusy, onAddStaff = onAddStaff)
        ReasonsToVisitCard(
          reasons = snapshot.reasons,
          onToggleReason = onToggleReason,
          onAddReason = onAddReason,
        )
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun ArchPointSyncCard(
  snapshot: DeskSnapshot,
  isBusy: Boolean,
  onSyncArchPointData: () -> Unit,
) {
  val pastVisitCount = snapshot.visits.count { it.id.startsWith("visit_past_") }
  GlassCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Rounded.CloudSync,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Spacer(Modifier.width(8.dp))
      Text("ArchPoint Directory & Past Records", style = MaterialTheme.typography.titleMedium)
    }
    Text(
      "Currently ${snapshot.staff.size} team members & $pastVisitCount imported past visitor entries.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = onSyncArchPointData,
      enabled = !isBusy,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(46.dp).testTag("sync_archpoint_data_button"),
    ) {
      Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Sync ArchPoint Team & Past Visitor Data")
    }
  }
}

@Composable
private fun StaffDirectoryCard(
  staffList: List<StaffMember>,
  isBusy: Boolean,
  onSaveStaff: (StaffMember) -> Unit,
) {
  GlassCard {
    Text("Staff", style = MaterialTheme.typography.titleLarge)
    Text(
      "Reception checks visitors in. Hosts get alerts for their own visitors only. Admins manage everything. Assign each colleague's Google email when they join.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    staffList.forEach { member ->
      StaffMemberEditCard(
        member = member,
        isBusy = isBusy,
        onSave = onSaveStaff,
      )
    }
  }
}

@Composable
private fun StaffMemberEditCard(
  member: StaffMember,
  isBusy: Boolean,
  onSave: (StaffMember) -> Unit,
) {
  val status = archPointStatusColors()
  var fullName by remember(member.id, member.fullName) { mutableStateOf(member.fullName) }
  var shownAs by remember(member.id, member.shownAs) { mutableStateOf(member.shownAs) }
  var email by remember(member.id, member.email) { mutableStateOf(member.email) }
  var designation by remember(member.id, member.designation) { mutableStateOf(member.designation) }
  var department by remember(member.id, member.department) { mutableStateOf(member.department) }
  var floor by remember(member.id, member.floor) { mutableStateOf(member.floor) }
  var cabin by remember(member.id, member.cabin) { mutableStateOf(member.cabin) }
  var otherLocation by remember(member.id, member.otherLocation) { mutableStateOf(member.otherLocation) }
  var requiredThings by remember(member.id, member.requiredThings) { mutableStateOf(member.requiredThings) }
  var notes by remember(member.id, member.notes) { mutableStateOf(member.notes) }
  var listOrderStr by remember(member.id, member.listOrder) { mutableStateOf(member.listOrder.toString()) }
  var role by remember(member.id, member.role) { mutableStateOf(member.parsedRole()) }
  var hostListed by remember(member.id, member.hostListed) { mutableStateOf(member.hostListed) }
  var activeAccount by remember(member.id, member.activeAccount) { mutableStateOf(member.activeAccount) }

  GlassInnerSurface(
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Box(
            modifier =
              Modifier.size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = DeskFormatters.initials(member.fullName),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              fontWeight = FontWeight.Bold,
            )
          }
          Spacer(Modifier.width(10.dp))
          Column {
            Text(member.shownAs.ifBlank { member.fullName }, style = MaterialTheme.typography.titleSmall)
            Text(
              listOfNotNull(
                member.designation.takeIf { it.isNotBlank() },
                member.department.takeIf { it.isNotBlank() },
              ).joinToString(" · ").ifBlank { member.fullName },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          text = if (activeAccount) "Active" else "Off",
          containerColor = if (activeAccount) status.freeContainer else status.deniedContainer,
          contentColor = if (activeAccount) status.freeOnContainer else status.deniedOnContainer,
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it },
          label = { Text("Full name") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = shownAs,
          onValueChange = { shownAs = it },
          label = { Text("Shown as") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Google Email (login access)") },
        placeholder = { Text("colleague@gmail.com") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("edit_staff_email_${member.id}"),
      )

      val context = LocalContext.current
      val loginStatusText =
        when {
          !activeAccount -> "Login disabled while switched off"
          member.email.isBlank() -> "No login email set yet · Appears on Reception only"
          member.uid.isNotBlank() -> "Google Sign-In linked (${member.email})"
          else -> "Access ready · Waiting for first Google Sign-In"
        }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Rounded.VerifiedUser,
            contentDescription = null,
            tint =
              if (member.uid.isNotBlank() && activeAccount) status.freeText
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
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
                "Hi ${shownAs.ifBlank { fullName }}, your ArchPoint Visitors (${role.label}) desk access is ready.\n" +
                  "Open the ArchPoint Visitors app and tap 'Sign in with Google' using: $cleanMail"
              val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, msg)
                }
              context.startActivity(Intent.createChooser(shareIntent, "Share Login Invite"))
            },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp).testTag("invite_staff_${member.id}"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Invite", style = MaterialTheme.typography.labelSmall)
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = designation,
          onValueChange = { designation = it },
          label = { Text("Designation") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = department,
          onValueChange = { department = it },
          label = { Text("Department") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = floor,
          onValueChange = { floor = it },
          label = { Text("Floor") },
          singleLine = true,
          modifier = Modifier.weight(0.42f),
        )
        OutlinedTextField(
          value = cabin,
          onValueChange = { cabin = it },
          label = { Text("Cabin / room") },
          singleLine = true,
          modifier = Modifier.weight(0.58f),
        )
      }

      OutlinedTextField(
        value = otherLocation,
        onValueChange = { otherLocation = it },
        label = { Text("Other location (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = requiredThings,
        onValueChange = { requiredThings = it },
        label = { Text("Required things") },
        placeholder = { Text("e.g. NDA pad, blueprints, badge") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("edit_staff_required_${member.id}"),
      )

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Notes (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        RoleSelectorField(
          selectedRole = role,
          onRoleSelected = { role = it },
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = listOrderStr,
          onValueChange = { listOrderStr = it.filter { ch -> ch.isDigit() }.take(4) },
          label = { Text("Order") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.width(88.dp),
        )
      }

      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { hostListed = !hostListed },
        ) {
          Checkbox(checked = hostListed, onCheckedChange = { hostListed = it })
          Text("Visitors can ask for them", style = MaterialTheme.typography.bodySmall)
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { activeAccount = !activeAccount },
        ) {
          Checkbox(checked = activeAccount, onCheckedChange = { activeAccount = it })
          Text("Active (can sign in & receive visits)", style = MaterialTheme.typography.bodySmall)
        }
      }

      OutlinedButton(
        onClick = {
          onSave(
            member.copy(
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
              listOrder = listOrderStr.toLongOrNull() ?: member.listOrder,
              role = role.wireValue,
              hostListed = hostListed,
              activeAccount = activeAccount,
            )
          )
        },
        enabled = !isBusy && fullName.isNotBlank() && shownAs.isNotBlank(),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("save_staff_${member.id}"),
      ) {
        Text("Save staff changes")
      }
    }
  }
}

@Composable
private fun AddStaffCard(
  isBusy: Boolean,
  onAddStaff: (
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
  ) -> Unit,
) {
  var fullName by remember { mutableStateOf("") }
  var shownAs by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var designation by remember { mutableStateOf("") }
  var department by remember { mutableStateOf("") }
  var floor by remember { mutableStateOf("") }
  var cabin by remember { mutableStateOf("") }
  var otherLocation by remember { mutableStateOf("") }
  var requiredThings by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var role by remember { mutableStateOf(StaffRole.HOST) }
  var hostListed by remember { mutableStateOf(true) }

  GlassCard {
    Text("Add a staff member", style = MaterialTheme.typography.titleLarge)

    OutlinedTextField(
      value = fullName,
      onValueChange = { fullName = it },
      label = { Text("Full name") },
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("add_staff_name_input"),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = shownAs,
        onValueChange = { shownAs = it },
        label = { Text("Shown as") },
        placeholder = { Text("e.g. Himanshi Gupta") },
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("add_staff_shown_input"),
      )
      OutlinedTextField(
        value = floor,
        onValueChange = { floor = it },
        label = { Text("Floor") },
        placeholder = { Text("Ground, 2nd") },
        singleLine = true,
        modifier = Modifier.weight(0.75f).testTag("add_staff_floor_input"),
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = designation,
        onValueChange = { designation = it },
        label = { Text("Designation") },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = department,
        onValueChange = { department = it },
        label = { Text("Department") },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
    }

    OutlinedTextField(
      value = cabin,
      onValueChange = { cabin = it },
      label = { Text("Cabin / room") },
      placeholder = { Text("e.g. ArchPoint Wellness cabin") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("add_staff_cabin_input"),
    )

    OutlinedTextField(
      value = otherLocation,
      onValueChange = { otherLocation = it },
      label = { Text("Other location (optional)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = requiredThings,
      onValueChange = { requiredThings = it },
      label = { Text("Required things (optional)") },
      placeholder = { Text("e.g. NDA pad, visitor badge") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("add_staff_required_input"),
    )

    OutlinedTextField(
      value = notes,
      onValueChange = { notes = it },
      label = { Text("Notes (optional)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = email,
      onValueChange = { email = it },
      label = { Text("Google Email (optional until assigned)") },
      placeholder = { Text("colleague@gmail.com") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("add_staff_email_input"),
    )

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      RoleSelectorField(
        selectedRole = role,
        onRoleSelected = { role = it },
        modifier = Modifier.weight(1f),
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f).clickable { hostListed = !hostListed },
      ) {
        Checkbox(checked = hostListed, onCheckedChange = { hostListed = it })
        Text("Visitors can ask for them", style = MaterialTheme.typography.bodySmall)
      }
    }

    Button(
      onClick = {
        onAddStaff(
          fullName,
          shownAs,
          email,
          designation,
          department,
          floor,
          cabin,
          otherLocation,
          requiredThings,
          notes,
          role,
          hostListed,
        )
        fullName = ""
        shownAs = ""
        designation = ""
        department = ""
        floor = ""
        cabin = ""
        otherLocation = ""
        requiredThings = ""
        notes = ""
        email = ""
      },
      enabled = !isBusy && fullName.isNotBlank() && (email.isBlank() || email.contains("@")),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(50.dp).testTag("add_staff_button"),
    ) {
      Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Add staff member")
    }

    Text(
      "Staff with a Google email can install the app and sign in right away. Without email, they still appear on Reception.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ReasonsToVisitCard(
  reasons: List<VisitReason>,
  onToggleReason: (VisitReason) -> Unit,
  onAddReason: (String) -> Unit,
) {
  var newReason by remember { mutableStateOf("") }

  GlassCard {
    Text("Reasons to visit", style = MaterialTheme.typography.titleLarge)

    reasons.forEach { reason ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onToggleReason(reason) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(reason.label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = reason.shown,
            onCheckedChange = { onToggleReason(reason) },
          )
          Text(
            "shown",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = newReason,
        onValueChange = { newReason = it },
        label = { Text("New reason") },
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("new_reason_input"),
      )
      Button(
        onClick = {
          if (newReason.isNotBlank()) {
            onAddReason(newReason)
            newReason = ""
          }
        },
        enabled = newReason.isNotBlank(),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(52.dp).testTag("add_reason_button"),
      ) {
        Text("Add")
      }
    }
  }
}

@Composable
private fun RoleSelectorField(
  selectedRole: StaffRole,
  onRoleSelected: (StaffRole) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier = modifier) {
    OutlinedTextField(
      value = selectedRole.wireValue,
      onValueChange = {},
      readOnly = true,
      label = { Text("Role") },
      trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Select role") },
      modifier = Modifier.fillMaxWidth(),
    )
    Box(
      modifier =
        Modifier.matchParentSize()
          .clickable { expanded = true }
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      StaffRole.entries.forEach { role ->
        DropdownMenuItem(
          text = { Text("${role.label} (${role.wireValue})") },
          onClick = {
            onRoleSelected(role)
            expanded = false
          },
        )
      }
    }
  }
}
