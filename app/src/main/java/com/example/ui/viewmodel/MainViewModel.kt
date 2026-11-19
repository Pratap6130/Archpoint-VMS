package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alerts.InAppAlertDedupStore
import com.example.alerts.VisitAlertEvent
import com.example.alerts.VisitorAlertManager
import com.example.data.ai.ScannedVisitorCard
import com.example.data.ai.VisitorCardScanner
import com.example.data.calendar.CalendarApiResult
import com.example.data.calendar.CalendarAuthState
import com.example.data.calendar.GoogleCalendarAuthHelper
import com.example.data.calendar.GoogleCalendarEventItem
import com.example.data.calendar.GoogleCalendarService
import com.example.data.model.AppTab
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.AvailabilityTone
import com.example.data.model.HostAvailability
import com.example.data.model.Office
import com.example.data.model.OfficeMembership
import com.example.data.model.PRCampaign
import com.example.data.model.PRCampaignStatus
import com.example.data.model.PRChannel
import com.example.data.model.PRContact
import com.example.data.model.PRContactTier
import com.example.data.model.PRFollowUpStatus
import com.example.data.model.PRGift
import com.example.data.model.PRGiftDirection
import com.example.data.model.PRGiftStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.UiState
import com.example.data.model.Visit
import com.example.data.model.VisitReason
import com.example.data.model.VisitStatus
import com.example.data.model.VisitorProfile
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.VisitorDeskRepository
import com.example.data.selfcheckin.SelfCheckInPayload
import com.example.data.selfcheckin.SelfCheckInServerConfig
import com.example.data.selfcheckin.SelfCheckInWebServer
import com.example.data.selfcheckin.VisitorQrCodec
import com.example.util.DeskFormatters
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class HostBoardItem(
  val staff: StaffMember,
  val inMeetingVisit: Visit?,
  val waitingCount: Int,
  val meetingMinutes: Long,
)

data class HostClientSummary(
  val key: String,
  val name: String,
  val company: String,
  val mobile: String,
  val email: String,
  val jobProfile: String,
  val visitCount: Int,
  val completedCount: Int,
  val lastVisitedAtMillis: Long,
  val lastReason: String,
  val lastRemark: String,
  val lastStatus: VisitStatus,
)

data class ReceptionFormState(
  val mobile: String = "",
  val name: String = "",
  val company: String = "",
  val email: String = "",
  val dob: String = "",
  val remark: String = "",
  val selectedReason: String = "",
  val selectedHostId: String = "",
  val matchedProfile: VisitorProfile? = null,
  val suppressedAutoMatchMobile: String = "",
  val isSubmitting: Boolean = false,
)

data class VisitorSearchState(
  val query: String = "",
  val isSearching: Boolean = false,
  val results: List<VisitorProfile> = emptyList(),
  val hasSearched: Boolean = false,
  val errorMessage: String? = null,
)

data class PRMetrics(
  val activeCampaigns: Int = 0,
  val liveCampaigns: Int = 0,
  val totalContacts: Int = 0,
  val dueFollowUps: Int = 0,
  val pendingGifts: Int = 0,
  val upcomingBirthdays: Int = 0,
)

data class DeskSnapshot(
  val office: Office?,
  val staff: List<StaffMember>,
  val reasons: List<VisitReason>,
  val visits: List<Visit>,
  val visitorProfiles: List<VisitorProfile>,
  val currentStaff: StaffMember?,
  val activeRole: StaffRole,
  val isDeactivated: Boolean,
  val prCampaigns: List<PRCampaign> = emptyList(),
  val prContacts: List<PRContact> = emptyList(),
  val prGifts: List<PRGift> = emptyList(),
  val appointments: List<Appointment> = emptyList(),
)

data class GoogleCalendarUiState(
  val isConnected: Boolean = false,
  val isConnecting: Boolean = false,
  val isSyncing: Boolean = false,
  val connectedEmail: String = "",
  val events: List<GoogleCalendarEventItem> = emptyList(),
  val lastSyncedMillis: Long = 0L,
  val errorMessage: String? = null,
  val pendingConsentIntentSender: IntentSender? = null,
)

sealed interface CardScanState {
  data object Idle : CardScanState
  data class Scanning(val preview: ImageBitmap?) : CardScanState
  data class Success(val card: ScannedVisitorCard, val preview: ImageBitmap?) : CardScanState
  data class Error(val message: String, val preview: ImageBitmap?) : CardScanState
}

data class SelfCheckInSubmissionRecord(
  val id: String,
  val payload: SelfCheckInPayload,
  val receivedAtMillis: Long,
  val autoCheckedIn: Boolean,
  val assignedHostName: String,
  val statusLabel: String,
)

data class SelfCheckInHubState(
  val isServerRunning: Boolean = false,
  val serverUrl: String = "",
  val localTestUrl: String = "",
  val isLanAvailable: Boolean = false,
  val autoCheckInOnSubmit: Boolean = true,
  val recentSubmissions: List<SelfCheckInSubmissionRecord> = emptyList(),
  val serverError: String? = null,
)

data class VisitorKioskState(
  val isActive: Boolean = false,
  val isSubmitting: Boolean = false,
  val isScanningCard: Boolean = false,
  val mobile: String = "",
  val name: String = "",
  val company: String = "",
  val email: String = "",
  val dob: String = "",
  val remark: String = "",
  val selectedReason: String = "",
  val selectedHostId: String = "",
  val matchedProfile: VisitorProfile? = null,
  val suppressedAutoMatchMobile: String = "",
  val completedVisitorName: String = "",
  val completedHostName: String = "",
  val completedHostCabin: String = "",
  val completedPassQrText: String = "",
)

