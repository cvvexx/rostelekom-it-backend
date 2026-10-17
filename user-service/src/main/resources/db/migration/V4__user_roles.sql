-- Ролей у пользователя может быть несколько, поэтому единственная колонка users.role
-- переезжает в отдельную таблицу. Пока роль ровно одна (USER), но схема к этому больше не привязана.
create table user_roles
(
    user_id uuid        not null references users (id) on delete cascade,
    role    varchar(32) not null,
    primary key (user_id, role)
);

-- Переносим то, что уже есть: у всех существующих пользователей роль была ровно одна
insert into user_roles (user_id, role)
select id, role
from users;

alter table users
    drop column role;
