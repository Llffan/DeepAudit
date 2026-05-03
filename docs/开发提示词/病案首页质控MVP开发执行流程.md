# 病案首页质控 MVP · 开发执行流程

> **配套文档**：本流程是《病案首页质控MVP开发计划书.md》§10 任务清单的**线性可勾化展开**，把半天粒度的任务表落地为一步一步的执行 checklist，并把 §9 风险预案的关键防御点插入对应步骤。
>
> **使用方式**：从阶段 0 开始顺序执行；每个阶段末尾必须通过校验点才进入下一阶段。
>
> **预计工时**：4 个时间盒共 16 小时 + 阶段 0 前置 0.5 小时。

---

## 🛠 总图

```
[阶段 0: 前置准备 0.5h]
        ↓
[阶段 1: Day1 上午 - 地基 4h] ──校验1
        ↓
[阶段 2: Day1 下午 - 规则引擎+配置 4h] ──校验2
        ↓
[阶段 3: Day2 上午 - PDF 导入 4h] ──校验3
        ↓
[阶段 4: Day2 下午 - 检查+解释+收尾 4h] ──DoD 8 条
        ↓
[交付]
```

---

## 阶段 0：前置准备（开 Day 1 前，0.5h）

> 这一步不在 §10 时间盒内，但 §9 R1 / R4 / R7 都要求提前做完，否则 Day 1 一开始就堵。

| 步骤 | 动作 | 防的是 |
|---|---|---|
| 0.1 | 申请阿里云 DashScope API Key，**充值至少 ¥50**，控制台关闭"数据改进计划"开关 | R1 / R9 |
| 0.2 | 用 curl 测试 `qwen-max` / `qwen-vl-max` / `text-embedding-v3` 三个模型连通性 | R1 |
| 0.3 | 开发机装好：JDK 21、Docker Desktop、Node 18+、pnpm、Git | — |
| 0.4 | 收集 1–3 份脱敏的他院病案首页 PDF 作为演示语料 | R2 |
| 0.5 | 下载或准备 1000 条高频 ICD-10 / ICD-9-CM-3 编码 CSV（不要先做全量 4.6 万） | R4 |
| 0.6 | 在仓库里建空的 `backend/` 和 `frontend/` 目录，写好 `.gitignore` | — |

**验收**：API Key 跑通三个模型；样本 PDF 与 ICD CSV 已就位；docker / jdk / node 命令可用。

---

## 阶段 1：Day 1 上午（4h）—— 地基

**目标**：`docker-compose up -d` 后前后端首屏可访问，数据库里有 5 张表 + 2 条种子规则。

### T1.1（0.5h）init 工程脚手架

- [ ] 后端：`spring-boot 3.2 + JPA + langchain4j 0.36`
- [ ] 前端：`vite + vue 3.4 + element-plus 2.7 + pinia`
- ⚠️ 锁版本号到 lockfile（`pnpm-lock.yaml` / `pom.xml` 显式版本），避免 R6 版本冲突

### T1.2（0.5h）写 docker-compose.yml

- [ ] 服务：`postgres:16`（装 pgvector 扩展）/ `backend` / `frontend` / `nginx`
- ⚠️ Nginx 配置里 `/api/check-results/*/explain` 端点必须 `proxy_buffering off; proxy_cache off;`（防 R8 SSE 流被缓冲）

### T1.3（1.0h）Flyway 迁移脚本

- [ ] `V1__init_schema.sql`：5 张表 DDL（复制开发计划书 §4.3–§4.6）
- [ ] `V2__seed_rules.sql`：R001 + R002 INSERT（复制 §4.3 末尾 DSL）
- ⚠️ pgvector 扩展先 `CREATE EXTENSION IF NOT EXISTS vector;`

### T1.4（1.0h）ICD 字典种子数据

- [ ] 写一次性脚本：读 CSV → 调 `text-embedding-v3` → 生成 SQL INSERT 包
- ⚠️ 离线生成完 commit 到仓库；docker 启动时直接 `psql` 灌入（R4 防首次启动卡 30 分钟）