sealed interface WorkspaceState {
  data object Loading : WorkspaceState
  data object NeedsSetup : WorkspaceState
  data class AccessNotAssigned(val userEmail: String) : WorkspaceState
  data class Deactivated(val officeName: String) : WorkspaceState
  data class Ready(val snapshot: DeskSnapshot) : WorkspaceState
  data class Error(val message: String) : WorkspaceState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
  private val repository: VisitorDeskRepository,
  private val preferences: ThemePreferences,
  val currentUserId: String,
  val currentUserEmail: String,
  val currentUserDisplayName: String,
  private val cardScanner: VisitorCardScanner = VisitorCardScanner(),
) : ViewModel() {

  private val normalizedEmail = currentUserEmail.trim().lowercase()

  private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
  val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

  private val _selectedTab = MutableStateFlow<AppTab?>(null)
  val selectedTab: StateFlow<AppTab?> = _selectedTab.asStateFlow()

  private val _receptionForm = MutableStateFlow(ReceptionFormState())
  val receptionForm: StateFlow<ReceptionFormState> = _receptionForm.asStateFlow()

  private val _visitorSearch = MutableStateFlow(VisitorSearchState())
  val visitorSearch: StateFlow<VisitorSearchState> = _visitorSearch.asStateFlow()

  private val _cardScanState = MutableStateFlow<CardScanState>(CardScanState.Idle)
  val cardScanState: StateFlow<CardScanState> = _cardScanState.asStateFlow()

  private var visitorSearchJob: Job? = null
  private var cardScanJob: Job? = null

  private val _isSettingUpWorkspace = MutableStateFlow(false)
  val isSettingUpWorkspace: StateFlow<Boolean> = _isSettingUpWorkspace.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _pendingAvailability = MutableStateFlow<HostAvailability?>(null)
  private val _pendingVisitOverrides = MutableStateFlow<Map<String, Visit>>(emptyMap())
  private val _inFlightVisitIds = MutableStateFlow<Set<String>>(emptySet())
  val inFlightVisitIds: StateFlow<Set<String>> = _inFlightVisitIds.asStateFlow()

  private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 16)
  val feedback = _feedback.asSharedFlow()

  private val _visitAlerts = MutableSharedFlow<VisitAlertEvent>(extraBufferCapacity = 32)
  val visitAlerts = _visitAlerts.asSharedFlow()

  private val calendarService = GoogleCalendarService()
  private var calendarAccessToken: String? = null
  private var silentCalendarChecked = false
  private var pendingSyncSnapshot: DeskSnapshot? = null
  private var pendingSingleAppointment: Appointment? = null

  private val _googleCalendarState = MutableStateFlow(GoogleCalendarUiState())
  val googleCalendarState: StateFlow<GoogleCalendarUiState> = _googleCalendarState.asStateFlow()

  private val selfCheckInWebServer = SelfCheckInWebServer()
  private val _selfCheckInHub = MutableStateFlow(SelfCheckInHubState())
  val selfCheckInHub: StateFlow<SelfCheckInHubState> = _selfCheckInHub.asStateFlow()

  private val _kioskState = MutableStateFlow(VisitorKioskState())
  val kioskState: StateFlow<VisitorKioskState> = _kioskState.asStateFlow()

  @Volatile
  private var latestReadySnapshot: DeskSnapshot? = null

  private var lastObservedOfficeId: String? = null
  private var lastVisitsById: Map<String, Visit>? = null
  private val autoSeedCheckedOffices = mutableSetOf<String>()

  init {
    viewModelScope.launch {
      while (isActive) {
        delay(10_000L)
        _nowMillis.value = System.currentTimeMillis()
      }
    }
  }

  private fun <T> Flow<T>.unwrapToUiState(label: String): Flow<UiState<T>> =
    this.map<T, UiState<T>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load $label"))
      }

  val workspaceState: StateFlow<WorkspaceState> =
    repository
      .observeMemberships(normalizedEmail)
      .unwrapToUiState("office memberships")
      .flatMapLatest { membershipState ->
        when (membershipState) {
          is UiState.Loading -> flowOf(WorkspaceState.Loading)
          is UiState.Error -> flowOf(WorkspaceState.Error(membershipState.message))
          is UiState.Success -> {
            val activeMembership =
              membershipState.data.firstOrNull { it.activeAccount }
                ?: membershipState.data.firstOrNull()
            if (activeMembership == null) {
              if (isBootstrapAdminEmail(normalizedEmail)) {
                flowOf(WorkspaceState.NeedsSetup)
              } else {
                flowOf(WorkspaceState.AccessNotAssigned(normalizedEmail))
              }
            } else if (!activeMembership.activeAccount) {
              flowOf(WorkspaceState.Deactivated(activeMembership.officeName))
            } else {
              observeWorkspaceSnapshot(activeMembership)
            }
          }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = WorkspaceState.Loading,
      )

  private data class CoreStates(
    val officeSt: UiState<Office?>,
    val staffSt: UiState<List<StaffMember>>,
    val reasonsSt: UiState<List<VisitReason>>,
    val visitsSt: UiState<List<Visit>>,
    val profilesSt: UiState<List<VisitorProfile>>,
  )

  private data class PrAndApptStates(
    val campaignsSt: UiState<List<PRCampaign>>,
    val contactsSt: UiState<List<PRContact>>,
    val giftsSt: UiState<List<PRGift>>,
    val appointmentsSt: UiState<List<Appointment>>,
  )

  private fun observeWorkspaceSnapshot(membership: OfficeMembership): Flow<WorkspaceState> {
    val officeId = membership.officeId
    val role = membership.parsedRole()
    val isHostOnly = role == StaffRole.HOST
    val isReceptionOrAdmin = role == StaffRole.ADMIN || role == StaffRole.RECEPTION

    val officeFlow = repository.observeOffice(officeId).unwrapToUiState("office")
    val staffFlow =
      if (isHostOnly) {
        repository
          .observeOwnStaffProfile(officeId, membership.staffId, normalizedEmail)
          .unwrapToUiState("host profile")
      } else {
        repository.observeStaff(officeId, normalizedEmail).unwrapToUiState("staff")
      }
    val reasonsFlow =
      if (isHostOnly) {
        flowOf(UiState.Success(emptyList()))
      } else {
        repository.observeReasons(officeId, normalizedEmail).unwrapToUiState("reasons")
      }
    val visitsFlow =
      if (isHostOnly) {
        repository
          .observeHostVisitorNotifications(officeId, normalizedEmail)
          .unwrapToUiState("host visitor notifications")
      } else {
        repository.observeVisits(officeId, normalizedEmail).unwrapToUiState("visits")
      }
    val profilesFlow =
      if (isReceptionOrAdmin) {
        repository.observeVisitorProfiles(officeId, normalizedEmail).unwrapToUiState("visitor profiles")
      } else {
        flowOf(UiState.Success(emptyList()))
      }

    val prCampaignsFlow =
      repository.observePrCampaigns(officeId, normalizedEmail, hostOnly = isHostOnly)
        .unwrapToUiState("PR campaigns")
    val prContactsFlow =
      repository.observePrContacts(officeId, normalizedEmail, hostOnly = isHostOnly)
        .unwrapToUiState("PR contacts")
    val prGiftsFlow =
      repository.observePrGifts(officeId, normalizedEmail, hostOnly = isHostOnly)
        .unwrapToUiState("PR gifts")
    val appointmentsFlow =
      repository.observeAppointments(officeId, normalizedEmail, hostOnly = isHostOnly)
        .unwrapToUiState("appointments")

    val coreFlow =
      combine(
        officeFlow,
        staffFlow,
        reasonsFlow,
        visitsFlow,
        profilesFlow,
      ) { officeSt, staffSt, reasonsSt, visitsSt, profilesSt ->
        CoreStates(officeSt, staffSt, reasonsSt, visitsSt, profilesSt)
      }

    val prAndApptFlow =
      combine(
        prCampaignsFlow,
        prContactsFlow,
        prGiftsFlow,
        appointmentsFlow,
      ) { campaignsSt, contactsSt, giftsSt, appointmentsSt ->
        PrAndApptStates(campaignsSt, contactsSt, giftsSt, appointmentsSt)
      }

    return combine(
      coreFlow,
      prAndApptFlow,
      _pendingAvailability,
      _pendingVisitOverrides,
    ) { core, prAndAppt, pendingAvail, pendingVisits ->
      val officeSt = core.officeSt
      val staffSt = core.staffSt
      val reasonsSt = core.reasonsSt
      val visitsSt = core.visitsSt
      val profilesSt = core.profilesSt
      val campaignsSt = prAndAppt.campaignsSt
      val contactsSt = prAndAppt.contactsSt
      val giftsSt = prAndAppt.giftsSt
      val appointmentsSt = prAndAppt.appointmentsSt
      val firstError =
        listOf(officeSt, staffSt, reasonsSt, visitsSt, profilesSt, campaignsSt, contactsSt, giftsSt, appointmentsSt)
          .filterIsInstance<UiState.Error>()
          .firstOrNull()
      if (firstError != null) {
        return@combine WorkspaceState.Error(firstError.message)
      }
      if (
        officeSt is UiState.Loading ||
          staffSt is UiState.Loading ||
          reasonsSt is UiState.Loading ||
          visitsSt is UiState.Loading ||
          profilesSt is UiState.Loading ||
          campaignsSt is UiState.Loading ||
          contactsSt is UiState.Loading ||
          giftsSt is UiState.Loading ||
          appointmentsSt is UiState.Loading
      ) {
        return@combine WorkspaceState.Loading
      }

      val office = (officeSt as UiState.Success).data
      val rawStaff = (staffSt as UiState.Success).data
      val reasons = (reasonsSt as UiState.Success).data
      val rawVisits = (visitsSt as UiState.Success).data
      val profiles = (profilesSt as UiState.Success).data
      val prCampaigns = (campaignsSt as UiState.Success).data
      val prContacts = (contactsSt as UiState.Success).data
      val prGifts = (giftsSt as UiState.Success).data
      val rawAppointments = (appointmentsSt as UiState.Success).data

      val staff =
        if (pendingAvail != null) {
          rawStaff.map { member ->
            if (member.email.equals(normalizedEmail, ignoreCase = true)) {
              member.copy(availabilityStatus = pendingAvail.wireValue)
            } else {
              member
            }
          }
        } else {
          rawStaff
        }

      val mergedVisits =
        if (pendingVisits.isEmpty()) {
          rawVisits
        } else {
          rawVisits.map { visit -> pendingVisits[visit.id] ?: visit }
        }

      val me = staff.firstOrNull { it.email.equals(normalizedEmail, ignoreCase = true) }
      val isOfficeOwner =
        !office?.ownerEmail.isNullOrBlank() &&
          normalizedEmail.equals(office?.ownerEmail, ignoreCase = true)
      if (me == null && !isOfficeOwner) {
        return@combine WorkspaceState.AccessNotAssigned(normalizedEmail)
      }
      if (me != null && !me.activeAccount) {
        return@combine WorkspaceState.Deactivated(office?.name ?: membership.officeName)
      }
      if (me != null && me.uid.isBlank()) {
        viewModelScope.launch { repository.claimStaffUidIfNeeded(officeId, me) }
      }

      val resolvedRole = me?.parsedRole() ?: role
      val isReceptionRole = resolvedRole == StaffRole.RECEPTION
      val scopedVisits =
        if (resolvedRole == StaffRole.HOST) {
          filterHostSpecificVisits(
            visits = mergedVisits,
            userEmail = normalizedEmail,
            staffId = me?.id,
          )
        } else {
          mergedVisits
        }
      val currentVisitsMap = rawVisits.associateBy { it.id }
      if (lastObservedOfficeId != officeId) {
        lastObservedOfficeId = officeId
        lastVisitsById = currentVisitsMap
      } else {
        val prevMap = lastVisitsById
        if (prevMap != null) {
          rawVisits.forEach { visit ->
            val event =
              VisitorAlertManager.detectVisitAlert(
                officeId = officeId,
                userEmail = normalizedEmail,
                isReception = isReceptionRole,
                prev = prevMap[visit.id],
                curr = visit,
              )
            if (event != null && InAppAlertDedupStore.shouldDispatch(event.key)) {
              _visitAlerts.tryEmit(event)
            }
          }
        }
        lastVisitsById = currentVisitsMap
      }

      val scopedCampaigns =
        if (resolvedRole == StaffRole.HOST) {
          filterHostSpecificPrCampaigns(prCampaigns, normalizedEmail)
        } else {
          prCampaigns
        }
      val scopedContacts =
        if (resolvedRole == StaffRole.HOST) {
          filterHostSpecificPrContacts(prContacts, normalizedEmail)
        } else {
          prContacts
        }
      val scopedGifts =
        if (resolvedRole == StaffRole.HOST) {
          filterHostSpecificPrGifts(prGifts, normalizedEmail)
        } else {
          prGifts
        }
      val scopedAppointments =
        if (resolvedRole == StaffRole.HOST) {
          filterHostSpecificAppointments(rawAppointments, normalizedEmail, me?.id)
        } else {
          rawAppointments
        }

      val snapshot =
        DeskSnapshot(
          office = office,
          staff = staff,
          reasons = reasons,
          visits = scopedVisits,
          visitorProfiles = if (resolvedRole == StaffRole.HOST) emptyList() else profiles,
          currentStaff = me,
          activeRole = resolvedRole,
          isDeactivated = false,
          prCampaigns = scopedCampaigns,
          prContacts = scopedContacts,
          prGifts = scopedGifts,
          appointments = scopedAppointments,
        )

      latestReadySnapshot = snapshot
      // Auto-migration (v3) & empty-log recovery (v4): loads the 55 ArchPoint visitor entries
      // without deleting appointments, and recovers cleanly if an earlier run left 0 visits.
      if (resolvedRole == StaffRole.ADMIN && office != null && autoSeedCheckedOffices.add(officeId)) {
        viewModelScope.launch {
          val v3Done = preferences.hasAttemptedDefaultSeed("v3_$officeId")
          val v4Recovered = preferences.hasAttemptedDefaultSeed("v4_recover_$officeId")
          val noImportedVisitsPresent = rawVisits.isEmpty() && rawVisits.none { it.id.startsWith("visit_past_") }
          if (!v3Done || (!v4Recovered && noImportedVisitsPresent)) {
            repository
              .replaceArchPointVisitorData(
                office = office,
                currentStaff = staff,
                currentReasons = reasons,
                currentProfiles = profiles,
                currentVisits = rawVisits,
              )
              .onSuccess { summary ->
                preferences.markDefaultSeedAttempted("v3_$officeId")
                preferences.markDefaultSeedAttempted("v4_recover_$officeId")
                _feedback.tryEmit(
                  "Loaded ${summary.visitsAdded} visitor records & ${summary.profilesAdded} profiles (appointments preserved)"
                )
              }
              .onFailure { err ->
                _feedback.tryEmit(err.localizedMessage ?: "Failed to update visitor records")
              }
          } else if (v3Done && !v4Recovered) {
            preferences.markDefaultSeedAttempted("v4_recover_$officeId")
          }
        }
      }

      // Ensure default reason & default host selection stay valid
      val shownReasons = reasons.filter { it.shown }
      val hosts = staff.filter { it.activeAccount && it.hostListed }
      val currentForm = _receptionForm.value
      var updatedForm = currentForm
      if (currentForm.selectedReason.isBlank() && shownReasons.isNotEmpty()) {
        updatedForm = updatedForm.copy(selectedReason = shownReasons.first().label)
      }
      if (currentForm.selectedHostId.isBlank() && hosts.isNotEmpty()) {
        updatedForm = updatedForm.copy(selectedHostId = hosts.first().id)
      }
      if (updatedForm != currentForm) {
        _receptionForm.value = updatedForm
      }

      WorkspaceState.Ready(snapshot)
    }
  }

  fun selectTab(tab: AppTab) {
    _selectedTab.value = tab
  }

  fun availableTabsFor(snapshot: DeskSnapshot): List<AppTab> =
    resolveTabsForRole(
      role = snapshot.activeRole,
      hostListed = snapshot.currentStaff?.hostListed == true,
    )

  fun initializeOfficeWorkspace(officeName: String, fullName: String, shownAs: String, cabin: String) {
    if (_isSettingUpWorkspace.value) return
    _isSettingUpWorkspace.value = true
    viewModelScope.launch {
      val result = repository.initializeOfficeWorkspace(officeName, fullName, shownAs, cabin)
      _isSettingUpWorkspace.value = false
      result.onSuccess { newOfficeId ->
        preferences.markDefaultSeedAttempted("v3_$newOfficeId")
        _feedback.tryEmit("Office workspace ready with ArchPoint team & 55 visitor records")
      }.onFailure { err ->
        _feedback.tryEmit(err.localizedMessage ?: "Failed to set up office")
      }
    }
  }

  fun syncArchPointSeedData(snapshot: DeskSnapshot) {
    val office = snapshot.office ?: return
    if (_isBusy.value) return
    _isBusy.value = true
    viewModelScope.launch {
      val result =
        repository.replaceArchPointVisitorData(
          office = office,
          currentStaff = snapshot.staff,
          currentReasons = snapshot.reasons,
          currentProfiles = snapshot.visitorProfiles,
          currentVisits = snapshot.visits,
        )
      _isBusy.value = false
      result
        .onSuccess { summary ->
          preferences.markDefaultSeedAttempted("v3_${office.id}")
          _visitorSearch.value = VisitorSearchState()
          _feedback.tryEmit(
            "Replaced ${summary.visitsRemoved} old entries with ${summary.visitsAdded} visitor records & ${summary.profilesAdded} profiles (appointments kept)"
          )
        }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to replace ArchPoint records") }
    }
  }

  // --- RECEPTION FORM HANDLERS ---

  fun onMobileChanged(input: String, profiles: List<VisitorProfile>, hosts: List<StaffMember>) {
    val digits = input.filter { it.isDigit() }.take(15)
    val current = _receptionForm.value
    if (digits.length >= 10 && digits != "919999999999" && digits != current.suppressedAutoMatchMobile) {
      val match = profiles.firstOrNull { mobileMatches(it.mobile, digits) }
      if (match != null) {
        val validHostId =
          hosts.firstOrNull { it.id == match.lastHostStaffId }?.id
            ?: current.selectedHostId
        val validReason =
          match.lastReason.takeIf { it.isNotBlank() } ?: current.selectedReason
        _receptionForm.value =
          current.copy(
            mobile = digits,
            name = match.name,
            company = match.company,
            email = match.email,
            dob = match.dob.ifBlank { current.dob },
            selectedHostId = validHostId,
            selectedReason = validReason,
            matchedProfile = match,
          )
        return
      }
    }
    _receptionForm.value =
      current.copy(
        mobile = digits,
        matchedProfile = if (current.matchedProfile?.mobile == digits) current.matchedProfile else null,
      )
  }

  fun onNameChanged(name: String) {
    _receptionForm.value = _receptionForm.value.copy(name = name)
  }

  fun onCompanyChanged(company: String) {
    _receptionForm.value = _receptionForm.value.copy(company = company)
  }

  fun onVisitorEmailChanged(email: String) {
    _receptionForm.value = _receptionForm.value.copy(email = email)
  }

  fun onVisitorDobChanged(dob: String) {
    _receptionForm.value = _receptionForm.value.copy(dob = dob)
  }

  fun onRemarkChanged(remark: String) {
    _receptionForm.value = _receptionForm.value.copy(remark = remark)
  }

  fun onReasonSelected(reason: String) {
    _receptionForm.value = _receptionForm.value.copy(selectedReason = reason)
  }

  fun onHostSelected(hostId: String) {
    _receptionForm.value = _receptionForm.value.copy(selectedHostId = hostId)
  }

  fun onVisitorSearchQueryChanged(officeId: String, query: String) {
    _visitorSearch.value = _visitorSearch.value.copy(query = query, errorMessage = null)
    visitorSearchJob?.cancel()
    val trimmed = query.trim()
    if (trimmed.length < 2) {
      _visitorSearch.value = VisitorSearchState(query = query)
      return
    }
    if (officeId.isBlank()) return
    visitorSearchJob =
      viewModelScope.launch {
        delay(280L)
        executeVisitorSearch(officeId, trimmed)
      }
  }

  fun triggerVisitorSearch(officeId: String) {
    val trimmed = _visitorSearch.value.query.trim()
    if (trimmed.isEmpty() || officeId.isBlank()) return
    visitorSearchJob?.cancel()
    visitorSearchJob =
      viewModelScope.launch {
        executeVisitorSearch(officeId, trimmed)
      }
  }

  fun clearVisitorSearch() {
    visitorSearchJob?.cancel()
    _visitorSearch.value = VisitorSearchState()
  }

  private suspend fun executeVisitorSearch(officeId: String, query: String) {
    _visitorSearch.value = _visitorSearch.value.copy(isSearching = true, errorMessage = null)
    val result = repository.searchReturningVisitors(officeId, query)
    result
      .onSuccess { matches ->
        _visitorSearch.value =
          _visitorSearch.value.copy(
            isSearching = false,
            results = matches,
            hasSearched = true,
            errorMessage = null,
          )
      }
      .onFailure { err ->
        _visitorSearch.value =
          _visitorSearch.value.copy(
            isSearching = false,
            results = emptyList(),
            hasSearched = true,
            errorMessage = err.localizedMessage ?: "Search failed",
          )
      }
  }

  fun applySuggestedProfile(profile: VisitorProfile, hosts: List<StaffMember>) {
    _receptionForm.value =
      populateFormFromProfile(
        current = _receptionForm.value,
        profile = profile,
        hosts = hosts,
      )
    clearVisitorSearch()
    clearCardScan()
    val displayName = profile.name.trim().ifBlank { "visitor" }
    _feedback.tryEmit("Filled form for $displayName")
  }

  fun clearReturningMatch() {
    val current = _receptionForm.value
    _receptionForm.value =
      current.copy(
        name = "",
        company = "",
        email = "",
        dob = "",
        remark = "",
        matchedProfile = null,
        suppressedAutoMatchMobile = current.mobile,
      )
  }

  fun scanVisitorCard(
    context: Context,
    imageUri: Uri,
    profiles: List<VisitorProfile>,
    hosts: List<StaffMember>,
  ) {
    cardScanJob?.cancel()
    cardScanJob =
      viewModelScope.launch {
        var preview: ImageBitmap? = null
        try {
          _cardScanState.value = CardScanState.Scanning(preview = null)
          val bitmap = cardScanner.loadScaledCardBitmap(context, imageUri)
          preview = bitmap.asImageBitmap()
          _cardScanState.value = CardScanState.Scanning(preview = preview)

          val result = cardScanner.extractFromBitmap(bitmap)
          result
            .onSuccess { extracted ->
              _receptionForm.value =
                mergeScannedCardIntoForm(
                  current = _receptionForm.value,
                  extracted = extracted,
                  profiles = profiles,
                  hosts = hosts,
                )
              _cardScanState.value = CardScanState.Success(extracted, preview)
              val label = extracted.name.ifBlank { extracted.company.ifBlank { "visitor card" } }
              _feedback.tryEmit("Filled form from card: $label")
            }
            .onFailure { err ->
              val msg = err.localizedMessage ?: "Failed to read details from the card image."
              _cardScanState.value = CardScanState.Error(msg, preview)
            }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Failed to load or scan visitor card image", e)
          _cardScanState.value =
            CardScanState.Error(
              e.localizedMessage ?: "Could not open the selected card image.",
              preview,
            )
        }
      }
  }

  fun clearCardScan() {
    cardScanJob?.cancel()
    _cardScanState.value = CardScanState.Idle
  }

  fun clearReceptionForm(defaultReason: String, defaultHostId: String) {
    clearVisitorSearch()
    clearCardScan()
    _receptionForm.value =
      ReceptionFormState(
        selectedReason = defaultReason,
        selectedHostId = defaultHostId,
      )
  }

  fun checkInVisitor(snapshot: DeskSnapshot) {
    val office = snapshot.office ?: return
    val form = _receptionForm.value
    if (form.isSubmitting) return
    val validationError = validateReceptionCheckIn(form, snapshot.staff)
    if (validationError != null) {
      _feedback.tryEmit(validationError)
      return
    }
    val host = snapshot.staff.first { it.id == form.selectedHostId && it.activeAccount }

    _receptionForm.value = form.copy(isSubmitting = true)
    viewModelScope.launch {
      val matchedForPhone =
        form.matchedProfile?.takeIf {
          it.id.isNotBlank() && (it.mobile == form.mobile.filter { ch -> ch.isDigit() } || it.mobile.isBlank())
        }
      val result =
        repository.checkInVisitor(
          office = office,
          mobile = form.mobile,
          name = form.name,
          company = form.company,
          email = form.email,
          dob = form.dob,
          reason = form.selectedReason,
          remark = form.remark,
          host = host,
          existingProfile = matchedForPhone,
        )
      result
        .onSuccess {
          val defaultReason = snapshot.reasons.firstOrNull { it.shown }?.label ?: "Meeting"
          val defaultHost =
            snapshot.staff.firstOrNull { it.activeAccount && it.hostListed }?.id ?: host.id
          clearCardScan()
          _receptionForm.value =
            ReceptionFormState(
              selectedReason = defaultReason,
              selectedHostId = defaultHost,
            )
          if (!host.email.equals(normalizedEmail, ignoreCase = true)) {
            _feedback.tryEmit("Checked in ${form.name.trim()} for ${host.shownAs}")
          }
        }
        .onFailure { err ->
          _receptionForm.value = _receptionForm.value.copy(isSubmitting = false)
          _feedback.tryEmit(err.localizedMessage ?: "Check-in failed")
        }
    }
  }

  // --- SELF CHECK-IN QR FORM, SCANNER & KIOSK MODE ---

  fun toggleSelfCheckInServer(snapshot: DeskSnapshot) {
    if (_selfCheckInHub.value.isServerRunning) {
      stopSelfCheckInServer()
      return
    }
    val office = snapshot.office ?: return
    val result =
      selfCheckInWebServer.start(
        scope = viewModelScope,
        configProvider = {
          val live = latestReadySnapshot ?: snapshot
          SelfCheckInServerConfig(
            officeName = live.office?.name ?: office.name,
            hosts = live.staff,
            reasons = live.reasons,
          )
        },
        onCheckInSubmitted = { payload ->
          val live = latestReadySnapshot ?: snapshot
          handleIncomingSelfCheckIn(payload = payload, snapshot = live)
        },
      )
    result
      .onSuccess { endpoint ->
        _selfCheckInHub.value =
          _selfCheckInHub.value.copy(
            isServerRunning = true,
            serverUrl = endpoint.primaryUrl,
            localTestUrl = endpoint.localTestUrl,
            isLanAvailable = endpoint.isLanAvailable,
            serverError = null,
          )
        _feedback.tryEmit("Visitor Self Check-In QR Form active at ${endpoint.primaryUrl}")
      }
      .onFailure { err ->
        _selfCheckInHub.value =
          _selfCheckInHub.value.copy(
            isServerRunning = false,
            serverError = err.localizedMessage ?: "Unable to start Self Check-In server",
          )
        _feedback.tryEmit(err.localizedMessage ?: "Unable to start Self Check-In server")
      }
  }

  fun stopSelfCheckInServer() {
    selfCheckInWebServer.stop()
    _selfCheckInHub.value =
      _selfCheckInHub.value.copy(
        isServerRunning = false,
        serverError = null,
      )
  }

  fun setAutoCheckInOnSelfSubmit(enabled: Boolean) {
    _selfCheckInHub.value = _selfCheckInHub.value.copy(autoCheckInOnSubmit = enabled)
  }

  private suspend fun handleIncomingSelfCheckIn(
    payload: SelfCheckInPayload,
    snapshot: DeskSnapshot,
  ): Result<String> {
    val office = snapshot.office
      ?: return Result.failure(IllegalStateException("Office workspace is not ready."))
    val hosts = snapshot.staff.filter { it.activeAccount && it.hostListed }
    val defaultHostId =
      hosts.firstOrNull { it.id == _receptionForm.value.selectedHostId }?.id
        ?: hosts.firstOrNull()?.id.orEmpty()
    val resolvedHost = resolveHostFromPayload(payload, snapshot.staff, defaultHostId)
    val shouldAutoCheckIn =
      _selfCheckInHub.value.autoCheckInOnSubmit &&
        resolvedHost != null &&
        payload.name.isNotBlank() &&
        payload.mobile.filter { it.isDigit() }.length >= 10

    if (shouldAutoCheckIn && resolvedHost != null) {
      val digits = payload.mobile.filter { it.isDigit() }
      val matchedProfile =
        snapshot.visitorProfiles.firstOrNull { profile ->
          mobileMatches(profile.mobile, digits) ||
            (payload.email.isNotBlank() && profile.email.equals(payload.email, ignoreCase = true))
        }
      val resolvedReason =
        payload.reason.ifBlank {
          matchedProfile?.lastReason?.takeIf { it.isNotBlank() }
            ?: snapshot.reasons.firstOrNull { it.shown }?.label
            ?: "Meeting"
        }
      val writeResult =
        repository.checkInVisitor(
          office = office,
          mobile = digits,
          name = payload.name,
          company = payload.company.ifBlank { matchedProfile?.company.orEmpty() },
          email = payload.email.ifBlank { matchedProfile?.email.orEmpty() },
          dob = payload.dob.ifBlank { matchedProfile?.dob.orEmpty() },
          reason = resolvedReason,
          remark = payload.remark,
          host = resolvedHost,
          existingProfile = matchedProfile,
        )
      return writeResult.map {
        val hostLabel = resolvedHost.shownAs.ifBlank { resolvedHost.fullName }
        recordSelfSubmission(
          payload = payload.copy(reason = resolvedReason, hostId = resolvedHost.id, hostName = hostLabel),
          autoCheckedIn = true,
          hostName = hostLabel,
          statusLabel = "Auto-checked in",
        )
        _feedback.tryEmit("Self Check-In received: ${payload.name} → $hostLabel")
        "You are checked in with $hostLabel."
      }
    }

    withContext(Dispatchers.Main.immediate) {
      _receptionForm.value =
        mergeSelfCheckInIntoForm(
          current = _receptionForm.value,
          payload = payload,
          profiles = snapshot.visitorProfiles,
          hosts = snapshot.staff,
          reasons = snapshot.reasons,
        )
    }
    val targetHostLabel = resolvedHost?.let { it.shownAs.ifBlank { it.fullName } } ?: payload.hostName.ifBlank { "Desk" }
    recordSelfSubmission(
      payload = payload,
      autoCheckedIn = false,
      hostName = targetHostLabel,
      statusLabel = "Loaded into Form",
    )
    _feedback.tryEmit("Visitor form auto-filled from ${payload.source}: ${payload.name}")
    return Result.success("Your details have been sent to Reception.")
  }

  private fun recordSelfSubmission(
    payload: SelfCheckInPayload,
    autoCheckedIn: Boolean,
    hostName: String,
    statusLabel: String,
  ) {
    val item =
      SelfCheckInSubmissionRecord(
        id = UUID.randomUUID().toString(),
        payload = payload,
        receivedAtMillis = System.currentTimeMillis(),
        autoCheckedIn = autoCheckedIn,
        assignedHostName = hostName,
        statusLabel = statusLabel,
      )
    _selfCheckInHub.value =
      _selfCheckInHub.value.copy(
        recentSubmissions = (listOf(item) + _selfCheckInHub.value.recentSubmissions).take(25),
      )
  }

  fun applySubmissionToReceptionForm(
    record: SelfCheckInSubmissionRecord,
    snapshot: DeskSnapshot,
  ) {
    _receptionForm.value =
      mergeSelfCheckInIntoForm(
        current = _receptionForm.value,
        payload = record.payload,
        profiles = snapshot.visitorProfiles,
        hosts = snapshot.staff,
        reasons = snapshot.reasons,
      )
    _feedback.tryEmit("Filled form from ${record.payload.name.ifBlank { record.payload.source }}")
  }

  fun applyScannedQrRaw(
    rawText: String,
    snapshot: DeskSnapshot,
    autoCheckIn: Boolean = false,
  ): Boolean {
    val parsed = VisitorQrCodec.parseQrContent(rawText)
    if (parsed == null) {
      _feedback.tryEmit("QR code did not contain readable visitor contact details.")
      return false
    }
    viewModelScope.launch {
      if (autoCheckIn) {
        val office = snapshot.office ?: return@launch
        val host = resolveHostFromPayload(parsed, snapshot.staff, _receptionForm.value.selectedHostId)
        val digits = parsed.mobile.filter { it.isDigit() }
        if (host != null && parsed.name.isNotBlank() && digits.length >= 10) {
          val matched =
            snapshot.visitorProfiles.firstOrNull {
              mobileMatches(it.mobile, digits) || (parsed.email.isNotBlank() && it.email.equals(parsed.email, ignoreCase = true))
            }
          val reason =
            parsed.reason.ifBlank {
              matched?.lastReason?.takeIf { it.isNotBlank() }
                ?: snapshot.reasons.firstOrNull { it.shown }?.label
                ?: "Meeting"
            }
          repository
            .checkInVisitor(
              office = office,
              mobile = digits,
              name = parsed.name,
              company = parsed.company.ifBlank { matched?.company.orEmpty() },
              email = parsed.email.ifBlank { matched?.email.orEmpty() },
              dob = parsed.dob.ifBlank { matched?.dob.orEmpty() },
              reason = reason,
              remark = parsed.remark,
              host = host,
              existingProfile = matched,
            )
            .onSuccess {
              val hostLabel = host.shownAs.ifBlank { host.fullName }
              recordSelfSubmission(
                payload = parsed.copy(reason = reason, hostId = host.id, hostName = hostLabel),
                autoCheckedIn = true,
                hostName = hostLabel,
                statusLabel = "QR Auto Check-In",
              )
              _feedback.tryEmit("QR checked in ${parsed.name} for $hostLabel")
            }
            .onFailure { err ->
              _feedback.tryEmit(err.localizedMessage ?: "QR check-in failed")
            }
          return@launch
        }
      }

      _receptionForm.value =
        mergeSelfCheckInIntoForm(
          current = _receptionForm.value,
          payload = parsed,
          profiles = snapshot.visitorProfiles,
          hosts = snapshot.staff,
          reasons = snapshot.reasons,
        )
      val hostLabel =
        resolveHostFromPayload(parsed, snapshot.staff, _receptionForm.value.selectedHostId)
          ?.let { it.shownAs.ifBlank { it.fullName } }
          ?: parsed.hostName
      recordSelfSubmission(
        payload = parsed,
        autoCheckedIn = false,
        hostName = hostLabel,
        statusLabel = "Scanned via ${parsed.source}",
      )
      val display = parsed.name.ifBlank { parsed.mobile.ifBlank { parsed.source } }
      _feedback.tryEmit("Auto-filled form from ${parsed.source}: $display")
    }
    return true
  }

  fun scanQrFromImageUri(
    context: Context,
    imageUri: Uri,
    snapshot: DeskSnapshot,
    autoCheckIn: Boolean = false,
  ) {
    viewModelScope.launch {
      try {
        val scanner = BarcodeScanning.getClient()
        val inputImage = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, imageUri) }
        val barcodes = scanner.process(inputImage).await()
        val raw = barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
        if (raw == null) {
          _feedback.tryEmit("No QR code found in the selected image.")
        } else {
          applyScannedQrRaw(raw, snapshot, autoCheckIn)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedback.tryEmit(e.localizedMessage ?: "Failed to read QR code image.")
      }
    }
  }

  fun openVisitorKiosk(snapshot: DeskSnapshot) {
    val defaultReason = snapshot.reasons.firstOrNull { it.shown }?.label ?: "Meeting"
    val defaultHost = snapshot.staff.firstOrNull { it.activeAccount && it.hostListed }?.id.orEmpty()
    _kioskState.value =
      VisitorKioskState(
        isActive = true,
        selectedReason = defaultReason,
        selectedHostId = defaultHost,
      )
  }

  fun closeVisitorKiosk() {
    _kioskState.value = VisitorKioskState(isActive = false)
  }

  fun resetKioskForNextVisitor(snapshot: DeskSnapshot) {
    val defaultReason = snapshot.reasons.firstOrNull { it.shown }?.label ?: "Meeting"
    val defaultHost = snapshot.staff.firstOrNull { it.activeAccount && it.hostListed }?.id.orEmpty()
    _kioskState.value =
      VisitorKioskState(
        isActive = true,
        selectedReason = defaultReason,
        selectedHostId = defaultHost,
      )
  }

  fun onKioskMobileChanged(
    input: String,
    profiles: List<VisitorProfile>,
    hosts: List<StaffMember>,
  ) {
    val digits = input.filter { it.isDigit() }.take(15)
    val current = _kioskState.value
    if (digits.length >= 10 && digits != "919999999999" && digits != current.suppressedAutoMatchMobile) {
      val match = profiles.firstOrNull { mobileMatches(it.mobile, digits) }
      if (match != null) {
        val validHostId = hosts.firstOrNull { it.id == match.lastHostStaffId }?.id ?: current.selectedHostId
        val validReason = match.lastReason.takeIf { it.isNotBlank() } ?: current.selectedReason
        _kioskState.value =
          current.copy(
            mobile = digits,
            name = match.name,
            company = match.company,
            email = match.email,
            dob = match.dob.ifBlank { current.dob },
            selectedHostId = validHostId,
            selectedReason = validReason,
            matchedProfile = match,
          )
        return
      }
    }
    _kioskState.value =
      current.copy(
        mobile = digits,
        matchedProfile = if (current.matchedProfile?.let { mobileMatches(it.mobile, digits) } == true) current.matchedProfile else null,
      )
  }

  fun clearKioskReturningMatch() {
    val current = _kioskState.value
    _kioskState.value =
      current.copy(
        name = "",
        company = "",
        email = "",
        dob = "",
        remark = "",
        matchedProfile = null,
        suppressedAutoMatchMobile = current.mobile,
      )
  }

  fun onKioskNameChanged(value: String) {
    _kioskState.value = _kioskState.value.copy(name = value)
  }

  fun onKioskCompanyChanged(value: String) {
    _kioskState.value = _kioskState.value.copy(company = value)
  }

  fun onKioskEmailChanged(value: String) {
    _kioskState.value = _kioskState.value.copy(email = value)
  }

  fun onKioskDobChanged(value: String) {
    _kioskState.value = _kioskState.value.copy(dob = value)
  }

  fun onKioskRemarkChanged(value: String) {
    _kioskState.value = _kioskState.value.copy(remark = value)
  }

  fun onKioskHostSelected(hostId: String) {
    _kioskState.value = _kioskState.value.copy(selectedHostId = hostId)
  }

  fun onKioskReasonSelected(reason: String) {
    _kioskState.value = _kioskState.value.copy(selectedReason = reason)
  }

  fun applyQrToKiosk(rawText: String, snapshot: DeskSnapshot): Boolean {
    val parsed = VisitorQrCodec.parseQrContent(rawText)
    if (parsed == null) {
      _feedback.tryEmit("Could not read visitor details from that QR code.")
      return false
    }
    val merged =
      mergeSelfCheckInIntoForm(
        current =
          ReceptionFormState(
            mobile = _kioskState.value.mobile,
            name = _kioskState.value.name,
            company = _kioskState.value.company,
            email = _kioskState.value.email,
            dob = _kioskState.value.dob,
            remark = _kioskState.value.remark,
            selectedReason = _kioskState.value.selectedReason,
            selectedHostId = _kioskState.value.selectedHostId,
            matchedProfile = _kioskState.value.matchedProfile,
          ),
        payload = parsed,
        profiles = snapshot.visitorProfiles,
        hosts = snapshot.staff,
        reasons = snapshot.reasons,
      )
    _kioskState.value =
      _kioskState.value.copy(
        mobile = merged.mobile,
        name = merged.name,
        company = merged.company,
        email = merged.email,
        dob = merged.dob,
        remark = merged.remark,
        selectedReason = merged.selectedReason,
        selectedHostId = merged.selectedHostId,
        matchedProfile = merged.matchedProfile,
      )
    _feedback.tryEmit("Pre-filled from ${parsed.source}")
    return true
  }

  fun scanQrFromImageUriForKiosk(
    context: Context,
    imageUri: Uri,
    snapshot: DeskSnapshot,
  ) {
    viewModelScope.launch {
      try {
        val scanner = BarcodeScanning.getClient()
        val inputImage = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, imageUri) }
        val barcodes = scanner.process(inputImage).await()
        val raw = barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
        if (raw == null) {
          _feedback.tryEmit("No QR code found in the selected image.")
        } else {
          applyQrToKiosk(raw, snapshot)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _feedback.tryEmit(e.localizedMessage ?: "Failed to read QR code image.")
      }
    }
  }

  fun scanCardForKiosk(context: Context, imageUri: Uri, snapshot: DeskSnapshot) {
    viewModelScope.launch {
      _kioskState.value = _kioskState.value.copy(isScanningCard = true)
      try {
        val bitmap = cardScanner.loadScaledCardBitmap(context, imageUri)
        val result = cardScanner.extractFromBitmap(bitmap)
        result
          .onSuccess { extracted ->
            val merged =
              mergeScannedCardIntoForm(
                current =
                  ReceptionFormState(
                    mobile = _kioskState.value.mobile,
                    name = _kioskState.value.name,
                    company = _kioskState.value.company,
                    email = _kioskState.value.email,
                    dob = _kioskState.value.dob,
                    remark = _kioskState.value.remark,
                    selectedReason = _kioskState.value.selectedReason,
                    selectedHostId = _kioskState.value.selectedHostId,
                    matchedProfile = _kioskState.value.matchedProfile,
                  ),
                extracted = extracted,
                profiles = snapshot.visitorProfiles,
                hosts = snapshot.staff,
              )
            _kioskState.value =
              _kioskState.value.copy(
                isScanningCard = false,
                mobile = merged.mobile,
                name = merged.name,
                company = merged.company,
                email = merged.email,
                dob = merged.dob,
                remark = merged.remark,
                selectedReason = merged.selectedReason,
                selectedHostId = merged.selectedHostId,
                matchedProfile = merged.matchedProfile,
              )
            _feedback.tryEmit("Details scanned from your card")
          }
          .onFailure { err ->
            _kioskState.value = _kioskState.value.copy(isScanningCard = false)
            _feedback.tryEmit(err.localizedMessage ?: "Unable to read card")
          }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _kioskState.value = _kioskState.value.copy(isScanningCard = false)
        _feedback.tryEmit(e.localizedMessage ?: "Failed to read card image")
      }
    }
  }

  fun submitKioskCheckIn(snapshot: DeskSnapshot) {
    val office = snapshot.office ?: return
    val kiosk = _kioskState.value
    if (kiosk.isSubmitting) return
    val asForm =
      ReceptionFormState(
        mobile = kiosk.mobile,
        name = kiosk.name,
        company = kiosk.company,
        email = kiosk.email,
        dob = kiosk.dob,
        remark = kiosk.remark,
        selectedReason = kiosk.selectedReason,
        selectedHostId = kiosk.selectedHostId,
      )
    val validationError = validateReceptionCheckIn(asForm, snapshot.staff)
    if (validationError != null) {
      _feedback.tryEmit(validationError)
      return
    }
    val host = snapshot.staff.first { it.id == kiosk.selectedHostId && it.activeAccount }
    _kioskState.value = kiosk.copy(isSubmitting = true)
    viewModelScope.launch {
      val matchedForPhone =
        kiosk.matchedProfile?.takeIf {
          it.id.isNotBlank() && (it.mobile == kiosk.mobile.filter { ch -> ch.isDigit() } || it.mobile.isBlank())
        }
      val result =
        repository.checkInVisitor(
          office = office,
          mobile = kiosk.mobile,
          name = kiosk.name,
          company = kiosk.company,
          email = kiosk.email,
          dob = kiosk.dob,
          reason = kiosk.selectedReason,
          remark = kiosk.remark,
          host = host,
          existingProfile = matchedForPhone,
        )
      result
        .onSuccess {
          val hostLabel = host.shownAs.ifBlank { host.fullName }
          val cabinLabel = host.formattedSeating().ifBlank { host.cabin }
          val payload =
            SelfCheckInPayload(
              name = kiosk.name.trim(),
              mobile = kiosk.mobile.filter { it.isDigit() },
              company = kiosk.company.trim(),
              email = kiosk.email.trim(),
              dob = kiosk.dob.trim(),
              reason = kiosk.selectedReason.trim(),
              hostId = host.id,
              hostName = hostLabel,
              remark = kiosk.remark.trim(),
              source = "Tablet Kiosk",
            )
          recordSelfSubmission(
            payload = payload,
            autoCheckedIn = true,
            hostName = hostLabel,
            statusLabel = "Kiosk Self Check-In",
          )
          _kioskState.value =
            _kioskState.value.copy(
              isSubmitting = false,
              completedVisitorName = kiosk.name.trim(),
              completedHostName = hostLabel,
              completedHostCabin = cabinLabel,
              completedPassQrText = VisitorQrCodec.encodeArchPointPass(payload),
            )
        }
        .onFailure { err ->
          _kioskState.value = _kioskState.value.copy(isSubmitting = false)
          _feedback.tryEmit(err.localizedMessage ?: "Self check-in failed")
        }
    }
  }

  override fun onCleared() {
    selfCheckInWebServer.stop()
    super.onCleared()
  }

  // --- VISIT STATE ACTIONS ---

  fun remindHost(officeId: String, visit: Visit) {
    viewModelScope.launch {
      repository
        .remindHost(officeId, visit.id)
        .onSuccess { _feedback.tryEmit("Reminder sent to ${visit.hostShownAs}") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to send reminder") }
    }
  }

  fun sendVisitorIn(officeId: String, visit: Visit, note: String = "Send in") {
    if (visit.id in _inFlightVisitIds.value) return
    val cleanNote = note.trim().take(120).ifBlank { "Send in" }
    val nowTs = com.google.firebase.Timestamp.now()
    _inFlightVisitIds.value = _inFlightVisitIds.value + visit.id
    _pendingVisitOverrides.value =
      _pendingVisitOverrides.value + (
        visit.id to visit.copy(
          status = VisitStatus.IN_MEETING.wireValue,
          waitNote = cleanNote,
          meetingStartedAt = visit.meetingStartedAt ?: nowTs,
        )
      )
    viewModelScope.launch {
      repository
        .sendVisitorIn(officeId, visit.id, cleanNote)
        .onSuccess {
          _feedback.tryEmit("Sending ${visit.visitorName} in ($cleanNote)")
        }
        .onFailure {
          _feedback.tryEmit(it.localizedMessage ?: "Could not update visit")
        }
      _pendingVisitOverrides.value = _pendingVisitOverrides.value - visit.id
      _inFlightVisitIds.value = _inFlightVisitIds.value - visit.id
    }
  }

  fun askVisitorToWait(
    officeId: String,
    visit: Visit,
    note: String = "asked to wait 10 min",
  ) {
    if (visit.id in _inFlightVisitIds.value) return
    val cleanNote = note.trim().take(120).ifBlank { "asked to wait 10 min" }
    _inFlightVisitIds.value = _inFlightVisitIds.value + visit.id
    _pendingVisitOverrides.value =
      _pendingVisitOverrides.value + (visit.id to visit.copy(waitNote = cleanNote))
    viewModelScope.launch {
      repository
        .askVisitorToWait(officeId, visit.id, cleanNote)
        .onSuccess {
          _feedback.tryEmit("Sent to reception: \"$cleanNote\"")
        }
        .onFailure {
          _feedback.tryEmit(it.localizedMessage ?: "Could not send reply")
        }
      _pendingVisitOverrides.value = _pendingVisitOverrides.value - visit.id
      _inFlightVisitIds.value = _inFlightVisitIds.value - visit.id
    }
  }

  fun denyVisitorToday(
    officeId: String,
    visit: Visit,
    reasonNote: String = "can't meet today",
  ) {
    if (visit.id in _inFlightVisitIds.value) return
    val cleanNote = reasonNote.trim().take(120).ifBlank { "can't meet today" }
    val nowTs = com.google.firebase.Timestamp.now()
    _inFlightVisitIds.value = _inFlightVisitIds.value + visit.id
    _pendingVisitOverrides.value =
      _pendingVisitOverrides.value + (
        visit.id to visit.copy(
          status = VisitStatus.DENIED.wireValue,
          waitNote = cleanNote,
          endedAt = nowTs,
        )
      )
    viewModelScope.launch {
      repository
        .denyVisitorToday(officeId, visit.id, cleanNote)
        .onSuccess { _feedback.tryEmit("Marked ${visit.visitorName}: $cleanNote") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Could not update visit") }
      _pendingVisitOverrides.value = _pendingVisitOverrides.value - visit.id
      _inFlightVisitIds.value = _inFlightVisitIds.value - visit.id
    }
  }

  fun markMeetingDone(officeId: String, visit: Visit) {
    if (visit.id in _inFlightVisitIds.value) return
    val nowTs = com.google.firebase.Timestamp.now()
    _inFlightVisitIds.value = _inFlightVisitIds.value + visit.id
    _pendingVisitOverrides.value =
      _pendingVisitOverrides.value + (
        visit.id to visit.copy(
          status = VisitStatus.DONE.wireValue,
          endedAt = nowTs,
        )
      )
    viewModelScope.launch {
      repository
        .markMeetingDone(officeId, visit.id)
        .onSuccess { _feedback.tryEmit("Meeting with ${visit.visitorName} marked Done") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Could not mark done") }
      _pendingVisitOverrides.value = _pendingVisitOverrides.value - visit.id
      _inFlightVisitIds.value = _inFlightVisitIds.value - visit.id
    }
  }

  // --- ADMIN & PROFILE ACTIONS ---

  fun addStaffMember(
    snapshot: DeskSnapshot,
    fullName: String,
    shownAs: String,
    email: String,
    designation: String,
    department: String,
    floor: String,
    cabin: String,
    otherLocation: String,
    requiredThings: String,
    notes: String,
    role: StaffRole,
    hostListed: Boolean,
    photoUrl: String = "",
  ) {
    val office = snapshot.office ?: return
    if (_isBusy.value) return
    _isBusy.value = true
    viewModelScope.launch {
      val result =
        repository.addStaffMember(
          office = office,
          currentStaff = snapshot.staff,
          currentReasons = snapshot.reasons,
          currentProfiles = snapshot.visitorProfiles,
          currentVisits = snapshot.visits,
          currentPrCampaigns = snapshot.prCampaigns,
          currentPrContacts = snapshot.prContacts,
          currentPrGifts = snapshot.prGifts,
          currentAppointments = snapshot.appointments,
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
      _isBusy.value = false
      result
        .onSuccess { _feedback.tryEmit("Added $shownAs to staff directory") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to add staff member") }
    }
  }

  fun updateOwnProfile(
    snapshot: DeskSnapshot,
    fullName: String,
    shownAs: String,
    designation: String,
    department: String,
    photoUrl: String,
    floor: String,
    cabin: String,
    otherLocation: String,
    requiredThings: String,
    notes: String,
  ) {
    val officeId = snapshot.office?.id.orEmpty()
    val staffId = snapshot.currentStaff?.id.orEmpty()
    if (officeId.isBlank() || staffId.isBlank() || _isBusy.value) return
    val cleanName = fullName.trim().take(80)
    if (cleanName.isEmpty()) {
      _feedback.tryEmit("Full name cannot be empty.")
      return
    }
    _isBusy.value = true
    viewModelScope.launch {
      val result =
        repository.updateOwnProfile(
          officeId = officeId,
          staffId = staffId,
          fullName = cleanName,
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
      _isBusy.value = false
      result
        .onSuccess { _feedback.tryEmit("Saved your profile & seating") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to save profile") }
    }
  }

  fun updateOwnSeatingAndRequirements(
    snapshot: DeskSnapshot,
    floor: String,
    cabin: String,
    otherLocation: String,
    requiredThings: String,
    notes: String,
  ) {
    val officeId = snapshot.office?.id.orEmpty()
    val staffId = snapshot.currentStaff?.id.orEmpty()
    if (officeId.isBlank() || staffId.isBlank() || _isBusy.value) return
    _isBusy.value = true
    viewModelScope.launch {
      val result =
        repository.updateOwnSeatingAndRequirements(
          officeId = officeId,
          staffId = staffId,
          floor = floor,
          cabin = cabin,
          otherLocation = otherLocation,
          requiredThings = requiredThings,
          notes = notes,
        )
      _isBusy.value = false
      result
        .onSuccess { _feedback.tryEmit("Saved your seating & required items") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to save profile") }
    }
  }

  fun updateOwnAvailabilityStatus(snapshot: DeskSnapshot, availability: HostAvailability) {
    val officeId = snapshot.office?.id.orEmpty()
    val staffId = snapshot.currentStaff?.id.orEmpty()
    if (officeId.isBlank() || staffId.isBlank()) return
    if (snapshot.currentStaff?.parsedAvailability() == availability) return
    _pendingAvailability.value = availability
    viewModelScope.launch {
      repository
        .updateOwnAvailabilityStatus(
          officeId = officeId,
          staffId = staffId,
          availability = availability,
        )
        .onSuccess { _feedback.tryEmit("Status set to ${availability.label}") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Could not update status") }
      _pendingAvailability.value = null
    }
  }

  fun updateStaffMember(snapshot: DeskSnapshot, edited: StaffMember) {
    val office = snapshot.office ?: return
    if (_isBusy.value) return
    _isBusy.value = true
    viewModelScope.launch {
      val result =
        repository.updateStaffMember(
          office = office,
          currentStaff = snapshot.staff,
          currentReasons = snapshot.reasons,
          currentProfiles = snapshot.visitorProfiles,
          currentVisits = snapshot.visits,
          edited = edited,
          currentPrCampaigns = snapshot.prCampaigns,
          currentPrContacts = snapshot.prContacts,
          currentPrGifts = snapshot.prGifts,
          currentAppointments = snapshot.appointments,
        )
      _isBusy.value = false
      result
        .onSuccess { _feedback.tryEmit("Saved changes for ${edited.shownAs}") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to save staff member") }
    }
  }

  fun addReason(snapshot: DeskSnapshot, label: String) {
    val office = snapshot.office ?: return
    viewModelScope.launch {
      repository
        .addReason(office, snapshot.reasons, label)
        .onSuccess { _feedback.tryEmit("Added reason \"$label\"") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to add reason") }
    }
  }

  fun toggleReasonShown(officeId: String, reason: VisitReason) {
    viewModelScope.launch {
      repository
        .toggleReasonShown(officeId, reason.id, !reason.shown)
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to update reason") }
    }
  }

  // --- PR MODULE ACTIONS ---

  fun savePrCampaign(
    snapshot: DeskSnapshot,
    existing: PRCampaign?,
    title: String,
    channel: PRChannel,
    status: PRCampaignStatus,
    targetDateLabel: String,
    location: String,
    audienceSummary: String,
    notes: String,
    ownerStaff: StaffMember?,
  ) {
    val office = snapshot.office ?: return
    val validation = validatePrCampaign(title)
    if (validation != null) {
      _feedback.tryEmit(validation)
      return
    }
    if (_isBusy.value) return
    val isHostOnly = snapshot.activeRole == StaffRole.HOST
    val resolvedOwner =
      if (isHostOnly) snapshot.currentStaff
      else ownerStaff ?: snapshot.currentStaff
    val ownerName =
      resolvedOwner?.shownAs?.ifBlank { resolvedOwner.fullName }
        ?: existing?.ownerName
        ?: currentUserDisplayName.ifBlank { normalizedEmail.substringBefore("@") }
    val ownerEmail =
      if (isHostOnly) normalizedEmail
      else (resolvedOwner?.email?.trim()?.lowercase()?.ifBlank { existing?.ownerEmail.orEmpty() }
        ?: normalizedEmail)

    val model =
      (existing ?: PRCampaign()).copy(
        title = title.trim(),
        channel = channel.wireValue,
        status = status.wireValue,
        targetDateLabel = targetDateLabel.trim(),
        location = location.trim(),
        audienceSummary = audienceSummary.trim(),
        notes = notes.trim(),
        ownerStaffId = resolvedOwner?.id ?: existing?.ownerStaffId.orEmpty(),
        ownerName = ownerName,
        ownerEmail = ownerEmail,
      )
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.upsertPrCampaign(office, existing, model, isHostSelfEdit = isHostOnly)
      _isBusy.value = false
      res
        .onSuccess {
          _feedback.tryEmit(
            if (existing == null) "Created PR campaign \"${model.title}\""
            else "Updated PR campaign \"${model.title}\""
          )
        }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to save campaign") }
    }
  }

  fun advancePrCampaignStatus(snapshot: DeskSnapshot, campaign: PRCampaign) {
    val officeId = snapshot.office?.id.orEmpty()
    if (officeId.isBlank() || campaign.id.isBlank()) return
    val next = nextPrCampaignStatus(campaign.parsedStatus())
    viewModelScope.launch {
      repository
        .updatePrCampaignStatus(officeId, campaign.id, next)
        .onSuccess { _feedback.tryEmit("${campaign.title} → ${next.label}") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to update campaign status") }
    }
  }

  fun deletePrCampaign(snapshot: DeskSnapshot, campaign: PRCampaign) {
    val officeId = snapshot.office?.id.orEmpty()
    if (officeId.isBlank() || campaign.id.isBlank() || snapshot.activeRole != StaffRole.ADMIN) return
    viewModelScope.launch {
      repository
        .deletePrCampaign(officeId, campaign.id)
        .onSuccess { _feedback.tryEmit("Removed campaign \"${campaign.title}\"") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to delete campaign") }
    }
  }

  fun savePrContact(
    snapshot: DeskSnapshot,
    existing: PRContact?,
    name: String,
    organization: String,
    designation: String,
    tier: PRContactTier,
    mobile: String,
    email: String,
    followUpStatus: PRFollowUpStatus,
    nextFollowUpDate: String,
    notes: String,
    ownerStaff: StaffMember?,
    photoUrl: String = existing?.photoUrl.orEmpty(),
    dob: String = existing?.dob.orEmpty(),
    anniversaryDate: String = existing?.anniversaryDate.orEmpty(),
    specialGiftPreference: String = existing?.specialGiftPreference.orEmpty(),
    cityOrAddress: String = existing?.cityOrAddress.orEmpty(),
  ) {
    val office = snapshot.office ?: return
    val validation = validatePrContact(name, email, mobile)
    if (validation != null) {
      _feedback.tryEmit(validation)
      return
    }
    if (_isBusy.value) return
    val isHostOnly = snapshot.activeRole == StaffRole.HOST
    val resolvedOwner =
      if (isHostOnly) snapshot.currentStaff
      else ownerStaff ?: snapshot.currentStaff
    val ownerName =
      resolvedOwner?.shownAs?.ifBlank { resolvedOwner.fullName }
        ?: existing?.ownerName
        ?: currentUserDisplayName.ifBlank { normalizedEmail.substringBefore("@") }
    val ownerEmail =
      if (isHostOnly) normalizedEmail
      else (resolvedOwner?.email?.trim()?.lowercase()?.ifBlank { existing?.ownerEmail.orEmpty() }
        ?: normalizedEmail)

    val model =
      (existing ?: PRContact()).copy(
        name = name.trim(),
        organization = organization.trim(),
        designation = designation.trim(),
        tier = tier.wireValue,
        mobile = mobile.trim(),
        email = email.trim().lowercase(),
        followUpStatus = followUpStatus.wireValue,
        nextFollowUpDate = nextFollowUpDate.trim(),
        photoUrl = photoUrl.trim(),
        dob = dob.trim(),
        anniversaryDate = anniversaryDate.trim(),
        lastWishedYear = existing?.lastWishedYear ?: 0L,
        specialGiftPreference = specialGiftPreference.trim(),
        cityOrAddress = cityOrAddress.trim(),
        notes = notes.trim(),
        ownerStaffId = resolvedOwner?.id ?: existing?.ownerStaffId.orEmpty(),
        ownerName = ownerName,
        ownerEmail = ownerEmail,
      )
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.upsertPrContact(office, existing, model, isHostSelfEdit = isHostOnly)
      _isBusy.value = false
      res
        .onSuccess {
          _feedback.tryEmit(
            if (existing == null) "Added ${model.name} to PR contacts"
            else "Updated PR contact ${model.name}"
          )
        }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to save PR contact") }
    }
  }

  fun markPrContactWished(snapshot: DeskSnapshot, contact: PRContact) {
    val officeId = snapshot.office?.id.orEmpty()
    if (officeId.isBlank() || contact.id.isBlank()) return
    val year = DeskFormatters.currentYear()
    viewModelScope.launch {
      repository
        .markPrContactWished(officeId, contact.id, year)
        .onSuccess { _feedback.tryEmit("Recorded wish sent to ${contact.name} ($year)") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Could not record wish") }
    }
  }

  fun cyclePrContactFollowUp(snapshot: DeskSnapshot, contact: PRContact) {
    val officeId = snapshot.office?.id.orEmpty()
    if (officeId.isBlank() || contact.id.isBlank()) return
    val next = nextPrFollowUpStatus(contact.parsedFollowUpStatus())
    viewModelScope.launch {
      repository
        .updatePrContactFollowUp(officeId, contact.id, next)
        .onSuccess { _feedback.tryEmit("${contact.name} marked ${next.label}") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to update follow-up") }
    }
  }

  fun deletePrContact(snapshot: DeskSnapshot, contact: PRContact) {
    val officeId = snapshot.office?.id.orEmpty()
    if (officeId.isBlank() || contact.id.isBlank() || snapshot.activeRole != StaffRole.ADMIN) return
    viewModelScope.launch {
      repository
        .deletePrContact(officeId, contact.id)
        .onSuccess { _feedback.tryEmit("Removed contact ${contact.name}") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to delete contact") }
    }
  }

  fun promoteVisitorToPrContact(
    snapshot: DeskSnapshot,
    profile: VisitorProfile,
    tier: PRContactTier,
  ) {
    val office = snapshot.office ?: return
    val alreadyExists =
      snapshot.prContacts.any { existing ->
        (profile.mobile.isNotBlank() && existing.mobile == profile.mobile) ||
          (profile.email.isNotBlank() && existing.email.equals(profile.email, ignoreCase = true)) ||
          (existing.name.equals(profile.name, ignoreCase = true) &&
            existing.organization.equals(profile.company, ignoreCase = true))
      }
    if (alreadyExists) {
      _feedback.tryEmit("${profile.name} is already in PR contacts")
      return
    }
    val owner =
      snapshot.staff.firstOrNull { it.id == profile.lastHostStaffId && it.activeAccount }
        ?: snapshot.currentStaff
    val contact =
      buildPrContactFromVisitorProfile(
        profile = profile,
        ownerStaff = owner,
        fallbackOwnerEmail = normalizedEmail,
        fallbackOwnerName = currentUserDisplayName.ifBlank { normalizedEmail.substringBefore("@") },
        tier = tier,
      )
    _isBusy.value = true
    viewModelScope.launch {
      val res =
        repository.upsertPrContact(
          office = office,
          existing = null,
          contact = contact,
          isHostSelfEdit = snapshot.activeRole == StaffRole.HOST,
        )
      _isBusy.value = false
      res
        .onSuccess { _feedback.tryEmit("Promoted ${contact.name} to ${tier.label} PR contacts") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to promote visitor") }
    }
  }

  fun promoteHostClientToPrContact(
    snapshot: DeskSnapshot,
    client: HostClientSummary,
    tier: PRContactTier,
  ) {
    val office = snapshot.office ?: return
    val alreadyExists =
      snapshot.prContacts.any { existing ->
        (client.mobile.isNotBlank() && existing.mobile == client.mobile) ||
          (client.email.isNotBlank() && existing.email.equals(client.email, ignoreCase = true)) ||
          (existing.name.equals(client.name, ignoreCase = true) &&
            existing.organization.equals(client.company, ignoreCase = true))
      }
    if (alreadyExists) {
      _feedback.tryEmit("${client.name} is already in your PR contacts")
      return
    }
    val contact =
      buildPrContactFromHostClient(
        client = client,
        ownerStaff = snapshot.currentStaff,
        fallbackOwnerEmail = normalizedEmail,
        fallbackOwnerName = currentUserDisplayName.ifBlank { normalizedEmail.substringBefore("@") },
        tier = tier,
      )
    _isBusy.value = true
    viewModelScope.launch {
      val res =
        repository.upsertPrContact(
          office = office,
          existing = null,
          contact = contact,
          isHostSelfEdit = snapshot.activeRole == StaffRole.HOST,
        )
      _isBusy.value = false
      res
        .onSuccess { _feedback.tryEmit("Added ${contact.name} to ${tier.label} PR contacts") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to add client to PR") }
    }
  }

  fun sendPrContactToReceptionCheckIn(snapshot: DeskSnapshot, contact: PRContact) {
    if (snapshot.activeRole != StaffRole.RECEPTION) return
    val hosts = snapshot.staff.filter { it.activeAccount && it.hostListed }
    val matchingHostId =
      hosts.firstOrNull { it.id == contact.ownerStaffId }?.id
        ?: hosts.firstOrNull { it.email.equals(contact.ownerEmail, ignoreCase = true) }?.id
        ?: _receptionForm.value.selectedHostId
        ?: hosts.firstOrNull()?.id.orEmpty()
    val preferredReason =
      snapshot.reasons.firstOrNull { it.shown && it.label.equals("Connection", ignoreCase = true) }?.label
        ?: snapshot.reasons.firstOrNull { it.shown }?.label
        ?: "Meeting"
    val cleanMobile =
      contact.mobile.filter { it.isDigit() }.takeUnless { it == "919999999999" }.orEmpty()
    _receptionForm.value =
      ReceptionFormState(
        mobile = cleanMobile,
        name = contact.name.trim(),
        company = contact.organization.trim(),
        email = contact.email.trim(),
        remark = listOfNotNull(
          contact.tier.takeIf { it.isNotBlank() },
          contact.designation.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        selectedReason = preferredReason,
        selectedHostId = matchingHostId,
      )
    _selectedTab.value = AppTab.RECEPTION
    _feedback.tryEmit("Pre-filled reception check-in for ${contact.name}")
  }

  fun savePrGift(
    snapshot: DeskSnapshot,
    existing: PRGift?,
    recipientOrSender: String,
    company: String,
    itemTitle: String,
    direction: PRGiftDirection,
    status: PRGiftStatus,
    occasion: String,
    dateLabel: String,
    notes: String,
    ownerStaff: StaffMember?,
  ) {
    val office = snapshot.office ?: return
    val validation = validatePrGift(recipientOrSender, itemTitle)
    if (validation != null) {
      _feedback.tryEmit(validation)
      return
    }
    if (_isBusy.value) return
    val isHostOnly = snapshot.activeRole == StaffRole.HOST
    val resolvedOwner =
      if (isHostOnly) snapshot.currentStaff
      else ownerStaff ?: snapshot.currentStaff
    val ownerName =
      resolvedOwner?.shownAs?.ifBlank { resolvedOwner.fullName }
        ?: existing?.ownerName
        ?: currentUserDisplayName.ifBlank { normalizedEmail.substringBefore("@") }
    val ownerEmail =
      if (isHostOnly) normalizedEmail
      else (resolvedOwner?.email?.trim()?.lowercase()?.ifBlank { existing?.ownerEmail.orEmpty() }
        ?: normalizedEmail)

    val model =
      (existing ?: PRGift()).copy(
        recipientOrSender = recipientOrSender.trim(),
        company = company.trim(),
        itemTitle = itemTitle.trim(),
        direction = direction.wireValue,
        status = status.wireValue,
        occasion = occasion.trim(),
        dateLabel = dateLabel.trim(),
        notes = notes.trim(),
        ownerStaffId = resolvedOwner?.id ?: existing?.ownerStaffId.orEmpty(),
        ownerName = ownerName,
        ownerEmail = ownerEmail,
      )
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.upsertPrGift(office, existing, model, isHostSelfEdit = isHostOnly)
      _isBusy.value = false
      res
        .onSuccess {
          _feedback.tryEmit(
            if (existing == null) "Logged PR gift for ${model.recipientOrSender}"
            else "Updated gift record for ${model.recipientOrSender}"
          )
        }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to save gift record") }
    }
  }

  fun advancePrGiftStatus(snapshot: DeskSnapshot, gift: PRGift) {
    val officeId = snapshot.office?.id.orEmpty()
    if (officeId.isBlank() || gift.id.isBlank()) return
    val next = nextPrGiftStatus(gift.parsedStatus())
    viewModelScope.launch {
      repository
        .updatePrGiftStatus(officeId, gift.id, next)
        .onSuccess { _feedback.tryEmit("${gift.recipientOrSender} gift → ${next.label}") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to update gift status") }
    }
  }

  fun deletePrGift(snapshot: DeskSnapshot, gift: PRGift) {
    val officeId = snapshot.office?.id.orEmpty()
    if (officeId.isBlank() || gift.id.isBlank() || snapshot.activeRole != StaffRole.ADMIN) return
    viewModelScope.launch {
      repository
        .deletePrGift(officeId, gift.id)
        .onSuccess { _feedback.tryEmit("Removed gift entry for ${gift.recipientOrSender}") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to delete gift") }
    }
  }

  // --- APPOINTMENT BOOKING ACTIONS ---

  fun saveAppointment(
    snapshot: DeskSnapshot,
    existing: Appointment?,
    visitorName: String,
    visitorMobile: String,
    visitorCompany: String,
    visitorEmail: String,
    purpose: String,
    location: String,
    scheduledDateIso: String,
    scheduledTimeLabel: String,
    notes: String,
    assignedHostStaff: StaffMember?,
  ) {
    val office = snapshot.office ?: return
    val isHostOnly = snapshot.activeRole == StaffRole.HOST
    val targetHost =
      if (isHostOnly) snapshot.currentStaff
      else assignedHostStaff ?: snapshot.currentStaff
    val hostId = targetHost?.id ?: existing?.hostStaffId.orEmpty()
    val validation =
      validateAppointment(
        visitorName = visitorName,
        scheduledDateIso = scheduledDateIso,
        scheduledTimeLabel = scheduledTimeLabel,
        hostStaffId = hostId,
      )
    if (validation != null) {
      _feedback.tryEmit(validation)
      return
    }
    if (_isBusy.value) return

    val resolvedHostName =
      targetHost?.shownAs?.ifBlank { targetHost.fullName }
        ?: existing?.hostShownAs
        ?: currentUserDisplayName.ifBlank { normalizedEmail.substringBefore("@") }
    val resolvedHostEmail =
      if (isHostOnly) normalizedEmail
      else (targetHost?.email?.trim()?.lowercase()?.ifBlank { existing?.hostEmail.orEmpty() }
        ?: normalizedEmail)
    val resolvedCabin =
      targetHost?.formattedSeating()?.ifBlank { targetHost.cabin }
        ?: existing?.hostCabin.orEmpty()
    val cleanDate = scheduledDateIso.trim()
    val cleanTime = scheduledTimeLabel.trim()
    val scheduledMs = DeskFormatters.parseScheduledMillis(cleanDate, cleanTime)

    val model =
      (existing ?: Appointment()).copy(
        visitorName = visitorName.trim(),
        visitorMobile = visitorMobile.trim(),
        visitorCompany = visitorCompany.trim(),
        visitorEmail = visitorEmail.trim().lowercase(),
        purpose = purpose.trim().ifBlank { "Meeting" },
        location = location.trim().ifBlank { resolvedCabin },
        scheduledDateIso = cleanDate,
        scheduledTimeLabel = cleanTime,
        scheduledAtMillis = scheduledMs,
        notes = notes.trim(),
        hostStaffId = hostId,
        hostShownAs = resolvedHostName,
        hostEmail = resolvedHostEmail,
        hostCabin = resolvedCabin,
        status = existing?.status ?: AppointmentStatus.SCHEDULED.wireValue,
        bookedByRole = existing?.bookedByRole?.ifBlank { snapshot.activeRole.wireValue }
          ?: snapshot.activeRole.wireValue,
      )

    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.upsertAppointment(office, existing, model, isHostSelfEdit = isHostOnly)
      _isBusy.value = false
      res
        .onSuccess { persisted ->
          val day = DeskFormatters.formatAppointmentDayLabel(cleanDate)
          val token = calendarAccessToken
          if (token != null) {
            val calNote = pushSingleAppointmentWithToken(token, snapshot, persisted)
            _feedback.tryEmit(
              if (existing == null) "Booked ${persisted.visitorName} for $resolvedHostName ($day · $cleanTime)$calNote"
              else "Updated appointment for ${persisted.visitorName}$calNote",
            )
            refreshCalendarEventsInternal(token)
          } else {
            _feedback.tryEmit(
              if (existing == null) "Booked ${persisted.visitorName} for $resolvedHostName ($day · $cleanTime)"
              else "Updated appointment for ${persisted.visitorName}",
            )
          }
        }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to save appointment") }
    }
  }

  fun updateAppointmentStatus(
    snapshot: DeskSnapshot,
    appointment: Appointment,
    newStatus: AppointmentStatus,
  ) {
    val officeId = snapshot.office?.id.orEmpty()
    if (officeId.isBlank() || appointment.id.isBlank()) return
    viewModelScope.launch {
      repository
        .updateAppointmentStatus(officeId, appointment.id, newStatus)
        .onSuccess {
          val token = calendarAccessToken
          if (newStatus == AppointmentStatus.CANCELLED && token != null && appointment.calendarEventId.isNotBlank()) {
            when (calendarService.cancelEvent(token, appointment.calendarEventId)) {
              is CalendarApiResult.Success -> {
                repository.updateAppointmentCalendarMetadata(
                  officeId = officeId,
                  appointmentId = appointment.id,
                  calendarEventId = "",
                  calendarHtmlLink = "",
                  calendarSyncedEmail = "",
                )
                refreshCalendarEventsInternal(token)
              }
              CalendarApiResult.AuthExpired -> handleCalendarAuthExpired()
              is CalendarApiResult.Failure -> Unit
            }
          }
          _feedback.tryEmit("Appointment for ${appointment.visitorName} marked ${newStatus.label}")
        }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to update appointment") }
    }
  }

  fun checkInFromAppointment(snapshot: DeskSnapshot, appointment: Appointment) {
    val office = snapshot.office ?: return
    if (snapshot.activeRole != StaffRole.RECEPTION) return
    val host =
      snapshot.staff.firstOrNull { it.id == appointment.hostStaffId && it.activeAccount }
        ?: snapshot.staff.firstOrNull {
          it.email.equals(appointment.hostEmail, ignoreCase = true) && it.activeAccount
        }
    if (host == null) {
      _feedback.tryEmit("Host account for this appointment is not active.")
      return
    }
    val digits =
      appointment.visitorMobile.filter { it.isDigit() }.takeUnless { it == "919999999999" }.orEmpty()
    val existingProfile =
      snapshot.visitorProfiles.firstOrNull { profile ->
        (digits.length >= 6 && profile.mobile.filter { it.isDigit() } == digits) ||
          (appointment.visitorEmail.isNotBlank() &&
            profile.email.equals(appointment.visitorEmail, ignoreCase = true)) ||
          (profile.name.equals(appointment.visitorName, ignoreCase = true) &&
            profile.company.equals(appointment.visitorCompany, ignoreCase = true))
      }
    if (_isBusy.value) return
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.checkInFromAppointment(office, appointment, host, existingProfile)
      _isBusy.value = false
      res
        .onSuccess {
          _feedback.tryEmit("Checked in ${appointment.visitorName} for ${host.shownAs.ifBlank { host.fullName }}")
        }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Check-in from appointment failed") }
    }
  }

  fun deleteAppointment(snapshot: DeskSnapshot, appointment: Appointment) {
    val officeId = snapshot.office?.id.orEmpty()
    if (officeId.isBlank() || appointment.id.isBlank() || snapshot.activeRole != StaffRole.ADMIN) return
    viewModelScope.launch {
      repository
        .deleteAppointment(officeId, appointment.id)
        .onSuccess {
          val token = calendarAccessToken
          if (token != null && appointment.calendarEventId.isNotBlank()) {
            when (calendarService.cancelEvent(token, appointment.calendarEventId)) {
              is CalendarApiResult.Success -> refreshCalendarEventsInternal(token)
              CalendarApiResult.AuthExpired -> handleCalendarAuthExpired()
              is CalendarApiResult.Failure -> Unit
            }
          }
          _feedback.tryEmit("Deleted appointment for ${appointment.visitorName}")
        }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to delete appointment") }
    }
  }

  // --- GOOGLE CALENDAR INTEGRATION ---

  fun trySilentCalendarConnect(context: Context, snapshot: DeskSnapshot?) {
    if (calendarAccessToken != null) {
      if (_googleCalendarState.value.lastSyncedMillis == 0L) {
        viewModelScope.launch {
          calendarAccessToken?.let { syncCalendarAndEvents(it, snapshot, showToast = false) }
        }
      }
      return
    }
    if (silentCalendarChecked || normalizedEmail.isBlank()) return
    silentCalendarChecked = true
    viewModelScope.launch {
      when (val auth = GoogleCalendarAuthHelper.authorize(context)) {
        is CalendarAuthState.Authorized -> {
          calendarAccessToken = auth.accessToken
          _googleCalendarState.value =
            _googleCalendarState.value.copy(
              isConnected = true,
              isConnecting = false,
              connectedEmail = normalizedEmail,
              errorMessage = null,
            )
          syncCalendarAndEvents(auth.accessToken, snapshot, showToast = false)
        }
        is CalendarAuthState.NeedsConsent,
        is CalendarAuthState.Error -> {
          // Stay disconnected silently until the user taps Connect Google Calendar
        }
      }
    }
  }

  fun connectOrSyncGoogleCalendar(context: Context, snapshot: DeskSnapshot?) {
    pendingSyncSnapshot = snapshot
    val existingToken = calendarAccessToken
    if (existingToken != null) {
      viewModelScope.launch {
        syncCalendarAndEvents(existingToken, snapshot, showToast = true)
      }
      return
    }
    if (_googleCalendarState.value.isConnecting) return
    _googleCalendarState.value =
      _googleCalendarState.value.copy(isConnecting = true, errorMessage = null)
    viewModelScope.launch {
      when (val auth = GoogleCalendarAuthHelper.authorize(context)) {
        is CalendarAuthState.Authorized -> {
          calendarAccessToken = auth.accessToken
          _googleCalendarState.value =
            _googleCalendarState.value.copy(
              isConnected = true,
              isConnecting = false,
              connectedEmail = normalizedEmail,
              errorMessage = null,
              pendingConsentIntentSender = null,
            )
          syncCalendarAndEvents(auth.accessToken, snapshot, showToast = true)
        }
        is CalendarAuthState.NeedsConsent -> {
          _googleCalendarState.value =
            _googleCalendarState.value.copy(
              isConnecting = false,
              pendingConsentIntentSender = auth.intentSender,
            )
        }
        is CalendarAuthState.Error -> {
          pendingSingleAppointment = null
          _googleCalendarState.value =
            _googleCalendarState.value.copy(
              isConnecting = false,
              errorMessage = auth.message,
            )
          _feedback.tryEmit(auth.message)
        }
      }
    }
  }

  fun consumeCalendarConsentPrompt() {
    if (_googleCalendarState.value.pendingConsentIntentSender != null) {
      _googleCalendarState.value = _googleCalendarState.value.copy(pendingConsentIntentSender = null)
    }
  }

  fun onCalendarConsentResult(
    context: Context,
    data: Intent?,
    snapshot: DeskSnapshot?,
  ) {
    val activeSnapshot = snapshot ?: pendingSyncSnapshot
    when (val auth = GoogleCalendarAuthHelper.extractTokenFromIntent(context, data)) {
      is CalendarAuthState.Authorized -> {
        calendarAccessToken = auth.accessToken
        _googleCalendarState.value =
          _googleCalendarState.value.copy(
            isConnected = true,
            isConnecting = false,
            connectedEmail = normalizedEmail,
            errorMessage = null,
            pendingConsentIntentSender = null,
          )
        viewModelScope.launch {
          syncCalendarAndEvents(auth.accessToken, activeSnapshot, showToast = true)
        }
      }
      is CalendarAuthState.Error -> {
        pendingSingleAppointment = null
        _googleCalendarState.value =
          _googleCalendarState.value.copy(
            isConnecting = false,
            errorMessage = auth.message,
            pendingConsentIntentSender = null,
          )
        _feedback.tryEmit(auth.message)
      }
      is CalendarAuthState.NeedsConsent -> {
        _googleCalendarState.value =
          _googleCalendarState.value.copy(
            isConnecting = false,
            pendingConsentIntentSender = auth.intentSender,
          )
      }
    }
  }

  fun disconnectGoogleCalendar() {
    calendarAccessToken = null
    pendingSingleAppointment = null
    pendingSyncSnapshot = null
    _googleCalendarState.value = GoogleCalendarUiState()
    _feedback.tryEmit("Disconnected Google Calendar")
  }

  fun syncSingleAppointmentToCalendar(
    context: Context,
    snapshot: DeskSnapshot,
    appointment: Appointment,
  ) {
    val token = calendarAccessToken
    if (token == null) {
      pendingSingleAppointment = appointment
      connectOrSyncGoogleCalendar(context, snapshot)
      return
    }
    viewModelScope.launch {
      _googleCalendarState.value = _googleCalendarState.value.copy(isSyncing = true, errorMessage = null)
      val note = pushSingleAppointmentWithToken(token, snapshot, appointment)
      refreshCalendarEventsInternal(token)
      _googleCalendarState.value = _googleCalendarState.value.copy(isSyncing = false)
      if (note.isNotBlank()) {
        _feedback.tryEmit("${appointment.visitorName}$note")
      }
    }
  }

  private suspend fun pushSingleAppointmentWithToken(
    token: String,
    snapshot: DeskSnapshot?,
    appointment: Appointment,
  ): String {
    val officeId = snapshot?.office?.id.orEmpty()
    return when (val result = calendarService.upsertAppointmentEvent(token, appointment)) {
      is CalendarApiResult.Success -> {
        if (officeId.isNotBlank() && appointment.id.isNotBlank()) {
          repository.updateAppointmentCalendarMetadata(
            officeId = officeId,
            appointmentId = appointment.id,
            calendarEventId = result.data.eventId,
            calendarHtmlLink = result.data.htmlLink,
            calendarSyncedEmail = normalizedEmail,
          )
        }
        " · Added to Google Calendar"
      }
      CalendarApiResult.AuthExpired -> {
        handleCalendarAuthExpired()
        ""
      }
      is CalendarApiResult.Failure -> {
        _googleCalendarState.value = _googleCalendarState.value.copy(errorMessage = result.message)
        ""
      }
    }
  }

  private suspend fun syncCalendarAndEvents(
    token: String,
    snapshot: DeskSnapshot?,
    showToast: Boolean,
  ) {
    _googleCalendarState.value = _googleCalendarState.value.copy(isSyncing = true, errorMessage = null)
    var syncedCount = 0
    val queued = pendingSingleAppointment
    pendingSingleAppointment = null

    val officeId = snapshot?.office?.id.orEmpty()
    if (queued != null) {
      when (val res = calendarService.upsertAppointmentEvent(token, queued)) {
        is CalendarApiResult.Success -> {
          syncedCount++
          if (officeId.isNotBlank() && queued.id.isNotBlank()) {
            repository.updateAppointmentCalendarMetadata(
              officeId = officeId,
              appointmentId = queued.id,
              calendarEventId = res.data.eventId,
              calendarHtmlLink = res.data.htmlLink,
              calendarSyncedEmail = normalizedEmail,
            )
          }
        }
        CalendarApiResult.AuthExpired -> {
          handleCalendarAuthExpired()
          return
        }
        is CalendarApiResult.Failure -> {
          _googleCalendarState.value = _googleCalendarState.value.copy(errorMessage = res.message)
        }
      }
    }

    if (snapshot != null && officeId.isNotBlank() && normalizedEmail.isNotBlank()) {
      val hostUpcoming =
        upcomingScheduledAppointments(
          filterHostSpecificAppointments(
            appointments = snapshot.appointments,
            userEmail = normalizedEmail,
            staffId = snapshot.currentStaff?.id,
          ),
        )
      val toPush =
        hostUpcoming.filter { appt ->
          appt.id != queued?.id &&
            (appt.calendarEventId.isBlank() ||
              !appt.calendarSyncedEmail.equals(normalizedEmail, ignoreCase = true))
        }
      for (appt in toPush) {
        when (val res = calendarService.upsertAppointmentEvent(token, appt)) {
          is CalendarApiResult.Success -> {
            syncedCount++
            repository.updateAppointmentCalendarMetadata(
              officeId = officeId,
              appointmentId = appt.id,
              calendarEventId = res.data.eventId,
              calendarHtmlLink = res.data.htmlLink,
              calendarSyncedEmail = normalizedEmail,
            )
          }
          CalendarApiResult.AuthExpired -> {
            handleCalendarAuthExpired()
            return
          }
          is CalendarApiResult.Failure -> {
            _googleCalendarState.value = _googleCalendarState.value.copy(errorMessage = res.message)
          }
        }
      }
    }

    val fetchedOk = refreshCalendarEventsInternal(token)
    if (showToast && fetchedOk) {
      val eventCount = _googleCalendarState.value.events.size
      val msg =
        if (syncedCount > 0) {
          "Synced $syncedCount appointment(s) & loaded $eventCount Google Calendar events"
        } else {
          "Google Calendar synced · $eventCount upcoming events for $normalizedEmail"
        }
      _feedback.tryEmit(msg)
    }
  }

  private suspend fun refreshCalendarEventsInternal(token: String): Boolean {
    return when (val result = calendarService.fetchUpcomingEvents(token)) {
      is CalendarApiResult.Success -> {
        _googleCalendarState.value =
          _googleCalendarState.value.copy(
            isConnected = true,
            isConnecting = false,
            isSyncing = false,
            connectedEmail = normalizedEmail,
            events = result.data,
            lastSyncedMillis = System.currentTimeMillis(),
            errorMessage = null,
          )
        true
      }
      CalendarApiResult.AuthExpired -> {
        handleCalendarAuthExpired()
        false
      }
      is CalendarApiResult.Failure -> {
        _googleCalendarState.value =
          _googleCalendarState.value.copy(
            isSyncing = false,
            errorMessage = result.message,
          )
        false
      }
    }
  }

  private fun handleCalendarAuthExpired() {
    calendarAccessToken = null
    _googleCalendarState.value =
      _googleCalendarState.value.copy(
        isConnected = false,
        isConnecting = false,
        isSyncing = false,
        errorMessage = "Google Calendar session expired. Tap Sync to reconnect.",
      )
    _feedback.tryEmit("Google Calendar session expired. Tap Sync to reconnect.")
  }

  // --- COMPUTED BOARD HELPERS ---

  fun buildHostBoard(snapshot: DeskSnapshot, now: Long): List<HostBoardItem> =
    buildHostBoard(staff = snapshot.staff, visits = snapshot.visits, now = now)

  companion object {
    private const val TAG = "MainViewModel"
    const val PRIMARY_ADMIN_EMAIL = "kunwarsingh9752005805@gmail.com"

    fun isBootstrapAdminEmail(email: String): Boolean =
      email.trim().lowercase() == PRIMARY_ADMIN_EMAIL

    fun filterHostSpecificVisits(
      visits: List<Visit>,
      userEmail: String,
      staffId: String? = null,
    ): List<Visit> {
      val cleanEmail = userEmail.trim().lowercase()
      val cleanStaffId = staffId?.trim().orEmpty()
      return visits.filter { visit ->
        val matchesEmail =
          cleanEmail.isNotBlank() && visit.hostEmail.trim().lowercase() == cleanEmail
        val matchesStaffId =
          cleanStaffId.isNotBlank() &&
            visit.hostStaffId == cleanStaffId &&
            (visit.hostEmail.isBlank() || visit.hostEmail.trim().lowercase() == cleanEmail)
        matchesEmail || matchesStaffId
      }
    }

    fun filterHostSpecificPrCampaigns(
      campaigns: List<PRCampaign>,
      userEmail: String,
    ): List<PRCampaign> {
      val cleanEmail = userEmail.trim().lowercase()
      if (cleanEmail.isBlank()) return emptyList()
      return campaigns.filter { it.ownerEmail.trim().lowercase() == cleanEmail }
    }

    fun filterHostSpecificPrContacts(
      contacts: List<PRContact>,
      userEmail: String,
    ): List<PRContact> {
      val cleanEmail = userEmail.trim().lowercase()
      if (cleanEmail.isBlank()) return emptyList()
      return contacts.filter { it.ownerEmail.trim().lowercase() == cleanEmail }
    }

    fun filterHostSpecificPrGifts(
      gifts: List<PRGift>,
      userEmail: String,
    ): List<PRGift> {
      val cleanEmail = userEmail.trim().lowercase()
      if (cleanEmail.isBlank()) return emptyList()
      return gifts.filter { it.ownerEmail.trim().lowercase() == cleanEmail }
    }

    fun filterHostSpecificAppointments(
      appointments: List<Appointment>,
      userEmail: String,
      staffId: String? = null,
    ): List<Appointment> {
      val cleanEmail = userEmail.trim().lowercase()
      val cleanStaffId = staffId?.trim().orEmpty()
      return appointments.filter { appt ->
        val matchesEmail =
          cleanEmail.isNotBlank() && appt.hostEmail.trim().lowercase() == cleanEmail
        val matchesStaffId =
          cleanStaffId.isNotBlank() &&
            appt.hostStaffId == cleanStaffId &&
            (appt.hostEmail.isBlank() || appt.hostEmail.trim().lowercase() == cleanEmail)
        matchesEmail || matchesStaffId
      }
    }

    fun upcomingScheduledAppointments(
      appointments: List<Appointment>,
      nowMillis: Long = System.currentTimeMillis(),
    ): List<Appointment> {
      val todayStart = DeskFormatters.startOfTodayMillis(nowMillis)
      return appointments
        .filter { appt ->
          appt.parsedStatus() == AppointmentStatus.SCHEDULED &&
            (appt.scheduledAtMillis == 0L || appt.scheduledAtMillis >= todayStart)
        }
        .sortedWith(
          compareBy<Appointment> { if (it.scheduledAtMillis > 0L) it.scheduledAtMillis else Long.MAX_VALUE }
            .thenBy { it.scheduledDateIso }
            .thenBy { it.scheduledTimeLabel }
        )
    }

    fun validateAppointment(
      visitorName: String,
      scheduledDateIso: String,
      scheduledTimeLabel: String,
      hostStaffId: String,
    ): String? {
      if (visitorName.trim().isEmpty()) return "Enter the visitor or client's name."
      if (hostStaffId.trim().isEmpty()) return "Select a host for this appointment."
      if (scheduledDateIso.trim().isEmpty()) return "Choose an appointment date."
      if (scheduledTimeLabel.trim().isEmpty()) return "Choose a time slot."
      return null
    }

    fun computePrMetrics(
      campaigns: List<PRCampaign>,
      contacts: List<PRContact>,
      gifts: List<PRGift>,
      nowMillis: Long = System.currentTimeMillis(),
    ): PRMetrics =
      PRMetrics(
        activeCampaigns = campaigns.count { it.parsedStatus() != PRCampaignStatus.COMPLETED },
        liveCampaigns = campaigns.count { it.parsedStatus() == PRCampaignStatus.LIVE },
        totalContacts = contacts.size,
        dueFollowUps = contacts.count { it.parsedFollowUpStatus() == PRFollowUpStatus.FOLLOW_UP_DUE },
        pendingGifts = gifts.count { it.parsedStatus() != PRGiftStatus.ACKNOWLEDGED },
        upcomingBirthdays = contacts.count {
          val days = DeskFormatters.daysUntilBirthday(it.dob, nowMillis)
          days != null && days in 0..30
        },
      )

    fun nextPrCampaignStatus(current: PRCampaignStatus): PRCampaignStatus =
      when (current) {
        PRCampaignStatus.PLANNED -> PRCampaignStatus.ACTIVE
        PRCampaignStatus.ACTIVE -> PRCampaignStatus.LIVE
        PRCampaignStatus.LIVE -> PRCampaignStatus.COMPLETED
        PRCampaignStatus.COMPLETED -> PRCampaignStatus.PLANNED
      }

    fun nextPrFollowUpStatus(current: PRFollowUpStatus): PRFollowUpStatus =
      when (current) {
        PRFollowUpStatus.WARM -> PRFollowUpStatus.FOLLOW_UP_DUE
        PRFollowUpStatus.FOLLOW_UP_DUE -> PRFollowUpStatus.SCHEDULED
        PRFollowUpStatus.SCHEDULED -> PRFollowUpStatus.CONNECTED
        PRFollowUpStatus.CONNECTED -> PRFollowUpStatus.WARM
      }

    fun nextPrGiftStatus(current: PRGiftStatus): PRGiftStatus =
      when (current) {
        PRGiftStatus.PREPARED -> PRGiftStatus.DISPATCHED
        PRGiftStatus.DISPATCHED -> PRGiftStatus.DELIVERED
        PRGiftStatus.DELIVERED -> PRGiftStatus.ACKNOWLEDGED
        PRGiftStatus.ACKNOWLEDGED -> PRGiftStatus.PREPARED
      }

    fun validatePrCampaign(title: String): String? {
      if (title.trim().isEmpty()) return "Enter a campaign or event title."
      return null
    }

    fun validatePrContact(name: String, email: String, mobile: String): String? {
      if (name.trim().isEmpty()) return "Enter the contact person's name."
      val cleanEmail = email.trim()
      if (cleanEmail.isNotEmpty() && (!cleanEmail.contains("@") || cleanEmail.length < 5)) {
        return "Enter a valid email address or leave blank."
      }
      val digits = mobile.filter { it.isDigit() }
      if (mobile.trim().isNotEmpty() && digits.length < 7) {
        return "Enter a valid phone number or leave blank."
      }
      return null
    }

    fun validatePrGift(recipientOrSender: String, itemTitle: String): String? {
      if (recipientOrSender.trim().isEmpty()) return "Enter recipient or sender name."
      if (itemTitle.trim().isEmpty()) return "Enter the gift / press kit description."
      return null
    }

    fun buildPrContactFromVisitorProfile(
      profile: VisitorProfile,
      ownerStaff: StaffMember?,
      fallbackOwnerEmail: String,
      fallbackOwnerName: String,
      tier: PRContactTier,
    ): PRContact {
      val usableMobile =
        profile.mobile.filter { it.isDigit() }.takeUnless { it == "919999999999" }.orEmpty()
      val ownerEmail =
        ownerStaff?.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: fallbackOwnerEmail
      val ownerName =
        ownerStaff?.shownAs?.ifBlank { ownerStaff.fullName } ?: fallbackOwnerName
      return PRContact(
        name = profile.name.trim(),
        organization = profile.company.trim(),
        designation = profile.visitorJobProfile.trim(),
        tier = tier.wireValue,
        mobile = usableMobile,
        email = profile.email.trim().lowercase(),
        followUpStatus = PRFollowUpStatus.WARM.wireValue,
        dob = profile.dob.trim(),
        notes = profile.remark.trim(),
        ownerStaffId = ownerStaff?.id.orEmpty(),
        ownerName = ownerName,
        ownerEmail = ownerEmail,
      )
    }

    fun buildPrContactFromHostClient(
      client: HostClientSummary,
      ownerStaff: StaffMember?,
      fallbackOwnerEmail: String,
      fallbackOwnerName: String,
      tier: PRContactTier,
    ): PRContact {
      val usableMobile =
        client.mobile.filter { it.isDigit() }.takeUnless { it == "919999999999" }.orEmpty()
      val ownerEmail =
        ownerStaff?.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: fallbackOwnerEmail
      val ownerName =
        ownerStaff?.shownAs?.ifBlank { ownerStaff.fullName } ?: fallbackOwnerName
      return PRContact(
        name = client.name.trim(),
        organization = client.company.trim(),
        designation = client.jobProfile.trim(),
        tier = tier.wireValue,
        mobile = usableMobile,
        email = client.email.trim().lowercase(),
        followUpStatus = PRFollowUpStatus.WARM.wireValue,
        notes = client.lastRemark.trim(),
        ownerStaffId = ownerStaff?.id.orEmpty(),
        ownerName = ownerName,
        ownerEmail = ownerEmail,
      )
    }

    fun buildBirthdayWishMessage(contact: PRContact, senderName: String): String {
      val firstName = contact.name.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "there"
      val from = senderName.trim().ifBlank { "Team ArchPoint" }
      val orgNote =
        contact.organization.trim().takeIf { it.isNotBlank() }?.let { " and continued success at $it" }.orEmpty()
      return "Warmest birthday wishes, $firstName! Wishing you a joyful year ahead$orgNote. Best regards, $from · ArchPoint Studio"
    }

    fun resolveTabsForRole(role: StaffRole, hostListed: Boolean): List<AppTab> =
      when (role) {
        StaffRole.RECEPTION ->
          buildList {
            add(AppTab.RECEPTION)
            add(AppTab.PR)
            if (hostListed) add(AppTab.MY_VISITORS)
            add(AppTab.PROFILE)
          }
        StaffRole.ADMIN ->
          buildList {
            add(AppTab.ADMIN)
            add(AppTab.VISITOR_LOG)
            add(AppTab.PR)
            if (hostListed) add(AppTab.MY_VISITORS)
            add(AppTab.PROFILE)
          }
        StaffRole.HOST ->
          listOf(
            AppTab.MY_VISITORS,
            AppTab.PR,
            AppTab.PROFILE,
          )
      }

    fun buildHostClients(
      visits: List<Visit>,
      userEmail: String,
      staffId: String? = null,
    ): List<HostClientSummary> {
      val scoped = filterHostSpecificVisits(visits = visits, userEmail = userEmail, staffId = staffId)
      if (scoped.isEmpty()) return emptyList()

      val sorted = scoped.sortedByDescending { it.arrivedAt?.toDate()?.time ?: 0L }
      val grouped = linkedMapOf<String, MutableList<Visit>>()
      for (visit in sorted) {
        if (visit.visitorName.isBlank()) continue
        val digits =
          visit.visitorMobile.filter { it.isDigit() }.takeUnless { it == "919999999999" }.orEmpty()
        val emailKey = visit.visitorEmail.trim().lowercase()
        val key =
          when {
            digits.length >= 6 -> "m:$digits"
            emailKey.isNotBlank() -> "e:$emailKey"
            else -> "n:${visit.visitorName.trim().lowercase()}|${visit.visitorCompany.trim().lowercase()}"
          }
        grouped.getOrPut(key) { mutableListOf() }.add(visit)
      }

      return grouped.entries.map { (key, list) ->
        val latest = list.first()
        val bestCompany = list.firstOrNull { it.visitorCompany.isNotBlank() }?.visitorCompany.orEmpty()
        val bestMobile =
          list
            .map { it.visitorMobile.filter { ch -> ch.isDigit() } }
            .firstOrNull { it.length >= 6 && it != "919999999999" }
            .orEmpty()
        val bestEmail = list.firstOrNull { it.visitorEmail.isNotBlank() }?.visitorEmail.orEmpty()
        val bestProfile = list.firstOrNull { it.visitorJobProfile.isNotBlank() }?.visitorJobProfile.orEmpty()
        val bestRemark = list.firstOrNull { it.remark.isNotBlank() }?.remark.orEmpty()
        HostClientSummary(
          key = key,
          name = latest.visitorName.trim(),
          company = bestCompany.trim(),
          mobile = bestMobile,
          email = bestEmail.trim(),
          jobProfile = bestProfile.trim(),
          visitCount = list.size,
          completedCount = list.count { it.parsedStatus() == VisitStatus.DONE },
          lastVisitedAtMillis = latest.arrivedAt?.toDate()?.time ?: 0L,
          lastReason = latest.reason.trim(),
          lastRemark = bestRemark.trim(),
          lastStatus = latest.parsedStatus(),
        )
      }
    }

    fun populateFormFromProfile(
      current: ReceptionFormState,
      profile: VisitorProfile,
      hosts: List<StaffMember>,
    ): ReceptionFormState {
      val validHostId =
        hosts.firstOrNull { it.id == profile.lastHostStaffId }?.id ?: current.selectedHostId
      val rawDigits = profile.mobile.filter { it.isDigit() }
      val usableMobile = if (rawDigits == "919999999999") "" else rawDigits
      val isSameVisitor =
        (profile.id.isNotBlank() && current.matchedProfile?.id == profile.id) ||
          (usableMobile.isNotBlank() && current.mobile == usableMobile && current.name.equals(profile.name, ignoreCase = true))
      return current.copy(
        mobile = usableMobile,
        name = profile.name.trim(),
        company = profile.company.trim(),
        email = profile.email.trim(),
        dob = profile.dob.trim(),
        remark = if (isSameVisitor) current.remark else "",
        selectedHostId = validHostId,
        selectedReason = profile.lastReason.trim().ifBlank { current.selectedReason },
        matchedProfile = profile.takeIf { it.id.isNotBlank() },
        suppressedAutoMatchMobile = "",
        isSubmitting = false,
      )
    }

    fun mergeScannedCardIntoForm(
      current: ReceptionFormState,
      extracted: ScannedVisitorCard,
      profiles: List<VisitorProfile>,
      hosts: List<StaffMember>,
    ): ReceptionFormState {
      val matchedProfile =
        profiles.firstOrNull { profile ->
          (extracted.mobile.length >= 10 && profile.mobile == extracted.mobile) ||
            (extracted.email.isNotBlank() && profile.email.equals(extracted.email, ignoreCase = true))
        }
      val validHostId =
        matchedProfile?.let { m -> hosts.firstOrNull { it.id == m.lastHostStaffId }?.id }
          ?: current.selectedHostId
      val validReason =
        matchedProfile?.lastReason?.takeIf { it.isNotBlank() } ?: current.selectedReason
      val cardRemark = extracted.resolvedRemark()
      return current.copy(
        name = extracted.name.ifBlank { matchedProfile?.name ?: current.name },
        company = extracted.company.ifBlank { matchedProfile?.company ?: current.company },
        mobile = extracted.mobile.ifBlank { current.mobile },
        email = extracted.email.ifBlank { matchedProfile?.email ?: current.email },
        dob = matchedProfile?.dob?.ifBlank { current.dob } ?: current.dob,
        remark = cardRemark.ifBlank { current.remark },
        selectedHostId = validHostId,
        selectedReason = validReason,
        matchedProfile = matchedProfile ?: current.matchedProfile,
        suppressedAutoMatchMobile = "",
      )
    }

    fun resolveHostFromPayload(
      payload: SelfCheckInPayload,
      staff: List<StaffMember>,
      fallbackHostId: String,
    ): StaffMember? {
      val hosts = staff.filter { it.activeAccount && it.hostListed }
      if (hosts.isEmpty()) return null
      if (payload.hostId.isNotBlank()) {
        hosts.firstOrNull { it.id == payload.hostId }?.let { return it }
      }
      if (payload.hostName.isNotBlank()) {
        val q = payload.hostName.trim().lowercase()
        hosts.firstOrNull {
          it.shownAs.trim().lowercase() == q ||
            it.fullName.trim().lowercase() == q ||
            it.shownAs.trim().lowercase().contains(q) ||
            it.fullName.trim().lowercase().contains(q)
        }?.let { return it }
      }
      return hosts.firstOrNull { it.id == fallbackHostId } ?: hosts.firstOrNull()
    }

    fun mobileMatches(storedMobile: String, inputDigits: String): Boolean {
      val cleanStored = storedMobile.filter { it.isDigit() }
      val cleanInput = inputDigits.filter { it.isDigit() }
      if (cleanStored.length < 10 || cleanInput.length < 10) return false
      if (cleanStored == "919999999999" || cleanInput == "919999999999") return false
      if (cleanStored == cleanInput) return true
      return cleanStored.takeLast(10) == cleanInput.takeLast(10)
    }

    fun mergeSelfCheckInIntoForm(
      current: ReceptionFormState,
      payload: SelfCheckInPayload,
      profiles: List<VisitorProfile>,
      hosts: List<StaffMember>,
      reasons: List<VisitReason>,
    ): ReceptionFormState {
      val digits = payload.mobile.filter { it.isDigit() }.take(15)
      val matchedProfile =
        profiles.firstOrNull { profile ->
          (digits.length >= 10 && mobileMatches(profile.mobile, digits)) ||
            (payload.email.isNotBlank() && profile.email.equals(payload.email, ignoreCase = true))
        }
      val resolvedHost =
        resolveHostFromPayload(
          payload = payload,
          staff = hosts,
          fallbackHostId = matchedProfile?.lastHostStaffId ?: current.selectedHostId,
        )
      val matchedReason =
        payload.reason.trim().takeIf { it.isNotBlank() }?.let { raw ->
          reasons.firstOrNull { it.label.equals(raw, ignoreCase = true) }?.label ?: raw
        }
          ?: matchedProfile?.lastReason?.takeIf { it.isNotBlank() }
          ?: current.selectedReason

      return current.copy(
        mobile = digits.ifBlank { current.mobile },
        name = payload.name.ifBlank { matchedProfile?.name ?: current.name },
        company = payload.company.ifBlank { matchedProfile?.company ?: current.company },
        email = payload.email.ifBlank { matchedProfile?.email ?: current.email },
        dob = payload.dob.ifBlank { matchedProfile?.dob ?: current.dob },
        remark = payload.remark.ifBlank { current.remark },
        selectedHostId = resolvedHost?.id ?: current.selectedHostId,
        selectedReason = matchedReason,
        matchedProfile = matchedProfile ?: current.matchedProfile,
        suppressedAutoMatchMobile = "",
      )
    }

    fun validateReceptionCheckIn(
      form: ReceptionFormState,
      staff: List<StaffMember>,
    ): String? {
      val host = staff.firstOrNull { it.id == form.selectedHostId && it.activeAccount }
        ?: return "Please select a person to meet."
      if (host.id.isBlank()) return "Please select a person to meet."
      if (form.mobile.filter { it.isDigit() }.length < 10) {
        return "Enter a valid 10-digit mobile number."
      }
      if (form.name.trim().isEmpty()) {
        return "Enter the visitor's name."
      }
      if (form.selectedReason.trim().isEmpty()) {
        return "Select a reason to meet."
      }
      return null
    }

    fun resolveHostAvailabilityVisual(item: HostBoardItem): Pair<String, AvailabilityTone> {
      val baseAvail = item.staff.parsedAvailability()
      return if (item.inMeetingVisit != null) {
        "In meeting · ${item.meetingMinutes}m" to AvailabilityTone.ORANGE
      } else {
        baseAvail.receptionLabel to baseAvail.tone
      }
    }

    fun buildHostBoard(
      staff: List<StaffMember>,
      visits: List<Visit>,
      now: Long,
    ): List<HostBoardItem> {
      val hosts = staff.filter { it.activeAccount && it.hostListed }
      return hosts.map { host ->
        val activeMeeting =
          visits.firstOrNull {
            it.hostStaffId == host.id && it.parsedStatus() == VisitStatus.IN_MEETING
          }
        val waitingCount =
          visits.count {
            it.hostStaffId == host.id && it.parsedStatus() == VisitStatus.WAITING
          }
        val meetingMinutes =
          DeskFormatters.minutesBetween(activeMeeting?.meetingStartedAt?.toDate()?.time, now)
        HostBoardItem(
          staff = host,
          inMeetingVisit = activeMeeting,
          waitingCount = waitingCount,
          meetingMinutes = meetingMinutes,
        )
      }
    }

    fun normalizeShownAs(fullName: String, shownAs: String): String {
      val cleanFull = fullName.trim().take(80)
      return shownAs.trim().take(60).ifBlank {
        cleanFull.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "Staff"
      }
    }

    fun buildRecentVisitors(
      profiles: List<VisitorProfile>,
      visits: List<Visit>,
      limit: Int = 15,
    ): List<VisitorProfile> {
      if (limit <= 0) return emptyList()
      val seenKeys = mutableSetOf<String>()
      val ordered = mutableListOf<VisitorProfile>()

      fun dedupKey(name: String, company: String, mobile: String, email: String): String {
        val digits = mobile.filter { it.isDigit() }.takeUnless { it == "919999999999" }.orEmpty()
        if (digits.length >= 6) return "m:$digits"
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isNotBlank()) return "e:$cleanEmail"
        return "n:${name.trim().lowercase()}|${company.trim().lowercase()}"
      }

      fun findMatchingProfile(visit: Visit): VisitorProfile? {
        val visitDigits =
          visit.visitorMobile.filter { it.isDigit() }.takeUnless { it == "919999999999" }.orEmpty()
        if (visitDigits.length >= 6) {
          profiles.firstOrNull { it.mobile.filter { ch -> ch.isDigit() } == visitDigits }?.let {
            return it
          }
        }
        val visitEmail = visit.visitorEmail.trim().lowercase()
        if (visitEmail.isNotBlank()) {
          profiles.firstOrNull { it.email.trim().lowercase() == visitEmail }?.let { return it }
        }
        val visitName = visit.visitorName.trim()
        val visitComp = visit.visitorCompany.trim()
        return profiles.firstOrNull {
          it.name.trim().equals(visitName, ignoreCase = true) &&
            it.company.trim().equals(visitComp, ignoreCase = true)
        }
      }

      val sortedVisits =
        visits
          .filter { it.visitorName.isNotBlank() }
          .sortedByDescending { it.arrivedAt?.toDate()?.time ?: 0L }

      for (visit in sortedVisits) {
        if (ordered.size >= limit) break
        val matched = findMatchingProfile(visit)
        val candidate =
          if (matched != null) {
            matched.copy(
              name = matched.name.ifBlank { visit.visitorName },
              company = matched.company.ifBlank { visit.visitorCompany },
              mobile =
                if (matched.mobile.isBlank() || matched.mobile == "919999999999") {
                  visit.visitorMobile
                } else {
                  matched.mobile
                },
              email = matched.email.ifBlank { visit.visitorEmail },
              lastHostStaffId = matched.lastHostStaffId.ifBlank { visit.hostStaffId },
              lastReason = matched.lastReason.ifBlank { visit.reason },
            )
          } else {
            VisitorProfile(
              id = "",
              mobile = visit.visitorMobile,
              name = visit.visitorName,
              company = visit.visitorCompany,
              email = visit.visitorEmail,
              visitorJobProfile = visit.visitorJobProfile,
              remark = visit.remark,
              visitCount = visit.visitNumber.coerceAtLeast(1L),
              lastHostStaffId = visit.hostStaffId,
              lastReason = visit.reason,
              updatedAt = visit.arrivedAt,
            )
          }
        val key = dedupKey(candidate.name, candidate.company, candidate.mobile, candidate.email)
        if (seenKeys.add(key)) {
          ordered += candidate
        }
      }

      if (ordered.size < limit) {
        val remainingProfiles =
          profiles
            .filter { it.name.isNotBlank() }
            .sortedWith(
              compareByDescending<VisitorProfile> { it.updatedAt?.toDate()?.time ?: 0L }
                .thenByDescending { it.visitCount }
                .thenBy { it.name.lowercase() }
            )
        for (profile in remainingProfiles) {
          if (ordered.size >= limit) break
          val key = dedupKey(profile.name, profile.company, profile.mobile, profile.email)
          if (seenKeys.add(key)) {
            ordered += profile
          }
        }
      }

      return ordered
    }
  }
}
