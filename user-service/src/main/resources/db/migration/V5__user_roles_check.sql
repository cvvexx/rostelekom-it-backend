-- БД про enum Role ничего не знает, поэтому в user_roles.role можно вписать любую строку.
-- Вставка при этом проходит, а падает потом чтение пользователя: Hibernate не может
-- сопоставить значение с константой enum'а и бросает IllegalArgumentException (HTTP 500).
-- Причём ломается любой запрос к этому пользователю, включая вход, потому что коллекция ролей EAGER.
-- Constraint переносит отказ на момент вставки, где его видно сразу.
--
-- ВАЖНО: добавляя новую роль в enum Role, допишите её сюда отдельной миграцией.
-- Тест FlywayMigrationIT.everyRoleFromEnumPassesCheckConstraint падает, если про это забыть.
alter table user_roles
    add constraint ck_user_roles_role check (role in ('USER'));
