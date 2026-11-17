package com.example

import com.example.data.model.StaffMember
import com.example.data.model.Visit
import com.example.data.model.VisitorProfile
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ReceptionFormState
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class RecentVisitorAutofillTest {
  private val hosts =
    listOf(
      StaffMember(id = "host_1", fullName = "Amit Khandelwal", shownAs = "Amit Sir", hostListed = true),
      StaffMember(id = "host_2", fullName = "Poonam Jain", shownAs = "Poonam Ma'am", hostListed = true),
    )

  @Test
  fun populateFormFromProfileFillsNameOrgPhoneEmailAndHost() {
    val profile =
      VisitorProfile(
        id = "9829012345",
        mobile = "+91 98290-12345",
        name = "  Rakesh Mittal ",
        company = " Mittal Builders ",
        email = " rakesh@mittal.in ",
        dob = "04/15/1988",
        visitCount = 4L,
        lastHostStaffId = "host_2",
        lastReason = "Vendor",
      )

    val initial = ReceptionFormState(selectedReason = "Meeting", selectedHostId = "host_1")
    val filled = MainViewModel.populateFormFromProfile(initial, profile, hosts)

    assertEquals("Rakesh Mittal", filled.name)
    assertEquals("Mittal Builders", filled.company)
    assertEquals("919829012345", filled.mobile)
    assertEquals("rakesh@mittal.in", filled.email)
    assertEquals("04/15/1988", filled.dob)
    assertEquals("host_2", filled.selectedHostId)
    assertEquals("Vendor", filled.selectedReason)
    assertEquals(profile, filled.matchedProfile)
  }

  @Test
  fun populateFormClearsPlaceholderPhoneAndPreviousVisitorDobRemark() {
    val first =
      VisitorProfile(
        id = "prof_1",
        mobile = "9829000001",
        name = "Anita Sharma",
        company = "Design Co",
        email = "anita@design.co",
        dob = "01/01/1990",
        lastHostStaffId = "host_1",
        lastReason = "Meeting",
      )
    val second =
      VisitorProfile(
        id = "prof_2",
        mobile = "919999999999",
        name = "Vikram Patel",
        company = "Patel Metals",
        email = "vikram@patel.com",
        dob = "",
        lastHostStaffId = "unknown_host",
        lastReason = "",
      )

    val afterFirst =
      MainViewModel.populateFormFromProfile(
        ReceptionFormState(remark = "First visit note", selectedReason = "Meeting", selectedHostId = "host_1"),
        first,
        hosts,
      )
    val afterSecond = MainViewModel.populateFormFromProfile(afterFirst, second, hosts)

    assertEquals("Vikram Patel", afterSecond.name)
    assertEquals("Patel Metals", afterSecond.company)
    assertEquals("", afterSecond.mobile)
    assertEquals("vikram@patel.com", afterSecond.email)
    assertEquals("", afterSecond.dob)
    assertEquals("", afterSecond.remark)
    assertEquals("host_1", afterSecond.selectedHostId)
    assertEquals("Meeting", afterSecond.selectedReason)
    assertEquals(second, afterSecond.matchedProfile)
  }

  @Test
  fun buildRecentVisitorsOrdersByLatestVisitDeduplicatesAndHandlesTransientVisit() {
    val savedProfile =
      VisitorProfile(
        id = "9829012345",
        mobile = "9829012345",
        name = "Rakesh Mittal",
        company = "Mittal Builders",
        email = "rakesh@mittal.in",
        dob = "04/15/1988",
        visitCount = 3L,
      )
    val olderProfileOnly =
      VisitorProfile(
        id = "9829099999",
        mobile = "9829099999",
        name = "Suresh Gupta",
        company = "Gupta Glass",
        email = "suresh@gupta.in",
        visitCount = 1L,
        updatedAt = Timestamp(Date(1_000_000L)),
      )

    val visits =
      listOf(
        Visit(
          id = "v_older_rakesh",
          visitorName = "Rakesh Mittal",
          visitorCompany = "Mittal Builders",
          visitorMobile = "9829012345",
          arrivedAt = Timestamp(Date(2_000_000L)),
        ),
        Visit(
          id = "v_latest_walkin",
          visitorName = "Neha Kapoor",
          visitorCompany = "Kapoor Interiors",
          visitorMobile = "9811122233",
          visitorEmail = "neha@kapoor.in",
          visitNumber = 1L,
          hostStaffId = "host_2",
          reason = "Site Vendor",
          arrivedAt = Timestamp(Date(5_000_000L)),
        ),
        Visit(
          id = "v_recent_rakesh",
          visitorName = "Rakesh Mittal",
          visitorCompany = "Mittal Builders",
          visitorMobile = "9829012345",
          arrivedAt = Timestamp(Date(4_000_000L)),
        ),
      )

    val recent =
      MainViewModel.buildRecentVisitors(
        profiles = listOf(olderProfileOnly, savedProfile),
        visits = visits,
        limit = 10,
      )

    assertEquals(3, recent.size)
    // Most recent visit first (Neha Kapoor, transient profile without doc id)
    assertEquals("Neha Kapoor", recent[0].name)
    assertEquals("", recent[0].id)
    assertEquals("neha@kapoor.in", recent[0].email)
    // Next most recent visit matched to savedProfile (preserving doc id & dob, deduplicated)
    assertEquals("Rakesh Mittal", recent[1].name)
    assertEquals("9829012345", recent[1].id)
    assertEquals("04/15/1988", recent[1].dob)
    // Remaining profile not in recent visits
    assertEquals("Suresh Gupta", recent[2].name)

    // Populating from transient profile leaves matchedProfile null so check-in creates a fresh profile
    val filledTransient = MainViewModel.populateFormFromProfile(ReceptionFormState(), recent[0], hosts)
    assertEquals("Neha Kapoor", filledTransient.name)
    assertEquals("Kapoor Interiors", filledTransient.company)
    assertEquals("9811122233", filledTransient.mobile)
    assertEquals("neha@kapoor.in", filledTransient.email)
    assertNull(filledTransient.matchedProfile)
  }
}
