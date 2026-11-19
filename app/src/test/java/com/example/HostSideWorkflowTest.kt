package com.example

import com.example.alerts.VisitorAlertManager
import com.example.data.model.AppTab
import com.example.data.model.AvailabilityTone
import com.example.data.model.HostAvailability
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.Visit
import com.example.data.model.VisitStatus
import com.example.ui.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostSideWorkflowTest {
  @Test
  fun hostPortalTabsHideReceptionGlobalLogsAndAdminControls() {
    val tabs = MainViewModel.resolveTabsForRole(StaffRole.HOST, hostListed = true)
    assertEquals(listOf(AppTab.MY_VISITORS, AppTab.PR, AppTab.PROFILE), tabs)
    assertFalse(AppTab.RECEPTION in tabs)
    assertFalse(AppTab.VISITOR_LOG in tabs)
    assertFalse(AppTab.ADMIN in tabs)
  }

  @Test
  fun hostNotificationDetectionTargetsOnlyMatchingHostEmail() {
    val newVisitForAmit =
      Visit(
        id = "visit_amit_1",
        visitorName = "Saurabh",
        visitorCompany = "Design Studio",
        reason = "Project Review",
        hostStaffId = "staff_amit",
        hostShownAs = "Amit Sir",
        hostEmail = "amit@archpoint.in",
        status = VisitStatus.WAITING.wireValue,
      )

    val alertForAmit =
      VisitorAlertManager.detectVisitAlert(
        officeId = "office_1",
        userEmail = "AMIT@archpoint.in",
        isReception = false,
        prev = null,
        curr = newVisitForAmit,
      )
    assertNotNull(alertForAmit)
    assertTrue(alertForAmit?.isHostAlert == true)
    assertEquals(AppTab.MY_VISITORS, alertForAmit?.targetTab)

    val alertForNeha =
      VisitorAlertManager.detectVisitAlert(
        officeId = "office_1",
        userEmail = "neha@archpoint.in",
        isReception = false,
        prev = null,
        curr = newVisitForAmit,
      )
    assertNull("Other hosts must not receive Amit's visitor alert", alertForNeha)
  }

  @Test
  fun hostAvailabilityTonesMapFreeBusyAwayAndMeetingToGreenOrangeRed() {
    assertEquals(AvailabilityTone.GREEN, HostAvailability.AVAILABLE.tone)
    assertEquals("Free", HostAvailability.AVAILABLE.receptionLabel)

    assertEquals(AvailabilityTone.ORANGE, HostAvailability.BUSY.tone)
    assertEquals("Busy", HostAvailability.BUSY.receptionLabel)

    assertEquals(AvailabilityTone.RED, HostAvailability.AWAY.tone)
    assertEquals("Away", HostAvailability.AWAY.receptionLabel)

    assertEquals(
      AvailabilityTone.ORANGE,
      HostAvailability.resolveTone(HostAvailability.AVAILABLE, inMeeting = true),
    )
    assertEquals(
      AvailabilityTone.RED,
      HostAvailability.resolveTone(HostAvailability.AWAY, inMeeting = false),
    )
  }

  @Test
  fun hostCanCustomizeProfileDisplayNameAndSeatingFormatting() {
    assertEquals("Amit Sir", MainViewModel.normalizeShownAs("Amitabh Verma", "Amit Sir"))
    assertEquals("Vartika", MainViewModel.normalizeShownAs("Vartika Singh", "   "))
    assertEquals("Staff", MainViewModel.normalizeShownAs("", ""))

    val host =
      StaffMember(
        id = "staff_amit",
        fullName = "Amitabh Verma",
        shownAs = "Amit Sir",
        designation = "Principal Architect",
        floor = "2nd Floor",
        cabin = "Amit Cabin",
      )
    assertEquals("2nd Floor · Amit Cabin", host.formattedSeating())
  }

  @Test
  fun hostVisitAndClientScopeNeverLeaksOtherHostsData() {
    val amitVisit =
      Visit(
        id = "v_1",
        visitorName = "Mahesh",
        visitorMobile = "9829011122",
        visitorCompany = "StoneCraft",
        hostStaffId = "staff_amit",
        hostEmail = "amit@archpoint.in",
        status = VisitStatus.DONE.wireValue,
      )
    val yogendraVisit =
      Visit(
        id = "v_2",
        visitorName = "Private Client",
        visitorMobile = "9829099988",
        visitorCompany = "Secret Ltd",
        hostStaffId = "staff_yogendra",
        hostEmail = "yogendra@archpoint.in",
        status = VisitStatus.WAITING.wireValue,
      )

    val scopedVisits =
      MainViewModel.filterHostSpecificVisits(
        visits = listOf(amitVisit, yogendraVisit),
        userEmail = "yogendra@archpoint.in",
        staffId = "staff_yogendra",
      )
    assertEquals(listOf("v_2"), scopedVisits.map { it.id })

    val amitClients =
      MainViewModel.buildHostClients(
        visits = listOf(amitVisit, yogendraVisit),
        userEmail = "amit@archpoint.in",
        staffId = "staff_amit",
      )
    assertEquals(1, amitClients.size)
    assertEquals("Mahesh", amitClients.first().name)
    assertFalse(amitClients.any { it.name == "Private Client" })
  }
}
