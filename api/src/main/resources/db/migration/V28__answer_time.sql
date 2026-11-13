-- AX-7 (changed 2026-11-14, founder) and AX-8: Time to answer (auto, quick, normal, relaxed; auto by default) and
-- I stammer. answer_time_auto is Bolo's pick from the learner's start delays; only Bolo writes it.
alter table learner_settings
    add column answer_time text not null default 'auto' check (answer_time in ('auto', 'quick', 'normal', 'relaxed')),
    add column answer_time_auto text not null default 'normal' check (answer_time_auto in ('quick', 'normal', 'relaxed')),
    add column stammer boolean not null default false;
