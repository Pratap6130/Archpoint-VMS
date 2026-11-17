package com.example.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AppTab
import com.example.data.model.Visit
import com.example.data.model.VisitStatus

object AlertDedupStore {
  private val lock = Any()
  private val lastNotifiedKeys = mutableSetOf<String>()

  fun shouldDispatch(key: String): Boolean =
    synchronized(lock) {
      if (lastNotifiedKeys.contains(key)) {
        false
      } else {
        if (lastNotifiedKeys.size > 400) {
          lastNotifiedKeys.clear()
        }
        lastNotifiedKeys.add(key)
        true
      }
    }

  fun clear() {
    synchronized(lock) { lastNotifiedKeys.clear() }
  }
}

object InAppAlertDedupStore {
  private val lock = Any()
  private val lastShownKeys = mutableSetOf<String>()

  fun shouldDispatch(key: String): Boolean =
    synchronized(lock) {
      if (lastShownKeys.contains(key)) {
        false
      } else {
        if (lastShownKeys.size > 400) {
          lastShownKeys.clear()
        }
        lastShownKeys.add(key)
        true
      }
    }

  fun clear() {
    synchronized(lock) { lastShownKeys.clear() }
  }
}

data class VisitAlertEvent(
  val key: String,
  val officeId: String,
  val visit: Visit,
  val waitMinutes: Long,
  val snackbarMessage: String,
  val actionLabel: String,
  val targetTab: AppTab,
  val isHostAlert: Boolean,
  val isReminder: Boolean,
  val notifTitle: String,
  val notifBody: String,
)

object VisitorAlertManager {
  const val CHANNEL_ALERTS = "archpoint_visitors_alerts_v1"
  const val CHANNEL_SERVICE = "archpoint_visitors_service_v1"
  const val EXTRA_TARGET_TAB = "extra_target_tab"
  private const val PREFS_NAME = "archpoint_alerts_prefs"
  private const val KEY_BACKGROUND_ALERTS = "background_alerts_enabled"
  private const val ALERT_TIMEOUT_MS = 10 * 60 * 1000L

