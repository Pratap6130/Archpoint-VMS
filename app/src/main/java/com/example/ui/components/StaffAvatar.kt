package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.util.DeskFormatters
import com.example.util.StaffPhotoCodec

@Composable
fun StaffAvatar(
  name: String,
  photoUrl: String,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
  containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
  contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
  textStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
  val bitmap = remember(photoUrl) { StaffPhotoCodec.decodeDataUriToImageBitmap(photoUrl) }

  Box(
    modifier =
      modifier
        .size(size)
        .clip(CircleShape)
        .background(containerColor)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription = "$name photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Text(
        text = DeskFormatters.initials(name),
        style = textStyle,
        color = contentColor,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
