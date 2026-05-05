# ICD 字典灌库与向量化规范

本文件约定 `docs/icd_dict/*.jsonl` → `icd_dict` 表 → pgvector 索引的整条管道，
供后续 T6/T7 编码标准化质控规则实现时参考。

---

## 1. 数据流总览

```
docs/icd_dict/icd9cm3_common.jsonl   ┐
docs/icd_dict/icd10_common.jsonl     ┼→ 灌库脚本 → INSERT icd_dict (无 embedding)
                                      │
                                      └→ 向量化任务 → embedding 模型 → UPDATE name_embedding
```

**两步分离的理由**：
- 灌库是确定性、可重复执行（基于 `UNIQUE(code, category, version)` 幂等）
- 向量化依赖外部 embedding API、要支付成本、可能失败重试 —— 必须独立任务

---

## 2. 灌库脚本约定

### 输入
`docs/icd_dict/*.jsonl` —— 每行一条 JSON，字段见 `README.md`

### 输出
INSERT 进 `icd_dict` 表（V1__init_schema.sql 已建）：

```sql
INSERT INTO icd_dict (code, name, category, version, name_embedding, created_at)
VALUES (?, ?, ?, ?, NULL, NOW())
ON CONFLICT (code, category, version) DO UPDATE
  SET name = EXCLUDED.name;        -- 名称可能修订；版本相同则覆盖
                                   -- name_embedding 不动，让向量化任务异步重算
```

### 推荐实现位置
`backend/.../service/IcdDictLoader.java`：
- `@PostConstruct` 启动时 idempotent 灌库（可被 `deepaudit.icd.auto-load=false` 关闭）
- 或者 `@RestController` 提供 `POST /api/admin/icd-dict/reload` 手动触发
- 字段 `chapter` / `block` / `aliases` / `embedding_text` 当前**不入表**，
  仅向量化任务读 `embedding_text`、UI 召回结果时通过文件二次解析读 `aliases`

> 若后续要让规则引擎按 chapter 过滤（"主诊为 Cxx 时主术应为肿瘤章节"），
> 需要 V4__add_icd_chapter.sql 在 `icd_dict` 加 `chapter VARCHAR(8)` 列，再回灌。

---

## 3. 向量化任务约定

### 模型选型（待定）
当前后端切到 DeepSeek V3，**DeepSeek 没有 embedding API**。需另选：

| 选项 | 维度 | 备注 |
|------|------|------|
| 阿里云通义 text-embedding-v3 | 1024 | 中文医疗友好，需 ALTER VECTOR(768) → VECTOR(1024) |
| BGE-large-zh-v1.5（自托管） | 1024 | 离线、零成本、需 GPU 或 ONNX CPU |
| OpenAI text-embedding-3-small | 1536 | 中文一般、需翻墙 |

> V2__resize_embeddings_for_gemini.sql 把列从 1024 改成了 768 适配 Gemini，
> 现已不用 Gemini —— 选定新模型后写 V4 把维度调到目标值。

### 向量化语料
**只 embed `embedding_text` 字段**，不 embed code+name 拼接 —— `embedding_text`
已包含 `code + name + aliases`，是离线时一次性拼好的最优语料：

```
"47.0900 其他阑尾切除术 开腹阑尾切除 经典阑尾切除术 阑尾切除"
```

aliases 加入语料是召回率的关键 —— 临床医生写"开腹阑尾切除"时
需能召回到标准编码 47.0900。

### 批量 + 重试
- 每批 ≤ 25 条（多数 embedding API 单批限额）
- 失败的条目记日志，不阻塞整批
- 入库 `UPDATE icd_dict SET name_embedding = ? WHERE id = ?`

### 推荐实现位置
`backend/.../service/IcdDictEmbeddingService.java`：
- 启动时扫描 `name_embedding IS NULL` 的行 → 批量 embed → 回填
- 或 `POST /api/admin/icd-dict/reembed` 手动触发（重新 embed 全部）

---

## 4. 编码维度规则示例（T6/T7 落地参考）

灌库 + 向量化完成后，可立刻落地以下 standardization / consistency 规则：

