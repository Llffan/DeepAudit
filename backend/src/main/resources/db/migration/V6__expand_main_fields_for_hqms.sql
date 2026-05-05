-- ============================================================================
-- V6: 扩充 medical_record_main 27 个 HQMS 国标字段
--
-- 全部铺平到主表（不进 extra_fields JSONB），方便后续 PDF 抽取直接落库 +
-- 让规则引擎可访问（FieldAccessor 白名单同步扩到 57 项）。
--
-- 不引入加密：电话 / 联系人信息 / 工作单位信息按明文存储，
-- 与现有 idCardMasked 的脱敏策略保持一致（仅证件号脱敏）。
-- ============================================================================

ALTER TABLE medical_record_main
    -- 人口学扩展 (10)
    ADD COLUMN nationality                VARCHAR(50),
    ADD COLUMN ethnicity                  VARCHAR(50),
    ADD COLUMN marital_status             VARCHAR(20),
    ADD COLUMN occupation                 VARCHAR(100),
    ADD COLUMN age_days                   INT,           -- 不足1周岁的年龄(天)
    ADD COLUMN newborn_birth_weight       INT,           -- 新生儿出生体重 (g)
    ADD COLUMN newborn_admission_weight   INT,           -- 新生儿入院体重 (g)
    ADD COLUMN id_card_type               VARCHAR(20),   -- 居民身份证 / 护照 / 军官证 / 其他
    ADD COLUMN birth_place                VARCHAR(200),
    ADD COLUMN native_place               VARCHAR(200),  -- 籍贯

    -- 地址 / 联系信息 (12) — 明文存储，仅在前端 UI 层考虑遮罩
    ADD COLUMN current_address            VARCHAR(300),
    ADD COLUMN current_phone              VARCHAR(30),
    ADD COLUMN current_zip                VARCHAR(10),
    ADD COLUMN registered_address         VARCHAR(300),
    ADD COLUMN registered_zip             VARCHAR(10),
    ADD COLUMN workplace                  VARCHAR(300),  -- 工作单位及地址
    ADD COLUMN work_phone                 VARCHAR(30),
    ADD COLUMN work_zip                   VARCHAR(10),
    ADD COLUMN contact_name               VARCHAR(100),
    ADD COLUMN contact_relation           VARCHAR(50),
    ADD COLUMN contact_address            VARCHAR(300),
    ADD COLUMN contact_phone              VARCHAR(30),

    -- 入出院扩展 (3)
    ADD COLUMN admission_ward             VARCHAR(50),
    ADD COLUMN discharge_ward             VARCHAR(50),
    ADD COLUMN specialty_dept             VARCHAR(100),

    -- 门急诊诊断 (2)
    ADD COLUMN outpatient_diagnosis       VARCHAR(200),
    ADD COLUMN outpatient_diagnosis_code  VARCHAR(32);
