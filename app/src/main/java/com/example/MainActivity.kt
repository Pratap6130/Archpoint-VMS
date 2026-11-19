package com.example

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.alerts.AlertDedupStore
import com.example.alerts.InAppAlertDedupStore
import com.example.alerts.VisitorAlertManager
import com.example.alerts.VisitorAlertService
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOutUser
import com.example.data.model.AppTab
import com.example.data.model.AppThemeMode
import com.example.data.model.StaffRole
import com.example.data.model.VisitStatus
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.VisitorDeskRepository
import com.example.ui.components.ArchPointBottomNav
import com.example.ui.components.ArchPointHeaderBar
import com.example.ui.components.ArchPointNavRail
import com.example.ui.components.CompactAlertsPromptBanner
import com.example.ui.components.GlassBackdrop
import com.example.ui.components.HostDashboard
import com.example.ui.screens.AccessNotAssignedScreen
import com.example.ui.screens.AccountDeactivatedScreen
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AppSettingsBottomSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PRScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReceptionScreen
import com.example.ui.screens.VisitorLogScreen
import com.example.ui.screens.WorkspaceSetupScreen
import com.example.ui.theme.ArchAmberBright
import com.example.ui.theme.ArchPointVisitorsTheme
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.WorkspaceState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val pendingTargetTab = MutableStateFlow<AppTab?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    VisitorAlertManager.ensureChannels(this)
    extractTargetTab(intent)?.let { pendingTargetTab.value = it }
    runCatching {
      val logoBmp = BitmapFactory.decodeResource(resources, R.drawable.img_archpoint_mark)
      @Suppress("DEPRECATION")
      setTaskDescription(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          ActivityManager.TaskDescription.Builder()
            .setLabel(getString(R.string.app_name))
            .build()
        } else {
          ActivityManager.TaskDescription(getString(R.string.app_name), logoBmp)
        }
      )
    }

    val themePreferences = ThemePreferences(applicationContext)

    setContent {
      val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.DARK)
      val appScope = rememberCoroutineScope()

      ArchPointVisitorsTheme(themeMode = themeMode) {
        val isDarkTheme = LocalIsDarkTheme.current
        DisposableEffect(isDarkTheme) {
          enableEdgeToEdge(
            statusBarStyle =
              if (isDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
              } else {
                SystemBarStyle.light(
                  android.graphics.Color.TRANSPARENT,
                  android.graphics.Color.TRANSPARENT,
                )
              },
            navigationBarStyle =
              if (isDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
              } else {
                SystemBarStyle.light(
                  android.graphics.Color.TRANSPARENT,
                  android.graphics.Color.TRANSPARENT,
                )
              },
          )
          onDispose {}
        }

        val targetTab by pendingTargetTab.collectAsStateWithLifecycle()
        AppNavigation(
          pendingTab = targetTab,
          onConsumedPendingTab = { pendingTargetTab.value = null },
          themeMode = themeMode,
          themePreferences = themePreferences,
          onSelectTheme = { mode -> appScope.launch { themePreferences.setThemeMode(mode) } },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractTargetTab(intent)?.let { pendingTargetTab.value = it }
  }

  private fun extractTargetTab(intent: Intent?): AppTab? {
    val route = intent?.getStringExtra(VisitorAlertManager.EXTRA_TARGET_TAB) ?: return null
    return AppTab.entries.firstOrNull { it.route == route }
  }
}

@Composable
fun AppNavigation(
  pendingTab: AppTab?,
  onConsumedPendingTab: () -> Unit,
  themeMode: AppThemeMode,
  themePreferences: ThemePreferences,
  onSelectTheme: (AppThemeMode) -> Unit,
) {
  val auth = remember { Firebase.auth }
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
  } else {
    val uid = user.uid
    val email = user.email.orEmpty()
    val displayName = user.displayName.orEmpty()
    key(uid) {
      AuthenticatedDeskHost(
        currentUserId = uid,
        currentUserEmail = email,
        currentUserDisplayName = displayName,
        pendingTab = pendingTab,
        onConsumedPendingTab = onConsumedPendingTab,
        themeMode = themeMode,
        themePreferences = themePreferences,
        onSelectTheme = onSelectTheme,
      )
    }
  }
}

