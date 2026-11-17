create table orders (
    id               varchar(64)    primary key,
    saga_id          varchar(64)    not null,
    customer_id      varchar(64)    not null,
    product_id       varchar(64)    not null,
    quantity         int            not null,
    amount           numeric(12, 2) not null,
    status           varchar(20)    not null,
    rejection_reason text,
    created_at       timestamptz    not null,
    updated_at       timestamptz    not null
);
