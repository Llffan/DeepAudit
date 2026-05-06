-- ============================================================================
-- V12: Seed rule R006 — 主手术编码-名称语义一致性（向量召回兜底）
--
-- 维度：consistency
-- 严重度：hint（语义判断带不确定性，定为提示而非扣分；运维稳定后可升级 deduction）
--
-- 与 R005 的关系：
--   R005 (icdNameMatches): 字符串精确比较（空白折叠后 .equals）。
--                          严，零误报，但同义词/俗称会判错。
--   R006 (icdNameSimilar): 向量 cosine 相似度。
--                          松，能容同义词，需要 DashScope embedding 才生效。
--   两条规则同时启用：R005 通过的肯定也通过 R006；R005 失败但 R006 通过的
--   情况说明"录入名称是合规同义词"，operator 可据此决定是否升级 R005 严重度。
--
-- 表达式语义：
--   when:   编码与名称两侧都填了才校验
--   assert: icdNameSimilar 在 icd_dict 里找编码对应行的 name_embedding，
--           对录入名称在线 embed 一次，做 cosine。距离 ≥ 0.6 → 通过。
--
-- 缺数据时的兜底（icdNameSimilar 内部 abstain → yield true）：
--   * 字典里查不到该 code           → 让 R004 抓
--   * 字典行 name_embedding IS NULL → 提醒 operator 跑 /admin/icd-dict/reembed
--   * EmbeddingModel bean 不存在    → DASHSCOPE_API_KEY 未配置，规则降级跳过
-- ============================================================================

INSERT INTO qc_rule (code, name, description, dimension, severity, expression, error_message_template)
VALUES
('R006',
 '主手术编码与名称语义一致性',
 '主手术编码与名称同时填写时，名称应与 ICD-9-CM-3 字典标准名称的语义相似度不低于 0.6（兜底兼容同义词）。',
 'consistency',
 'hint',
 '{
   "when":   { "op": "and", "args": [
                 { "op": "notNull", "field": "mainOperationCode" },
                 { "op": "notNull", "field": "mainOperationName" } ] },
   "assert": { "op": "icdNameSimilar",
               "codeField": "mainOperationCode",
               "nameField": "mainOperationName",
               "category":  "icd9cm3",
               "threshold": 0.6 }
 }'::jsonb,
 '主手术编码 {{mainOperationCode}} 与名称 {{mainOperationName}} 语义相似度偏低，请核对是否对应同一术式')
ON CONFLICT (code) DO NOTHING;
