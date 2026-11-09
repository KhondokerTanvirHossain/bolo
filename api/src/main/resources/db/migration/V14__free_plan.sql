-- P1d, open sign-up: learners outside the free plan (the smoke/live probes, the founder), and invites that make them.
alter table learner add column unlimited boolean not null default false;
alter table invite add column unlimited boolean not null default false;
update invite set unlimited = true where label = 'smoke and live probes';
