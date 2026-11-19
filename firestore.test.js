const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_1";
const ADMIN_EMAIL = "kunwarsingh9752005805@gmail.com";
const RECEPTION_UID = "reception_uid_4";
const RECEPTION_EMAIL = "reception@archpoint.in";
const HOST_UID = "host_uid_2";
const HOST_EMAIL = "amit@archpoint.in";
const OTHER_HOST_UID = "host_uid_3";
const OTHER_HOST_EMAIL = "neha@archpoint.in";
const OUTSIDER_UID = "outsider_uid_9";
const OUTSIDER_EMAIL = "stranger@gmail.com";
const OFFICE_ID = "office_archpoint_1";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function verifiedCtx(uid, email) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: true,
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedOffice() {
  const past = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("offices")
      .doc(OFFICE_ID)
      .set({
        name: "ArchPoint Jaipur",
        ownerUid: ADMIN_UID,
        ownerEmail: ADMIN_EMAIL,
        activeMemberEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL, OTHER_HOST_EMAIL],
        receptionAdminEmails: [ADMIN_EMAIL, RECEPTION_EMAIL],
        receptionEmails: [RECEPTION_EMAIL],
        adminEmails: [ADMIN_EMAIL],
        createdAt: past,
        updatedAt: past,
      });
    await db
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_amit")
      .set({
        fullName: "Amit Khandelwal",
        shownAs: "Amit Sir",
        email: HOST_EMAIL,
        role: "host",
        cabin: "Amit cabin, 2nd",
        listOrder: 2,
        hostListed: true,
        activeAccount: true,
        uid: HOST_UID,
        officeOwnerUid: ADMIN_UID,
        allowedReaderEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL, OTHER_HOST_EMAIL],
        createdAt: past,
        updatedAt: past,
      });
  });
}

test("Unauthenticated and unverified users cannot read or write", async () => {
  await seedOffice();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("offices").doc(OFFICE_ID).get());
  await assertFails(
    unauthDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .where("allowedReaderEmails", "array-contains", ADMIN_EMAIL)
      .get()
  );

  // Spoofed unverified email check
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
  await assertFails(spoofDb.collection("offices").doc(OFFICE_ID).get());
});

