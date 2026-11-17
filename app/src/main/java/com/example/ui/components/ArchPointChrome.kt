package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppTab
import com.example.data.model.StaffRole
import com.example.ui.theme.ArchAmber
import com.example.ui.theme.ArchAmberBorderDark
import com.example.ui.theme.ArchOrange
import com.example.ui.theme.DarkGlassBorderBottom
import com.example.ui.theme.DarkGlassBorderTop
import com.example.ui.theme.HeaderDark
import com.example.ui.theme.LightGlassBorderBottom
import com.example.ui.theme.LightGlassBorderTop
import com.example.ui.theme.LightGlassElevated
import com.example.ui.theme.LightGlassFill
import com.example.ui.theme.LightGlassHeader
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PrimaryActionBlue
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.archPointStatusColors

@Composable
fun GlassBackdrop(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val isDark = LocalIsDarkTheme.current
  val bgColors =
    if (isDark) {
      listOf(Color(0xFF050A16), Color(0xFF0A142B), Color(0xFF060C1A))
    } else {
      listOf(Color(0xFFE7EFFC), Color(0xFFF3F7FE), Color(0xFFE5EEFA))
    }

  Box(
    modifier = modifier.background(Brush.verticalGradient(bgColors))
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val topLeftOrb = Offset(size.width * 0.14f, size.height * 0.12f)
      val topRightOrb = Offset(size.width * 0.88f, size.height * 0.24f)
      val bottomOrb = Offset(size.width * 0.45f, size.height * 0.86f)

      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                PrimaryActionBlue.copy(alpha = if (isDark) 0.24f else 0.16f),
                Color.Transparent,
              ),
            center = topLeftOrb,
            radius = size.maxDimension * 0.48f,
          ),
        center = topLeftOrb,
        radius = size.maxDimension * 0.48f,
      )
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                ArchAmber.copy(alpha = if (isDark) 0.14f else 0.12f),
                Color.Transparent,
              ),
            center = topRightOrb,
            radius = size.maxDimension * 0.40f,
          ),
        center = topRightOrb,
        radius = size.maxDimension * 0.40f,
      )
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                Color(0xFF0EA5E9).copy(alpha = if (isDark) 0.14f else 0.11f),
                Color.Transparent,
              ),
            center = bottomOrb,
            radius = size.maxDimension * 0.45f,
          ),
        center = bottomOrb,
        radius = size.maxDimension * 0.45f,
      )
    }
    content()
  }
}

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(22.dp),
  borderBrush: Brush? = null,
  contentPadding: PaddingValues = PaddingValues(18.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val isDark = LocalIsDarkTheme.current
  val fill = if (isDark) SurfaceDark else LightGlassFill
  val rim =
    borderBrush
      ?: Brush.linearGradient(
        colors =
          if (isDark) {
            listOf(DarkGlassBorderTop, DarkGlassBorderBottom)
          } else {
            listOf(LightGlassBorderTop, LightGlassBorderBottom)
          }
      )

  Surface(
    modifier = modifier,
    shape = shape,
    color = fill,
    border = BorderStroke(1.dp, rim),
    shadowElevation = 0.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

@Composable
fun GlassInnerSurface(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(14.dp),
  containerColor: Color? = null,
  borderColor: Color? = null,
  content: @Composable () -> Unit,
) {
  val isDark = LocalIsDarkTheme.current
  val bg = containerColor ?: if (isDark) SurfaceElevatedDark else LightGlassElevated
  val borderStroke =
    if (borderColor != null) {
      BorderStroke(1.dp, borderColor)
    } else {
      BorderStroke(
        1.dp,
        Brush.linearGradient(
          colors =
            if (isDark) {
              listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
            } else {
              listOf(Color.White.copy(alpha = 0.90f), Color(0xFF94A3B8).copy(alpha = 0.25f))
            }
        ),
      )
    }

  Surface(
    modifier = modifier,
    shape = shape,
    color = bg,
    border = borderStroke,
  ) {
    content()
  }
}

@Composable
fun <T> GlassSegmentedControl(
  options: List<Pair<T, String>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  tagPrefix: String = "segment",
) {
  val isDark = LocalIsDarkTheme.current
  GlassInnerSurface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      options.forEach { (value, label) ->
        val isSelected = value == selected
        Surface(
          modifier =
            Modifier.weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .clickable { onSelect(value) }
              .testTag("${tagPrefix}_$label"),
          shape = RoundedCornerShape(12.dp),
          color =
            when {
              isSelected -> MaterialTheme.colorScheme.primary
              else -> Color.Transparent
            },
          shadowElevation = if (isSelected && !isDark) 2.dp else 0.dp,
        ) {
          Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelLarge,
              color =
                if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
fun ArchPointHeaderBar(
  staffDisplayName: String,
  role: StaffRole,
  officeName: String,
  onOpenProfile: () -> Unit,
) {
  val isDark = LocalIsDarkTheme.current
  val headerBg = if (isDark) HeaderDark else LightGlassHeader
  val rimColor =
    if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFF94A3B8).copy(alpha = 0.28f)

  Surface(
    color = headerBg,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(0.5.dp, rimColor),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
      ) {
        Image(
          painter =
            painterResource(
              id = if (isDark) R.drawable.img_archpoint_logo_dark else R.drawable.img_archpoint_logo
            ),
          contentDescription = "ArchPoint logo",
          modifier = Modifier.width(96.dp).height(34.dp),
          contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column {
          Text(
            text = officeName.ifBlank { stringResource(R.string.app_name) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "$staffDisplayName · ${role.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Surface(
        modifier =
          Modifier.clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenProfile)
            .testTag("header_profile_pill"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = "Open Profile and Settings",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(5.dp))
          Text(
            text = "Profile",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }
  }
}

@Composable
fun CompactAlertsPromptBanner(
  onTurnOnAlerts: () -> Unit,
) {
  val status = archPointStatusColors()
  GlassInnerSurface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    shape = RoundedCornerShape(14.dp),
    containerColor = status.waitingContainer,
    borderColor = ArchAmberBorderDark.copy(alpha = 0.55f),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Icon(
          imageVector = Icons.Outlined.NotificationsActive,
          contentDescription = null,
          tint = status.waitingOnContainer,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = "Enable ring alerts for new visitors",
          style = MaterialTheme.typography.bodySmall,
          color = status.waitingOnContainer,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.width(8.dp))
      Button(
        onClick = onTurnOnAlerts,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.height(38.dp).testTag("turn_on_alerts_button"),
      ) {
        Text("Enable", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
fun ArchPointBottomNav(
  tabs: List<AppTab>,
  selectedTab: AppTab,
  myWaitingCount: Int,
  onSelectTab: (AppTab) -> Unit,
) {
  val isDark = LocalIsDarkTheme.current
  val navBg = if (isDark) HeaderDark else LightGlassHeader
  NavigationBar(
    containerColor = navBg,
    tonalElevation = 0.dp,
    windowInsets = WindowInsets.navigationBars,
  ) {
    tabs.forEach { tab ->
      val selected = tab == selectedTab
      NavigationBarItem(
        selected = selected,
        onClick = { onSelectTab(tab) },
        modifier = Modifier.testTag("nav_tab_${tab.route}"),
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor =
              if (isDark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.14f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        icon = { TabIconWithBadge(tab = tab, selected = selected, myWaitingCount = myWaitingCount) },
        label = {
          Text(
            text = tabTitle(tab),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
          )
        },
      )
    }
  }
}

@Composable
fun ArchPointNavRail(
  tabs: List<AppTab>,
  selectedTab: AppTab,
  myWaitingCount: Int,
  onSelectTab: (AppTab) -> Unit,
) {
  val isDark = LocalIsDarkTheme.current
  val railBg = if (isDark) HeaderDark else LightGlassHeader
  NavigationRail(
    containerColor = railBg,
  ) {
    Spacer(Modifier.height(12.dp))
    tabs.forEach { tab ->
      val selected = tab == selectedTab
      NavigationRailItem(
        selected = selected,
        onClick = { onSelectTab(tab) },
        modifier = Modifier.testTag("rail_tab_${tab.route}"),
        colors =
          NavigationRailItemDefaults.colors(
            selectedIconColor =
              if (isDark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.14f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        icon = { TabIconWithBadge(tab = tab, selected = selected, myWaitingCount = myWaitingCount) },
        label = { Text(tabTitle(tab), style = MaterialTheme.typography.labelSmall) },
      )
    }
  }
}

@Composable
private fun TabIconWithBadge(tab: AppTab, selected: Boolean, myWaitingCount: Int) {
  val iconVector =
    when (tab) {
      AppTab.RECEPTION -> if (selected) Icons.Rounded.Badge else Icons.Outlined.Badge
      AppTab.MY_VISITORS -> if (selected) Icons.Rounded.People else Icons.Outlined.PeopleOutline
      AppTab.VISITOR_LOG -> if (selected) Icons.Rounded.History else Icons.Outlined.History
      AppTab.ADMIN -> if (selected) Icons.Rounded.AdminPanelSettings else Icons.Outlined.AdminPanelSettings
      AppTab.PROFILE -> if (selected) Icons.Rounded.Person else Icons.Outlined.PersonOutline
    }

  if (tab == AppTab.MY_VISITORS && myWaitingCount > 0) {
    BadgedBox(
      badge = {
        Badge(
          containerColor = ArchOrange,
          contentColor = Color.White,
        ) {
          Text(myWaitingCount.toString())
        }
      }
    ) {
      Icon(imageVector = iconVector, contentDescription = tabTitle(tab))
    }
  } else {
    Icon(imageVector = iconVector, contentDescription = tabTitle(tab))
  }
}

@Composable
fun tabTitle(tab: AppTab): String =
  when (tab) {
    AppTab.RECEPTION -> stringResource(R.string.tab_reception)
    AppTab.MY_VISITORS -> stringResource(R.string.tab_my_visitors)
    AppTab.VISITOR_LOG -> stringResource(R.string.tab_visitor_log)
    AppTab.ADMIN -> stringResource(R.string.tab_admin)
    AppTab.PROFILE -> stringResource(R.string.tab_profile)
  }

@Composable
fun StatusPill(
  text: String,
  containerColor: Color,
  contentColor: Color,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = containerColor,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun FreeDotLabel(isFree: Boolean, text: String) {
  val status = archPointStatusColors()
  val accent = if (isFree) status.freeText else status.meetingText
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier =
        Modifier.size(8.dp)
          .clip(CircleShape)
          .background(accent)
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = accent,
      fontWeight = FontWeight.SemiBold,
    )
  }
}
