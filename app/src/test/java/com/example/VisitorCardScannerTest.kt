package com.example

import com.example.data.ai.ScannedVisitorCard
import com.example.data.ai.VisitorCardScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VisitorCardScannerTest {
  @Test
  fun normalizeScannedPhoneHandlesIndianCountryCodesAndFormatting() {
    assertEquals("9829012345", VisitorCardScanner.normalizeScannedPhone("+91 98290-12345"))
    assertEquals("9829012345", VisitorCardScanner.normalizeScannedPhone("098290 12345"))
    assertEquals("9829012345", VisitorCardScanner.normalizeScannedPhone("9829012345"))
    assertEquals("", VisitorCardScanner.normalizeScannedPhone(""))
  }

  @Test
  fun parseCardExtractionJsonPopulatesAllFieldsCleanly() {
    val json =
      """
      {
        "name": "  Rakesh Mittal ",
        "company": "Mittal Constructions",
        "mobile": "+91 98290-12345",
        "email": "rakesh@mittal.in",
        "jobProfile": "Managing Director",
        "remark": "Jaipur"
      }
      """.trimIndent()

    val card = VisitorCardScanner.parseCardExtractionJson(json)
    assertEquals("Rakesh Mittal", card.name)
    assertEquals("Mittal Constructions", card.company)
    assertEquals("9829012345", card.mobile)
    assertEquals("rakesh@mittal.in", card.email)
    assertEquals("Managing Director", card.jobProfile)
    assertEquals("Jaipur", card.remark)
    assertEquals("Managing Director · Jaipur", card.resolvedRemark())
    assertTrue(card.hasAnyDetails)
  }

  @Test
  fun blankCardHasNoDetails() {
    val blank = ScannedVisitorCard()
    assertFalse(blank.hasAnyDetails)
    assertEquals("", blank.resolvedRemark())
  }
}
