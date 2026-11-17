alter table outbox add column available_at timestamptz not null default now();

create index outbox_ready_idx on outbox (available_at) where published_at is null;
create index outbox_saga_idx on outbox (saga_id);
