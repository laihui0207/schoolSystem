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
-- ponytail: 5 科按校级建一次, 全年级共用 => 一年级也有物理/化学, 不符合真实学制。
-- 需求是"每个学生学习 5 门课程", 满足即可。要按年级区分开课,
-- 给 subject 加 grade_code 列并在此按年级过滤。
INSERT INTO subject (school_id, subject_name, subject_code, full_score, pass_score)
SELECT sc.id,
       (ARRAY['语文','数学','英语','物理','化学'])[n],
       (ARRAY['CHN','MAT','ENG','PHY','CHE'])[n],
       100, 60
FROM school sc
CROSS JOIN generate_series(1, 5) n
WHERE sc.school_code LIKE 'SEED-%';

-- ---------- 辅助: 科目/学期索引表 ----------
CREATE TEMP TABLE seed_subject AS
SELECT sb.id AS subject_id, sb.school_id,
       row_number() OVER (PARTITION BY sb.school_id ORDER BY sb.id)::int AS idx
FROM subject sb
JOIN school sc ON sc.id = sb.school_id AND sc.school_code LIKE 'SEED-%';

CREATE TEMP TABLE seed_term AS
SELECT t.id AS term_id, t.school_id, t.start_date,
       row_number() OVER (PARTITION BY t.school_id ORDER BY t.id)::int AS idx
FROM academic_term t
JOIN school sc ON sc.id = t.school_id AND sc.school_code LIKE 'SEED-%';

-- ---------- 5. 班级 (5 × 6 × 15 = 450) ----------
-- ponytail: 班级只挂在当前学期(idx=4), 不为 4 个学期各复制一套 1800 个班。
-- 需要历史学期的班级快照时再按学期展开。
INSERT INTO class (school_id, grade_id, class_name, class_code, term_id)
SELECT g.school_id, g.id, g.grade_name || c || '班', c, st.term_id
FROM grade g
JOIN school sc ON sc.id = g.school_id AND sc.school_code LIKE 'SEED-%'
JOIN seed_term st ON st.school_id = g.school_id AND st.idx = 4
CROSS JOIN generate_series(1, 15) c;

CREATE TEMP TABLE seed_class AS
SELECT c.id AS class_id, c.school_id, c.grade_id, c.class_code,
       g.grade_code,
       row_number() OVER (ORDER BY c.id)::int AS seq
FROM class c
JOIN grade g ON g.id = c.grade_id
JOIN school sc ON sc.id = c.school_id AND sc.school_code LIKE 'SEED-%';

CREATE INDEX ON seed_class (class_id);

-- ---------- 6. 教师 (450 班 × 5 科 = 2250) ----------
-- 每班每科一名教师, 教师不跨班 (符合"每个班级有 5 个老师")
CREATE TEMP TABLE seed_teacher_src AS
SELECT row_number() OVER (ORDER BY cl.seq, sj.idx)::int AS seq,
       cl.class_id, cl.school_id, sj.subject_id, sj.idx AS subject_idx
FROM seed_class cl
JOIN seed_subject sj ON sj.school_id = cl.school_id;

INSERT INTO teacher (teacher_no, school_id, name, gender, subject_id, status)
SELECT 'SEED-T' || lpad(seq::text, 6, '0'),
       school_id,
       (ARRAY['张','王','李','赵','刘','陈','杨','黄','周','吴']
          )[1 + (seq * 7) % 10]
       || (ARRAY['伟','敏','静','丽','强','磊','洋','勇','艳','杰']
          )[1 + (seq * 3) % 10]
       || '老师',
       1 + (seq % 2),
       subject_id,
       1
FROM seed_teacher_src;

-- 密码统一为 123456 的 BCrypt 哈希（cost=10，明文 123456，固定盐）。
-- 所有 seed 账号共用同一个字面量而非 gen_salt，避免逐行 bcrypt 导致 INSERT 耗时数分钟。
-- 若要改口令: 用 Spring 的 BCryptPasswordEncoder.encode("新口令") 生成后替换这两处字面量。
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM sys_user WHERE username = 'seed-t000001'
      AND password = '$2a$10$2UKn3XcdjXDl7uAkFT9GWel6pOe0RdAw9Bj5PKGKD/hwaU1OOIe32'
  ) THEN
    RAISE NOTICE '警告: seed 账号口令哈希与预期不符，请检查 V9__Fix_Admin_Password.sql 是否覆盖了此值';
  END IF;
END $$;

INSERT INTO sys_user (username, password, phone, real_name, user_type, school_id, status)
SELECT lower(t.teacher_no),
       '$2a$10$2UKn3XcdjXDl7uAkFT9GWel6pOe0RdAw9Bj5PKGKD/hwaU1OOIe32',
       '17' || lpad(s.seq::text, 9, '0'), t.name, 3, t.school_id, 1