@Composable
private fun AuthenticatedDeskHost(
  currentUserId: String,
  currentUserEmail: String,
  currentUserDisplayName: String,
  pendingTab: AppTab?,
  onConsumedPendingTab: () -> Unit,
  themeMode: AppThemeMode,
  themePreferences: ThemePreferences,
  onSelectTheme: (AppThemeMode) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: MainViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            MainViewModel(
              repository = VisitorDeskRepository(db),
              preferences = themePreferences,
              currentUserId = currentUserId,
              currentUserEmail = currentUserEmail,
              currentUserDisplayName = currentUserDisplayName,
            )
          }
        },
    )

  val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
  val isSettingUp by viewModel.isSettingUpWorkspace.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val selectedTabOverride by viewModel.selectedTab.collectAsStateWithLifecycle()
  val receptionForm by viewModel.receptionForm.collectAsStateWithLifecycle()
  val visitorSearch by viewModel.visitorSearch.collectAsStateWithLifecycle()
  val cardScanState by viewModel.cardScanState.collectAsStateWithLifecycle()
  val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
  val inFlightVisitIds by viewModel.inFlightVisitIds.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }

  val handleSignOut: () -> Unit = {
    VisitorAlertService.stop(context)
    AlertDedupStore.clear()
    InAppAlertDedupStore.clear()
    signOutUser(credentialManager = credentialManager, scope = scope)
  }

  LaunchedEffect(viewModel) {
    viewModel.feedback.collect { message ->
      scope.launch { snackbarHostState.showSnackbar(message) }
    }
  }

  when (val state = workspaceState) {
    WorkspaceState.Loading -> {
      GlassBackdrop(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator()
            Text("Loading ArchPoint desk...", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }

    WorkspaceState.NeedsSetup -> {
      WorkspaceSetupScreen(
        userDisplayName = currentUserDisplayName,
        userEmail = currentUserEmail,
        isBusy = isSettingUp,
        onInitialize = { officeName, fullName, shownAs, cabin ->
          viewModel.initializeOfficeWorkspace(officeName, fullName, shownAs, cabin)
        },
        onSignOut = handleSignOut,
      )
    }

    is WorkspaceState.AccessNotAssigned -> {
      LaunchedEffect(Unit) { VisitorAlertService.stop(context) }
      AccessNotAssignedScreen(
        userEmail = state.userEmail,
        onSignOut = handleSignOut,
      )
    }

    is WorkspaceState.Deactivated -> {
      LaunchedEffect(Unit) { VisitorAlertService.stop(context) }
      AccountDeactivatedScreen(
        officeName = state.officeName,
        userEmail = currentUserEmail,
        onSignOut = handleSignOut,
      )
    }

    is WorkspaceState.Error -> {
      GlassBackdrop(modifier = Modifier.fillMaxSize()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("Unable to load workspace", style = MaterialTheme.typography.titleLarge)
            Text(
              state.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = handleSignOut) { Text("Sign out") }
          }
        }
      }
    }

    is WorkspaceState.Ready -> {
      val snapshot = state.snapshot
      val officeId = snapshot.office?.id.orEmpty()
      val availableTabs =
        remember(snapshot.activeRole, snapshot.currentStaff?.hostListed) {
          viewModel.availableTabsFor(snapshot)
        }
      val defaultTab = availableTabs.first()
      val activeTab =
        selectedTabOverride?.takeIf { it in availableTabs } ?: defaultTab

      LaunchedEffect(pendingTab, availableTabs) {
        if (pendingTab != null && pendingTab in availableTabs) {
          viewModel.selectTab(pendingTab)
          onConsumedPendingTab()
        }
      }

      if (activeTab != defaultTab) {
        BackHandler { viewModel.selectTab(defaultTab) }
      }

      // Notification permissions & background service state
      var notificationsEnabled by remember {
        mutableStateOf(VisitorAlertManager.hasNotificationPermission(context))
      }
      var backgroundAlertsEnabled by remember {
        mutableStateOf(VisitorAlertManager.isBackgroundAlertsPreferred(context))
      }

      val lifecycleOwner = LocalLifecycleOwner.current
      DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) {
            notificationsEnabled = VisitorAlertManager.hasNotificationPermission(context)
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

      LaunchedEffect(notificationsEnabled, backgroundAlertsEnabled, officeId, currentUserEmail, snapshot.activeRole) {
        if (notificationsEnabled && backgroundAlertsEnabled && officeId.isNotBlank()) {
          VisitorAlertService.start(
            context = context,
            officeId = officeId,
            userEmail = currentUserEmail,
            role = snapshot.activeRole,
          )
        }
      }

      val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
          notificationsEnabled = granted || VisitorAlertManager.hasNotificationPermission(context)
          if (notificationsEnabled) {
            VisitorAlertManager.setBackgroundAlertsPreferred(context, true)
            backgroundAlertsEnabled = true
            VisitorAlertManager.sendTestAlert(context)
            if (officeId.isNotBlank()) {
              VisitorAlertService.start(
                context = context,
                officeId = officeId,
                userEmail = currentUserEmail,
                role = snapshot.activeRole,
              )
            }
          }
        }

      val turnOnAlerts: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          !VisitorAlertManager.hasNotificationPermission(context)
        ) {
          permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          notificationsEnabled = VisitorAlertManager.hasNotificationPermission(context)
          VisitorAlertManager.setBackgroundAlertsPreferred(context, true)
          backgroundAlertsEnabled = true
          VisitorAlertManager.sendTestAlert(context)
          if (officeId.isNotBlank()) {
            VisitorAlertService.start(
              context = context,
              officeId = officeId,
              userEmail = currentUserEmail,
              role = snapshot.activeRole,
            )
          }
        }
      }

      val toggleBackgroundAlerts: (Boolean) -> Unit = { enabled ->
        backgroundAlertsEnabled = enabled
        VisitorAlertManager.setBackgroundAlertsPreferred(context, enabled)
        if (enabled && officeId.isNotBlank()) {
          VisitorAlertService.start(
            context = context,
            officeId = officeId,
            userEmail = currentUserEmail,
            role = snapshot.activeRole,
          )
        } else {
          VisitorAlertService.stop(context)
        }
      }

      val openBatterySettings: () -> Unit = {
        try {
          context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
          )
        } catch (_: Exception) {
        }
      }

      // Real-time Firestore listener alerts: triggers actionable in-app Snackbar + OS push notification
      LaunchedEffect(viewModel) {
        viewModel.visitAlerts.collect { event ->
          VisitorAlertManager.dispatchSystemNotification(context, event)
          scope.launch {
            val result =
              snackbarHostState.showSnackbar(
                message = event.snackbarMessage,
                actionLabel = event.actionLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
              )
            if (result == SnackbarResult.ActionPerformed) {
              viewModel.selectTab(event.targetTab)
            }
          }
        }
      }

      val myWaitingCount =
        remember(snapshot.visits, currentUserEmail) {
          val clean = currentUserEmail.trim().lowercase()
          snapshot.visits.count {
            it.hostEmail.trim().lowercase() == clean &&
              it.parsedStatus() == VisitStatus.WAITING
          }
        }

      val hostBoard =
        remember(snapshot.activeRole, snapshot.staff, snapshot.visits, nowMillis) {
          if (snapshot.activeRole == StaffRole.HOST) emptyList()
          else viewModel.buildHostBoard(snapshot, nowMillis)
        }

      val staffDisplay =
        snapshot.currentStaff?.shownAs?.ifBlank { snapshot.currentStaff.fullName }
          ?: currentUserDisplayName.ifBlank { currentUserEmail.substringBefore("@") }

      var showSettingsSheet by remember { mutableStateOf(false) }

      if (showSettingsSheet) {
        AppSettingsBottomSheet(
          themeMode = themeMode,
          notificationsEnabled = notificationsEnabled,
          backgroundServiceRunning = backgroundAlertsEnabled,
          userEmail = currentUserEmail,
          roleLabel = snapshot.activeRole.label,
          onDismiss = { showSettingsSheet = false },
          onSelectTheme = onSelectTheme,
          onTurnOnAlerts = turnOnAlerts,
          onToggleBackgroundAlerts = toggleBackgroundAlerts,
          onOpenBatterySettings = openBatterySettings,
          onSendTestAlert = { VisitorAlertManager.sendTestAlert(context) },
          onSignOut = handleSignOut,
        )
      }

      GlassBackdrop(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
          val useRail = maxWidth >= 840.dp

          Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
              SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                  snackbarData = data,
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  actionColor = ArchAmberBright,
                  dismissActionContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            },
            topBar = {
              ArchPointHeaderBar(
                staffDisplayName = staffDisplay,
                role = snapshot.activeRole,
                officeName = snapshot.office?.name.orEmpty(),
                onOpenProfile = { viewModel.selectTab(AppTab.PROFILE) },
                onOpenSettings = { showSettingsSheet = true },
              )
            },
            bottomBar = {
              if (!useRail) {
                ArchPointBottomNav(
                  tabs = availableTabs,
                  selectedTab = activeTab,
                  myWaitingCount = myWaitingCount,
                  onSelectTab = { viewModel.selectTab(it) },
                )
              }
            },
          ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
              if (useRail) {
                ArchPointNavRail(
                  tabs = availableTabs,
                  selectedTab = activeTab,
                  myWaitingCount = myWaitingCount,
                  onSelectTab = { viewModel.selectTab(it) },
                )
              }

              Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                if (!notificationsEnabled && activeTab != AppTab.PROFILE) {
                  CompactAlertsPromptBanner(onTurnOnAlerts = turnOnAlerts)
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                  when (activeTab) {
                    AppTab.RECEPTION ->
                      ReceptionScreen(
                        snapshot = snapshot,
                        form = receptionForm,
                        searchState = visitorSearch,
                        cardScanState = cardScanState,
                        hostBoard = hostBoard,
                        nowMillis = nowMillis,
                        appointments = snapshot.appointments,
                        isBusy = isBusy,
                        onSearchQueryChanged = { viewModel.onVisitorSearchQueryChanged(officeId, it) },
                        onTriggerSearch = { viewModel.triggerVisitorSearch(officeId) },
                        onClearSearch = { viewModel.clearVisitorSearch() },
                        onScanCardUri = { uri ->
                          viewModel.scanVisitorCard(
                            context = context,
                            imageUri = uri,
                            profiles = snapshot.visitorProfiles,
                            hosts = hostBoard.map { item -> item.staff },
                          )
                        },
                        onClearCardScan = { viewModel.clearCardScan() },
                        onMobileChanged = {
                          viewModel.onMobileChanged(
                            input = it,
                            profiles = snapshot.visitorProfiles,
                            hosts = hostBoard.map { item -> item.staff },
                          )
                        },
                        onNameChanged = { viewModel.onNameChanged(it) },
                        onCompanyChanged = { viewModel.onCompanyChanged(it) },
                        onEmailChanged = { viewModel.onVisitorEmailChanged(it) },
                        onDobChanged = { viewModel.onVisitorDobChanged(it) },
                        onRemarkChanged = { viewModel.onRemarkChanged(it) },
                        onReasonSelected = { viewModel.onReasonSelected(it) },
                        onHostSelected = { viewModel.onHostSelected(it) },
                        onApplySuggestion = {
                          viewModel.applySuggestedProfile(
                            profile = it,
                            hosts = hostBoard.map { item -> item.staff },
                          )
                        },
                        onClearReturningMatch = { viewModel.clearReturningMatch() },
                        onClearForm = {
                          val defaultReason = snapshot.reasons.firstOrNull { it.shown }?.label ?: "Meeting"
                          val defaultHost = hostBoard.firstOrNull()?.staff?.id.orEmpty()
                          viewModel.clearReceptionForm(defaultReason, defaultHost)
                        },
                        onCheckIn = { viewModel.checkInVisitor(snapshot) },
                        onRemind = { visit -> viewModel.remindHost(officeId, visit) },
                        onSendIn = { visit -> viewModel.sendVisitorIn(officeId, visit, "Sent in") },
                        onMarkDone = { visit -> viewModel.markMeetingDone(officeId, visit) },
                        onSaveAppointment = { existing, vName, vMob, vComp, vEmail, purpose, loc, dateIso, timeLbl, notes, host ->
                          viewModel.saveAppointment(
                            snapshot = snapshot,
                            existing = existing,
                            visitorName = vName,
                            visitorMobile = vMob,
                            visitorCompany = vComp,
                            visitorEmail = vEmail,
                            purpose = purpose,
                            location = loc,
                            scheduledDateIso = dateIso,
                            scheduledTimeLabel = timeLbl,
                            notes = notes,
                            assignedHostStaff = host,
                          )
                        },
                        onCheckInAppointment = { appt ->
                          viewModel.checkInFromAppointment(snapshot, appt)
                        },
                        onUpdateAppointmentStatus = { appt, newStatus ->
                          viewModel.updateAppointmentStatus(snapshot, appt, newStatus)
                        },
                      )

                    AppTab.MY_VISITORS ->
                      HostDashboard(
                        currentStaff = snapshot.currentStaff,
                        visits = snapshot.visits,
                        userEmail = currentUserEmail,
                        nowMillis = nowMillis,
                        inFlightVisitIds = inFlightVisitIds,
                        appointments = snapshot.appointments,
                        isBusy = isBusy,
                        onUpdateAvailability = { availability ->
                          viewModel.updateOwnAvailabilityStatus(snapshot, availability)
                        },
                        onSendIn = { visit, note ->
                          viewModel.sendVisitorIn(officeId, visit, note)
                        },
                        onSendWaitNote = { visit, note ->
                          viewModel.askVisitorToWait(officeId, visit, note)
                        },
                        onNotToday = { visit, reasonNote ->
                          viewModel.denyVisitorToday(officeId, visit, reasonNote)
                        },
                        onMeetingDone = { visit -> viewModel.markMeetingDone(officeId, visit) },
                        onSaveAppointment = { existing, vName, vMob, vComp, vEmail, purpose, loc, dateIso, timeLbl, notes, host ->
                          viewModel.saveAppointment(
                            snapshot = snapshot,
                            existing = existing,
                            visitorName = vName,
                            visitorMobile = vMob,
                            visitorCompany = vComp,
                            visitorEmail = vEmail,
                            purpose = purpose,
                            location = loc,
                            scheduledDateIso = dateIso,
                            scheduledTimeLabel = timeLbl,
                            notes = notes,
                            assignedHostStaff = host,
                          )
                        },
                        onUpdateAppointmentStatus = { appt, newStatus ->
                          viewModel.updateAppointmentStatus(snapshot, appt, newStatus)
                        },
                      )

                    AppTab.VISITOR_LOG ->
                      VisitorLogScreen(
                        visits = snapshot.visits,
                        staff = snapshot.staff,
                        nowMillis = nowMillis,
                        onToastMessage = { msg ->
                          viewModel.selectTab(AppTab.VISITOR_LOG)
                          android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT)
                            .show()
                        },
                      )

                    AppTab.PR ->
                      PRScreen(
                        snapshot = snapshot,
                        nowMillis = nowMillis,
                        isBusy = isBusy,
                        onSaveCampaign = { existing, title, channel, st, targetDate, location, audience, notes, owner ->
                          viewModel.savePrCampaign(
                            snapshot = snapshot,
                            existing = existing,
                            title = title,
                            channel = channel,
                            status = st,
                            targetDateLabel = targetDate,
                            location = location,
                            audienceSummary = audience,
                            notes = notes,
                            ownerStaff = owner,
                          )
                        },
                        onAdvanceCampaignStatus = { campaign ->
                          viewModel.advancePrCampaignStatus(snapshot, campaign)
                        },
                        onDeleteCampaign = { campaign ->
                          viewModel.deletePrCampaign(snapshot, campaign)
                        },
                        onSaveContact = { existing, name, org, desig, tier, mobile, email, followUp, nextDate, photoUrl, dob, anniv, giftPref, city, notes, owner ->
                          viewModel.savePrContact(
                            snapshot = snapshot,
                            existing = existing,
                            name = name,
                            organization = org,
                            designation = desig,
                            tier = tier,
                            mobile = mobile,
                            email = email,
                            followUpStatus = followUp,
                            nextFollowUpDate = nextDate,
                            notes = notes,
                            ownerStaff = owner,
                            photoUrl = photoUrl,
                            dob = dob,
                            anniversaryDate = anniv,
                            specialGiftPreference = giftPref,
                            cityOrAddress = city,
                          )
                        },
                        onMarkWished = { contact ->
                          viewModel.markPrContactWished(snapshot, contact)
                        },
                        onCycleContactFollowUp = { contact ->
                          viewModel.cyclePrContactFollowUp(snapshot, contact)
                        },
                        onDeleteContact = { contact ->
                          viewModel.deletePrContact(snapshot, contact)
                        },
                        onPromoteVisitor = { profile, tier ->
                          viewModel.promoteVisitorToPrContact(snapshot, profile, tier)
                        },
                        onPromoteHostClient = { client, tier ->
                          viewModel.promoteHostClientToPrContact(snapshot, client, tier)
                        },
                        onSendContactToCheckIn = { contact ->
                          viewModel.sendPrContactToReceptionCheckIn(snapshot, contact)
                        },
                        onSaveGift = { existing, person, company, item, dir, st, occasion, dateLabel, notes, owner ->
                          viewModel.savePrGift(
                            snapshot = snapshot,
                            existing = existing,
                            recipientOrSender = person,
                            company = company,
                            itemTitle = item,
                            direction = dir,
                            status = st,
                            occasion = occasion,
                            dateLabel = dateLabel,
                            notes = notes,
                            ownerStaff = owner,
                          )
                        },
                        onAdvanceGiftStatus = { gift ->
                          viewModel.advancePrGiftStatus(snapshot, gift)
                        },
                        onDeleteGift = { gift ->
                          viewModel.deletePrGift(snapshot, gift)
                        },
                      )

                    AppTab.ADMIN ->
                      AdminScreen(
                        snapshot = snapshot,
                        isBusy = isBusy,
                        onSaveStaff = { edited -> viewModel.updateStaffMember(snapshot, edited) },
                        onAddStaff = { fullName, shownAs, email, designation, department, floor, cabin, otherLocation, requiredThings, notes, role, hostListed, photoUrl ->
                          viewModel.addStaffMember(
                            snapshot = snapshot,
                            fullName = fullName,
                            shownAs = shownAs,
                            email = email,
                            designation = designation,
                            department = department,
                            floor = floor,
                            cabin = cabin,
                            otherLocation = otherLocation,
                            requiredThings = requiredThings,
                            notes = notes,
                            role = role,
                            hostListed = hostListed,
                            photoUrl = photoUrl,
                          )
                        },
                        onToggleReason = { reason -> viewModel.toggleReasonShown(officeId, reason) },
                        onAddReason = { label -> viewModel.addReason(snapshot, label) },
                        onSyncArchPointData = { viewModel.syncArchPointSeedData(snapshot) },
                      )

                    AppTab.PROFILE ->
                      ProfileScreen(
                        snapshot = snapshot,
                        userEmail = currentUserEmail,
                        themeMode = themeMode,
                        notificationsEnabled = notificationsEnabled,
                        backgroundServiceRunning = backgroundAlertsEnabled,
                        isBusy = isBusy,
                        onSelectTheme = onSelectTheme,
                        onSaveOwnProfile = { fullName, shownAs, designation, department, photoUrl, floor, cabin, otherLocation, requiredThings, notes ->
                          viewModel.updateOwnProfile(
                            snapshot = snapshot,
                            fullName = fullName,
                            shownAs = shownAs,
                            designation = designation,
                            department = department,
                            photoUrl = photoUrl,
                            floor = floor,
                            cabin = cabin,
                            otherLocation = otherLocation,
                            requiredThings = requiredThings,
                            notes = notes,
                          )
                        },
                        onOpenSettings = { showSettingsSheet = true },
                        onTurnOnAlerts = turnOnAlerts,
                        onToggleBackgroundAlerts = toggleBackgroundAlerts,
                        onOpenBatterySettings = openBatterySettings,
                        onSendTestAlert = { VisitorAlertManager.sendTestAlert(context) },
                        onSignOut = handleSignOut,
                      )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
