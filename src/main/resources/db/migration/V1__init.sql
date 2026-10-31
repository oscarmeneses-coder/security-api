create table app_users (
    id uuid primary key,
    email varchar(320) not null unique,
    password_hash varchar(255) not null,
    role varchar(30) not null,
    enabled boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table user_sessions (
    id uuid primary key,
    user_id uuid not null references app_users(id) on delete cascade,
    refresh_token_hash varchar(64) not null unique,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    last_used_at timestamptz,
    revoked_at timestamptz,
    ip_address varchar(64),
    user_agent varchar(1000)
);

create index idx_user_sessions_user_id on user_sessions(user_id);
create index idx_user_sessions_expires_at on user_sessions(expires_at);
create index idx_user_sessions_refresh_hash on user_sessions(refresh_token_hash);
