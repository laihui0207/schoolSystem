# 学生成绩管理系统 - 开发计划

**更新时间**: 2026-09-11 12:25  
**当前状态**: ✅ 应用成功启动，Phase 1 基础搭建完成 85%

---

## 📊 当前进度总览

### ✅ 已完成模块 (Phase 1 - 85%)

| 模块 | 完成度 | 文件数 | 状态 |
|------|--------|--------|------|
| 认证授权 (auth) | 100% | 5个 | ✅ 完成 |
| 用户管理 (user) | 100% | 4个 | ✅ 完成 |
| 学校管理 (school) | 100% | 9个 | ✅ 完成 |
| 学期管理 (term) | 100% | 9个 | ✅ 完成 |
| 科目管理 (subject) | 100% | 9个 | ✅ 完成 |
| 年级管理 (grade) | 100% | 9个 | ✅ 完成 |

**技术基础设施**:
- ✅ Spring Boot 3.2.0 + MyBatis-Plus
- ✅ PostgreSQL 16 + Redis 7 (Docker运行中)
- ✅ Flyway数据库迁移 (5个脚本已执行)
- ✅ JWT认证 + Spring Security
- ✅ Swagger/Knife4j API文档
- ✅ 统一响应封装 + 全局异常处理

---

## 🚧 待开发模块

### Phase 1 剩余 (本周完成)

#### 1. 班级管理模块 (class) - 优先级: ⭐⭐⭐
**预估时间**: 4小时

**文件结构**:
```
src/main/java/com/school/module/class/
├── entity/Class.java
├── dto/
│   ├── ClassCreateRequest.java
│   ├── ClassUpdateRequest.java
│   ├── ClassQueryRequest.java
│   └── ClassResponse.java
├── mapper/ClassMapper.java
├── service/
│   ├── ClassService.java
│   └── ClassServiceImpl.java
└── controller/ClassController.java
```

**功能清单**:
- [ ] 创建班级（关联学校、年级、学期）
- [ ] 更新班级信息
- [ ] 查询班级详情
- [ ] 分页查询班级列表（支持按学校、年级筛选）
- [ ] 设置班主任
- [ ] 班级状态管理

**Mapper XML**:
```
src/main/resources/mapper/class/ClassMapper.xml
```

#### 2. 学生管理模块 (student) - 优先级: ⭐⭐⭐
**预估时间**: 5小时

**文件结构**:
```
src/main/java/com/school/module/student/
├── entity/Student.java
├── dto/
│   ├── StudentCreateRequest.java
│   ├── StudentUpdateRequest.java
│   ├── StudentQueryRequest.java
│   ├── StudentResponse.java
│   └── StudentImportRequest.java
├── mapper/StudentMapper.java
├── service/
│   ├── StudentService.java
│   └── StudentServiceImpl.java
└── controller/StudentController.java
```

**功能清单**:
- [ ] 创建学生档案
- [ ] 更新学生信息
- [ ] 查询学生详情
- [ ] 分页查询学生列表
- [ ] 学生分班（关联class_student表）
- [ ] 学生转班
- [ ] 学生状态管理（在读、休学、离校）
- [ ] Excel批量导入学生

#### 3. 教师管理模块 (teacher) - 优先级: ⭐⭐
**预估时间**: 4小时

**文件结构**:
```
src/main/java/com/school/module/teacher/
├── entity/Teacher.java
├── dto/
│   ├── TeacherCreateRequest.java
│   ├── TeacherUpdateRequest.java
│   ├── TeacherQueryRequest.java
│   └── TeacherResponse.java
├── mapper/TeacherMapper.java
├── service/
│   ├── TeacherService.java
│   └── TeacherServiceImpl.java
└── controller/TeacherController.java
```

**功能清单**:
- [ ] 创建教师档案
- [ ] 更新教师信息
- [ ] 查询教师详情
- [ ] 分页查询教师列表
- [ ] 设置教师任教科目
- [ ] 教师状态管理

---

### Phase 2: 核心业务 - 成绩管理 (下周开始)

#### 4. 考试管理模块 (exam) - 优先级: ⭐⭐⭐
**预估时间**: 6小时

**功能清单**:
- [ ] 创建考试（关联学期、年级、科目）
- [ ] 考试信息管理
- [ ] 考试状态流转（未开始→录入中→审核中→已发布）
- [ ] 查询考试列表

#### 5. 成绩管理模块 (score) - 优先级: ⭐⭐⭐
**预估时间**: 10小时

