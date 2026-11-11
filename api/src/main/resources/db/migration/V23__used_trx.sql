-- Review (P2e): a TrxID is used once, ever, even after "delete everything" removes the payment row. Only the TrxID and
-- when it was used: nothing that points to a person.
create table used_trx (
    trx_id     text        primary key,
    created_at timestamptz not null
);
insert into used_trx (trx_id, created_at) select trx_id, created_at from payment on conflict do nothing;
