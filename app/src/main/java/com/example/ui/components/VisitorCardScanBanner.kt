package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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

/**
 * Compact card-scan launcher button meant to sit inline beside the Search Returning Visitor field,
 * plus an expandable status strip below only when actively scanning, succeeded, or errored.
 */
@Composable
fun VisitorCardScanAction(
  scanState: CardScanState,
  onScanCardUri: (Uri) -> Unit,
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

  val isScanning = scanState is CardScanState.Scanning
  val previewBitmap: ImageBitmap? =
    when (scanState) {
      CardScanState.Idle -> null
      is CardScanState.Scanning -> scanState.preview
      is CardScanState.Success -> scanState.preview
      is CardScanState.Error -> scanState.preview
    }

  val borderColor =
    when (scanState) {
      is CardScanState.Success -> status.freeText.copy(alpha = 0.65f)
      is CardScanState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
      is CardScanState.Scanning -> MaterialTheme.colorScheme.primary
      CardScanState.Idle -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    }

  Surface(
    modifier =
      modifier
        .height(54.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable(enabled = !isScanning) { showSourceChooser = true }
        .testTag("visitor_card_image_clickable"),
    shape = RoundedCornerShape(12.dp),
    color =
      when (scanState) {
        is CardScanState.Success -> status.freeContainer
        is CardScanState.Error -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
      },
    border = BorderStroke(1.dp, borderColor),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      if (isScanning) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
        )
      } else if (previewBitmap != null) {
        Image(
          bitmap = previewBitmap,
          contentDescription = "Scanned card image",
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
        )
      } else {
        Icon(
          imageVector = Icons.Rounded.DocumentScanner,
          contentDescription = "Scan visitor card",
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(18.dp),
        )
      }
      Text(
        text =
          when (scanState) {
            is CardScanState.Scanning -> "Reading..."
            is CardScanState.Success -> "Rescan"
            is CardScanState.Error -> "Retry"
            CardScanState.Idle -> "Scan card"
          },
        style = MaterialTheme.typography.labelLarge,
        color =
          when (scanState) {
            is CardScanState.Success -> status.freeOnContainer
            is CardScanState.Error -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
          },
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun VisitorCardScanStatusStrip(
  scanState: CardScanState,
  onClearScan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val status = archPointStatusColors()
  AnimatedVisibility(
    visible = scanState !is CardScanState.Idle,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
    modifier = modifier.fillMaxWidth(),
  ) {
    val previewBitmap: ImageBitmap? =
      when (scanState) {
        CardScanState.Idle -> null
        is CardScanState.Scanning -> scanState.preview
        is CardScanState.Success -> scanState.preview
        is CardScanState.Error -> scanState.preview
      }
    val borderColor =
      when (scanState) {
        is CardScanState.Success -> status.freeText.copy(alpha = 0.55f)
        is CardScanState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
      }

    GlassInnerSurface(
      modifier = Modifier.fillMaxWidth().testTag("visitor_card_scanner_banner"),
      shape = RoundedCornerShape(12.dp),
      borderColor = borderColor,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (previewBitmap != null) {
          Image(
            bitmap = previewBitmap,
            contentDescription = "Clicked visitor card image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(52.dp).height(34.dp).clip(RoundedCornerShape(6.dp)),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          when (scanState) {
            is CardScanState.Scanning -> {
              Text(
                text = "Reading visitor card...",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            is CardScanState.Success -> {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = status.freeText,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  text = "Filled from card",
                  style = MaterialTheme.typography.labelLarge,
                  color = status.freeText,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              val summary =
                listOfNotNull(
                  scanState.card.name.takeIf { it.isNotBlank() },
                  scanState.card.company.takeIf { it.isNotBlank() },
                  scanState.card.mobile.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
              if (summary.isNotBlank()) {
                Text(
                  text = summary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
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
                  modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  text = scanState.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            CardScanState.Idle -> Unit
          }
        }
        IconButton(
          onClick = onClearScan,
          modifier = Modifier.size(36.dp).testTag("clear_card_scan_button"),
        ) {
          Icon(Icons.Rounded.Clear, contentDescription = "Clear scanned card", modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
fun VisitorCardScanBanner(
  scanState: CardScanState,
  onScanCardUri: (Uri) -> Unit,
  onClearScan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    VisitorCardScanAction(scanState = scanState, onScanCardUri = onScanCardUri)
    VisitorCardScanStatusStrip(scanState = scanState, onClearScan = onClearScan)
  }
}
