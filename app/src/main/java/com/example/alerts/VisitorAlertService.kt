package com.example.alerts

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.StaffRole
import com.example.data.model.Visit
import com.example.data.repository.VisitorDeskRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VisitorAlertService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var listenerJob: Job? = null
  private var lastVisitsById: Map<String, Visit>? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }

    val officeId = intent?.getStringExtra(EXTRA_OFFICE_ID).orEmpty()
    val userEmail = intent?.getStringExtra(EXTRA_USER_EMAIL).orEmpty()
    val roleWire = intent?.getStringExtra(EXTRA_ROLE).orEmpty()

    if (officeId.isBlank() || userEmail.isBlank() || Firebase.auth.currentUser == null) {
      stopSelf()
      return START_NOT_STICKY
    }

    VisitorAlertManager.ensureChannels(this)
    val openAppIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val ongoing =
      NotificationCompat.Builder(this, VisitorAlertManager.CHANNEL_SERVICE)
        .setSmallIcon(R.drawable.ic_stat_archpoint)
        .setContentTitle("ArchPoint Visitors · Alerts active")
        .setContentText("Listening for visitor check-ins and host replies")
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(openAppIntent)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        FOREGROUND_NOTIF_ID,
        ongoing,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      startForeground(FOREGROUND_NOTIF_ID, ongoing)
    }

    startListening(officeId, userEmail, roleWire)
    return START_STICKY
  }

  private fun startListening(officeId: String, userEmail: String, roleWire: String) {
    listenerJob?.cancel()
    lastVisitsById = null
    val repo = VisitorDeskRepository(applicationContext)
    val role = StaffRole.fromWire(roleWire)
    val isReceptionOrAdmin = role == StaffRole.ADMIN || role == StaffRole.RECEPTION

    listenerJob =
      serviceScope.launch {
        repo
          .observeVisits(officeId, userEmail)
          .catch { /* Handled by UI / stop safely if signed out */ }
          .collectLatest { visits ->
            val currentMap = visits.associateBy { it.id }
            val previousMap = lastVisitsById
            if (previousMap != null) {
              visits.forEach { visit ->
                VisitorAlertManager.evaluateVisitChange(
                  context = applicationContext,
                  officeId = officeId,
                  userEmail = userEmail,
                  isReceptionOrAdmin = isReceptionOrAdmin,
                  prev = previousMap[visit.id],
                  curr = visit,
                )
              }
            }
            lastVisitsById = currentMap
          }
      }
  }

  override fun onDestroy() {
    listenerJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    private const val FOREGROUND_NOTIF_ID = 4401
    private const val ACTION_STOP = "com.example.alerts.STOP_SERVICE"
    private const val EXTRA_OFFICE_ID = "extra_office_id"
    private const val EXTRA_USER_EMAIL = "extra_user_email"
    private const val EXTRA_ROLE = "extra_role"

    fun start(context: Context, officeId: String, userEmail: String, role: StaffRole) {
      val intent =
        Intent(context, VisitorAlertService::class.java).apply {
          putExtra(EXTRA_OFFICE_ID, officeId)
          putExtra(EXTRA_USER_EMAIL, userEmail)
          putExtra(EXTRA_ROLE, role.wireValue)
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, VisitorAlertService::class.java))
    }
  }
}
