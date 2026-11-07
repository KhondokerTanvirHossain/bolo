-- "Try it" and practice attempts (US-4.3): what was read, the result and the words to work on. Scores only in v1;
-- practice audio for the accuracy gate comes with the recording kit.
create table attempt (
    id         bigserial   primary key,
    learner_id uuid        not null references learner (id) on delete cascade,
    reference  text        not null,
    level      text        not null check (level in ('NAILED', 'CLOSER', 'ONCE_MORE')),
    heard      boolean     not null,
    accuracy   real        not null,
    focus      jsonb       not null,
    at         timestamptz not null
);
create index attempt_learner on attempt (learner_id, at desc);
