package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TabletMac
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.selfcheckin.VisitorQrCodec
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.SelfCheckInHubState
import com.example.ui.viewmodel.SelfCheckInSubmissionRecord
import com.example.util.DeskFormatters
import java.util.Date

private enum class SelfCheckInHubTab {
  WIFI_QR,
  KIOSK,
  INBOX,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfCheckInHubSheet(
  state: SelfCheckInHubState,
  onDismiss: () -> Unit,
  onToggleServer: () -> Unit,
  onSetAutoCheckIn: (Boolean) -> Unit,
  onLaunchKiosk: () -> Unit,
  onApplySubmission: (SelfCheckInSubmissionRecord) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var activeTab by remember { mutableStateOf(SelfCheckInHubTab.WIFI_QR) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Visitor Self Check-In",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Visitors fill details from their phone QR or tablet kiosk — data flows live into ArchPoint.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      GlassSegmentedControl(
        options =
          listOf(
            SelfCheckInHubTab.WIFI_QR to "Wi-Fi QR Form",
            SelfCheckInHubTab.KIOSK to "Tablet Kiosk",
            SelfCheckInHubTab.INBOX to
              if (state.recentSubmissions.isNotEmpty()) "Inbox (${state.recentSubmissions.size})"
              else "Inbox",
          ),
        selected = activeTab,
        onSelect = { activeTab = it },
        tagPrefix = "self_checkin_tab",
      )

      when (activeTab) {
        SelfCheckInHubTab.WIFI_QR ->
          WifiQrServerTabContent(
            state = state,
            onToggleServer = onToggleServer,
            onSetAutoCheckIn = onSetAutoCheckIn,
          )

        SelfCheckInHubTab.KIOSK ->
          KioskLauncherTabContent(
            onLaunchKiosk = {
              onDismiss()
              onLaunchKiosk()
            },
          )

        SelfCheckInHubTab.INBOX ->
          SelfSubmissionsInboxContent(
            submissions = state.recentSubmissions,
            onApplySubmission = { record ->
              onApplySubmission(record)
              onDismiss()
            },
          )
      }
    }
  }
}

@Composable
private fun WifiQrServerTabContent(
  state: SelfCheckInHubState,
  onToggleServer: () -> Unit,
  onSetAutoCheckIn: (Boolean) -> Unit,
) {
  val context = LocalContext.current
  @Suppress("DEPRECATION")
  val clipboardManager = LocalClipboardManager.current
  val status = archPointStatusColors()
  var copiedToast by remember { mutableStateOf(false) }

  GlassInnerSurface(
    modifier = Modifier.fillMaxWidth(),
    borderColor =
      if (state.isServerRunning) status.freeText.copy(alpha = 0.45f)
      else MaterialTheme.colorScheme.outlineVariant,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier =
              Modifier.size(10.dp)
                .clip(CircleShape)
                .background(if (state.isServerRunning) status.freeText else MaterialTheme.colorScheme.outline)
          )
          Text(
            text = if (state.isServerRunning) "QR Web Form Active" else "QR Web Form Stopped",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }

        Button(
          onClick = onToggleServer,
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (state.isServerRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            ),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("toggle_self_checkin_server_button"),
        ) {
          Icon(
            imageVector = if (state.isServerRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(if (state.isServerRunning) "Stop" else "Start QR Form")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Auto check-in on submit",
            style = MaterialTheme.typography.labelLarge,
          )
          Text(
            text =
              if (state.autoCheckInOnSubmit)
                "Automatically writes to Queue & notifies the chosen Host"
              else "Pre-fills Reception form so desk staff tap Check in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
          checked = state.autoCheckInOnSubmit,
          onCheckedChange = onSetAutoCheckIn,
          modifier = Modifier.testTag("auto_checkin_self_submit_switch"),
        )
      }

      state.serverError?.let { err ->
        Text(
          text = err,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (state.isServerRunning && state.serverUrl.isNotBlank()) {
        val qrBitmap =
          remember(state.serverUrl) {
            VisitorQrCodec.renderQrBitmap(state.serverUrl, sizePx = 540)?.asImageBitmap()
          }

        if (qrBitmap != null) {
          Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Image(
              bitmap = qrBitmap,
              contentDescription = "Visitor Self Check-In QR Code",
              modifier = Modifier.size(210.dp).padding(12.dp),
            )
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Wifi,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text =
                if (state.isLanAvailable) "Scan with phone on the same Wi-Fi:"
                else "Local preview address (connect Wi-Fi for other phones):",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = state.serverUrl,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
            fontWeight = FontWeight.SemiBold,
          )
          if (state.localTestUrl.isNotBlank() && state.localTestUrl != state.serverUrl) {
            Text(
              text = "On-device test URL: ${state.localTestUrl}",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(state.serverUrl))
              copiedToast = true
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("copy_qr_link_button"),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (copiedToast) "Copied!" else "Copy Link")
          }
          Button(
            onClick = {
              val target = state.localTestUrl.ifBlank { state.serverUrl }
              runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
              }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_qr_form_browser_button"),
          ) {
            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Test Form")
          }
        }
      }
    }
  }
}

@Composable
private fun KioskLauncherTabContent(
  onLaunchKiosk: () -> Unit,
) {
  GlassInnerSurface(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.TabletMac,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Visitor Desk Kiosk Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Hand this device or tablet to arriving visitors at reception.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
        text =
          "• Hides staff queue, admin, PR, and visitor logs behind exit confirmation.\n" +
            "• Visitors can scan their visiting card, scan a QR pass, or type their details.\n" +
            "• Submitting checks them into Firestore immediately, alerts the selected Host, and shows a Digital QR Pass with floor/cabin directions.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Button(
        onClick = onLaunchKiosk,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("launch_kiosk_mode_button"),
      ) {
        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Open Visitor Kiosk Screen", style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
private fun SelfSubmissionsInboxContent(
  submissions: List<SelfCheckInSubmissionRecord>,
  onApplySubmission: (SelfCheckInSubmissionRecord) -> Unit,
) {
  val status = archPointStatusColors()
  if (submissions.isEmpty()) {
    GlassInnerSurface(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = "No QR / Self Check-In submissions received in this session yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
      )
    }
    return
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    submissions.forEach { record ->
      GlassInnerSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor =
          if (record.autoCheckedIn) status.freeText.copy(alpha = 0.4f)
          else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = record.payload.name.ifBlank { record.payload.mobile.ifBlank { "Visitor" } },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              StatusPill(
                text = record.statusLabel,
                containerColor =
                  if (record.autoCheckedIn) status.freeContainer
                  else MaterialTheme.colorScheme.primaryContainer,
                contentColor =
                  if (record.autoCheckedIn) status.freeOnContainer
                  else MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            val sub =
              listOfNotNull(
                record.payload.company.takeIf { it.isNotBlank() },
                record.payload.mobile.takeIf { it.isNotBlank() },
                record.assignedHostName.takeIf { it.isNotBlank() }?.let { "→ $it" },
                DeskFormatters.formatTime(Date(record.receivedAtMillis)),
              ).joinToString(" · ")
            Text(
              text = sub,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.width(8.dp))
          TextButton(
            onClick = { onApplySubmission(record) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("apply_submission_${record.id}"),
          ) {
            Text("Fill Form")
          }
        }
      }
    }
  }
}
