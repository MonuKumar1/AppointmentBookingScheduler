# Limitations & Follow-up Risks

## Discovered while building this slice

- **L7: The concurrency test gives confidence, not proof.** 100 repetitions × 16 threads never
  produced a double-booking. A rare interleaving could still go unobserved, so the design is kept
  simple enough to verify by reading: one `synchronized` block per provider. Follow-up: a jcstress
  harness.
- **L8: The CLI cannot send a blank id.** It splits on whitespace, so a missing argument becomes
  `ERROR usage`, not `INVALID`. Blank and null ids are validated by the library (TC-040, TC-045).
  Follow-up: a JSON-lines CLI if blank input must be exercised end to end.
- **L9: Virtual-thread pinning on JDK 21–23.** The build targets Java 21, where a virtual thread
  inside `synchronized` pins its carrier thread. This is harmless for a microsecond, I/O-free block,
  but would matter if the block ever did I/O. Follow-up: `ReentrantLock`, or JDK 24+ (JEP 491).
- **L10: Ordering ties on rebook.** A cancelled appointment and its rebooking share a `start`.
  `listByProvider` breaks the tie by `createdAt`, then by insertion order (stable sort), which is
  correct but not obvious. Follow-up: a sequence number if strict ordering matters.
- **L11: `-00:00` is accepted as UTC.** Spec analysis found the spec and Java disagreed. The decision
  was to accept it because it is a zero offset. RFC 3339 uses `-00:00` to mean "UTC, local offset
  unknown", and such callers get UTC semantics, which is the only time-zone handling in scope.

- **L12: Ids are not normalised.** Found in the 2026-10-23 code review. A provider id with
  surrounding spaces or different case is a different provider, so a caller that pads or re-cases
  ids could double-book a person. Follow-up: trim (and possibly case-fold) ids at the boundary if
  callers are not trusted to send canonical ids.

## Known limits of the in-memory prototype (by design, per the brief)

- **L1: No persistence.** Data is lost on restart. A database version would need a DB-level
  guarantee, e.g. a PostgreSQL exclusion constraint on `(provider_id, tstzrange(start, end, '[)'))`
  for active rows.
- **L2: The lock is JVM-local.** Two service instances could book the same slot. Move the
  guarantee into shared storage, or route each provider to one instance.
- **L3: O(n) overlap scan per provider.** Follow-up: a `TreeMap` keyed by start, checking only the
  neighbouring entries (O(log n)).
- **L4: Unbounded growth.** Cancelled and past appointments are never removed.
- **L5: No authorisation on cancel.** Anyone with an id can cancel; authentication is out of scope.
- **L6: Past bookings are allowed.** There is no "not before now" rule (kept deterministic and
  testable).
