-- ============================================================================
-- V11__add_icd_dict_embedding_text.sql
--
-- Adds the precomposed embedding source text column. The JSONL files in
-- docs/icd_dict/ already supply an "embedding_text" field of the form
--   "47.0900 其他阑尾切除术 开腹阑尾切除 经典阑尾切除术 阑尾切除"
-- (code + canonical name + every alias, space-separated). Storing this in
-- the table — rather than re-stitching from code+name+aliases at re-embed
-- time — lets IcdDictEmbeddingService pull a single column and feed it
-- straight to the model. Aliases drive most of the recall lift, so dropping
-- them here would make the cosine search useless for synonym queries.
--
-- Loader fallback (in IcdDictLoader): if a JSONL row omits embedding_text,
-- we synthesize "code + ' ' + name" so the column is never NULL. That
-- preserves "code legality" recall but loses alias-based recall for that
-- entry — operator should fix the JSONL and POST /admin/icd-dict/reload.
-- ============================================================================

ALTER TABLE icd_dict ADD COLUMN embedding_text TEXT;

-- Backfill existing rows with the minimal "code name" string so new
-- IcdDictEmbeddingService runs don't see NULLs. Re-running the JSONL
-- loader after this migration will overwrite this with the rich
-- code+name+aliases text from the source files.
UPDATE icd_dict SET embedding_text = code || ' ' || name WHERE embedding_text IS NULL;

ALTER TABLE icd_dict ALTER COLUMN embedding_text SET NOT NULL;
