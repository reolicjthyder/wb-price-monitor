CREATE TABLE sku (
    id          BIGSERIAL PRIMARY KEY,
    chat_id     BIGINT      NOT NULL,
    marketplace VARCHAR(10) NOT NULL,
    sku_code    VARCHAR(50) NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT uq_sku_chat_marketplace_code UNIQUE (chat_id, marketplace, sku_code)
);

CREATE TABLE price_snapshot (
    id         BIGSERIAL PRIMARY KEY,
    sku_id     BIGINT        NOT NULL REFERENCES sku (id) ON DELETE CASCADE,
    price      NUMERIC(10,2) NOT NULL,
    quantity   INTEGER       NOT NULL,
    checked_at TIMESTAMP     NOT NULL
);

CREATE INDEX idx_price_snapshot_sku_checked ON price_snapshot (sku_id, checked_at DESC, id DESC);

CREATE TABLE bot_config (
    chat_id             BIGINT        PRIMARY KEY,
    poll_interval_min   INTEGER       NOT NULL DEFAULT 20,
    price_threshold_pct NUMERIC(5,2)  NOT NULL DEFAULT 3.0,
    qty_low_threshold   INTEGER       NOT NULL DEFAULT 5
);
