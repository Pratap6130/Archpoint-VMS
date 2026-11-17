package com.example

import com.example.alerts.VisitorAlertManager
import com.example.data.model.AppTab
import com.example.data.model.ArchPointSeedData
import com.example.data.model.HostAvailability
import com.example.data.model.StaffRole
import com.example.data.model.Visit
import com.example.data.model.VisitStatus
import com.example.ui.components.matchesLocationText
import com.example.ui.components.matchesSeating
import com.example.ui.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleAccessTest {
  @Test
  fun onlyPrimaryAdminEmailIsAllowedToBootstrapWorkspace() {
    assertTrue(MainViewModel.isBootstrapAdminEmail("kunwarsingh9752005805@gmail.com"))
    assertTrue(MainViewModel.isBootstrapAdminEmail("  KUNWARSINGH9752005805@GMAIL.COM "))
    assertFalse(MainViewModel.isBootstrapAdminEmail("unknown.stranger@gmail.com"))
    assertFalse(MainViewModel.isBootstrapAdminEmail("host@archpoint.in"))
    assertFalse(MainViewModel.isBootstrapAdminEmail(""))
  }

  @Test
  fun adminDashboardExcludesReceptionTab() {
    val nonHostAdminTabs = MainViewModel.resolveTabsForRole(StaffRole.ADMIN, hostListed = false)
    assertEquals(listOf(AppTab.ADMIN, AppTab.VISITOR_LOG, AppTab.PROFILE), nonHostAdminTabs)
    assertFalse(AppTab.RECEPTION in nonHostAdminTabs)
    assertFalse(AppTab.MY_VISITORS in nonHostAdminTabs)

    val hostListedAdminTabs = MainViewModel.resolveTabsForRole(StaffRole.ADMIN, hostListed = true)
    assertEquals(
      listOf(AppTab.ADMIN, AppTab.VISITOR_LOG, AppTab.MY_VISITORS, AppTab.PROFILE),
      hostListedAdminTabs,
    )
    assertFalse(AppTab.RECEPTION in hostListedAdminTabs)
  }

  @Test
  fun receptionDashboardExcludesAdminAndGlobalVisitorLogTabs() {
    val receptionTabs = MainViewModel.resolveTabsForRole(StaffRole.RECEPTION, hostListed = false)
    assertEquals(listOf(AppTab.RECEPTION, AppTab.PROFILE), receptionTabs)
    assertFalse(AppTab.ADMIN in receptionTabs)
    assertFalse(AppTab.VISITOR_LOG in receptionTabs)

    val hostListedReceptionTabs = MainViewModel.resolveTabsForRole(StaffRole.RECEPTION, hostListed = true)
    assertEquals(
      listOf(AppTab.RECEPTION, AppTab.MY_VISITORS, AppTab.PROFILE),
      hostListedReceptionTabs,
    )
    assertFalse(AppTab.ADMIN in hostListedReceptionTabs)
    assertFalse(AppTab.VISITOR_LOG in hostListedReceptionTabs)
  }

  @Test
  fun hostDashboardIsLimitedToMyDeskAndProfile() {
    val hostTabs = MainViewModel.resolveTabsForRole(StaffRole.HOST, hostListed = true)
    assertEquals(listOf(AppTab.MY_VISITORS, AppTab.PROFILE), hostTabs)
    assertFalse(AppTab.RECEPTION in hostTabs)
    assertFalse(AppTab.ADMIN in hostTabs)
    assertFalse(AppTab.VISITOR_LOG in hostTabs)
  }

  @Test
  fun hostSpecificVisitFilterIsolatesOtherHostsVisits() {
    val amitVisit =
      Visit(
        id = "v_amit",
        visitorName = "Rakesh",
        hostStaffId = "staff_amit",
        hostEmail = "amit@archpoint.in",
      )
    val nehaVisit =
      Visit(
        id = "v_neha",
        visitorName = "Devendra",
        hostStaffId = "staff_neha",
        hostEmail = "neha@archpoint.in",
      )
    val unassignedEmailSameId =
      Visit(
        id = "v_legacy_amit",
        visitorName = "Kishan",
        hostStaffId = "staff_amit",
        hostEmail = "",
      )

    val scoped =
      MainViewModel.filterHostSpecificVisits(
        visits = listOf(amitVisit, nehaVisit, unassignedEmailSameId),
        userEmail = "  AMIT@archpoint.in ",
        staffId = "staff_amit",
      )

    assertEquals(listOf("v_amit", "v_legacy_amit"), scoped.map { it.id })
    assertFalse(scoped.any { it.id == "v_neha" })
  }

  @Test
  fun hostAvailabilityParsesKnownAndFallbackStatuses() {
    assertEquals(HostAvailability.AVAILABLE, HostAvailability.fromWire("Available"))
    assertEquals(HostAvailability.BUSY, HostAvailability.fromWire("busy"))
    assertEquals(HostAvailability.AWAY, HostAvailability.fromWire("Away"))
    assertEquals(HostAvailability.AVAILABLE, HostAvailability.fromWire(null))
  }

  @Test
  fun receptionReplyAlertsOnlyNotifyReceptionNotAdmin() {
    val prev =
      Visit(
        id = "visit_1",
        visitorName = "Rohan",
        reason = "Meeting",
        hostShownAs = "Amit Sir",
        hostEmail = "amit@archpoint.in",
        status = VisitStatus.WAITING.wireValue,
      )
    val curr = prev.copy(status = VisitStatus.IN_MEETING.wireValue)

    val adminAlert =
      VisitorAlertManager.detectVisitAlert(
        officeId = "office_1",
        userEmail = "kunwarsingh9752005805@gmail.com",
        isReception = false,
        prev = prev,
        curr = curr,
      )
    assertNull(adminAlert)

    val receptionAlert =
      VisitorAlertManager.detectVisitAlert(
        officeId = "office_1",
        userEmail = "reception@archpoint.in",
        isReception = true,
        prev = prev,
        curr = curr,
      )
    assertNotNull(receptionAlert)
    assertEquals(AppTab.RECEPTION, receptionAlert?.targetTab)
  }

  @Test
  fun hostCustomWaitAndInMeetingNotesPropagateToReceptionAlerts() {
    val waitingPrev =
      Visit(
        id = "visit_note_1",
        visitorName = "Priya",
        reason = "Project Review",
        hostShownAs = "Amit Sir",
        hostEmail = "amit@archpoint.in",
        status = VisitStatus.WAITING.wireValue,
        waitNote = "",
      )
    val waitingReply = waitingPrev.copy(waitNote = "Coming out to reception")
    val replyAlert =
      VisitorAlertManager.detectVisitAlert(
        officeId = "office_1",
        userEmail = "reception@archpoint.in",
        isReception = true,
        prev = waitingPrev,
        curr = waitingReply,
      )
    assertNotNull(replyAlert)
    assertEquals("Amit Sir: Coming out to reception", replyAlert?.notifTitle)

    val meetingPrev = waitingPrev.copy(status = VisitStatus.IN_MEETING.wireValue, waitNote = "Send in")
    val meetingNote = meetingPrev.copy(waitNote = "Send tea / coffee")
    val meetingAlert =
      VisitorAlertManager.detectVisitAlert(
        officeId = "office_1",
        userEmail = "reception@archpoint.in",
        isReception = true,
        prev = meetingPrev,
        curr = meetingNote,
      )
    assertNotNull(meetingAlert)
    assertEquals("Amit Sir: Send tea / coffee", meetingAlert?.notifTitle)
  }

  @Test
  fun buildHostClientsAggregatesOnlyOwnVisitorsAndExcludesOtherHostsClients() {
    val visits =
      listOf(
        Visit(
          id = "v1",
          visitorName = "Rakesh Mittal",
          visitorMobile = "9829012345",
          visitorCompany = "Mittal Group",
          reason = "Meeting",
          remark = "Project handover",
          hostStaffId = "staff_amit",
          hostEmail = "amit@archpoint.in",
          status = VisitStatus.DONE.wireValue,
        ),
        Visit(
          id = "v2",
          visitorName = "Rakesh Mittal",
          visitorMobile = "9829012345",
          visitorCompany = "Mittal Group",
          reason = "Site Vendor",
          hostStaffId = "staff_amit",
          hostEmail = "amit@archpoint.in",
          status = VisitStatus.WAITING.wireValue,
        ),
        Visit(
          id = "v3",
          visitorName = "Confidential Client",
          visitorMobile = "9999888877",
          visitorCompany = "Other Co",
          reason = "Meeting",
          hostStaffId = "staff_neha",
          hostEmail = "neha@archpoint.in",
          status = VisitStatus.DONE.wireValue,
        ),
      )

    val amitClients =
      MainViewModel.buildHostClients(
        visits = visits,
        userEmail = "amit@archpoint.in",
        staffId = "staff_amit",
      )
    assertEquals(1, amitClients.size)
    assertEquals("Rakesh Mittal", amitClients.first().name)
    assertEquals(2, amitClients.first().visitCount)
    assertEquals(1, amitClients.first().completedCount)
    assertEquals("Project handover", amitClients.first().lastRemark)
    assertFalse(amitClients.any { it.name == "Confidential Client" })
  }

  @Test
  fun officeLocationPresetsMatchAllRequestedFloorsAndCabins() {
    val presets = ArchPointSeedData.locationPresets
    assertEquals(9, presets.size)

    val studio = presets.first { it.id == "studio_3rd" }
    assertEquals("3rd Floor", studio.floor)
    assertTrue(studio.matchesLocationText("Studio (3rd Floor)"))

    val conference = presets.first { it.id == "conference_2nd" }
    assertEquals("2nd Floor", conference.floor)
    assertTrue(conference.matchesSeating("2nd", "Conference"))

    val secondFloorCabins =
      listOf("Amit Cabin", "Vartika Cabin", "Neha Cabin", "Vasudha Cabin", "Yogendra Cabin")
    secondFloorCabins.forEach { cabinName ->
      val match = presets.firstOrNull { it.cabin == cabinName }
      assertNotNull("Expected preset for $cabinName", match)
      assertEquals("2nd Floor", match?.floor)
    }

    val poonam = presets.first { it.id == "poonam_seating_ground" }
    assertEquals("Ground Floor", poonam.floor)
    assertEquals("Poonam Ma'am Seating", poonam.cabin)

    val himanshi = presets.first { it.id == "himanshi_seating_ground" }
    assertEquals("Ground Floor", himanshi.floor)
    assertEquals("Himanshi Seating", himanshi.cabin)
  }
}
