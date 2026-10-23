package com.example.booking;

import static com.example.booking.InvariantAssertions.assertNoOverlappingActive;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * User Story 4: concurrent calls never break the booking invariant. All threads wait on one start
 * gate so they hit the check-and-insert window at the same moment.
 */
class ConcurrencyTest {

    private static final int THREADS = 16;
    private static final String SLOT_START = "2026-11-02T09:00:00Z";
    private static final String SLOT_END = "2026-11-02T10:00:00Z";

    @RepeatedTest(100)
    void tc050_sameSlotRaceHasExactlyOneWinner() throws Exception {
        BookingService service = new BookingService();
        List<BookingResult> results = race(i -> () -> service.book("provider-1", SLOT_START, SLOT_END));

        assertEquals(1, count(results, BookingResult.Booked.class));
        assertEquals(THREADS - 1, count(results, BookingResult.Conflict.class));
        assertNoOverlappingActive(service, "provider-1");
    }

    @RepeatedTest(100)
    void tc051_concurrentCancelsHaveExactlyOneWinner() throws Exception {
        BookingService service = new BookingService();
        BookingResult.Booked booked = (BookingResult.Booked) service.book("provider-1", SLOT_START, SLOT_END);
        String id = booked.appointment().id();

        List<CancellationResult> results = race(i -> () -> service.cancel(id));

        assertEquals(1, count(results, CancellationResult.Cancelled.class));
        assertEquals(THREADS - 1, count(results, CancellationResult.AlreadyCancelled.class));
    }

    @Test
    void tc052_differentProvidersNeverBlockEachOther() throws Exception {
        BookingService service = new BookingService();
        List<BookingResult> results =
                race(i -> () -> service.book("provider-" + (i + 1), SLOT_START, SLOT_END));

        assertEquals(THREADS, count(results, BookingResult.Booked.class));
    }

    @Test
    void tc053_nonOverlappingSlotsForOneProviderAllSucceed() throws Exception {
        BookingService service = new BookingService();
        List<BookingResult> results = race(i -> () -> service.book("provider-1",
                String.format("2026-11-02T%02d:00:00Z", i), String.format("2026-11-02T%02d:00:00Z", i + 1)));

        assertEquals(THREADS, count(results, BookingResult.Booked.class));
        assertNoOverlappingActive(service, "provider-1");
    }

    /** Runs THREADS tasks released together by a start gate and returns their results. */
    static <T> List<T> race(IntFunction<Callable<T>> taskFor) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                Callable<T> task = taskFor.apply(i);
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    static long count(List<?> results, Class<?> type) {
        return results.stream().filter(type::isInstance).count();
    }
}