  fun isBackgroundAlertsPreferred(context: Context): Boolean {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_BACKGROUND_ALERTS, true)
  }

  fun setBackgroundAlertsPreferred(context: Context, enabled: Boolean) {
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_BACKGROUND_ALERTS, enabled)
      .apply()
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  }

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      if (manager.getNotificationChannel(CHANNEL_ALERTS) == null) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes =
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel =
          NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_visitors_name),
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = context.getString(R.string.channel_visitors_desc)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 260L, 120L, 260L)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(soundUri, audioAttributes)
          }
        manager.createNotificationChannel(channel)
      }

      if (manager.getNotificationChannel(CHANNEL_SERVICE) == null) {
        val serviceChannel =
          NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_LOW,
          ).apply {
            description = context.getString(R.string.channel_service_desc)
            setShowBadge(false)
          }
        manager.createNotificationChannel(serviceChannel)
      }
    }
  }

  fun sendTestAlert(context: Context) {
    ensureChannels(context)
    if (!hasNotificationPermission(context)) return
    val openIntent = openTabPendingIntent(context, AppTab.PROFILE, 9001)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_stat_archpoint)
        .setContentTitle("Alerts are working")
        .setContentText("You will hear high-priority visitor alerts even when locked.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .setTimeoutAfter(ALERT_TIMEOUT_MS)
        .build()
    NotificationManagerCompat.from(context).notify(9001, notification)
    playInAppChimeAndVibrate(context)
  }

  fun showHostArrivalAlert(
    context: Context,
    officeId: String,
    visit: Visit,
    waitMinutes: Long,
    isReminder: Boolean,
  ) {
    ensureChannels(context)
    if (!hasNotificationPermission(context)) return
    val title =
      if (isReminder) {
        "Reminder: ${visit.visitorName} is still waiting"
      } else {
        "${visit.visitorName} is here to see you"
      }
    val companyPart = visit.visitorCompany.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
    val body = "${companyPart}${visit.reason} · waiting ${waitMinutes} min at reception"
    val notifId = visit.id.hashCode() and 0x7FFFFFFF

    val openIntent = openTabPendingIntent(context, AppTab.MY_VISITORS, notifId)

    val sendInIntent =
      actionPendingIntent(
        context = context,
        requestCode = notifId + 1,
        action = VisitorNotificationActionReceiver.ACTION_SEND_IN,
        officeId = officeId,
        visitId = visit.id,
        notifId = notifId,
      )
    val waitIntent =
      actionPendingIntent(
        context = context,
        requestCode = notifId + 2,
        action = VisitorNotificationActionReceiver.ACTION_WAIT_10,
        officeId = officeId,
        visitId = visit.id,
        notifId = notifId,
      )
    val notTodayIntent =
      actionPendingIntent(
        context = context,
        requestCode = notifId + 3,
        action = VisitorNotificationActionReceiver.ACTION_NOT_TODAY,
        officeId = officeId,
        visitId = visit.id,
        notifId = notifId,
      )

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_stat_archpoint)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .setTimeoutAfter(ALERT_TIMEOUT_MS)
        .addAction(0, "Send in", sendInIntent)
        .addAction(0, "Wait 10 min", waitIntent)
        .addAction(0, "Not today", notTodayIntent)
        .build()

    NotificationManagerCompat.from(context).notify(notifId, notification)
    playInAppChimeAndVibrate(context)
  }

  fun showReceptionStatusAlert(
    context: Context,
    visit: Visit,
    title: String,
    body: String,
  ) {
    ensureChannels(context)
    if (!hasNotificationPermission(context)) return
    val notifId = ("rec_${visit.id}_${visit.status}_${visit.waitNote}".hashCode()) and 0x7FFFFFFF
    val openIntent = openTabPendingIntent(context, AppTab.RECEPTION, notifId)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_stat_archpoint)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .setTimeoutAfter(ALERT_TIMEOUT_MS)
        .build()
    NotificationManagerCompat.from(context).notify(notifId, notification)
    playInAppChimeAndVibrate(context)
  }

  fun playInAppChimeAndVibrate(context: Context) {
    try {
      val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val ringtone = RingtoneManager.getRingtone(context.applicationContext, soundUri)
      ringtone?.play()
    } catch (_: Exception) {
    }
    try {
      val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
          @Suppress("DEPRECATION")
          context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
      if (vibrator != null && vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0L, 180L, 90L, 180L), -1)
          )
        } else {
          @Suppress("DEPRECATION")
          vibrator.vibrate(250L)
        }
      }
    } catch (_: Exception) {
    }
  }

  fun detectVisitAlert(
    officeId: String,
    userEmail: String,
    isReception: Boolean,
    prev: Visit?,
    curr: Visit,
  ): VisitAlertEvent? {
    val cleanEmail = userEmail.trim().lowercase()
    val isMyHostVisit = curr.hostEmail.trim().lowercase() == cleanEmail
    val waitMin =
      ((System.currentTimeMillis() - (curr.waitStartedAt?.toDate()?.time ?: System.currentTimeMillis())) /
          60_000L)
        .coerceAtLeast(0L)

    // 1. New Waiting visitor checked in for this specific host
    if ((prev == null || prev.parsedStatus() != VisitStatus.WAITING) &&
      curr.parsedStatus() == VisitStatus.WAITING &&
      isMyHostVisit
    ) {
      val companyPart = curr.visitorCompany.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
      val title = "${curr.visitorName} checked in to see you"
      val body = "${curr.visitorName}$companyPart · ${curr.reason}"
      return VisitAlertEvent(
        key = "new_wait_${curr.id}",
        officeId = officeId,
        visit = curr,
        waitMinutes = waitMin,
        snackbarMessage = "$title · ${curr.reason}",
        actionLabel = "View",
        targetTab = AppTab.MY_VISITORS,
        isHostAlert = true,
        isReminder = false,
        notifTitle = title,
        notifBody = body,
      )
    }

    if (prev != null) {
      // 2. Reception pressed Remind for this host
      if (curr.parsedStatus() == VisitStatus.WAITING &&
        curr.remindedCount > prev.remindedCount &&
        isMyHostVisit
      ) {
        val title = "Reminder: ${curr.visitorName} is still waiting"
        return VisitAlertEvent(
          key = "remind_${curr.id}_${curr.remindedCount}",
          officeId = officeId,
          visit = curr,
          waitMinutes = waitMin,
          snackbarMessage = title,
          actionLabel = "View",
          targetTab = AppTab.MY_VISITORS,
          isHostAlert = true,
          isReminder = true,
          notifTitle = title,
          notifBody = "${curr.reason} · waiting ${waitMin} min at reception",
        )
      }

      // 3. Host replied -> alert reception only
      if (isReception) {
        if (prev.parsedStatus() == VisitStatus.WAITING &&
          curr.parsedStatus() == VisitStatus.IN_MEETING
        ) {
          val cabin = curr.hostCabin.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
          val title = "Send ${curr.visitorName} in to ${curr.hostShownAs}$cabin"
          return VisitAlertEvent(
            key = "send_in_${curr.id}",
            officeId = officeId,
            visit = curr,
            waitMinutes = waitMin,
            snackbarMessage = title,
            actionLabel = "Reception",
            targetTab = AppTab.RECEPTION,
            isHostAlert = false,
            isReminder = false,
            notifTitle = title,
            notifBody = "Meeting started",
          )
        } else if ((curr.parsedStatus() == VisitStatus.WAITING || curr.parsedStatus() == VisitStatus.IN_MEETING) &&
          curr.waitNote != prev.waitNote &&
          curr.waitNote.isNotBlank()
        ) {
          val title = "${curr.hostShownAs}: ${curr.waitNote}"
          val body = "${curr.visitorName} · ${curr.reason}"
          return VisitAlertEvent(
            key = "wait_note_${curr.id}_${curr.waitNote}",
            officeId = officeId,
            visit = curr,
            waitMinutes = waitMin,
            snackbarMessage = "$title (${curr.visitorName})",
            actionLabel = "Reception",
            targetTab = AppTab.RECEPTION,
            isHostAlert = false,
            isReminder = false,
            notifTitle = title,
            notifBody = body,
          )
        } else if (prev.parsedStatus() == VisitStatus.WAITING &&
          curr.parsedStatus() == VisitStatus.DENIED
        ) {
          val title = "${curr.hostShownAs} can't meet ${curr.visitorName} today"
          return VisitAlertEvent(
            key = "denied_${curr.id}",
            officeId = officeId,
            visit = curr,
            waitMinutes = waitMin,
            snackbarMessage = if (curr.waitNote.isNotBlank()) "$title (${curr.waitNote})" else title,
            actionLabel = "Reception",
            targetTab = AppTab.RECEPTION,
            isHostAlert = false,
            isReminder = false,
            notifTitle = title,
            notifBody = curr.waitNote.ifBlank { "Marked Denied" },
          )
        } else if (prev.parsedStatus() == VisitStatus.IN_MEETING &&
          curr.parsedStatus() == VisitStatus.DONE
        ) {
          val title = "${curr.hostShownAs} is free now"
          return VisitAlertEvent(
            key = "done_${curr.id}",
            officeId = officeId,
            visit = curr,
            waitMinutes = waitMin,
            snackbarMessage = title,
            actionLabel = "Reception",
            targetTab = AppTab.RECEPTION,
            isHostAlert = false,
            isReminder = false,
            notifTitle = title,
            notifBody = "Finished meeting with ${curr.visitorName}",
          )
        }
      }
    }
    return null
  }

  fun dispatchSystemNotification(context: Context, event: VisitAlertEvent) {
    if (!hasNotificationPermission(context)) return
    if (!AlertDedupStore.shouldDispatch(event.key)) return
    if (event.isHostAlert) {
      showHostArrivalAlert(
        context = context,
        officeId = event.officeId,
        visit = event.visit,
        waitMinutes = event.waitMinutes,
        isReminder = event.isReminder,
      )
    } else {
      showReceptionStatusAlert(
        context = context,
        visit = event.visit,
        title = event.notifTitle,
        body = event.notifBody,
      )
    }
  }

  fun evaluateVisitChange(
    context: Context,
    officeId: String,
    userEmail: String,
    isReception: Boolean,
    prev: Visit?,
    curr: Visit,
  ): VisitAlertEvent? {
    val event =
      detectVisitAlert(
        officeId = officeId,
        userEmail = userEmail,
        isReception = isReception,
        prev = prev,
        curr = curr,
      ) ?: return null
    dispatchSystemNotification(context, event)
    return event
  }

  private fun openTabPendingIntent(context: Context, tab: AppTab, requestCode: Int): PendingIntent {
    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_TARGET_TAB, tab.route)
      }
    return PendingIntent.getActivity(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun actionPendingIntent(
    context: Context,
    requestCode: Int,
    action: String,
    officeId: String,
    visitId: String,
    notifId: Int,
  ): PendingIntent {
    val intent =
      Intent(context, VisitorNotificationActionReceiver::class.java).apply {
        this.action = action
        putExtra(VisitorNotificationActionReceiver.EXTRA_OFFICE_ID, officeId)
        putExtra(VisitorNotificationActionReceiver.EXTRA_VISIT_ID, visitId)
        putExtra(VisitorNotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
      }
    return PendingIntent.getBroadcast(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
