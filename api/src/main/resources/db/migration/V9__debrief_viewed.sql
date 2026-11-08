-- Home (US-6.9): "Finish your feedback" until the learner has seen a ready debrief.
alter table debrief add column viewed_at timestamptz;
