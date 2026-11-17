create table outbox (
    id           bigserial primary key,
    message_id   varchar(64)  not null,
    saga_id      varchar(64)  not null,
    exchange     varchar(100) not null,
    routing_key  varchar(100) not null,
    type         varchar(100) not null,
    body         text         not null,
    created_at   timestamptz  not null,
    published_at timestamptz
);
create index outbox_unpublished_idx on outbox (id) where published_at is null;

create table processed_messages (
    message_id   varchar(64)  primary key,
    message_type varchar(100) not null,
    processed_at timestamptz  not null
);
