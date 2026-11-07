-- Learners start as guests on the first visit (ACC-2) and become accounts on sign-in (ACC-1).
create table learner (
    id         uuid        primary key,
    kind       text        not null check (kind in ('guest', 'account')),
    created_at timestamptz not null
);

-- Append-only: every choice is a new row, the latest one is in force (ACC-3, NFR-16).
create table consent_record (
    id              bigserial   primary key,
    learner_id      uuid        not null references learner (id) on delete cascade,
    keep_recordings boolean     not null,
    over_13         boolean     not null check (over_13),
    policy_version  text        not null,
    recorded_at     timestamptz not null
);
create index consent_record_learner_latest on consent_record (learner_id, id desc);

-- Only the SHA-256 of the session token is stored, never the token.
create table web_session (
    token_hash   bytea       primary key,
    learner_id   uuid        not null references learner (id) on delete cascade,
    created_at   timestamptz not null,
    last_seen_at timestamptz not null
);
create index web_session_learner on web_session (learner_id);
