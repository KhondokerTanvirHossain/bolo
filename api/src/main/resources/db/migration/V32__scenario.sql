-- SCN-1/SCN-2 (2026-11-15): Free talk or a Standup rehearsal (Friendly or Realistic), chosen under the Talk button and
-- copied onto each talk when it starts. A free talk has no difficulty.
alter table learner_settings
    add column scenario text not null default 'free' check (scenario in ('free', 'standup')),
    add column difficulty text not null default 'realistic' check (difficulty in ('friendly', 'realistic'));
alter table talk_session
    add column scenario text not null default 'free' check (scenario in ('free', 'standup')),
    add column difficulty text check (difficulty in ('friendly', 'realistic'));
