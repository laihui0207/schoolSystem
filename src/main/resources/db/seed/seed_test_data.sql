-- ============================================================
-- 测试数据生成脚本 (PostgreSQL 16)
--
-- 规模: 5 学校 × 6 年级 × 15 班 × 40 学生
--       每班 5 教师(每科 1 名) / 每生 5 科 / 4 学期 × 2 次考试
--       => 学生 18000 / 教师 2250 / 成绩 720000 行
--
-- 手动执行, 不受 Flyway 管理 (flyway locations 仅 db/migration):
--   docker exec -i school-postgres psql -U school_admin -d school_system \
--     < src/main/resources/db/seed/seed_test_data.sql
--
-- 可重复执行: 开头只清理本脚本自己生成的数据(school_code 以 SEED- 开头),
-- 不触碰任何既有数据。
-- ============================================================

\timing on

BEGIN;

-- 固定随机种子 => 每次生成完全相同的数据, 便于断言
SELECT setseed(0.42);

-- ---------- 0. 清理上一次 SEED 数据 ----------
CREATE TEMP TABLE seed_school_ids AS
SELECT id FROM school WHERE school_code LIKE 'SEED-%';

CREATE TEMP TABLE seed_user_ids AS
SELECT user_id AS id FROM student
 WHERE school_id IN (SELECT id FROM seed_school_ids) AND user_id IS NOT NULL
UNION
SELECT user_id AS id FROM teacher
 WHERE school_id IN (SELECT id FROM seed_school_ids) AND user_id IS NOT NULL;

DELETE FROM score          WHERE school_id IN (SELECT id FROM seed_school_ids);
DELETE FROM score_statistics WHERE exam_id IN
       (SELECT id FROM exam WHERE school_id IN (SELECT id FROM seed_school_ids));
DELETE FROM exam_subject   WHERE exam_id IN
       (SELECT id FROM exam WHERE school_id IN (SELECT id FROM seed_school_ids));
DELETE FROM exam           WHERE school_id IN (SELECT id FROM seed_school_ids);
DELETE FROM class_student  WHERE student_id IN
       (SELECT id FROM student WHERE school_id IN (SELECT id FROM seed_school_ids));
DELETE FROM teacher_class  WHERE teacher_id IN
       (SELECT id FROM teacher WHERE school_id IN (SELECT id FROM seed_school_ids));
DELETE FROM sys_user_role  WHERE user_id IN (SELECT id FROM seed_user_ids);
DELETE FROM sys_user       WHERE id IN (SELECT id FROM seed_user_ids);
DELETE FROM student        WHERE school_id IN (SELECT id FROM seed_school_ids);
DELETE FROM teacher        WHERE school_id IN (SELECT id FROM seed_school_ids);
DELETE FROM class          WHERE school_id IN (SELECT id FROM seed_school_ids);
DELETE FROM grade          WHERE school_id IN (SELECT id FROM seed_school_ids);
DELETE FROM subject        WHERE school_id IN (SELECT id FROM seed_school_ids);
DELETE FROM academic_term  WHERE school_id IN (SELECT id FROM seed_school_ids);
DELETE FROM school         WHERE id IN (SELECT id FROM seed_school_ids);

DROP TABLE seed_school_ids;
DROP TABLE seed_user_ids;

-- ---------- 1. 学校 (5) ----------
INSERT INTO school
  (school_code, school_name, school_type, province, city, district,
   address, contact_person, contact_phone, status)
SELECT
  'SEED-S' || s,
  (ARRAY['阳光第一小学','实验第二小学','育才第三小学','东城第四小学','西湖第五小学'])[s],
  (ARRAY['PUBLIC','PUBLIC','PRIVATE','PUBLIC','PRIVATE'])[s],
  (ARRAY['浙江省','浙江省','江苏省','北京市','浙江省'])[s],
  (ARRAY['杭州市','杭州市','南京市','北京市','杭州市'])[s],
  (ARRAY['西湖区','滨江区','鼓楼区','东城区','余杭区'])[s],
  (ARRAY['文一西路 1 号','江南大道 22 号','中山路 333 号','东单大街 44 号','未来科技城 5 号'])[s],
  (ARRAY['张校长','李校长','王校长','刘校长','陈校长'])[s],
  '0571-8800' || lpad(s::text, 4, '0'),
  1
FROM generate_series(1, 5) s;

-- ---------- 2. 年级 (5 × 6 = 30) ----------
INSERT INTO grade (school_id, grade_name, grade_code)
SELECT sc.id,
       (ARRAY['一年级','二年级','三年级','四年级','五年级','六年级'])[g],
       g
FROM school sc
CROSS JOIN generate_series(1, 6) g
WHERE sc.school_code LIKE 'SEED-%';

-- ---------- 3. 学期 (5 × 4 = 20) ----------
-- ponytail: 最后一个学期(2025-2026 下学期) status=1 当作"当前学期",
-- 虽然 end_date 已过期。这样 8 次考试全部落在过去、成绩才自洽。
-- 若必须有一个日期真正进行中的学期, 单独插一个第 5 学期即可。
INSERT INTO academic_term (school_id, year, term, start_date, end_date, status)
SELECT sc.id,
       (ARRAY['2024-2025','2024-2025','2025-2026','2025-2026'])[t],
       (ARRAY[1, 2, 1, 2])[t],
       (ARRAY['2024-09-01','2025-02-20','2025-09-01','2026-02-20'])[t]::date,
       (ARRAY['2025-01-15','2025-07-05','2026-01-15','2026-07-05'])[t]::date,
       CASE WHEN t = 4 THEN 1 ELSE 0 END
FROM school sc
CROSS JOIN generate_series(1, 4) t
WHERE sc.school_code LIKE 'SEED-%';

-- ---------- 4. 科目 (5 × 5 = 25) ----------
INSERT INTO subject (school_id, subject_name, subject_code, full_score, pass_score)
SELECT sc.id,
       (ARRAY['语文','数学','英语','物理','化学'])[n],
       (ARRAY['CHN','MAT','ENG','PHY','CHE'])[n],
       100, 60
FROM school sc
CROSS JOIN generate_series(1, 5) n
WHERE sc.school_code LIKE 'SEED-%';

-- @@SEED_CONT@@

COMMIT;
