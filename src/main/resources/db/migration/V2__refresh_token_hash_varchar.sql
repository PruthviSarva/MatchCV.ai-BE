-- token_hash was CHAR(64) in V1, on the reasoning that a hex SHA-256 is always
-- exactly 64 characters. That is true, but PostgreSQL's CHAR is bpchar: it pads
-- to the declared width and carries the padding into comparisons, so it would
-- quietly break the moment the hash encoding changed length. It also disagreed
-- with the entity's `length = 64` String, which `ddl-auto: validate` rejects.
--
-- VARCHAR(64) is what the entity means and costs nothing here. The unique index
-- behind refresh_tokens_hash_unique is rebuilt by the type change.
ALTER TABLE refresh_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);
