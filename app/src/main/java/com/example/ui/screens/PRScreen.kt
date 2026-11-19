package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PRCampaign
import com.example.data.model.PRCampaignStatus
import com.example.data.model.PRChannel
import com.example.data.model.PRContact
import com.example.data.model.PRContactTier
import com.example.data.model.PRFollowUpStatus
import com.example.data.model.PRGift
import com.example.data.model.PRGiftDirection
import com.example.data.model.PRGiftStatus
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.VisitorProfile
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassInnerSurface
import com.example.ui.components.GlassSegmentedControl
import com.example.ui.components.ImportHostClientToPrSheet
import com.example.ui.components.ImportVisitorToPrSheet
import com.example.ui.components.PRCampaignEditorSheet
import com.example.ui.components.PRContactEditorSheet
import com.example.ui.components.PRGiftEditorSheet
import com.example.ui.components.SendBirthdayWishSheet
import com.example.ui.components.StaffAvatar
import com.example.ui.components.StatusPill
import com.example.ui.theme.ArchAmberBright
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.archPointStatusColors
import com.example.ui.viewmodel.DeskSnapshot
import com.example.ui.viewmodel.HostClientSummary
import com.example.ui.viewmodel.MainViewModel
import com.example.util.DeskFormatters

enum class PRSection(val label: String) {
  CAMPAIGNS("Campaigns"),
  CONTACTS("VIP & Media"),
  GIFTS("Gifts"),
}

private enum class CampaignFilter(val label: String) {
  ALL("All"),
  ACTIVE_LIVE("Active & Live"),
  PLANNED("Planned"),
  COMPLETED("Done"),
}

private enum class ContactFilter(val label: String) {
  ALL("All"),
  BIRTHDAYS("Birthdays & Wishes"),
  VIP("VIP"),
  MEDIA("Media"),
  PARTNER("Partner"),
  DUE("Follow-up Due"),
}

private enum class GiftFilter(val label: String) {
  ALL("All"),
  SENT("Sent"),
  RECEIVED("Received"),
  PENDING("In Progress"),
}

