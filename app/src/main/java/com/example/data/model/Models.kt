package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StaffRole(val wireValue: String, val label: String) {
  ADMIN("admin", "Admin"),
  RECEPTION("reception", "Reception"),
  HOST("host", "Host");

  companion object {
    fun fromWire(raw: String?): StaffRole =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: HOST
  }
}

enum class VisitStatus(val wireValue: String) {
  WAITING("Waiting"),
  IN_MEETING("In meeting"),
  DONE("Done"),
  DENIED("Denied");

  companion object {
    fun fromWire(raw: String?): VisitStatus =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: WAITING
  }
}

enum class AppTab(val route: String) {
  RECEPTION("reception"),
  MY_VISITORS("my_visitors"),
  VISITOR_LOG("visitor_log"),
  ADMIN("admin"),
  PROFILE("profile"),
}

enum class AppThemeMode(val wireValue: String, val label: String) {
  SYSTEM("system", "Auto"),
  LIGHT("light", "Light"),
  DARK("dark", "Dark");

  companion object {
    fun fromWire(raw: String?): AppThemeMode =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: DARK
  }
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class OfficeMembership(
  @DocumentId val id: String = "",
  val officeId: String = "",
  val officeName: String = "",
  val staffId: String = "",
  val email: String = "",
  val role: String = StaffRole.HOST.wireValue,
  val activeAccount: Boolean = true,
  val officeOwnerUid: String = "",
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun parsedRole(): StaffRole = StaffRole.fromWire(role)
}

@Keep
@IgnoreExtraProperties
data class Office(
  @DocumentId val id: String = "",
  val name: String = "",
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val activeMemberEmails: List<String> = emptyList(),
  val receptionAdminEmails: List<String> = emptyList(),
  val adminEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StaffMember(
  @DocumentId val id: String = "",
  val fullName: String = "",
  val shownAs: String = "",
  val email: String = "",
  val role: String = StaffRole.HOST.wireValue,
  val designation: String = "",
  val department: String = "",
  val floor: String = "",
  val cabin: String = "",
  val otherLocation: String = "",
  val requiredThings: String = "",
  val notes: String = "",
  val listOrder: Long = 1L,
  val hostListed: Boolean = true,
  val activeAccount: Boolean = true,
  val uid: String = "",
  val officeOwnerUid: String = "",
  val allowedReaderEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun parsedRole(): StaffRole = StaffRole.fromWire(role)

  fun formattedSeating(): String {
    val parts = mutableListOf<String>()
    val cleanFloor = floor.trim()
    if (cleanFloor.isNotEmpty()) {
      parts += if (cleanFloor.contains("floor", ignoreCase = true)) cleanFloor else "$cleanFloor floor"
    }
    if (cabin.trim().isNotEmpty()) {
      parts += cabin.trim()
    }
    return parts.joinToString(" · ")
  }
}

@Keep
@IgnoreExtraProperties
data class VisitReason(
  @DocumentId val id: String = "",
  val label: String = "",
  val shown: Boolean = true,
  val sortOrder: Long = 0L,
  val officeOwnerUid: String = "",
  val allowedReaderEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Visit(
  @DocumentId val id: String = "",
  val visitorName: String = "",
  val visitorMobile: String = "",
  val visitorCompany: String = "",
  val visitorEmail: String = "",
  val visitorType: String = "",
  val visitorJobProfile: String = "",
  val remark: String = "",
  val reason: String = "",
  val hostStaffId: String = "",
  val hostShownAs: String = "",
  val hostEmail: String = "",
  val hostCabin: String = "",
  val visitNumber: Long = 1L,
  val status: String = VisitStatus.WAITING.wireValue,
  val waitNote: String = "",
  val remindedCount: Long = 0L,
  @ServerTimestamp val arrivedAt: Timestamp? = null,
  @ServerTimestamp val waitStartedAt: Timestamp? = null,
  val meetingStartedAt: Timestamp? = null,
  val endedAt: Timestamp? = null,
  val createdByUid: String = "",
  val createdByEmail: String = "",
  val officeOwnerUid: String = "",
  val allowedReaderEmails: List<String> = emptyList(),
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun parsedStatus(): VisitStatus = VisitStatus.fromWire(status)
}

@Keep
@IgnoreExtraProperties
data class VisitorProfile(
  @DocumentId val id: String = "",
  val mobile: String = "",
  val name: String = "",
  val company: String = "",
  val email: String = "",
  val dob: String = "",
  val visitorType: String = "",
  val visitorJobProfile: String = "",
  val remark: String = "",
  val visitCount: Long = 1L,
  val lastHostStaffId: String = "",
  val lastReason: String = "",
  val officeOwnerUid: String = "",
  val allowedReaderEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
