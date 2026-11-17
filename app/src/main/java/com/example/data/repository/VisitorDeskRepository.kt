package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ArchPointSeedData
import com.example.data.model.Office
import com.example.data.model.OfficeMembership
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.Visit
import com.example.data.model.VisitReason
import com.example.data.model.VisitStatus
import com.example.data.model.VisitorProfile
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
    val (activeEmails, receptionAdminEmails, _) = computeOfficeEmailLists(office, currentStaff)

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
        val targetSpecId = ArchPointSeedData.hostSpecIdForName(past.personToMeet)
        val hostMember = resolvedStaffById[targetSpecId]
        val hostShownAs = hostMember?.shownAs?.ifBlank { hostMember.fullName } ?: past.personToMeet
        val hostEmail = hostMember?.email?.trim()?.lowercase().orEmpty()
        val hostCabin = hostMember?.formattedSeating()?.ifBlank { hostMember.cabin }.orEmpty()
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
        val targetSpecId = ArchPointSeedData.hostSpecIdForName(latestSpec.personToMeet)
        val hostId = resolvedStaffById[targetSpecId]?.id ?: targetSpecId
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
              "createdAt" to ts,
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

  suspend fun askVisitorToWait(officeId: String, visitId: String): Result<Unit> {
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
            "waitNote" to "asked to wait 10 min",
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

  suspend fun denyVisitorToday(officeId: String, visitId: String): Result<Unit> {
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
            "status" to VisitStatus.DENIED.wireValue,
            "waitNote" to "can't meet today",
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
  ): Result<Unit> {
    val cleanName = fullName.trim().take(80)
    val cleanShown = shownAs.trim().take(60).ifBlank { cleanName.split(" ").firstOrNull() ?: "Staff" }
    val cleanEmail = email.trim().lowercase().take(120)
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
          officeOwnerUid = office.ownerUid,
        )
      val updatedList = currentStaff + newMember
      val (activeEmails, receptionAdminEmails, adminEmails) = computeOfficeEmailLists(office, updatedList)

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
        activeEmails = activeEmails,
        receptionAdminEmails = receptionAdminEmails,
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
      val (activeEmails, receptionAdminEmails, adminEmails) = computeOfficeEmailLists(office, updatedList)
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
        activeEmails = activeEmails,
        receptionAdminEmails = receptionAdminEmails,
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

  private fun computeOfficeEmailLists(
    office: Office,
    staffList: List<StaffMember>,
  ): Triple<List<String>, List<String>, List<String>> {
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

    val adminEmails =
      (staffList
        .filter { it.activeAccount && it.email.isNotBlank() && it.parsedRole() == StaffRole.ADMIN }
        .map { it.email.trim().lowercase() } + office.ownerEmail.lowercase())
        .filter { it.isNotBlank() }
        .distinct()
        .take(100)

    return Triple(activeEmails, receptionAdminEmails, adminEmails)
  }

  private fun syncOfficeAccessControl(
    office: Office,
    staffList: List<StaffMember>,
    reasons: List<VisitReason>,
    profiles: List<VisitorProfile>,
    visits: List<Visit>,
    activeEmails: List<String>,
    receptionAdminEmails: List<String>,
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

    visits.take(250).forEach { visit ->
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
  }
}
