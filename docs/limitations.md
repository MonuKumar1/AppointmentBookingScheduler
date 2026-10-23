# Limitations & Follow-up Risks

| # | Limitation | Risk if used beyond this exercise | Possible follow-up |
|---|---|---|---|
| L1 | **In-memory only**; all data is lost on restart. | Bookings disappear on any crash or redeploy. | A persistent store. With a database, the invariant needs a DB-level guarantee, e.g. a PostgreSQL exclusion constraint on `(provider_id, tstzrange(start, end, '[)'))` for active rows. |
| L2 | **The lock is JVM-local.** | Two service instances (horizontal scaling) could each book the same slot. | Move the guarantee to shared storage (constraint or row lock), or route each provider to one instance. |
| L3 | **O(n) overlap scan** per provider. | Slower booking for providers with very many appointments. | A `TreeMap` of active appointments keyed by start; check only `lowerEntry` and `ceilingEntry` for O(log n). This is valid because active intervals are disjoint. |
| L4 | **Unbounded growth**: cancelled and past appointments are never removed. | Memory grows forever in a long-running process. | Retention or archiving of cancelled and past appointments. |
| L5 | **No authorisation on cancel.** | Anyone who knows an id can cancel someone else's appointment. | Ownership checks once authentication exists (out of scope here). |
| L6 | **Past bookings are allowed.** | Nonsensical bookings may be accepted. | An injected-clock "not before now" rule, if the business wants it. |
| L7 | **The concurrency test is probabilistic.** 100 repetitions × 16 threads build confidence but do not prove the absence of races. | A rare interleaving could go unobserved. | Keep the design simple enough to verify by reading (one `synchronized` block). Optionally add a jcstress harness. |
| L8 | **The CLI splits on whitespace.** It cannot express blank or space-containing ids, so a missing argument becomes `ERROR usage`, not `INVALID`. | Blank-id validation is visible only through the library API. | It is covered by `ValidationTest` (TC-040, TC-045); a JSON-lines CLI could expose it if needed. |
| L9 | **Virtual-thread pinning on JDK 21–23.** A virtual thread inside `synchronized` pins its carrier thread. | Negligible here (microsecond, I/O-free block), but would matter if the block ever did I/O. | Switch to `ReentrantLock`, or run on JDK 24+ (JEP 491). |
| L10 | **Equal start instants on rebook.** `listByProvider` orders by `start`, then `createdAt`. With a coarse clock, two records could share both values. | Ordering between a cancelled record and its rebooking could look arbitrary. | Ties keep insertion order (stable sort over a `LinkedHashMap`); add a sequence number if strict ordering matters. |
