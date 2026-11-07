-- Coach v1. Each learner turn keeps its audio (encrypted file, key in audio_key), where it starts in the recogniser's
-- stream (word times are stream times) and its word-level pronunciation scores.
alter table talk_turn add column audio_key text, add column stream_start_ms bigint, add column scored jsonb;

-- When the talk's audio was deleted by the retention sweep (NFR-14).
alter table talk_session add column media_purged_at timestamptz;

-- The Coach's debrief of one talk.
create table debrief (
    session_id uuid        primary key references talk_session (id) on delete cascade,
    status     text        not null check (status in ('scoring', 'ready', 'too_short', 'failed')),
    body       jsonb,
    attempts   int         not null default 0,
    updated_at timestamptz not null
);

-- "That's not what I said" (DBF-4), kept for quality review.
create table fix_dispute (
    id         bigserial   primary key,
    session_id uuid        not null references talk_session (id) on delete cascade,
    fix_index  int         not null,
    kind       text        not null,
    at         timestamptz not null
);
