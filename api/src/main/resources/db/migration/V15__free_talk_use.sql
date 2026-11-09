-- P1d task 6: deleting everything must not reset the free plan. After deletion only a one-way key made from the Google
-- account (SHA-256 of "bolo-free:" + Firebase uid; no email, no words, no audio) and the start times of its counted free
-- talks are kept, for 12 months (removed by the retention sweep).
create table free_talk_use (
    subject_hash text        not null,
    started_at   timestamptz not null,
    primary key (subject_hash, started_at)
);
