-- SET-1/SET-2: Bolo's speaking speed and captions, per learner. No row means the defaults.
create table learner_settings (
    learner_id  uuid primary key references learner(id) on delete cascade,
    buddy_speed text not null default 'normal' check (buddy_speed in ('normal', 'slower')),
    captions    boolean not null default true,
    updated_at  timestamptz not null
);
