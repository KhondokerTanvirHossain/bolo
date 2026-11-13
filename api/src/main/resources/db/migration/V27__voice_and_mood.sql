-- P3c (SET-5, SET-6 added 2026-11-13, founder): Settings → Bolo's voice (female or male; British, American or Indian
-- English) and Bolo's mood (friendly, funny, professional, calm). Defaults keep today's Bolo: British, female, friendly.
alter table learner_settings
    add column voice_gender text not null default 'female' check (voice_gender in ('female', 'male')),
    add column voice_accent text not null default 'british' check (voice_accent in ('british', 'american', 'indian')),
    add column mood text not null default 'friendly' check (mood in ('friendly', 'funny', 'professional', 'calm'));
