# DashScope Smoke-Test Scripts

Pre-Day-1 prerequisite (C-timeline-002 / R1 defense): run these BEFORE
opening Day-1 morning time-box, ideally 24h prior so any DashScope
account/quota issues surface early.

## What this verifies

| Script | Model | What it proves |
|---|---|---|
| `dashscope-chat.sh` | `qwen-max` | Phase 2 NL→Rule (L2) connectivity + ¥50 余额可用 |
| `dashscope-vision.sh` | `qwen-vl-max` | Phase 3 multimodal extraction (L1) connectivity |
| `dashscope-embedding.sh` | `text-embedding-v3` | Phase 1 ICD seed (V) + Phase 2 dedup (V) connectivity, 1024-dim output |
| `dashscope-all.sh` | all of the above | One-shot aggregator; PASS/FAIL summary |

## Running

```bash
export DASHSCOPE_API_KEY="sk-..."  # from https://dashscope.console.aliyun.com
bash scripts/smoke-test/dashscope-all.sh
```

Expected output on success:
```
=== Running dashscope-chat.sh ===
[chat] PASS — qwen-max
=== Running dashscope-vision.sh ===
[vision] PASS — qwen-vl-max
=== Running dashscope-embedding.sh ===
[embedding] PASS — text-embedding-v3 (~1024 dims)
=============================
ALL DashScope smoke-tests PASS
=============================
Reminder (C-regulatory-002): verify DashScope console
'数据改进计划' toggle is OFF before any production demo.
```

## Pre-flight checklist (operator-only — not automatable)

- [ ] DashScope API Key 已申请: https://dashscope.console.aliyun.com
- [ ] 充值 ≥ ¥50 (REQ-NFR-cost / R1)
- [ ] **关闭"数据改进计划"开关 (C-regulatory-002 / REQ-NFR-data-residency)** —
      否则上传的 PDF/抽取结果可能被用于训练。MVP 演示阶段必须关闭；
      预生产前过医院信安+法务评审 (C-regulatory-001).
- [ ] 已设置 `DASHSCOPE_API_KEY` 环境变量
- [ ] 网络能访问 `dashscope.aliyuncs.com`

## What to do on failure

| Failure | Diagnose |
|---|---|
| `chat` only fails | qwen-max quota / model-specific outage; check console |
| `vision` only fails | qwen-vl-max permission (some accounts need explicit enable) |
| `embedding` only fails | text-embedding-v3 access; check console > 模型开通 |
| All fail with 401 | Bad API key — re-issue from console |
| All fail with timeout | Network/firewall to `dashscope.aliyuncs.com:443` blocked |
| All fail with 403 余额 | 充值 ≥ ¥50 |

## Phase handoff

- **Phase 0 (this plan):** these scripts are the smoke-test gate.
- **Phase 1 (`01-*-PLAN.md`):** business code stops using curl and
  routes all LLM calls through langchain4j Beans (D-006). These scripts
  remain as a CI/troubleshooting dev tool but are not in the deploy path.

## See also

- C-timeline-002 (Stage-0 prereqs)
- C-llm-001 (LLM provider config)
- C-regulatory-002 (data improvement plan toggle — must be off)
- C-regulatory-001 (pre-prod data security review)
- D-006 (langchain4j is the sole LLM entry point — for code path; smoke-test bypasses for connectivity proof)
