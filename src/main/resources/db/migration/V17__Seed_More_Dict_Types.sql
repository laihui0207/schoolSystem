-- V17: 补充常用字典类型（性别 / 学生状态 / 家长关系 / 地区示例）
-- 全部用 INSERT ... SELECT ... WHERE NOT EXISTS 保证幂等；已被管理员软删除（deleted=1）的行不会被重新插入。
-- 取值与 ai/db-schema.md 一致：student.gender 1=男,2=女；student.status 0=离校,1=在读,2=休学。

-- ---------- 字典类型 ----------
INSERT INTO sys_dict_type (dict_name, dict_type, status, remark)
SELECT '性别', 'gender', 0, '学生/教师性别：1-男，2-女'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'gender');

INSERT INTO sys_dict_type (dict_name, dict_type, status, remark)
SELECT '学生状态', 'student_status', 0, '学籍状态：0-离校，1-在读，2-休学'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'student_status');

INSERT INTO sys_dict_type (dict_name, dict_type, status, remark)
SELECT '家长关系', 'relationship', 0, '家长与学生的关系，供家长绑定（parent_student.relationship）使用'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'relationship');

INSERT INTO sys_dict_type (dict_name, dict_type, status, remark)
SELECT '地区', 'region', 0, '行政区划：dict_value 为区划代码，dict_label 为名称（示例数据）'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'region');

-- ---------- 字典数据：gender ----------
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 1, '男', '1', 'gender', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'gender' AND dict_value = '1');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 2, '女', '2', 'gender', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'gender' AND dict_value = '2');

-- ---------- 字典数据：student_status ----------
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 1, '在读', '1', 'student_status', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'student_status' AND dict_value = '1');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 2, '休学', '2', 'student_status', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'student_status' AND dict_value = '2');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 3, '离校', '0', 'student_status', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'student_status' AND dict_value = '0');

-- ---------- 字典数据：relationship ----------
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 1, '父亲', 'father', 'relationship', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'relationship' AND dict_value = 'father');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 2, '母亲', 'mother', 'relationship', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'relationship' AND dict_value = 'mother');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 3, '其他', 'other', 'relationship', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'relationship' AND dict_value = 'other');

-- ---------- 字典数据：region（示例，按需在字典管理页补充） ----------
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 1, '深圳市', '440300', 'region', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'region' AND dict_value = '440300');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, status)
SELECT 2, '广州市', '440100', 'region', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'region' AND dict_value = '440100');
