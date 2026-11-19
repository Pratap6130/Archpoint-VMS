package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.ArchPointSeedData
import com.example.data.model.HostAvailability
import com.example.data.model.Office
import com.example.data.model.OfficeMembership
import com.example.data.model.PRCampaign
import com.example.data.model.PRCampaignStatus
import com.example.data.model.PRContact
import com.example.data.model.PRFollowUpStatus
import com.example.data.model.PRGift
import com.example.data.model.PRGiftStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.Visit
import com.example.data.model.VisitReason
import com.example.data.model.VisitStatus
import com.example.data.model.VisitorProfile
import com.example.util.DeskFormatters
import com.example.util.StaffPhotoCodec
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class ImportSummary(
  val staffAdded: Int,
  val reasonsAdded: Int,
  val visitsAdded: Int,
  val profilesAdded: Int,
  val visitsRemoved: Int = 0,
  val profilesRemoved: Int = 0,
) {
  val totalAdded: Int
    get() = staffAdded + reasonsAdded + visitsAdded + profilesAdded
}

class VisitorDeskRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  private fun requireVerifiedEmail(): String {
    val user = auth.currentUser
      ?: throw IllegalStateException("User must be signed in with Google.")
    val email = user.email?.trim()?.lowercase()
      ?: throw IllegalStateException("Google account email is required.")
    return email
  }

  private fun <T> Flow<T>.withFirestoreLogging(
    operationType: OperationType,
    path: String,
    uid: String,
  ): Flow<T> = catch { error ->
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path)
    }
    throw error
  }

  private class BatchQueue(private val db: FirebaseFirestore) {
    private val ops = mutableListOf<(WriteBatch) -> Unit>()

    fun enqueue(op: (WriteBatch) -> Unit) {
      ops += op
    }

    suspend fun commitAll() {
      if (ops.isEmpty()) return
      ops.chunked(350).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { it(batch) }
        batch.commit().await()
      }
    }
  }

  fun observeMemberships(email: String): Flow<List<OfficeMembership>> = flow {
    val uid = requireUserId()
    val cleanEmail = email.trim().lowercase()
    val path = "officeMemberships"
    emitAll(
      db.collection(path)
        .whereEqualTo("email", cleanEmail)
        .snapshots()
        .map { snap ->
          snap.toObjects(OfficeMembership::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  fun observeOffice(officeId: String): Flow<Office?> = flow {
    val uid = requireUserId()
    val path = "offices/$officeId"
    emitAll(
      db.collection("offices")
        .document(officeId)
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(Office::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .withFirestoreLogging(OperationType.GET, path, uid)
    )
  }

  fun observeStaff(officeId: String, userEmail: String): Flow<List<StaffMember>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    val path = "offices/$officeId/staff"
    emitAll(
      db.collection("offices")
        .document(officeId)
        .collection("staff")
        .whereArrayContains("allowedReaderEmails", cleanEmail)
        .snapshots()
        .map { snap ->
          snap.toObjects(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareBy<StaffMember> { it.listOrder }.thenBy { it.shownAs.lowercase() })
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  fun observeOwnStaffProfile(
    officeId: String,
    staffId: String,
    userEmail: String,
  ): Flow<List<StaffMember>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    if (staffId.isNotBlank()) {
      val path = "offices/$officeId/staff/$staffId"
      emitAll(
        db.collection("offices")
          .document(officeId)
          .collection("staff")
          .document(staffId)
          .snapshots()
          .map { snap ->
            if (snap.exists()) {
              listOfNotNull(
                snap.toObject(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              )
            } else {
              emptyList()
            }
          }
          .withFirestoreLogging(OperationType.GET, path, uid)
      )
    } else {
      val path = "offices/$officeId/staff"
      emitAll(
        db.collection("offices")
          .document(officeId)
          .collection("staff")
          .whereEqualTo("email", cleanEmail)
          .snapshots()
          .map { snap ->
            snap.toObjects(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          }
          .withFirestoreLogging(OperationType.LIST, path, uid)
      )
    }
  }

  fun observeReasons(officeId: String, userEmail: String): Flow<List<VisitReason>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    val path = "offices/$officeId/reasons"
    emitAll(
      db.collection("offices")
        .document(officeId)
        .collection("reasons")
        .whereArrayContains("allowedReaderEmails", cleanEmail)
        .snapshots()
        .map { snap ->
          snap.toObjects(VisitReason::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.sortOrder }
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  fun observeVisits(officeId: String, userEmail: String): Flow<List<Visit>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    val path = "offices/$officeId/visits"
    emitAll(
      db.collection("offices")
        .document(officeId)
        .collection("visits")
        .whereArrayContains("allowedReaderEmails", cleanEmail)
        .snapshots()
        .map { snap ->
          snap.toObjects(Visit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.arrivedAt?.toDate()?.time ?: 0L }
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  /**
   * Host-only query: fetches strictly visitor notifications associated with [userEmail]
   * (`hostEmail == cleanEmail`), never fetching global office visitor logs.
   */
  fun observeHostVisitorNotifications(officeId: String, userEmail: String): Flow<List<Visit>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    val path = "offices/$officeId/visits"
    emitAll(
      db.collection("offices")
        .document(officeId)
        .collection("visits")
        .whereEqualTo("hostEmail", cleanEmail)
        .snapshots()
        .map { snap ->
          snap.toObjects(Visit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.arrivedAt?.toDate()?.time ?: 0L }
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  fun observeVisitorProfiles(officeId: String, userEmail: String): Flow<List<VisitorProfile>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    val path = "offices/$officeId/visitorProfiles"
    emitAll(
      db.collection("offices")
        .document(officeId)
        .collection("visitorProfiles")
        .whereArrayContains("allowedReaderEmails", cleanEmail)
        .snapshots()
        .map { snap ->
          snap.toObjects(VisitorProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  fun observePrCampaigns(
    officeId: String,
    userEmail: String,
    hostOnly: Boolean,
  ): Flow<List<PRCampaign>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    val path = "offices/$officeId/prCampaigns"
    val base = db.collection("offices").document(officeId).collection("prCampaigns")
    val query =
      if (hostOnly) base.whereEqualTo("ownerEmail", cleanEmail)
      else base.whereArrayContains("allowedReaderEmails", cleanEmail)
    emitAll(
      query.snapshots()
        .map { snap ->
          snap.toObjects(PRCampaign::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  fun observePrContacts(
    officeId: String,
    userEmail: String,
    hostOnly: Boolean,
  ): Flow<List<PRContact>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    val path = "offices/$officeId/prContacts"
    val base = db.collection("offices").document(officeId).collection("prContacts")
    val query =
      if (hostOnly) base.whereEqualTo("ownerEmail", cleanEmail)
      else base.whereArrayContains("allowedReaderEmails", cleanEmail)
    emitAll(
      query.snapshots()
        .map { snap ->
          snap.toObjects(PRContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  fun observePrGifts(
    officeId: String,
    userEmail: String,
    hostOnly: Boolean,
  ): Flow<List<PRGift>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    val path = "offices/$officeId/prGifts"
    val base = db.collection("offices").document(officeId).collection("prGifts")
    val query =
      if (hostOnly) base.whereEqualTo("ownerEmail", cleanEmail)
      else base.whereArrayContains("allowedReaderEmails", cleanEmail)
    emitAll(
      query.snapshots()
        .map { snap ->
          snap.toObjects(PRGift::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  fun observeAppointments(
    officeId: String,
    userEmail: String,
    hostOnly: Boolean,
  ): Flow<List<Appointment>> = flow {
    val uid = requireUserId()
    val cleanEmail = userEmail.trim().lowercase()
    val path = "offices/$officeId/appointments"
    val base = db.collection("offices").document(officeId).collection("appointments")
    val query =
      if (hostOnly) base.whereEqualTo("hostEmail", cleanEmail)
      else base.whereArrayContains("allowedReaderEmails", cleanEmail)
    emitAll(
      query.snapshots()
        .map { snap ->
          snap.toObjects(Appointment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
              compareBy<Appointment> { it.scheduledAtMillis }
                .thenBy { it.scheduledDateIso }
                .thenBy { it.scheduledTimeLabel }
            )
        }
        .withFirestoreLogging(OperationType.LIST, path, uid)
    )
  }

  suspend fun searchReturningVisitors(officeId: String, query: String): Result<List<VisitorProfile>> {
    val cleanQuery = query.trim().lowercase()
    val path = "offices/$officeId/visitorProfiles"
    if (cleanQuery.isEmpty()) return Result.success(emptyList())
    return try {
      requireUserId()
      val cleanEmail = requireVerifiedEmail()
      val snapshot =
        db.collection("offices")
          .document(officeId)
          .collection("visitorProfiles")
          .whereArrayContains("allowedReaderEmails", cleanEmail)
          .get()
          .await()
      val profiles =
        snapshot.toObjects(VisitorProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      val matches =
        profiles
          .filter { profile ->
            profile.name.lowercase().contains(cleanQuery) ||
              profile.email.lowercase().contains(cleanQuery) ||
              profile.company.lowercase().contains(cleanQuery) ||
              profile.mobile.contains(cleanQuery)
          }
          .sortedWith(
            compareByDescending<VisitorProfile> {
              it.name.lowercase().startsWith(cleanQuery) ||
                it.email.lowercase().startsWith(cleanQuery) ||
                it.mobile.startsWith(cleanQuery)
            }.thenByDescending { it.visitCount }
              .thenBy { it.name.lowercase() }
          )
          .take(8)
      Result.success(matches)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path)
      Result.failure(e)
    }
  }

  suspend fun initializeOfficeWorkspace(
    officeName: String,
    adminFullName: String,
    adminShownAs: String,
    cabin: String,
  ): Result<String> {
    val cleanOffice = officeName.trim().take(100).ifBlank { "ArchPoint Jaipur" }
    val cleanName = adminFullName.trim().take(80).ifBlank { "Pratap" }
    val cleanShown = adminShownAs.trim().take(60).ifBlank { cleanName.split(" ").firstOrNull() ?: "Admin" }
    val cleanCabin = cabin.trim().take(60)
    val officeId = "office_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    var path = "offices/$officeId"
    return try {
      val uid = requireUserId()
      val email = requireVerifiedEmail()

      val officePayload = mapOf(
        "name" to cleanOffice,
        "ownerUid" to uid,
        "ownerEmail" to email,
        "activeMemberEmails" to listOf(email),
        "receptionAdminEmails" to listOf(email),
        "receptionEmails" to emptyList<String>(),
        "adminEmails" to listOf(email),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      db.collection("offices").document(officeId).set(officePayload).await()

      val ownerIsPratap = cleanName.contains("pratap", ignoreCase = true) || cleanShown.equals("pratap", ignoreCase = true)
      val ownerStaffId = if (ownerIsPratap) "staff_archpoint_09" else "staff_${UUID.randomUUID().toString().replace("-", "").take(12)}"
      val membershipId = "${officeId}__${ownerStaffId}"

      val ownerMember =
        StaffMember(
          id = ownerStaffId,
          fullName = cleanName,
          shownAs = cleanShown,
          email = email,
          role = StaffRole.ADMIN.wireValue,
          designation = if (ownerIsPratap) "Admin (software)" else "Admin",
          department = if (ownerIsPratap) "Technology" else "Management",
          floor = "",
          cabin = cleanCabin,
          otherLocation = "",
          requiredThings = "",
          notes = if (ownerIsPratap) "Manages staff and settings" else "",
          listOrder = if (ownerIsPratap) 10L else 1L,
          hostListed = false,
          activeAccount = true,
          uid = uid,
          officeOwnerUid = uid,
          allowedReaderEmails = listOf(email),
        )

      val queue = BatchQueue(db)
      path = "offices/$officeId/staff/$ownerStaffId"
      queue.enqueue { batch ->
        batch.set(
          db.collection("offices").document(officeId).collection("staff").document(ownerStaffId),
          mapOf(
            "fullName" to ownerMember.fullName,
            "shownAs" to ownerMember.shownAs,
            "email" to email,
            "role" to StaffRole.ADMIN.wireValue,
            "designation" to ownerMember.designation,
            "department" to ownerMember.department,
            "floor" to ownerMember.floor,
            "cabin" to ownerMember.cabin,
            "otherLocation" to ownerMember.otherLocation,
            "requiredThings" to ownerMember.requiredThings,
            "notes" to ownerMember.notes,
            "listOrder" to ownerMember.listOrder,
            "hostListed" to ownerMember.hostListed,
            "activeAccount" to true,
            "uid" to uid,
            "officeOwnerUid" to uid,
            "allowedReaderEmails" to listOf(email),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.set(
          db.collection("officeMemberships").document(membershipId),
          mapOf(
            "officeId" to officeId,
            "officeName" to cleanOffice,
            "staffId" to ownerStaffId,
            "email" to email,
            "role" to StaffRole.ADMIN.wireValue,
            "activeAccount" to true,
            "officeOwnerUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val office =
        Office(
          id = officeId,
          name = cleanOffice,
          ownerUid = uid,
          ownerEmail = email,
          activeMemberEmails = listOf(email),
          receptionAdminEmails = listOf(email),
          adminEmails = listOf(email),
        )

      enqueueDefaultArchPointCatalog(
        office = office,
        currentStaff = listOf(ownerMember),
        currentReasons = emptyList(),
        currentProfiles = emptyList(),
        currentVisits = emptyList(),
        actorUid = uid,
        actorEmail = email,
        queue = queue,
      )

      queue.commitAll()
      Result.success(officeId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun replaceArchPointVisitorData(
    office: Office,
    currentStaff: List<StaffMember>,
    currentReasons: List<VisitReason>,
    currentProfiles: List<VisitorProfile>,
    currentVisits: List<Visit>,
  ): Result<ImportSummary> {
    val path = "offices/${office.id}"
    return try {
      val uid = requireUserId()
      val email = requireVerifiedEmail()
      val officeRef = db.collection("offices").document(office.id)

      val remoteVisitsSnap =
        officeRef
          .collection("visits")
          .whereArrayContains("allowedReaderEmails", email)
          .get()
          .await()
      val remoteProfilesSnap =
        officeRef
          .collection("visitorProfiles")
          .whereArrayContains("allowedReaderEmails", email)
          .get()
          .await()

      val targetVisitIds = ArchPointSeedData.pastVisits.map { it.docId }.toSet()
      val targetProfileIds =
        ArchPointSeedData.pastVisits
          .map { past ->
            if (past.phone.isNotBlank() && past.phone != "919999999999") {
              past.phone
            } else {
              "prof_past_${past.rowNumber.toString().padStart(2, '0')}"
            }
          }
          .toSet()

      val visitIdsToDelete =
        (currentVisits.map { it.id } + remoteVisitsSnap.documents.map { it.id })
          .filter { it.isNotBlank() && it !in targetVisitIds }
          .distinct()
      val profileIdsToDelete =
        (currentProfiles.map { it.id } + remoteProfilesSnap.documents.map { it.id })
          .filter { it.isNotBlank() && it !in targetProfileIds }
          .distinct()

      // Write/overwrite target records first so a failed batch never leaves the database wiped empty.
      val writeQueue = BatchQueue(db)
      val summary =
        enqueueDefaultArchPointCatalog(
          office = office,
          currentStaff = currentStaff,
          currentReasons = currentReasons,
          currentProfiles = emptyList(),
          currentVisits = emptyList(),
          actorUid = uid,
          actorEmail = email,
          queue = writeQueue,
        )
      writeQueue.commitAll()

      val deleteQueue = BatchQueue(db)
      visitIdsToDelete.forEach { visitId ->
        deleteQueue.enqueue { batch ->
          batch.delete(officeRef.collection("visits").document(visitId))
        }
      }
      profileIdsToDelete.forEach { profileId ->
        deleteQueue.enqueue { batch ->
          batch.delete(officeRef.collection("visitorProfiles").document(profileId))
        }
      }
      deleteQueue.commitAll()

      Result.success(
        summary.copy(
          visitsRemoved = visitIdsToDelete.size,
          profilesRemoved = profileIdsToDelete.size,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun importDefaultArchPointData(
    office: Office,
    currentStaff: List<StaffMember>,
    currentReasons: List<VisitReason>,
    currentProfiles: List<VisitorProfile>,
    currentVisits: List<Visit>,
  ): Result<ImportSummary> {
    val path = "offices/${office.id}"
    return try {
      val uid = requireUserId()
      val email = requireVerifiedEmail()
      val queue = BatchQueue(db)
      val summary =
        enqueueDefaultArchPointCatalog(
          office = office,
          currentStaff = currentStaff,
          currentReasons = currentReasons,
          currentProfiles = currentProfiles,
          currentVisits = currentVisits,
          actorUid = uid,
          actorEmail = email,
          queue = queue,
        )
      queue.commitAll()
      Result.success(summary)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  private fun enqueueDefaultArchPointCatalog(
    office: Office,
    currentStaff: List<StaffMember>,
    currentReasons: List<VisitReason>,
    currentProfiles: List<VisitorProfile>,
    currentVisits: List<Visit>,
    actorUid: String,
    actorEmail: String,
    queue: BatchQueue,
  ): ImportSummary {
    val officeRef = db.collection("offices").document(office.id)
    val lists = computeOfficeEmailLists(office, currentStaff)
    val activeEmails = lists.activeEmails
    val receptionAdminEmails = lists.receptionAdminEmails
    if (office.receptionEmails != lists.receptionEmails) {
      queue.enqueue { batch ->
        batch.update(
          officeRef,
          mapOf(
            "receptionEmails" to lists.receptionEmails,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
    }

    val resolvedStaffById = currentStaff.associateBy { it.id }.toMutableMap()
    var staffAdded = 0

    ArchPointSeedData.teamDirectory.forEach { spec ->
      val matchedExisting =
        currentStaff.firstOrNull { existing ->
          existing.id == spec.id ||
            existing.fullName.equals(spec.fullName, ignoreCase = true) ||
            existing.shownAs.equals(spec.shownAs, ignoreCase = true) ||
            (spec.id == "staff_archpoint_09" &&
              existing.parsedRole() == StaffRole.ADMIN &&
              existing.fullName.contains("pratap", ignoreCase = true))
        }
      if (matchedExisting == null) {
        staffAdded++
        val seededMember =
          StaffMember(
            id = spec.id,
            fullName = spec.fullName,
            shownAs = spec.shownAs,
            email = "",
            role = spec.role.wireValue,
            designation = spec.designation,
            department = spec.department,
            floor = spec.floor,
            cabin = spec.cabin,
            otherLocation = spec.otherLocation,
            requiredThings = "",
            notes = spec.notes,
            listOrder = spec.listOrder,
            hostListed = spec.hostListed,
            activeAccount = true,
            uid = "",
            officeOwnerUid = office.ownerUid,
            allowedReaderEmails = activeEmails,
          )
        resolvedStaffById[spec.id] = seededMember
        queue.enqueue { batch ->
          batch.set(
            officeRef.collection("staff").document(spec.id),
            mapOf(
              "fullName" to spec.fullName,
              "shownAs" to spec.shownAs,
              "email" to "",
              "role" to spec.role.wireValue,
              "designation" to spec.designation,
              "department" to spec.department,
              "floor" to spec.floor,
              "cabin" to spec.cabin,
              "otherLocation" to spec.otherLocation,
              "requiredThings" to "",
              "notes" to spec.notes,
              "listOrder" to spec.listOrder,
              "hostListed" to spec.hostListed,
              "activeAccount" to true,
              "uid" to "",
              "officeOwnerUid" to office.ownerUid,
              "allowedReaderEmails" to activeEmails,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      } else {
        resolvedStaffById[spec.id] = matchedExisting
      }
    }

    var reasonsAdded = 0
    val existingReasonLabels = currentReasons.map { it.label.trim().lowercase() }.toSet()
    var nextReasonOrder = (currentReasons.maxOfOrNull { it.sortOrder } ?: 0L) + 1L
    ArchPointSeedData.defaultReasons.forEachIndexed { idx, label ->
      if (label.lowercase() !in existingReasonLabels) {
        reasonsAdded++
        val reasonId = "reason_archpoint_${idx + 1}"
        val order = (nextReasonOrder++).coerceIn(0L, 999L)
        queue.enqueue { batch ->
          batch.set(
            officeRef.collection("reasons").document(reasonId),
            mapOf(
              "label" to label,
              "shown" to true,
              "sortOrder" to order,
              "officeOwnerUid" to office.ownerUid,
              "allowedReaderEmails" to activeEmails,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
    }

    val existingVisitIds = currentVisits.map { it.id }.toSet()
    val existingProfileIds = currentProfiles.map { it.id }.toSet()
    val visitCountByKey = mutableMapOf<String, Long>()
    val profileSpecsByDocId = mutableMapOf<String, Pair<com.example.data.model.SeedPastVisitSpec, Long>>()

    var visitsAdded = 0
    ArchPointSeedData.pastVisits.forEach { past ->
      val profileKey =
        if (past.phone.isNotBlank() && past.phone != "919999999999") {
          past.phone
        } else {
          "prof_past_${past.rowNumber.toString().padStart(2, '0')}"
        }
      val visitNumber = (visitCountByKey[profileKey] ?: 0L) + 1L
      visitCountByKey[profileKey] = visitNumber
      profileSpecsByDocId[profileKey] = past to visitNumber

      if (past.docId !in existingVisitIds) {
        visitsAdded++
        val isOtherPerson = past.personToMeet.trim().equals("Other", ignoreCase = true)
        val targetSpecId =
          if (isOtherPerson) "other" else ArchPointSeedData.hostSpecIdForName(past.personToMeet)
        val hostMember = if (isOtherPerson) null else resolvedStaffById[targetSpecId]
        val hostShownAs =
          when {
            isOtherPerson -> "Other (2nd Floor)"
            hostMember != null -> hostMember.shownAs.ifBlank { hostMember.fullName }
            else -> past.personToMeet
          }
        val hostEmail = hostMember?.email?.trim()?.lowercase().orEmpty()
        val hostCabin =
          when {
            isOtherPerson -> "2nd Floor"
            hostMember != null -> hostMember.formattedSeating().ifBlank { hostMember.cabin }
            else -> ""
          }
        val visitReaders =
          (if (hostEmail.isNotBlank() && hostEmail in activeEmails) receptionAdminEmails + hostEmail else receptionAdminEmails)
            .distinct()
            .take(100)
        val ts = past.parseSubmittedTimestamp()
        val waitNote = if (past.remark.isNotBlank()) "Past entry" else "Completed past visit"

        queue.enqueue { batch ->
          batch.set(
            officeRef.collection("visits").document(past.docId),
            mapOf(
              "visitorName" to past.visitorName.take(100),
              "visitorMobile" to past.phone.take(20),
              "visitorCompany" to past.company.take(100),
              "visitorEmail" to past.email.take(120),
              "visitorType" to past.visitorType.take(60),
              "visitorJobProfile" to past.jobProfile.take(100),
              "remark" to past.remark.take(300),
              "reason" to past.resolvedReason().take(60),
              "hostStaffId" to (hostMember?.id ?: targetSpecId),
              "hostShownAs" to hostShownAs.take(60),
              "hostEmail" to hostEmail,
              "hostCabin" to hostCabin.take(100),
              "visitNumber" to visitNumber,
              "status" to VisitStatus.DONE.wireValue,
              "waitNote" to waitNote,
              "remindedCount" to 0L,
              "arrivedAt" to ts,
              "waitStartedAt" to ts,
              "endedAt" to ts,
              "createdByUid" to actorUid,
              "createdByEmail" to actorEmail,
              "officeOwnerUid" to office.ownerUid,
              "allowedReaderEmails" to visitReaders,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
    }

    var profilesAdded = 0
    profileSpecsByDocId.forEach { (profileDocId, pair) ->
      if (profileDocId !in existingProfileIds) {
        profilesAdded++
        val (latestSpec, totalCount) = pair
        val isOtherPerson = latestSpec.personToMeet.trim().equals("Other", ignoreCase = true)
        val targetSpecId =
          if (isOtherPerson) "other" else ArchPointSeedData.hostSpecIdForName(latestSpec.personToMeet)
        val hostId =
          if (isOtherPerson) "other" else (resolvedStaffById[targetSpecId]?.id ?: targetSpecId)
        val ts = latestSpec.parseSubmittedTimestamp()
        queue.enqueue { batch ->
          batch.set(
            officeRef.collection("visitorProfiles").document(profileDocId),
            mapOf(
              "mobile" to latestSpec.phone.take(20),
              "name" to latestSpec.visitorName.take(100),
              "company" to latestSpec.company.take(100),
              "email" to latestSpec.email.take(120),
              "dob" to latestSpec.dob.take(20),
              "visitorType" to latestSpec.visitorType.take(60),
              "visitorJobProfile" to latestSpec.jobProfile.take(100),
              "remark" to latestSpec.remark.take(300),
              "visitCount" to totalCount,
              "lastHostStaffId" to hostId,
              "lastReason" to latestSpec.resolvedReason().take(60),
              "officeOwnerUid" to office.ownerUid,
              "allowedReaderEmails" to receptionAdminEmails,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
    }

    return ImportSummary(
      staffAdded = staffAdded,
      reasonsAdded = reasonsAdded,
      visitsAdded = visitsAdded,
      profilesAdded = profilesAdded,
    )
  }

  suspend fun claimStaffUidIfNeeded(officeId: String, staff: StaffMember): Result<Unit> {
    val path = "offices/$officeId/staff/${staff.id}"
    return try {
      val uid = requireUserId()
      val email = requireVerifiedEmail()
      if (staff.id.isBlank() || staff.email != email || !staff.activeAccount || staff.uid == uid) {
        return Result.success(Unit)
      }
      db.collection("offices")
        .document(officeId)
        .collection("staff")
        .document(staff.id)
        .update(mapOf("uid" to uid, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateOwnProfile(
    officeId: String,
    staffId: String,
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
  ): Result<Unit> {
    val path = "offices/$officeId/staff/$staffId"
    return try {
      val uid = requireUserId()
      require(officeId.isNotBlank() && staffId.isNotBlank()) { "Staff profile ID is missing." }
      val cleanFullName = fullName.trim().take(80)
      require(cleanFullName.isNotEmpty()) { "Full name is required." }
      val cleanShownAs =
        shownAs.trim().take(60).ifBlank { cleanFullName.split(" ").firstOrNull() ?: "Staff" }
      val cleanPhotoUrl =
        photoUrl.trim().take(com.example.util.StaffPhotoCodec.MAX_PHOTO_DATA_URI_CHARS)
      db.collection("offices")
        .document(officeId)
        .collection("staff")
        .document(staffId)
        .update(
          mapOf(
            "uid" to uid,
            "fullName" to cleanFullName,
            "shownAs" to cleanShownAs,
            "designation" to designation.trim().take(80),
            "department" to department.trim().take(80),
            "photoUrl" to cleanPhotoUrl,
            "floor" to floor.trim().take(40),
            "cabin" to cabin.trim().take(60),
            "otherLocation" to otherLocation.trim().take(120),
            "requiredThings" to requiredThings.trim().take(200),
            "notes" to notes.trim().take(240),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateOwnSeatingAndRequirements(
    officeId: String,
    staffId: String,
    floor: String,
    cabin: String,
    otherLocation: String,
    requiredThings: String,
    notes: String,
  ): Result<Unit> {
    val path = "offices/$officeId/staff/$staffId"
    return try {
      val uid = requireUserId()
      require(officeId.isNotBlank() && staffId.isNotBlank()) { "Staff profile ID is missing." }
      db.collection("offices")
        .document(officeId)
        .collection("staff")
        .document(staffId)
        .update(
          mapOf(
            "uid" to uid,
            "floor" to floor.trim().take(40),
            "cabin" to cabin.trim().take(60),
            "otherLocation" to otherLocation.trim().take(120),
            "requiredThings" to requiredThings.trim().take(200),
            "notes" to notes.trim().take(240),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateOwnAvailabilityStatus(
    officeId: String,
    staffId: String,
    availability: HostAvailability,
  ): Result<Unit> {
    val path = "offices/$officeId/staff/$staffId"
    return try {
      val uid = requireUserId()
      require(officeId.isNotBlank() && staffId.isNotBlank()) { "Staff profile ID is missing." }
      db.collection("offices")
        .document(officeId)
        .collection("staff")
        .document(staffId)
        .update(
          mapOf(
            "uid" to uid,
            "availabilityStatus" to availability.wireValue,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun checkInVisitor(
    office: Office,
    mobile: String,
    name: String,
    company: String,
    email: String,
    dob: String,
    reason: String,
    remark: String,
    host: StaffMember,
    existingProfile: VisitorProfile?,
  ): Result<String> {
    val cleanMobile = mobile.filter { it.isDigit() }.take(20)
    val cleanName = name.trim().take(100)
    val cleanCompany = company.trim().take(100)
    val cleanVisitorEmail = email.trim().take(120)
    val cleanDob = dob.trim().take(20)
    val cleanReason = reason.trim().take(60)
    val cleanRemark = remark.trim().take(300)
    val visitId = "visit_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    var path = "offices/${office.id}/visits/$visitId"

    return try {
      val uid = requireUserId()
      val actorEmail = requireVerifiedEmail()
      require(cleanMobile.length >= 10) { "Enter at least 10 digits for mobile number." }
      require(cleanName.isNotEmpty()) { "Visitor name is required." }
      require(cleanReason.isNotEmpty()) { "Select a reason to meet." }
      require(host.id.isNotBlank()) { "Select a person to meet." }

      val nextVisitNumber = (existingProfile?.visitCount ?: 0L) + 1L
      val cleanHostEmail = host.email.trim().lowercase()
      val visitReaders =
        (if (cleanHostEmail.isNotBlank()) office.receptionAdminEmails + cleanHostEmail else office.receptionAdminEmails)
          .map { it.trim().lowercase() }
          .filter { it.isNotBlank() }
          .distinct()
          .take(100)

      val profileReaders = office.receptionAdminEmails
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(100)

      val hostLocation = host.formattedSeating().ifBlank { host.cabin }.take(100)

      val batch = db.batch()
      val visitRef = db.collection("offices").document(office.id).collection("visits").document(visitId)
      batch.set(
        visitRef,
        mapOf(
          "visitorName" to cleanName,
          "visitorMobile" to cleanMobile,
          "visitorCompany" to cleanCompany,
          "visitorEmail" to cleanVisitorEmail,
          "visitorType" to cleanReason,
          "visitorJobProfile" to (existingProfile?.visitorJobProfile ?: "").take(100),
          "remark" to cleanRemark,
          "reason" to cleanReason,
          "hostStaffId" to host.id,
          "hostShownAs" to host.shownAs.ifBlank { host.fullName }.take(60),
          "hostEmail" to cleanHostEmail,
          "hostCabin" to hostLocation,
          "visitNumber" to nextVisitNumber,
          "status" to VisitStatus.WAITING.wireValue,
          "waitNote" to "",
          "remindedCount" to 0L,
          "arrivedAt" to FieldValue.serverTimestamp(),
          "waitStartedAt" to FieldValue.serverTimestamp(),
          "createdByUid" to uid,
          "createdByEmail" to actorEmail,
          "officeOwnerUid" to office.ownerUid,
          "allowedReaderEmails" to visitReaders,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      val profileDocId = existingProfile?.id?.ifBlank { cleanMobile } ?: cleanMobile
      val profileRef =
        db.collection("offices").document(office.id).collection("visitorProfiles").document(profileDocId)
      if (existingProfile == null) {
        batch.set(
          profileRef,
          mapOf(
            "mobile" to cleanMobile,
            "name" to cleanName,
            "company" to cleanCompany,
            "email" to cleanVisitorEmail,
            "dob" to cleanDob,
            "visitorType" to cleanReason,
            "visitorJobProfile" to "",
            "remark" to cleanRemark,
            "visitCount" to nextVisitNumber,
            "lastHostStaffId" to host.id,
            "lastReason" to cleanReason,
            "officeOwnerUid" to office.ownerUid,
            "allowedReaderEmails" to profileReaders,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.update(
          profileRef,
          mapOf(
            "mobile" to cleanMobile,
            "name" to cleanName,
            "company" to cleanCompany,
            "email" to cleanVisitorEmail,
            "dob" to cleanDob.ifBlank { existingProfile.dob }.take(20),
            "remark" to cleanRemark.ifBlank { existingProfile.remark }.take(300),
            "visitCount" to nextVisitNumber,
            "lastHostStaffId" to host.id,
            "lastReason" to cleanReason,
            "allowedReaderEmails" to profileReaders,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      if (cleanDob.length >= 4) {
        val dobId = "dob_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        path = "offices/${office.id}/visitorDob/$dobId"
        batch.set(
          db.collection("offices").document(office.id).collection("visitorDob").document(dobId),
          mapOf(
            "visitorMobile" to cleanMobile,
            "dob" to cleanDob,
            "recordedByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().await()
      Result.success(visitId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun checkInFromAppointment(
    office: Office,
    appointment: Appointment,
    host: StaffMember,
    existingProfile: VisitorProfile?,
  ): Result<String> {
    val cleanMobile =
      appointment.visitorMobile.filter { it.isDigit() }.take(20).ifBlank { "919999999999" }
    val cleanName = appointment.visitorName.trim().take(100)
    val cleanCompany = appointment.visitorCompany.trim().take(100)
    val cleanVisitorEmail = appointment.visitorEmail.trim().lowercase().take(120)
    val cleanReason = appointment.purpose.trim().take(60).ifBlank { "Meeting" }
    val cleanRemark =
      listOfNotNull(
        "Booked ${appointment.scheduledTimeLabel}".takeIf { appointment.scheduledTimeLabel.isNotBlank() },
        appointment.location.takeIf { it.isNotBlank() },
        appointment.notes.takeIf { it.isNotBlank() },
      ).joinToString(" · ").take(300)

    val visitId = "visit_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val path = "offices/${office.id}/visits/$visitId"
    return try {
      val uid = requireUserId()
      val actorEmail = requireVerifiedEmail()
      require(appointment.id.isNotBlank()) { "Appointment ID missing." }
      require(cleanName.isNotEmpty()) { "Visitor name is required." }
      require(host.id.isNotBlank()) { "Assigned host not found." }

      val nextVisitNumber = (existingProfile?.visitCount ?: 0L) + 1L
      val cleanHostEmail = host.email.trim().lowercase()
      val visitReaders =
        (if (cleanHostEmail.isNotBlank()) office.receptionAdminEmails + cleanHostEmail else office.receptionAdminEmails)
          .map { it.trim().lowercase() }
          .filter { it.isNotBlank() }
          .distinct()
          .take(100)
      val profileReaders = office.receptionAdminEmails
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(100)
      val hostLocation = appointment.location.ifBlank {
        host.formattedSeating().ifBlank { host.cabin }
      }.take(100)

      val batch = db.batch()
      val visitRef = db.collection("offices").document(office.id).collection("visits").document(visitId)
      batch.set(
        visitRef,
        mapOf(
          "visitorName" to cleanName,
          "visitorMobile" to cleanMobile,
          "visitorCompany" to cleanCompany,
          "visitorEmail" to cleanVisitorEmail,
          "visitorType" to cleanReason,
          "visitorJobProfile" to (existingProfile?.visitorJobProfile ?: "").take(100),
          "remark" to cleanRemark,
          "reason" to cleanReason,
          "hostStaffId" to host.id,
          "hostShownAs" to host.shownAs.ifBlank { host.fullName }.take(60),
          "hostEmail" to cleanHostEmail,
          "hostCabin" to hostLocation,
          "visitNumber" to nextVisitNumber,
          "status" to VisitStatus.WAITING.wireValue,
          "waitNote" to "",
          "remindedCount" to 0L,
          "arrivedAt" to FieldValue.serverTimestamp(),
          "waitStartedAt" to FieldValue.serverTimestamp(),
          "createdByUid" to uid,
          "createdByEmail" to actorEmail,
          "officeOwnerUid" to office.ownerUid,
          "allowedReaderEmails" to visitReaders,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      if (cleanMobile != "919999999999") {
        val profileDocId = existingProfile?.id?.ifBlank { cleanMobile } ?: cleanMobile
        val profileRef =
          db.collection("offices").document(office.id).collection("visitorProfiles").document(profileDocId)
        if (existingProfile == null) {
          batch.set(
            profileRef,
            mapOf(
              "mobile" to cleanMobile,
              "name" to cleanName,
              "company" to cleanCompany,
              "email" to cleanVisitorEmail,
              "dob" to "",
              "visitorType" to cleanReason,
              "visitorJobProfile" to "",
              "remark" to cleanRemark,
              "visitCount" to nextVisitNumber,
              "lastHostStaffId" to host.id,
              "lastReason" to cleanReason,
              "officeOwnerUid" to office.ownerUid,
              "allowedReaderEmails" to profileReaders,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else {
          batch.update(
            profileRef,
            mapOf(
              "mobile" to cleanMobile,
              "name" to cleanName,
              "company" to cleanCompany.ifBlank { existingProfile.company }.take(100),
              "email" to cleanVisitorEmail.ifBlank { existingProfile.email }.take(120),
              "dob" to existingProfile.dob.take(20),
              "remark" to cleanRemark.ifBlank { existingProfile.remark }.take(300),
              "visitCount" to nextVisitNumber,
              "lastHostStaffId" to host.id,
              "lastReason" to cleanReason,
              "allowedReaderEmails" to profileReaders,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }

      val apptRef =
        db.collection("offices").document(office.id).collection("appointments").document(appointment.id)
      batch.update(
        apptRef,
        mapOf(
          "status" to AppointmentStatus.CHECKED_IN.wireValue,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      batch.commit().await()
      Result.success(visitId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun remindHost(officeId: String, visitId: String): Result<Unit> {
    val path = "offices/$officeId/visits/$visitId"
    return try {
      requireUserId()
      require(officeId.isNotBlank() && visitId.isNotBlank())
      db.collection("offices")
        .document(officeId)
        .collection("visits")
        .document(visitId)
        .update(
          mapOf(
            "remindedCount" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun sendVisitorIn(officeId: String, visitId: String, note: String = "Send in"): Result<Unit> {
    val path = "offices/$officeId/visits/$visitId"
    return try {
      requireUserId()
      require(officeId.isNotBlank() && visitId.isNotBlank())
      db.collection("offices")
        .document(officeId)
        .collection("visits")
        .document(visitId)
        .update(
          mapOf(
            "status" to VisitStatus.IN_MEETING.wireValue,
            "waitNote" to note.take(120),
            "meetingStartedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun askVisitorToWait(
    officeId: String,
    visitId: String,
    note: String = "asked to wait 10 min",
  ): Result<Unit> {
    val path = "offices/$officeId/visits/$visitId"
    val cleanNote = note.trim().take(120).ifBlank { "asked to wait 10 min" }
    return try {
      requireUserId()
      require(officeId.isNotBlank() && visitId.isNotBlank())
      db.collection("offices")
        .document(officeId)
        .collection("visits")
        .document(visitId)
        .update(
          mapOf(
            "waitNote" to cleanNote,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun denyVisitorToday(
    officeId: String,
    visitId: String,
    reasonNote: String = "can't meet today",
  ): Result<Unit> {
    val path = "offices/$officeId/visits/$visitId"
    val cleanNote = reasonNote.trim().take(120).ifBlank { "can't meet today" }
    return try {
      requireUserId()
      require(officeId.isNotBlank() && visitId.isNotBlank())
      db.collection("offices")
        .document(officeId)
        .collection("visits")
        .document(visitId)
        .update(
          mapOf(
            "status" to VisitStatus.DENIED.wireValue,
            "waitNote" to cleanNote,
            "endedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun markMeetingDone(officeId: String, visitId: String): Result<Unit> {
    val path = "offices/$officeId/visits/$visitId"
    return try {
      requireUserId()
      require(officeId.isNotBlank() && visitId.isNotBlank())
      db.collection("offices")
        .document(officeId)
        .collection("visits")
        .document(visitId)
        .update(
          mapOf(
            "status" to VisitStatus.DONE.wireValue,
            "endedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun addStaffMember(
    office: Office,
    currentStaff: List<StaffMember>,
    currentReasons: List<VisitReason>,
    currentProfiles: List<VisitorProfile>,
    currentVisits: List<Visit>,
    currentPrCampaigns: List<PRCampaign> = emptyList(),
    currentPrContacts: List<PRContact> = emptyList(),
    currentPrGifts: List<PRGift> = emptyList(),
    currentAppointments: List<Appointment> = emptyList(),
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
  ): Result<Unit> {
    val cleanName = fullName.trim().take(80)
    val cleanShown = shownAs.trim().take(60).ifBlank { cleanName.split(" ").firstOrNull() ?: "Staff" }
    val cleanEmail = email.trim().lowercase().take(120)
    val cleanPhotoUrl = photoUrl.trim().take(com.example.util.StaffPhotoCodec.MAX_PHOTO_DATA_URI_CHARS)
    val staffId = "staff_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val path = "offices/${office.id}/staff/$staffId"

    return try {
      requireUserId()
      require(cleanName.isNotEmpty()) { "Full name is required." }
      if (cleanEmail.isNotEmpty()) {
        require(cleanEmail.contains("@") && cleanEmail.length >= 5) { "Enter a valid Google email or leave blank." }
        require(currentStaff.none { it.email.equals(cleanEmail, ignoreCase = true) }) {
          "A staff member with this email already exists."
        }
      }

      val nextOrder = (currentStaff.maxOfOrNull { it.listOrder } ?: 0L) + 1L
      val newMember =
        StaffMember(
          id = staffId,
          fullName = cleanName,
          shownAs = cleanShown,
          email = cleanEmail,
          role = role.wireValue,
          designation = designation.trim().take(80),
          department = department.trim().take(80),
          floor = floor.trim().take(40),
          cabin = cabin.trim().take(60),
          otherLocation = otherLocation.trim().take(120),
          requiredThings = requiredThings.trim().take(200),
          notes = notes.trim().take(240),
          listOrder = nextOrder,
          hostListed = hostListed,
          activeAccount = true,
          uid = "",
          photoUrl = cleanPhotoUrl,
          officeOwnerUid = office.ownerUid,
        )
      val updatedList = currentStaff + newMember
      val lists = computeOfficeEmailLists(office, updatedList)
      val activeEmails = lists.activeEmails
      val receptionAdminEmails = lists.receptionAdminEmails
      val adminEmails = lists.adminEmails

      val queue = BatchQueue(db)
      queue.enqueue { batch ->
        batch.set(
          db.collection("offices").document(office.id).collection("staff").document(staffId),
          mapOf(
            "fullName" to newMember.fullName,
            "shownAs" to newMember.shownAs,
            "email" to cleanEmail,
            "role" to role.wireValue,
            "designation" to newMember.designation,
            "department" to newMember.department,
            "floor" to newMember.floor,
            "cabin" to newMember.cabin,
            "otherLocation" to newMember.otherLocation,
            "requiredThings" to newMember.requiredThings,
            "notes" to newMember.notes,
            "listOrder" to nextOrder,
            "hostListed" to hostListed,
            "activeAccount" to true,
            "uid" to "",
            "photoUrl" to cleanPhotoUrl,
            "officeOwnerUid" to office.ownerUid,
            "allowedReaderEmails" to activeEmails,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        if (cleanEmail.isNotEmpty()) {
          batch.set(
            db.collection("officeMemberships").document("${office.id}__${staffId}"),
            mapOf(
              "officeId" to office.id,
              "officeName" to office.name,
              "staffId" to staffId,
              "email" to cleanEmail,
              "role" to role.wireValue,
              "activeAccount" to true,
              "officeOwnerUid" to office.ownerUid,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }

      syncOfficeAccessControl(
        office = office,
        staffList = currentStaff,
        reasons = currentReasons,
        profiles = currentProfiles,
        visits = currentVisits,
        prCampaigns = currentPrCampaigns,
        prContacts = currentPrContacts,
        prGifts = currentPrGifts,
        appointments = currentAppointments,
        activeEmails = activeEmails,
        receptionAdminEmails = receptionAdminEmails,
        receptionEmails = lists.receptionEmails,
        adminEmails = adminEmails,
        queue = queue,
      )

      queue.commitAll()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun updateStaffMember(
    office: Office,
    currentStaff: List<StaffMember>,
    currentReasons: List<VisitReason>,
    currentProfiles: List<VisitorProfile>,
    currentVisits: List<Visit>,
    edited: StaffMember,
    currentPrCampaigns: List<PRCampaign> = emptyList(),
    currentPrContacts: List<PRContact> = emptyList(),
    currentPrGifts: List<PRGift> = emptyList(),
    currentAppointments: List<Appointment> = emptyList(),
  ): Result<Unit> {
    val path = "offices/${office.id}/staff/${edited.id}"
    return try {
      requireUserId()
      val prevMember = currentStaff.firstOrNull { it.id == edited.id }
      val cleanName = edited.fullName.trim().take(80)
      val cleanShown = edited.shownAs.trim().take(60)
      val cleanEmail = edited.email.trim().lowercase().take(120)
      require(edited.id.isNotBlank()) { "Staff ID cannot be blank." }
      require(cleanName.isNotEmpty() && cleanShown.isNotEmpty()) { "Name fields are required." }
      if (cleanEmail.isNotEmpty()) {
        require(cleanEmail.contains("@") && cleanEmail.length >= 5) { "Valid email is required, or leave blank." }
      }

      val updatedList = currentStaff.map { if (it.id == edited.id) edited.copy(email = cleanEmail) else it }
      val lists = computeOfficeEmailLists(office, updatedList)
      val activeEmails = lists.activeEmails
      val receptionAdminEmails = lists.receptionAdminEmails
      val adminEmails = lists.adminEmails
      require(adminEmails.isNotEmpty()) { "At least one active admin account must remain enabled." }

      val queue = BatchQueue(db)
      queue.enqueue { batch ->
        batch.update(
          db.collection("offices").document(office.id).collection("staff").document(edited.id),
          mapOf(
            "fullName" to cleanName,
            "shownAs" to cleanShown,
            "email" to cleanEmail,
            "role" to edited.role,
            "designation" to edited.designation.trim().take(80),
            "department" to edited.department.trim().take(80),
            "floor" to edited.floor.trim().take(40),
            "cabin" to edited.cabin.trim().take(60),
            "otherLocation" to edited.otherLocation.trim().take(120),
            "requiredThings" to edited.requiredThings.trim().take(200),
            "notes" to edited.notes.trim().take(240),
            "listOrder" to edited.listOrder.coerceIn(0L, 9999L),
            "hostListed" to edited.hostListed,
            "activeAccount" to edited.activeAccount,
            "photoUrl" to edited.photoUrl.trim().take(com.example.util.StaffPhotoCodec.MAX_PHOTO_DATA_URI_CHARS),
            "allowedReaderEmails" to activeEmails,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        val prevEmail = prevMember?.email?.trim()?.lowercase().orEmpty()
        val membershipRef = db.collection("officeMemberships").document("${office.id}__${edited.id}")
        if (cleanEmail.isNotEmpty() && prevEmail.isEmpty()) {
          batch.set(
            membershipRef,
            mapOf(
              "officeId" to office.id,
              "officeName" to office.name,
              "staffId" to edited.id,
              "email" to cleanEmail,
              "role" to edited.role,
              "activeAccount" to edited.activeAccount,
              "officeOwnerUid" to office.ownerUid,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else if (cleanEmail.isNotEmpty()) {
          batch.update(
            membershipRef,
            mapOf(
              "officeName" to office.name,
              "email" to cleanEmail,
              "role" to edited.role,
              "activeAccount" to edited.activeAccount,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        } else if (prevEmail.isNotEmpty()) {
          batch.delete(membershipRef)
        }
      }

      syncOfficeAccessControl(
        office = office,
        staffList = currentStaff.filter { it.id != edited.id },
        reasons = currentReasons,
        profiles = currentProfiles,
        visits = currentVisits,
        prCampaigns = currentPrCampaigns,
        prContacts = currentPrContacts,
        prGifts = currentPrGifts,
        appointments = currentAppointments,
        activeEmails = activeEmails,
        receptionAdminEmails = receptionAdminEmails,
        receptionEmails = lists.receptionEmails,
        adminEmails = adminEmails,
        queue = queue,
      )

      queue.commitAll()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun addReason(office: Office, currentReasons: List<VisitReason>, label: String): Result<Unit> {
    val cleanLabel = label.trim().take(40)
    val reasonId = "reason_${UUID.randomUUID().toString().replace("-", "").take(10)}"
    val path = "offices/${office.id}/reasons/$reasonId"
    return try {
      requireUserId()
      require(cleanLabel.isNotEmpty()) { "Reason label cannot be empty." }
      require(currentReasons.none { it.label.equals(cleanLabel, ignoreCase = true) }) {
        "This reason already exists."
      }
      val nextOrder = (currentReasons.maxOfOrNull { it.sortOrder } ?: 0L) + 1L
      db.collection("offices")
        .document(office.id)
        .collection("reasons")
        .document(reasonId)
        .set(
          mapOf(
            "label" to cleanLabel,
            "shown" to true,
            "sortOrder" to nextOrder.coerceIn(0L, 999L),
            "officeOwnerUid" to office.ownerUid,
            "allowedReaderEmails" to office.activeMemberEmails.take(100),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun toggleReasonShown(officeId: String, reasonId: String, shown: Boolean): Result<Unit> {
    val path = "offices/$officeId/reasons/$reasonId"
    return try {
      requireUserId()
      db.collection("offices")
        .document(officeId)
        .collection("reasons")
        .document(reasonId)
        .update(mapOf("shown" to shown, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  private data class OfficeEmailLists(
    val activeEmails: List<String>,
    val receptionAdminEmails: List<String>,
    val receptionEmails: List<String>,
    val adminEmails: List<String>,
  )

  private fun computeOfficeEmailLists(
    office: Office,
    staffList: List<StaffMember>,
  ): OfficeEmailLists {
    val activeEmails =
      (staffList.filter { it.activeAccount && it.email.isNotBlank() }.map { it.email.trim().lowercase() } + office.ownerEmail.lowercase())
        .filter { it.isNotBlank() }
        .distinct()
        .take(100)

    val receptionAdminEmails =
      (staffList
        .filter {
          it.activeAccount &&
            it.email.isNotBlank() &&
            (it.parsedRole() == StaffRole.ADMIN || it.parsedRole() == StaffRole.RECEPTION)
        }
        .map { it.email.trim().lowercase() } + office.ownerEmail.lowercase())
        .filter { it.isNotBlank() }
        .distinct()
        .take(100)

    val receptionEmails =
      staffList
        .filter { it.activeAccount && it.email.isNotBlank() && it.parsedRole() == StaffRole.RECEPTION }
        .map { it.email.trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(100)

    val adminEmails =
      (staffList
        .filter { it.activeAccount && it.email.isNotBlank() && it.parsedRole() == StaffRole.ADMIN }
        .map { it.email.trim().lowercase() } + office.ownerEmail.lowercase())
        .filter { it.isNotBlank() }
        .distinct()
        .take(100)

    return OfficeEmailLists(
      activeEmails = activeEmails,
      receptionAdminEmails = receptionAdminEmails,
      receptionEmails = receptionEmails,
      adminEmails = adminEmails,
    )
  }

  // --- PR MODULE CRUD OPERATIONS ---

  private fun buildPrReaders(office: Office, ownerEmail: String): List<String> {
    val cleanOwner = ownerEmail.trim().lowercase()
    return (office.receptionAdminEmails + cleanOwner)
      .map { it.trim().lowercase() }
      .filter { it.isNotBlank() }
      .distinct()
      .take(100)
  }

  suspend fun upsertPrCampaign(
    office: Office,
    existing: PRCampaign?,
    campaign: PRCampaign,
    isHostSelfEdit: Boolean,
  ): Result<Unit> {
    val campaignId =
      existing?.id?.takeIf { it.isNotBlank() }
        ?: "pr_camp_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val path = "offices/${office.id}/prCampaigns/$campaignId"
    return try {
      val uid = requireUserId()
      val email = requireVerifiedEmail()
      val cleanTitle = campaign.title.trim().take(120)
      require(cleanTitle.isNotEmpty()) { "Campaign title is required." }

      val docRef = db.collection("offices").document(office.id).collection("prCampaigns").document(campaignId)
      if (existing == null) {
        val cleanOwnerEmail =
          if (isHostSelfEdit) email else campaign.ownerEmail.trim().lowercase().take(120)
        val payload = mapOf(
          "title" to cleanTitle,
          "channel" to campaign.parsedChannel().wireValue,
          "status" to campaign.parsedStatus().wireValue,
          "targetDateLabel" to campaign.targetDateLabel.trim().take(40),
          "location" to campaign.location.trim().take(120),
          "audienceSummary" to campaign.audienceSummary.trim().take(160),
          "notes" to campaign.notes.trim().take(300),
          "ownerStaffId" to campaign.ownerStaffId.trim().take(64),
          "ownerName" to campaign.ownerName.trim().take(80),
          "ownerEmail" to cleanOwnerEmail,
          "createdByUid" to uid,
          "createdByEmail" to email,
          "officeOwnerUid" to office.ownerUid,
          "allowedReaderEmails" to buildPrReaders(office, cleanOwnerEmail),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val updateMap = mutableMapOf<String, Any>(
          "title" to cleanTitle,
          "channel" to campaign.parsedChannel().wireValue,
          "status" to campaign.parsedStatus().wireValue,
          "targetDateLabel" to campaign.targetDateLabel.trim().take(40),
          "location" to campaign.location.trim().take(120),
          "audienceSummary" to campaign.audienceSummary.trim().take(160),
          "notes" to campaign.notes.trim().take(300),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!isHostSelfEdit) {
          val cleanOwnerEmail = campaign.ownerEmail.trim().lowercase().take(120)
          updateMap["ownerStaffId"] = campaign.ownerStaffId.trim().take(64)
          updateMap["ownerName"] = campaign.ownerName.trim().take(80)
          updateMap["ownerEmail"] = cleanOwnerEmail
          updateMap["allowedReaderEmails"] = buildPrReaders(office, cleanOwnerEmail)
        }
        docRef.update(updateMap).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (existing == null) OperationType.CREATE else OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updatePrCampaignStatus(
    officeId: String,
    campaignId: String,
    status: PRCampaignStatus,
  ): Result<Unit> {
    val path = "offices/$officeId/prCampaigns/$campaignId"
    return try {
      requireUserId()
      require(campaignId.isNotBlank()) { "Campaign ID is required." }
      db.collection("offices")
        .document(officeId)
        .collection("prCampaigns")
        .document(campaignId)
        .update(
          mapOf(
            "status" to status.wireValue,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deletePrCampaign(officeId: String, campaignId: String): Result<Unit> {
    val path = "offices/$officeId/prCampaigns/$campaignId"
    return try {
      requireUserId()
      require(campaignId.isNotBlank()) { "Campaign ID is required." }
      db.collection("offices")
        .document(officeId)
        .collection("prCampaigns")
        .document(campaignId)
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun upsertPrContact(
    office: Office,
    existing: PRContact?,
    contact: PRContact,
    isHostSelfEdit: Boolean,
  ): Result<Unit> {
    val contactId =
      existing?.id?.takeIf { it.isNotBlank() }
        ?: "pr_contact_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val path = "offices/${office.id}/prContacts/$contactId"
    return try {
      val uid = requireUserId()
      val email = requireVerifiedEmail()
      val cleanName = contact.name.trim().take(100)
      require(cleanName.isNotEmpty()) { "Contact name is required." }

      val docRef = db.collection("offices").document(office.id).collection("prContacts").document(contactId)
      val cleanPhotoUrl = contact.photoUrl.trim().take(StaffPhotoCodec.MAX_PHOTO_DATA_URI_CHARS)
      val cleanDob = contact.dob.trim().take(30)
      val cleanAnniv = contact.anniversaryDate.trim().take(30)
      val cleanGiftPref = contact.specialGiftPreference.trim().take(160)
      val cleanCity = contact.cityOrAddress.trim().take(160)
      val cleanWishedYear = contact.lastWishedYear.coerceIn(0L, 2999L)
      if (existing == null) {
        val cleanOwnerEmail =
          if (isHostSelfEdit) email else contact.ownerEmail.trim().lowercase().take(120)
        val payload = mapOf(
          "name" to cleanName,
          "organization" to contact.organization.trim().take(100),
          "designation" to contact.designation.trim().take(80),
          "tier" to contact.parsedTier().wireValue,
          "mobile" to contact.mobile.trim().take(20),
          "email" to contact.email.trim().lowercase().take(120),
          "followUpStatus" to contact.parsedFollowUpStatus().wireValue,
          "nextFollowUpDate" to contact.nextFollowUpDate.trim().take(40),
          "photoUrl" to cleanPhotoUrl,
          "dob" to cleanDob,
          "anniversaryDate" to cleanAnniv,
          "lastWishedYear" to cleanWishedYear,
          "specialGiftPreference" to cleanGiftPref,
          "cityOrAddress" to cleanCity,
          "notes" to contact.notes.trim().take(300),
          "ownerStaffId" to contact.ownerStaffId.trim().take(64),
          "ownerName" to contact.ownerName.trim().take(80),
          "ownerEmail" to cleanOwnerEmail,
          "createdByUid" to uid,
          "createdByEmail" to email,
          "officeOwnerUid" to office.ownerUid,
          "allowedReaderEmails" to buildPrReaders(office, cleanOwnerEmail),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val updateMap = mutableMapOf<String, Any>(
          "name" to cleanName,
          "organization" to contact.organization.trim().take(100),
          "designation" to contact.designation.trim().take(80),
          "tier" to contact.parsedTier().wireValue,
          "mobile" to contact.mobile.trim().take(20),
          "email" to contact.email.trim().lowercase().take(120),
          "followUpStatus" to contact.parsedFollowUpStatus().wireValue,
          "nextFollowUpDate" to contact.nextFollowUpDate.trim().take(40),
          "photoUrl" to cleanPhotoUrl,
          "dob" to cleanDob,
          "anniversaryDate" to cleanAnniv,
          "lastWishedYear" to cleanWishedYear,
          "specialGiftPreference" to cleanGiftPref,
          "cityOrAddress" to cleanCity,
          "notes" to contact.notes.trim().take(300),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!isHostSelfEdit) {
          val cleanOwnerEmail = contact.ownerEmail.trim().lowercase().take(120)
          updateMap["ownerStaffId"] = contact.ownerStaffId.trim().take(64)
          updateMap["ownerName"] = contact.ownerName.trim().take(80)
          updateMap["ownerEmail"] = cleanOwnerEmail
          updateMap["allowedReaderEmails"] = buildPrReaders(office, cleanOwnerEmail)
        }
        docRef.update(updateMap).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (existing == null) OperationType.CREATE else OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun markPrContactWished(
    officeId: String,
    contactId: String,
    year: Int,
  ): Result<Unit> {
    val path = "offices/$officeId/prContacts/$contactId"
    return try {
      requireUserId()
      require(contactId.isNotBlank()) { "Contact ID is required." }
      db.collection("offices")
        .document(officeId)
        .collection("prContacts")
        .document(contactId)
        .update(
          mapOf(
            "lastWishedYear" to year.toLong().coerceIn(0L, 2999L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updatePrContactFollowUp(
    officeId: String,
    contactId: String,
    status: PRFollowUpStatus,
    nextFollowUpDate: String? = null,
  ): Result<Unit> {
    val path = "offices/$officeId/prContacts/$contactId"
    return try {
      requireUserId()
      require(contactId.isNotBlank()) { "Contact ID is required." }
      val updates = mutableMapOf<String, Any>(
        "followUpStatus" to status.wireValue,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (nextFollowUpDate != null) {
        updates["nextFollowUpDate"] = nextFollowUpDate.trim().take(40)
      }
      db.collection("offices")
        .document(officeId)
        .collection("prContacts")
        .document(contactId)
        .update(updates)
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deletePrContact(officeId: String, contactId: String): Result<Unit> {
    val path = "offices/$officeId/prContacts/$contactId"
    return try {
      requireUserId()
      require(contactId.isNotBlank()) { "Contact ID is required." }
      db.collection("offices")
        .document(officeId)
        .collection("prContacts")
        .document(contactId)
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun upsertPrGift(
    office: Office,
    existing: PRGift?,
    gift: PRGift,
    isHostSelfEdit: Boolean,
  ): Result<Unit> {
    val giftId =
      existing?.id?.takeIf { it.isNotBlank() }
        ?: "pr_gift_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val path = "offices/${office.id}/prGifts/$giftId"
    return try {
      val uid = requireUserId()
      val email = requireVerifiedEmail()
      val cleanRecipient = gift.recipientOrSender.trim().take(100)
      val cleanItem = gift.itemTitle.trim().take(120)
      require(cleanRecipient.isNotEmpty() && cleanItem.isNotEmpty()) {
        "Recipient/sender and item title are required."
      }

      val docRef = db.collection("offices").document(office.id).collection("prGifts").document(giftId)
      if (existing == null) {
        val cleanOwnerEmail =
          if (isHostSelfEdit) email else gift.ownerEmail.trim().lowercase().take(120)
        val payload = mapOf(
          "recipientOrSender" to cleanRecipient,
          "company" to gift.company.trim().take(100),
          "itemTitle" to cleanItem,
          "direction" to gift.parsedDirection().wireValue,
          "status" to gift.parsedStatus().wireValue,
          "occasion" to gift.occasion.trim().take(60),
          "dateLabel" to gift.dateLabel.trim().take(40),
          "notes" to gift.notes.trim().take(300),
          "ownerStaffId" to gift.ownerStaffId.trim().take(64),
          "ownerName" to gift.ownerName.trim().take(80),
          "ownerEmail" to cleanOwnerEmail,
          "createdByUid" to uid,
          "createdByEmail" to email,
          "officeOwnerUid" to office.ownerUid,
          "allowedReaderEmails" to buildPrReaders(office, cleanOwnerEmail),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val updateMap = mutableMapOf<String, Any>(
          "recipientOrSender" to cleanRecipient,
          "company" to gift.company.trim().take(100),
          "itemTitle" to cleanItem,
          "direction" to gift.parsedDirection().wireValue,
          "status" to gift.parsedStatus().wireValue,
          "occasion" to gift.occasion.trim().take(60),
          "dateLabel" to gift.dateLabel.trim().take(40),
          "notes" to gift.notes.trim().take(300),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!isHostSelfEdit) {
          val cleanOwnerEmail = gift.ownerEmail.trim().lowercase().take(120)
          updateMap["ownerStaffId"] = gift.ownerStaffId.trim().take(64)
          updateMap["ownerName"] = gift.ownerName.trim().take(80)
          updateMap["ownerEmail"] = cleanOwnerEmail
          updateMap["allowedReaderEmails"] = buildPrReaders(office, cleanOwnerEmail)
        }
        docRef.update(updateMap).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (existing == null) OperationType.CREATE else OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updatePrGiftStatus(
    officeId: String,
    giftId: String,
    status: PRGiftStatus,
  ): Result<Unit> {
    val path = "offices/$officeId/prGifts/$giftId"
    return try {
      requireUserId()
      require(giftId.isNotBlank()) { "Gift ID is required." }
      db.collection("offices")
        .document(officeId)
        .collection("prGifts")
        .document(giftId)
        .update(
          mapOf(
            "status" to status.wireValue,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deletePrGift(officeId: String, giftId: String): Result<Unit> {
    val path = "offices/$officeId/prGifts/$giftId"
    return try {
      requireUserId()
      require(giftId.isNotBlank()) { "Gift ID is required." }
      db.collection("offices")
        .document(officeId)
        .collection("prGifts")
        .document(giftId)
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  // --- APPOINTMENT BOOKING CRUD ---

  suspend fun upsertAppointment(
    office: Office,
    existing: Appointment?,
    appointment: Appointment,
    isHostSelfEdit: Boolean,
  ): Result<Appointment> {
    val apptId =
      existing?.id?.takeIf { it.isNotBlank() }
        ?: "appt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val path = "offices/${office.id}/appointments/$apptId"
    return try {
      val uid = requireUserId()
      val email = requireVerifiedEmail()
      val cleanVisitorName = appointment.visitorName.trim().take(100)
      val cleanPurpose = appointment.purpose.trim().take(60).ifBlank { "Meeting" }
      val cleanDateIso = appointment.scheduledDateIso.trim().take(20)
      val cleanTimeLabel = appointment.scheduledTimeLabel.trim().take(24)
      require(cleanVisitorName.isNotEmpty()) { "Visitor name is required." }
      require(cleanDateIso.isNotEmpty() && cleanTimeLabel.isNotEmpty()) { "Date and time are required." }
      require(appointment.hostStaffId.isNotBlank() && appointment.hostShownAs.isNotBlank()) {
        "Assigned host is required."
      }

      val scheduledMs =
        appointment.scheduledAtMillis.takeIf { it > 0L }
          ?: DeskFormatters.parseScheduledMillis(cleanDateIso, cleanTimeLabel)

      val cleanCalendarEventId =
        appointment.calendarEventId.ifBlank { existing?.calendarEventId.orEmpty() }.trim().take(128)
      val cleanCalendarHtmlLink =
        appointment.calendarHtmlLink.ifBlank { existing?.calendarHtmlLink.orEmpty() }.trim().take(400)
      val cleanCalendarSyncedEmail =
        appointment.calendarSyncedEmail.ifBlank { existing?.calendarSyncedEmail.orEmpty() }.trim().lowercase().take(120)

      val docRef = db.collection("offices").document(office.id).collection("appointments").document(apptId)
      val resolvedHostEmail: String
      val resolvedStatus: String
      if (existing == null) {
        resolvedHostEmail =
          if (isHostSelfEdit) email else appointment.hostEmail.trim().lowercase().take(120)
        resolvedStatus = AppointmentStatus.SCHEDULED.wireValue
        val payload = mapOf(
          "visitorName" to cleanVisitorName,
          "visitorMobile" to appointment.visitorMobile.trim().take(20),
          "visitorCompany" to appointment.visitorCompany.trim().take(100),
          "visitorEmail" to appointment.visitorEmail.trim().lowercase().take(120),
          "purpose" to cleanPurpose,
          "location" to appointment.location.trim().take(120),
          "scheduledDateIso" to cleanDateIso,
          "scheduledTimeLabel" to cleanTimeLabel,
          "scheduledAtMillis" to scheduledMs.coerceAtLeast(0L),
          "notes" to appointment.notes.trim().take(300),
          "hostStaffId" to appointment.hostStaffId.trim().take(64),
          "hostShownAs" to appointment.hostShownAs.trim().take(60),
          "hostEmail" to resolvedHostEmail,
          "hostCabin" to appointment.hostCabin.trim().take(100),
          "status" to resolvedStatus,
          "bookedByRole" to appointment.bookedByRole.trim().take(30),
          "calendarEventId" to cleanCalendarEventId,
          "calendarHtmlLink" to cleanCalendarHtmlLink,
          "calendarSyncedEmail" to cleanCalendarSyncedEmail,
          "createdByUid" to uid,
          "createdByEmail" to email,
          "officeOwnerUid" to office.ownerUid,
          "allowedReaderEmails" to buildPrReaders(office, resolvedHostEmail),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        resolvedStatus = appointment.parsedStatus().wireValue
        val updateMap = mutableMapOf<String, Any>(
          "visitorName" to cleanVisitorName,
          "visitorMobile" to appointment.visitorMobile.trim().take(20),
          "visitorCompany" to appointment.visitorCompany.trim().take(100),
          "visitorEmail" to appointment.visitorEmail.trim().lowercase().take(120),
          "purpose" to cleanPurpose,
          "location" to appointment.location.trim().take(120),
          "scheduledDateIso" to cleanDateIso,
          "scheduledTimeLabel" to cleanTimeLabel,
          "scheduledAtMillis" to scheduledMs.coerceAtLeast(0L),
          "notes" to appointment.notes.trim().take(300),
          "status" to resolvedStatus,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!isHostSelfEdit) {
          resolvedHostEmail = appointment.hostEmail.trim().lowercase().take(120)
          updateMap["hostStaffId"] = appointment.hostStaffId.trim().take(64)
          updateMap["hostShownAs"] = appointment.hostShownAs.trim().take(60)
          updateMap["hostEmail"] = resolvedHostEmail
          updateMap["hostCabin"] = appointment.hostCabin.trim().take(100)
          updateMap["allowedReaderEmails"] = buildPrReaders(office, resolvedHostEmail)
        } else {
          resolvedHostEmail = existing.hostEmail.trim().lowercase().take(120)
        }
        docRef.update(updateMap).await()
      }

      val persisted =
        appointment.copy(
          id = apptId,
          visitorName = cleanVisitorName,
          visitorMobile = appointment.visitorMobile.trim().take(20),
          visitorCompany = appointment.visitorCompany.trim().take(100),
          visitorEmail = appointment.visitorEmail.trim().lowercase().take(120),
          purpose = cleanPurpose,
          location = appointment.location.trim().take(120),
          scheduledDateIso = cleanDateIso,
          scheduledTimeLabel = cleanTimeLabel,
          scheduledAtMillis = scheduledMs.coerceAtLeast(0L),
          notes = appointment.notes.trim().take(300),
          hostStaffId =
            if (isHostSelfEdit && existing != null) existing.hostStaffId
            else appointment.hostStaffId.trim().take(64),
          hostShownAs =
            if (isHostSelfEdit && existing != null) existing.hostShownAs
            else appointment.hostShownAs.trim().take(60),
          hostEmail = resolvedHostEmail,
          hostCabin =
            if (isHostSelfEdit && existing != null) existing.hostCabin
            else appointment.hostCabin.trim().take(100),
          status = resolvedStatus,
          calendarEventId = cleanCalendarEventId,
          calendarHtmlLink = cleanCalendarHtmlLink,
          calendarSyncedEmail = cleanCalendarSyncedEmail,
        )
      Result.success(persisted)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (existing == null) OperationType.CREATE else OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateAppointmentCalendarMetadata(
    officeId: String,
    appointmentId: String,
    calendarEventId: String,
    calendarHtmlLink: String,
    calendarSyncedEmail: String,
  ): Result<Unit> {
    val path = "offices/$officeId/appointments/$appointmentId"
    return try {
      requireUserId()
      require(appointmentId.isNotBlank()) { "Appointment ID is required." }
      db.collection("offices")
        .document(officeId)
        .collection("appointments")
        .document(appointmentId)
        .update(
          mapOf(
            "calendarEventId" to calendarEventId.trim().take(128),
            "calendarHtmlLink" to calendarHtmlLink.trim().take(400),
            "calendarSyncedEmail" to calendarSyncedEmail.trim().lowercase().take(120),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateAppointmentStatus(
    officeId: String,
    appointmentId: String,
    status: AppointmentStatus,
  ): Result<Unit> {
    val path = "offices/$officeId/appointments/$appointmentId"
    return try {
      requireUserId()
      require(appointmentId.isNotBlank()) { "Appointment ID is required." }
      db.collection("offices")
        .document(officeId)
        .collection("appointments")
        .document(appointmentId)
        .update(
          mapOf(
            "status" to status.wireValue,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteAppointment(officeId: String, appointmentId: String): Result<Unit> {
    val path = "offices/$officeId/appointments/$appointmentId"
    return try {
      requireUserId()
      require(appointmentId.isNotBlank()) { "Appointment ID is required." }
      db.collection("offices")
        .document(officeId)
        .collection("appointments")
        .document(appointmentId)
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  private fun syncOfficeAccessControl(
    office: Office,
    staffList: List<StaffMember>,
    reasons: List<VisitReason>,
    profiles: List<VisitorProfile>,
    visits: List<Visit>,
    prCampaigns: List<PRCampaign> = emptyList(),
    prContacts: List<PRContact> = emptyList(),
    prGifts: List<PRGift> = emptyList(),
    appointments: List<Appointment> = emptyList(),
    activeEmails: List<String>,
    receptionAdminEmails: List<String>,
    receptionEmails: List<String>,
    adminEmails: List<String>,
    queue: BatchQueue,
  ) {
    val officeRef = db.collection("offices").document(office.id)
    queue.enqueue { batch ->
      batch.update(
        officeRef,
        mapOf(
          "activeMemberEmails" to activeEmails,
          "receptionAdminEmails" to receptionAdminEmails,
          "receptionEmails" to receptionEmails,
          "adminEmails" to adminEmails,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
    }

    staffList.forEach { member ->
      if (member.id.isNotBlank()) {
        queue.enqueue { batch ->
          batch.update(
            officeRef.collection("staff").document(member.id),
            mapOf("allowedReaderEmails" to activeEmails, "updatedAt" to FieldValue.serverTimestamp()),
          )
        }
      }
    }

    reasons.forEach { reason ->
      if (reason.id.isNotBlank()) {
        queue.enqueue { batch ->
          batch.update(
            officeRef.collection("reasons").document(reason.id),
            mapOf("allowedReaderEmails" to activeEmails, "updatedAt" to FieldValue.serverTimestamp()),
          )
        }
      }
    }

    profiles.forEach { profile ->
      if (profile.id.isNotBlank()) {
        queue.enqueue { batch ->
          batch.update(
            officeRef.collection("visitorProfiles").document(profile.id),
            mapOf("allowedReaderEmails" to receptionAdminEmails, "updatedAt" to FieldValue.serverTimestamp()),
          )
        }
      }
    }

    visits.forEach { visit ->
      if (visit.id.isNotBlank()) {
        val cleanHostEmail = visit.hostEmail.trim().lowercase()
        val hostActive = cleanHostEmail.isNotBlank() && cleanHostEmail in activeEmails
        val readers =
          (if (hostActive) receptionAdminEmails + cleanHostEmail else receptionAdminEmails)
            .distinct()
            .take(100)
        queue.enqueue { batch ->
          batch.update(
            officeRef.collection("visits").document(visit.id),
            mapOf("allowedReaderEmails" to readers, "updatedAt" to FieldValue.serverTimestamp()),
          )
        }
      }
    }

    prCampaigns.forEach { campaign ->
      if (campaign.id.isNotBlank()) {
        val cleanOwnerEmail = campaign.ownerEmail.trim().lowercase()
        val readers =
          (if (cleanOwnerEmail.isNotBlank() && cleanOwnerEmail in activeEmails) {
            receptionAdminEmails + cleanOwnerEmail
          } else {
            receptionAdminEmails
          }).distinct().take(100)
        queue.enqueue { batch ->
          batch.update(
            officeRef.collection("prCampaigns").document(campaign.id),
            mapOf("allowedReaderEmails" to readers, "updatedAt" to FieldValue.serverTimestamp()),
          )
        }
      }
    }

    prContacts.forEach { contact ->
      if (contact.id.isNotBlank()) {
        val cleanOwnerEmail = contact.ownerEmail.trim().lowercase()
        val readers =
          (if (cleanOwnerEmail.isNotBlank() && cleanOwnerEmail in activeEmails) {
            receptionAdminEmails + cleanOwnerEmail
          } else {
            receptionAdminEmails
          }).distinct().take(100)
        queue.enqueue { batch ->
          batch.update(
            officeRef.collection("prContacts").document(contact.id),
            mapOf("allowedReaderEmails" to readers, "updatedAt" to FieldValue.serverTimestamp()),
          )
        }
      }
    }

    prGifts.forEach { gift ->
      if (gift.id.isNotBlank()) {
        val cleanOwnerEmail = gift.ownerEmail.trim().lowercase()
        val readers =
          (if (cleanOwnerEmail.isNotBlank() && cleanOwnerEmail in activeEmails) {
            receptionAdminEmails + cleanOwnerEmail
          } else {
            receptionAdminEmails
          }).distinct().take(100)
        queue.enqueue { batch ->
          batch.update(
            officeRef.collection("prGifts").document(gift.id),
            mapOf("allowedReaderEmails" to readers, "updatedAt" to FieldValue.serverTimestamp()),
          )
        }
      }
    }

    appointments.forEach { appt ->
      if (appt.id.isNotBlank()) {
        val cleanHostEmail = appt.hostEmail.trim().lowercase()
        val readers =
          (if (cleanHostEmail.isNotBlank() && cleanHostEmail in activeEmails) {
            receptionAdminEmails + cleanHostEmail
          } else {
            receptionAdminEmails
          }).distinct().take(100)
        queue.enqueue { batch ->
          batch.update(
            officeRef.collection("appointments").document(appt.id),
            mapOf("allowedReaderEmails" to readers, "updatedAt" to FieldValue.serverTimestamp()),
          )
        }
      }
    }
  }
}
