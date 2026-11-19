package com.example

import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.ui.viewmodel.MainViewModel
import com.example.util.DeskFormatters
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentWorkflowTest {
  @Test
  fun filterHostSpecificAppointmentsIsolatesByEmailAndBlankEmailStaffIdFallback() {
    val list =
      listOf(
        Appointment(id = "a1", visitorName = "Rohan", hostStaffId = "s_amit", hostEmail = "AMIT@archpoint.in"),
        Appointment(id = "a2", visitorName = "Kavita", hostStaffId = "s_amit", hostEmail = ""),
        Appointment(id = "a3", visitorName = "Meera", hostStaffId = "s_neha", hostEmail = "neha@archpoint.in"),
        Appointment(id = "a4", visitorName = "Karan", hostStaffId = "s_amit", hostEmail = "other@archpoint.in"),
      )

    val amitAppts =
      MainViewModel.filterHostSpecificAppointments(
        appointments = list,
        userEmail = " amit@archpoint.in ",
        staffId = "s_amit",
      )
    assertEquals(listOf("a1", "a2"), amitAppts.map { it.id })

    val nehaAppts =
      MainViewModel.filterHostSpecificAppointments(
        appointments = list,
        userEmail = "neha@archpoint.in",
        staffId = "s_neha",
      )
    assertEquals(listOf("a3"), nehaAppts.map { it.id })
  }

  @Test
  fun upcomingScheduledAppointmentsFiltersCancelledDonePastAndSortsChronologically() {
    val todayCal = Calendar.getInstance().apply {
      set(2026, Calendar.APRIL, 15, 10, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val nowMillis = todayCal.timeInMillis
    val yesterdayIso = "2026-04-14"
    val todayIso = "2026-04-15"
    val tomorrowIso = "2026-04-16"

    val list =
      listOf(
        Appointment(
          id = "past",
          visitorName = "Past Guest",
          scheduledDateIso = yesterdayIso,
          scheduledTimeLabel = "11:00 AM",
          scheduledAtMillis = DeskFormatters.parseScheduledMillis(yesterdayIso, "11:00 AM"),
          status = AppointmentStatus.SCHEDULED.wireValue,
        ),
        Appointment(
          id = "tomorrow",
          visitorName = "Tomorrow Guest",
          scheduledDateIso = tomorrowIso,
          scheduledTimeLabel = "10:00 AM",
          scheduledAtMillis = DeskFormatters.parseScheduledMillis(tomorrowIso, "10:00 AM"),
          status = AppointmentStatus.SCHEDULED.wireValue,
        ),
        Appointment(
          id = "cancelled",
          visitorName = "Cancelled Guest",
          scheduledDateIso = todayIso,
          scheduledTimeLabel = "12:00 PM",
          scheduledAtMillis = DeskFormatters.parseScheduledMillis(todayIso, "12:00 PM"),
          status = AppointmentStatus.CANCELLED.wireValue,
        ),
        Appointment(
          id = "today_pm",
          visitorName = "Afternoon Guest",
          scheduledDateIso = todayIso,
          scheduledTimeLabel = "04:30 PM",
          scheduledAtMillis = DeskFormatters.parseScheduledMillis(todayIso, "04:30 PM"),
          status = AppointmentStatus.SCHEDULED.wireValue,
        ),
        Appointment(
          id = "checked_in",
          visitorName = "Arrived Guest",
          scheduledDateIso = todayIso,
          scheduledTimeLabel = "11:30 AM",
          scheduledAtMillis = DeskFormatters.parseScheduledMillis(todayIso, "11:30 AM"),
          status = AppointmentStatus.CHECKED_IN.wireValue,
        ),
      )

    val upcoming = MainViewModel.upcomingScheduledAppointments(list, nowMillis)
    assertEquals(listOf("today_pm", "tomorrow"), upcoming.map { it.id })
  }

  @Test
  fun validateAppointmentRequiresVisitorHostDateAndTime() {
    assertNotNull(MainViewModel.validateAppointment(" ", "2026-04-15", "11:30 AM", "s1"))
    assertNotNull(MainViewModel.validateAppointment("Aarav", "2026-04-15", "11:30 AM", " "))
    assertNotNull(MainViewModel.validateAppointment("Aarav", "", "11:30 AM", "s1"))
    assertNotNull(MainViewModel.validateAppointment("Aarav", "2026-04-15", " ", "s1"))
    assertNull(MainViewModel.validateAppointment("Aarav", "2026-04-15", "11:30 AM", "s1"))
  }

  @Test
  fun scheduledMillisAndDayLabelsResolveTodayTomorrowAndOrdering() {
    val baseCal = Calendar.getInstance().apply {
      set(2026, Calendar.MAY, 1, 9, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val now = baseCal.timeInMillis
    val todayIso = DeskFormatters.formatIsoDate(now)
    val tomorrowCal = Calendar.getInstance().apply {
      timeInMillis = now
      add(Calendar.DAY_OF_YEAR, 1)
    }
    val tomorrowIso = DeskFormatters.formatIsoDate(tomorrowCal.timeInMillis)

    assertEquals("Today", DeskFormatters.formatAppointmentDayLabel(todayIso, now))
    assertEquals("Tomorrow", DeskFormatters.formatAppointmentDayLabel(tomorrowIso, now))

    val morningMs = DeskFormatters.parseScheduledMillis(todayIso, "10:00 AM")
    val afternoonMs = DeskFormatters.parseScheduledMillis(todayIso, "03:30 PM")
    assertTrue(afternoonMs > morningMs)
  }
}
