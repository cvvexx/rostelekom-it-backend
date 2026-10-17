-- Переезд с Keycloak (username + пароль) на вход по номеру телефона.
-- Телефон становится основным идентификатором пользователя, остальные поля — необязательными,
-- потому что при входе по SMS мы их на момент создания пользователя просто не знаем.

alter table users
    add column phone varchar(20);

-- У пользователей, заведённых через Keycloak, телефона нет и восстановить его неоткуда.
-- Чтобы не терять строки и при этом включить not null + unique, проставляем заглушку:
-- она гарантированно не совпадёт с реальным номером (те всегда начинаются с '+').
update users
set phone = 'legacy-' || substr(replace(id::text, '-', ''), 1, 13)
where phone is null;

alter table users
    alter column phone set not null;

alter table users
    add constraint uk_users_phone unique (phone);

-- username/first_name/last_name пользователь заполняет уже после входа, через профиль
alter table users
    alter column username drop not null;

alter table users
    alter column first_name drop not null;

alter table users
    alter column last_name drop not null;

alter table users
    add column role varchar(32) not null default 'USER';

alter table users
    add column created_at timestamp with time zone not null default now();

alter table users
    add column updated_at timestamp with time zone not null default now();
