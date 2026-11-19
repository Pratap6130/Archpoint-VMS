package com.example

import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.VisitReason
import com.example.data.model.VisitorProfile
import com.example.data.selfcheckin.SelfCheckInPayload
import com.example.data.selfcheckin.SelfCheckInServerConfig
import com.example.data.selfcheckin.SelfCheckInWebServer
import com.example.data.selfcheckin.VisitorQrCodec
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ReceptionFormState
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisitorQrAndSelfCheckInTest {

  @Test
  fun `archPoint pass round trips cleanly through QR encoder and parser`() {
    val input =
      SelfCheckInPayload(
        name = "Jeet Sethi",
        mobile = "+91 93510 46635",
        company = "HFN Capital",
        email = "sethijeet09@gmail.com",
        dob = "1996-09-23",
        reason = "Connection",
        hostId = "amit",
        hostName = "Ar. Amit Khandelwal",
        remark = "EKASA meeting",
      )

    val encoded = VisitorQrCodec.encodeArchPointPass(input)
    assertTrue(encoded.startsWith(VisitorQrCodec.PASS_PREFIX))

    val decoded = VisitorQrCodec.parseQrContent(encoded)
    assertNotNull(decoded)
    assertEquals("Jeet Sethi", decoded?.name)
    assertEquals("9351046635", decoded?.mobile)
    assertEquals("HFN Capital", decoded?.company)
    assertEquals("sethijeet09@gmail.com", decoded?.email)
    assertEquals("Connection", decoded?.reason)
    assertEquals("amit", decoded?.hostId)
    assertEquals("Ar. Amit Khandelwal", decoded?.hostName)
    assertEquals("EKASA meeting", decoded?.remark)

    val qrBitmap = VisitorQrCodec.renderQrBitmap(encoded, sizePx = 128)
    assertNotNull(qrBitmap)
    assertEquals(128, qrBitmap?.width)
    assertEquals(128, qrBitmap?.height)
  }

  @Test
  fun `parseQrContent supports vCard MECARD URL and phone numbers`() {
    val vcard =
      """
      BEGIN:VCARD
      VERSION:3.0
      FN:Sahil Rewani
      ORG:Telesia Shree Balaji Windows
      TEL;TYPE=CELL:+91 73758 58968
      EMAIL:infoshreebalajiwindows@gmail.com
      TITLE:MD
      END:VCARD
      """.trimIndent()

    val parsedVcard = VisitorQrCodec.parseQrContent(vcard)
    assertNotNull(parsedVcard)
    assertEquals("Sahil Rewani", parsedVcard?.name)
    assertEquals("Telesia Shree Balaji Windows", parsedVcard?.company)
    assertEquals("7375858968", parsedVcard?.mobile)
    assertEquals("infoshreebalajiwindows@gmail.com", parsedVcard?.email)
    assertEquals("MD", parsedVcard?.remark)

    val mecard = "MECARD:N:Sharma,Mayank;ORG:SFA India;TEL:7229876108;NOTE:Diwali Gift;;"
    val parsedMecard = VisitorQrCodec.parseQrContent(mecard)
    assertNotNull(parsedMecard)
    assertEquals("Mayank Sharma", parsedMecard?.name)
    assertEquals("SFA India", parsedMecard?.company)
    assertEquals("7229876108", parsedMecard?.mobile)
    assertEquals("Diwali Gift", parsedMecard?.remark)

    val urlQr = "archpoint://checkin?name=Kishan&phone=9928789771&org=BNI&host=Amit&reason=Vendor"
    val parsedUrl = VisitorQrCodec.parseQrContent(urlQr)
    assertNotNull(parsedUrl)
    assertEquals("Kishan", parsedUrl?.name)
    assertEquals("9928789771", parsedUrl?.mobile)
    assertEquals("BNI", parsedUrl?.company)
    assertEquals("Amit", parsedUrl?.hostName)
    assertEquals("Vendor", parsedUrl?.reason)

    val phoneQr = VisitorQrCodec.parseQrContent("+91 98290-63788")
    assertEquals("9829063788", phoneQr?.mobile)

    assertNull(VisitorQrCodec.parseQrContent("   "))
    assertNull(VisitorQrCodec.parseQrContent("Hello world"))
  }

  @Test
  fun `mergeSelfCheckInIntoForm resolves host reason and returning profile`() {
    val hosts =
      listOf(
        StaffMember(
          id = "host_amit",
          fullName = "Ar. Amit Khandelwal",
          shownAs = "Ar. Amit Khandelwal",
          role = StaffRole.HOST.wireValue,
          hostListed = true,
          activeAccount = true,
        ),
        StaffMember(
          id = "host_poonam",
          fullName = "Ar. Poonam Jain",
          shownAs = "Ar. Poonam Jain",
          role = StaffRole.HOST.wireValue,
          hostListed = true,
          activeAccount = true,
        ),
      )
    val reasons =
      listOf(
        VisitReason(id = "r1", label = "Client Meeting", shown = true),
        VisitReason(id = "r2", label = "Vendor", shown = true),
      )
    val profiles =
      listOf(
        VisitorProfile(
          id = "vp1",
          mobile = "917375858968",
          name = "Sahil Rewani",
          company = "Shree Balaji Windows",
          email = "infoshreebalajiwindows@gmail.com",
          dob = "1990-04-15",
          visitCount = 4,
          lastHostStaffId = "host_poonam",
          lastReason = "Vendor",
        )
      )

    val merged =
      MainViewModel.mergeSelfCheckInIntoForm(
        current = ReceptionFormState(selectedReason = "Client Meeting", selectedHostId = "host_amit"),
        payload =
          SelfCheckInPayload(
            name = "Sahil Rewani",
            mobile = "7375858968",
            hostName = "poonam",
            reason = "vendor",
            remark = "Sample delivery",
          ),
        profiles = profiles,
        hosts = hosts,
        reasons = reasons,
      )

    assertEquals("7375858968", merged.mobile)
    assertEquals("Sahil Rewani", merged.name)
    assertEquals("Shree Balaji Windows", merged.company)
    assertEquals("infoshreebalajiwindows@gmail.com", merged.email)
    assertEquals("1990-04-15", merged.dob)
    assertEquals("host_poonam", merged.selectedHostId)
    assertEquals("Vendor", merged.selectedReason)
    assertEquals("Sample delivery", merged.remark)
    assertEquals("vp1", merged.matchedProfile?.id)
  }

  @Test
  fun `selfCheckInWebServer serves HTML form and accepts valid POST submissions`() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val server = SelfCheckInWebServer()
    val latch = CountDownLatch(1)
    var received: SelfCheckInPayload? = null

    try {
      val startResult =
        server.start(
          scope = scope,
          preferredPorts = listOf(0),
          bindAddress = InetAddress.getByName("127.0.0.1"),
          configProvider = {
            SelfCheckInServerConfig(
              officeName = "ArchPoint Test Studio",
              hosts =
                listOf(
                  StaffMember(
                    id = "host_amit",
                    fullName = "Ar. Amit Khandelwal",
                    shownAs = "Ar. Amit Khandelwal",
                    cabin = "Cabin 1",
                    floor = "1st Floor",
                    hostListed = true,
                    activeAccount = true,
                  )
                ),
              reasons = listOf(VisitReason(id = "r1", label = "Vendor", shown = true)),
            )
          },
          onCheckInSubmitted = { payload ->
            received = payload
            latch.countDown()
            Result.success("Checked in cleanly")
          },
        )

      assertTrue(startResult.isSuccess)
      val endpoint = startResult.getOrThrow()

      // Verify GET / renders interactive HTML form containing live studio name & host
      val getConn = URL("${endpoint.localTestUrl}/").openConnection() as HttpURLConnection
      getConn.requestMethod = "GET"
      assertEquals(200, getConn.responseCode)
      val html = getConn.inputStream.bufferedReader().readText()
      assertTrue(html.contains("ArchPoint Test Studio"))
      assertTrue(html.contains("Ar. Amit Khandelwal"))
      assertTrue(html.contains("Vendor"))
      getConn.disconnect()

      // Verify invalid submission returns 400
      val badConn = URL("${endpoint.localTestUrl}/api/checkin").openConnection() as HttpURLConnection
      badConn.requestMethod = "POST"
      badConn.doOutput = true
      badConn.setRequestProperty("Content-Type", "application/json")
      OutputStreamWriter(badConn.outputStream).use { it.write("""{"name":"Only Name","mobile":"123"}""") }
      assertEquals(400, badConn.responseCode)
      badConn.disconnect()

      // Verify valid submission returns 200 and forwards parsed payload
      val postConn = URL("${endpoint.localTestUrl}/api/checkin").openConnection() as HttpURLConnection
      postConn.requestMethod = "POST"
      postConn.doOutput = true
      postConn.setRequestProperty("Content-Type", "application/json")
      OutputStreamWriter(postConn.outputStream).use {
        it.write(
          """{"name":"Kishan","mobile":"9928789771","company":"BNI Construction","hostId":"host_amit","hostName":"Ar. Amit Khandelwal","reason":"Vendor","remark":"Gift"}"""
        )
      }
      assertEquals(200, postConn.responseCode)
      val respBody = postConn.inputStream.bufferedReader().readText()
      assertTrue(respBody.contains("Checked in cleanly"))
      postConn.disconnect()

      assertTrue(latch.await(3, TimeUnit.SECONDS))
      assertEquals("Kishan", received?.name)
      assertEquals("9928789771", received?.mobile)
      assertEquals("host_amit", received?.hostId)
      assertEquals("QR Web Form", received?.source)
    } finally {
      server.stop()
      scope.cancel()
    }
  }
}
