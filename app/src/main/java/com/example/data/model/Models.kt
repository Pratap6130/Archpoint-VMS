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

enum class AvailabilityTone {
  GREEN,
  ORANGE,
  RED,
}

enum class HostAvailability(
  val wireValue: String,
  val label: String,
  val receptionLabel: String,
  val tone: AvailabilityTone,
) {
  AVAILABLE("Available", "Free", "Free", AvailabilityTone.GREEN),
  BUSY("Busy", "Busy", "Busy", AvailabilityTone.ORANGE),
  AWAY("Away", "Away", "Away", AvailabilityTone.RED);

  companion object {
    fun fromWire(raw: String?): HostAvailability =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: AVAILABLE

    fun resolveTone(availability: HostAvailability, inMeeting: Boolean = false): AvailabilityTone =
      if (inMeeting) AvailabilityTone.ORANGE else availability.tone
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
  PR("pr"),
  ADMIN("admin"),
  PROFILE("profile"),
}

enum class PRChannel(val wireValue: String, val label: String) {
  PRESS("Press / Media", "Press"),
  EVENT("Event", "Event"),
  SOCIAL("Social & Digital", "Social"),
  VIP_OUTREACH("VIP Outreach", "VIP"),
  FESTIVE_GIFTING("Festive Gifting", "Festive");

  companion object {
    fun fromWire(raw: String?): PRChannel =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: EVENT
  }
}

enum class PRCampaignStatus(val wireValue: String, val label: String) {
  PLANNED("Planned", "Planned"),
  ACTIVE("Active", "Active"),
  LIVE("Live", "Live"),
  COMPLETED("Completed", "Done");

  companion object {
    fun fromWire(raw: String?): PRCampaignStatus =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: PLANNED
  }
}

enum class PRContactTier(val wireValue: String, val label: String) {
  VIP("VIP", "VIP"),
  MEDIA("Media", "Media"),
  PARTNER("Partner", "Partner");

  companion object {
    fun fromWire(raw: String?): PRContactTier =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: VIP
  }
}

enum class PRFollowUpStatus(val wireValue: String, val label: String) {
  WARM("Warm", "Warm"),
  FOLLOW_UP_DUE("Follow-up Due", "Follow-up Due"),
  SCHEDULED("Scheduled", "Scheduled"),
  CONNECTED("Connected", "Connected");

  companion object {
    fun fromWire(raw: String?): PRFollowUpStatus =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: WARM
  }
}

enum class PRGiftDirection(val wireValue: String, val label: String) {
  SENT("Sent", "Sent"),
  RECEIVED("Received", "Received");

  companion object {
    fun fromWire(raw: String?): PRGiftDirection =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: SENT
  }
}

enum class PRGiftStatus(val wireValue: String, val label: String) {
  PREPARED("Prepared", "Prepared"),
  DISPATCHED("Dispatched", "Dispatched"),
  DELIVERED("Delivered", "Delivered"),
  ACKNOWLEDGED("Acknowledged", "Ack'd");

  companion object {
    fun fromWire(raw: String?): PRGiftStatus =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: PREPARED
  }
}

enum class AppointmentStatus(val wireValue: String, val label: String) {
  SCHEDULED("Scheduled", "Scheduled"),
  CHECKED_IN("Checked in", "Arrived"),
  COMPLETED("Completed", "Done"),
  CANCELLED("Cancelled", "Cancelled");

  companion object {
    fun fromWire(raw: String?): AppointmentStatus =
      entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: SCHEDULED
  }
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
  val receptionEmails: List<String> = emptyList(),
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
  val availabilityStatus: String = HostAvailability.AVAILABLE.wireValue,
  val listOrder: Long = 1L,
  val hostListed: Boolean = true,
  val activeAccount: Boolean = true,
  val uid: String = "",
  val photoUrl: String = "",
  val officeOwnerUid: String = "",
  val allowedReaderEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun parsedRole(): StaffRole = StaffRole.fromWire(role)

  fun parsedAvailability(): HostAvailability = HostAvailability.fromWire(availabilityStatus)

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

@Keep
@IgnoreExtraProperties
data class Appointment(
  @DocumentId val id: String = "",
  val visitorName: String = "",
  val visitorMobile: String = "",
  val visitorCompany: String = "",
  val visitorEmail: String = "",
  val purpose: String = "Meeting",
  val location: String = "",
  val scheduledDateIso: String = "",
  val scheduledTimeLabel: String = "",
  val scheduledAtMillis: Long = 0L,
  val notes: String = "",
  val hostStaffId: String = "",
  val hostShownAs: String = "",
  val hostEmail: String = "",
  val hostCabin: String = "",
  val status: String = AppointmentStatus.SCHEDULED.wireValue,
  val bookedByRole: String = "",
  val calendarEventId: String = "",
  val calendarHtmlLink: String = "",
  val calendarSyncedEmail: String = "",
  val createdByUid: String = "",
  val createdByEmail: String = "",
  val officeOwnerUid: String = "",
  val allowedReaderEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun parsedStatus(): AppointmentStatus = AppointmentStatus.fromWire(status)
}

@Keep
@IgnoreExtraProperties
data class PRCampaign(
  @DocumentId val id: String = "",
  val title: String = "",
  val channel: String = PRChannel.EVENT.wireValue,
  val status: String = PRCampaignStatus.PLANNED.wireValue,
  val targetDateLabel: String = "",
  val location: String = "",
  val audienceSummary: String = "",
  val notes: String = "",
  val ownerStaffId: String = "",
  val ownerName: String = "",
  val ownerEmail: String = "",
  val createdByUid: String = "",
  val createdByEmail: String = "",
  val officeOwnerUid: String = "",
  val allowedReaderEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun parsedChannel(): PRChannel = PRChannel.fromWire(channel)
  fun parsedStatus(): PRCampaignStatus = PRCampaignStatus.fromWire(status)
}

@Keep
@IgnoreExtraProperties
data class PRContact(
  @DocumentId val id: String = "",
  val name: String = "",
  val organization: String = "",
  val designation: String = "",
  val tier: String = PRContactTier.VIP.wireValue,
  val mobile: String = "",
  val email: String = "",
  val followUpStatus: String = PRFollowUpStatus.WARM.wireValue,
  val nextFollowUpDate: String = "",
  val photoUrl: String = "",
  val dob: String = "",
  val anniversaryDate: String = "",
  val lastWishedYear: Long = 0L,
  val specialGiftPreference: String = "",
  val cityOrAddress: String = "",
  val notes: String = "",
  val ownerStaffId: String = "",
  val ownerName: String = "",
  val ownerEmail: String = "",
  val createdByUid: String = "",
  val createdByEmail: String = "",
  val officeOwnerUid: String = "",
  val allowedReaderEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun parsedTier(): PRContactTier = PRContactTier.fromWire(tier)
  fun parsedFollowUpStatus(): PRFollowUpStatus = PRFollowUpStatus.fromWire(followUpStatus)
}

@Keep
@IgnoreExtraProperties
data class PRGift(
  @DocumentId val id: String = "",
  val recipientOrSender: String = "",
  val company: String = "",
  val itemTitle: String = "",
  val direction: String = PRGiftDirection.SENT.wireValue,
  val status: String = PRGiftStatus.PREPARED.wireValue,
  val occasion: String = "",
  val dateLabel: String = "",
  val notes: String = "",
  val ownerStaffId: String = "",
  val ownerName: String = "",
  val ownerEmail: String = "",
  val createdByUid: String = "",
  val createdByEmail: String = "",
  val officeOwnerUid: String = "",
  val allowedReaderEmails: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun parsedDirection(): PRGiftDirection = PRGiftDirection.fromWire(direction)
  fun parsedStatus(): PRGiftStatus = PRGiftStatus.fromWire(status)
}
