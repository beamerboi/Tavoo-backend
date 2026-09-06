ALTER TABLE order_items
    DROP CONSTRAINT IF EXISTS order_items_preparation_status_check;

ALTER TABLE order_items
    ADD CONSTRAINT order_items_preparation_status_check
        CHECK (preparation_status IN ('ON_HOLD', 'ORDERED', 'IN_PREPARATION', 'READY', 'SERVED'));

ALTER TABLE order_items
    ADD COLUMN preparation_priority integer;

UPDATE order_items oi
SET preparation_priority = CASE
    WHEN mi.category IN ('BEVERAGE', 'ALCOHOL') THEN NULL
    ELSE CASE mi.course_type
        WHEN 'ANTIPASTO' THEN 1
        WHEN 'PRIMO' THEN 2
        WHEN 'SECONDO' THEN 3
        WHEN 'STEAK' THEN 3
        WHEN 'DESSERT' THEN 4
        ELSE 1
    END
END
FROM menu_items mi
WHERE mi.id = oi.menu_item_id;

UPDATE order_items oi
SET preparation_status = 'READY'
FROM menu_items mi
WHERE mi.id = oi.menu_item_id
  AND mi.category IN ('BEVERAGE', 'ALCOHOL')
  AND oi.preparation_status IN ('ORDERED', 'ON_HOLD');

ALTER TABLE order_items
    ADD CONSTRAINT order_items_preparation_priority_check
        CHECK (preparation_priority IS NULL OR preparation_priority > 0);

CREATE INDEX idx_order_items_order_preparation_priority
    ON order_items (order_id, preparation_priority);
