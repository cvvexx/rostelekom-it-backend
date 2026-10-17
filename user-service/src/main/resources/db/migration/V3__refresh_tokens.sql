-- Refresh-токены храним в БД, чтобы их можно было отзывать (logout) и ротировать.
-- В колонке лежит не сам токен, а его SHA-256 хэш: утечка дампа БД не даёт войти за пользователя.
create table refresh_tokens
(
    id         uuid primary key,
    user_id    uuid                     not null references users (id) on delete cascade,
    token_hash varchar(64)              not null unique,
    issued_at  timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone
);

create index idx_refresh_tokens_user_id on refresh_tokens (user_id);
create index idx_refresh_tokens_expires_at on refresh_tokens (expires_at);