**功能清单**:
- [ ] 手动录入成绩（单个/批量）
- [ ] Excel批量导入成绩
- [ ] 成绩审核流程
- [ ] 成绩修改审批
- [ ] 成绩发布
- [ ] 查询学生成绩（个人/班级/年级）
- [ ] 成绩排名计算

#### 6. 成绩统计模块 (stat) - 优先级: ⭐⭐
**预估时间**: 8小时

**功能清单**:
- [ ] 个人成绩分析（趋势、雷达图数据）
- [ ] 班级成绩统计（均分、及格率、优秀率、分数段）
- [ ] 年级成绩统计
- [ ] 校级成绩统计
- [ ] 横向/纵向对比分析

---

### Phase 3: 增强功能 (后续迭代)

#### 7. 通知模块 (notification)
- [ ] 成绩发布通知
- [ ] 成绩预警通知
- [ ] 站内消息中心

#### 8. 报表导出模块 (export)
- [ ] 成绩单PDF导出
- [ ] 统计报表Excel导出
- [ ] 自定义模板

#### 9. 系统管理模块 (system)
- [ ] 数据字典管理
- [ ] 操作日志查询
- [ ] 系统参数配置

---

## 📝 下一步行动 (立即执行)

### 1. 提交当前代码 ⭐
```bash
git add .
git commit -m "feat: 完成学校、学期、科目、年级管理模块并修复应用启动问题"
git push origin master
```

### 2. 开发班级管理模块 (4小时)
**任务清单**:
- [ ] 创建Class实体类
- [ ] 创建DTO类（4个）
- [ ] 创建ClassMapper接口和XML
- [ ] 实现ClassService
- [ ] 实现ClassController
- [ ] 编写单元测试

### 3. 开发学生管理模块 (5小时)
**任务清单**:
- [ ] 创建Student实体类
- [ ] 创建DTO类（5个）
- [ ] 创建StudentMapper接口和XML
- [ ] 实现StudentService（包含分班逻辑）
- [ ] 实现StudentController
- [ ] 实现Excel导入功能
- [ ] 编写单元测试

### 4. 开发教师管理模块 (4小时)
**任务清单**:
- [ ] 创建Teacher实体类
- [ ] 创建DTO类（4个）
- [ ] 创建TeacherMapper接口和XML
- [ ] 实现TeacherService
- [ ] 实现TeacherController
- [ ] 编写单元测试

---

## 🎯 本周目标

完成 **Phase 1: 基础数据模块** 100%：
- [x] 认证授权模块
- [x] 用户管理模块
- [x] 学校管理模块
- [x] 学期管理模块
- [x] 科目管理模块
- [x] 年级管理模块
- [ ] 班级管理模块 ⬅️ 当前任务
- [ ] 学生管理模块
- [ ] 教师管理模块

---

## 📂 数据库表映射

| 实体模块 | 数据库表 | 状态 |
|----------|----------|------|
| School | school | ✅ 已实现 |
| AcademicTerm | academic_term | ✅ 已实现 |
| Subject | subject | ✅ 已实现 |
| Grade | grade | ✅ 已实现 |
| Class | class | ⏳ 待实现 |
| Student | student | ⏳ 待实现 |
| ClassStudent | class_student | ⏳ 待实现 |
| Teacher | teacher | ⏳ 待实现 |
| Exam | exam | ⏳ 待实现 |
| ExamSubject | exam_subject | ⏳ 待实现 |
| Score | score | ⏳ 待实现 |

---

## 🔧 技术债务

1. **API文档访问** - Swagger被Security拦截，需要配置白名单
2. **测试覆盖率** - 当前约60%，目标80%
3. **代码注释** - 部分Service方法缺少详细注释
4. **日志优化** - 需要添加业务操作日志

---

## 📈 工作量估算

| Phase | 模块数 | 预估工时 | 完成情况 |
|-------|--------|----------|----------|
| Phase 1 | 9个 | 40小时 | 85% (6/9) |
| Phase 2 | 3个 | 24小时 | 0% (0/3) |
| Phase 3 | 3个 | 16小时 | 0% (0/3) |
| **总计** | **15个** | **80小时** | **40%** |

---

## 🚀 快速命令

```bash
# 启动应用
mvn spring-boot:run -DskipTests

# 运行测试
mvn test

# 查看测试覆盖率
mvn clean test jacoco:report
open target/site/jacoco/index.html

# 查看API文档
# 浏览器访问: http://localhost:8080/api/v1/doc.html

# 查看数据库
docker exec -it school-postgres psql -U school_admin -d school_system

# 查看Flyway迁移状态
mvn flyway:info
```

---

**下一步**: 立即开始开发**班级管理模块**
