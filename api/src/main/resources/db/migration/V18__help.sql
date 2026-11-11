-- P2b: "Report a problem". The learner's note and a little context (the screen, the last talk, the browser); no audio,
-- no transcript. Goes with the learner on delete everything.
create table help_request (
    id         bigserial   primary key,
    learner_id uuid        not null references learner (id) on delete cascade,
    message    text        not null,
    context    jsonb       not null,
    created_at timestamptz not null,
    done_at    timestamptz
);
create index help_request_open on help_request (created_at desc) where done_at is null;
