-- DBF-8 (US-4.11 changed 2026-11-13: a typed note, not a voice comment): "How was today?" after some talks' feedback.
-- score null = skipped ("Not now" before a face). One row per talk: it never asks twice.
create table talk_rating (
    talk_id    uuid        primary key references talk_session (id) on delete cascade,
    learner_id uuid        not null references learner (id) on delete cascade,
    score      smallint    check (score between 1 and 5),
    note       text        check (char_length(note) <= 300),
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index talk_rating_created on talk_rating (created_at);
create index talk_rating_learner on talk_rating (learner_id);
