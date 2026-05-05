-- ============================================================================
-- V4: Seed rule R004 — 主手术编码标准化校验
--
-- 维度：standardization（编码/格式合规）
-- 严重度：deduction（编码错误是质控扣分项，不是 mandatory 完整性问题）
--
-- 表达式语义：
--   when:   主手术编码非空时才校验（空编码归 R001 完整性管）
--   assert: 调用新内置布尔算子 icdCodeExists 查 icd_dict 表
--           (RuleEvaluator 通过 IcdDictCache 内存索引判定，不打 DB)
--
-- 前置依赖：icd_dict 表必须已有 ICD-9-CM-3 字典数据
-- (IcdDictLoader 启动时从 docs/icd_dict/icd9cm3_common.jsonl 自动灌入).
-- ============================================================================

INSERT INTO qc_rule (code, name, description, dimension, severity, expression, error_message_template)
VALUES
('R004',
 '主手术编码标准化校验',
 '当病案主手术编码非空时，必须存在于 ICD-9-CM-3 国家临床版字典中。',
 'standardization',
 'deduction',
 '{
   "when":   { "op": "notNull",        "field": "mainOperationCode" },
   "assert": { "op": "icdCodeExists",  "field": "mainOperationCode", "category": "icd9cm3" }
 }'::jsonb,
 '主手术编码 {{mainOperationCode}} 不在 ICD-9-CM-3 标准字典中，请核对编码或字典版本')
ON CONFLICT (code) DO NOTHING;
