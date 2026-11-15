-- Bolo's hats (2026-11-16): English teacher and IELTS Speaking practice join Free talk and Standup. Talk length:
-- 5 or 10 minutes (10 on Premium and Ultimate); null means the onboarding answer.
alter table learner_settings drop constraint learner_settings_scenario_check;
alter table learner_settings add constraint learner_settings_scenario_check check (scenario in ('free', 'standup', 'teacher', 'ielts'));
alter table talk_session drop constraint talk_session_scenario_check;
alter table talk_session add constraint talk_session_scenario_check check (scenario in ('free', 'standup', 'teacher', 'ielts'));
alter table learner_settings add column talk_minutes integer check (talk_minutes in (5, 10));
