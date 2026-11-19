package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AvailabilityTone
import com.example.data.model.HostAvailability
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.VisitReason
import com.example.data.model.VisitStatus
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassInnerSurface
import com.example.ui.components.GlassSegmentedControl
import com.example.ui.components.StaffAvatar
import com.example.ui.components.StaffEditorBottomSheet
import com.example.ui.components.StatusDotLabel
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.DeskSnapshot

private enum class AdminSectionTab {
  USERS,
  WORKSPACE,
}

private enum class StaffAccessFilter(val label: String) {
  ALL("All"),
  HOSTS("Hosts"),
  ADMIN("Admin"),
  RECEPTION("Reception"),
  HOST("Host role"),
  UNASSIGNED("No login"),
}

private sealed interface StaffSheetState {
  data class AddNew(
    val defaultRole: StaffRole = StaffRole.HOST,
    val defaultHostListed: Boolean = true,
  ) : StaffSheetState

  data class Edit(val member: StaffMember) : StaffSheetState
}

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
    photoUrl: String,
  ) -> Unit,
  onToggleReason: (VisitReason) -> Unit,
  onAddReason: (String) -> Unit,
  onSyncArchPointData: () -> Unit,
) {
  var sheetState by remember { mutableStateOf<StaffSheetState?>(null) }
  var staffQuery by remember { mutableStateOf("") }
  var roleFilter by remember { mutableStateOf(StaffAccessFilter.ALL) }
  var compactTab by remember { mutableStateOf(AdminSectionTab.USERS) }

  val hostsOverview =
    remember(snapshot.staff) {
      snapshot.staff.filter { it.hostListed || it.parsedRole() == StaffRole.HOST }
    }

  val inMeetingHostIds =
    remember(snapshot.visits) {
      snapshot.visits
        .filter { it.parsedStatus() == VisitStatus.IN_MEETING }
        .map { it.hostStaffId }
        .toSet()
    }

  val filteredStaff =
    remember(snapshot.staff, staffQuery, roleFilter) {
      val q = staffQuery.trim().lowercase()
      snapshot.staff.filter { member ->
        val matchesRole =
          when (roleFilter) {
            StaffAccessFilter.ALL -> true
            StaffAccessFilter.HOSTS -> member.hostListed || member.parsedRole() == StaffRole.HOST
            StaffAccessFilter.ADMIN -> member.parsedRole() == StaffRole.ADMIN
            StaffAccessFilter.RECEPTION -> member.parsedRole() == StaffRole.RECEPTION
            StaffAccessFilter.HOST -> member.parsedRole() == StaffRole.HOST
            StaffAccessFilter.UNASSIGNED -> member.email.isBlank() || !member.activeAccount
          }
        val matchesQuery =
          q.isEmpty() ||
            member.fullName.lowercase().contains(q) ||
            member.shownAs.lowercase().contains(q) ||
            member.designation.lowercase().contains(q) ||
            member.department.lowercase().contains(q) ||
            member.cabin.lowercase().contains(q) ||
            member.email.lowercase().contains(q)
        matchesRole && matchesQuery
      }
    }

  val activeLoginCount =
    remember(snapshot.staff) {
      snapshot.staff.count { it.activeAccount && it.email.isNotBlank() }
    }
  val adminCount =
    remember(snapshot.staff) {
      snapshot.staff.count { it.activeAccount && it.parsedRole() == StaffRole.ADMIN }
    }

  when (val activeSheet = sheetState) {
    is StaffSheetState.AddNew ->
      StaffEditorBottomSheet(
        existingMember = null,
        isBusy = isBusy,
        onDismiss = { sheetState = null },
        onCreateUser = onAddStaff,
        onSaveUser = onSaveStaff,
        defaultRole = activeSheet.defaultRole,
        defaultHostListed = activeSheet.defaultHostListed,
      )

    is StaffSheetState.Edit ->
      StaffEditorBottomSheet(
        existingMember = activeSheet.member,
        isBusy = isBusy,
        onDismiss = { sheetState = null },
        onCreateUser = onAddStaff,
        onSaveUser = onSaveStaff,
      )

    null -> Unit
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWide = maxWidth >= 860.dp

    if (isWide) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
      ) {
        LazyColumn(
          modifier = Modifier.weight(1.2f).fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 24.dp),
        ) {
          item(key = "admin_users_header") {
            AdminUsersHeaderCard(
              totalUsers = snapshot.staff.size,
              hostCount = hostsOverview.size,
              activeLogins = activeLoginCount,
              adminCount = adminCount,
              hostsOverview = hostsOverview,
              inMeetingHostIds = inMeetingHostIds,
              searchQuery = staffQuery,
              roleFilter = roleFilter,
              onSearchChange = { staffQuery = it },
              onRoleFilterSelect = { roleFilter = it },
              onAddHostClick = {
                sheetState = StaffSheetState.AddNew(defaultRole = StaffRole.HOST, defaultHostListed = true)
              },
              onAddUserClick = {
                sheetState = StaffSheetState.AddNew(defaultRole = StaffRole.RECEPTION, defaultHostListed = false)
              },
              onEditHostClick = { host -> sheetState = StaffSheetState.Edit(host) },
            )
          }
          if (filteredStaff.isEmpty()) {
            item(key = "admin_users_empty") {
              EmptyStaffFilterCard(hasAnyStaff = snapshot.staff.isNotEmpty())
            }
          } else {
            items(
              items = filteredStaff,
              key = { it.id },
            ) { member ->
              StaffSummaryRowCard(
                member = member,
                inMeeting = member.id in inMeetingHostIds,
                onEditClick = { sheetState = StaffSheetState.Edit(member) },
              )
            }
          }
        }

        LazyColumn(
          modifier = Modifier.weight(0.85f).fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          contentPadding = PaddingValues(bottom = 24.dp),
        ) {
          item(key = "reasons_card") {
            ReasonsToVisitCard(
              reasons = snapshot.reasons,
              onToggleReason = onToggleReason,
              onAddReason = onAddReason,
            )
          }
          item(key = "sync_card") {
            ArchPointSyncCard(
              snapshot = snapshot,
              isBusy = isBusy,
              onSyncArchPointData = onSyncArchPointData,
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item(key = "admin_section_tabs") {
          GlassSegmentedControl(
            options =
              listOf(
                AdminSectionTab.USERS to "Team (${snapshot.staff.size})",
                AdminSectionTab.WORKSPACE to "Reasons & Sync",
              ),
            selected = compactTab,
            onSelect = { compactTab = it },
            tagPrefix = "admin_section",
          )
        }

        if (compactTab == AdminSectionTab.USERS) {
          item(key = "admin_users_header") {
            AdminUsersHeaderCard(
              totalUsers = snapshot.staff.size,
              hostCount = hostsOverview.size,
              activeLogins = activeLoginCount,
              adminCount = adminCount,
              hostsOverview = hostsOverview,
              inMeetingHostIds = inMeetingHostIds,
              searchQuery = staffQuery,
              roleFilter = roleFilter,
              onSearchChange = { staffQuery = it },
              onRoleFilterSelect = { roleFilter = it },
              onAddHostClick = {
                sheetState = StaffSheetState.AddNew(defaultRole = StaffRole.HOST, defaultHostListed = true)
              },
              onAddUserClick = {
                sheetState = StaffSheetState.AddNew(defaultRole = StaffRole.RECEPTION, defaultHostListed = false)
              },
              onEditHostClick = { host -> sheetState = StaffSheetState.Edit(host) },
            )
          }

          if (filteredStaff.isEmpty()) {
            item(key = "admin_users_empty") {
              EmptyStaffFilterCard(hasAnyStaff = snapshot.staff.isNotEmpty())
            }
          } else {
            items(
              items = filteredStaff,
              key = { it.id },
            ) { member ->
              StaffSummaryRowCard(
                member = member,
                inMeeting = member.id in inMeetingHostIds,
                onEditClick = { sheetState = StaffSheetState.Edit(member) },
              )
            }
          }
        } else {
          item(key = "reasons_card") {
            ReasonsToVisitCard(
              reasons = snapshot.reasons,
              onToggleReason = onToggleReason,
              onAddReason = onAddReason,
            )
          }
          item(key = "sync_card") {
            ArchPointSyncCard(
              snapshot = snapshot,
              isBusy = isBusy,
              onSyncArchPointData = onSyncArchPointData,
            )
          }
        }

        item(key = "bottom_spacer") {
          Spacer(Modifier.height(16.dp))
        }
      }
    }
  }
}

