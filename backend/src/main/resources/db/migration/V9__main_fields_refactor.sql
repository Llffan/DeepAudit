-- V9 (2026-05-05) 病案首页字段重构
--
-- 业务背景：录入页将"主要手术 / 费用"两个区块整体下线（运营反馈这些字段在
-- 当前质控流程里基本不录入，PDF 抽取也常缺失），改为补充损伤中毒外因、
-- 病理诊断扩展、药物过敏、血型、医生与质控等信息。原 9 列 DROP，新增 21 列。
--
-- 上线影响：
--  - 任何引用 main_operation_*/operation_date/operator/anesthesia_method/
--    *_cost 列的规则会跑空 —— 当前规则（R001~R005）未引用这些列，安全。
--  - 历史 medical_record_main 行里这些列的数据将随列一起丢失（DROP COLUMN
--    无法回滚）；MVP 阶段未启用这些字段的人工质控，可接受。

ALTER TABLE medical_record_main
    DROP COLUMN main_operation_code,
    DROP COLUMN main_operation_name,
    DROP COLUMN operation_date,
    DROP COLUMN operator,
    DROP COLUMN anesthesia_method,
    DROP COLUMN total_cost,
    DROP COLUMN drug_cost,
    DROP COLUMN operation_cost,
    DROP COLUMN medical_service_cost;

-- 损伤、中毒（2）
ALTER TABLE medical_record_main
    ADD COLUMN injury_poisoning_cause       VARCHAR(300),
    ADD COLUMN injury_poisoning_code        VARCHAR(32);

-- 病理（pathological_diagnosis 已在 V1 §4.4 存在；此处只补编码与病理号）
ALTER TABLE medical_record_main
    ADD COLUMN pathological_diagnosis_code  VARCHAR(32),
    ADD COLUMN pathology_number             VARCHAR(64);

-- 过敏 / 尸检 / 血型（5）
ALTER TABLE medical_record_main
    ADD COLUMN drug_allergy                 VARCHAR(20),    -- 无 / 有
    ADD COLUMN allergy_drugs                VARCHAR(300),
    ADD COLUMN autopsy                      VARCHAR(20),    -- 是 / 否
    ADD COLUMN blood_type                   VARCHAR(20),    -- A / B / O / AB / 不详 / 未查
    ADD COLUMN rh_blood_type                VARCHAR(20);    -- 阴 / 阳 / 不详 / 未查

-- 医生（8）
ALTER TABLE medical_record_main
    ADD COLUMN department_director          VARCHAR(100),   -- 科主任
    ADD COLUMN chief_physician              VARCHAR(100),   -- 主(副主)任医生
    ADD COLUMN attending_physician          VARCHAR(100),   -- 主治医生
    ADD COLUMN resident_physician           VARCHAR(100),   -- 住院医生
    ADD COLUMN responsible_nurse            VARCHAR(100),   -- 责任护士
    ADD COLUMN trainee_physician            VARCHAR(100),   -- 进修医生
    ADD COLUMN intern_physician             VARCHAR(100),   -- 实习医生
    ADD COLUMN coder                        VARCHAR(100);   -- 编码员

-- 质控（4）
ALTER TABLE medical_record_main
    ADD COLUMN record_quality               VARCHAR(20),    -- 甲 / 乙 / 丙
    ADD COLUMN qc_physician                 VARCHAR(100),
    ADD COLUMN qc_nurse                     VARCHAR(100),
    ADD COLUMN qc_date                      DATE;

-- 禁用引用了已 DROP 列的种子规则（R001/R004/R005 都依赖 mainOperationCode/Name/operationDate/operator）。
-- 软删而非硬删：保留行有助于审计；新版规则会通过后续迁移或运营 UI 添加。
UPDATE qc_rule
   SET enabled = FALSE,
       updated_at = NOW()
 WHERE code IN ('R001', 'R004', 'R005');
