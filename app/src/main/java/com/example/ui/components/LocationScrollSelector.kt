package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ArchPointSeedData
import com.example.data.model.OfficeLocationPreset

fun OfficeLocationPreset.matchesSeating(floor: String, cabin: String): Boolean {
  val cleanCabin = cabin.trim().lowercase()
  val cleanFloor = floor.trim().lowercase()
  if (cleanCabin.isEmpty()) return false
  val sameCabin = cleanCabin == this.cabin.lowercase() || cleanCabin == this.label.lowercase()
  val floorMatches =
    cleanFloor.isEmpty() ||
      cleanFloor == this.floor.lowercase() ||
      this.floor.lowercase().startsWith(cleanFloor) ||
      cleanFloor.startsWith(this.floor.lowercase().substringBefore(" "))
  return sameCabin && floorMatches
}

fun OfficeLocationPreset.matchesLocationText(locationText: String): Boolean {
  val clean = locationText.trim().lowercase()
  if (clean.isEmpty()) return false
  return clean == chipText.lowercase() ||
    clean == combinedLocation.lowercase() ||
    clean == label.lowercase() ||
    clean == cabin.lowercase()
}

/**
 * Horizontal scrollable bar of office locations so Admin, Hosts, and Staff
 * can pick a floor & cabin/seating without typing.
 */
@Composable
fun LocationPresetScrollBar(
  title: String,
  selectedPresetId: String?,
  onSelectPreset: (OfficeLocationPreset) -> Unit,
  onClearSelection: (() -> Unit)? = null,
  presets: List<OfficeLocationPreset> = ArchPointSeedData.locationPresets,
  tagPrefix: String = "location_preset",
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(
      modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_scroll_bar"),
      contentPadding = PaddingValues(horizontal = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (onClearSelection != null) {
        item(key = "clear_location") {
          val selected = selectedPresetId == null
          Surface(
            shape = RoundedCornerShape(12.dp),
            color =
              if (selected) MaterialTheme.colorScheme.secondaryContainer
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border =
              BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color =
                  if (selected) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
              ),
            modifier =
              Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClearSelection)
                .testTag("${tagPrefix}_none"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "No alternate location",
                tint =
                  if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(5.dp))
              Text(
                text = "None",
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      items(
        items = presets,
        key = { it.id },
      ) { preset ->
        val isSelected = preset.id == selectedPresetId
        Surface(
          shape = RoundedCornerShape(12.dp),
          color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
          border =
            BorderStroke(
              width = if (isSelected) 1.5.dp else 1.dp,
              color =
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
            ),
          modifier =
            Modifier
              .heightIn(min = 48.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable { onSelectPreset(preset) }
              .testTag("${tagPrefix}_${preset.id}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.LocationOn,
              contentDescription = null,
              tint =
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Column {
              Text(
                text = preset.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color =
                  if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = preset.floor,
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
          }
        }
      }
    }
  }
}
