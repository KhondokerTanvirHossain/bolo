-- P2e: bKash Send Money to the founder's number, checked by hand (Option A). A TrxID is used once, ever; one pending
-- payment per learner. Goes with the learner on delete everything.
create table payment (
    id           bigserial   primary key,
    learner_id   uuid        not null references learner (id) on delete cascade,
    plan         text        not null,
    amount_bdt   integer     not null,
    trx_id       text        not null unique,
    payer_number text        not null,
    status       text        not null check (status in ('pending', 'approved', 'rejected')),
    reason       text,
    created_at   timestamptz not null,
    decided_at   timestamptz,
    decided_by   uuid
);
create unique index payment_one_pending on payment (learner_id) where status = 'pending';
create index payment_learner on payment (learner_id, created_at desc);
