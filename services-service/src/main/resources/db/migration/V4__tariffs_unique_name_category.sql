ALTER TABLE tariffs
    ADD CONSTRAINT uk_tariffs_name_category UNIQUE (name);
