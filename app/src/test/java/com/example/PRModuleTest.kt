package com.example

import com.example.data.model.AppTab
import com.example.data.model.PRCampaign
import com.example.data.model.PRCampaignStatus
import com.example.data.model.PRContact
import com.example.data.model.PRContactTier
import com.example.data.model.PRFollowUpStatus
import com.example.data.model.PRGift
import com.example.data.model.PRGiftDirection
import com.example.data.model.PRGiftStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.VisitorProfile
import com.example.ui.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PRModuleTest {
  @Test
  fun hostPrFiltersIsolateCampaignsContactsAndGiftsToOwnerEmailOnly() {
    val campaigns =
      listOf(
        PRCampaign(id = "c1", title = "Amit Press Meet", ownerEmail = "amit@archpoint.in"),
        PRCampaign(id = "c2", title = "Neha Event", ownerEmail = "neha@archpoint.in"),
      )
    val contacts =
      listOf(
        PRContact(id = "p1", name = "Editor A", ownerEmail = "AMIT@archpoint.in"),
        PRContact(id = "p2", name = "Editor B", ownerEmail = "neha@archpoint.in"),
      )
    val gifts =
      listOf(
        PRGift(id = "g1", recipientOrSender = "Client X", ownerEmail = "amit@archpoint.in"),
        PRGift(id = "g2", recipientOrSender = "Client Y", ownerEmail = "vasudha@archpoint.in"),
      )

    val amitCampaigns = MainViewModel.filterHostSpecificPrCampaigns(campaigns, " amit@archpoint.in ")
    val amitContacts = MainViewModel.filterHostSpecificPrContacts(contacts, "amit@archpoint.in")
    val amitGifts = MainViewModel.filterHostSpecificPrGifts(gifts, "amit@archpoint.in")

    assertEquals(listOf("c1"), amitCampaigns.map { it.id })
    assertEquals(listOf("p1"), amitContacts.map { it.id })
    assertEquals(listOf("g1"), amitGifts.map { it.id })
    assertTrue(MainViewModel.filterHostSpecificPrCampaigns(campaigns, "").isEmpty())
  }

  @Test
  fun computePrMetricsAggregatesLiveActiveDueAndPendingCounts() {
    val campaigns =
      listOf(
        PRCampaign(id = "c1", title = "Expo", status = PRCampaignStatus.LIVE.wireValue),
        PRCampaign(id = "c2", title = "Launch", status = PRCampaignStatus.ACTIVE.wireValue),
        PRCampaign(id = "c3", title = "Archive", status = PRCampaignStatus.COMPLETED.wireValue),
      )
    val contacts =
      listOf(
        PRContact(id = "p1", name = "VIP 1", followUpStatus = PRFollowUpStatus.FOLLOW_UP_DUE.wireValue),
        PRContact(id = "p2", name = "VIP 2", followUpStatus = PRFollowUpStatus.CONNECTED.wireValue),
      )
    val gifts =
      listOf(
        PRGift(id = "g1", recipientOrSender = "A", itemTitle = "Box", status = PRGiftStatus.PREPARED.wireValue),
        PRGift(id = "g2", recipientOrSender = "B", itemTitle = "Book", status = PRGiftStatus.ACKNOWLEDGED.wireValue),
      )

    val metrics = MainViewModel.computePrMetrics(campaigns, contacts, gifts)
    assertEquals(2, metrics.activeCampaigns)
    assertEquals(1, metrics.liveCampaigns)
    assertEquals(2, metrics.totalContacts)
    assertEquals(1, metrics.dueFollowUps)
    assertEquals(1, metrics.pendingGifts)
  }

  @Test
  fun statusProgressionsCycleThroughLifecycleStagesCleanly() {
    assertEquals(PRCampaignStatus.ACTIVE, MainViewModel.nextPrCampaignStatus(PRCampaignStatus.PLANNED))
    assertEquals(PRCampaignStatus.LIVE, MainViewModel.nextPrCampaignStatus(PRCampaignStatus.ACTIVE))
    assertEquals(PRCampaignStatus.COMPLETED, MainViewModel.nextPrCampaignStatus(PRCampaignStatus.LIVE))
    assertEquals(PRCampaignStatus.PLANNED, MainViewModel.nextPrCampaignStatus(PRCampaignStatus.COMPLETED))

    assertEquals(PRFollowUpStatus.FOLLOW_UP_DUE, MainViewModel.nextPrFollowUpStatus(PRFollowUpStatus.WARM))
    assertEquals(PRFollowUpStatus.SCHEDULED, MainViewModel.nextPrFollowUpStatus(PRFollowUpStatus.FOLLOW_UP_DUE))
    assertEquals(PRFollowUpStatus.CONNECTED, MainViewModel.nextPrFollowUpStatus(PRFollowUpStatus.SCHEDULED))
    assertEquals(PRFollowUpStatus.WARM, MainViewModel.nextPrFollowUpStatus(PRFollowUpStatus.CONNECTED))

    assertEquals(PRGiftStatus.DISPATCHED, MainViewModel.nextPrGiftStatus(PRGiftStatus.PREPARED))
    assertEquals(PRGiftStatus.DELIVERED, MainViewModel.nextPrGiftStatus(PRGiftStatus.DISPATCHED))
    assertEquals(PRGiftStatus.ACKNOWLEDGED, MainViewModel.nextPrGiftStatus(PRGiftStatus.DELIVERED))
    assertEquals(PRGiftStatus.PREPARED, MainViewModel.nextPrGiftStatus(PRGiftStatus.ACKNOWLEDGED))
  }

  @Test
  fun validatorsRejectBlankTitlesNamesAndMalformedContactDetails() {
    assertNotNull(MainViewModel.validatePrCampaign("   "))
    assertNull(MainViewModel.validatePrCampaign("Jaipur Monograph Launch"))

    assertNotNull(MainViewModel.validatePrContact("", "", ""))
    assertNotNull(MainViewModel.validatePrContact("Riya", "bad-email", ""))
    assertNotNull(MainViewModel.validatePrContact("Riya", "", "123"))
    assertNull(MainViewModel.validatePrContact("Riya", "riya@press.in", "9829012345"))

    assertNotNull(MainViewModel.validatePrGift("", "Monograph"))
    assertNotNull(MainViewModel.validatePrGift("Mittal Group", "  "))
    assertNull(MainViewModel.validatePrGift("Mittal Group", "ArchPoint Monograph"))
  }

  @Test
  fun promoteVisitorProfileBuildsCleanPrContactAndStripsDummyPhone() {
    val profile =
      VisitorProfile(
        id = "p_dummy",
        name = "Vikram Mehta",
        company = "Heritage Estates",
        mobile = "919999999999",
        email = "VIKRAM@HERITAGE.IN",
        visitorJobProfile = "Managing Partner",
        remark = "Met at Jaipur Design Week",
      )
    val host =
      StaffMember(
        id = "staff_amit",
        fullName = "Ar. Amit Khandelwal",
        shownAs = "Amit Sir",
        email = "amit@archpoint.in",
        role = StaffRole.HOST.wireValue,
      )

    val contact =
      MainViewModel.buildPrContactFromVisitorProfile(
        profile = profile,
        ownerStaff = host,
        fallbackOwnerEmail = "reception@archpoint.in",
        fallbackOwnerName = "Reception",
        tier = PRContactTier.VIP,
      )

    assertEquals("Vikram Mehta", contact.name)
    assertEquals("Heritage Estates", contact.organization)
    assertEquals("Managing Partner", contact.designation)
    assertEquals(PRContactTier.VIP.wireValue, contact.tier)
    assertEquals("", contact.mobile)
    assertEquals("vikram@heritage.in", contact.email)
    assertEquals("staff_amit", contact.ownerStaffId)
    assertEquals("Amit Sir", contact.ownerName)
    assertEquals("amit@archpoint.in", contact.ownerEmail)
  }

  @Test
  fun allRolesIncludePrTabWhilePreservingRoleBoundaries() {
    assertTrue(AppTab.PR in MainViewModel.resolveTabsForRole(StaffRole.ADMIN, hostListed = false))
    assertTrue(AppTab.PR in MainViewModel.resolveTabsForRole(StaffRole.RECEPTION, hostListed = false))
    assertTrue(AppTab.PR in MainViewModel.resolveTabsForRole(StaffRole.HOST, hostListed = true))
    assertFalse(AppTab.ADMIN in MainViewModel.resolveTabsForRole(StaffRole.HOST, hostListed = true))
  }

  @Test
  fun birthdayCountdownHandlesMultipleDateFormatsAndThirtyDayMetricsWindow() {
    val cal = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.MARCH, 10, 9, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val nowMillis = cal.timeInMillis

    // Same day (March 10) -> 0 days
    assertEquals(0, com.example.util.DeskFormatters.daysUntilBirthday("03/10/1988", nowMillis))
    // 10 days away (March 20 in dd/MM/yyyy format where day > 12)
    assertEquals(10, com.example.util.DeskFormatters.daysUntilBirthday("20/03/1990", nowMillis))
    // Text format ("25 Mar") -> 15 days away
    assertEquals(15, com.example.util.DeskFormatters.daysUntilBirthday("25 Mar", nowMillis))
    // Past date in same year (March 5) -> wraps to next year (~360 days)
    val wrapped = com.example.util.DeskFormatters.daysUntilBirthday("03/05/1985", nowMillis)
    assertNotNull(wrapped)
    assertTrue(wrapped!! > 350)

    val contacts =
      listOf(
        PRContact(id = "c1", name = "Today Bday", dob = "03/10/1988"),
        PRContact(id = "c2", name = "Soon Bday", dob = "25 Mar"),
        PRContact(id = "c3", name = "Far Bday", dob = "08/15/1992"),
      )
    val metrics = MainViewModel.computePrMetrics(emptyList(), contacts, emptyList(), nowMillis)
    assertEquals(2, metrics.upcomingBirthdays)
  }

  @Test
  fun promoteHostClientBuildsPrivateHostOwnedPrContactAndFormatsWishMessage() {
    val client =
      com.example.ui.viewmodel.HostClientSummary(
        key = "client_1",
        name = "Nidhi Sharma",
        company = "DesignCraft",
        mobile = "919999999999",
        email = "NIDHI@DESIGNCRAFT.IN",
        jobProfile = "Principal Architect",
        visitCount = 3,
        completedCount = 2,
        lastVisitedAtMillis = 1_700_000_000_000L,
        lastReason = "Client Brief",
        lastRemark = "Interested in residential façade collaboration",
        lastStatus = com.example.data.model.VisitStatus.DONE,
      )
    val host =
      StaffMember(
        id = "staff_neha",
        fullName = "Neha Gupta",
        shownAs = "Neha",
        email = "neha@archpoint.in",
        role = StaffRole.HOST.wireValue,
      )

    val contact =
      MainViewModel.buildPrContactFromHostClient(
        client = client,
        ownerStaff = host,
        fallbackOwnerEmail = "fallback@archpoint.in",
        fallbackOwnerName = "Fallback",
        tier = PRContactTier.PARTNER,
      )
    assertEquals("Nidhi Sharma", contact.name)
    assertEquals("DesignCraft", contact.organization)
    assertEquals("Principal Architect", contact.designation)
    assertEquals("", contact.mobile)
    assertEquals("nidhi@designcraft.in", contact.email)
    assertEquals("neha@archpoint.in", contact.ownerEmail)
    assertEquals(PRContactTier.PARTNER.wireValue, contact.tier)

    val wish = MainViewModel.buildBirthdayWishMessage(contact, "Neha")
    assertTrue(wish.contains("Nidhi"))
    assertTrue(wish.contains("DesignCraft"))
    assertTrue(wish.contains("Neha"))
  }
}
