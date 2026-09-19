# ADR-001: Kafka events between services, with one deliberate exception

**Status:** Accepted · **Date:** 2026-08-22

## Context

Three services have to coordinate on one workflow: an order is placed, stock is
allocated, the order is confirmed, a shipment goes out, and stock is finally
decremented. Each service owns its own database. There is no distributed
transaction available, and I did not want to introduce one.

The realistic options were synchronous REST between services, or events.

Synchronous REST is simpler to write and much simpler to debug. `POST /orders`
would call inventory, get an answer, and return it. One request, one response,
one stack trace when it breaks.

It fails on availability. Order placement would then be unavailable whenever
inventory is unavailable, and the coupling compounds: fulfilment would need
inventory, order would need both. In a distribution business, refusing to take an
order because a downstream service is restarting is worse than taking the order
and allocating a moment later.

It also fails on partial failure. If inventory reserves stock and the response is
lost in transit, the order service does not know whether stock is held. Stock
would sit reserved for an order that does not exist, until something noticed.

## Decision

State changes travel as events over Kafka, in a choreographed saga. Every service
writes its state change and its outgoing event in one local transaction using the
transactional outbox pattern (ADR-003), so the two cannot diverge.

Topics are keyed on `orderId`. All events for one order land on one partition and
are therefore consumed in the order they were produced, which is the ordering
guarantee the saga actually needs. Ordering *across* orders is not needed and
buying it would cost throughput.

**One synchronous call is kept.** Order placement calls
`GET /api/v1/inventory/{sku}/availability` through OpenFeign to show the customer
what is on the shelf as they order. It is a read, it changes nothing, and it is
wrapped in a Resilience4j circuit breaker whose fallback is to omit the preview.
If inventory is down, the customer sees no preview and the order is still
accepted. Making this asynchronous would have meant a request/reply topic and a
correlation store to answer one screen's worth of read — real complexity for no
real gain.

## Consequences

**What this buys.** Any service can be down without stopping order intake.
Events survive a broker outage in the outbox and drain on recovery; there is a
chaos test that proves it. Adding a consumer — analytics, notifications — needs
no change to any producer.

**What it costs.** The system is eventually consistent, and the API has to be
honest about that: placement returns `202 Accepted`, not `201 Created`, because
allocation has not happened yet. Debugging spans three services, which is why
`traceId` is carried in every event envelope and `orderId` is in the MDC.
Every consumer must be idempotent, because delivery is at-least-once.

There is also a class of bug that synchronous calls do not have: two facts
arriving on two topics in either order. The fulfilment service needs both the
allocation and the confirmation before it can build a shipment, and Kafka orders
records within a partition, not across topics. It records both facts and
materialises the shipment when the pair is complete, rather than assuming a
sequence. That is tested in both orders.

## Alternatives considered

**Orchestrated saga with a central coordinator.** Easier to reason about, and the
workflow lives in one readable place. Rejected because the coordinator becomes a
service that must know every other service's business rules, and it is a single
point of failure for a workflow that otherwise has none. Choreography was chosen
knowing it makes the flow harder to see; the ADRs and the README sequence diagram
exist to compensate.

**Keying inventory events on `sku` instead of `orderId`.** This would serialise
allocation per product inside Kafka and remove the need for row locks entirely —
genuinely elegant. Rejected because a popular SKU becomes a hot partition and
caps throughput for that product at one consumer, and because an order spanning
several SKUs would then have no single ordering guarantee.