@Composable
fun PRScreen(
  snapshot: DeskSnapshot,
  nowMillis: Long,
  isBusy: Boolean,
  onSaveCampaign: (
    existing: PRCampaign?,
    title: String,
    channel: PRChannel,
    status: PRCampaignStatus,
    targetDateLabel: String,
    location: String,
    audienceSummary: String,
    notes: String,
    ownerStaff: StaffMember?,
  ) -> Unit,
  onAdvanceCampaignStatus: (PRCampaign) -> Unit,
  onDeleteCampaign: (PRCampaign) -> Unit,
  onSaveContact: (
    existing: PRContact?,
    name: String,
    organization: String,
    designation: String,
    tier: PRContactTier,
    mobile: String,
    email: String,
    followUpStatus: PRFollowUpStatus,
    nextFollowUpDate: String,
    photoUrl: String,
    dob: String,
    anniversaryDate: String,
    specialGiftPreference: String,
    cityOrAddress: String,
    notes: String,
    ownerStaff: StaffMember?,
  ) -> Unit,
  onMarkWished: (PRContact) -> Unit,
  onCycleContactFollowUp: (PRContact) -> Unit,
  onDeleteContact: (PRContact) -> Unit,
  onPromoteVisitor: (VisitorProfile, PRContactTier) -> Unit,
  onPromoteHostClient: (HostClientSummary, PRContactTier) -> Unit,
  onSendContactToCheckIn: (PRContact) -> Unit,
  onSaveGift: (
    existing: PRGift?,
    recipientOrSender: String,
    company: String,
    itemTitle: String,
    direction: PRGiftDirection,
    status: PRGiftStatus,
    occasion: String,
    dateLabel: String,
    notes: String,
    ownerStaff: StaffMember?,
  ) -> Unit,
  onAdvanceGiftStatus: (PRGift) -> Unit,
  onDeleteGift: (PRGift) -> Unit,
) {
  val context = LocalContext.current
  val statusColors = archPointStatusColors()
  val isHostOnly = snapshot.activeRole == StaffRole.HOST
  val isAdmin = snapshot.activeRole == StaffRole.ADMIN
  val isReception = snapshot.activeRole == StaffRole.RECEPTION
  val canManageOfficeDirectory = isAdmin || isReception
  val currentYear = remember(nowMillis / 3_600_000L) { DeskFormatters.currentYear(nowMillis) }

  val assignableStaff =
    remember(snapshot.staff) {
      snapshot.staff.filter { it.activeAccount && it.email.isNotBlank() }
    }
  val currentStaffName =
    snapshot.currentStaff?.shownAs?.ifBlank { snapshot.currentStaff.fullName } ?: "Team Member"
  val myClients =
    remember(snapshot.visits, snapshot.currentStaff?.email, snapshot.currentStaff?.id, isHostOnly) {
      if (!isHostOnly) emptyList()
      else {
        MainViewModel.buildHostClients(
          visits = snapshot.visits,
          userEmail = snapshot.currentStaff?.email.orEmpty(),
          staffId = snapshot.currentStaff?.id,
        )
      }
    }

  var selectedSection by remember { mutableStateOf(PRSection.CAMPAIGNS) }
  var searchQuery by remember { mutableStateOf("") }
  var campaignFilter by remember { mutableStateOf(CampaignFilter.ALL) }
  var contactFilter by remember { mutableStateOf(ContactFilter.ALL) }
  var giftFilter by remember { mutableStateOf(GiftFilter.ALL) }

  var showCampaignSheet by remember { mutableStateOf(false) }
  var editingCampaign by remember { mutableStateOf<PRCampaign?>(null) }

  var showContactSheet by remember { mutableStateOf(false) }
  var editingContact by remember { mutableStateOf<PRContact?>(null) }

  var showGiftSheet by remember { mutableStateOf(false) }
  var editingGift by remember { mutableStateOf<PRGift?>(null) }
  var giftPrefillContact by remember { mutableStateOf<PRContact?>(null) }

  var showImportVisitorSheet by remember { mutableStateOf(false) }
  var showImportHostClientSheet by remember { mutableStateOf(false) }
  var wishingContact by remember { mutableStateOf<PRContact?>(null) }

  val metrics =
    remember(snapshot.prCampaigns, snapshot.prContacts, snapshot.prGifts, nowMillis / 3_600_000L) {
      MainViewModel.computePrMetrics(
        campaigns = snapshot.prCampaigns,
        contacts = snapshot.prContacts,
        gifts = snapshot.prGifts,
        nowMillis = nowMillis,
      )
    }

  val upcomingBirthdayContacts =
    remember(snapshot.prContacts, nowMillis / 3_600_000L) {
      snapshot.prContacts
        .mapNotNull { contact ->
          val days = DeskFormatters.daysUntilBirthday(contact.dob, nowMillis)
          if (days != null && days in 0..30) contact to days else null
        }
        .sortedBy { it.second }
    }

  val filteredCampaigns =
    remember(snapshot.prCampaigns, campaignFilter, searchQuery) {
      val q = searchQuery.trim().lowercase()
      snapshot.prCampaigns
        .filter { camp ->
          when (campaignFilter) {
            CampaignFilter.ALL -> true
            CampaignFilter.ACTIVE_LIVE ->
              camp.parsedStatus() == PRCampaignStatus.ACTIVE ||
                camp.parsedStatus() == PRCampaignStatus.LIVE
            CampaignFilter.PLANNED -> camp.parsedStatus() == PRCampaignStatus.PLANNED
            CampaignFilter.COMPLETED -> camp.parsedStatus() == PRCampaignStatus.COMPLETED
          }
        }
        .filter { camp ->
          q.isEmpty() ||
            camp.title.lowercase().contains(q) ||
            camp.channel.lowercase().contains(q) ||
            camp.location.lowercase().contains(q) ||
            camp.audienceSummary.lowercase().contains(q) ||
            camp.ownerName.lowercase().contains(q)
        }
    }

  val filteredContacts =
    remember(snapshot.prContacts, contactFilter, searchQuery, nowMillis / 3_600_000L) {
      val q = searchQuery.trim().lowercase()
      snapshot.prContacts
        .filter { contact ->
          when (contactFilter) {
            ContactFilter.ALL -> true
            ContactFilter.BIRTHDAYS ->
              contact.dob.isNotBlank() || contact.anniversaryDate.isNotBlank() ||
                contact.specialGiftPreference.isNotBlank()
            ContactFilter.VIP -> contact.parsedTier() == PRContactTier.VIP
            ContactFilter.MEDIA -> contact.parsedTier() == PRContactTier.MEDIA
            ContactFilter.PARTNER -> contact.parsedTier() == PRContactTier.PARTNER
            ContactFilter.DUE -> contact.parsedFollowUpStatus() == PRFollowUpStatus.FOLLOW_UP_DUE
          }
        }
        .filter { contact ->
          q.isEmpty() ||
            contact.name.lowercase().contains(q) ||
            contact.organization.lowercase().contains(q) ||
            contact.designation.lowercase().contains(q) ||
            contact.mobile.contains(q) ||
            contact.email.lowercase().contains(q) ||
            contact.specialGiftPreference.lowercase().contains(q) ||
            contact.cityOrAddress.lowercase().contains(q) ||
            contact.ownerName.lowercase().contains(q)
        }
    }

  val filteredGifts =
    remember(snapshot.prGifts, giftFilter, searchQuery) {
      val q = searchQuery.trim().lowercase()
      snapshot.prGifts
        .filter { gift ->
          when (giftFilter) {
            GiftFilter.ALL -> true
            GiftFilter.SENT -> gift.parsedDirection() == PRGiftDirection.SENT
            GiftFilter.RECEIVED -> gift.parsedDirection() == PRGiftDirection.RECEIVED
            GiftFilter.PENDING -> gift.parsedStatus() != PRGiftStatus.ACKNOWLEDGED
          }
        }
        .filter { gift ->
          q.isEmpty() ||
            gift.recipientOrSender.lowercase().contains(q) ||
            gift.company.lowercase().contains(q) ||
            gift.itemTitle.lowercase().contains(q) ||
            gift.occasion.lowercase().contains(q) ||
            gift.ownerName.lowercase().contains(q)
        }
    }

  val openNewForCurrentSection: () -> Unit = {
    when (selectedSection) {
      PRSection.CAMPAIGNS -> {
        editingCampaign = null
        showCampaignSheet = true
      }
      PRSection.CONTACTS -> {
        editingContact = null
        showContactSheet = true
      }
      PRSection.GIFTS -> {
        editingGift = null
        giftPrefillContact = null
        showGiftSheet = true
      }
    }
  }

  if (showCampaignSheet) {
    PRCampaignEditorSheet(
      existing = editingCampaign,
      assignableStaff = assignableStaff,
      isHostOnly = isHostOnly,
      currentStaffName = currentStaffName,
      isBusy = isBusy,
      onDismiss = { showCampaignSheet = false },
      onSave = { title, channel, st, targetDate, location, audience, notes, owner ->
        onSaveCampaign(editingCampaign, title, channel, st, targetDate, location, audience, notes, owner)
      },
    )
  }

  if (showContactSheet) {
    PRContactEditorSheet(
      existing = editingContact,
      assignableStaff = assignableStaff,
      isHostOnly = isHostOnly,
      currentStaffName = currentStaffName,
      isBusy = isBusy,
      onDismiss = { showContactSheet = false },
      onSave = { name, org, desig, tier, mobile, email, followUp, nextDate, photoUrl, dob, anniv, giftPref, city, notes, owner ->
        onSaveContact(
          editingContact,
          name,
          org,
          desig,
          tier,
          mobile,
          email,
          followUp,
          nextDate,
          photoUrl,
          dob,
          anniv,
          giftPref,
          city,
          notes,
          owner,
        )
      },
    )
  }

  if (showGiftSheet) {
    PRGiftEditorSheet(
      existing = editingGift,
      assignableStaff = assignableStaff,
      isHostOnly = isHostOnly,
      currentStaffName = currentStaffName,
      isBusy = isBusy,
      prefillPerson = giftPrefillContact?.name.orEmpty(),
      prefillCompany = giftPrefillContact?.organization.orEmpty(),
      prefillOccasion =
        if (giftPrefillContact != null) "Birthday / Special Gift" else "",
      prefillNotes =
        giftPrefillContact?.let { c ->
          listOfNotNull(
            c.specialGiftPreference.takeIf { it.isNotBlank() }?.let { "Preference: $it" },
            c.cityOrAddress.takeIf { it.isNotBlank() }?.let { "Deliver to: $it" },
          ).joinToString(" · ")
        }.orEmpty(),
      onDismiss = {
        showGiftSheet = false
        giftPrefillContact = null
      },
      onSave = { person, company, item, dir, st, occasion, dateLabel, notes, owner ->
        onSaveGift(editingGift, person, company, item, dir, st, occasion, dateLabel, notes, owner)
      },
    )
  }

  if (showImportVisitorSheet) {
    ImportVisitorToPrSheet(
      profiles = snapshot.visitorProfiles,
      onDismiss = { showImportVisitorSheet = false },
      onSelectVisitor = { profile, tier -> onPromoteVisitor(profile, tier) },
    )
  }

  if (showImportHostClientSheet) {
    ImportHostClientToPrSheet(
      clients = myClients,
      onDismiss = { showImportHostClientSheet = false },
      onSelectClient = { client, tier -> onPromoteHostClient(client, tier) },
    )
  }

  wishingContact?.let { target ->
    SendBirthdayWishSheet(
      contact = target,
      senderName = currentStaffName,
      onDismiss = { wishingContact = null },
      onSendWish = { msg, channel ->
        onMarkWished(target)
        val phone = target.mobile.filter { it.isDigit() || it == '+' }
        when (channel) {
          "whatsapp" -> {
            val waPhone = phone.filter { it.isDigit() }
            val waIntent = Intent(
              Intent.ACTION_VIEW,
              Uri.parse("https://wa.me/$waPhone?text=${Uri.encode(msg)}"),
            )
            runCatching { context.startActivity(waIntent) }.onFailure {
              val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                putExtra("sms_body", msg)
              }
              runCatching { context.startActivity(smsIntent) }
            }
          }
          "email" -> {
            val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${target.email}")).apply {
              putExtra(Intent.EXTRA_SUBJECT, "Warm Birthday Wishes from ArchPoint Studio")
              putExtra(Intent.EXTRA_TEXT, msg)
            }
            runCatching { context.startActivity(mailIntent) }
          }
          "share" -> {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, msg)
            }
            runCatching { context.startActivity(Intent.createChooser(sendIntent, "Send wish")) }
          }
        }
      },
    )
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        GlassCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.Campaign,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "PR & Outreach Hub",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
              }
              Text(
                text =
                  if (isHostOnly) "Private to $currentStaffName · Your campaigns, VIPs & gifts"
                  else "Studio-wide campaigns, press relations & VIP gifting",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            StatusPill(
              text = if (isHostOnly) "My PR" else snapshot.activeRole.label,
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            PrMetricTile(
              label = "Campaigns",
              value = metrics.activeCampaigns.toString(),
              subLabel = "${metrics.liveCampaigns} live",
              accent = statusColors.meetingText,
              container = statusColors.meetingContainer,
              modifier = Modifier.weight(1f),
            )
            PrMetricTile(
              label = "VIP & Media",
              value = metrics.totalContacts.toString(),
              subLabel = if (metrics.dueFollowUps > 0) "${metrics.dueFollowUps} due" else "On track",
              accent = if (metrics.dueFollowUps > 0) statusColors.busyText else statusColors.freeText,
              container = if (metrics.dueFollowUps > 0) statusColors.busyContainer else statusColors.freeContainer,
              modifier = Modifier.weight(1f),
            )
            PrMetricTile(
              label = "Birthdays",
              value = metrics.upcomingBirthdays.toString(),
              subLabel = "next 30d",
              accent = if (metrics.upcomingBirthdays > 0) statusColors.waitingOnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
              container = if (metrics.upcomingBirthdays > 0) statusColors.waitingContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.weight(1f),
            )
            PrMetricTile(
              label = "Gifts Open",
              value = metrics.pendingGifts.toString(),
              subLabel = "${snapshot.prGifts.size} logged",
              accent = ArchAmberBright,
              container = statusColors.waitingContainer,
              modifier = Modifier.weight(1f),
            )
          }

          if (upcomingBirthdayContacts.isNotEmpty()) {
            GlassInnerSurface(
              containerColor = statusColors.waitingContainer.copy(alpha = 0.65f),
              borderColor = ArchAmberBright.copy(alpha = 0.4f),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Rounded.Cake,
                    contentDescription = null,
                    tint = ArchAmberBright,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = "UPCOMING BIRTHDAYS & SPECIAL WISHES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColors.waitingOnContainer,
                  )
                }
                upcomingBirthdayContacts.take(3).forEach { (contact, days) ->
                  val whenLabel = when (days) {
                    0 -> "Today!"
                    1 -> "Tomorrow"
                    else -> "In ${days}d"
                  }
                  val alreadyWished = contact.lastWishedYear >= currentYear
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.weight(1f),
                    ) {
                      StaffAvatar(
                        name = contact.name,
                        photoUrl = contact.photoUrl,
                        size = 28.dp,
                        textStyle = MaterialTheme.typography.labelSmall,
                      )
                      Spacer(Modifier.width(8.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = "${contact.name} · $whenLabel",
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        val pref = contact.specialGiftPreference.takeIf { it.isNotBlank() }?.let { "Gift pref: $it" }
                          ?: contact.organization
                        if (pref.isNotBlank()) {
                          Text(
                            text = pref,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                        }
                      }
                    }
                    TextButton(
                      onClick = { wishingContact = contact },
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                      modifier = Modifier.testTag("pr_radar_wish_${contact.id}"),
                    ) {
                      Text(if (alreadyWished) "Wished ✓" else "Send Wish")
                    }
                  }
                }
              }
            }
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search campaigns, VIPs, gifts...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Rounded.Clear, contentDescription = "Clear PR search")
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).testTag("pr_search_input"),
          )
          Button(
            onClick = openNewForCurrentSection,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            modifier = Modifier.height(54.dp).testTag("pr_add_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add PR record", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add", style = MaterialTheme.typography.labelLarge)
          }
        }
      }

      item {
        GlassSegmentedControl(
          options = PRSection.entries.map { it to it.label },
          selected = selectedSection,
          onSelect = { selectedSection = it },
          tagPrefix = "pr_section",
        )
      }

      when (selectedSection) {
        PRSection.CAMPAIGNS -> {
          item {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CampaignFilter.entries.forEach { filter ->
                FilterChip(
                  selected = campaignFilter == filter,
                  onClick = { campaignFilter = filter },
                  label = { Text(filter.label) },
                )
              }
            }
          }

          if (filteredCampaigns.isEmpty()) {
            item {
              PREmptyCard(
                icon = Icons.Rounded.Campaign,
                title = "No PR campaigns yet",
                tip = "Track studio launches, architectural press features, exhibitions, or festive outreach here.",
                ctaLabel = "Create first campaign",
                onCta = openNewForCurrentSection,
              )
            }
          } else {
            items(filteredCampaigns, key = { it.id }) { campaign ->
              PRCampaignCard(
                campaign = campaign,
                canDelete = isAdmin,
                onAdvanceStatus = { onAdvanceCampaignStatus(campaign) },
                onEdit = {
                  editingCampaign = campaign
                  showCampaignSheet = true
                },
                onDelete = { onDeleteCampaign(campaign) },
              )
            }
          }
        }

        PRSection.CONTACTS -> {
          item {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (canManageOfficeDirectory && snapshot.visitorProfiles.isNotEmpty()) {
                OutlinedButton(
                  onClick = { showImportVisitorSheet = true },
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                  modifier = Modifier.height(34.dp).testTag("pr_promote_visitor_button"),
                ) {
                  Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.width(5.dp))
                  Text("Import Visitor", style = MaterialTheme.typography.labelMedium)
                }
              } else if (isHostOnly && myClients.isNotEmpty()) {
                OutlinedButton(
                  onClick = { showImportHostClientSheet = true },
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                  modifier = Modifier.height(34.dp).testTag("pr_promote_host_client_button"),
                ) {
                  Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.width(5.dp))
                  Text("Import My Client", style = MaterialTheme.typography.labelMedium)
                }
              }
              ContactFilter.entries.forEach { filter ->
                FilterChip(
                  selected = contactFilter == filter,
                  onClick = { contactFilter = filter },
                  label = { Text(filter.label) },
                )
              }
            }
          }

          if (filteredContacts.isEmpty()) {
            item {
              PREmptyCard(
                icon = Icons.Rounded.Stars,
                title = "No VIP or Media contacts",
                tip =
                  if (canManageOfficeDirectory) "Add journalists, patrons, or partners with birthdays & gift preferences — or tap Import Visitor."
                  else "Add your VIP clients, birthdays & gift preferences — or tap Import My Client.",
                ctaLabel = "Add PR contact",
                onCta = openNewForCurrentSection,
              )
            }
          } else {
            items(filteredContacts, key = { it.id }) { contact ->
              PRContactCard(
                contact = contact,
                nowMillis = nowMillis,
                currentYear = currentYear,
                showCheckInAction = isReception,
                canDelete = isAdmin,
                onCall = {
                  val clean = contact.mobile.filter { ch -> ch.isDigit() || ch == '+' }
                  if (clean.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
                  }
                },
                onEmail = {
                  if (contact.email.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${contact.email}")))
                  }
                },
                onSendWish = { wishingContact = contact },
                onSendSpecialGift = {
                  editingGift = null
                  giftPrefillContact = contact
                  showGiftSheet = true
                },
                onCycleFollowUp = { onCycleContactFollowUp(contact) },
                onCheckIn = { onSendContactToCheckIn(contact) },
                onEdit = {
                  editingContact = contact
                  showContactSheet = true
                },
                onDelete = { onDeleteContact(contact) },
              )
            }
          }
        }

        PRSection.GIFTS -> {
          item {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              GiftFilter.entries.forEach { filter ->
                FilterChip(
                  selected = giftFilter == filter,
                  onClick = { giftFilter = filter },
                  label = { Text(filter.label) },
                )
              }
            }
          }

          if (filteredGifts.isEmpty()) {
            item {
              PREmptyCard(
                icon = Icons.Rounded.CardGiftcard,
                title = "No gifts or press kits logged",
                tip = "Log festive hampers, studio monographs, press kits, or hospitality gifts sent and received.",
                ctaLabel = "Log first gift",
                onCta = openNewForCurrentSection,
              )
            }
          } else {
            items(filteredGifts, key = { it.id }) { gift ->
              PRGiftCard(
                gift = gift,
                canDelete = isAdmin,
                onAdvanceStatus = { onAdvanceGiftStatus(gift) },
                onEdit = {
                  editingGift = gift
                  showGiftSheet = true
                },
                onDelete = { onDeleteGift(gift) },
              )
            }
          }
        }
      }

      item { Spacer(Modifier.height(24.dp)) }
    }
  }
}

