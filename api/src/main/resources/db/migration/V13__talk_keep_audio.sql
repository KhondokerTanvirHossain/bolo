-- US-8.5: a talk keeps the recording choice it was made under; a later change applies from the next talk.
alter table talk_session add column keep_audio boolean;
-- Existing talks: the choice in force when the talk started (else the latest; else not kept).
update talk_session s set keep_audio = coalesce(
    (select c.keep_recordings from consent_record c where c.learner_id = s.learner_id and c.recorded_at <= s.started_at
     order by c.id desc limit 1),
    (select c.keep_recordings from consent_record c where c.learner_id = s.learner_id order by c.id desc limit 1),
    false);
