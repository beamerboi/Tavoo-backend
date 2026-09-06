ALTER TABLE app_users
    DROP CONSTRAINT IF EXISTS app_users_role_check;

UPDATE app_users
SET role = 'WAITER'
WHERE role = 'SERVER';

ALTER TABLE app_users
    ADD CONSTRAINT app_users_role_check
    CHECK (role IN ('ADMIN', 'WAITER', 'KITCHEN'));

ALTER TABLE menu_items
    DROP CONSTRAINT IF EXISTS menu_items_category_check;

ALTER TABLE menu_items
    ADD CONSTRAINT menu_items_category_check
    CHECK (category IN ('FOOD', 'DESSERT', 'BEVERAGE', 'ALCOHOL'));

ALTER TABLE menu_items
    ADD COLUMN course_type varchar(20);

UPDATE menu_items
SET course_type = CASE
    WHEN category = 'ALCOHOL' THEN 'BEVERAGE'
    ELSE 'PRIMO'
END;

ALTER TABLE menu_items
    ALTER COLUMN course_type SET NOT NULL,
    ADD CONSTRAINT menu_items_course_type_check
        CHECK (course_type IN ('ANTIPASTO', 'PRIMO', 'SECONDO', 'STEAK', 'DESSERT', 'BEVERAGE'));

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'customer_orders'
          AND column_name = 'server_id'
    ) THEN
        ALTER TABLE customer_orders
            RENAME COLUMN server_id TO waiter_id;
    END IF;
END
$$;

ALTER TABLE order_items
    ADD COLUMN preparation_status varchar(20) NOT NULL DEFAULT 'ORDERED',
    ADD CONSTRAINT order_items_preparation_status_check
        CHECK (preparation_status IN ('ORDERED', 'IN_PREPARATION', 'READY', 'SERVED'));