### T1.5（0.5h）后端骨架

- [ ] `@Entity` 类（5 张表）/ `Repository` 接口 / DTO `record` / `Controller` 占位

### T1.6（0.5h）前端骨架

- [ ] 3 个路由占位页 + 全局 layout 侧边栏（规则配置 / 病案导入 / 结果列表）

### 🔍 校验点 1（必须通过才能进 Day1 下午）

- [ ] `docker-compose up -d` 后 `docker ps` 看到 4 个 container 都 healthy
- [ ] `psql` 进 PG，`\dt` 看到 5 张表
- [ ] `SELECT COUNT(*) FROM qc_rule WHERE deleted_at IS NULL;` 返回 `2`
- [ ] 浏览器访问 `http://localhost:8080` 看到前端首屏
- [ ] `http://localhost:8080/api/actuator/health` 返回 `{"status":"UP"}`

---

## 阶段 2：Day 1 下午（4h）—— 规则引擎 + 规则配置

**目标**：界面里能看到 R001/R002，能切换启用状态，能用一句话生成新规则。

### T2.1（1.0h）`RuleEvaluator`（DSL 求值器）

- [ ] 200 行 Java 递归 switch（复制开发计划书 §7.2 骨架）
- [ ] 每写完一个操作符立即写一个 JUnit 测试
- [ ] 覆盖 R001（`when` + `and` + `notNull`）和 R002（`and` + `dateBefore` + `eq` + `dateDiffDays`）
- ⚠️ 这一步是核心，求值错了所有检查都崩

### T2.2（0.5h）`RuleDslValidator`

- [ ] 遍历 DSL 树，检查 `op` 在白名单内 + `field` 在已知字段表内
- ⚠️ 防 R3 + R10（LLM 写出未知操作符不让入库）

### T2.3（1.0h）规则 CRUD API

- [ ] `GET / POST / PUT / DELETE /api/rules`
- [ ] `PATCH /api/rules/{id}/enabled`
- [ ] `POST /api/rules/dry-run`
- ⚠️ `DELETE` 在 service 层检查 `enabled=true` 时返回 409（开发计划书 §5.4）

### T2.4（0.5h）langchain4j 配置 + Embedding 服务

- [ ] `LlmAutoConfiguration`：装配 `ChatModel` / `StreamingChatModel` / `EmbeddingModel` 三个 Bean
- [ ] 保存规则时调 `EmbeddingService.embed(name + description)` 写 `description_embedding` 列

### T2.5（0.5h）`RuleDslGenerator` AiService + NL→Rule API

- [ ] 接口式（复制开发计划书 §8.3 接口签名）
- [ ] `POST /api/rules/from-natural-language`
- ⚠️ 输出必走 `RuleDslValidator` 二次校验

### T2.6（0.5h）前端规则列表 + 编辑抽屉

- [ ] 列表 + 筛选栏 + 启用开关原地切换
- [ ] 编辑抽屉 Tab1（基础信息）+ Tab3（AI 写规则）
- ⚠️ 时间紧：DSL 编辑器只做 Monaco JSON 单模式；可视化树编辑器砍到 M0+ 增量（开发计划书 §11.3）

### 🔍 校验点 2

- [ ] 列表页能看到 R001 / R002 两条规则
- [ ] 切换启用开关后 PATCH 调用成功，DB 里 `enabled` 字段同步
- [ ] 对一份样例 JSON 调 dry-run，能看到 R001 命中或不命中
- [ ] 输入"年龄超过 120 提示用户确认"，AI 写规则能生成合法 DSL 并保存
- [ ] 启用状态的规则点删除按 钮被前端隐藏 / 后端返回 409

---

## 阶段 3：Day 2 上午（4h）—— PDF 导入

**目标**：上传一份 PDF，30 秒内字段表单出来，可修改后保存。

### T3.1（0.5h）PDFBox 集成

- [ ] `PDFRenderer.renderImageWithDPI(page, 200)` → `byte[]` PNG
- ⚠️ docker base image 必须装 `noto-cjk` 字体（防 R7 中文渲染缺失）