test("Only primary admin can initialize an office; strangers cannot create rogue offices", async () => {
  const past = new Date(Date.now() - 2000);
  const outsiderDb = verifiedCtx(OUTSIDER_UID, OUTSIDER_EMAIL).firestore();
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();

  await assertFails(
    outsiderDb.collection("offices").doc("rogue_office").set({
      name: "Rogue Office",
      ownerUid: OUTSIDER_UID,
      ownerEmail: OUTSIDER_EMAIL,
      activeMemberEmails: [OUTSIDER_EMAIL],
      receptionAdminEmails: [OUTSIDER_EMAIL],
      receptionEmails: [],
      adminEmails: [OUTSIDER_EMAIL],
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    adminDb.collection("offices").doc("bootstrapped_office").set({
      name: "ArchPoint Jaipur",
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      activeMemberEmails: [ADMIN_EMAIL],
      receptionAdminEmails: [ADMIN_EMAIL],
      receptionEmails: [],
      adminEmails: [ADMIN_EMAIL],
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Reception creates visits & DOB; Admin cannot control Reception queue; Host reads & acts on own visits only", async () => {
  await seedOffice();
  const past = new Date(Date.now() - 2000);
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();
  const receptionDb = verifiedCtx(RECEPTION_UID, RECEPTION_EMAIL).firestore();
  const hostDb = verifiedCtx(HOST_UID, HOST_EMAIL).firestore();
  const otherHostDb = verifiedCtx(OTHER_HOST_UID, OTHER_HOST_EMAIL).firestore();

  const visitPayload = {
    visitorName: "Rakesh Mittal",
    visitorMobile: "9829012345",
    visitorCompany: "Mittal Constructions",
    visitorEmail: "rakesh@example.com",
    reason: "Meeting",
    hostStaffId: "staff_amit",
    hostShownAs: "Amit Sir",
    hostEmail: HOST_EMAIL,
    hostCabin: "Amit cabin, 2nd",
    visitNumber: 5,
    status: "Waiting",
    waitNote: "",
    remindedCount: 0,
    arrivedAt: past,
    waitStartedAt: past,
    createdByUid: ADMIN_UID,
    createdByEmail: ADMIN_EMAIL,
    officeOwnerUid: ADMIN_UID,
    allowedReaderEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL],
    updatedAt: past,
  };

  // Admin CANNOT check in a live Waiting visitor at Reception
  await assertFails(
    adminDb.collection("offices").doc(OFFICE_ID).collection("visits").doc("visit_admin_nope").set(visitPayload)
  );

  // Reception creates Waiting visit
  const visitRef = receptionDb
    .collection("offices")
    .doc(OFFICE_ID)
    .collection("visits")
    .doc("visit_1");
  await assertSucceeds(
    visitRef.set({
      ...visitPayload,
      createdByUid: RECEPTION_UID,
      createdByEmail: RECEPTION_EMAIL,
    })
  );

  // Write-only DOB succeeds for Reception on create, fails on read
  const dobRef = receptionDb
    .collection("offices")
    .doc(OFFICE_ID)
    .collection("visitorDob")
    .doc("dob_1");
  await assertSucceeds(
    dobRef.set({
      visitorMobile: "9829012345",
      dob: "1985-04-12",
      recordedByUid: RECEPTION_UID,
      createdAt: past,
    })
  );
  await assertFails(dobRef.get());

  // Admin CANNOT trigger Reception remind on another host's visit
  await assertFails(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_1")
      .update({
        remindedCount: 1,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reception CAN trigger remind
  await assertSucceeds(
    visitRef.update({
      remindedCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Host can list ONLY visitor notifications associated with their own email
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .where("hostEmail", "==", HOST_EMAIL)
      .get()
  );

  // Host CANNOT query another host's visitor notifications or unfiltered global visits
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .where("hostEmail", "==", OTHER_HOST_EMAIL)
      .get()
  );
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .get()
  );

  // Other host CANNOT read Amit's visit
  await assertFails(
    otherHostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_1")
      .get()
  );

  // Host CANNOT create a visit
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_hack")
      .set({
        ...visitPayload,
        createdByUid: HOST_UID,
        createdByEmail: HOST_EMAIL,
      })
  );

  // Host replies: Wait 10 min -> custom quick reply -> Send in -> in-meeting note -> Meeting done -> terminal lock
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_1")
      .update({
        waitNote: "asked to wait 10 min",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_1")
      .update({
        waitNote: "Wait 15 min · Coming out shortly",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Empty waitNote is rejected
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_1")
      .update({
        waitNote: "",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_1")
      .update({
        status: "In meeting",
        waitNote: "Send in · Board room",
        meetingStartedAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Host can send a note while In meeting
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_1")
      .update({
        waitNote: "Need 10 more min",
        updatedAt: new Date(Date.now() - 750),
      })
  );

  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_1")
      .update({
        status: "Done",
        endedAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Terminal state lock: cannot reopen Done visit
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_1")
      .update({
        status: "Waiting",
        updatedAt: new Date(Date.now() - 100),
      })
  );
});

test("Outsider and deactivated staff are blocked", async () => {
  await seedOffice();
  const outsiderDb = verifiedCtx(OUTSIDER_UID, OUTSIDER_EMAIL).firestore();
  await assertFails(outsiderDb.collection("offices").doc(OFFICE_ID).get());
  await assertFails(
    outsiderDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visitorProfiles")
      .where("allowedReaderEmails", "array-contains", OUTSIDER_EMAIL)
      .get()
  );
});

test("Admin manages staff & past Done visits; Reception cannot modify staff; Host updates own seating", async () => {
  await seedOffice();
  const past = new Date(Date.now() - 86400000);
  const now = new Date(Date.now() - 1000);
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();
  const receptionDb = verifiedCtx(RECEPTION_UID, RECEPTION_EMAIL).firestore();
  const hostDb = verifiedCtx(HOST_UID, HOST_EMAIL).firestore();

  const staffPayload = {
    fullName: "Himanshi Gupta",
    shownAs: "Himanshi Gupta",
    email: "",
    role: "host",
    designation: "",
    department: "ArchPoint Wellness",
    floor: "Ground",
    cabin: "ArchPoint Wellness cabin",
    otherLocation: "",
    requiredThings: "",
    notes: "",
    listOrder: 9,
    hostListed: true,
    activeAccount: true,
    uid: "",
    officeOwnerUid: ADMIN_UID,
    allowedReaderEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL, OTHER_HOST_EMAIL],
    createdAt: past,
    updatedAt: now,
  };

  // Reception CANNOT add staff
  await assertFails(
    receptionDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_archpoint_11")
      .set(staffPayload)
  );

  // Admin CAN add directory staff
  await assertSucceeds(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_archpoint_11")
      .set(staffPayload)
  );

  // Admin can import a historical Done visit
  await assertSucceeds(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_past_12")
      .set({
        visitorName: "Bhim Singh",
        visitorMobile: "",
        visitorCompany: "BJP",
        visitorEmail: "",
        visitorType: "Connection",
        visitorJobProfile: "",
        remark: "For 3rd floor",
        reason: "Connection",
        hostStaffId: "staff_amit",
        hostShownAs: "Amit Sir",
        hostEmail: HOST_EMAIL,
        hostCabin: "2nd · Amit cabin",
        visitNumber: 1,
        status: "Done",
        waitNote: "Past entry",
        remindedCount: 0,
        arrivedAt: past,
        waitStartedAt: past,
        endedAt: past,
        createdByUid: ADMIN_UID,
        createdByEmail: ADMIN_EMAIL,
        officeOwnerUid: ADMIN_UID,
        allowedReaderEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL],
        updatedAt: now,
      })
  );

  // Host CANNOT import Done historical visits directly
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_past_hack")
      .set({
        visitorName: "Fake",
        visitorMobile: "",
        visitorCompany: "",
        visitorEmail: "",
        reason: "Meeting",
        hostStaffId: "staff_amit",
        hostShownAs: "Amit Sir",
        hostEmail: HOST_EMAIL,
        hostCabin: "",
        visitNumber: 1,
        status: "Done",
        waitNote: "",
        remindedCount: 0,
        arrivedAt: past,
        waitStartedAt: past,
        endedAt: past,
        createdByUid: HOST_UID,
        createdByEmail: HOST_EMAIL,
        officeOwnerUid: ADMIN_UID,
        allowedReaderEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL],
        updatedAt: now,
      })
  );

  // Host (and any active staff) can update own profile (name, designation, photoUrl, seating, requiredThings, notes, availabilityStatus)
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_amit")
      .update({
        fullName: "Amit Khandelwal",
        shownAs: "Amit Sir",
        designation: "Principal Architect",
        department: "Architecture",
        photoUrl: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
        floor: "2nd Floor",
        cabin: "Amit Cabin",
        otherLocation: "Studio (3rd Floor)",
        requiredThings: "Blueprints & samples",
        notes: "Client days Tue & Sat in Conference (2nd Floor)",
        availabilityStatus: "Busy",
        updatedAt: now,
      })
  );

  // Host CANNOT escalate own role, email, or hostListed status
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_amit")
      .update({
        role: "admin",
        updatedAt: now,
      })
  );

  // Admin can update colleague photoUrl within limit, but oversized photoUrl (> 120000 chars) is rejected
  await assertSucceeds(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_amit")
      .update({
        photoUrl: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
        updatedAt: now,
      })
  );
  await assertFails(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_amit")
      .update({
        photoUrl: "a".repeat(120001),
        updatedAt: now,
      })
  );

  // Host CAN read own staff doc & query own email, but CANNOT list or get other colleagues' staff docs
  await assertSucceeds(
    hostDb.collection("offices").doc(OFFICE_ID).collection("staff").doc("staff_amit").get()
  );
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .where("email", "==", HOST_EMAIL)
      .get()
  );
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .where("allowedReaderEmails", "array-contains", HOST_EMAIL)
      .get()
  );
  await assertFails(
    hostDb.collection("offices").doc(OFFICE_ID).collection("staff").doc("staff_archpoint_11").get()
  );

  // Host CANNOT read global visitorProfiles directory
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visitorProfiles")
      .where("allowedReaderEmails", "array-contains", HOST_EMAIL)
      .get()
  );
});

test("PR module enforces Admin/Reception workspace visibility, Host owner-only privacy, and Admin-only delete", async () => {
  await seedOffice();
  const past = new Date(Date.now() - 2000);
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();
  const receptionDb = verifiedCtx(RECEPTION_UID, RECEPTION_EMAIL).firestore();
  const hostDb = verifiedCtx(HOST_UID, HOST_EMAIL).firestore();
  const otherHostDb = verifiedCtx(OTHER_HOST_UID, OTHER_HOST_EMAIL).firestore();
  const outsiderDb = verifiedCtx(OUTSIDER_UID, OUTSIDER_EMAIL).firestore();

  const amitCampaignPayload = {
    title: "Jaipur Architecture Showcase",
    channel: "Event",
    status: "Active",
    targetDateLabel: "24 Nov",
    location: "Studio (3rd Floor)",
    audienceSummary: "Luxury developers & press",
    notes: "Prepare project portfolio",
    ownerStaffId: "staff_amit",
    ownerName: "Amit Sir",
    ownerEmail: HOST_EMAIL,
    createdByUid: RECEPTION_UID,
    createdByEmail: RECEPTION_EMAIL,
    officeOwnerUid: ADMIN_UID,
    allowedReaderEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL],
    createdAt: past,
    updatedAt: past,
  };

  // Reception creates a PR campaign assigned to Amit Sir
  await assertSucceeds(
    receptionDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prCampaigns")
      .doc("pr_camp_1")
      .set(amitCampaignPayload)
  );

  // Admin and Reception can read office PR campaigns via allowedReaderEmails
  await assertSucceeds(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prCampaigns")
      .where("allowedReaderEmails", "array-contains", ADMIN_EMAIL)
      .get()
  );

  // Host (Amit) can query ONLY his own PR campaigns
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prCampaigns")
      .where("ownerEmail", "==", HOST_EMAIL)
      .get()
  );

  // Other Host (Neha) CANNOT read Amit's campaign or list unfiltered global campaigns
  await assertFails(
    otherHostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prCampaigns")
      .doc("pr_camp_1")
      .get()
  );
  await assertFails(
    otherHostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prCampaigns")
      .where("ownerEmail", "==", HOST_EMAIL)
      .get()
  );
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prCampaigns")
      .where("allowedReaderEmails", "array-contains", HOST_EMAIL)
      .get()
  );

  // Host CANNOT assign a new PR contact to another host's email
  const contactBase = {
    name: "Ananya Verma",
    organization: "Architectural Digest India",
    designation: "Senior Editor",
    tier: "Media",
    mobile: "9811002233",
    email: "ananya@adindia.in",
    followUpStatus: "Follow-up Due",
    nextFollowUpDate: "Tomorrow",
    notes: "Send high-res renders",
    ownerStaffId: "staff_amit",
    ownerName: "Amit Sir",
    ownerEmail: HOST_EMAIL,
    createdByUid: HOST_UID,
    createdByEmail: HOST_EMAIL,
    officeOwnerUid: ADMIN_UID,
    allowedReaderEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL],
    createdAt: past,
    updatedAt: past,
  };
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prContacts")
      .doc("pr_contact_hack")
      .set({
        ...contactBase,
        ownerEmail: OTHER_HOST_EMAIL,
      })
  );

  // Host CAN create & update a PR contact owned by themselves
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prContacts")
      .doc("pr_contact_1")
      .set(contactBase)
  );
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prContacts")
      .doc("pr_contact_1")
      .update({
        followUpStatus: "Connected",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Host CANNOT reassign ownership or alter allowedReaderEmails on update
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prContacts")
      .doc("pr_contact_1")
      .update({
        ownerEmail: OTHER_HOST_EMAIL,
        updatedAt: new Date(Date.now() - 900),
      })
  );

  // Admin creates PR gift; Reception & Host cannot delete PR items; Admin CAN delete
  await assertSucceeds(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prGifts")
      .doc("pr_gift_1")
      .set({
        recipientOrSender: "Rakesh Mittal",
        company: "Mittal Group",
        itemTitle: "ArchPoint Studio Monograph + Brass Lamp",
        direction: "Sent",
        status: "Prepared",
        occasion: "Diwali gift",
        dateLabel: "Today",
        notes: "Include handwritten note",
        ownerStaffId: "staff_amit",
        ownerName: "Amit Sir",
        ownerEmail: HOST_EMAIL,
        createdByUid: ADMIN_UID,
        createdByEmail: ADMIN_EMAIL,
        officeOwnerUid: ADMIN_UID,
        allowedReaderEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL],
        createdAt: past,
        updatedAt: past,
      })
  );

  await assertFails(
    receptionDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prGifts")
      .doc("pr_gift_1")
      .delete()
  );
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prGifts")
      .doc("pr_gift_1")
      .delete()
  );
  await assertFails(
    outsiderDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prGifts")
      .doc("pr_gift_1")
      .get()
  );
  await assertSucceeds(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prGifts")
      .doc("pr_gift_1")
      .delete()
  );

  // Host CAN add PR contact with profile photo, birthday, special gift preference, and wish tracking
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prContacts")
      .doc("pr_contact_photo")
      .set({
        ...contactBase,
        photoUrl: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
        dob: "04/12/1988",
        anniversaryDate: "18 Nov",
        lastWishedYear: 2026,
        specialGiftPreference: "Sugar-free festive hamper + Monograph",
        cityOrAddress: "C-Scheme, Jaipur",
      })
  );

  // Oversized PR contact photo (>120000 chars) is rejected
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("prContacts")
      .doc("pr_contact_big_photo")
      .set({
        ...contactBase,
        photoUrl: "a".repeat(120001),
      })
  );
});

