-- DeepAudit Postgres init -- Phase 0 only seeds the pgvector extension.
-- Phase 1 (Flyway V1__init_schema.sql) creates the 5 tables.
-- This file is mounted into /docker-entrypoint-initdb.d/ and runs once on
-- first DB boot. Subsequent boots find the extension already created and
-- the IF NOT EXISTS guard makes the statement a no-op.
CREATE EXTENSION IF NOT EXISTS vector;
