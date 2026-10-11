-- The monthly tailoring allowance moves from configuration onto the row.
--
-- Until now the limit was read from app.quota.* on every check, so there was
-- no way to give one tester a larger allowance short of raising it for
-- everybody and redeploying. Storing it per subscription makes that a single
-- UPDATE:
--
--     UPDATE subscriptions SET monthly_tailorings = 30
--      WHERE user_id = (SELECT id FROM users WHERE email = 'someone@example.com');
--
-- It takes effect on that account's next request, because nothing caches it.
--
-- The DEFAULT exists only to backfill the rows that already exist -- the
-- column is NOT NULL and the entity always sets it explicitly. The two
-- literals below are the shipped defaults for each plan at the time of this
-- migration; they are deliberately not read from configuration, because a
-- migration has to produce the same result every time it runs, on every
-- environment, regardless of what the application was configured with on the
-- day it happened to run.
ALTER TABLE subscriptions
    ADD COLUMN monthly_tailorings INTEGER NOT NULL DEFAULT 3;

UPDATE subscriptions SET monthly_tailorings = 200 WHERE plan = 'PRO';

ALTER TABLE subscriptions
    ADD CONSTRAINT subscriptions_allowance_check CHECK (monthly_tailorings >= 0);
