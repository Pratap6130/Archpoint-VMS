package com.example.data.model

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SeedStaffSpec(
  val id: String,
  val fullName: String,
  val shownAs: String,
  val designation: String,
  val department: String,
  val floor: String,
  val cabin: String,
  val otherLocation: String,
  val role: StaffRole,
  val hostListed: Boolean,
  val notes: String,
  val listOrder: Long,
) {
  fun formattedSeating(): String {
    val parts = mutableListOf<String>()
    if (floor.isNotBlank()) {
      parts += if (floor.contains("floor", ignoreCase = true)) floor else "$floor floor"
    }
    if (cabin.isNotBlank()) {
      parts += cabin
    }
    return parts.joinToString(" · ")
  }
}

data class SeedPastVisitSpec(
  val rowNumber: Int,
  val submissionId: String,
  val submittedAtIso: String,
  val visitorName: String,
  val company: String,
  val phone: String,
  val email: String,
  val dob: String,
  val jobProfile: String,
  val visitorType: String,
  val personToMeet: String,
  val remark: String,
) {
  val docId: String = "visit_past_${rowNumber.toString().padStart(2, '0')}"

  fun resolvedReason(): String {
    val cleanType = visitorType.trim()
    if (cleanType.isNotEmpty() && cleanType != "----") return cleanType
    val lowerRemark = remark.lowercase()
    return when {
      "diwali" in lowerRemark || "gift" in lowerRemark -> "Diwali gift"
      "interview" in lowerRemark -> "Interview Candidate"
      "payment" in lowerRemark -> "Payment"
      else -> "Meeting"
    }
  }

  fun parseSubmittedTimestamp(): Timestamp {
    val fmt =
      SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
      }
    val parsed = runCatching { fmt.parse(submittedAtIso) }.getOrNull()
    val nowMs = System.currentTimeMillis() - 60_000L
    val safeDate =
      if (parsed != null && parsed.time <= nowMs) {
        parsed
      } else {
        Date(nowMs - (45 - rowNumber) * 60_000L)
      }
    return Timestamp(safeDate)
  }
}

object ArchPointSeedData {
  val defaultReasons =
    listOf(
      "Meeting",
      "Vendor",
      "Site Vendor",
      "Connection",
      "Interview Candidate",
      "Diwali gift",
      "EKASA",
      "Payment",
      "Delivery",
      "Other",
    )

  val teamDirectory: List<SeedStaffSpec> =
    listOf(
      SeedStaffSpec(
        id = "staff_archpoint_01",
        fullName = "Ar. Poonam Jain",
        shownAs = "Poonam Ma’am",
        designation = "Founder",
        department = "Arch Point Group",
        floor = "Ground",
        cabin = "Poonam Ma’am cabin",
        otherLocation = "",
        role = StaffRole.HOST,
        hostListed = true,
        notes = "Surname Jain taken from company records; confirm",
        listOrder = 1L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_02",
        fullName = "Ar. Amit Khandelwal",
        shownAs = "Amit Sir",
        designation = "CMD",
        department = "Management",
        floor = "2nd",
        cabin = "Amit cabin",
        otherLocation = "3rd floor studio (name to confirm)",
        role = StaffRole.HOST,
        hostListed = true,
        notes = "Which location is usual? Client/vendor days (Tue and Sat) may use Board room, 3rd",
        listOrder = 2L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_03",
        fullName = "Vasudha Goyal",
        shownAs = "Vasudha Ma’am",
        designation = "Director of Design",
        department = "Design",
        floor = "2nd",
        cabin = "Vasudha cabin",
        otherLocation = "",
        role = StaffRole.HOST,
        hostListed = true,
        notes = "",
        listOrder = 3L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_04",
        fullName = "Neha Sharma",
        shownAs = "Neha Ma’am",
        designation = "COO",
        department = "Operations",
        floor = "2nd",
        cabin = "Neha cabin",
        otherLocation = "",
        role = StaffRole.HOST,
        hostListed = true,
        notes = "",
        listOrder = 4L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_05",
        fullName = "Vartika Maheshwari",
        shownAs = "Vartika Ma’am",
        designation = "HR and interviews",
        department = "HR",
        floor = "2nd",
        cabin = "Vartika cabin",
        otherLocation = "",
        role = StaffRole.HOST,
        hostListed = true,
        notes = "",
        listOrder = 5L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_06",
        fullName = "Ar. Yogendra Kumar",
        shownAs = "Yogendra Ji",
        designation = "Architect",
        department = "Design",
        floor = "2nd",
        cabin = "Yogendra cabin",
        otherLocation = "",
        role = StaffRole.HOST,
        hostListed = true,
        notes = "Floor assumed 2nd; confirm",
        listOrder = 6L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_07",
        fullName = "Yogendra Choudhary",
        shownAs = "Yogendra Choudhary",
        designation = "Senior Civil Engineer",
        department = "Operations",
        floor = "2nd",
        cabin = "Yogendra Choudhary cabin",
        otherLocation = "",
        role = StaffRole.HOST,
        hostListed = true,
        notes = "Floor assumed 2nd; confirm",
        listOrder = 7L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_08",
        fullName = "Satveer Ji",
        shownAs = "Satveer Ji",
        designation = "Managing Officer",
        department = "Operations",
        floor = "2nd",
        cabin = "Satveer cabin",
        otherLocation = "",
        role = StaffRole.HOST,
        hostListed = true,
        notes = "Floor and department assumed; confirm",
        listOrder = 8L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_11",
        fullName = "Himanshi Gupta",
        shownAs = "Himanshi Gupta",
        designation = "Host",
        department = "ArchPoint Wellness",
        floor = "Ground",
        cabin = "ArchPoint Wellness cabin",
        otherLocation = "",
        role = StaffRole.HOST,
        hostListed = true,
        notes = "",
        listOrder = 9L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_09",
        fullName = "Pratap",
        shownAs = "Pratap",
        designation = "Admin (software)",
        department = "Technology",
        floor = "",
        cabin = "",
        otherLocation = "",
        role = StaffRole.ADMIN,
        hostListed = false,
        notes = "Manages staff and settings",
        listOrder = 10L,
      ),
      SeedStaffSpec(
        id = "staff_archpoint_10",
        fullName = "Reception desk (name to fill)",
        shownAs = "Reception",
        designation = "Reception",
        department = "Front office",
        floor = "Ground",
        cabin = "Reception and waiting",
        otherLocation = "",
        role = StaffRole.RECEPTION,
        hostListed = false,
        notes = "",
        listOrder = 11L,
      ),
    )

