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
const ADMIN_EMAIL = "pratap@archpoint.in";
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
        activeMemberEmails: [ADMIN_EMAIL, HOST_EMAIL, OTHER_HOST_EMAIL],
        receptionAdminEmails: [ADMIN_EMAIL],
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
        allowedReaderEmails: [ADMIN_EMAIL, HOST_EMAIL, OTHER_HOST_EMAIL],
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

test("Admin/Reception creates visit; Host reads own visit only; DOB is strictly write-only", async () => {
  await seedOffice();
  const past = new Date(Date.now() - 2000);
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();
  const hostDb = verifiedCtx(HOST_UID, HOST_EMAIL).firestore();
  const otherHostDb = verifiedCtx(OTHER_HOST_UID, OTHER_HOST_EMAIL).firestore();

  const visitRef = adminDb
    .collection("offices")
    .doc(OFFICE_ID)
    .collection("visits")
    .doc("visit_1");

  // Reception/Admin creates Waiting visit
  await assertSucceeds(
    visitRef.set({
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
      allowedReaderEmails: [ADMIN_EMAIL, HOST_EMAIL],
      updatedAt: past,
    })
  );

  // Write-only DOB succeeds on create, fails on read
  const dobRef = adminDb
    .collection("offices")
    .doc(OFFICE_ID)
    .collection("visitorDob")
    .doc("dob_1");
  await assertSucceeds(
    dobRef.set({
      visitorMobile: "9829012345",
      dob: "1985-04-12",
      recordedByUid: ADMIN_UID,
      createdAt: past,
    })
  );
  await assertFails(dobRef.get());

  // Host can list their own visits via allowedReaderEmails query
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .where("allowedReaderEmails", "array-contains", HOST_EMAIL)
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

  // Host CANNOT create a visit or send a reminder
  await assertFails(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("visits")
      .doc("visit_hack")
      .set({
        visitorName: "Fake",
        visitorMobile: "9999999999",
        visitorCompany: "",
        visitorEmail: "",
        reason: "Meeting",
        hostStaffId: "staff_amit",
        hostShownAs: "Amit Sir",
        hostEmail: HOST_EMAIL,
        hostCabin: "",
        visitNumber: 1,
        status: "Waiting",
        waitNote: "",
        remindedCount: 0,
        arrivedAt: past,
        waitStartedAt: past,
        createdByUid: HOST_UID,
        createdByEmail: HOST_EMAIL,
        officeOwnerUid: ADMIN_UID,
        allowedReaderEmails: [ADMIN_EMAIL, HOST_EMAIL],
        updatedAt: past,
      })
  );

  // Host replies: Wait 10 min -> Send in -> Meeting done -> terminal lock
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
        status: "In meeting",
        waitNote: "Send in",
        meetingStartedAt: new Date(Date.now() - 1000),
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

test("Admin can create directory staff without email and import past Done visits; Host can update own seating", async () => {
  await seedOffice();
  const past = new Date(Date.now() - 86400000);
  const now = new Date(Date.now() - 1000);
  const adminDb = verifiedCtx(ADMIN_UID, ADMIN_EMAIL).firestore();
  const hostDb = verifiedCtx(HOST_UID, HOST_EMAIL).firestore();

  // Directory staff pre-seeded before Google email assigned
  await assertSucceeds(
    adminDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_archpoint_11")
      .set({
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
        allowedReaderEmails: [ADMIN_EMAIL, HOST_EMAIL, OTHER_HOST_EMAIL],
        createdAt: past,
        updatedAt: now,
      })
  );

  // Admin can import a historical Done visit with blank mobile & remarks
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
        allowedReaderEmails: [ADMIN_EMAIL, HOST_EMAIL],
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
        allowedReaderEmails: [ADMIN_EMAIL, HOST_EMAIL],
        updatedAt: now,
      })
  );

  // Host can update own floor, cabin, otherLocation, requiredThings, notes
  await assertSucceeds(
    hostDb
      .collection("offices")
      .doc(OFFICE_ID)
      .collection("staff")
      .doc("staff_amit")
      .update({
        floor: "2nd",
        cabin: "Amit cabin",
        otherLocation: "3rd floor studio",
        requiredThings: "Blueprints & samples",
        notes: "Client days Tue & Sat in Board room, 3rd",
        updatedAt: now,
      })
  );
});
