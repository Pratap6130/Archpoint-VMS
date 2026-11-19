package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.selfcheckin.VisitorQrCodec
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.DeskSnapshot
import com.example.ui.viewmodel.HostBoardItem
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.VisitorKioskState
import com.example.util.DeskFormatters
import java.io.File
import kotlinx.coroutines.delay

private fun createKioskCardTempUri(context: Context): Uri {
  val dir = File(context.cacheDir, "card_scans").apply { mkdirs() }
  val file = File(dir, "kiosk_card_${System.currentTimeMillis()}.jpg")
  return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VisitorKioskOverlay(
  snapshot: DeskSnapshot,
  hostBoard: List<HostBoardItem>,
  kioskState: VisitorKioskState,
  onMobileChanged: (String) -> Unit,
  onNameChanged: (String) -> Unit,
  onCompanyChanged: (String) -> Unit,
  onEmailChanged: (String) -> Unit,
  onDobChanged: (String) -> Unit,
  onRemarkChanged: (String) -> Unit,
  onHostSelected: (String) -> Unit,
  onReasonSelected: (String) -> Unit,
  onClearReturningMatch: () -> Unit,
  onScanCardUri: (Uri) -> Unit,
  onQrScanned: (String) -> Unit,
  onPickQrImageUri: (Uri) -> Unit,
  onSubmit: () -> Unit,
  onNextVisitor: () -> Unit,
  onExitKiosk: () -> Unit,
) {
  val context = LocalContext.current
  val status = archPointStatusColors()
  var showExitDialog by remember { mutableStateOf(false) }
  var showQrScanner by remember { mutableStateOf(false) }
  var showCardSourceDialog by remember { mutableStateOf(false) }
  var showDobPicker by remember { mutableStateOf(false) }
  var hostDropdownExpanded by remember { mutableStateOf(false) }
  var pendingCardUri by remember { mutableStateOf<Uri?>(null) }

  BackHandler { showExitDialog = true }

  val cardGalleryLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) onScanCardUri(uri)
    }
  val cardCameraLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
      val captured = pendingCardUri
      pendingCardUri = null
      if (ok && captured != null) onScanCardUri(captured)
    }

  if (showExitDialog) {
    AlertDialog(
      onDismissRequest = { showExitDialog = false },
      modifier = Modifier.testTag("kiosk_exit_confirm_dialog"),
      title = { Text("Exit Visitor Kiosk?") },
      text = { Text("Return to the Reception staff desk controls.") },
      confirmButton = {
        Button(
          onClick = {
            showExitDialog = false
            onExitKiosk()
          },
          modifier = Modifier.testTag("kiosk_exit_confirm_button"),
        ) {
          Text("Exit to Desk")
        }
      },
      dismissButton = {
        TextButton(onClick = { showExitDialog = false }) { Text("Stay in Kiosk") }
      },
    )
  }

  if (showCardSourceDialog) {
    AlertDialog(
      onDismissRequest = { showCardSourceDialog = false },
      title = { Text("Scan your visiting card") },
      text = { Text("Capture a photo or choose a card image to auto-fill your visitor form.") },
      confirmButton = {
        TextButton(
          onClick = {
            showCardSourceDialog = false
            val uri = createKioskCardTempUri(context)
            pendingCardUri = uri
            cardCameraLauncher.launch(uri)
          }
        ) {
          Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Camera")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showCardSourceDialog = false
            cardGalleryLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          }
        ) {
          Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Gallery")
        }
      },
    )
  }

  if (showQrScanner) {
    VisitorQrScannerDialog(
      onDismiss = { showQrScanner = false },
      onQrScanned = { raw, _ -> onQrScanned(raw) },
      onPickQrImageUri = { uri, _ -> onPickQrImageUri(uri) },
      showAutoCheckInToggle = false,
      title = "Scan Your QR Pass",
      subtitle = "Hold your vCard or ArchPoint Visitor QR in front of the camera.",
    )
  }

  if (showDobPicker) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDobPicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let { millis ->
              onDobChanged(DeskFormatters.formatDisplayDateField(millis))
            }
            showDobPicker = false
          }
        ) { Text("OK") }
      },
      dismissButton = {
        TextButton(onClick = { showDobPicker = false }) { Text("Cancel") }
      },
    ) {
      DatePicker(state = pickerState)
    }
  }

  Surface(modifier = Modifier.fillMaxSize().testTag("visitor_kiosk_overlay")) {
    GlassBackdrop(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Kiosk Header Bar
        Row(
          modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color.White,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.size(40.dp),
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_archpoint_mark),
                contentDescription = "ArchPoint Logo",
                modifier = Modifier.fillMaxSize().padding(3.dp),
                contentScale = ContentScale.Fit,
              )
            }
            Spacer(Modifier.width(10.dp))
            Column {
              Text(
                text = snapshot.office?.name?.ifBlank { "ArchPoint Studio" } ?: "ArchPoint Studio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "Visitor Self Check-In",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          OutlinedButton(
            onClick = { showExitDialog = true },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("kiosk_exit_button"),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Exit")
          }
        }

        Spacer(Modifier.height(12.dp))

        if (kioskState.completedVisitorName.isNotBlank()) {
          LaunchedEffect(kioskState.completedVisitorName) {
            delay(7500L)
            onNextVisitor()
          }

          GlassCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
            contentPadding = PaddingValues(22.dp),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Checked in",
                tint = status.freeText,
                modifier = Modifier.size(56.dp),
              )
              Text(
                text = "Welcome, ${kioskState.completedVisitorName}!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
              )
              Text(
                text = "${kioskState.completedHostName} has been notified of your arrival.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
              if (kioskState.completedHostCabin.isNotBlank()) {
                GlassInnerSurface(
                  borderColor = status.freeText.copy(alpha = 0.4f),
                  containerColor = status.freeContainer,
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      Icons.Rounded.LocationOn,
                      contentDescription = null,
                      tint = status.freeText,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = "Host seating: ${kioskState.completedHostCabin}",
                      style = MaterialTheme.typography.titleSmall,
                      color = status.freeOnContainer,
                      fontWeight = FontWeight.SemiBold,
                    )
                  }
                }
              }

              val passQr =
                remember(kioskState.completedPassQrText) {
                  VisitorQrCodec.renderQrBitmap(kioskState.completedPassQrText, 440)?.asImageBitmap()
                }
              if (passQr != null) {
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = Color.White,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                  Image(
                    bitmap = passQr,
                    contentDescription = "Digital Visitor QR Pass",
                    modifier = Modifier.size(170.dp).padding(10.dp),
                  )
                }
                Text(
                  text = "Save or scan this QR pass on your next visit for 1-second check-in",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
              }

              Button(
                onClick = onNextVisitor,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("kiosk_next_visitor_button"),
              ) {
                Text("Next Visitor Check-In", style = MaterialTheme.typography.labelLarge)
              }
            }
          }
        } else {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .widthIn(max = 640.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            GlassCard(contentPadding = PaddingValues(16.dp)) {
              Text(
                text = "Welcome! Please enter your details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )

              // Quick scan strip (Visiting card OR QR pass)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                  onClick = { showCardSourceDialog = true },
                  enabled = !kioskState.isScanningCard,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("kiosk_scan_card_button"),
                ) {
                  if (kioskState.isScanningCard) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Scanning...")
                  } else {
                    Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan Card", maxLines = 1)
                  }
                }
                OutlinedButton(
                  onClick = { showQrScanner = true },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("kiosk_scan_qr_button"),
                ) {
                  Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Scan QR", maxLines = 1)
                }
              }

              OutlinedTextField(
                value = kioskState.mobile,
                onValueChange = onMobileChanged,
                label = { Text("Mobile Number (10 digits)*") },
                placeholder = { Text("9829012345") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier.fillMaxWidth().testTag("kiosk_mobile_input"),
              )

              kioskState.matchedProfile?.let { match ->
                GlassInnerSurface(
                  containerColor = status.freeContainer,
                  borderColor = status.freeText.copy(alpha = 0.4f),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = "Welcome back, ${match.name}! Details auto-filled.",
                      style = MaterialTheme.typography.labelMedium,
                      color = status.freeOnContainer,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.weight(1f),
                    )
                    TextButton(
                      onClick = onClearReturningMatch,
                      modifier = Modifier.heightIn(min = 40.dp).testTag("kiosk_not_you_button"),
                    ) {
                      Text("Not you?", color = status.freeText)
                    }
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedTextField(
                  value = kioskState.name,
                  onValueChange = onNameChanged,
                  label = { Text("Full Name*") },
                  placeholder = { Text("Your good name") },
                  keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("kiosk_name_input"),
                )
                OutlinedTextField(
                  value = kioskState.company,
                  onValueChange = onCompanyChanged,
                  label = { Text("Organization") },
                  placeholder = { Text("Company (opt.)") },
                  keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("kiosk_company_input"),
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedTextField(
                  value = kioskState.email,
                  onValueChange = onEmailChanged,
                  label = { Text("Email (opt.)") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                  singleLine = true,
                  modifier = Modifier.weight(1.1f).testTag("kiosk_email_input"),
                )
                OutlinedTextField(
                  value = kioskState.dob,
                  onValueChange = onDobChanged,
                  label = { Text("DOB (opt.)") },
                  placeholder = { Text("mm/dd/yyyy") },
                  singleLine = true,
                  trailingIcon = {
                    IconButton(onClick = { showDobPicker = true }, modifier = Modifier.size(48.dp)) {
                      Icon(Icons.Rounded.CalendarToday, contentDescription = "Pick birthday", modifier = Modifier.size(16.dp))
                    }
                  },
                  modifier = Modifier.weight(0.9f).testTag("kiosk_dob_input"),
                )
              }

              val selectedHostItem = hostBoard.firstOrNull { it.staff.id == kioskState.selectedHostId }
              ExposedDropdownMenuBox(
                expanded = hostDropdownExpanded,
                onExpandedChange = { if (hostBoard.isNotEmpty()) hostDropdownExpanded = !hostDropdownExpanded },
              ) {
                val hostLabel =
                  selectedHostItem?.staff?.let { it.shownAs.ifBlank { it.fullName } } ?: "Select person to meet"
                val hostTone = selectedHostItem?.let { MainViewModel.resolveHostAvailabilityVisual(it).second }
                OutlinedTextField(
                  value = hostLabel,
                  onValueChange = {},
                  readOnly = true,
                  label = { Text("Person to Meet*") },
                  leadingIcon =
                    hostTone?.let { tone ->
                      {
                        Box(
                          modifier = Modifier.size(10.dp).clip(CircleShape).background(status.dotColor(tone))
                        )
                      }
                    },
                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostDropdownExpanded) },
                  singleLine = true,
                  colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                  modifier =
                    Modifier
                      .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                      .fillMaxWidth()
                      .testTag("kiosk_host_dropdown"),
                )
                ExposedDropdownMenu(
                  expanded = hostDropdownExpanded,
                  onDismissRequest = { hostDropdownExpanded = false },
                ) {
                  hostBoard.forEach { item ->
                    val (availLabel, availTone) = MainViewModel.resolveHostAvailabilityVisual(item)
                    val title = item.staff.shownAs.ifBlank { item.staff.fullName }
                    DropdownMenuItem(
                      leadingIcon = {
                        StaffAvatar(
                          name = title,
                          photoUrl = item.staff.photoUrl,
                          size = 28.dp,
                          textStyle = MaterialTheme.typography.labelSmall,
                        )
                      },
                      text = {
                        Column {
                          Text(title, fontWeight = FontWeight.SemiBold)
                          val sub = listOfNotNull(
                            item.staff.designation.takeIf { it.isNotBlank() },
                            item.staff.formattedSeating().takeIf { it.isNotBlank() },
                          ).joinToString(" · ")
                          if (sub.isNotBlank()) {
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                          }
                        }
                      },
                      trailingIcon = { StatusDotLabel(tone = availTone, text = availLabel) },
                      onClick = {
                        onHostSelected(item.staff.id)
                        hostDropdownExpanded = false
                      },
                    )
                  }
                }
              }

              val shownReasons = remember(snapshot.reasons) { snapshot.reasons.filter { it.shown } }
              if (shownReasons.isNotEmpty()) {
                Text(
                  text = "Purpose of Visit*",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  shownReasons.forEach { reason ->
                    FilterChip(
                      selected = reason.label.equals(kioskState.selectedReason, ignoreCase = true),
                      onClick = { onReasonSelected(reason.label) },
                      label = { Text(reason.label) },
                      modifier = Modifier.heightIn(min = 40.dp).testTag("kiosk_reason_${reason.label}"),
                    )
                  }
                }
              }

              OutlinedTextField(
                value = kioskState.remark,
                onValueChange = onRemarkChanged,
                label = { Text("Message / Remark (opt.)") },
                placeholder = { Text("e.g. Scheduled meeting or sample delivery") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("kiosk_remark_input"),
              )

              Button(
                onClick = onSubmit,
                enabled = !kioskState.isSubmitting && hostBoard.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("kiosk_submit_button"),
              ) {
                if (kioskState.isSubmitting) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                } else {
                  Text("Complete Check-In", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
              }
            }
            Spacer(Modifier.height(16.dp))
          }
        }
      }
    }
  }
}
