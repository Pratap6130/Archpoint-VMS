package com.example.ui.viewmodel

import android.content.Context
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
import com.example.data.model.AppTab
import com.example.data.model.HostAvailability
import com.example.data.model.Office
import com.example.data.model.OfficeMembership
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.UiState
import com.example.data.model.Visit
import com.example.data.model.VisitReason
import com.example.data.model.VisitStatus
import com.example.data.model.VisitorProfile
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.VisitorDeskRepository
import com.example.util.DeskFormatters
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
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

data class DeskSnapshot(
  val office: Office?,
  val staff: List<StaffMember>,
  val reasons: List<VisitReason>,
  val visits: List<Visit>,
  val visitorProfiles: List<VisitorProfile>,
  val currentStaff: StaffMember?,
  val activeRole: StaffRole,
  val isDeactivated: Boolean,
)

sealed interface CardScanState {
  data object Idle : CardScanState
  data class Scanning(val preview: ImageBitmap?) : CardScanState
  data class Success(val card: ScannedVisitorCard, val preview: ImageBitmap?) : CardScanState
  data class Error(val message: String, val preview: ImageBitmap?) : CardScanState
}

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

    return combine(
      coreFlow,
      _pendingAvailability,
      _pendingVisitOverrides,
    ) { core, pendingAvail, pendingVisits ->
      val officeSt = core.officeSt
      val staffSt = core.staffSt
      val reasonsSt = core.reasonsSt
      val visitsSt = core.visitsSt
      val profilesSt = core.profilesSt
      val firstError =
        listOf(officeSt, staffSt, reasonsSt, visitsSt, profilesSt)
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
          profilesSt is UiState.Loading
      ) {
        return@combine WorkspaceState.Loading
      }

      val office = (officeSt as UiState.Success).data
      val rawStaff = (staffSt as UiState.Success).data
      val reasons = (reasonsSt as UiState.Success).data
      val rawVisits = (visitsSt as UiState.Success).data
      val profiles = (profilesSt as UiState.Success).data

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
        )

      // One-time auto-seed / access-field sync for existing Admin workspaces
      if (resolvedRole == StaffRole.ADMIN && office != null && autoSeedCheckedOffices.add(officeId)) {
        val missingSeed =
          staff.none { it.id == "staff_archpoint_11" } ||
            rawVisits.none { it.id.startsWith("visit_past_") } ||
            (staff.any { it.activeAccount && it.email.isNotBlank() && it.parsedRole() == StaffRole.RECEPTION } &&
              office.receptionEmails.isEmpty())
        if (missingSeed) {
          viewModelScope.launch {
            if (!preferences.hasAttemptedDefaultSeed("v2_$officeId")) {
              preferences.markDefaultSeedAttempted("v2_$officeId")
              repository.importDefaultArchPointData(
                office = office,
                currentStaff = staff,
                currentReasons = reasons,
                currentProfiles = profiles,
                currentVisits = rawVisits,
              ).onSuccess { summary ->
                if (summary.totalAdded > 0) {
                  _feedback.tryEmit(
                    "Loaded ${summary.staffAdded} team members & ${summary.visitsAdded} past visitor records"
                  )
                }
              }
            }
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
        preferences.markDefaultSeedAttempted("v2_$newOfficeId")
        _feedback.tryEmit("Office workspace ready with ArchPoint team & past records")
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
        repository.importDefaultArchPointData(
          office = office,
          currentStaff = snapshot.staff,
          currentReasons = snapshot.reasons,
          currentProfiles = snapshot.visitorProfiles,
          currentVisits = snapshot.visits,
        )
      _isBusy.value = false
      result
        .onSuccess { summary ->
          preferences.markDefaultSeedAttempted("v2_${office.id}")
          if (summary.totalAdded == 0) {
            _feedback.tryEmit("ArchPoint team & past records are already up to date")
          } else {
            _feedback.tryEmit(
              "Imported ${summary.staffAdded} staff, ${summary.visitsAdded} visits, ${summary.profilesAdded} profiles"
            )
          }
        }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Failed to sync ArchPoint records") }
    }
  }

  // --- RECEPTION FORM HANDLERS ---

  fun onMobileChanged(input: String, profiles: List<VisitorProfile>, hosts: List<StaffMember>) {
    val digits = input.filter { it.isDigit() }.take(15)
    val current = _receptionForm.value
    if (digits.length >= 10 && digits != "919999999999" && digits != current.suppressedAutoMatchMobile) {
      val match = profiles.firstOrNull { it.mobile == digits }
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
              val current = _receptionForm.value
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

              _receptionForm.value =
                current.copy(
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
    val host = snapshot.staff.firstOrNull { it.id == form.selectedHostId && it.activeAccount }
    if (host == null) {
      _feedback.tryEmit("Please select a person to meet.")
      return
    }
    if (form.mobile.filter { it.isDigit() }.length < 10) {
      _feedback.tryEmit("Enter a valid 10-digit mobile number.")
      return
    }
    if (form.name.trim().isEmpty()) {
      _feedback.tryEmit("Enter the visitor's name.")
      return
    }
    if (form.selectedReason.trim().isEmpty()) {
      _feedback.tryEmit("Select a reason to meet.")
      return
    }

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

  // --- COMPUTED BOARD HELPERS ---

  fun buildHostBoard(snapshot: DeskSnapshot, now: Long): List<HostBoardItem> {
    val hosts = snapshot.staff.filter { it.activeAccount && it.hostListed }
    return hosts.map { host ->
      val activeMeeting =
        snapshot.visits.firstOrNull {
          it.hostStaffId == host.id && it.parsedStatus() == VisitStatus.IN_MEETING
        }
      val waitingCount =
        snapshot.visits.count {
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

    fun resolveTabsForRole(role: StaffRole, hostListed: Boolean): List<AppTab> =
      when (role) {
        StaffRole.RECEPTION ->
          buildList {
            add(AppTab.RECEPTION)
            if (hostListed) add(AppTab.MY_VISITORS)
            add(AppTab.PROFILE)
          }
        StaffRole.ADMIN ->
          buildList {
            add(AppTab.ADMIN)
            add(AppTab.VISITOR_LOG)
            if (hostListed) add(AppTab.MY_VISITORS)
            add(AppTab.PROFILE)
          }
        StaffRole.HOST ->
          listOf(
            AppTab.MY_VISITORS,
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