### T3.2（1.0h）`MedicalRecordExtractor` AiService

- [ ] 接口签名（复制开发计划书 §8.2）
- [ ] prompt 文件放 `resources/prompts/extractor.system.txt`
- ⚠️ 关键：prompt 写"宁缺勿臆测"，模糊字段输出 `null`（防 R2）
- [ ] 先用 1 份样本 PDF 调通，再处理边缘情况

### T3.3（1.0h）`POST /api/medical-records/import`

- [ ] multipart 接收 → 落盘 `./uploads/{uuid}.pdf` → PDFBox → Extractor → 入库
- ⚠️ 异常时 LLM 失败回退：返回空 schema + `extraction_confidence=0`，前端进手填模式（开发计划书 §8.7）
- ⚠️ 事务边界：文件落盘 + DB 入库一并成功或一并清理

### T3.4（0.5h）前端上传页

- [ ] 拖拽区 + loading 转圈 + 错误提示

### T3.5（1.0h）前端字段确认页

- [ ] 简化：PDF 预览用 `<embed src="...pdf">` 一行搞定
- [ ] 字段表单按开发计划书 §6.4 分块，用 `v-for` 循环类别
- [ ] 置信度三档着色（§6.5）可以延后，MVP 显示数字也行

### 🔍 校验点 3

- [ ] 上传一份样本 PDF，30s 内跳到字段确认页
- [ ] 表单里 main 字段都有值（或明确空），`extraction_confidence` 显示
- [ ] 改两个字段，点"保存草稿"成功
- [ ] 点"确认并执行检查"跳到 `/records/:id/results`（页面此时尚未实现，看 placeholder 即可）

---

## 阶段 4：Day 2 下午（4h）—— 检查执行 + LLM 解释 + 收尾

**目标**：从字段确认页 → 看到违规 → 点详情 → 流式解释。DoD 8 条全过。

### T4.1（0.5h）`POST /api/medical-records/{id}/check`

- [ ] 加载 `main` + `extra` → 加载所有 `enabled=true` 规则 → 串行求值
- [ ] 命中的规则写 `check_result`（含开发计划书 §4.5 所有快照字段）
- [ ] 返回聚合结果（§7.6 的 `summary` 结构）

### T4.2（0.25h）`GET /api/check-results/{recordId}`

- [ ] 单表查询返回最近一次结果

### T4.3（1.0h）`ViolationExplainer` + SSE Endpoint

- [ ] `StreamingChatModel` + `Flux<ServerSentEvent<String>>`
- ⚠️ 首次生成完写回 `check_result.llm_explanation` 缓存
- ⚠️ 命中缓存发 `event:cached` 一次性返回
- [ ] prompt 文件 `resources/prompts/explainer.system.txt`（开发计划书 §8.4）

### T4.4（0.25h）`PATCH /api/check-results/{id}/status`

- [ ] `open` / `acknowledged` / `false_positive` 三态切换

### T4.5（1.0h）前端检查结果页

- [ ] 顶部 4×3 KPI 矩阵（开发计划书 §7.3 ASCII 线框）
- [ ] 违规卡片按 severity 排序：`mandatory` → `deduction` → `hint`
- [ ] 每张卡 3 按钮：查看人话解释 / 跳转到字段修正 / 标记误报

### T4.6（0.5h）违规卡片接 SSE

- [ ] `EventSource`（复制开发计划书 §8.4 末尾代码）
- [ ] token 追加渲染 + `cached` 一次性显示 + `error` 重试按钮

### T4.7（0.5h）收尾

- [ ] DoD 8 条逐项过
- [ ] README 写部署步骤
- [ ] 录屏 demo

### 🔍 终态校验（开发计划书 §2.4 DoD 8 条全部 ✅）

- [ ] **D1** docker-compose 一键拉起，前端可访问
- [ ] **D2** 规则配置页可完成 R001/R002 的查看与启停切换
- [ ] **D3** AI 写规则功能由自然语言生成至少 1 条新规则并保存
- [ ] **D4** 上传一份样本 PDF，自动抽取并展示字段置信度
- [ ] **D5** 字段确认后触发检查，至少展示 2 条违规命中
- [ ] **D6** 点击违规可看到 LLM 流式生成的人话解释，首 token < 3s
- [ ] **D7** 规则可被停用，停用后再次检查不再命中
- [ ] **D8** 已启用规则尝试删除时被拦截，提示"请先停用"

