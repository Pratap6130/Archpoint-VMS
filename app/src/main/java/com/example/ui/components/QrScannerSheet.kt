package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun VisitorQrScannerDialog(
  onDismiss: () -> Unit,
  onQrScanned: (rawText: String, autoCheckIn: Boolean) -> Unit,
  onPickQrImageUri: (uri: Uri, autoCheckIn: Boolean) -> Unit,
  showAutoCheckInToggle: Boolean = true,
  title: String = "Scan Visitor QR",
  subtitle: String = "Supports ArchPoint passes, vCard / MECARD QRs, or check-in links.",
) {
  val context = LocalContext.current
  var autoCheckIn by remember { mutableStateOf(false) }
  var hasEmittedResult by remember { mutableStateOf(false) }
  var cameraError by remember { mutableStateOf<String?>(null) }

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
      hasCameraPermission = granted
      if (!granted) {
        cameraError = "Camera permission denied. You can still pick a QR image from your device."
      }
    }

  val imagePickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
      if (uri != null) {
        onPickQrImageUri(uri, autoCheckIn)
        onDismiss()
      }
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 20.dp)
          .testTag("qr_scanner_dialog"),
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.QrCodeScanner,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp),
            )
            Column {
              Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(48.dp).testTag("qr_scanner_close_button"),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close QR scanner")
          }
        }

        if (showAutoCheckInToggle) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Auto Check-In on Scan",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text =
                    if (autoCheckIn) "Instantly checks in and notifies host if details are complete"
                    else "Fills the reception form for quick review first",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Spacer(Modifier.width(8.dp))
              Switch(
                checked = autoCheckIn,
                onCheckedChange = { autoCheckIn = it },
                modifier = Modifier.testTag("qr_auto_checkin_switch"),
              )
            }
          }
        }

        Box(
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(1.15f)
              .clip(RoundedCornerShape(18.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
        ) {
          if (hasCameraPermission && cameraError == null) {
            QrLiveCameraPreview(
              onQrDetected = { raw ->
                if (!hasEmittedResult) {
                  hasEmittedResult = true
                  onQrScanned(raw, autoCheckIn)
                  onDismiss()
                }
              },
              onCameraError = { err -> cameraError = err },
            )
            Surface(
              modifier = Modifier.size(190.dp),
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.04f),
              border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
            ) {}
          } else {
            Column(
              modifier = Modifier.fillMaxSize().padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
              )
              Spacer(Modifier.height(10.dp))
              Text(
                text = cameraError ?: "Camera access lets Reception & Kiosk scan visitor QR codes live.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(14.dp))
              Button(
                onClick = {
                  cameraError = null
                  permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("qr_grant_camera_button"),
              ) {
                Text("Enable Camera Scanner")
              }
            }
          }
        }

        OutlinedButton(
          onClick = {
            imagePickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("qr_pick_image_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text("Choose QR Image from Gallery")
        }
      }
    }
  }
}

@Composable
private fun QrLiveCameraPreview(
  onQrDetected: (String) -> Unit,
  onCameraError: (String) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
  val scanner = remember {
    BarcodeScanning.getClient(
      BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
          Barcode.FORMAT_QR_CODE,
          Barcode.FORMAT_AZTEC,
          Barcode.FORMAT_DATA_MATRIX,
        )
        .build()
    )
  }
  val previewView = remember {
    PreviewView(context).apply {
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }

  DisposableEffect(lifecycleOwner) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    var boundProvider: ProcessCameraProvider? = null
    providerFuture.addListener(
      {
        try {
          val provider = providerFuture.get()
          boundProvider = provider
          val preview =
            Preview.Builder().build().also {
              it.setSurfaceProvider(previewView.surfaceProvider)
            }
          val analysis =
            ImageAnalysis.Builder()
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()
              .also { imageAnalysis ->
                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                  analyzeQrFrame(
                    imageProxy = imageProxy,
                    scanner = scanner,
                    onQrDetected = onQrDetected,
                  )
                }
              }
          provider.unbindAll()
          provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
          )
        } catch (e: Exception) {
          Log.w("QrLiveCamera", "Unable to start back camera", e)
          onCameraError("Camera preview unavailable here. You can pick a QR image below.")
        }
      },
      mainExecutor,
    )

    onDispose {
      runCatching { boundProvider?.unbindAll() }
      runCatching { scanner.close() }
      runCatching { analysisExecutor.shutdown() }
    }
  }

  AndroidView(
    factory = { previewView },
    modifier = Modifier.fillMaxSize(),
  )
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeQrFrame(
  imageProxy: ImageProxy,
  scanner: BarcodeScanner,
  onQrDetected: (String) -> Unit,
) {
  val mediaImage = imageProxy.image
  if (mediaImage == null) {
    imageProxy.close()
    return
  }
  val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
  scanner
    .process(inputImage)
    .addOnSuccessListener { barcodes ->
      val raw =
        barcodes.firstNotNullOfOrNull { barcode ->
          barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
        }
      if (raw != null) {
        onQrDetected(raw)
      }
    }
    .addOnCompleteListener {
      imageProxy.close()
    }
}
