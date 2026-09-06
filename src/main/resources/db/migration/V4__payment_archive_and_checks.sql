ALTER TABLE customer_orders
    ADD COLUMN payment_method varchar(20),
    ADD COLUMN paid_at timestamp with time zone;

UPDATE customer_orders
SET payment_method = 'CHECK',
    paid_at = CURRENT_TIMESTAMP
WHERE status = 'PAID';

ALTER TABLE customer_orders
    ADD CONSTRAINT customer_orders_payment_method_check
        CHECK (payment_method IN ('POS', 'CHECK')),
    ADD CONSTRAINT customer_orders_payment_state_check
        CHECK (
            (status = 'OPEN' AND payment_method IS NULL AND paid_at IS NULL)
            OR
            (status = 'PAID' AND payment_method IS NOT NULL AND paid_at IS NOT NULL)
        );

CREATE INDEX idx_customer_orders_paid_at
    ON customer_orders (paid_at DESC)
    WHERE status = 'PAID';

ALTER TABLE order_items
    ADD COLUMN unit_price numeric(12, 2),
    ADD COLUMN tax_rate numeric(5, 4);

UPDATE order_items oi
SET unit_price = mi.price,
    tax_rate = CASE WHEN mi.category = 'ALCOHOL' THEN 0.1500 ELSE 0.1000 END
FROM menu_items mi
WHERE mi.id = oi.menu_item_id;

ALTER TABLE order_items
    ALTER COLUMN unit_price SET NOT NULL,
    ALTER COLUMN tax_rate SET NOT NULL,
    ADD CONSTRAINT order_items_unit_price_check CHECK (unit_price > 0),
    ADD CONSTRAINT order_items_tax_rate_check CHECK (tax_rate >= 0 AND tax_rate <= 1);
