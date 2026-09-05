-- The outbox deliberately breaks the automatic trace chain: the publish happens
-- in a scheduled job, long after the request that caused it, and on a different
-- thread. Carrying the W3C traceparent of the originating request lets the
-- publisher re-attach it as a Kafka header, so a consumer continues the caller's
-- trace instead of starting an unrelated one.
ALTER TABLE outbox ADD COLUMN traceparent TEXT;
