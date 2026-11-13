-- AX-7 (2026-11-14): how long the learner was quiet before starting this answer (learner turns only), for the
-- automatic Time to answer.
alter table talk_turn add column start_ms int;
