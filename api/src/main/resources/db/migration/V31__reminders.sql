-- Daily reminders (PRG-5/6, spec 2026-11-14). One row per learner once Home showed the card or the learner answered.
create table reminder_settings (
    learner_id     uuid primary key references learner (id) on delete cascade,
    enabled        boolean     not null default false,
    local_time     text        not null default '18:30' check (local_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    time_zone      text        not null default 'Asia/Dhaka',
    keep_private   boolean     not null default false,
    asked_at       timestamptz,          -- answered the reminder step (on, Not now, or saved in Me)
    ignored        int         not null default 0,
    stopped_at     timestamptz,          -- 3 ignored in a row (PRG-6)
    comeback_sent  boolean     not null default false,
    last_sent_on   date,                 -- the local day of the last reminder
    last_sent_at   timestamptz,
    last_sent_id   uuid,
    tapped         boolean     not null default false,
    card_days      int         not null default 0,
    card_last_on   date,
    card_dismissed boolean     not null default false,
    line           text,                 -- the next story line (written after Bolo's notes change)
    line_source    text,                 -- the follow-up it was written from
    updated_at     timestamptz not null
);

create table push_subscription (
    id          bigserial primary key,
    learner_id  uuid        not null references learner (id) on delete cascade,
    endpoint    text        not null unique,
    p256dh      text        not null,
    auth        text        not null,
    user_agent  text,
    created_at  timestamptz not null,
    last_ok_at  timestamptz
);
create index push_subscription_learner on push_subscription (learner_id);
