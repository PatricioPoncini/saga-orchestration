create table products (
    id        varchar(64)    primary key,
    name      varchar(200)   not null,
    price     numeric(12, 2) not null,
    available int            not null check (available >= 0),
    reserved  int            not null default 0
);

create table reservations (
    order_id   varchar(64) primary key,
    product_id varchar(64) not null,
    quantity   int         not null,
    status     varchar(20) not null,
    reason     text,
    created_at timestamptz not null
);

insert into products (id, name, price, available) values
    ('p-1', 'Teclado mecánico', 120.00, 10),
    ('p-2', 'Monitor 27"',      450.00, 5),
    ('p-3', 'Silla ergonómica', 300.00, 0),
    ('p-4', 'Mouse inalámbrico', 40.00, 50);
