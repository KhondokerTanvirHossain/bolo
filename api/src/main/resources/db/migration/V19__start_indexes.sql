-- Review (P2a): the daily-cap count runs on every talk start (under the start lock) and the admin usage reads by day.
create index talk_session_started on talk_session (started_at);
create index usage_at on usage (at);