@Composable
private fun AdminUsersHeaderCard(
  totalUsers: Int,
  hostCount: Int,
  activeLogins: Int,
  adminCount: Int,
  hostsOverview: List<StaffMember>,
  inMeetingHostIds: Set<String>,
  searchQuery: String,
  roleFilter: StaffAccessFilter,
  onSearchChange: (String) -> Unit,
  onRoleFilterSelect: (StaffAccessFilter) -> Unit,
  onAddHostClick: () -> Unit,
  onAddUserClick: () -> Unit,
  onEditHostClick: (StaffMember) -> Unit,
) {
  val status = archPointStatusColors()
  GlassCard(contentPadding = PaddingValues(16.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text("Hosts & Portal Access", style = MaterialTheme.typography.titleLarge)
        }
      }
      Text(
        text = "Only assigned emails can sign in. Each host sees only their own visits and clients.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Top quick Add Host + Add User buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onAddHostClick,
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("admin_add_host_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Add host", style = MaterialTheme.typography.labelLarge)
        }

        OutlinedButton(
          onClick = onAddUserClick,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("admin_add_user_button"),
        ) {
          Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Add user", style = MaterialTheme.typography.labelLarge)
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AdminStatBadge(value = hostCount.toString(), label = "hosts", modifier = Modifier.weight(1f))
      AdminStatBadge(value = activeLogins.toString(), label = "logins", modifier = Modifier.weight(1f))
      AdminStatBadge(value = "$adminCount/$totalUsers", label = "admins", modifier = Modifier.weight(1f))
    }

    // Top Quick-Edit Strip for All Hosts
    if (hostsOverview.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "HOSTS AT A GLANCE · TAP TO EDIT",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          hostsOverview.forEach { host ->
            val inMeeting = host.id in inMeetingHostIds
            val tone = HostAvailability.resolveTone(host.parsedAvailability(), inMeeting)
            val toneDot = status.dotColor(tone)
            val displayName = host.shownAs.ifBlank { host.fullName }
            GlassInnerSurface(
              shape = RoundedCornerShape(14.dp),
              borderColor = toneDot.copy(alpha = 0.45f),
              modifier =
                Modifier
                  .width(164.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onEditHostClick(host) }
                  .testTag("admin_host_quick_card_${host.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Box {
                  StaffAvatar(
                    name = displayName,
                    photoUrl = host.photoUrl,
                    size = 34.dp,
                    textStyle = MaterialTheme.typography.labelSmall,
                  )
                  Box(
                    modifier =
                      Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(toneDot)
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  val sub =
                    host.cabin.takeIf { it.isNotBlank() }
                      ?: host.designation.takeIf { it.isNotBlank() }
                      ?: host.parsedAvailability().receptionLabel
                  Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Icon(
                  imageVector = Icons.Rounded.Edit,
                  contentDescription = "Edit $displayName",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }
      }
    }

    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchChange,
      placeholder = { Text("Search name, cabin, or designation...") },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search staff", modifier = Modifier.size(18.dp)) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { onSearchChange("") }) {
            Icon(Icons.Rounded.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
          }
        }
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("admin_staff_search_input"),
    )

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      StaffAccessFilter.entries.forEach { filter ->
        FilterChip(
          selected = roleFilter == filter,
          onClick = { onRoleFilterSelect(filter) },
          label = { Text(filter.label) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
          modifier = Modifier.testTag("admin_filter_${filter.name.lowercase()}"),
        )
      }
    }
  }
}

