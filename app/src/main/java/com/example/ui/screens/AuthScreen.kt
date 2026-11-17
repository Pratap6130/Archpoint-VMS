package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.onGoogleSignInClicked
import com.example.ui.components.GlassBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassInnerSurface
import com.example.ui.theme.LocalIsDarkTheme

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var isCheckingAuto by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        isCheckingAuto = false
        onAuthSuccess()
      },
      onUnauthenticated = { isCheckingAuto = false },
      scope = scope,
    )
  }

  GlassBackdrop(
    modifier = Modifier.fillMaxSize(),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .systemBarsPadding()
          .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      GlassCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(28.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          val isDark = LocalIsDarkTheme.current
          Image(
            painter =
              painterResource(
                id = if (isDark) R.drawable.img_archpoint_logo_dark else R.drawable.img_archpoint_logo
              ),
            contentDescription = "ArchPoint Visitors logo",
            modifier = Modifier.width(210.dp).height(68.dp),
            contentScale = ContentScale.Fit,
          )

          Spacer(Modifier.height(18.dp))

          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.staff_sign_in_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          Spacer(Modifier.height(24.dp))

          if (!errorMessage.isNullOrBlank()) {
            GlassInnerSurface(
              modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
              shape = RoundedCornerShape(12.dp),
              containerColor = MaterialTheme.colorScheme.errorContainer,
            ) {
              Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Button(
            onClick = {
              errorMessage = null
              isLoading = true
              onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = { isLoading = false },
              )
            },
            enabled = !isLoading && !isCheckingAuto,
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("google_sign_in_button"),
            shape = RoundedCornerShape(14.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
          ) {
            if (isLoading || isCheckingAuto) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp,
              )
            } else {
              Icon(
                imageVector = Icons.Rounded.Login,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(Modifier.size(10.dp))
              Text(
                text = stringResource(R.string.sign_in_with_google),
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }

          Spacer(Modifier.height(16.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(
              imageVector = Icons.Rounded.VerifiedUser,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
              text = "Staff accounts & roles are managed by your ArchPoint Admin.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
fun WorkspaceSetupScreen(
  userDisplayName: String,
  userEmail: String,
  isBusy: Boolean,
  onInitialize: (officeName: String, fullName: String, shownAs: String, cabin: String) -> Unit,
  onSignOut: () -> Unit,
) {
  var officeName by remember { mutableStateOf("ArchPoint Jaipur") }
  var fullName by remember(userDisplayName) {
    mutableStateOf(userDisplayName.ifBlank { userEmail.substringBefore("@").replaceFirstChar { it.uppercase() } })
  }
  var shownAs by remember(fullName) {
    mutableStateOf(fullName.split(" ").firstOrNull()?.ifBlank { "Admin" } ?: "Admin")
  }
  var cabin by remember { mutableStateOf("Main Studio") }

  GlassBackdrop(
    modifier = Modifier.fillMaxSize(),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .systemBarsPadding()
          .padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      GlassCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(24.dp),
      ) {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.size(48.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Business,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Spacer(Modifier.size(12.dp))
            Column {
              Text("Set up your ArchPoint desk", style = MaterialTheme.typography.titleLarge)
              Text(
                "Signed in as $userEmail",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Text(
            "No existing office was linked to your email yet. Create your office workspace as Admin (and listed Host) to start checking in visitors immediately, or ask your Admin to add $userEmail in the Admin tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          OutlinedTextField(
            value = officeName,
            onValueChange = { officeName = it },
            label = { Text("Office name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_office_name_input"),
          )
          OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Your full name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_full_name_input"),
          )
          OutlinedTextField(
            value = shownAs,
            onValueChange = { shownAs = it },
            label = { Text("Shown as (e.g. Pratap, Amit Sir)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_shown_as_input"),
          )
          OutlinedTextField(
            value = cabin,
            onValueChange = { cabin = it },
            label = { Text("Cabin / seating (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_cabin_input"),
          )

          Button(
            onClick = { onInitialize(officeName, fullName, shownAs, cabin) },
            enabled = !isBusy && officeName.isNotBlank() && fullName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("create_office_button"),
            shape = RoundedCornerShape(14.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
              )
            } else {
              Text("Start ArchPoint Desk", fontWeight = FontWeight.SemiBold)
            }
          }

          OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("setup_sign_out_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Use a different Google account")
          }
        }
      }
    }
  }
}

@Composable
fun AccountDeactivatedScreen(
  officeName: String,
  userEmail: String,
  onSignOut: () -> Unit,
) {
  GlassBackdrop(
    modifier = Modifier.fillMaxSize(),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .systemBarsPadding()
          .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      GlassCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(24.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Block,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
          )
          Text("Account switched off", style = MaterialTheme.typography.titleLarge)
          Text(
            "Your access to $officeName ($userEmail) has been disabled by an Admin. Alerts and desk access are blocked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(6.dp))
          Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("deactivated_sign_out_button"),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
          ) {
            Text(stringResource(R.string.sign_out))
          }
        }
      }
    }
  }
}
