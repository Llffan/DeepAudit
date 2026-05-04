-- ============================================================================
-- V2__resize_embeddings_for_gemini.sql
--
-- Switch the embedding provider from DashScope text-embedding-v3 (1024 dim)
-- to Google text-embedding-004 (768 dim). The MVP database has no embeddings
-- stored yet -- description_embedding is NULL across both seed rules and
-- the ICD dictionary has not been bulk-loaded -- so the dimension change is
-- safe without a USING clause.
--
-- pgvector accepts ALTER TYPE on an empty (all-NULL) vector column directly;
-- if a future deployment ever reaches this migration with non-NULL embeddings
-- already populated against the 1024-dim provider, those rows must be
-- truncated or re-embedded BEFORE running this migration.
-- ============================================================================

ALTER TABLE qc_rule  ALTER COLUMN description_embedding TYPE VECTOR(768);
ALTER TABLE icd_dict ALTER COLUMN name_embedding        TYPE VECTOR(768);
