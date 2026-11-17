package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemeMode

val LocalIsDarkTheme = compositionLocalOf { true }

data class SemanticStatusColors(
  val freeText: Color,
  val freeContainer: Color,
  val freeOnContainer: Color,
  val meetingText: Color,
  val meetingContainer: Color,
  val meetingOnContainer: Color,
  val waitingContainer: Color,
  val waitingOnContainer: Color,
  val warningAccent: Color,
  val deniedText: Color,
  val deniedContainer: Color,
  val deniedOnContainer: Color,
)

@Composable
fun archPointStatusColors(): SemanticStatusColors {
  val isDark = LocalIsDarkTheme.current
  return if (isDark) {
    SemanticStatusColors(
      freeText = FreeGreenBright,
      freeContainer = FreeGreenDarkContainer,
      freeOnContainer = FreeGreenOnContainer,
      meetingText = MeetingBlueBright,
      meetingContainer = MeetingBlueDarkContainer,
      meetingOnContainer = MeetingBlueOnContainer,
      waitingContainer = ArchAmberDarkContainer,
      waitingOnContainer = ArchAmberOnContainer,
      warningAccent = ArchOrangeBright,
      deniedText = DeniedRedBright,
      deniedContainer = DeniedRedDarkContainer,
      deniedOnContainer = DeniedRedOnContainer,
    )
  } else {
    SemanticStatusColors(
      freeText = FreeGreen,
      freeContainer = FreeGreenSoft,
      freeOnContainer = FreeGreen,
      meetingText = MeetingBlue,
      meetingContainer = MeetingBlueSoft,
      meetingOnContainer = MeetingBlue,
      waitingContainer = ArchAmberSoft,
      waitingOnContainer = Color(0xFF92400E),
      warningAccent = ArchOrange,
      deniedText = DeniedRed,
      deniedContainer = DeniedRedSoft,
      deniedOnContainer = DeniedRed,
    )
  }
}

private val LightColorScheme = lightColorScheme(
  primary = PrimaryActionBlue,
  onPrimary = Color.White,
  primaryContainer = ArchNavyContainer,
  onPrimaryContainer = ArchNavy,
  secondary = ArchAmberDeep,
  onSecondary = Color.White,
  secondaryContainer = ArchAmberSoft,
  onSecondaryContainer = Color(0xFF78350F),
  tertiary = FreeGreen,
  onTertiary = Color.White,
  tertiaryContainer = FreeGreenSoft,
  onTertiaryContainer = FreeGreen,
  error = DeniedRed,
  onError = Color.White,
  errorContainer = DeniedRedSoft,
  onErrorContainer = DeniedRed,
  background = CanvasLight,
  onBackground = InkPrimary,
  surface = SurfaceWhite,
  onSurface = InkPrimary,
  surfaceVariant = SurfaceSubtle,
  onSurfaceVariant = InkSecondary,
  outline = BorderSlate,
  outlineVariant = BorderSlate,
)

private val DarkColorScheme = darkColorScheme(
  primary = PrimaryActionBlue,
  onPrimary = Color.White,
  primaryContainer = PrimaryContainerDark,
  onPrimaryContainer = PrimaryOnContainerDark,
  secondary = ArchAmberBright,
  onSecondary = InkPrimary,
  secondaryContainer = ArchAmberDarkContainer,
  onSecondaryContainer = ArchAmberOnContainer,
  tertiary = FreeGreenBright,
  onTertiary = Color(0xFF052E16),
  tertiaryContainer = FreeGreenDarkContainer,
  onTertiaryContainer = FreeGreenOnContainer,
  error = DeniedRedBright,
  onError = Color(0xFF450A0A),
  errorContainer = DeniedRedDarkContainer,
  onErrorContainer = DeniedRedOnContainer,
  background = CanvasDark,
  onBackground = TextPrimaryDark,
  surface = SurfaceDark,
  onSurface = TextPrimaryDark,
  surfaceVariant = SurfaceElevatedDark,
  onSurfaceVariant = TextSecondaryDark,
  outline = BorderDark,
  outlineVariant = BorderDark,
)

val AppShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ArchPointVisitorsTheme(
  themeMode: AppThemeMode = AppThemeMode.DARK,
  content: @Composable () -> Unit,
) {
  val isDark =
    when (themeMode) {
      AppThemeMode.DARK -> true
      AppThemeMode.LIGHT -> false
      AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
  val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
  CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = AppShapes,
      content = content,
    )
  }
}
