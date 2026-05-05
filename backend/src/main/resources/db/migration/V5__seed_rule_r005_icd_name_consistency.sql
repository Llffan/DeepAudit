-- ============================================================================
-- V5: Seed rule R005 — 主手术编码-名称一致性校验
--
-- 维度：consistency（跨字段一致性）
-- 严重度：deduction（编码与名称不匹配是病案质控扣分项）
--
-- 表达式语义：
--   when:   编码与名称两侧都填了才校验
--           （编码空 → 归 R001 完整性管；名称空 → 与本规则无关）
--   assert: icdNameMatches 拿编码到 icd_dict 查标准名，与录入名称
--           做空白折叠后的字符串相等比较。
--
-- 与 R004 的分工：
--   R004 (standardization): 编码本身是否在字典中
--   R005 (consistency)    : 编码存在前提下，名称是否与字典一致
--   字典查不到的场景由 R004 抓，R005 在 dictName == null 时主动放行避免重复告警。
-- ============================================================================

INSERT INTO qc_rule (code, name, description, dimension, severity, expression, error_message_template)
VALUES
('R005',
 '主手术编码与名称一致性',
 '主手术编码与名称同时填写时，名称必须与 ICD-9-CM-3 字典中的标准名称一致（忽略多余空白）。',
 'consistency',
 'deduction',
 '{
   "when":   { "op": "and", "args": [
                 { "op": "notNull", "field": "mainOperationCode" },
                 { "op": "notNull", "field": "mainOperationName" } ] },
   "assert": { "op": "icdNameMatches",
               "codeField": "mainOperationCode",
               "nameField": "mainOperationName",
               "category":  "icd9cm3" }
 }'::jsonb,
 '主手术编码 {{mainOperationCode}} 对应字典名称与录入名称 {{mainOperationName}} 不一致，请核对')
ON CONFLICT (code) DO NOTHING;
