package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alerts.InAppAlertDedupStore
import com.example.alerts.VisitAlertEvent
import com.example.alerts.VisitorAlertManager
import com.example.data.model.AppTab
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

sealed interface WorkspaceState {
  data object Loading : WorkspaceState
  data object NeedsSetup : WorkspaceState
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

  private var visitorSearchJob: Job? = null

  private val _isSettingUpWorkspace = MutableStateFlow(false)
  val isSettingUpWorkspace: StateFlow<Boolean> = _isSettingUpWorkspace.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

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
        delay(20_000L)
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
              flowOf(WorkspaceState.NeedsSetup)
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

  private fun observeWorkspaceSnapshot(membership: OfficeMembership): Flow<WorkspaceState> {
    val officeId = membership.officeId
    val role = membership.parsedRole()
    val isReceptionOrAdmin = role == StaffRole.ADMIN || role == StaffRole.RECEPTION

    val officeFlow = repository.observeOffice(officeId).unwrapToUiState("office")
    val staffFlow = repository.observeStaff(officeId, normalizedEmail).unwrapToUiState("staff")
    val reasonsFlow = repository.observeReasons(officeId, normalizedEmail).unwrapToUiState("reasons")
    val visitsFlow = repository.observeVisits(officeId, normalizedEmail).unwrapToUiState("visits")
    val profilesFlow =
      if (isReceptionOrAdmin) {
        repository.observeVisitorProfiles(officeId, normalizedEmail).unwrapToUiState("visitor profiles")
      } else {
        flowOf(UiState.Success(emptyList()))
      }

    return combine(
      officeFlow,
      staffFlow,
      reasonsFlow,
      visitsFlow,
      profilesFlow,
    ) { officeSt, staffSt, reasonsSt, visitsSt, profilesSt ->
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
      val staff = (staffSt as UiState.Success).data
      val reasons = (reasonsSt as UiState.Success).data
      val visits = (visitsSt as UiState.Success).data
      val profiles = (profilesSt as UiState.Success).data

      val me = staff.firstOrNull { it.email.equals(normalizedEmail, ignoreCase = true) }
      if (me != null && !me.activeAccount) {
        return@combine WorkspaceState.Deactivated(office?.name ?: membership.officeName)
      }
      if (me != null && me.uid.isBlank()) {
        viewModelScope.launch { repository.claimStaffUidIfNeeded(officeId, me) }
      }

      val resolvedRole = me?.parsedRole() ?: role
      val isReceptionOrAdminRole = resolvedRole == StaffRole.ADMIN || resolvedRole == StaffRole.RECEPTION
      val currentVisitsMap = visits.associateBy { it.id }
      if (lastObservedOfficeId != officeId) {
        lastObservedOfficeId = officeId
        lastVisitsById = currentVisitsMap
      } else {
        val prevMap = lastVisitsById
        if (prevMap != null) {
          visits.forEach { visit ->
            val event =
              VisitorAlertManager.detectVisitAlert(
                officeId = officeId,
                userEmail = normalizedEmail,
                isReceptionOrAdmin = isReceptionOrAdminRole,
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
          visits = visits,
          visitorProfiles = profiles,
          currentStaff = me,
          activeRole = resolvedRole,
          isDeactivated = false,
        )

      // One-time auto-seed for existing Admin workspaces missing new staff (e.g. Himanshi Gupta) or past 43 visits
      if (resolvedRole == StaffRole.ADMIN && office != null && autoSeedCheckedOffices.add(officeId)) {
        val missingSeed =
          staff.none { it.id == "staff_archpoint_11" } ||
            visits.none { it.id.startsWith("visit_past_") }
        if (missingSeed) {
          viewModelScope.launch {
            if (!preferences.hasAttemptedDefaultSeed("v2_$officeId")) {
              preferences.markDefaultSeedAttempted("v2_$officeId")
              repository.importDefaultArchPointData(
                office = office,
                currentStaff = staff,
                currentReasons = reasons,
                currentProfiles = profiles,
                currentVisits = visits,
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

  fun availableTabsFor(snapshot: DeskSnapshot): List<AppTab> {
    val role = snapshot.activeRole
    val isAlsoHost = snapshot.currentStaff?.hostListed == true
    return buildList {
      if (role == StaffRole.RECEPTION || role == StaffRole.ADMIN) {
        add(AppTab.RECEPTION)
      }
      if (role == StaffRole.HOST || isAlsoHost) {
        add(AppTab.MY_VISITORS)
      }
      if (role == StaffRole.RECEPTION || role == StaffRole.ADMIN) {
        add(AppTab.VISITOR_LOG)
      }
      if (role == StaffRole.ADMIN) {
        add(AppTab.ADMIN)
      }
      if (isEmpty()) {
        add(AppTab.MY_VISITORS)
      }
      add(AppTab.PROFILE)
    }
  }

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
    val current = _receptionForm.value
    val validHostId =
      hosts.firstOrNull { it.id == profile.lastHostStaffId }?.id ?: current.selectedHostId
    val usableMobile = if (profile.mobile == "919999999999") "" else profile.mobile
    _receptionForm.value =
      current.copy(
        mobile = usableMobile,
        name = profile.name,
        company = profile.company,
        email = profile.email,
        dob = profile.dob.ifBlank { current.dob },
        selectedHostId = validHostId,
        selectedReason = profile.lastReason.ifBlank { current.selectedReason },
        matchedProfile = profile,
        suppressedAutoMatchMobile = "",
      )
    clearVisitorSearch()
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

  fun clearReceptionForm(defaultReason: String, defaultHostId: String) {
    clearVisitorSearch()
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
    viewModelScope.launch {
      repository
        .sendVisitorIn(officeId, visit.id, note)
        .onSuccess { _feedback.tryEmit("Sending ${visit.visitorName} in to ${visit.hostShownAs}") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Could not update visit") }
    }
  }

  fun askVisitorToWait(officeId: String, visit: Visit) {
    viewModelScope.launch {
      repository
        .askVisitorToWait(officeId, visit.id)
        .onSuccess { _feedback.tryEmit("Asked reception to have ${visit.visitorName} wait 10 min") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Could not update visit") }
    }
  }

  fun denyVisitorToday(officeId: String, visit: Visit) {
    viewModelScope.launch {
      repository
        .denyVisitorToday(officeId, visit.id)
        .onSuccess { _feedback.tryEmit("Marked ${visit.visitorName} as Not today") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Could not update visit") }
    }
  }

  fun markMeetingDone(officeId: String, visit: Visit) {
    viewModelScope.launch {
      repository
        .markMeetingDone(officeId, visit.id)
        .onSuccess { _feedback.tryEmit("Meeting with ${visit.visitorName} marked Done") }
        .onFailure { _feedback.tryEmit(it.localizedMessage ?: "Could not mark done") }
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

  private companion object {
    const val TAG = "MainViewModel"
  }
}
