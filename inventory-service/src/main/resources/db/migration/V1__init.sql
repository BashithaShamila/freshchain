CREATE TABLE product (
    sku             TEXT PRIMARY KEY,
    name            TEXT   NOT NULL,
    category        TEXT   NOT NULL,
    description     TEXT   NOT NULL DEFAULT '',
    unit            TEXT   NOT NULL,
    shelf_life_days INT    NOT NULL,
    allergens       TEXT[] NOT NULL DEFAULT '{}'
);

CREATE TABLE inventory_lot (
    lot_id       UUID PRIMARY KEY,
    sku          TEXT        NOT NULL REFERENCES product(sku),
    warehouse_id TEXT        NOT NULL,
    qty_on_hand  INT         NOT NULL CHECK (qty_on_hand  >= 0),
    qty_reserved INT         NOT NULL DEFAULT 0 CHECK (qty_reserved >= 0),
    expiry_date  DATE        NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT      NOT NULL DEFAULT 0,
    -- Last line of defence. Application logic should never let this fire, but if
    -- it does the database refuses the write instead of silently overselling.
    CONSTRAINT chk_reserved_le_onhand CHECK (qty_reserved <= qty_on_hand)
);

-- Partial index: only lots that still have something to give. Column order
-- matches the FEFO query's filter-then-sort shape exactly.
CREATE INDEX idx_lot_fefo
    ON inventory_lot(sku, warehouse_id, expiry_date)
    WHERE qty_on_hand > qty_reserved;

CREATE TABLE reservation (
    reservation_id UUID PRIMARY KEY,
    order_id       UUID        NOT NULL UNIQUE,
    customer_id    UUID,
    warehouse_id   TEXT        NOT NULL,
    status         TEXT        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_reservation_expiry
    ON reservation(expires_at) WHERE status = 'HELD';

CREATE TABLE reservation_line (
    reservation_line_id UUID PRIMARY KEY,
    reservation_id      UUID NOT NULL REFERENCES reservation(reservation_id),
    lot_id              UUID NOT NULL REFERENCES inventory_lot(lot_id),
    sku                 TEXT NOT NULL,
    qty                 INT  NOT NULL CHECK (qty > 0)
);

CREATE INDEX idx_reservation_line_reservation ON reservation_line(reservation_id);

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
