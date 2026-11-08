-- The closed beta: a new guest or a new Google account needs an invite link (?invite=CODE). Returning learners never do.
create table invite (
    code       text primary key,
    label      text not null,
    max_uses   integer not null check (max_uses > 0),
    uses       integer not null default 0,
    created_at timestamptz not null
);
