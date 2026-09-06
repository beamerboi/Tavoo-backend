DO $$
BEGIN
    ALTER TABLE restaurant_tables
        ADD COLUMN IF NOT EXISTS location_id bigint;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'restaurant_tables'
          AND column_name = 'location'
    ) THEN
        INSERT INTO locations (name, type)
        VALUES
            ('Main Hall', 'INSIDE'),
            ('Terrace', 'OUTSIDE')
        ON CONFLICT (name) DO NOTHING;

        UPDATE restaurant_tables AS restaurant_table
        SET location_id = location.id
        FROM locations AS location
        WHERE (restaurant_table.location = 'INSIDE' AND location.name = 'Main Hall')
           OR (restaurant_table.location = 'OUTSIDE' AND location.name = 'Terrace');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM restaurant_tables
        WHERE location_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot migrate restaurant_tables: one or more rows have no matching location';
    END IF;

    ALTER TABLE restaurant_tables
        ALTER COLUMN location_id SET NOT NULL;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint AS constraint_definition
        JOIN pg_class AS table_definition
          ON table_definition.oid = constraint_definition.conrelid
        JOIN pg_namespace AS schema_definition
          ON schema_definition.oid = table_definition.relnamespace
        JOIN pg_attribute AS column_definition
          ON column_definition.attrelid = table_definition.oid
         AND column_definition.attnum = ANY (constraint_definition.conkey)
        WHERE constraint_definition.contype = 'f'
          AND schema_definition.nspname = current_schema()
          AND table_definition.relname = 'restaurant_tables'
          AND column_definition.attname = 'location_id'
    ) THEN
        ALTER TABLE restaurant_tables
            ADD CONSTRAINT fk_restaurant_tables_location
            FOREIGN KEY (location_id) REFERENCES locations(id);
    END IF;

    ALTER TABLE restaurant_tables
        DROP COLUMN IF EXISTS location;
END
$$;
