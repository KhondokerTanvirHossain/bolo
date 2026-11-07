-- A talk with Bolo (TLK-*). ended_at is null while the talk is live; live talks are capped (vendor concurrency).
create table talk_session (
    id         uuid        primary key,
    learner_id uuid        not null references learner (id) on delete cascade,
    minutes    int         not null check (minutes in (5, 10)),
    started_at timestamptz not null,
    ended_at   timestamptz,
    end_reason text        check (end_reason in ('time_up', 'learner_ended', 'disconnected', 'voice_unavailable'))
);
create index talk_session_live on talk_session (started_at) where ended_at is null;
create index talk_session_learner on talk_session (learner_id);

-- One row per spoken turn. server_ms: turn committed → first reply audio sent; heard_ms: the browser's own measure
-- of turn end → Bolo's voice starting (NFR-1).
create table talk_turn (
    id         bigserial   primary key,
    session_id uuid        not null references talk_session (id) on delete cascade,
    idx        int         not null,
    speaker    text        not null check (speaker in ('buddy', 'learner')),
    text       text        not null,
    server_ms  int,
    heard_ms   int,
    at         timestamptz not null,
    unique (session_id, idx)
);

-- Every vendor call (NFR-22): cost per loop is one query over this table.
create table usage (
    id         bigserial   primary key,
    learner_id uuid        not null references learner (id) on delete cascade,
    session_id uuid        references talk_session (id) on delete cascade,
    vendor     text        not null,
    kind       text        not null,
    units      bigint      not null,
    unit_type  text        not null check (unit_type in ('tokens_in', 'tokens_out', 'audio_ms', 'chars')),
    at         timestamptz not null
);
create index usage_session on usage (session_id);
