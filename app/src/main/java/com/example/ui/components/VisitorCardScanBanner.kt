package com.example.ui.components

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.CardScanState
import java.io.File

private fun createTempCardScanUri(context: Context): Uri {
  val dir = File(context.cacheDir, "card_scans").apply { mkdirs() }
  val tempFile = File(dir, "card_${System.currentTimeMillis()}.jpg")
  return FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    tempFile,
  )
}

@Composable
fun VisitorCardScanBanner(
  scanState: CardScanState,
  onScanCardUri: (Uri) -> Unit,
  onClearScan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val status = archPointStatusColors()
  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
  var showSourceChooser by remember { mutableStateOf(false) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
      if (uri != null) {
        onScanCardUri(uri)
      }
    }

  val cameraLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicture(),
    ) { success ->
      val capturedUri = pendingCameraUri
      pendingCameraUri = null
      if (success && capturedUri != null) {
        onScanCardUri(capturedUri)
      }
    }

  val launchCamera: () -> Unit = {
    val uri = createTempCardScanUri(context)
    pendingCameraUri = uri
    cameraLauncher.launch(uri)
  }

  val launchGallery: () -> Unit = {
    photoPickerLauncher.launch(
      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )
  }

  if (showSourceChooser) {
    AlertDialog(
      onDismissRequest = { showSourceChooser = false },
      title = { Text("Scan visitor card") },
      text = {
        Text("Take a clear photo of the visiting card or choose a card image to auto-fill visitor details.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            showSourceChooser = false
            launchCamera()
          },
          modifier = Modifier.testTag("card_scan_dialog_camera"),
        ) {
          Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Camera")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showSourceChooser = false
            launchGallery()
          },
          modifier = Modifier.testTag("card_scan_dialog_gallery"),
        ) {
          Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Choose Image")
        }
      },
    )
  }

  val previewBitmap: ImageBitmap? =
    when (scanState) {
      CardScanState.Idle -> null
      is CardScanState.Scanning -> scanState.preview
      is CardScanState.Success -> scanState.preview
      is CardScanState.Error -> scanState.preview
    }

  val isScanning = scanState is CardScanState.Scanning

  val borderColor =
    when (scanState) {
      is CardScanState.Success -> status.freeText.copy(alpha = 0.55f)
      is CardScanState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
      is CardScanState.Scanning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
      CardScanState.Idle -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

  GlassInnerSurface(
    modifier = modifier.fillMaxWidth().testTag("visitor_card_scanner_banner"),
    shape = RoundedCornerShape(14.dp),
    borderColor = borderColor,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Clickable visitor card image thumbnail
        Surface(
          modifier =
            Modifier
              .width(92.dp)
              .height(62.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable(enabled = !isScanning) { showSourceChooser = true }
              .testTag("visitor_card_image_clickable"),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, borderColor),
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .background(
                  Brush.linearGradient(
                    listOf(
                      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                      MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    )
                  )
                ),
            contentAlignment = Alignment.Center,
          ) {
            if (previewBitmap != null) {
              Image(
                bitmap = previewBitmap,
                contentDescription = "Clicked visitor card image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.DocumentScanner,
                  contentDescription = "Tap visitor card image to scan",
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                  text = "Card photo",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
            if (isScanning) {
              Box(
                modifier =
                  Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  strokeWidth = 2.5.dp,
                )
              }
            }
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          when (scanState) {
            CardScanState.Idle -> {
              Text(
                text = "Tap card image to auto-fill",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Snap or pick a visitor's business card to fill Name, Org, Phone & Email.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }

            is CardScanState.Scanning -> {
              Text(
                text = "Reading visitor card...",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Extracting contact details from card image",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            is CardScanState.Success -> {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = status.freeText,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text = "Details filled from card",
                  style = MaterialTheme.typography.titleSmall,
                  color = status.freeText,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              val summary =
                listOfNotNull(
                  scanState.card.name.takeIf { it.isNotBlank() },
                  scanState.card.company.takeIf { it.isNotBlank() },
                  scanState.card.mobile.takeIf { it.isNotBlank() },
                  scanState.card.email.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
              if (summary.isNotBlank()) {
                Text(
                  text = summary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            is CardScanState.Error -> {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text = "Could not read card",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.error,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              Text(
                text = scanState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        if (scanState !is CardScanState.Idle) {
          IconButton(
            onClick = onClearScan,
            modifier = Modifier.size(40.dp).testTag("clear_card_scan_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Clear,
              contentDescription = "Clear scanned card",
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = launchCamera,
          enabled = !isScanning,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
          modifier = Modifier.weight(1f).height(42.dp).testTag("scan_card_camera_button"),
        ) {
          Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Snap card", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
          onClick = launchGallery,
          enabled = !isScanning,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
          modifier = Modifier.weight(1f).height(42.dp).testTag("scan_card_gallery_button"),
        ) {
          Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Choose image", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}
