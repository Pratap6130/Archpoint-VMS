package com.example.data.selfcheckin

import com.example.data.model.StaffMember
import com.example.data.model.VisitReason
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

data class SelfCheckInServerEndpoint(
  val port: Int,
  val lanIp: String?,
) {
  val primaryUrl: String
    get() = if (lanIp != null) "http://$lanIp:$port" else "http://127.0.0.1:$port"

  val localTestUrl: String
    get() = "http://127.0.0.1:$port"

  val isLanAvailable: Boolean
    get() = lanIp != null
}

data class SelfCheckInServerConfig(
  val officeName: String,
  val hosts: List<StaffMember>,
  val reasons: List<VisitReason>,
)

class SelfCheckInWebServer {
  private var serverSocket: ServerSocket? = null
  private var acceptJob: Job? = null

  @Volatile
  private var configProvider: () -> SelfCheckInServerConfig = {
    SelfCheckInServerConfig("ArchPoint Studio", emptyList(), emptyList())
  }

  @Volatile
  private var onCheckInSubmitted: suspend (SelfCheckInPayload) -> Result<String> = {
    Result.success("Checked in at reception")
  }

  val isRunning: Boolean
    get() = serverSocket?.isClosed == false

  fun start(
    scope: CoroutineScope,
    preferredPorts: List<Int> = listOf(8765, 8766, 8767, 8768, 8769, 8770, 0),
    bindAddress: InetAddress? = null,
    configProvider: () -> SelfCheckInServerConfig,
    onCheckInSubmitted: suspend (SelfCheckInPayload) -> Result<String>,
  ): Result<SelfCheckInServerEndpoint> {
    stop()
    this.configProvider = configProvider
    this.onCheckInSubmitted = onCheckInSubmitted

    val socket = openFirstAvailableSocket(preferredPorts, bindAddress)
      ?: return Result.failure(IllegalStateException("Could not open local port for QR Check-In server."))
    serverSocket = socket
    val lanIp = findLocalIpv4Address()

    acceptJob =
      scope.launch(Dispatchers.IO) {
        while (isActive && !socket.isClosed) {
          val client =
            try {
              socket.accept()
            } catch (_: Exception) {
              break
            }
          launch(Dispatchers.IO) {
            handleClient(client)
          }
        }
      }

    return Result.success(SelfCheckInServerEndpoint(port = socket.localPort, lanIp = lanIp))
  }

  fun stop() {
    acceptJob?.cancel()
    acceptJob = null
    runCatching { serverSocket?.close() }
    serverSocket = null
  }

  private fun openFirstAvailableSocket(
    ports: List<Int>,
    bindAddress: InetAddress?,
  ): ServerSocket? {
    for (port in ports) {
      val attempt =
        runCatching {
          if (bindAddress != null) {
            ServerSocket(port, 32, bindAddress)
          } else {
            ServerSocket(port, 32)
          }.apply {
            reuseAddress = true
          }
        }.getOrNull()
      if (attempt != null) return attempt
    }
    return null
  }