@Composable
private fun PrMetricTile(
  label: String,
  value: String,
  subLabel: String,
  accent: Color,
  container: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = container,
    border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        color = accent,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PRCampaignCard(
  campaign: PRCampaign,
  canDelete: Boolean,
  onAdvanceStatus: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val statusColors = archPointStatusColors()
  val parsedStatus = campaign.parsedStatus()
  val (pillBg, pillFg) =
    when (parsedStatus) {
      PRCampaignStatus.LIVE -> statusColors.freeContainer to statusColors.freeText
      PRCampaignStatus.ACTIVE -> statusColors.meetingContainer to statusColors.meetingText
      PRCampaignStatus.PLANNED -> statusColors.waitingContainer to statusColors.waitingOnContainer
      PRCampaignStatus.COMPLETED ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

  GlassCard(contentPadding = PaddingValues(14.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          StatusPill(text = parsedStatus.label, containerColor = pillBg, contentColor = pillFg)
          StatusPill(
            text = campaign.parsedChannel().label,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
        Text(
          text = campaign.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      Row {
        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
          Icon(Icons.Rounded.Edit, contentDescription = "Edit campaign", modifier = Modifier.size(18.dp))
        }
        if (canDelete) {
          IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = "Delete campaign",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (campaign.targetDateLabel.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(campaign.targetDateLabel, style = MaterialTheme.typography.bodySmall)
        }
      }
      if (campaign.location.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(campaign.location, style = MaterialTheme.typography.bodySmall)
        }
      }
      if (campaign.ownerName.isNotBlank()) {
        Text(
          text = "Lead: ${campaign.ownerName}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (campaign.audienceSummary.isNotBlank()) {
      Text(
        text = "Audience: ${campaign.audienceSummary}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    if (campaign.notes.isNotBlank()) {
      Text(
        text = campaign.notes,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val nextStatus = MainViewModel.nextPrCampaignStatus(parsedStatus)
      TextButton(onClick = onAdvanceStatus) {
        Text("Move to ${nextStatus.label}")
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
      }
      OutlinedButton(
        onClick = onEdit,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(36.dp),
      ) {
        Text("Details", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PRContactCard(
  contact: PRContact,
  nowMillis: Long,
  currentYear: Int,
  showCheckInAction: Boolean,
  canDelete: Boolean,
  onCall: () -> Unit,
  onEmail: () -> Unit,
  onSendWish: () -> Unit,
  onSendSpecialGift: () -> Unit,
  onCycleFollowUp: () -> Unit,
  onCheckIn: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val statusColors = archPointStatusColors()
  val tier = contact.parsedTier()
  val followUp = contact.parsedFollowUpStatus()
  val daysToBirthday =
    remember(contact.dob, nowMillis / 3_600_000L) {
      DeskFormatters.daysUntilBirthday(contact.dob, nowMillis)
    }
  val alreadyWishedThisYear = contact.lastWishedYear >= currentYear

  val (tierBg, tierFg) =
    when (tier) {
      PRContactTier.VIP -> statusColors.waitingContainer to statusColors.waitingOnContainer
      PRContactTier.MEDIA -> statusColors.meetingContainer to statusColors.meetingText
      PRContactTier.PARTNER -> statusColors.freeContainer to statusColors.freeText
    }

  val (followBg, followFg) =
    when (followUp) {
      PRFollowUpStatus.FOLLOW_UP_DUE -> statusColors.busyContainer to statusColors.busyText
      PRFollowUpStatus.CONNECTED -> statusColors.freeContainer to statusColors.freeText
      PRFollowUpStatus.SCHEDULED -> statusColors.meetingContainer to statusColors.meetingText
      PRFollowUpStatus.WARM -> statusColors.waitingContainer to statusColors.waitingOnContainer
    }

  GlassCard(contentPadding = PaddingValues(14.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
      ) {
        StaffAvatar(
          name = contact.name,
          photoUrl = contact.photoUrl,
          size = 44.dp,
          textStyle = MaterialTheme.typography.titleSmall,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            StatusPill(text = tier.label, containerColor = tierBg, contentColor = tierFg)
            StatusPill(text = followUp.label, containerColor = followBg, contentColor = followFg)
            if (daysToBirthday != null && daysToBirthday <= 30) {
              StatusPill(
                text = when (daysToBirthday) {
                  0 -> "Birthday Today!"
                  1 -> "B'day Tomorrow"
                  else -> "B'day in ${daysToBirthday}d"
                },
                containerColor = statusColors.waitingContainer,
                contentColor = statusColors.waitingOnContainer,
              )
            }
            if (alreadyWishedThisYear) {
              StatusPill(
                text = "Wished $currentYear",
                containerColor = statusColors.freeContainer,
                contentColor = statusColors.freeText,
              )
            }
          }
          Text(
            text = contact.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          val roleLine =
            listOfNotNull(
              contact.designation.takeIf { it.isNotBlank() },
              contact.organization.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
          if (roleLine.isNotBlank()) {
            Text(
              text = roleLine,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Row {
        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
          Icon(Icons.Rounded.Edit, contentDescription = "Edit contact", modifier = Modifier.size(18.dp))
        }
        if (canDelete) {
          IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = "Delete contact",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (contact.dob.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Rounded.Cake,
            contentDescription = null,
            tint = ArchAmberBright,
            modifier = Modifier.size(14.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "B'day: ${contact.dob}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
      if (contact.anniversaryDate.isNotBlank()) {
        Text(
          text = "Anniversary: ${contact.anniversaryDate}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (contact.nextFollowUpDate.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = statusColors.busyText,
            modifier = Modifier.size(14.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "Next: ${contact.nextFollowUpDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
      if (contact.ownerName.isNotBlank()) {
        Text(
          text = "Owner: ${contact.ownerName}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (contact.specialGiftPreference.isNotBlank() || contact.cityOrAddress.isNotBlank()) {
      val giftLine =
        listOfNotNull(
          contact.specialGiftPreference.takeIf { it.isNotBlank() }?.let { "Special gift: $it" },
          contact.cityOrAddress.takeIf { it.isNotBlank() }?.let { "Address: $it" },
        ).joinToString(" · ")
      Text(
        text = giftLine,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }

    if (contact.notes.isNotBlank()) {
      Text(
        text = contact.notes,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (contact.mobile.isNotBlank()) {
        IconButton(onClick = onCall, modifier = Modifier.size(40.dp)) {
          Icon(
            Icons.Rounded.Call,
            contentDescription = "Call ${contact.name}",
            tint = statusColors.freeText,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      if (contact.email.isNotBlank()) {
        IconButton(onClick = onEmail, modifier = Modifier.size(40.dp)) {
          Icon(
            Icons.Rounded.AlternateEmail,
            contentDescription = "Email ${contact.name}",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      OutlinedButton(
        onClick = onSendWish,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp).testTag("pr_contact_wish_${contact.id}"),
      ) {
        Icon(Icons.Rounded.Cake, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
          text = if (alreadyWishedThisYear) "Wish again" else "Send Wish",
          style = MaterialTheme.typography.labelMedium,
        )
      }
      OutlinedButton(
        onClick = onSendSpecialGift,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp).testTag("pr_contact_gift_${contact.id}"),
      ) {
        Icon(Icons.Rounded.CardGiftcard, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Special Gift", style = MaterialTheme.typography.labelMedium)
      }
      val nextFollow = MainViewModel.nextPrFollowUpStatus(followUp)
      TextButton(onClick = onCycleFollowUp) {
        Text("Mark ${nextFollow.label}")
      }
      if (showCheckInAction) {
        OutlinedButton(
          onClick = onCheckIn,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.height(34.dp),
        ) {
          Icon(Icons.Rounded.Badge, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text("Check in", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PRGiftCard(
  gift: PRGift,
  canDelete: Boolean,
  onAdvanceStatus: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val statusColors = archPointStatusColors()
  val direction = gift.parsedDirection()
  val status = gift.parsedStatus()

  val (dirBg, dirFg) =
    if (direction == PRGiftDirection.SENT) statusColors.meetingContainer to statusColors.meetingText
    else statusColors.freeContainer to statusColors.freeText

  val (stBg, stFg) =
    when (status) {
      PRGiftStatus.PREPARED -> statusColors.waitingContainer to statusColors.waitingOnContainer
      PRGiftStatus.DISPATCHED -> statusColors.busyContainer to statusColors.busyText
      PRGiftStatus.DELIVERED -> statusColors.meetingContainer to statusColors.meetingText
      PRGiftStatus.ACKNOWLEDGED -> statusColors.freeContainer to statusColors.freeText
    }

  GlassCard(contentPadding = PaddingValues(14.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          StatusPill(text = direction.label, containerColor = dirBg, contentColor = dirFg)
          StatusPill(text = status.label, containerColor = stBg, contentColor = stFg)
          if (gift.occasion.isNotBlank()) {
            StatusPill(
              text = gift.occasion,
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
        Text(
          text = gift.itemTitle,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        val partyLine =
          listOfNotNull(
            gift.recipientOrSender.takeIf { it.isNotBlank() },
            gift.company.takeIf { it.isNotBlank() },
          ).joinToString(" · ")
        Text(
          text = if (direction == PRGiftDirection.SENT) "To: $partyLine" else "From: $partyLine",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row {
        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
          Icon(Icons.Rounded.Edit, contentDescription = "Edit gift", modifier = Modifier.size(18.dp))
        }
        if (canDelete) {
          IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = "Delete gift",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (gift.dateLabel.isNotBlank()) {
        Text(
          text = "Date: ${gift.dateLabel}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (gift.ownerName.isNotBlank()) {
        Text(
          text = "Coordinator: ${gift.ownerName}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (gift.notes.isNotBlank()) {
      Text(
        text = gift.notes,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val nextStage = MainViewModel.nextPrGiftStatus(status)
      TextButton(onClick = onAdvanceStatus) {
        Text("Mark ${nextStage.label}")
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
      }
      OutlinedButton(
        onClick = onEdit,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(36.dp),
      ) {
        Text("Edit", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun PREmptyCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  tip: String,
  ctaLabel: String,
  onCta: () -> Unit,
) {
  GlassCard {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      GlassInnerSurface(shape = RoundedCornerShape(16.dp)) {
        Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
          )
        }
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = tip,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(
        onClick = onCta,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(48.dp).testTag("pr_empty_cta_button"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(ctaLabel)
      }
    }
  }
}
