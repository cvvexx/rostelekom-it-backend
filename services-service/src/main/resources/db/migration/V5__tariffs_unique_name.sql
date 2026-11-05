ALTER TABLE tariffs
    DROP CONSTRAINT IF EXISTS uk_tariffs_name_category;

ALTER TABLE tariffs
    ADD CONSTRAINT uk_tariffs_name UNIQUE (name);
