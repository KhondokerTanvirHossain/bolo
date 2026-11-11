-- P2c: the plan waitlist (card payments abroad; bKash soon). One row per learner, the latest choice wins; goes with the
-- learner on delete everything.
create table waitlist (
    learner_id uuid        primary key references learner (id) on delete cascade,
    plan       text        not null,
    region     text        not null,
    time_zone  text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index waitlist_updated on waitlist (updated_at desc);
