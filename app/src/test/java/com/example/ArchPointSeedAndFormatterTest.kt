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
  fun pastVisitsCatalogContainsAllFortyThreeRecords() {
    assertEquals(43, ArchPointSeedData.pastVisits.size)
    assertEquals("Mr. Kishan", ArchPointSeedData.pastVisits.first().visitorName)
    assertEquals("Harshvardhan singh", ArchPointSeedData.pastVisits.last().visitorName)
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
}