  fun hostSpecIdForName(personToMeet: String): String {
    val clean = personToMeet.trim().lowercase()
    return when {
      "amit" in clean -> "staff_archpoint_02"
      "poonam" in clean -> "staff_archpoint_01"
      "vasudha" in clean -> "staff_archpoint_03"
      "neha" in clean -> "staff_archpoint_04"
      "vartika" in clean -> "staff_archpoint_05"
      "yogendra kumar" in clean -> "staff_archpoint_06"
      "yogendra choudhary" in clean -> "staff_archpoint_07"
      "satveer" in clean -> "staff_archpoint_08"
      "himanshi" in clean -> "staff_archpoint_11"
      else -> "staff_archpoint_02"
    }
  }

  val pastVisits: List<SeedPastVisitSpec> =
    listOf(
      SeedPastVisitSpec(1, "BEJVORK", "2026-10-31 12:05:03", "Mr. Kishan", "BNI Shri Raghunath Construction", "919928789771", "", "", "Director", "Connection", "Ar. Amit Khandelwal", "Gift"),
      SeedPastVisitSpec(2, "ArMOXxy", "2026-10-31 11:32:23", "Jeet Sethi", "Hfn", "919351046635", "sethijeet09@gmail.com", "1996-09-23", "Cfa", "Connection", "Ar. Amit Khandelwal", "EKASA and meeting"),
      SeedPastVisitSpec(3, "0VqgKdB", "2026-10-31 11:40:16", "Gunjun Saramal", "Hettich", "917073496849", "", "", "BDA", "Site Vendor", "Ar. Amit Khandelwal", ""),
      SeedPastVisitSpec(4, "4alDZ4d", "2026-10-31 11:44:40", "Nayan", "Elite window", "919773320850", "nayan@elitewindow.com", "1997-06-20", "Sales Head", "Vendor", "Ar. Amit Khandelwal", "Diwali gift"),
      SeedPastVisitSpec(5, "Z9bLxae", "2026-10-31 11:45:24", "Mukesh solanki", "Ozone", "918561074541", "", "", "", "Vendor", "Ar. Amit Khandelwal", "For Meeting"),
      SeedPastVisitSpec(6, "jeADBBJ", "2026-11-02 10:11:25", "Devendra Ji", "Devendra", "919829063788", "", "1977-08-02", "", "Vendor", "Neha Sharma", "For 3rd floor"),
      SeedPastVisitSpec(7, "aOqNGGE", "2026-11-02 10:18:46", "Ajaypal Singh Rathor", "Marino", "918078641913", "", "1992-02-12", "Sales team", "Vendor", "Ar. Amit Khandelwal", "Diwali gift"),
      SeedPastVisitSpec(8, "9NjGONE", "2026-11-02 10:21:23", "Sanya", "Navyro", "919610794866", "", "", "", "Connection", "Ar. Amit Khandelwal", "Diwali gift"),
      SeedPastVisitSpec(9, "OQM18lM", "2026-11-02 10:39:27", "Hiteshna Sharma", "Ophelia Homes Pvt Ltd", "917878232760", "hiteshna@opheliahomes.in", "2000-09-22", "Co-founder", "Vendor", "Ar. Amit Khandelwal", ""),
      SeedPastVisitSpec(10, "dbeqzeV", "2026-11-02 10:59:51", "Pradeep Goyal", "Xyz", "919999999999", "", "", "", "Connection", "Ar. Amit Khandelwal", ""),
      SeedPastVisitSpec(11, "yX5ZRr0", "2026-11-02 11:17:32", "Sandeep Ji", "Ms interiors solution", "919828600099", "", "", "", "Vendor", "Ar. Amit Khandelwal", "Greetings"),
      SeedPastVisitSpec(12, "", "2026-11-02 11:30:00", "Bhim Singh", "BJP", "", "", "", "", "Connection", "Ar. Amit Khandelwal", "For 3rd floor"),
      SeedPastVisitSpec(13, "jeADBBJ_2", "2026-11-03 10:00:00", "Devendra Ji", "Devendra", "919829063788", "", "1977-08-02", "", "Vendor", "Neha Sharma", "For 3rd floor"),
      SeedPastVisitSpec(14, "VpOPgby", "2026-11-03 06:32:47", "Bhupesh Kataria", "Senator", "917486059541", "bhupesh.kataria@cera-india.com", "", "Area manager sales", "Vendor", "Ar. Amit Khandelwal", "Direct visit not able to connect to sir, from there china visit, will come at 4 today"),
      SeedPastVisitSpec(15, "PRJYRzd", "2026-11-04 06:29:19", "Rahul", "Adtiya bath", "917372900007", "", "1995-01-01", "", "Vendor", "Ar. Poonam Jain", "Diwali gift"),
      SeedPastVisitSpec(16, "RWyYdBl", "2026-11-04 06:40:24", "Rakesh Ji", "Sidharth nagar site", "917742164989", "", "", "", "Site Vendor", "Ar. Amit Khandelwal", "Payment (site plumber)"),
      SeedPastVisitSpec(17, "OQMYEBA", "2026-11-04 08:05:40", "Sandeep Pilania", "Kitchen kulture", "919950445500", "kitchenkulture.jaipur@gmail.com", "", "CEO & Founder", "Vendor", "Ar. Amit Khandelwal", "Diwali Greetings and gift to amit sir"),
      SeedPastVisitSpec(18, "jeA77ka", "2026-11-04 10:26:57", "Mr. Bheen Singh", "BJP", "919999999999", "", "2026-04-02", "", "Connection", "Ar. Amit Khandelwal", "Meeting"),
      SeedPastVisitSpec(19, "BEWQQre", "2026-11-04 10:28:15", "Mayank sharma", "Sfa india", "917229876108", "", "1995-11-24", "Sales Head", "Vendor", "Ar. Poonam Jain", "Diwali gift"),
      SeedPastVisitSpec(20, "8N4P2zP", "2026-11-04 11:01:27", "Lalit ji", "Sb tecno", "919079758006", "", "1992-09-27", "", "Vendor", "Ar. Amit Khandelwal", "Diwali gift"),
      SeedPastVisitSpec(21, "7XrV5Wa", "2026-11-04 11:08:50", "Abhishek ji", "O general", "918209963451", "", "", "Manager", "Vendor", "Ar. Amit Khandelwal", "Gift nd meeting appointment"),
      SeedPastVisitSpec(22, "2jGJYQj", "2026-11-04 11:24:34", "Manish", "Kolot", "918302899014", "", "", "Sales", "Vendor", "Ar. Amit Khandelwal", "Diwali Greetings"),
      SeedPastVisitSpec(23, "KpKGp6K", "2026-11-05 09:00:56", "Shivam choudhary", "Aluventra system pvt ltd", "917828113687", "", "1997-09-08", "Sales Head", "Vendor", "Ar. Amit Khandelwal", "Vasudha maam and amit sir for diwali gift"),
      SeedPastVisitSpec(24, "vX5zDzv", "2026-11-05 09:17:54", "Aditya from Saleen agrawal", "Sai kria stones", "919929933651", "", "", "", "Vendor", "Ar. Amit Khandelwal", "Gift from shallen agrawal"),
      SeedPastVisitSpec(25, "gb6zGLd", "2026-11-05 09:24:38", "Keshav, Arvind", "Lingal", "918209976116", "", "1995-11-11", "", "Vendor", "Ar. Amit Khandelwal", ""),
      SeedPastVisitSpec(26, "PRJrOvd", "2026-11-05 09:25:11", "Mr. AAYUSH", "Fenesta", "919999999999", "", "", "", "Vendor", "Ar. Amit Khandelwal", "Diwali Greetings and gift"),
      SeedPastVisitSpec(27, "2jGoeZp", "2026-11-05 09:33:39", "Vikash", "Vikas", "918005620772", "", "", "", "Vendor", "Ar. Amit Khandelwal", "Diwali gift"),
      SeedPastVisitSpec(28, "M1pVJ2M", "2026-11-05 09:58:26", "Himanshu +2", "Asianpaint", "917424501432", "", "", "", "Vendor", "Ar. Amit Khandelwal", "For meeting at 3:30, total 3 peoples"),
      SeedPastVisitSpec(29, "Eq76RPX", "2026-11-05 11:10:48", "Lavkesh ju", "Lavkesh", "918107773653", "", "", "", "Connection", "Ar. Amit Khandelwal", "Meet and diwali"),
      SeedPastVisitSpec(30, "", "2026-11-05 11:10:48", "Kushboo Jain", "", "", "", "", "", "", "Ar. Amit Khandelwal", "appointment schedule for 5:30"),
      SeedPastVisitSpec(31, "X5PdW6L", "2026-11-06 05:11:44", "Lavkesh Ji", "Lavkesh Ji", "919999999999", "", "", "", "Connection", "Ar. Amit Khandelwal", "Meeting scheduled with sir"),
      SeedPastVisitSpec(32, "5XPoQBd", "2026-11-06 06:39:36", "Vikram singh", "Galta gat", "919414362460", "", "", "", "", "Yogendra Choudhary", "Meet"),
      SeedPastVisitSpec(33, "M1pllzE", "2026-11-06 06:59:05", "Ajay Ram", "Dainik bhaskar", "918233136787", "", "1994-10-15", "", "Site Vendor", "Ar. Amit Khandelwal", "Meeting with Amit sir"),
      SeedPastVisitSpec(34, "dbeXyWo", "2026-11-06 07:21:10", "Puneet kasana", "Kasana", "919785888838", "", "2026-09-17", "", "Vendor", "Ar. Amit Khandelwal", "Diwali"),
      SeedPastVisitSpec(35, "e5WGEpe", "2026-11-06 08:55:10", "Deelip ji", "Accessory tv", "919999999999", "", "", "", "Vendor", "Ar. Poonam Jain", "Gift"),
      SeedPastVisitSpec(36, "WJV6jMQ", "2026-11-06 09:29:39", "Ankit Jain", "Icici bank", "919999999999", "", "", "", "Other", "Ar. Poonam Jain", "Bank"),
      SeedPastVisitSpec(37, "zE5Aokg", "2026-11-06 09:59:43", "Lalit agrawal", "Nayaab", "919529569602", "", "2026-05-09", "", "Vendor", "Ar. Poonam Jain", "Gift"),
      SeedPastVisitSpec(38, "X5PAJ5d", "2026-11-06 11:14:08", "Harisvar Nursery Mumish bhaiya", "Munish bhaiya", "919999999999", "", "", "", "Connection", "Ar. Amit Khandelwal", "Gift for amit sir and Vasudha maam"),
      SeedPastVisitSpec(39, "X5byYEz", "2026-11-12 07:12:05", "Abhishek ji", "Kausabh bansal", "919999999999", "", "", "", "Connection", "Ar. Amit Khandelwal", "Ekasa flower of his son"),
      SeedPastVisitSpec(40, "OQNyBpK", "2026-11-12 07:25:12", "Sandeep singh", "Ankur shahji", "919352474744", "", "", "Sales", "Vendor", "Ar. Amit Khandelwal", ""),
      SeedPastVisitSpec(41, "BERl8WK", "2026-11-12 10:33:03", "Soumya shrivastva", "Itself pycrashitci", "918619342632", "", "", "Past life regression expert", "EKASA", "Ar. Poonam Jain", ""),
      SeedPastVisitSpec(42, "vX998Qg", "2026-11-13 10:38:41", "Tarun Soni", "Own", "919166667550", "", "", "Owner", "Other", "Ar. Amit Khandelwal", "For his plot construction"),
      SeedPastVisitSpec(43, "kboYAaZ", "2026-11-16 11:08:13", "Harshvardhan singh", "Git uni", "918112280766", "", "2006-09-09", "Full Stack Developer", "Interview Candidate", "Vartika Maheshwari", "Interview"),
    )
}
