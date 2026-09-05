-- This service's local projection of what inventory allocated for an order.
-- It exists because two facts arrive on two different topics — the allocation
-- and the confirmation — and Kafka orders records within a partition, not
-- across topics. Either can land first, so both are recorded and the shipment
-- is materialised once both are present.
CREATE TABLE order_allocation (
    order_id        UUID PRIMARY KEY,
    reservation_id  UUID,
    customer_id     UUID,
    warehouse_id    TEXT,
    lines           JSONB,
    allocated       BOOLEAN     NOT NULL DEFAULT false,
    confirmed       BOOLEAN     NOT NULL DEFAULT false,
    shipment_created BOOLEAN    NOT NULL DEFAULT false,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE shipment (
    shipment_id  UUID PRIMARY KEY,
    order_id     UUID        NOT NULL UNIQUE,
    customer_id  UUID,
    warehouse_id TEXT        NOT NULL,
    status       TEXT        NOT NULL,
    tracking_ref TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    picked_at    TIMESTAMPTZ,
    shipped_at   TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_shipment_status ON shipment(status);

CREATE TABLE shipment_line (
    shipment_line_id UUID PRIMARY KEY,
    shipment_id      UUID NOT NULL REFERENCES shipment(shipment_id),
    lot_id           UUID NOT NULL,
    sku              TEXT NOT NULL,
    qty              INT  NOT NULL CHECK (qty > 0)
);

CREATE INDEX idx_shipment_line_shipment ON shipment_line(shipment_id);

CREATE TABLE outbox (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type TEXT        NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     TEXT        NOT NULL,
    topic          TEXT        NOT NULL,
    payload        JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

CREATE TABLE processed_event (
    event_id       UUID        NOT NULL,
    consumer_group TEXT        NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_group)
);
