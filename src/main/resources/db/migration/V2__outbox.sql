-- Transactional outbox: rows are written in the same transaction as the catalog change,
-- then relayed to Kafka by OutboxRelay. Kafka never sees a change that was rolled back.
CREATE TABLE outbox_event (
    id            BIGSERIAL PRIMARY KEY,
    event_id      VARCHAR(36)   NOT NULL,
    event_type    VARCHAR(64)   NOT NULL,
    catalog_id    VARCHAR(64)   NOT NULL,
    message_key   VARCHAR(256)  NOT NULL,
    payload       TEXT          NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    published_at  TIMESTAMP,
    attempts      INT           NOT NULL DEFAULT 0,
    last_error    VARCHAR(1024),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX idx_outbox_unpublished ON outbox_event (published_at, id);
