create table users
(
    id         uuid primary key,
    username   varchar(20)  not null unique,
    first_name varchar(100) not null,
    last_name  varchar(100) not null
);