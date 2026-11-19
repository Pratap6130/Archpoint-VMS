package com.example

import com.example.data.model.AppTab
import com.example.data.model.AvailabilityTone
import com.example.data.model.HostAvailability
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.Visit
import com.example.data.model.VisitStatus
import com.example.data.model.VisitorProfile
import com.example.data.ai.ScannedVisitorCard
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ReceptionFormState
import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceptionSideWorkflowTest {
  private val hosts =
    listOf(
      StaffMember(
        id = "h_amit",
        fullName = "Amitabh Verma",
        shownAs = "Amit Sir",
        email = "amit@archpoint.in",
        role = StaffRole.HOST.wireValue,
        floor = "2nd Floor",
        cabin = "Amit Cabin",
        availabilityStatus = HostAvailability.AVAILABLE.wireValue,
        hostListed = true,
        activeAccount = true,
      ),
      StaffMember(
        id = "h_neha",
        fullName = "Neha Gupta",
        shownAs = "Neha",
        email = "neha@archpoint.in",
        role = StaffRole.HOST.wireValue,
        floor = "2nd Floor",
        cabin = "Neha Cabin",
        availabilityStatus = HostAvailability.BUSY.wireValue,
        hostListed = true,
        activeAccount = true,
      ),
      StaffMember(
        id = "h_yogendra",
        fullName = "Yogendra Joshi",
        shownAs = "Yogendra",
        email = "yogendra@archpoint.in",
        role = StaffRole.HOST.wireValue,
        floor = "2nd Floor",
        cabin = "Yogendra Cabin",
        availabilityStatus = HostAvailability.AWAY.wireValue,
        hostListed = true,
        activeAccount = true,
      ),
    )

  @Test
  fun receptionRoleIncludesReceptionAndProfileWithoutAdminOrGlobalLogs() {
    val tabs = MainViewModel.resolveTabsForRole(StaffRole.RECEPTION, hostListed = false)
    assertEquals(listOf(AppTab.RECEPTION, AppTab.PR, AppTab.PROFILE), tabs)
  }

  @Test
  fun hostBoardMapsFreeBusyInMeetingAndAwayColorsCorrectly() {
    val now = 1_750_000_600_000L
    val visits =
      listOf(
        Visit(
          id = "v_meet",
          visitorName = "Client One",
          hostStaffId = "h_amit",
          status = VisitStatus.IN_MEETING.wireValue,
          meetingStartedAt = Timestamp(Date(now - 12 * 60_000L)),
        ),
        Visit(
          id = "v_wait",
          visitorName = "Waiting Client",
          hostStaffId = "h_neha",
          status = VisitStatus.WAITING.wireValue,
        ),
      )

    val board = MainViewModel.buildHostBoard(staff = hosts, visits = visits, now = now)
    assertEquals(3, board.size)

    val amitItem = board.first { it.staff.id == "h_amit" }
    val (amitLabel, amitTone) = MainViewModel.resolveHostAvailabilityVisual(amitItem)
    assertEquals(AvailabilityTone.ORANGE, amitTone)
    assertEquals("In meeting · 12m", amitLabel)

    val nehaItem = board.first { it.staff.id == "h_neha" }
    val (nehaLabel, nehaTone) = MainViewModel.resolveHostAvailabilityVisual(nehaItem)
    assertEquals(AvailabilityTone.ORANGE, nehaTone)
    assertEquals("Busy", nehaLabel)
    assertEquals(1, nehaItem.waitingCount)

    val yogItem = board.first { it.staff.id == "h_yogendra" }
    val (yogLabel, yogTone) = MainViewModel.resolveHostAvailabilityVisual(yogItem)
    assertEquals(AvailabilityTone.RED, yogTone)
    assertEquals("Away", yogLabel)

    // When Amit's meeting ends, status returns to Free (GREEN)
    val freeBoard = MainViewModel.buildHostBoard(staff = hosts, visits = emptyList(), now = now)
    val (freeLabel, freeTone) = MainViewModel.resolveHostAvailabilityVisual(freeBoard.first { it.staff.id == "h_amit" })
    assertEquals(AvailabilityTone.GREEN, freeTone)
    assertEquals("Free", freeLabel)
  }

  @Test
  fun cardScanAutofillsFormAndMatchesReturningProfileAndHost() {
    val profiles =
      listOf(
        VisitorProfile(
          id = "vp_1",
          name = "Rajesh Khanna",
          company = "Jaipur Marbles",
          mobile = "9829012345",
          email = "rajesh@jaipurmarbles.in",
          dob = "04/12/1984",
          visitCount = 4L,
          lastHostStaffId = "h_neha",
          lastReason = "Material Sample",
        )
      )
    val extracted =
      ScannedVisitorCard(
        name = "Rajesh Khanna",
        company = "Jaipur Marbles",
        mobile = "9829012345",
        email = "rajesh@jaipurmarbles.in",
        jobProfile = "Director",
      )
    val baseForm = ReceptionFormState(selectedReason = "Meeting", selectedHostId = "h_amit")
    val merged =
      MainViewModel.mergeScannedCardIntoForm(
        current = baseForm,
        extracted = extracted,
        profiles = profiles,
        hosts = hosts,
      )

    assertEquals("Rajesh Khanna", merged.name)
    assertEquals("Jaipur Marbles", merged.company)
    assertEquals("9829012345", merged.mobile)
    assertEquals("rajesh@jaipurmarbles.in", merged.email)
    assertEquals("04/12/1984", merged.dob)
    assertEquals("h_neha", merged.selectedHostId)
    assertEquals("Material Sample", merged.selectedReason)
    assertNotNull(merged.matchedProfile)
  }

  @Test
  fun validateReceptionCheckInRejectsIncompleteAndAcceptsCompleteForm() {
    val missingHost =
      ReceptionFormState(
        name = "Ankit",
        mobile = "9829012345",
        selectedReason = "Meeting",
        selectedHostId = "",
      )
    assertEquals("Please select a person to meet.", MainViewModel.validateReceptionCheckIn(missingHost, hosts))

    val shortMobile = missingHost.copy(selectedHostId = "h_amit", mobile = "98290")
    assertEquals("Enter a valid 10-digit mobile number.", MainViewModel.validateReceptionCheckIn(shortMobile, hosts))

    val emptyName = shortMobile.copy(mobile = "9829012345", name = "   ")
    assertEquals("Enter the visitor's name.", MainViewModel.validateReceptionCheckIn(emptyName, hosts))

    val emptyReason = emptyName.copy(name = "Ankit Sharma", selectedReason = "")
    assertEquals("Select a reason to meet.", MainViewModel.validateReceptionCheckIn(emptyReason, hosts))

    val validForm = emptyReason.copy(selectedReason = "Client")
    assertNull(MainViewModel.validateReceptionCheckIn(validForm, hosts))
  }

  @Test
  fun tappingRecentVisitorPopulatesDropdownHostReasonAndDetails() {
    val profile =
      VisitorProfile(
        id = "vp_recent",
        name = "Kavita Rao",
        company = "Kala Studio",
        mobile = "9988776655",
        email = "kavita@kalastudio.in",
        dob = "11/02/1990",
        lastHostStaffId = "h_neha",
        lastReason = "Site Vendor",
      )
    val filled =
      MainViewModel.populateFormFromProfile(
        current = ReceptionFormState(selectedHostId = "h_amit", selectedReason = "Meeting"),
        profile = profile,
        hosts = hosts,
      )
    assertEquals("Kavita Rao", filled.name)
    assertEquals("Kala Studio", filled.company)
    assertEquals("9988776655", filled.mobile)
    assertEquals("kavita@kalastudio.in", filled.email)
    assertEquals("h_neha", filled.selectedHostId)
    assertEquals("Site Vendor", filled.selectedReason)
    assertTrue(filled.matchedProfile?.id == "vp_recent")
  }
}