FROM teacher t
JOIN seed_teacher_src s ON 'SEED-T' || lpad(s.seq::text, 6, '0') = t.teacher_no;

UPDATE teacher t SET user_id = u.id
FROM sys_user u
WHERE u.username = lower(t.teacher_no) AND t.teacher_no LIKE 'SEED-T%';

INSERT INTO sys_user_role (user_id, role_id)
SELECT t.user_id, r.id
FROM teacher t, sys_role r
WHERE t.teacher_no LIKE 'SEED-T%' AND r.role_code = 'ROLE_TEACHER'
  AND t.user_id IS NOT NULL;

INSERT INTO teacher_class (teacher_id, class_id, subject_id)
SELECT t.id, s.class_id, s.subject_id
FROM seed_teacher_src s
JOIN teacher t ON t.teacher_no = 'SEED-T' || lpad(s.seq::text, 6, '0');

-- 班主任 = 该班语文老师 (subject_idx = 1)
UPDATE class c SET head_teacher_id = t.id
FROM seed_teacher_src s
JOIN teacher t ON t.teacher_no = 'SEED-T' || lpad(s.seq::text, 6, '0')
WHERE s.class_id = c.id AND s.subject_idx = 1;

-- ---------- 7. 学生 (450 班 × 40 = 18000) ----------
CREATE TEMP TABLE seed_student_src AS
SELECT row_number() OVER (ORDER BY cl.seq, n)::int AS seq,
       cl.class_id, cl.school_id, cl.grade_code, n AS no_in_class
FROM seed_class cl
CROSS JOIN generate_series(1, 40) n;

INSERT INTO student
  (student_no, school_id, name, gender, birth_date, enrollment_date, status)
SELECT 'SEED-2' || lpad(seq::text, 7, '0'),
       school_id,
       (ARRAY['张','王','李','赵','刘','陈','杨','黄','周','吴',
              '徐','孙','马','朱','胡','郭','何','高','林','罗']
          )[1 + (seq * 11) % 20]
       || (ARRAY['小明','小红','雨欣','子轩','梓涵','浩然','思远','嘉怡',
                 '一诺','宇轩','欣怡','俊杰','雨泽','诗涵','明轩','佳琪',
                 '博文','若曦','天赐','梦洁']
          )[1 + (seq * 13) % 20],
       1 + (seq % 2),
       -- 一年级约 7 岁入学, 生日按年级回推
       (date '2019-09-01' - ((grade_code - 1) * 365 + (seq % 365)) * interval '1 day')::date,
       (date '2025-09-01' - (grade_code - 1) * interval '1 year')::date,
       1
FROM seed_student_src;

INSERT INTO sys_user (username, password, phone, real_name, user_type, school_id, status)
SELECT lower(st.student_no),
       '$2a$10$2UKn3XcdjXDl7uAkFT9GWel6pOe0RdAw9Bj5PKGKD/hwaU1OOIe32',
       '18' || lpad(s.seq::text, 9, '0'), st.name, 4, st.school_id, 1
FROM student st
JOIN seed_student_src s ON 'SEED-2' || lpad(s.seq::text, 7, '0') = st.student_no;

UPDATE student st SET user_id = u.id
FROM sys_user u
WHERE u.username = lower(st.student_no) AND st.student_no LIKE 'SEED-2%';

INSERT INTO sys_user_role (user_id, role_id)
SELECT st.user_id, r.id
FROM student st, sys_role r
WHERE st.student_no LIKE 'SEED-2%' AND r.role_code = 'ROLE_STUDENT'
  AND st.user_id IS NOT NULL;

INSERT INTO class_student (class_id, student_id)
SELECT s.class_id, st.id
FROM seed_student_src s
JOIN student st ON st.student_no = 'SEED-2' || lpad(s.seq::text, 7, '0');

-- 学生索引表: 带上"学生能力值", 用于生成有区分度的成绩
CREATE TEMP TABLE seed_student AS
SELECT st.id AS student_id, s.class_id, s.school_id, s.seq,
       -- 能力值 ~ N(0,1) 的近似 (Irwin-Hall, 3 项求和), 决定该生长期水平
       -- 取值约 [-1, 1], 中间密集两端稀疏
       (((s.seq * 37) % 100 + (s.seq * 61) % 100 + (s.seq * 17) % 100)
         / 150.0 - 1.0)::numeric AS ability
FROM seed_student_src s
JOIN student st ON st.student_no = 'SEED-2' || lpad(s.seq::text, 7, '0');

CREATE INDEX ON seed_student (class_id);

