-- P3a (TLK-8, TLK-16 changed 2026-11-12): Settings → Talk view. "chat" shows the talk as a live chat (the default);
-- "classic" keeps the earlier screen (Bolo's caption only, the time arc).
alter table learner_settings add column talk_view text not null default 'chat' check (talk_view in ('chat', 'classic'));
