package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.HostAvailability
import com.example.data.model.StaffMember
import com.example.data.model.Visit
import com.example.data.model.VisitStatus
import com.example.ui.theme.ArchAmberBright
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MeetingBlueBright
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.HostClientSummary
import com.example.ui.viewmodel.MainViewModel
import com.example.util.DeskFormatters
import java.util.Date

private enum class HostNotificationSection {
  LIVE_NOTIFICATIONS,
  MY_CLIENTS,
  MY_HISTORY,
}

private enum class HostHistoryFilter(val label: String) {
  ALL("All"),
  TODAY("Today"),
  DONE("Completed"),
  DENIED("Declined"),
}

/**
 * Dedicated Host Dashboard component.
 * Displays ONLY visitor notifications associated with [userEmail] and the host's own status,
 * hiding all global office logs, colleague directories, and administrative/reception controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HostDashboard(
  currentStaff: StaffMember?,
  visits: List<Visit>,
  userEmail: String,
  nowMillis: Long,
  inFlightVisitIds: Set<String> = emptySet(),
  onUpdateAvailability: (HostAvailability) -> Unit,
  onSendIn: (Visit, String) -> Unit,
  onSendWaitNote: (Visit, String) -> Unit,
  onNotToday: (Visit, String) -> Unit,
  onMeetingDone: (Visit) -> Unit,
  modifier: Modifier = Modifier,
) {
  val status = archPointStatusColors()
  val haptic = LocalHapticFeedback.current
  val context = LocalContext.current
  val cleanEmail = userEmail.trim().lowercase()
  val displayName =
    currentStaff?.shownAs?.ifBlank { currentStaff.fullName } ?: "Host"
  val roleTitle =
    listOfNotNull(
      currentStaff?.designation?.takeIf { it.isNotBlank() },
      currentStaff?.department?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
  val mySeating = currentStaff?.formattedSeating().orEmpty().ifBlank { currentStaff?.cabin.orEmpty() }
  val myOtherLocation = currentStaff?.otherLocation.orEmpty()
  val myRequiredThings = currentStaff?.requiredThings.orEmpty()
  val myAvailability = currentStaff?.parsedAvailability() ?: HostAvailability.AVAILABLE
  val todayStart = remember(nowMillis / 60_000L) { DeskFormatters.startOfTodayMillis(nowMillis) }

  // Strict email-based filter guarantees only notifications for the current user's email are shown
  val myVisits =
    remember(visits, cleanEmail, currentStaff?.id) {
      MainViewModel.filterHostSpecificVisits(
        visits = visits,
        userEmail = cleanEmail,
        staffId = currentStaff?.id,
      )
    }
  val myWaiting =
    remember(myVisits) {
      myVisits
        .filter { it.parsedStatus() == VisitStatus.WAITING }
        .sortedWith(
          compareByDescending<Visit> { it.remindedCount > 0L }
            .thenBy { it.waitStartedAt?.toDate()?.time ?: Long.MAX_VALUE }
        )
    }
  val myInMeeting =
    remember(myVisits) {
      myVisits
        .filter { it.parsedStatus() == VisitStatus.IN_MEETING }
        .sortedByDescending { it.meetingStartedAt?.toDate()?.time ?: 0L }
    }
  val myAllCompleted =
    remember(myVisits) {
      myVisits
        .filter {
          val st = it.parsedStatus()
          st == VisitStatus.DONE || st == VisitStatus.DENIED
        }
        .sortedByDescending { it.endedAt?.toDate()?.time ?: it.arrivedAt?.toDate()?.time ?: 0L }
    }
  val metTodayCount =
    remember(myVisits, todayStart) {
      myVisits.count {
        it.parsedStatus() == VisitStatus.DONE &&
          (it.arrivedAt?.toDate()?.time ?: 0L) >= todayStart
      }
    }
  val longestWaitMinutes =
    myWaiting.maxOfOrNull {
      DeskFormatters.minutesBetween(it.waitStartedAt?.toDate()?.time, nowMillis)
    } ?: 0L

  val activeNotificationsCount = myWaiting.size + myInMeeting.size
  val myClients =
    remember(myVisits, cleanEmail, currentStaff?.id) {
      MainViewModel.buildHostClients(
        visits = myVisits,
        userEmail = cleanEmail,
        staffId = currentStaff?.id,
      )
    }
  var feedSection by remember { mutableStateOf(HostNotificationSection.LIVE_NOTIFICATIONS) }
  var clientsQuery by remember { mutableStateOf("") }
  var historyQuery by remember { mutableStateOf("") }
  var historyFilter by remember { mutableStateOf(HostHistoryFilter.ALL) }
  var actionSheet by remember { mutableStateOf<HostActionSheetState?>(null) }

  val filteredClients =
    remember(myClients, clientsQuery) {
      val q = clientsQuery.trim().lowercase()
      if (q.isEmpty()) {
        myClients
      } else {
        myClients.filter {
          it.name.lowercase().contains(q) ||
            it.company.lowercase().contains(q) ||
            it.mobile.lowercase().contains(q) ||
            it.email.lowercase().contains(q) ||
            it.jobProfile.lowercase().contains(q) ||
            it.lastReason.lowercase().contains(q) ||
            it.lastRemark.lowercase().contains(q)
        }
      }
    }

  val filteredHistory =
    remember(myAllCompleted, historyQuery, historyFilter, todayStart) {
      val q = historyQuery.trim().lowercase()
      myAllCompleted.filter { visit ->
        val matchesTab =
          when (historyFilter) {
            HostHistoryFilter.ALL -> true
            HostHistoryFilter.TODAY -> (visit.arrivedAt?.toDate()?.time ?: 0L) >= todayStart
            HostHistoryFilter.DONE -> visit.parsedStatus() == VisitStatus.DONE
            HostHistoryFilter.DENIED -> visit.parsedStatus() == VisitStatus.DENIED
          }
        val matchesQuery =
          q.isEmpty() ||
            visit.visitorName.lowercase().contains(q) ||
            visit.visitorCompany.lowercase().contains(q) ||
            visit.reason.lowercase().contains(q) ||
            visit.remark.lowercase().contains(q) ||
            visit.visitorJobProfile.lowercase().contains(q)
        matchesTab && matchesQuery
      }
    }

  val dialVisitor: (String) -> Unit = { phone ->
    val cleanPhone = phone.filter { it.isDigit() || it == '+' }
    if (cleanPhone.length >= 7) {
      try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")))
      } catch (_: Exception) {
      }
    }
  }

  when (val sheet = actionSheet) {
    is HostActionSheetState.QuickReply ->
      HostQuickReplySheet(
        visit = sheet.visit,
        isInMeeting = sheet.isInMeeting,
        onDismiss = { actionSheet = null },
        onSendReply = { note ->
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onSendWaitNote(sheet.visit, note)
        },
      )

    is HostActionSheetState.DeclineReason ->
      HostDeclineReasonSheet(
        visit = sheet.visit,
        onDismiss = { actionSheet = null },
        onConfirmDecline = { note ->
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onNotToday(sheet.visit, note)
        },
      )

    null -> Unit
  }

  Box(
    modifier = modifier.fillMaxSize().testTag("host_dashboard_root"),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // 1. Host Identity, Email Scope & Live Status Card
      item(key = "host_status_header") {
        GlassCard(modifier = Modifier.testTag("host_status_card")) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Lock,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text = "HOST DASHBOARD",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }

            val activeMeetingVisit = myInMeeting.firstOrNull()
            val (liveBadgeText, liveBg, liveFg) =
              if (activeMeetingVisit != null) {
                val mins =
                  DeskFormatters.minutesBetween(
                    activeMeetingVisit.meetingStartedAt?.toDate()?.time,
                    nowMillis,
                  )
                Triple(
                  "In meeting · ${mins}m",
                  status.meetingContainer,
                  status.meetingOnContainer,
                )
              } else {
                when (myAvailability) {
                  HostAvailability.AVAILABLE ->
                    Triple("Available", status.freeContainer, status.freeOnContainer)
                  HostAvailability.BUSY ->
                    Triple("Busy", status.waitingContainer, status.waitingOnContainer)
                  HostAvailability.AWAY ->
                    Triple("Away", status.deniedContainer, status.deniedOnContainer)
                }
              }
            StatusPill(text = liveBadgeText, containerColor = liveBg, contentColor = liveFg)
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            StaffAvatar(
              name = displayName,
              photoUrl = currentStaff?.photoUrl.orEmpty(),
              size = 48.dp,
              textStyle = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${DeskFormatters.greetingForHour()}, $displayName",
                style = MaterialTheme.typography.titleLarge,
              )
              if (roleTitle.isNotBlank()) {
                Text(
                  text = roleTitle,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (cleanEmail.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Rounded.AlternateEmail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(
                    text = "Only notifications for $cleanEmail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }

          // Self-service host availability status toggle (instant optimistic response)
          if (currentStaff != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "MY LIVE STATUS AT RECEPTION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              GlassSegmentedControl(
                options =
                  listOf(
                    HostAvailability.AVAILABLE to HostAvailability.AVAILABLE.label,
                    HostAvailability.BUSY to HostAvailability.BUSY.label,
                    HostAvailability.AWAY to HostAvailability.AWAY.label,
                  ),
                selected = myAvailability,
                onSelect = { nextStatus ->
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onUpdateAvailability(nextStatus)
                },
                tagPrefix = "host_status",
              )
            }
          }

          // Quick nudge if marked Busy/Away while visitors are waiting and no active meeting
          AnimatedVisibility(
            visible = currentStaff != null &&
              myAvailability != HostAvailability.AVAILABLE &&
              myInMeeting.isEmpty() &&
              myWaiting.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
          ) {
            GlassInnerSurface(
              containerColor = status.waitingContainer.copy(alpha = 0.7f),
              borderColor = status.warningAccent.copy(alpha = 0.45f),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Reception sees you as ${myAvailability.label}",
                  style = MaterialTheme.typography.labelMedium,
                  color = status.waitingOnContainer,
                  modifier = Modifier.weight(1f),
                )
                TextButton(
                  onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onUpdateAvailability(HostAvailability.AVAILABLE)
                  },
                  modifier = Modifier.testTag("host_quick_go_available"),
                ) {
                  Text("Go Available", fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          if (mySeating.isNotBlank() || myOtherLocation.isNotBlank() || myRequiredThings.isNotBlank()) {
            GlassInnerSurface(modifier = Modifier.fillMaxWidth()) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (mySeating.isNotBlank()) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Rounded.Chair,
                      contentDescription = "My seating",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                      text =
                        if (myOtherLocation.isNotBlank()) {
                          "Seating: $mySeating · Alt: $myOtherLocation"
                        } else {
                          "Seating: $mySeating"
                        },
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
                if (myRequiredThings.isNotBlank()) {
                  Row(verticalAlignment = Alignment.Top) {
                    Icon(
                      imageVector = Icons.Rounded.Checklist,
                      contentDescription = "Required things",
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(15.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                      text = "Required things: $myRequiredThings",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            HostKpiTile(
              value = myWaiting.size.toString(),
              label = "waiting",
              accent = if (myWaiting.isNotEmpty()) status.warningAccent else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            HostKpiTile(
              value = if (myInMeeting.isEmpty()) "—" else myInMeeting.size.toString(),
              label = "in meeting",
              accent = if (myInMeeting.isNotEmpty()) status.meetingText else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            HostKpiTile(
              value = metTodayCount.toString(),
              label = "met today",
              accent = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            HostKpiTile(
              value = if (myWaiting.isEmpty()) "—" else "${longestWaitMinutes}m",
              label = "longest wait",
              accent = if (longestWaitMinutes >= 15L) status.warningAccent else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      // 2. Host-Only Feed Selector (Live Visitor Notifications vs. My Clients vs. My History)
      item(key = "feed_selector") {
        GlassSegmentedControl(
          options =
            listOf(
              HostNotificationSection.LIVE_NOTIFICATIONS to
                if (activeNotificationsCount > 0) "Live ($activeNotificationsCount)" else "Live",
              HostNotificationSection.MY_CLIENTS to "Clients (${myClients.size})",
              HostNotificationSection.MY_HISTORY to "History (${myAllCompleted.size})",
            ),
          selected = feedSection,
          onSelect = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            feedSection = it
          },
          tagPrefix = "host_feed",
        )
      }

      if (feedSection == HostNotificationSection.LIVE_NOTIFICATIONS) {
        if (myWaiting.isNotEmpty()) {
          item(key = "waiting_section_header") {
            Text(
              text = "MY VISITOR NOTIFICATIONS (${myWaiting.size})",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          itemsIndexed(
            items = myWaiting,
            key = { _, visit -> "waiting_${visit.id}" },
          ) { idx, visit ->
            HostWaitingNotificationCard(
              visit = visit,
              isNext = idx == 0,
              nowMillis = nowMillis,
              isInFlight = visit.id in inFlightVisitIds,
              otherLocation = myOtherLocation,
              onDialVisitor = { dialVisitor(visit.visitorMobile) },
              onSendIn = { note ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSendIn(visit, note)
              },
              onQuickWaitNote = { note ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSendWaitNote(visit, note)
              },
              onOpenReplySheet = {
                actionSheet = HostActionSheetState.QuickReply(visit, isInMeeting = false)
              },
              onOpenDeclineSheet = {
                actionSheet = HostActionSheetState.DeclineReason(visit)
              },
              modifier = Modifier.animateItem(),
            )
          }
        }

        if (myInMeeting.isNotEmpty()) {
          item(key = "meeting_section_header") {
            Text(
              text = "IN MEETING NOW",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          items(
            items = myInMeeting,
            key = { visit -> "meeting_${visit.id}" },
          ) { visit ->
            HostInMeetingCard(
              visit = visit,
              nowMillis = nowMillis,
              isInFlight = visit.id in inFlightVisitIds,
              onOpenDeskNoteSheet = {
                actionSheet = HostActionSheetState.QuickReply(visit, isInMeeting = true)
              },
              onMeetingDone = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMeetingDone(visit)
              },
              modifier = Modifier.animateItem(),
            )
          }
        }

        if (myWaiting.isEmpty() && myInMeeting.isEmpty()) {
          item(key = "empty_notifications") {
            GlassCard(contentPadding = PaddingValues(24.dp)) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Image(
                  painter = painterResource(id = R.drawable.img_empty_visitors),
                  contentDescription = "No waiting visitors",
                  modifier = Modifier.size(136.dp).clip(RoundedCornerShape(16.dp)),
                  contentScale = ContentScale.Crop,
                )
                Text("No active notifications", style = MaterialTheme.typography.titleMedium)
                Text(
                  "When reception checks a visitor in for $displayName, your notification will appear here immediately.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }
        }
      } else if (feedSection == HostNotificationSection.MY_CLIENTS) {
        item(key = "clients_controls") {
          GlassCard(contentPadding = PaddingValues(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "MY CLIENTS & VISITORS (${myClients.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              StatusPill(
                text = "Private to you",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            OutlinedTextField(
              value = clientsQuery,
              onValueChange = { clientsQuery = it },
              placeholder = { Text("Search your client name, company, phone...") },
              leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
              trailingIcon = {
                if (clientsQuery.isNotEmpty()) {
                  IconButton(onClick = { clientsQuery = "" }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                  }
                }
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("host_clients_search_input"),
            )
          }
        }

        if (filteredClients.isEmpty()) {
          item(key = "clients_empty") {
            GlassCard(contentPadding = PaddingValues(18.dp)) {
              Text(
                text =
                  if (myClients.isEmpty()) {
                    "No clients or visitors recorded for $cleanEmail yet."
                  } else {
                    "No clients match \"$clientsQuery\"."
                  },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          items(
            items = filteredClients,
            key = { client -> "client_${client.key}" },
          ) { client ->
            HostClientSummaryCard(
              client = client,
              onDial = { dialVisitor(client.mobile) },
              modifier = Modifier.animateItem(),
            )
          }
        }
      } else {
        item(key = "history_controls") {
          GlassCard(contentPadding = PaddingValues(14.dp)) {
            Text(
              text = "MY VISITOR NOTIFICATIONS HISTORY",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = historyQuery,
              onValueChange = { historyQuery = it },
              placeholder = { Text("Search visitor, company, or reason...") },
              leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
              trailingIcon = {
                if (historyQuery.isNotEmpty()) {
                  IconButton(onClick = { historyQuery = "" }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                  }
                }
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("host_history_search_input"),
            )
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              HostHistoryFilter.entries.forEach { filter ->
                FilterChip(
                  selected = historyFilter == filter,
                  onClick = { historyFilter = filter },
                  label = { Text(filter.label) },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = MaterialTheme.colorScheme.primary,
                      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
              }
            }
          }
        }

        if (filteredHistory.isEmpty()) {
          item(key = "history_empty") {
            GlassCard(contentPadding = PaddingValues(18.dp)) {
              Text(
                text =
                  if (myAllCompleted.isEmpty()) {
                    "No past visitor notifications for $cleanEmail yet."
                  } else {
                    "No past notifications match your current filter."
                  },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          itemsIndexed(
            items = filteredHistory,
            key = { _, visit -> "history_${visit.id}" },
          ) { idx, visit ->
            GlassInnerSurface(
              modifier = Modifier.fillMaxWidth().animateItem(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                HostHistoryNotificationRow(
                  visit = visit,
                  onDialVisitor = { dialVisitor(visit.visitorMobile) },
                )
              }
            }
          }
        }
      }

      // Privacy boundary reminder
      item(key = "privacy_footer") {
        GlassInnerSurface(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Private Host View: fetches and shows only notifications addressed to $cleanEmail. Global visitor logs & admin controls are hidden.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item(key = "bottom_spacer") {
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun HostKpiTile(
  value: String,
  label: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  GlassInnerSurface(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
        color = accent,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostWaitingNotificationCard(
  visit: Visit,
  isNext: Boolean,
  nowMillis: Long,
  isInFlight: Boolean,
  otherLocation: String,
  onDialVisitor: () -> Unit,
  onSendIn: (String) -> Unit,
  onQuickWaitNote: (String) -> Unit,
  onOpenReplySheet: () -> Unit,
  onOpenDeclineSheet: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val status = archPointStatusColors()
  val waitMin = DeskFormatters.minutesBetween(visit.waitStartedAt?.toDate()?.time, nowMillis)
  val arrivedTime = DeskFormatters.formatTime(visit.arrivedAt?.toDate())
  val hasReminder = visit.remindedCount > 0L
  val hasDialablePhone =
    visit.visitorMobile.filter { it.isDigit() }.length >= 10 &&
      visit.visitorMobile != "919999999999"

  GlassCard(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    borderBrush =
      if (isNext || hasReminder) {
        Brush.linearGradient(
          listOf(ArchAmberBright.copy(alpha = 0.85f), ArchAmberBright.copy(alpha = 0.25f))
        )
      } else {
        null
      },
  ) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (hasReminder) {
        StatusPill(
          text = "REMINDER FROM RECEPTION (${visit.remindedCount}x)",
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
      } else if (isNext) {
        StatusPill(
          text = "NEXT NOTIFICATION",
          containerColor = status.waitingContainer,
          contentColor = status.waitingOnContainer,
        )
      }
      StatusPill(
        text = visit.reason,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      StatusPill(
        text = DeskFormatters.ordinalVisit(visit.visitNumber),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .background(
              if (isNext || hasReminder) status.waitingContainer
              else MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = DeskFormatters.initials(visit.visitorName),
          style = MaterialTheme.typography.titleMedium,
          color =
            if (isNext || hasReminder) status.waitingOnContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
          fontWeight = FontWeight.Bold,
        )
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(visit.visitorName, style = MaterialTheme.typography.titleLarge)
        val orgProfile =
          listOfNotNull(
            visit.visitorCompany.takeIf { it.isNotBlank() },
            visit.visitorJobProfile.takeIf { it.isNotBlank() },
          ).joinToString(" · ")
        if (orgProfile.isNotBlank()) {
          Text(
            orgProfile,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        val contactLine =
          listOfNotNull(
            visit.visitorMobile.takeIf { hasDialablePhone },
            visit.visitorEmail.takeIf { it.isNotBlank() },
          ).joinToString(" · ")
        if (contactLine.isNotBlank()) {
          Text(
            text = contactLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (hasDialablePhone) {
        IconButton(
          onClick = onDialVisitor,
          modifier = Modifier.size(48.dp).testTag("host_call_visitor_${visit.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Call,
            contentDescription = "Call ${visit.visitorName}",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    if (visit.remark.isNotBlank()) {
      GlassInnerSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector = Icons.Rounded.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp).padding(top = 2.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Remark: ${visit.remark}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Rounded.Schedule,
        contentDescription = null,
        tint = if (waitMin >= 15L) status.warningAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(15.dp),
      )
      Spacer(Modifier.width(6.dp))
      Text(
        text =
          buildString {
            append("Waiting ${waitMin} min · arrived $arrivedTime")
            if (visit.waitNote.isNotBlank()) {
              append(" · Sent: ${visit.waitNote}")
            }
          },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (waitMin >= 15L) status.warningAccent else MaterialTheme.colorScheme.onSurface,
      )
    }

    // Primary Send-In CTA (+ quick alternate location option if host configured otherLocation)
    Button(
      onClick = { onSendIn("Send in") },
      enabled = !isInFlight,
      colors =
        ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("host_send_in_${visit.id}"),
    ) {
      if (isInFlight) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text("Updating...", style = MaterialTheme.typography.labelLarge)
      } else {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Send in", style = MaterialTheme.typography.labelLarge)
      }
    }

    if (otherLocation.isNotBlank()) {
      OutlinedButton(
        onClick = { onSendIn("Send to $otherLocation") },
        enabled = !isInFlight,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("host_send_alt_${visit.id}"),
      ) {
        Icon(Icons.Rounded.MeetingRoom, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          text = "Send to $otherLocation",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Horizontal location scroll strip so host can send visitor to any room/cabin in one tap
    LocationPresetScrollBar(
      title = "Send visitor to location (scroll & tap)",
      selectedPresetId = null,
      onSelectPreset = { preset ->
        if (!isInFlight) {
          onSendIn("Send to ${preset.chipText}")
        }
      },
      tagPrefix = "host_send_loc_${visit.id}",
    )

    // 1-tap Wait Presets & Quick Instructions Strip
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = { onQuickWaitNote("Wait 5 min") },
        enabled = !isInFlight,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(44.dp).testTag("host_wait_5_${visit.id}"),
      ) {
        Text("Wait 5m")
      }
      OutlinedButton(
        onClick = { onQuickWaitNote("asked to wait 10 min") },
        enabled = !isInFlight,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(44.dp).testTag("host_wait_10_${visit.id}"),
      ) {
        Text("Wait 10m")
      }
      OutlinedButton(
        onClick = { onQuickWaitNote("Wait 15 min") },
        enabled = !isInFlight,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(44.dp).testTag("host_wait_15_${visit.id}"),
      ) {
        Text("Wait 15m")
      }
      OutlinedButton(
        onClick = { onQuickWaitNote("Coming out to reception") },
        enabled = !isInFlight,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(44.dp).testTag("host_coming_out_${visit.id}"),
      ) {
        Text("Coming out")
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = onOpenReplySheet,
        enabled = !isInFlight,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("host_reply_button_${visit.id}"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Reply...")
      }
      OutlinedButton(
        onClick = onOpenDeclineSheet,
        enabled = !isInFlight,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = status.deniedText),
        border = BorderStroke(1.dp, status.deniedText.copy(alpha = 0.5f)),
        modifier = Modifier.weight(1f).height(48.dp).testTag("host_not_today_${visit.id}"),
      ) {
        Text("Not today")
      }
    }
  }
}

@Composable
private fun HostInMeetingCard(
  visit: Visit,
  nowMillis: Long,
  isInFlight: Boolean,
  onOpenDeskNoteSheet: () -> Unit,
  onMeetingDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val status = archPointStatusColors()
  val meetingMin = DeskFormatters.minutesBetween(visit.meetingStartedAt?.toDate()?.time, nowMillis)
  GlassCard(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    borderBrush =
      Brush.linearGradient(
        listOf(MeetingBlueBright.copy(alpha = 0.65f), MeetingBlueBright.copy(alpha = 0.25f))
      ),
    contentPadding = PaddingValues(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(visit.visitorName, style = MaterialTheme.typography.titleMedium)
        val sub =
          listOfNotNull(
            visit.visitorCompany.takeIf { it.isNotBlank() },
            visit.visitorJobProfile.takeIf { it.isNotBlank() },
            visit.reason.takeIf { it.isNotBlank() },
          ).joinToString(" · ")
        if (sub.isNotBlank()) {
          Text(
            sub,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      StatusPill(
        text = "In meeting · ${meetingMin}m",
        containerColor = status.meetingContainer,
        contentColor = status.meetingOnContainer,
      )
    }
    if (visit.remark.isNotBlank()) {
      Text(
        text = "Note: ${visit.remark}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (visit.waitNote.isNotBlank() && !visit.waitNote.equals("Send in", ignoreCase = true)) {
      Text(
        text = "Desk update: ${visit.waitNote}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = onOpenDeskNoteSheet,
        enabled = !isInFlight,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(0.9f).height(48.dp).testTag("host_meeting_note_${visit.id}"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Desk note")
      }
      Button(
        onClick = onMeetingDone,
        enabled = !isInFlight,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier = Modifier.weight(1.2f).height(48.dp).testTag("host_meeting_done_${visit.id}"),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text("Meeting done", fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun HostClientSummaryCard(
  client: HostClientSummary,
  onDial: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val status = archPointStatusColors()
  val hasDialablePhone =
    client.mobile.filter { it.isDigit() }.length >= 10 &&
      client.mobile != "919999999999"
  val lastVisitDate =
    if (client.lastVisitedAtMillis > 0L) {
      DeskFormatters.formatLogDate(Date(client.lastVisitedAtMillis))
    } else {
      "Recent"
    }

  GlassInnerSurface(
    modifier = modifier.fillMaxWidth().testTag("host_client_card_${client.key}"),
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          StaffAvatar(
            name = client.name,
            photoUrl = "",
            size = 42.dp,
            textStyle = MaterialTheme.typography.titleSmall,
          )
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = client.name,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val sub =
              listOfNotNull(
                client.company.takeIf { it.isNotBlank() },
                client.jobProfile.takeIf { it.isNotBlank() },
              ).joinToString(" · ")
            if (sub.isNotBlank()) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.Business,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  text = sub,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(
          text = if (client.visitCount == 1) "1 visit" else "${client.visitCount} visits",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      val meta =
        listOfNotNull(
          "Last: $lastVisitDate",
          client.lastReason.takeIf { it.isNotBlank() },
          "${client.completedCount} completed".takeIf { client.completedCount > 0 },
        ).joinToString(" · ")
      Text(
        text = meta,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (client.lastRemark.isNotBlank()) {
        Text(
          text = "Remark: ${client.lastRemark}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (hasDialablePhone || client.email.isNotBlank()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            if (hasDialablePhone) {
              Text(
                text = client.mobile,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            if (client.email.isNotBlank()) {
              Text(
                text = client.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          if (hasDialablePhone) {
            IconButton(
              onClick = onDial,
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = "Call ${client.name}",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HostHistoryNotificationRow(
  visit: Visit,
  onDialVisitor: () -> Unit,
) {
  val status = archPointStatusColors()
  val isDenied = visit.parsedStatus() == VisitStatus.DENIED
  val dateLabel = DeskFormatters.formatLogDate(visit.arrivedAt?.toDate())
  val timeLabel = DeskFormatters.formatTime(visit.arrivedAt?.toDate())
  val startMs = visit.meetingStartedAt?.toDate()?.time
  val endMs = visit.endedAt?.toDate()?.time
  val durationMin =
    if (startMs != null && endMs != null && endMs >= startMs) {
      DeskFormatters.minutesBetween(startMs, endMs).coerceAtLeast(1L)
    } else {
      null
    }
  val hasDialablePhone =
    visit.visitorMobile.filter { it.isDigit() }.length >= 10 &&
      visit.visitorMobile != "919999999999"

  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = visit.visitorName,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val meta =
        listOfNotNull(
          visit.visitorCompany.takeIf { it.isNotBlank() },
          visit.visitorJobProfile.takeIf { it.isNotBlank() },
          visit.reason.takeIf { it.isNotBlank() },
          "$dateLabel · $timeLabel",
          durationMin?.let { "${it}m meeting" },
        ).joinToString(" · ")
      Text(
        text = meta,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val noteLine =
        listOfNotNull(
          visit.visitorEmail.takeIf { it.isNotBlank() },
          visit.remark.takeIf { it.isNotBlank() },
          visit.waitNote.takeIf {
            it.isNotBlank() &&
              !it.equals("Past entry", ignoreCase = true) &&
              !it.equals("Completed past visit", ignoreCase = true)
          },
        ).joinToString(" · ")
      if (noteLine.isNotBlank()) {
        Text(
          text = noteLine,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (hasDialablePhone) {
      IconButton(
        onClick = onDialVisitor,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Call,
          contentDescription = "Call ${visit.visitorName}",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }
    } else {
      Spacer(Modifier.width(8.dp))
    }
    StatusPill(
      text = visit.status,
      containerColor = if (isDenied) status.deniedContainer else status.freeContainer,
      contentColor = if (isDenied) status.deniedOnContainer else status.freeOnContainer,
    )
  }
}
