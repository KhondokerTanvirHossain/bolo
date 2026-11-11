-- P2d: invite 3 friends, get a month of Premium. A code per account; a friend counts once, for one referrer; each reward
-- is a numbered row, so it is granted once even if two requests race. All go with the learner on delete everything.
create table referral_code (
    learner_id uuid        primary key references learner (id) on delete cascade,
    code       text        not null unique,
    created_at timestamptz not null
);
create table referral (
    friend_id   uuid        primary key references learner (id) on delete cascade,
    referrer_id uuid        not null references learner (id) on delete cascade,
    joined_at   timestamptz not null
);
create index referral_referrer on referral (referrer_id);
create table referral_reward (
    learner_id uuid        not null references learner (id) on delete cascade,
    n          integer     not null check (n between 1 and 3),
    granted_at timestamptz not null,
    ends_at    timestamptz,
    seen_at    timestamptz,
    primary key (learner_id, n)
);
