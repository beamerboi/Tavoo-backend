ALTER TABLE customer_orders
    ADD COLUMN coperto_count integer NOT NULL DEFAULT 0,
    ADD COLUMN coperto_unit_price numeric(12, 2) NOT NULL DEFAULT 0.00,
    ADD CONSTRAINT customer_orders_coperto_count_check CHECK (coperto_count >= 0),
    ADD CONSTRAINT customer_orders_coperto_unit_price_check CHECK (coperto_unit_price >= 0);

CREATE TABLE restaurant_settings (
    id bigint PRIMARY KEY,
    coperto_unit_price numeric(12, 2) NOT NULL CHECK (coperto_unit_price >= 0),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT restaurant_settings_singleton_check CHECK (id = 1)
);

INSERT INTO restaurant_settings (id, coperto_unit_price, version)
VALUES (1, 2.50, 0);
