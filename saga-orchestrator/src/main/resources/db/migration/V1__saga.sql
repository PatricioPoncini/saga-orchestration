create table saga_instance (
    id             varchar(64)  primary key,
    type           varchar(100) not null,
    status         varchar(20)  not null,
    data           text         not null,
    step_states    text         not null,
    awaiting       text,
    awaiting_since timestamptz,
    failure_reason text,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null
);

create table saga_timeline (
    id           bigserial    primary key,
    saga_id      varchar(64)  not null,
    occurred_at  timestamptz  not null,
    kind         varchar(50)  not null,
    actor        varchar(50)  not null,
    target       varchar(50),
    message_type varchar(100),
    description  text         not null
);
create index saga_timeline_saga_idx on saga_timeline (saga_id, occurred_at);
