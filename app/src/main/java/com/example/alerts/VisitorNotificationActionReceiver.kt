package com.example.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.data.repository.VisitorDeskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VisitorNotificationActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val officeId = intent.getStringExtra(EXTRA_OFFICE_ID) ?: return
    val visitId = intent.getStringExtra(EXTRA_VISIT_ID) ?: return
    val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
    val action = intent.action ?: return

    val pendingResult = goAsync()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
      try {
        val repo = VisitorDeskRepository(context.applicationContext)
        when (action) {
          ACTION_SEND_IN -> repo.sendVisitorIn(officeId, visitId, "Send in")
          ACTION_WAIT_10 -> repo.askVisitorToWait(officeId, visitId)
          ACTION_NOT_TODAY -> repo.denyVisitorToday(officeId, visitId)
        }
        if (notifId != -1) {
          NotificationManagerCompat.from(context.applicationContext).cancel(notifId)
        }
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    const val ACTION_SEND_IN = "com.example.alerts.ACTION_SEND_IN"
    const val ACTION_WAIT_10 = "com.example.alerts.ACTION_WAIT_10"
    const val ACTION_NOT_TODAY = "com.example.alerts.ACTION_NOT_TODAY"
    const val EXTRA_OFFICE_ID = "extra_office_id"
    const val EXTRA_VISIT_ID = "extra_visit_id"
    const val EXTRA_NOTIF_ID = "extra_notif_id"
  }
}
