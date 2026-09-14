-- 补齐 school 表缺失字段
-- V1 建表用的是旧字段设计（region_code），而 School 实体 / SchoolMapper.xml / DTO
-- 用的是 school_type + province/city/district + contact_person，
-- 导致查询报 ERROR: column "school_type" does not exist。此处以 Java 层为准补齐。
-- 字段长度与 SchoolCreateRequest 的 @Size 校验保持一致。

ALTER TABLE school ADD COLUMN IF NOT EXISTS school_type    VARCHAR(20);
ALTER TABLE school ADD COLUMN IF NOT EXISTS province       VARCHAR(50);
ALTER TABLE school ADD COLUMN IF NOT EXISTS city           VARCHAR(50);
ALTER TABLE school ADD COLUMN IF NOT EXISTS district       VARCHAR(50);
ALTER TABLE school ADD COLUMN IF NOT EXISTS contact_person VARCHAR(50);

-- school_code 原为 VARCHAR(32)，DTO 允许 50，放宽以免超长写入直接抛 SQL 异常
ALTER TABLE school ALTER COLUMN school_code TYPE VARCHAR(50);

COMMENT ON COLUMN school.school_type IS '学校类型: PUBLIC-公立 PRIVATE-私立';
COMMENT ON COLUMN school.province IS '省份';
COMMENT ON COLUMN school.city IS '城市';
COMMENT ON COLUMN school.district IS '区县';
COMMENT ON COLUMN school.contact_person IS '联系人';

-- ponytail: region_code 保留不动，Java 层无任何引用，但删列不可逆；
-- 确认确实废弃后再单独发一版 DROP COLUMN + DROP INDEX idx_school_region
