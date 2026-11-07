-- When the browser's socket attached. A talk nobody attaches to stops holding the live slot after a few seconds,
-- and a talk can be attached once (no second socket multiplying vendor streams).
alter table talk_session add column attached_at timestamptz;
