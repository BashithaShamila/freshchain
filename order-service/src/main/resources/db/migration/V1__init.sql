CREATE TABLE orders (
    order_id      UUID PRIMARY KEY,
    customer_id   UUID        NOT NULL,
    warehouse_id  TEXT        NOT NULL,
    status        TEXT        NOT NULL,
    allow_partial BOOLEAN     NOT NULL DEFAULT false,
    reservation_id UUID,
    reservation_expires_at TIMESTAMPTZ,
    -- A cached projection of the inventory service's advice, not order data.
    -- Kept as a document because this service never queries into it.
    substitution_advice JSONB,
    status_reason TEXT,
    placed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer ON orders(customer_id, placed_at DESC);

CREATE TABLE order_line (
    order_line_id UUID PRIMARY KEY,
    order_id      UUID           NOT NULL REFERENCES orders(order_id),
    sku           TEXT           NOT NULL,
    qty_requested INT            NOT NULL CHECK (qty_requested > 0),
    qty_allocated INT            NOT NULL DEFAULT 0 CHECK (qty_allocated >= 0),
    unit_price    NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT chk_allocated_le_requested CHECK (qty_allocated <= qty_requested)
);

CREATE INDEX idx_order_line_order ON order_line(order_id);

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

-- Static price list. A pricing engine is explicitly out of scope; unit prices
-- are per SKU and do not vary by customer, contract or volume.
CREATE TABLE sku_price (
    sku        TEXT PRIMARY KEY,
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0)
);

INSERT INTO sku_price (sku, unit_price) VALUES
  ('CHK-BRST-5LB',  42.50), ('CHK-THGH-5LB',  31.75), ('CHK-TNDR-4LB',  38.90),
  ('TKY-BRST-5LB',  47.20), ('BEF-GRND-10LB', 64.00), ('BEF-SRLN-8LB',  118.40),
  ('SLM-FLLT-4LB',  96.25), ('SHR-1621-5LB',  84.60), ('MLK-WHL-4GAL',  22.80),
  ('CHZ-MOZZ-5LB',  29.95), ('CHZ-CHDR-5LB',  31.40), ('LET-ROM-24CT',  34.10),
  ('LET-ICE-24CT',  26.75), ('TOM-ROMA-25LB', 39.60), ('BRD-BRGR-96CT', 18.25),
  ('BRD-SUB-72CT',  21.50);
