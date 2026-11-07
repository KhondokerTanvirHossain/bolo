-- TLK-14: a talk that met a crisis ends with its own reason (no debrief follows).
alter table talk_session drop constraint talk_session_end_reason_check;
alter table talk_session add constraint talk_session_end_reason_check
    check (end_reason in ('time_up', 'learner_ended', 'disconnected', 'voice_unavailable', 'crisis'));
