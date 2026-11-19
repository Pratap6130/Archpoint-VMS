package com.example

import com.example.data.model.ArchPointSeedData
import com.example.data.model.StaffMember
import com.example.data.model.Visit
import com.example.util.TimeOfDayBucket
import com.example.util.VisitorAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitorAnalyticsTest {
  private val seededStaff: List<StaffMember> =
    ArchPointSeedData.teamDirectory.map { spec ->
      StaffMember(
        id = spec.id,
        fullName = spec.fullName,
        shownAs = spec.shownAs,
        role = spec.role.wireValue,
        designation = spec.designation,
        department = spec.department,
        floor = spec.floor,
        cabin = spec.cabin,
        otherLocation = spec.otherLocation,
        notes = spec.notes,
        listOrder = spec.listOrder,
        hostListed = spec.hostListed,
      )
    }

  private val seededVisits: List<Visit> =
    ArchPointSeedData.pastVisits.map { past ->
      val isOtherPerson = past.personToMeet.trim().equals("Other", ignoreCase = true)
      val hostId = if (isOtherPerson) "other" else ArchPointSeedData.hostSpecIdForName(past.personToMeet)
      val host = if (isOtherPerson) null else seededStaff.firstOrNull { it.id == hostId }
      val ts = past.parseSubmittedTimestamp()
      Visit(
        id = past.docId,
        visitorName = past.visitorName,
        visitorMobile = past.phone,
        visitorCompany = past.company,
        visitorEmail = past.email,
        visitorType = past.visitorType,
        visitorJobProfile = past.jobProfile,
        remark = past.remark,
        reason = past.resolvedReason(),
        hostStaffId = hostId,
        hostShownAs = if (isOtherPerson) "Other (2nd Floor)" else (host?.shownAs ?: past.personToMeet),
        status = "Done",
        arrivedAt = ts,
        waitStartedAt = ts,
        endedAt = ts,
      )
    }

  @Test
  fun aggregatesAllFiftyFivePastVisitsByDepartmentAndTimeOfDay() {
    val report = VisitorAnalytics.buildReport(visits = seededVisits, staff = seededStaff)

    assertEquals(55, report.totalVisits)
    assertNotNull(report.topDepartment)
    assertEquals("Management", report.topDepartment?.department)
    assertTrue((report.topDepartment?.totalVisits ?: 0) >= 35)

    assertNotNull(report.peakHour)
    assertNotNull(report.peakPeriod)
    assertEquals(TimeOfDayBucket.MORNING, report.peakPeriod?.bucket)
    assertTrue(report.departmentSummaries.any { it.department == "Arch Point Group" })
    assertTrue(report.departmentSummaries.any { it.department == "Operations" })
    assertTrue(report.departmentSummaries.any { it.department == "HR" })
    assertTrue(report.departmentSummaries.any { it.department == "General" })
    assertTrue(report.reasonBreakdown.isNotEmpty())
  }

  @Test
  fun departmentFilterIsolatesTrafficCorrectly() {
    val report =
      VisitorAnalytics.buildReport(
        visits = seededVisits,
        staff = seededStaff,
        departmentFilter = "Arch Point Group",
      )

    assertEquals(12, report.totalVisits)
    assertEquals(1, report.departmentSummaries.size)
    assertEquals("Arch Point Group", report.departmentSummaries.first().department)
    // Filter chips list still contains all departments from full dataset
    assertTrue(report.departments.contains("Management"))
    assertTrue(report.departments.contains("Arch Point Group"))
  }

  @Test
  fun emptyVisitsProducesCleanZeroReport() {
    val report = VisitorAnalytics.buildReport(visits = emptyList(), staff = seededStaff)
    assertEquals(0, report.totalVisits)
    assertEquals(12, report.hourlySeries.size)
    assertEquals("—", report.busiestDayLabel)
  }
}
