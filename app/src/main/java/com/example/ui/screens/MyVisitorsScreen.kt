package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Visit
import com.example.data.model.VisitStatus
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassInnerSurface
import com.example.ui.components.StatusPill
import com.example.ui.theme.ArchAmberBright
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MeetingBlueBright
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.DeskSnapshot
import com.example.util.DeskFormatters

@Composable
fun MyVisitorsScreen(
  snapshot: DeskSnapshot,
  userEmail: String,
  nowMillis: Long,
  onSendIn: (Visit) -> Unit,
  onWait10Min: (Visit) -> Unit,
  onNotToday: (Visit) -> Unit,
  onMeetingDone: (Visit) -> Unit,
) {
  val status = archPointStatusColors()
  val cleanEmail = userEmail.trim().lowercase()
  val displayName =
    snapshot.currentStaff?.shownAs?.ifBlank { snapshot.currentStaff.fullName } ?: "Host"
  val myCabin = snapshot.currentStaff?.cabin.orEmpty()
  val myRequiredThings = snapshot.currentStaff?.requiredThings.orEmpty()
  val todayStart = remember(nowMillis / 60_000L) { DeskFormatters.startOfTodayMillis(nowMillis) }

  val myVisits =
    remember(snapshot.visits, cleanEmail) {
      snapshot.visits.filter { it.hostEmail.trim().lowercase() == cleanEmail }
    }
  val myWaiting =
    remember(myVisits) {
      myVisits
        .filter { it.parsedStatus() == VisitStatus.WAITING }
        .sortedBy { it.waitStartedAt?.toDate()?.time ?: Long.MAX_VALUE }
    }
  val myInMeeting =
    remember(myVisits) {
      myVisits.filter { it.parsedStatus() == VisitStatus.IN_MEETING }
    }
  val metTodayCount =
    remember(myVisits, todayStart) {
      myVisits.count {
        it.parsedStatus() == VisitStatus.DONE &&
          (it.arrivedAt?.toDate()?.time ?: 0L) >= todayStart
      }
    }
  val longestWaitMinutes =
    myWaiting.firstOrNull()?.let {
      DeskFormatters.minutesBetween(it.waitStartedAt?.toDate()?.time, nowMillis)
    } ?: 0L

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 680.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Greeting & live counts card
      GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = DeskFormatters.initials(displayName),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              fontWeight = FontWeight.Bold,
            )
          }
          Spacer(Modifier.width(12.dp))
          Column {
            Text(
              text = "${DeskFormatters.greetingForHour()}, $displayName",
              style = MaterialTheme.typography.titleLarge,
            )
            val waitingLabel =
              when (myWaiting.size) {
                0 -> "No visitors waiting for you right now."
                1 -> "1 person is waiting to see you."
                else -> "${myWaiting.size} people are waiting to see you."
              }
            Text(
              text = waitingLabel,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (myCabin.isNotBlank() || myRequiredThings.isNotBlank()) {
          GlassInnerSurface(modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              if (myCabin.isNotBlank()) {
                Text(
                  text = "Seating: $myCabin",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              if (myRequiredThings.isNotBlank()) {
                Text(
                  text = "Required things: $myRequiredThings",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            value = metTodayCount.toString(),
            label = "met today",
            accent = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          HostKpiTile(
            value = if (myInMeeting.isEmpty()) "—" else myInMeeting.size.toString(),
            label = "in meetings",
            accent = if (myInMeeting.isNotEmpty()) status.meetingText else MaterialTheme.colorScheme.onSurface,
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

      // NEXT / waiting cards
      if (myWaiting.isNotEmpty()) {
        myWaiting.forEachIndexed { idx, visit ->
          WaitingVisitorActionCard(
            visit = visit,
            isNext = idx == 0,
            nowMillis = nowMillis,
            onSendIn = { onSendIn(visit) },
            onWait10Min = { onWait10Min(visit) },
            onNotToday = { onNotToday(visit) },
          )
        }
      }

      // In-meeting cards with Meeting done CTA
      if (myInMeeting.isNotEmpty()) {
        Text(
          "IN MEETING NOW",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        myInMeeting.forEach { visit ->
          val meetingMin = DeskFormatters.minutesBetween(visit.meetingStartedAt?.toDate()?.time, nowMillis)
          GlassCard(
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
                if (visit.visitorCompany.isNotBlank()) {
                  Text(
                    visit.visitorCompany,
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
            Button(
              onClick = { onMeetingDone(visit) },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("host_meeting_done_${visit.id}"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text("Meeting done", fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }

      if (myWaiting.isEmpty() && myInMeeting.isEmpty()) {
        GlassCard(contentPadding = PaddingValues(24.dp)) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_empty_visitors),
              contentDescription = "No waiting visitors",
              modifier = Modifier.size(150.dp).clip(RoundedCornerShape(16.dp)),
              contentScale = ContentScale.Crop,
            )
            Text("All clear at reception", style = MaterialTheme.typography.titleMedium)
            Text(
              "When reception checks a visitor in for you, this phone will chime and show them here.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      Spacer(Modifier.height(16.dp))
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

@Composable
private fun WaitingVisitorActionCard(
  visit: Visit,
  isNext: Boolean,
  nowMillis: Long,
  onSendIn: () -> Unit,
  onWait10Min: () -> Unit,
  onNotToday: () -> Unit,
) {
  val status = archPointStatusColors()
  val waitMin = DeskFormatters.minutesBetween(visit.waitStartedAt?.toDate()?.time, nowMillis)
  val arrivedTime = DeskFormatters.formatTime(visit.arrivedAt?.toDate())

  GlassCard(
    shape = RoundedCornerShape(20.dp),
    borderBrush =
      if (isNext) {
        Brush.linearGradient(
          listOf(ArchAmberBright.copy(alpha = 0.85f), ArchAmberBright.copy(alpha = 0.25f))
        )
      } else {
        null
      },
  ) {
    if (isNext) {
      StatusPill(
        text = "NEXT",
        containerColor = status.waitingContainer,
        contentColor = status.waitingOnContainer,
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .background(
              if (isNext) status.waitingContainer
              else MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = DeskFormatters.initials(visit.visitorName),
          style = MaterialTheme.typography.titleMedium,
          color =
            if (isNext) status.waitingOnContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
          fontWeight = FontWeight.Bold,
        )
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(visit.visitorName, style = MaterialTheme.typography.titleLarge)
        if (visit.visitorCompany.isNotBlank()) {
          Text(
            visit.visitorCompany,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
      }
    }

    Text(
      text =
        buildString {
          append("Waiting ${waitMin} min · arrived $arrivedTime")
          if (visit.waitNote.isNotBlank()) {
            append(" · ${visit.waitNote}")
          }
        },
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = if (waitMin >= 15L) status.warningAccent else MaterialTheme.colorScheme.onSurface,
    )

    Button(
      onClick = onSendIn,
      colors =
        ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("host_send_in_${visit.id}"),
    ) {
      Text("Send in", style = MaterialTheme.typography.labelLarge)
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = onWait10Min,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("host_wait_10_${visit.id}"),
      ) {
        Text("Wait 10 min")
      }
      OutlinedButton(
        onClick = onNotToday,
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
