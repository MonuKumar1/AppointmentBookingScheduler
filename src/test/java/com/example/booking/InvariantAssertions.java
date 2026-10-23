package com.example.booking;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

/**
 * Independent check of the booking invariant: no two ACTIVE appointments of one provider overlap.
 * The half-open comparison is written out here on purpose, so it does not share code with the
 * production overlap check it is verifying.
 */
final class InvariantAssertions {

    private InvariantAssertions() {}

    static void assertNoOverlappingActive(BookingService service, String... providerIds) {
        for (String providerId : providerIds) {
            List<Appointment> active = service.listByProvider(providerId).stream()
                    .filter(a -> a.status() == AppointmentStatus.ACTIVE)
                    .toList();
            for (int i = 0; i < active.size(); i++) {
                for (int j = i + 1; j < active.size(); j++) {
                    Appointment a = active.get(i);
                    Appointment b = active.get(j);
                    if (a.start().isBefore(b.end()) && b.start().isBefore(a.end())) {
                        fail("Invariant violated for " + providerId + ": " + a + " overlaps " + b);
                    }
                }
            }
        }
    }
}