  private suspend fun handleClient(client: Socket) {
    client.use { sock ->
      sock.soTimeout = 5000
      val input = BufferedInputStream(sock.getInputStream())
      val headerBytes = ByteArrayOutputStream()
      var prev3 = 0
      var prev2 = 0
      var prev1 = 0
      var totalHeaderBytes = 0
      while (totalHeaderBytes < MAX_HEADER_BYTES) {
        val b = input.read()
        if (b == -1) break
        headerBytes.write(b)
        totalHeaderBytes++
        if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
          break
        }
        prev3 = prev2
        prev2 = prev1
        prev1 = b
      }

      val headerText = headerBytes.toString(StandardCharsets.UTF_8.name())
      val lines = headerText.split("\r\n")
      val requestLine = lines.firstOrNull().orEmpty()
      val parts = requestLine.split(' ')
      if (parts.size < 2) {
        writeHttpResponse(sock, 400, "application/json", """{"ok":false,"error":"Bad request"}""")
        return
      }
      val method = parts[0].uppercase()
      val path = parts[1].substringBefore('?')

      val contentLength =
        lines
          .drop(1)
          .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
          ?.substringAfter(':')
          ?.trim()
          ?.toIntOrNull()
          ?.coerceIn(0, MAX_BODY_BYTES)
          ?: 0

      val bodyBytes =
        if (contentLength > 0) {
          val buf = ByteArray(contentLength)
          var readTotal = 0
          while (readTotal < contentLength) {
            val n = input.read(buf, readTotal, contentLength - readTotal)
            if (n <= 0) break
            readTotal += n
          }
          String(buf, 0, readTotal, StandardCharsets.UTF_8)
        } else {
          ""
        }

      when {
        method == "OPTIONS" -> {
          writeHttpResponse(sock, 204, "text/plain", "")
        }

        method == "GET" && (path == "/" || path == "/checkin") -> {
          val cfg = configProvider()
          val html = buildSelfCheckInHtml(cfg)
          writeHttpResponse(sock, 200, "text/html; charset=utf-8", html)
        }

        method == "GET" && path == "/health" -> {
          writeHttpResponse(sock, 200, "application/json", """{"ok":true}""")
        }

        method == "POST" && path == "/api/checkin" -> {
          val parsed = VisitorQrCodec.parseJsonPayload(bodyBytes, source = "QR Web Form")
          if (parsed == null || parsed.name.isBlank()) {
            writeHttpResponse(
              sock,
              400,
              "application/json",
              """{"ok":false,"error":"Please enter your full name and mobile number."}""",
            )
            return
          }
          if (parsed.mobile.filter { it.isDigit() }.length < 10) {
            writeHttpResponse(
              sock,
              400,
              "application/json",
              """{"ok":false,"error":"Please enter a valid 10-digit mobile number."}""",
            )
            return
          }

          val cfg = configProvider()
          val matchedHost =
            cfg.hosts.firstOrNull { it.id == parsed.hostId }
              ?: cfg.hosts.firstOrNull {
                it.shownAs.equals(parsed.hostName, ignoreCase = true) ||
                  it.fullName.equals(parsed.hostName, ignoreCase = true)
              }
          val normalizedPayload =
            parsed.copy(
              hostId = matchedHost?.id ?: parsed.hostId,
              hostName = matchedHost?.let { it.shownAs.ifBlank { it.fullName } } ?: parsed.hostName,
              source = "QR Web Form",
            )

          val submitResult = onCheckInSubmitted(normalizedPayload)
          submitResult
            .onSuccess { message ->
              val passCode = VisitorQrCodec.encodeArchPointPass(normalizedPayload)
              val cabin = matchedHost?.formattedSeating().orEmpty()
              val jsonResp =
                JSONObject().apply {
                  put("ok", true)
                  put("message", message)
                  put("visitorName", normalizedPayload.name)
                  put("hostName", normalizedPayload.hostName)
                  put("cabin", cabin)
                  put("passCode", passCode)
                }
              writeHttpResponse(sock, 200, "application/json", jsonResp.toString())
            }
            .onFailure { err ->
              val jsonErr =
                JSONObject().apply {
                  put("ok", false)
                  put("error", err.localizedMessage ?: "Failed to save check-in.")
                }
              writeHttpResponse(sock, 500, "application/json", jsonErr.toString())
            }
        }

        else -> {
          writeHttpResponse(sock, 404, "application/json", """{"ok":false,"error":"Not found"}""")
        }
      }
    }
  }

  private fun writeHttpResponse(
    socket: Socket,
    statusCode: Int,
    contentType: String,
    body: String,
  ) {
    val statusText =
      when (statusCode) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        404 -> "Not Found"
        else -> "Internal Server Error"
      }
    val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
    val headers =
      buildString {
        append("HTTP/1.1 $statusCode $statusText\r\n")
        append("Content-Type: $contentType\r\n")
        append("Content-Length: ${bodyBytes.size}\r\n")
        append("Cache-Control: no-store\r\n")
        append("Access-Control-Allow-Origin: *\r\n")
        append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        append("Access-Control-Allow-Headers: Content-Type\r\n")
        append("Connection: close\r\n")
        append("\r\n")
      }.toByteArray(StandardCharsets.US_ASCII)

    val out = socket.getOutputStream()
    out.write(headers)
    if (bodyBytes.isNotEmpty()) {
      out.write(bodyBytes)
    }
    out.flush()
  }

  companion object {
    private const val MAX_HEADER_BYTES = 16 * 1024
    private const val MAX_BODY_BYTES = 32 * 1024

    fun findLocalIpv4Address(): String? =
      runCatching {
        val candidates = mutableListOf<Pair<Int, String>>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (ni in interfaces) {
          if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
          val name = ni.name.lowercase()
          val addresses = ni.inetAddresses
          for (addr in addresses) {
            if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
              val ip = addr.hostAddress ?: continue
              val priority =
                when {
                  name.startsWith("wlan") || name.startsWith("ap") -> 0
                  name.startsWith("eth") -> 1
                  else -> 2
                }
              candidates += priority to ip
            }
          }
        }
        candidates.minByOrNull { it.first }?.second
      }.getOrNull()

    fun buildSelfCheckInHtml(config: SelfCheckInServerConfig): String {
      val officeTitle = escapeHtml(config.officeName.ifBlank { "ArchPoint Studio" })
      val activeHosts = config.hosts.filter { it.activeAccount && it.hostListed }
      val hostOptionsHtml =
        if (activeHosts.isEmpty()) {
          """<option value="">Reception Desk</option>"""
        } else {
          activeHosts.joinToString("\n") { host ->
            val label = host.shownAs.ifBlank { host.fullName }
            val seating = host.formattedSeating().ifBlank { host.cabin }
            val avail = host.parsedAvailability().label
            val display =
              listOfNotNull(
                label,
                host.designation.takeIf { it.isNotBlank() },
                seating.takeIf { it.isNotBlank() },
                avail,
              ).joinToString(" · ")
            """<option value="${escapeHtml(host.id)}" data-name="${escapeHtml(label)}">${escapeHtml(display)}</option>"""
          }
        }

      val activeReasons =
        config.reasons.filter { it.shown }.map { it.label }.ifEmpty {
          listOf("Meeting", "Client", "Vendor", "Site Vendor", "Connection", "Interview Candidate", "Delivery", "Other")
        }
      val reasonOptionsHtml =
        activeReasons.joinToString("\n") { label ->
          """<option value="${escapeHtml(label)}">${escapeHtml(label)}</option>"""
        }

      return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
  <title>$officeTitle · Visitor Self Check-In</title>
  <style>
    :root {
      --bg: #F6F4EF;
      --card: #FFFFFF;
      --ink: #1C232B;
      --muted: #5B6672;
      --accent: #B85D3B;
      --accent-soft: #F7ECE6;
      --line: #E4DFD5;
      --ok: #1E7E4E;
      --ok-bg: #E7F5EE;
      --err: #B3261E;
      --err-bg: #FCECEB;
    }
    * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { margin: 0; padding: 16px; background: var(--bg); color: var(--ink); }
    .wrap { max-width: 520px; margin: 0 auto; }
    .brand {
      background: linear-gradient(135deg, #1E2832 0%, #31414F 100%);
      color: #FFF;
      border-radius: 18px;
      padding: 18px 20px;
      margin-bottom: 14px;
      box-shadow: 0 8px 22px rgba(28,35,43,0.12);
    }
    .brand-tag { font-size: 11px; letter-spacing: 1.4px; text-transform: uppercase; opacity: 0.8; font-weight: 600; }
    .brand h1 { margin: 6px 0 4px; font-size: 22px; font-weight: 700; }
    .brand p { margin: 0; font-size: 13px; opacity: 0.88; }
    .card {
      background: var(--card);
      border: 1px solid var(--line);
      border-radius: 18px;
      padding: 18px;
      box-shadow: 0 4px 16px rgba(28,35,43,0.05);
    }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    @media (max-width: 440px) { .grid { grid-template-columns: 1fr; } }
    .field { margin-bottom: 12px; }
    label { display: block; font-size: 12px; font-weight: 600; color: var(--muted); margin-bottom: 5px; }
    input, select, textarea {
      width: 100%;
      min-height: 46px;
      padding: 10px 12px;
      border: 1.5px solid var(--line);
      border-radius: 12px;
      font-size: 15px;
      color: var(--ink);
      background: #FCFBF8;
      outline: none;
    }
    input:focus, select:focus, textarea:focus { border-color: var(--accent); background: #FFF; }
    textarea { min-height: 70px; resize: vertical; }
    .btn {
      width: 100%;
      min-height: 50px;
      border: 0;
      border-radius: 14px;
      background: var(--accent);
      color: #FFF;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      margin-top: 6px;
    }
    .btn:disabled { opacity: 0.6; cursor: not-allowed; }
    .btn-ghost {
      width: 100%;
      min-height: 46px;
      border: 1.5px solid var(--line);
      border-radius: 12px;
      background: #FFF;
      color: var(--ink);
      font-size: 14px;
      font-weight: 600;
      margin-top: 12px;
      cursor: pointer;
    }
    .alert-err { display: none; background: var(--err-bg); color: var(--err); padding: 10px 12px; border-radius: 10px; font-size: 13px; margin-bottom: 12px; font-weight: 600; }
    .success-box { display: none; text-align: center; padding: 10px 4px; }
    .badge-ok { display: inline-block; background: var(--ok-bg); color: var(--ok); padding: 6px 14px; border-radius: 999px; font-size: 12px; font-weight: 700; margin-bottom: 10px; }
    .info-pill { background: var(--accent-soft); color: #6E311B; padding: 12px; border-radius: 12px; font-size: 14px; font-weight: 600; margin: 12px 0; }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="brand">
      <div class="brand-tag">Self Check-In</div>
      <h1>$officeTitle</h1>
      <p>Fill in your details below — reception and your host are notified automatically.</p>
    </div>

    <div class="card">
      <div id="errBox" class="alert-err"></div>

      <form id="checkInForm">
        <div class="grid">
          <div class="field">
            <label for="name">Full Name *</label>
            <input id="name" name="name" type="text" required placeholder="Your full name" />
          </div>
          <div class="field">
            <label for="mobile">Mobile Number *</label>
            <input id="mobile" name="mobile" type="tel" required placeholder="10-digit number" />
          </div>
        </div>

        <div class="grid">
          <div class="field">
            <label for="company">Company / Organization</label>
            <input id="company" name="company" type="text" placeholder="Optional" />
          </div>
          <div class="field">
            <label for="profile">Designation / Role</label>
            <input id="profile" name="profile" type="text" placeholder="e.g. Architect, Sales" />
          </div>
        </div>

        <div class="grid">
          <div class="field">
            <label for="email">Email (Optional)</label>
            <input id="email" name="email" type="email" placeholder="you@company.com" />
          </div>
          <div class="field">
            <label for="dob">Date of Birth (Optional)</label>
            <input id="dob" name="dob" type="date" />
          </div>
        </div>

        <div class="grid">
          <div class="field">
            <label for="hostId">Person to Meet *</label>
            <select id="hostId" name="hostId">
              $hostOptionsHtml
            </select>
          </div>
          <div class="field">
            <label for="reason">Purpose / Visitor Type *</label>
            <select id="reason" name="reason">
              $reasonOptionsHtml
            </select>
          </div>
        </div>

        <div class="field">
          <label for="remark">Remark / Notes (Optional)</label>
          <textarea id="remark" name="remark" placeholder="Appointment time, project name, parcel, etc."></textarea>
        </div>

        <button id="submitBtn" type="submit" class="btn">Submit Check-In</button>
      </form>

      <div id="successBox" class="success-box">
        <div class="badge-ok">Checked In Successfully</div>
        <h2 id="welcomeTitle" style="margin: 4px 0 6px; font-size: 20px;">Welcome!</h2>
        <p id="welcomeSub" style="margin: 0; font-size: 14px; color: var(--muted);"></p>
        <div id="cabinPill" class="info-pill"></div>
        <button type="button" class="btn-ghost" onclick="resetForNext()">Check In Another Visitor</button>
      </div>
    </div>
  </div>

  <script>
    const form = document.getElementById('checkInForm');
    const errBox = document.getElementById('errBox');
    const successBox = document.getElementById('successBox');
    const submitBtn = document.getElementById('submitBtn');

    function showError(msg) {
      errBox.textContent = msg;
      errBox.style.display = 'block';
    }

    function resetForNext() {
      form.reset();
      errBox.style.display = 'none';
      successBox.style.display = 'none';
      form.style.display = 'block';
    }

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      errBox.style.display = 'none';
      const hostSel = document.getElementById('hostId');
      const selectedOpt = hostSel.options[hostSel.selectedIndex];
      const payload = {
        name: document.getElementById('name').value.trim(),
        mobile: document.getElementById('mobile').value.trim(),
        company: document.getElementById('company').value.trim(),
        profile: document.getElementById('profile').value.trim(),
        email: document.getElementById('email').value.trim(),
        dob: document.getElementById('dob').value.trim(),
        hostId: hostSel.value,
        hostName: selectedOpt ? (selectedOpt.getAttribute('data-name') || selectedOpt.textContent) : '',
        reason: document.getElementById('reason').value,
        remark: document.getElementById('remark').value.trim()
      };

      submitBtn.disabled = true;
      submitBtn.textContent = 'Sending to Reception...';
      try {
        const res = await fetch('/api/checkin', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok || !data.ok) {
          showError(data.error || 'Could not submit check-in. Please check your details.');
        } else {
          form.style.display = 'none';
          document.getElementById('welcomeTitle').textContent = 'Thank you, ' + (data.visitorName || payload.name) + '!';
          document.getElementById('welcomeSub').textContent = data.message || 'Reception and your host have been notified.';
          const cabinText = data.cabin ? (data.hostName + ' · ' + data.cabin) : (data.hostName || 'Reception will guide you shortly');
          document.getElementById('cabinPill').textContent = cabinText;
          successBox.style.display = 'block';
        }
      } catch (err) {
        showError('Unable to reach the Reception desk device. Ensure you are on the office Wi-Fi.');
      } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Submit Check-In';
      }
    });
  </script>
</body>
</html>
""".trimIndent()
    }

    private fun escapeHtml(raw: String): String =
      raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
  }
}
