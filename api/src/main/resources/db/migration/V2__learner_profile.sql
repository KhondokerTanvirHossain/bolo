-- The 3 setup answers (ONB-3, US-1.4). One row per learner, overwritten when edited (Me → My goals).
create table learner_profile (
    learner_id    uuid        primary key references learner (id) on delete cascade,
    goal          text        not null check (goal in ('work_meetings', 'job_interviews', 'study_abroad', 'daily_confidence')),
    feared_moment text        not null check (feared_moment in ('speaking_up', 'interviews', 'phone_calls', 'small_talk')),
    talk_minutes  int         not null check (talk_minutes in (5, 10)),
    updated_at    timestamptz not null
);
