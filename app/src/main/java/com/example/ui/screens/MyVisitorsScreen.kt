package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.model.HostAvailability
import com.example.data.model.Visit
import com.example.ui.components.HostDashboard
import com.example.ui.viewmodel.DeskSnapshot

@Composable
fun MyVisitorsScreen(
  snapshot: DeskSnapshot,
  userEmail: String,
  nowMillis: Long,
  inFlightVisitIds: Set<String> = emptySet(),
  onUpdateAvailability: (HostAvailability) -> Unit,
  onSendIn: (Visit, String) -> Unit,
  onSendWaitNote: (Visit, String) -> Unit,
  onNotToday: (Visit, String) -> Unit,
  onMeetingDone: (Visit) -> Unit,
) {
  HostDashboard(
    currentStaff = snapshot.currentStaff,
    visits = snapshot.visits,
    userEmail = userEmail,
    nowMillis = nowMillis,
    inFlightVisitIds = inFlightVisitIds,
    onUpdateAvailability = onUpdateAvailability,
    onSendIn = onSendIn,
    onSendWaitNote = onSendWaitNote,
    onNotToday = onNotToday,
    onMeetingDone = onMeetingDone,
  )
}
