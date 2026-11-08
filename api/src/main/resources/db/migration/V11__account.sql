-- ACC-1: a learner becomes an account on Google sign-in (Firebase). One account per Google user.
create table account (
    learner_id      uuid primary key references learner(id) on delete cascade,
    firebase_uid    text not null unique,
    email           text not null,
    name            text,
    created_at      timestamptz not null,
    last_sign_in_at timestamptz not null
);