test("Appointment booking allows Host self-booking and Reception/Admin office-wide booking with strict host isolation", async () => {
  await seedOffice();
  const past = new Date(Date.now() - 2000);
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();
  const receptionDb = verifiedCtx(RECEPTION_UID, RECEPTION_EMAIL).firestore();
  const hostDb = verifiedCtx(HOST_UID, HOST_EMAIL).firestore();
  const otherHostDb = verifiedCtx(OTHER_HOST_UID, OTHER_HOST_EMAIL).firestore();
  const outsiderDb = verifiedCtx(OUTSIDER_UID, OUTSIDER_EMAIL).firestore();

  const amitApptPayload = {
    visitorName: "Vikram Rathore",
    visitorMobile: "9829001122",
    visitorCompany: "Rathore Heritage Homes",
    visitorEmail: "vikram@rathore.in",
    purpose: "Project Brief",
    location: "Amit Cabin · 2nd Floor",
    scheduledDateIso: "2026-11-20",
    scheduledTimeLabel: "03:30 PM",
    scheduledAtMillis: Date.now() + 86400000,
    notes: "Bring elevation renders",
    hostStaffId: "staff_amit",
    hostShownAs: "Amit Sir",
    hostEmail: HOST_EMAIL,
    hostCabin: "Amit cabin, 2nd",
    status: "Scheduled",
    bookedByRole: "reception",
    createdByUid: RECEPTION_UID,
    createdByEmail: RECEPTION_EMAIL,
    officeOwnerUid: ADMIN_UID,
    allowedReaderEmails: [ADMIN_EMAIL, RECEPTION_EMAIL, HOST_EMAIL],
    createdAt: past,
    updatedAt: past,
  };

  // Reception can book an appointment for Amit
  await assertSucceeds(
    receptionDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .doc("appt_1")
      .set(amitApptPayload)
  );

  // Amit (Host) can book an appointment ONLY for himself
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .doc("appt_host_self")
      .set({
        ...amitApptPayload,
        bookedByRole: "host",
        createdByUid: HOST_UID,
        createdByEmail: HOST_EMAIL,
      })
  );

  // Host CANNOT book an appointment under another host's email
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .doc("appt_host_spoof")
      .set({
        ...amitApptPayload,
        hostEmail: OTHER_HOST_EMAIL,
        bookedByRole: "host",
        createdByUid: HOST_UID,
        createdByEmail: HOST_EMAIL,
      })
  );

  // Amit can query his own appointments
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .where("hostEmail", "==", HOST_EMAIL)
      .get()
  );

  // Neha (other host) CANNOT read Amit's appointment or query Amit's appointments
  await assertFails(
    otherHostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .doc("appt_1")
      .get()
  );
  await assertFails(
    otherHostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .where("hostEmail", "==", HOST_EMAIL)
      .get()
  );

  // Outsider cannot read
  await assertFails(
    outsiderDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .doc("appt_1")
      .get()
  );

  // Reception marks appt_1 Checked in, and Amit marks appt_host_self Completed
  await assertSucceeds(
    receptionDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .doc("appt_1")
      .update({
        status: "Checked in",
        updatedAt: new Date(Date.now() - 500),
      })
  );
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .doc("appt_host_self")
      .update({
        status: "Completed",
        updatedAt: new Date(Date.now() - 400),
      })
  );

  // Only Admin can delete appointments; Reception and Host cannot
  await assertFails(
    receptionDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .doc("appt_1")
      .delete()
  );
  await assertSucceeds(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("appointments")
      .doc("appt_1")
      .delete()
  );
});
