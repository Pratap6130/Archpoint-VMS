package com.example

import com.example.data.model.ArchPointSeedData
import com.example.data.model.StaffMember
import com.example.data.model.Visit
import com.example.util.DeskFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchPointSeedAndFormatterTest {
  @Test
  fun teamDirectoryIncludesAllElevenMembersAndHimanshiGupta() {
    assertEquals(11, ArchPointSeedData.teamDirectory.size)
    val himanshi = ArchPointSeedData.teamDirectory.firstOrNull { it.fullName == "Himanshi Gupta" }
    assertTrue(himanshi != null)
    assertEquals("Ground Floor", himanshi?.floor)
    assertEquals("Himanshi Seating", himanshi?.cabin)
    assertTrue(himanshi?.hostListed == true)

    val amit = ArchPointSeedData.teamDirectory.first { it.shownAs == "Amit Sir" }
    assertEquals("2nd Floor · Amit Cabin", amit.formattedSeating())
    assertEquals("Studio (3rd Floor)", amit.otherLocation)
  }

  @Test
  fun pastVisitsCatalogContainsAllFiftyFiveRecords() {
    assertEquals(55, ArchPointSeedData.pastVisits.size)
    assertEquals("Mr. Kishan", ArchPointSeedData.pastVisits.first().visitorName)
    assertEquals("Vijendra", ArchPointSeedData.pastVisits.last().visitorName)
    assertEquals("PK chouhan", ArchPointSeedData.pastVisits.first { it.submissionId == "6DlYlyP" }.visitorName)
    assertEquals("Client", ArchPointSeedData.pastVisits.first { it.submissionId == "aOLJN9E" }.visitorType)
    assertTrue(ArchPointSeedData.defaultReasons.contains("Client"))
    assertTrue(ArchPointSeedData.pastVisits.all { it.resolvedReason().isNotBlank() })
  }

  @Test
  fun staffSeatingAndCsvSanitizationWorkCleanly() {
    val staff = StaffMember(floor = "Ground", cabin = "Reception and waiting")
    assertEquals("Ground floor · Reception and waiting", staff.formattedSeating())

    val visit =
      Visit(
        visitorName = "=SUM(A1)",
        visitorCompany = "Acme",
        visitorJobProfile = "Director",
        visitorMobile = "919928789771",
        hostShownAs = "Amit Sir",
        reason = "Connection",
        remark = "Gift",
        status = "Done",
      )
    val csv = DeskFormatters.buildSafeVisitorCsv(listOf(visit))
    assertTrue(csv.contains("'=SUM(A1)"))
    assertTrue(csv.contains("Director"))
    assertTrue(csv.contains("Gift"))
  }

  @Test
  fun visitorLogFilterIncludesAllHistoricalVisitsWhenRangeIsOpenAndFiltersSearch() {
    val allSeededVisits =
      ArchPointSeedData.pastVisits.map { past ->
        val ts = past.parseSubmittedTimestamp()
        Visit(
          id = past.docId,
          visitorName = past.visitorName,
          visitorCompany = past.company,
          visitorMobile = past.phone,
          visitorJobProfile = past.jobProfile,
          reason = past.resolvedReason(),
          remark = past.remark,
          hostShownAs = past.personToMeet,
          status = "Done",
          arrivedAt = ts,
          waitStartedAt = ts,
          endedAt = ts,
        )
      }

    // Open range (fromMillis = 0L, toMillis = 0L) includes all 55 Oct-Nov 2026 visits regardless of phone clock
    val futurePhoneNowMillis = 1_900_000_000_000L // Year 2030
    val allMatched =
      com.example.ui.screens.filterVisitorLogVisits(
        visits = allSeededVisits,
        fromMillis = 0L,
        toMillis = 0L,
        searchQuery = "",
        nowMillis = futurePhoneNowMillis,
      )
    assertEquals(55, allMatched.size)

    // Verify authentic CSV month/year is preserved (Oct/Nov 2026)
    val firstDateLabel = DeskFormatters.formatCsvDate(allSeededVisits.first().arrivedAt?.toDate())
    val lastDateLabel = DeskFormatters.formatCsvDate(allSeededVisits.last().arrivedAt?.toDate())
    assertEquals("2026-10-31", firstDateLabel)
    assertEquals("2026-11-19", lastDateLabel)

    // Narrow query matches across remark, company, or name
    val diwaliMatches =
      com.example.ui.screens.filterVisitorLogVisits(
        visits = allSeededVisits,
        fromMillis = 0L,
        toMillis = 0L,
        searchQuery = "Elite window",
        nowMillis = futurePhoneNowMillis,
      )
    assertEquals(1, diwaliMatches.size)
    assertEquals("Nayan", diwaliMatches.first().visitorName)
  }
}
