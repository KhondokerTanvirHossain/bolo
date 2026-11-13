-- P3b "Bolo knows you" (docs/superpowers/specs/2026-11-13-bolo-knows-you-design.md; TLK-11 changed 2026-11-13):
-- one short note per learner, updated after each talk. enabled = "Let Bolo remember our talks". talks counts talks that
-- asked for a brief; last_talk_id is the latest of them (a second brief for the same talk keeps its opener).
-- Delete everything removes it through the cascade.
create table learner_memory (
    learner_id   uuid        primary key references learner (id) on delete cascade,
    enabled      boolean     not null default true,
    note         jsonb       not null default '{}',
    talks        int         not null default 0,
    last_talk_id uuid,
    updated_at   timestamptz not null default now()
);
