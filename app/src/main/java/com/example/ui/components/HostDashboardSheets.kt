package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.data.model.Visit
import com.example.ui.theme.archPointStatusColors

internal sealed interface HostActionSheetState {
  data class QuickReply(val visit: Visit, val isInMeeting: Boolean) : HostActionSheetState
  data class DeclineReason(val visit: Visit) : HostActionSheetState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HostQuickReplySheet(
  visit: Visit,
  isInMeeting: Boolean,
  onDismiss: () -> Unit,
  onSendReply: (String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var customNote by remember(visit.id, isInMeeting) {
    mutableStateOf(
      if (visit.waitNote.isNotBlank() && !visit.waitNote.equals("Send in", ignoreCase = true)) {
        visit.waitNote
      } else {
        ""
      }
    )
  }
  val presets =
    remember(isInMeeting) {
      if (isInMeeting) {
        listOf(
          "Need 5 more min",
          "Need 10 more min",
          "Send tea / coffee",
          "Send documents / file",
          "Wrapping up now",
        )
      } else {
        listOf(
          "Wait 5 min",
          "asked to wait 10 min",
          "Wait 15 min",
          "Wait 30 min",
          "Coming out to reception",
          "Offer water, 5 min",
        )
      }
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (isInMeeting) "Message Reception (In meeting)" else "Quick Reply to Reception",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "For ${visit.visitorName} (${visit.reason})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        presets.forEach { preset ->
          val displayLabel = if (preset == "asked to wait 10 min") "Wait 10 min" else preset
          FilterChip(
            selected = customNote.equals(preset, ignoreCase = true),
            onClick = {
              onSendReply(preset)
              onDismiss()
            },
            label = { Text(displayLabel) },
            modifier = Modifier.testTag("host_reply_preset_${preset.replace(" ", "_").lowercase()}"),
          )
        }
      }

      if (!isInMeeting) {
        LocationPresetScrollBar(
          title = "Or tell reception which location to prepare (scroll & tap)",
          selectedPresetId = null,
          onSelectPreset = { preset ->
            onSendReply("Wait 5 min · Meet at ${preset.chipText}")
            onDismiss()
          },
          tagPrefix = "host_reply_loc",
        )
      }

      OutlinedTextField(
        value = customNote,
        onValueChange = { if (it.length <= 120) customNote = it },
        label = { Text("Custom note for reception") },
        placeholder = { Text("Optional extra note...") },
        singleLine = true,
        supportingText = { Text("${customNote.length}/120") },
        modifier = Modifier.fillMaxWidth().testTag("host_custom_reply_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp),
        ) {
          Text("Cancel")
        }
        Button(
          onClick = {
            val clean = customNote.trim()
            if (clean.isNotEmpty()) {
              onSendReply(clean)
              onDismiss()
            }
          },
          enabled = customNote.trim().isNotEmpty(),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1.3f).height(48.dp).testTag("host_send_custom_reply_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Send reply")
        }
      }
      Spacer(Modifier.height(6.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HostDeclineReasonSheet(
  visit: Visit,
  onDismiss: () -> Unit,
  onConfirmDecline: (String) -> Unit,
) {
  val status = archPointStatusColors()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var reasonNote by remember(visit.id) { mutableStateOf("can't meet today") }
  val presets =
    remember {
      listOf(
        "can't meet today",
        "Reschedule tomorrow",
        "In external meeting",
        "Out of office today",
        "Ask to call / email instead",
      )
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Rounded.EventBusy,
          contentDescription = null,
          tint = status.deniedText,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = "Can't meet ${visit.visitorName}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = "Choose a message so Reception knows what to tell the visitor:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        presets.forEach { preset ->
          FilterChip(
            selected = reasonNote.equals(preset, ignoreCase = true),
            onClick = { reasonNote = preset },
            label = { Text(preset) },
          )
        }
      }

      OutlinedTextField(
        value = reasonNote,
        onValueChange = { if (it.length <= 120) reasonNote = it },
        label = { Text("Note for reception") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("host_decline_reason_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp),
        ) {
          Text("Back")
        }
        Button(
          onClick = {
            onConfirmDecline(reasonNote.trim().ifBlank { "can't meet today" })
            onDismiss()
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1.3f).height(48.dp).testTag("host_confirm_decline_button"),
        ) {
          Text("Confirm Not Today")
        }
      }
      Spacer(Modifier.height(6.dp))
    }
  }
}
