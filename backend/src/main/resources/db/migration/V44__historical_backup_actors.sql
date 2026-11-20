-- Historical authors are retained as disabled identities, never as usable logins.
ALTER TABLE admin_users
    ADD COLUMN historical_username VARCHAR(80) NULL,
    ADD CONSTRAINT chk_historical_actor_inactive CHECK (
        historical_username IS NULL OR (is_active = FALSE AND password_hash = '!HISTORICAL_ACTOR')
    );
