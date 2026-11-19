package com.example.data.calendar

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface CalendarAuthState {
  data class Authorized(val accessToken: String) : CalendarAuthState
  data class NeedsConsent(val intentSender: IntentSender) : CalendarAuthState
  data class Error(val message: String) : CalendarAuthState
}

object GoogleCalendarAuthHelper {
  const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"

  suspend fun authorize(context: Context): CalendarAuthState {
    val request =
      AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(CALENDAR_EVENTS_SCOPE)))
        .build()

    return suspendCancellableCoroutine { continuation ->
      Identity.getAuthorizationClient(context)
        .authorize(request)
        .addOnSuccessListener { result ->
          if (continuation.isActive) {
            continuation.resume(mapResult(result))
          }
        }
        .addOnFailureListener { error ->
          if (continuation.isActive) {
            continuation.resume(
              CalendarAuthState.Error(
                error.localizedMessage ?: "Unable to connect to Google Calendar.",
              ),
            )
          }
        }
    }
  }

  fun extractTokenFromIntent(context: Context, data: Intent?): CalendarAuthState =
    try {
      val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
      val token = result.accessToken.orEmpty()
      if (token.isNotBlank()) {
        CalendarAuthState.Authorized(token)
      } else {
        CalendarAuthState.Error("Google Calendar access was not granted.")
      }
    } catch (e: Exception) {
      CalendarAuthState.Error(e.localizedMessage ?: "Google Calendar authorization cancelled.")
    }

  private fun mapResult(result: AuthorizationResult): CalendarAuthState {
    if (result.hasResolution()) {
      val sender = result.pendingIntent?.intentSender
      return if (sender != null) {
        CalendarAuthState.NeedsConsent(sender)
      } else {
        CalendarAuthState.Error("Google Calendar consent prompt could not be opened.")
      }
    }
    val token = result.accessToken.orEmpty()
    return if (token.isNotBlank()) {
      CalendarAuthState.Authorized(token)
    } else {
      CalendarAuthState.Error("No Google Calendar access token returned.")
    }
  }
}