### R-Std-001：手术编码合法性
```json
{
  "name": "主手术编码合法性",
  "dimension": "standardization",
  "severity": "deduction",
  "errorMessageTemplate": "主手术编码 {{mainOperationCode}} 不在 ICD-9-CM-3 字典中",
  "expression": {
    "when":   { "op": "notNull", "field": "mainOperationCode" },
    "assert": { "op": "custom",  "code": "icdCodeExists",
                "args": { "codeField": "mainOperationCode", "category": "icd9cm3" } }
  }
}
```

需新增自定义算子 `icdCodeExists(codeField, category)`，body 通过新增的
内置布尔 op 实现 —— 不能纯 JSON DSL 表达，因为要查 DB。
**实现路径**：`RuleEvaluator.evalBool` 中加 `case "icdLookup"` 分支，
或在 `CustomOperatorRegistry` 旁挂一个 "Java-backed builtin" 注册表。

### R-Std-002：ICD 版本一致性
"主诊断 ICD 版本"必须与"主手术 ICD 版本"成对出现（`mainDiagnosisIcdVer` 字段已存在）。
纯 DSL 可写：

```json
{
  "name": "ICD 版本一致性",
  "dimension": "consistency",
  "severity": "hint",
  "errorMessageTemplate": "主诊断 ICD 版本 {{mainDiagnosisIcdVer}} 与主手术编码不匹配",
  "expression": {
    "when":   { "op": "and", "args": [
                  { "op": "notNull", "field": "mainOperationCode" },
                  { "op": "notNull", "field": "mainDiagnosisIcdVer" } ] },
    "assert": { "op": "custom", "code": "icdVersionMatch",
                "args": { "codeField": "mainOperationCode",
                          "versionField": "mainDiagnosisIcdVer" } }
  }
}
```

### R-Cons-001：编码-名称语义一致性（向量召回）
"主手术编码" + "主手术名称"应语义匹配 —— 给 `mainOperationName` embed 后，
和 `icd_dict.name_embedding` 中 `code = mainOperationCode` 的那条做 cosine 距离，
距离 > 阈值（如 0.5）时报警。需 Java 侧实现，不在 DSL 表达力范围。

---

## 5. 待办清单

- [x] `IcdDictLoader` —— 启动灌库 + 管理端点
      （`backend/.../service/IcdDictLoader.java` + `IcdDictAdminController.java`）
- [x] Maven 单点维护 —— `backend/pom.xml` 中 `<resource>` 把
      `docs/icd_dict/*.jsonl` 镜像到 `classpath:data/icd_dict/`，
      JSONL 只在 docs 一份。
- [ ] V4 migration：选定 embedding 模型后调整 `name_embedding` 维度
- [ ] V4 migration：可选地给 `icd_dict` 加 `chapter` / `block` 列
- [ ] `IcdDictEmbeddingService` —— 批量向量化 + 重试 + 进度日志
- [ ] `RuleEvaluator` 加 `case "icdLookup"` 内置布尔算子，或 Java-backed 自定义算子机制
- [ ] 三条 standardization/consistency 维度的种子规则（参考 §4）
- [ ] 替换为权威全量字典（V3.0 国卫办医函〔2020〕438号 附件 / 医保版）

---

## 6. 当前用法（已落地）

**冷启动自动灌库**（推荐）：
```bash
# 数据库为空时启动后端，IcdDictLoader 会自动把
#   docs/icd_dict/*.jsonl → icd_dict 表
# 启动日志可见: "ICD dict auto-load complete: 227 rows from 2 file(s)"
```

**修改 JSONL 后强制重载**（无需重启）：
```bash
curl -X POST http://localhost:8080/api/admin/icd-dict/reload
# {"upserted":227,"totalAfterLoad":227,"icd9cm3":157,"icd10":70}
```

**查询当前条数**：
```bash
curl http://localhost:8080/api/admin/icd-dict/stats
# {"icd9cm3":157,"icd10":70,"total":227}
```

**关闭自动灌库**（如需）：
```yaml
# application.yml 或环境变量
deepaudit:
  icd:
    auto-load: false
# 等价于  DEEPAUDIT_ICD_AUTO_LOAD=false
```
