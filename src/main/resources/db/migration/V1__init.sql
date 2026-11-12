CREATE TABLE product (
    id          BIGSERIAL PRIMARY KEY,
    catalog_id  VARCHAR(64)    NOT NULL,
    sku         VARCHAR(128)   NOT NULL,
    name        VARCHAR(512)   NOT NULL,
    brand       VARCHAR(256),
    category    VARCHAR(256),
    price       NUMERIC(12, 2) NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    image_url   VARCHAR(2048),
    version     BIGINT         NOT NULL,
    updated_at  TIMESTAMP      NOT NULL,
    CONSTRAINT uk_product_catalog_sku UNIQUE (catalog_id, sku)
);

CREATE TABLE variant (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT         NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    sku         VARCHAR(128)   NOT NULL,
    color       VARCHAR(64),
    size        VARCHAR(64),
    price       NUMERIC(12, 2),
    in_stock    BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_variant_product ON variant (product_id);

CREATE TABLE health_issue (
    id          BIGSERIAL PRIMARY KEY,
    catalog_id  VARCHAR(64)    NOT NULL,
    sku         VARCHAR(128)   NOT NULL,
    rule        VARCHAR(64)    NOT NULL,
    severity    VARCHAR(16)    NOT NULL,
    field       VARCHAR(128),
    message     VARCHAR(1024)  NOT NULL,
    detected_at TIMESTAMP      NOT NULL
);

CREATE INDEX idx_issue_catalog_sku ON health_issue (catalog_id, sku);
