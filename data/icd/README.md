# ICD Dictionary CSV Drop Folder

This folder is the entry point for the ICD-10 / ICD-9-CM-3 dictionary that
the rule engine and the field-confirm UI will consume from Phase 1 onward.

## What goes here

- `icd10_top1000.csv` — 1000 high-frequency ICD-10 国临版 2.0 codes
- `icd9cm3_top1000.csv` — 1000 high-frequency ICD-9-CM-3 国临版 4.0 codes

CSV format (header row required):
```
code,name,category,version
A01.0,伤寒,icd10,GB-T-14396-2016
00.01,治疗性超声波,icd9cm3,2017
...
```

Where `category` ∈ {`icd10`, `icd9cm3`} and matches the
`icd_dict.category` CHECK constraint locked in C-schema-005.

## Why 1000 rows, not full 4.6 万

D-019 + R4: full corpus needs ~30 minutes to embed via DashScope on first
boot. The MVP demo runs against a 1000-row subset; Phase 1 generates
`icd_seed.sql` (offline-embedded) from these CSVs and ships the SQL pack
into the postgres init folder.

## Phase handoff

- **Phase 0 (this plan):** drop the CSVs into this folder.
- **Phase 1 (`01-*-PLAN.md`):** consume CSVs, call `text-embedding-v3` once
  per row, generate `icd_seed.sql` containing INSERT statements with the
  1024-dim vectors literal-encoded; commit the resulting SQL into
  `DeepAudit/postgres/init/` so it auto-loads on first DB boot.
- The CSV files themselves are committed to git (they are public reference
  data); the generated `*.sql` is git-ignored (regenerable + size).

## See also

- C-schema-005 (icd_dict DDL)
- D-019 (ICD seed strategy)
- D-007 (pgvector usage scope)
