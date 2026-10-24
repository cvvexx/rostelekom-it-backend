alter table user_roles
    drop constraint ck_user_roles_role;

alter table user_roles
    add constraint ck_user_roles_role check (role in ('USER', 'ADMIN'));