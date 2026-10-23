package com.example.booking;

import static com.example.booking.BookingTest.P1;
import static com.example.booking.BookingTest.at;
import static com.example.booking.BookingTest.newService;
import static com.example.booking.InvariantAssertions.assertNoOverlappingActive;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** User Story 2: cancel an appointment and free its slot. */
class CancellationTest {

    private BookingService service;

    @BeforeEach
    void bookExisting() {
        service = newService();
        assertInstanceOf(BookingResult.Booked.class, service.book(P1, at("09:00"), at("10:00")));
    }

    @AfterEach
    void invariantHolds() {
        assertNoOverlappingActive(service, P1);
    }

    @Test
    void tc030_cancelActiveAppointment() {
        CancellationResult.Cancelled cancelled =
                assertInstanceOf(CancellationResult.Cancelled.class, service.cancel("appt-1"));
        assertEquals(AppointmentStatus.CANCELLED, cancelled.appointment().status());
        assertEquals(AppointmentStatus.CANCELLED, service.findById("appt-1").orElseThrow().status());
    }

    @Test
    void tc031_cancelledSlotCanBeRebooked() {
        service.cancel("appt-1");
        BookingResult.Booked rebooked =
                assertInstanceOf(BookingResult.Booked.class, service.book(P1, at("09:00"), at("10:00")));
        assertEquals("appt-2", rebooked.appointment().id());
    }

    @Test
    void tc032_cancelTwiceIsAlreadyCancelled() {
        service.cancel("appt-1");
        List<Appointment> before = service.listByProvider(P1);

        CancellationResult.AlreadyCancelled result =
                assertInstanceOf(CancellationResult.AlreadyCancelled.class, service.cancel("appt-1"));
        assertEquals("appt-1", result.appointment().id());
        assertEquals(before, service.listByProvider(P1));
    }

    @Test
    void tc033_cancelUnknownIsNotFound() {
        List<Appointment> before = service.listByProvider(P1);

        CancellationResult.NotFound result =
                assertInstanceOf(CancellationResult.NotFound.class, service.cancel("appt-999"));
        assertEquals("appt-999", result.appointmentId());
        assertEquals(before, service.listByProvider(P1));
    }

    @Test
    void tc034_cancelledAppointmentIsStillRetrievable() {
        service.cancel("appt-1");
        assertEquals(AppointmentStatus.CANCELLED, service.findById("appt-1").orElseThrow().status());
        List<Appointment> listed = service.listByProvider(P1);
        assertEquals(1, listed.size());
        assertEquals(AppointmentStatus.CANCELLED, listed.get(0).status());
    }
}