-- ---------- 8. 考试 (5 校 × 4 学期 × 2 = 40) ----------
-- exam_type: 2-期中 3-期末; status=3 已发布
INSERT INTO exam (school_id, term_id, exam_name, exam_type, exam_date, grade_ids, status)
SELECT t.school_id,
       t.term_id,
       at.year || CASE at.term WHEN 1 THEN '学年第一学期' ELSE '学年第二学期' END
                || CASE k WHEN 1 THEN '期中考试' ELSE '期末考试' END,
       k + 1,
       (t.start_date + CASE k WHEN 1 THEN 70 ELSE 130 END * interval '1 day')::date,
       (SELECT '[' || string_agg(g.id::text, ',' ORDER BY g.grade_code) || ']'
          FROM grade g WHERE g.school_id = t.school_id),
       3
FROM seed_term t
JOIN academic_term at ON at.id = t.term_id
CROSS JOIN generate_series(1, 2) k;

CREATE TEMP TABLE seed_exam AS
SELECT e.id AS exam_id, e.school_id, e.term_id, e.exam_type, e.exam_date,
       row_number() OVER (PARTITION BY e.school_id ORDER BY e.exam_date)::int AS idx
FROM exam e
JOIN school sc ON sc.id = e.school_id AND sc.school_code LIKE 'SEED-%';

-- 考试科目 (40 × 5 = 200)
INSERT INTO exam_subject (exam_id, subject_id, full_score, sort_order)
SELECT e.exam_id, sj.subject_id, 100, sj.idx
FROM seed_exam e
JOIN seed_subject sj ON sj.school_id = e.school_id;

-- ---------- 9. 成绩 (18000 生 × 5 科 × 8 次 = 720000) ----------
-- 分数模型: 基线 72 + 学生能力(±14) + 科目难度 + 考试进步趋势 + 随机波动
-- 期末(exam_type=3) 比期中略高, 学期越靠后整体略有进步。
-- ponytail: 单条 INSERT ... SELECT 一次写完 72 万行, 不分批。
-- 实测数十秒级; 若内存吃紧再按 seed_exam.idx 拆成 8 批。
INSERT INTO score
  (exam_id, student_id, subject_id, class_id, school_id, score, is_absent,
   recorder_id, status, recorded_at, reviewed_at)
SELECT
  e.exam_id,
  s.student_id,
  sj.subject_id,
  s.class_id,
  s.school_id,
  CASE WHEN absent.flag THEN NULL ELSE
    round(
      least(100, greatest(0,
          71                                        -- 基线
        + s.ability * 26                            -- 学生长期水平 (±26)
        + (ARRAY[3, -4, 1, -6, -1])[sj.idx]         -- 科目难度
        + (e.idx - 1) * 0.6                         -- 逐次进步
        + CASE e.exam_type WHEN 3 THEN 1.5 ELSE 0 END
        + (random() + random() + random() - 1.5) * 13  -- 单次波动(近似正态)
      ))::numeric, 1)
  END,
  CASE WHEN absent.flag THEN 1 ELSE 0 END,
  tc.teacher_id,
  1,
  e.exam_date + time '18:00',
  e.exam_date + interval '1 day' + time '10:00'
FROM seed_student s
JOIN seed_exam e   ON e.school_id = s.school_id
JOIN seed_subject sj ON sj.school_id = s.school_id
JOIN teacher_class tc ON tc.class_id = s.class_id AND tc.subject_id = sj.subject_id
CROSS JOIN LATERAL (SELECT random() < 0.002 AS flag) absent;

-- ---------- 10. 排名 (班 / 年级 / 校, 按 考试+科目 分区) ----------
-- ponytail: 一次性全表 UPDATE 72 万行, 约 4~5 分钟(堆重写是主要成本)。
-- 只跑一次可以接受; 若要反复重算排名, 改成按 exam_id 分批 UPDATE。
WITH ranked AS (
  SELECT sc.id,
         rank() OVER (PARTITION BY sc.exam_id, sc.subject_id, sc.class_id
                      ORDER BY sc.score DESC NULLS LAST) AS r_class,
         rank() OVER (PARTITION BY sc.exam_id, sc.subject_id, c.grade_id
                      ORDER BY sc.score DESC NULLS LAST) AS r_grade,
         rank() OVER (PARTITION BY sc.exam_id, sc.subject_id, sc.school_id
                      ORDER BY sc.score DESC NULLS LAST) AS r_school
  FROM score sc
  JOIN class c ON c.id = sc.class_id
  WHERE sc.exam_id IN (SELECT exam_id FROM seed_exam)
)
UPDATE score sc
SET rank_in_class = r.r_class,
    rank_in_grade = r.r_grade,
    rank_in_school = r.r_school
FROM ranked r
WHERE r.id = sc.id AND sc.is_absent = 0;

ANALYZE score;

COMMIT;

