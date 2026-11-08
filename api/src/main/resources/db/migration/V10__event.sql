-- §10 analytics: allow-listed events with no audio and no transcript text (props are numbers, booleans, short keys).
create table event (
    id         bigserial primary key,
    learner_id uuid not null references learner(id) on delete cascade,
    name       text not null,
    props      jsonb not null default '{}',
    at         timestamptz not null
);
create index event_name_at on event (name, at);
