package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.VisitorProfile
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.ReceptionFormState
import com.example.util.DeskFormatters

private fun isVisitorSelectedInForm(
  profile: VisitorProfile,
  form: ReceptionFormState,
): Boolean {
  val matchedId = form.matchedProfile?.id.orEmpty()
  if (matchedId.isNotBlank() && profile.id.isNotBlank() && matchedId == profile.id) {
    return true
  }
  val profilePhone = profile.mobile.filter { it.isDigit() }.takeUnless { it == "919999999999" }.orEmpty()
  val formPhone = form.mobile.filter { it.isDigit() }
  val nameMatches = form.name.trim().equals(profile.name.trim(), ignoreCase = true) && profile.name.isNotBlank()
  if (!nameMatches) return false
  if (profilePhone.isNotBlank() && formPhone == profilePhone) return true
  if (profile.email.isNotBlank() && form.email.trim().equals(profile.email.trim(), ignoreCase = true)) return true
  return false
}

private fun recentVisitorTagKey(profile: VisitorProfile): String =
  profile.id
    .ifBlank { profile.mobile.filter { it.isDigit() } }
    .ifBlank { profile.name.lowercase().replace(Regex("[^a-z0-9]+"), "_") }

@Composable
fun RecentVisitorsStripCard(
  visitors: List<VisitorProfile>,
  form: ReceptionFormState,
  onSelectVisitor: (VisitorProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (visitors.isEmpty()) return

  GlassCard(
    modifier = modifier.fillMaxWidth().testTag("recent_visitors_strip_section"),
    contentPadding = PaddingValues(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Icon(
          imageVector = Icons.Rounded.History,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = "RECENT VISITORS",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = "Tap card to fill",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      visitors.forEach { profile ->
        RecentVisitorCardItem(
          profile = profile,
          selected = isVisitorSelectedInForm(profile, form),
          onClick = { onSelectVisitor(profile) },
          modifier = Modifier.width(236.dp),
          tagPrefix = "recent_visitor_strip_card",
        )
      }
    }
  }
}

@Composable
fun RecentVisitorsCard(
  visitors: List<VisitorProfile>,
  form: ReceptionFormState,
  onSelectVisitor: (VisitorProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  var filterQuery by remember { mutableStateOf("") }
  var expanded by remember { mutableStateOf(false) }

  val filteredVisitors =
    remember(visitors, filterQuery) {
      val q = filterQuery.trim().lowercase()
      if (q.isEmpty()) {
        visitors
      } else {
        visitors.filter { p ->
          p.name.lowercase().contains(q) ||
            p.company.lowercase().contains(q) ||
            p.mobile.contains(q) ||
            p.email.lowercase().contains(q)
        }
      }
    }

  val visibleVisitors =
    remember(filteredVisitors, expanded, filterQuery) {
      if (expanded || filterQuery.isNotBlank()) filteredVisitors else filteredVisitors.take(4)
    }

  GlassCard(
    modifier = modifier.fillMaxWidth().testTag("recent_visitors_section"),
    contentPadding = PaddingValues(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Icon(
          imageVector = Icons.Rounded.History,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = "RECENT VISITORS",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Bold,
        )
        if (visitors.isNotEmpty()) {
          Spacer(Modifier.width(8.dp))
          StatusPill(
            text = "${visitors.size}",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Text(
        text = "Tap to auto-fill",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
      )
    }

    if (visitors.size > 4) {
      OutlinedTextField(
        value = filterQuery,
        onValueChange = { filterQuery = it },
        placeholder = { Text("Filter recent visitors...") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Filter recent visitors",
            modifier = Modifier.size(18.dp),
          )
        },
        trailingIcon = {
          if (filterQuery.isNotEmpty()) {
            IconButton(onClick = { filterQuery = "" }) {
              Icon(
                imageVector = Icons.Rounded.Clear,
                contentDescription = "Clear filter",
                modifier = Modifier.size(18.dp),
              )
            }
          }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("recent_visitors_filter_input"),
      )
    }

    if (visitors.isEmpty()) {
      Text(
        text = "No recent visitors yet. Saved visitors will appear here for one-tap check-in.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
      )
    } else if (visibleVisitors.isEmpty()) {
      Text(
        text = "No recent visitors match \"${filterQuery.trim()}\".",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        visibleVisitors.forEach { profile ->
          RecentVisitorCardItem(
            profile = profile,
            selected = isVisitorSelectedInForm(profile, form),
            onClick = { onSelectVisitor(profile) },
            modifier = Modifier.fillMaxWidth(),
            tagPrefix = "recent_visitor_card",
          )
        }
      }

      if (filterQuery.isBlank() && filteredVisitors.size > 4) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.heightIn(min = 48.dp).testTag("recent_visitors_toggle_expand"),
          ) {
            Icon(
              imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = if (expanded) "Show fewer" else "Show all ${filteredVisitors.size} recent visitors",
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RecentVisitorCardItem(
  profile: VisitorProfile,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tagPrefix: String,
) {
  val status = archPointStatusColors()
  val bgColor by
    animateColorAsState(
      targetValue =
        if (selected) {
          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
      label = "recentVisitorCardBg",
    )
  val borderColor =
    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

  val usablePhone =
    profile.mobile.filter { it.isDigit() }.takeIf { it.isNotBlank() && it != "919999999999" }.orEmpty()
  val orgLabel = profile.company.trim().ifBlank { "Walk-in / Independent" }
  val phoneLabel = usablePhone.ifBlank { "No phone saved" }
  val emailLabel = profile.email.trim().ifBlank { "No email saved" }

  Surface(
    modifier =
      modifier
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag("${tagPrefix}_${recentVisitorTagKey(profile)}"),
    shape = RoundedCornerShape(14.dp),
    color = bgColor,
    border = BorderStroke(width = if (selected) 1.5.dp else 1.dp, color = borderColor),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
              ),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = DeskFormatters.initials(profile.name),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color =
              if (selected) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = profile.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = DeskFormatters.ordinalVisit(profile.visitCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (selected) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = status.freeContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Selected visitor",
                tint = status.freeText,
                modifier = Modifier.size(14.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = "Filled",
                style = MaterialTheme.typography.labelSmall,
                color = status.freeOnContainer,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VisitorDetailRow(
          icon = Icons.Rounded.Business,
          value = orgLabel,
          isPlaceholder = profile.company.isBlank(),
        )
        VisitorDetailRow(
          icon = Icons.Rounded.Phone,
          value = phoneLabel,
          isPlaceholder = usablePhone.isBlank(),
          useMonospace = usablePhone.isNotBlank(),
        )
        VisitorDetailRow(
          icon = Icons.Rounded.Email,
          value = emailLabel,
          isPlaceholder = profile.email.isBlank(),
        )
      }
    }
  }
}

@Composable
private fun VisitorDetailRow(
  icon: ImageVector,
  value: String,
  isPlaceholder: Boolean,
  useMonospace: Boolean = false,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint =
        if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(14.dp),
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = value,
      style =
        if (useMonospace) {
          MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
        } else {
          MaterialTheme.typography.bodySmall
        },
      color =
        if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
