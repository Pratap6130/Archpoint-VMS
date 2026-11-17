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

  // Host can update own floor, cabin, otherLocation, requiredThings, notes, and availabilityStatus
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_amit")
      .update({
        floor: "2nd Floor",
        cabin: "Amit Cabin",
        otherLocation: "Studio (3rd Floor)",
        requiredThings: "Blueprints & samples",
        notes: "Client days Tue & Sat in Conference (2nd Floor)",
        availabilityStatus: "Busy",
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
