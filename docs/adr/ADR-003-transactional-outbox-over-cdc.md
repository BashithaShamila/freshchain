# ADR-003: Transactional outbox, polled, rather than Debezium CDC

**Status:** Accepted · **Date:** 2026-08-22

## Context

Every service has to do two things when something happens: change its own state,
and tell the other services. Those are two different systems — Postgres and Kafka
— and there is no transaction spanning both.

Writing to the database and then publishing to Kafka has a window in between. A
crash in that window loses the event: the order exists and nobody will ever be
told. Publishing first and then writing is worse, because now an event exists for
a state change that never happened, and consumers have already acted on it.

## Decision

**Transactional outbox, drained by a poller.**

The state change and an `outbox` row are written in the same local transaction.
They commit together or not at all. A scheduled publisher then reads unpublished
rows, sends them to Kafka, and marks them published only after the broker
acknowledges. A crash anywhere leaves the row unpublished and the next poll
re-sends it.

Three implementation choices are worth naming:

- **The claim query uses `FOR UPDATE SKIP LOCKED`.** Several replicas can drain
  the table at the same time without any of them publishing the same row twice.
- **No ShedLock on the publisher.** `SKIP LOCKED` already makes concurrent
  draining safe, and a scheduler lock would remove that parallelism while buying
  nothing. The expiry sweeper is the opposite case — cheap, infrequent, and fine
  on one replica — so it does take one.
- **Sends are issued for the whole batch, then awaited.** The producer batches
  them into roughly one round trip, instead of one per row.

## Consequences

**What this buys.** No lost events and no phantom events, with sub-second lag and
no infrastructure beyond the database that was already there. During a broker
outage the business keeps running and events queue durably; `BrokerOutageIT`
pauses the broker, places five orders, and asserts all five are accepted, all
five events are still pending, and the backlog drains on recovery with no
operator involvement:

```
broker paused; placing orders into the outage
5 event(s) queued in the outbox while the broker was unreachable
broker resumed; waiting for the publisher to drain the backlog
outbox fully drained after broker recovery
```

**What it costs.** Delivery is at-least-once, never exactly-once — the crash
window moved from "between write and publish" to "between publish and mark
published". Every consumer must therefore be idempotent, which is what the
`processed_event` table is for. There is a polling floor of 500 ms on latency,
and a write amplification of one extra row per event. The outbox table needs
periodic pruning, so each publisher deletes published rows older than
`freshchain.outbox.retention` (default three days) on an hourly schedule;
unpublished rows are never pruned, whatever their age.

The idempotency insert uses `ON CONFLICT DO NOTHING` rather than catching a
`DuplicateKeyException`. In Postgres a constraint violation marks the whole
transaction as failed, so "insert and catch" would poison the very transaction
the handler still needs in order to commit.

## Alternatives considered

**Debezium CDC.** Reads the write-ahead log and publishes changes to Kafka with
no polling and no application code at all — lower latency, and it cannot be
bypassed by a developer who forgets to write the outbox row. Rejected for this
project because it needs a Kafka Connect cluster to run, monitor and upgrade,
which is a large operational surface for a system whose events tolerate a
half-second of lag. It also couples the event schema to the table schema unless
an outbox table is used anyway.

**I would switch when** either event latency has to drop below ~100 ms, or the
outbox poller's write load becomes measurable against the primary. At that point
Kafka Connect is already likely to be running for other reasons, and the
operational argument reverses.

**Kafka transactions.** Exactly-once between Kafka and Kafka, but the database is
not a Kafka participant, so the original problem — two systems, one intent —
remains unsolved.
