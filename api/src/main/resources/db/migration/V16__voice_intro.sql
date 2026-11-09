-- PRG-2 / US-6.1: the intro recorded again on Day 7, 14, 21 and every 30 days after. One row per day (recording again
-- replaces it). on_server: the audio is kept on the server (only for learners who chose to keep recordings); otherwise
-- only the date is here and the clip stays in their browser.
create table voice_intro (
    learner_id  uuid        not null references learner (id) on delete cascade,
    day         integer     not null check (day > 1),
    recorded_at timestamptz not null,
    on_server   boolean     not null,
    primary key (learner_id, day)
);
