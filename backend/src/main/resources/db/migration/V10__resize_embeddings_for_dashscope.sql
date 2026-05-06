-- ============================================================================
-- V10__resize_embeddings_for_dashscope.sql
--
-- Switch the embedding provider back to Aliyun DashScope (text-embedding-v3,
-- 1024 dim) after V2 had narrowed both columns to 768 for Gemini.
--
-- DeepSeek V3 has no embedding endpoint, so the project ran without embedding
-- bean from the DeepSeek migration through V9. Both vector columns are
-- expected to be entirely NULL at this point — no production deployment ever
-- back-filled embeddings against the 768-dim Gemini provider.
--
-- pgvector accepts ALTER TYPE on an empty (all-NULL) vector column directly.
-- If a future deployment reaches this migration with non-NULL embeddings
-- already present, those rows MUST be truncated or re-embedded BEFORE running
-- this migration (Postgres will reject the dimension change otherwise).
-- ============================================================================

-- Defensive: any 768-dim embedding from a prior provider is incompatible with
-- 1024-dim DashScope vectors and would silently produce nonsense distances.
-- Wipe before resizing so post-migration callers get a clean re-embed path.
UPDATE qc_rule  SET description_embedding = NULL WHERE description_embedding IS NOT NULL;
UPDATE icd_dict SET name_embedding        = NULL WHERE name_embedding        IS NOT NULL;

ALTER TABLE qc_rule  ALTER COLUMN description_embedding TYPE VECTOR(1024);
ALTER TABLE icd_dict ALTER COLUMN name_embedding        TYPE VECTOR(1024);