-- ============================================================
-- 校验: 每行 expected 与 actual 必须一致
-- ============================================================
\echo '=== 数据量校验 ==='
SELECT 'school' AS t, 5 AS expected, count(*) AS actual FROM school WHERE school_code LIKE 'SEED-%'
UNION ALL SELECT 'grade', 30, count(*) FROM grade g
  WHERE g.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
UNION ALL SELECT 'academic_term', 20, count(*) FROM academic_term t
  WHERE t.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
UNION ALL SELECT 'subject', 25, count(*) FROM subject s
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
UNION ALL SELECT 'class', 450, count(*) FROM class c
  WHERE c.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
UNION ALL SELECT 'teacher', 2250, count(*) FROM teacher WHERE teacher_no LIKE 'SEED-T%'
UNION ALL SELECT 'teacher_class', 2250, count(*) FROM teacher_class tc
  WHERE tc.teacher_id IN (SELECT id FROM teacher WHERE teacher_no LIKE 'SEED-T%')
UNION ALL SELECT 'student', 18000, count(*) FROM student WHERE student_no LIKE 'SEED-2%'
UNION ALL SELECT 'class_student', 18000, count(*) FROM class_student cs
  WHERE cs.student_id IN (SELECT id FROM student WHERE student_no LIKE 'SEED-2%')
UNION ALL SELECT 'exam', 40, count(*) FROM exam e
  WHERE e.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
UNION ALL SELECT 'exam_subject', 200, count(*) FROM exam_subject es
  WHERE es.exam_id IN (SELECT id FROM exam WHERE school_id IN
        (SELECT id FROM school WHERE school_code LIKE 'SEED-%'))
UNION ALL SELECT 'score', 720000, count(*) FROM score s
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
UNION ALL SELECT 'sys_user(seed)', 20250, count(*) FROM sys_user WHERE username LIKE 'seed-%';

\echo '=== 不变量校验 (全部应为 0) ==='
SELECT 'class_without_40_students' AS check, count(*) AS violations FROM (
  SELECT cs.class_id FROM class_student cs
  JOIN class c ON c.id = cs.class_id
  WHERE c.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
  GROUP BY cs.class_id HAVING count(*) <> 40) x
UNION ALL
SELECT 'class_without_5_teachers', count(*) FROM (
  SELECT tc.class_id FROM teacher_class tc
  JOIN class c ON c.id = tc.class_id
  WHERE c.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
  GROUP BY tc.class_id HAVING count(DISTINCT tc.subject_id) <> 5) x
UNION ALL
SELECT 'student_without_40_scores', count(*) FROM (
  SELECT s.student_id FROM score s
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
  GROUP BY s.student_id HAVING count(*) <> 40) x
UNION ALL
SELECT 'score_out_of_range', count(*) FROM score s
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
    AND (s.score < 0 OR s.score > 100)
UNION ALL
SELECT 'orphan_score_class', count(*) FROM score s
  LEFT JOIN class c ON c.id = s.class_id
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
    AND c.id IS NULL
UNION ALL
SELECT 'null_score_not_absent', count(*) FROM score s
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
    AND s.score IS NULL AND s.is_absent = 0;

\echo '=== 分布合理性校验 (全部应为 0) ==='
-- 及格率若接近 100% 则统计类功能(及格率/优秀率/分数段)无法测试
SELECT 'pass_rate_too_high(>97%)' AS check, count(*) AS violations FROM (
  SELECT s.subject_id FROM score s
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
    AND s.score IS NOT NULL
  GROUP BY s.subject_id
  HAVING 100.0 * count(*) FILTER (WHERE s.score >= 60) / count(*) > 97) x
UNION ALL
SELECT 'no_failing_students', count(*) FROM (SELECT 1 WHERE NOT EXISTS (
  SELECT 1 FROM score s
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
    AND s.score < 60)) x
UNION ALL
SELECT 'no_top_students(>=95)', count(*) FROM (SELECT 1 WHERE NOT EXISTS (
  SELECT 1 FROM score s
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
    AND s.score >= 95)) x
UNION ALL
SELECT 'rank_missing', count(*) FROM score s
  WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
    AND s.is_absent = 0 AND s.rank_in_class IS NULL;

\echo '=== 分数分布抽样 ==='
SELECT sb.subject_name,
       count(*) AS n,
       round(avg(s.score), 2) AS avg,
       min(s.score) AS min, max(s.score) AS max,
       round(100.0 * count(*) FILTER (WHERE s.score >= 60) / count(*), 2) AS pass_rate
FROM score s
JOIN subject sb ON sb.id = s.subject_id
WHERE s.school_id IN (SELECT id FROM school WHERE school_code LIKE 'SEED-%')
  AND s.score IS NOT NULL
GROUP BY sb.subject_name ORDER BY avg DESC;