---

## 🎯 关键决策点速查表

写代码时遇到这些时刻，按表里的决策走，不要现场二次设计：

| 时刻 | 决策 | 计划书章节 |
|---|---|---|
| 设计规则表 | 用 JSONB 存 DSL，不用表达式字符串 | §4.3 |
| 写 evaluator | 只支持白名单操作符，遇到未知抛 `UnknownOperatorException` | §7.2 |
| 已启用规则被改 | 改基础信息直接保存；改 DSL 弹二次确认；删除直接拦截 | §5.4 |
| LLM 任何调用 | 走 langchain4j 的 AiService 接口，不裸调 HTTP | §8.1 |
| LLM 输出入库前 | 必走后置校验（DSL Validator / JSON Schema） | §8.7 |
| LLM 失败时 | 业务流不阻断，进手填 / 跳过去重 / 显示半截结果 | §8.7 |
| 写 check_result | 6 个 snapshot 字段必须填齐，规则后续修订不污染历史 | §4.2 / §4.5 |
| Nginx 反代 SSE | `proxy_buffering off` 必加 | §9 R8 |
| pgvector 灌库 | 离线预生成 SQL 包，启动时不在线调 embedding | §4.6 / §9 R4 |
| Prompt 维护 | 放 `resources/prompts/*.txt` 文件，不硬编码 Java 字符串 | §8.6 |

---

## ✂️ 时间不够时的优先砍单

按"砍掉损失最小"排序，依次砍：

| 砍单顺序 | 功能 | 损失 |
|---|---|---|
| 1 | 可视化 DSL 树编辑器 → 只用 Monaco JSON | 质控员体验差，但 DoD 不影响 |
| 2 | 测试沙盒 Tab4 | 保存前自测能力丢失 |
| 3 | 字段级置信度着色 → 整体 confidence 数字显示即可 | UI 朴素，DoD 不影响 |
| 4 | AI 写规则功能 | DoD #3 失分，但核心闭环不影响 |
| 5 | 规则去重检测 | §5.5 不展示，保存路径变简单 |

**核心闭环不能砍**：上传 PDF → 抽取字段 → 确认 → 触发检查 → 看到违规 → 看到流式解释。这条路径就是 DoD #1 / #2 / #4 / #5 / #6 / #7 的全部。

---

## 📋 每个阶段开始前的自检清单

| 阶段 | 入场前自检 |
|---|---|
| 阶段 0 | 我是不是已经准备好通义千问 API Key 和样本 PDF？|
| 阶段 1 | docker / jdk / node 命令是不是都能跑？|
| 阶段 2 | 阶段 1 校验点 5 项是不是全过了？|
| 阶段 3 | 规则 CRUD 和 dry-run 是不是都能用？AI 写规则是不是出了一条新规则？|
| 阶段 4 | 字段确认页是不是能保存草稿，并跳到 results 占位页？|

---

## 🔗 与开发计划书的对应关系

| 本文件 | 对应计划书章节 |
|---|---|
| 阶段 0 | §9（风险与对策） |
| 阶段 1 | §3（架构）、§4（数据模型）、§10.2（Day1 上午） |
| 阶段 2 | §5（规则配置）、§7.2（DSL 求值）、§8.1 / §8.3 / §8.5（LLM 配置）、§10.3（Day1 下午） |
| 阶段 3 | §6（PDF 导入）、§8.2（多模态抽取）、§10.4（Day2 上午） |
| 阶段 4 | §7（检查执行）、§8.4（流式解释）、§10.5（Day2 下午） |
| 终态校验 | §2.4（DoD） |
| 关键决策点 | §4 / §5 / §7 / §8 散落决策汇总 |
| 砍单顺序 | §11.3（短期增量预留） |

---

*— 流程文档完 —*