@Composable
private fun AdminStatBadge(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  GlassInnerSurface(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun StaffSummaryRowCard(
  member: StaffMember,
  inMeeting: Boolean,
  onEditClick: () -> Unit,
) {
  val status = archPointStatusColors()
  val displayName = member.fullName.ifBlank { member.shownAs }
  val designationLabel =
    member.designation.trim().ifBlank { member.parsedRole().label }
  val avail = member.parsedAvailability()
  val tone = HostAvailability.resolveTone(avail, inMeeting)
  val dotLabel = if (inMeeting) "In meeting" else avail.receptionLabel

  GlassInnerSurface(
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth().testTag("admin_staff_row_${member.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
      ) {
        StaffAvatar(
          name = displayName,
          photoUrl = member.photoUrl,
          size = 44.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = designationLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (member.hostListed && member.activeAccount) {
            StatusDotLabel(tone = tone, text = dotLabel)
          }
        }
      }

      Spacer(Modifier.width(8.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val accessBadgeText =
          when {
            !member.activeAccount -> "Off"
            member.email.isBlank() -> "No login"
            else -> member.parsedRole().label
          }
        val (badgeBg, badgeFg) =
          when {
            !member.activeAccount -> status.deniedContainer to status.deniedOnContainer
            member.email.isBlank() -> status.waitingContainer to status.waitingOnContainer
            else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
          }
        StatusPill(
          text = accessBadgeText,
          containerColor = badgeBg,
          contentColor = badgeFg,
        )

        OutlinedButton(
          onClick = onEditClick,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("edit_staff_button_${member.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Edit $displayName",
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text("Edit", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@Composable
private fun EmptyStaffFilterCard(hasAnyStaff: Boolean) {
  GlassCard(contentPadding = PaddingValues(18.dp)) {
    Text(
      text =
        if (hasAnyStaff) {
          "No staff members match this search or filter."
        } else {
          "No staff members yet. Tap Add user to create your first staff account."
        },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sync_archpoint_data_button"),
    ) {
      Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Sync ArchPoint Team & Past Visitor Data")
    }
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
            .heightIn(min = 48.dp)
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
