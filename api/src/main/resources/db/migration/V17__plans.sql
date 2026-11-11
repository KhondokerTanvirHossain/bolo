-- P2a: paid plans as monthly talk counts (ACC-5), given by the admin, by referrals or by bKash payments; and app settings
-- the admin can change without a deploy (the global daily talk cap).
create table plan_setting (
    plan          text    primary key check (plan in ('basic', 'premium', 'ultimate')),
    monthly_talks integer not null check (monthly_talks >= 0),
    price_bdt     integer not null check (price_bdt >= 0),
    price_usd     numeric(8, 2) not null check (price_usd >= 0)
);
insert into plan_setting values ('basic', 8, 300, 3.00), ('premium', 20, 500, 5.00), ('ultimate', 45, 1000, 10.00);

create table subscription (
    id         bigserial   primary key,
    learner_id uuid        not null references learner (id) on delete cascade,
    plan       text        not null references plan_setting (plan),
    starts_at  timestamptz not null,
    ends_at    timestamptz not null check (ends_at > starts_at),
    source     text        not null check (source in ('admin', 'referral', 'bkash')),
    note       text,
    created_at timestamptz not null
);
create index subscription_learner on subscription (learner_id, ends_at desc);

create table app_setting (
    key   text primary key,
    value text not null
);
insert into app_setting values ('daily_talk_cap', '300');
