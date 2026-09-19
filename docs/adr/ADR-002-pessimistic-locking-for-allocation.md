# ADR-002: Pessimistic row locking for lot allocation

**Status:** Accepted · **Date:** 2026-08-22

## Context

Allocation is a read-decide-write cycle: read the available lots for a SKU,
decide which ones to draw from, increment `qty_reserved` on each. Two orders
running that cycle concurrently against the same lot will both read the same
availability and both decide they can have it. That is an oversell, and for
perishable goods it means a truck leaves without stock somebody was promised.

Three options were on the table.

**Optimistic locking.** Every lot already carries a `@Version`. Read without a
lock, and let Hibernate fail the second writer with an
`OptimisticLockException`, then retry.

**Pessimistic locking with `SKIP LOCKED`.** `SELECT ... FOR UPDATE SKIP LOCKED`
so a transaction that finds a lot locked moves straight on to the next one
instead of waiting.

**Pessimistic locking, blocking.** Plain `SELECT ... FOR UPDATE`, ordered by
expiry, so a second transaction waits for the first to commit.

## Decision

**Blocking `SELECT ... FOR UPDATE`, ordered by `expiry_date, received_at, lot_id`.**

`SKIP LOCKED` was the intuitive choice and it is the wrong one here. Under
contention on a single lot, every concurrent caller skips the locked row, finds
no other candidate, and is told there is no stock — while the units are sitting
right there on the shelf. The 50-thread test would report 1 success and 49
spurious rejections instead of 10 and 40. It also quietly breaks FEFO: skipping a
locked near-expiry lot and taking a later-expiring one instead is exactly the
behaviour the whole allocation strategy exists to prevent.

Optimistic locking was rejected because contention here is the normal case, not
the exception. Popular SKUs are contended by design, and optimistic locking
degrades precisely when load is highest — the retry storm arrives exactly when
there is least headroom for it.

Two details make the blocking version correct rather than merely safe:

- **Postgres re-checks the predicate after granting a blocked lock.** When a
  waiter finally acquires the row, the `qty_on_hand > qty_reserved` clause is
  re-evaluated against the committed version. A lot another transaction has just
  exhausted drops out of the result rather than being handed out twice. This is
  what makes "exactly 10 of 50 succeed" fall out of the query itself.
- **The `ORDER BY` fixes a global lock-acquisition order.** Beyond expressing
  FEFO, it means two concurrent multi-line orders touching the same lots always
  take them in the same sequence, so they cannot deadlock by grabbing them in
  opposite orders.

`SKIP LOCKED` is still used — in the outbox publisher and the expiry sweeper.
There, skipping is exactly right: a row another replica has already claimed does
not need this replica's attention.

A database `CHECK (qty_reserved <= qty_on_hand)` sits behind all of it. Application
logic should never let it fire. If it ever does, the write is refused rather than
the oversell being recorded.

## Consequences

**What this buys.** No oversell, and no spurious rejection either. Strict FEFO
holds under concurrency. The proof is
`ConcurrentReservationIT.fiftyConcurrentReservationsOnTenUnitsAllocateExactlyTen`:

```
outcome of 50 concurrent single-unit reservations against 10 units: {FULL=10, REJECTED=40}
lot.qty_reserved                  = 10
sum(reservation_line.qty)         = 10
```

A second test bypasses the entity and the service and writes straight at the
table, to confirm the check constraint is real rather than decorative.

**What it costs.** Allocation for one SKU in one warehouse is serialised. That is
the deliberate trade: correctness of allocation over throughput per SKU. Requests
wait rather than fail, so the pressure shows up as latency, which is why
allocation latency is a first-class panel on the dashboard and a k6 threshold.
Connections are held while blocked, so the Hikari pool has to be sized against
expected concurrency rather than expected throughput.

**When I would revisit this.** If one SKU's allocation latency became the
bottleneck, the fix is not a different lock — it is to remove the contention:
partition inventory by warehouse so each row is contended by fewer callers, or
key inventory events on `sku` and let Kafka serialise per product (ADR-001), which
removes row locks entirely at the cost of hot partitions.
