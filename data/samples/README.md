# Sample 病案首页 PDF Drop Folder

This folder holds 1–3 desensitized 他院 病案首页 PDFs used as demo input
for Phase 3 (PDF import + multimodal extraction) and as the source for
Phase 4's DoD #4/#5/#6 walkthrough.

## PII / regulatory note

- PDFs in this folder MUST be desensitized BEFORE landing here.
- Per C-regulatory-003: the `id_card_masked` column stores only the last
  4 digits of any ID; the source PDF should already be desensitized.
- Per C-regulatory-001: PDFs ARE sent to 阿里云 DashScope during demos
  (qwen-vl-max processing); this is acceptable for demo only — pre-prod
  must pass hospital infosec + legal review.
- This folder is .gitignored for `*.pdf` / `*.PDF` / `*.png` / `*.jpg`.
  Operator keeps PDFs locally; do NOT commit them.

## File expectations

- File extension: `.pdf` only
- Size: ≤ 10 MB each (C-input-001 / D-029)
- Pages: 1–5 typical (backend processes only first 5; D-029)
- Naming suggestion: `sample-01.pdf` ... `sample-08.pdf`

## Phase handoff

- **Phase 0 (this plan):** create the folder + this README; operator drops
  in 1–3 desensitized PDFs locally.
- **Phase 3 (`03-*-PLAN.md`):** the upload UI accepts these PDFs; the
  multimodal extraction pipeline (PDFBox @ DPI 200 → qwen-vl-max →
  structured JSON) parses 30 fields with confidence ≥ 0.7 per DoD #4.
- **Phase 4:** demo runs `8 张样本病案首页` to prove pass-rate 60% → ≥95%.

## See also

- D-015, D-016, D-029 (PDF input + multimodal extraction)
- C-input-001 (upload limits)
- C-regulatory-001, C-regulatory-003 (PII / data residency)
