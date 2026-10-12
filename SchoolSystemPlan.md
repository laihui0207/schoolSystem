# 学生成绩管理系统 - 项目计划书

## 一、项目概述

### 1.1 项目背景

为满足区域级教育管理平台对学生成绩数据的集中管理、多维度分析和智能化决策支持的需求，建设一套基于 Java + Spring Boot 的学生成绩管理系统。系统面向多校联合场景，支持高并发访问与分布式部署。

### 1.2 项目目标

- 构建覆盖多学校、多年级、多班级的成绩数据管理平台
- 支持管理员、教师、学生、家长四类角色的差异化权限访问
- 提供成绩录入、查询、统计分析、数据导出等核心功能
- 支持区域级数据汇总与横向对比分析
- 系统具备高可用性、高并发处理能力和良好的可扩展性

### 1.3 用户角色

| 角色 | 权限范围 | 核心操作 |
|------|----------|----------|
| 超级管理员 | 全平台 | 学校管理、系统配置、用户管理、数据备份 |
| 学校管理员 | 本校 | 年级/班级管理、教师分配、数据导入导出 |
| 教师 | 所授班级 | 成绩录入/修改、班级成绩分析、学生评价 |
| 学生 | 本人 | 查看个人成绩、成绩趋势、排名信息 |
| 家长 | 关联学生 | 查看孩子成绩、接收成绩通知 |

---

## 二、功能模块设计

### 2.1 模块总览

```
学生成绩管理系统
├── 用户与权限模块
│   ├── 注册/登录（支持手机号+验证码）
│   ├── 角色与权限管理（RBAC）
│   ├── 数据权限隔离（行级安全）
│   └── 操作日志审计
├── 基础数据模块
│   ├── 学校管理（多校入驻）
│   ├── 学年/学期管理
│   ├── 年级管理
│   ├── 班级管理
│   ├── 科目管理
│   └── 学生/教师档案管理
├── 成绩管理模块
│   ├── 考试管理（创建考试、关联科目和班级）
│   ├── 成绩录入（手动录入 + Excel批量导入）
│   ├── 成绩审核（录入后审核流程）
│   ├── 成绩修改（带审批的修改流程）
│   └── 成绩发布（按考试/班级发布）
├── 统计分析模块
│   ├── 个人成绩分析（趋势图、雷达图）
│   ├── 班级成绩分析（均分、及格率、优秀率、分数段分布）
│   ├── 年级成绩分析（班级横向对比）
│   ├── 校级成绩分析（年级纵向对比）
│   ├── 区域成绩分析（跨校对比）
│   └── 学科质量分析（各科难度、区分度）
├── 报表导出模块
│   ├── 成绩单生成（PDF）
│   ├── 统计报表导出（Excel）
│   ├── 分析报告导出（Word/PDF）
│   └── 自定义报表模板
├── 通知模块
│   ├── 成绩发布通知
│   ├── 成绩预警通知
│   └── 站内消息中心
└── 系统管理模块
    ├── 系统参数配置
    ├── 数据字典管理
    ├── 定时任务管理
    └── 数据备份与恢复
```

### 2.2 核心功能详细说明

#### 2.2.1 成绩录入

- **手动录入**：教师选择考试 → 选择班级 → 逐科录入，支持 Tab 键快速切换
- **批量导入**：上传 Excel 模板，系统自动校验数据格式和逻辑（如分数范围），校验通过后入库
- **成绩审核**：录入后默认"待审核"状态，教研组长或学校管理员审核通过后才可发布
- **修改流程**：已发布成绩修改需提交申请 → 审批通过 → 修改 → 记录变更日志

#### 2.2.2 统计分析

- **多维度聚合**：支持按学生、班级、年级、学校、区域、科目、考试等维度聚合
- **对比分析**：同班级不同考试纵向对比、同考试不同班级横向对比
- **排名体系**：班级排名、年级排名、校级排名（可按科目和总分）
- **预警机制**：成绩大幅下降自动预警，支持自定义预警阈值

---

## 三、技术架构设计

### 3.1 整体架构

```
                        ┌──────────────┐
                        │   Nginx      │
                        │  (负载均衡)   │
                        └──────┬───────┘
                               │
                ┌──────────────┼──────────────┐
                │              │              │
        ┌───────┴──────┐ ┌────┴───────┐ ┌────┴───────┐
        │  Gateway     │ │  Gateway   │ │  Gateway   │
        │  (网关实例1)  │ │  (实例2)   │ │  (实例3)   │
        └───────┬──────┘ └────┬───────┘ └────┬───────┘
                │              │              │
        ────────┴──────────────┴──────────────┴──────── 服务层
                │              │              │
   ┌────────────┼─────────────┼──────────────┼────────────┐
   │            │             │              │            │
┌──┴─────┐ ┌───┴────┐ ┌─────┴─────┐ ┌─────┴─────┐ ┌───┴────┐
│用户服务 │ │基础数据 │ │ 成绩服务   │ │ 统计服务   │ │通知服务│
│user-svc│ │base-svc│ │ grade-svc │ │ stat-svc  │ │msg-svc │
└──┬─────┘ └───┬────┘ └─────┬─────┘ └─────┬─────┘ └───┬────┘
   │            │             │              │            │
   └────────────┴─────────────┴──────────────┴────────────┘
                │              │              │
        ────────┴──────────────┴──────────────┴──────── 数据层
                │              │              │
        ┌───────┴──────┐ ┌────┴───────┐ ┌────┴───────┐
        │   MySQL      │ │   Redis    │ │   ES       │
        │  (主从集群)   │ │  (缓存集群) │ │ (搜索引擎) │
        └──────────────┘ └────────────┘ └────────────┘
```

### 3.2 技术选型

| 层次 | 技术 | 版本建议 | 用途 |
|------|------|----------|------|
| 语言 | Java | 17+ | 开发语言 |
| 框架 | Spring Boot | 3.x | 应用框架 |
| 微服务 | Spring Cloud Alibaba | 2022.x | 微服务治理 |
| 网关 | Spring Cloud Gateway | 4.x | API 网关 |
| 注册中心 | Nacos | 2.x | 服务注册与配置 |
| ORM | MyBatis-Plus | 3.5+ | 数据持久化 |
| 数据库 | MySQL | 8.0+ | 主数据库 |
| 缓存 | Redis | 7.x | 缓存 + 分布式锁 |
| 搜索 | Elasticsearch | 8.x | 成绩全文检索 |
| 消息队列 | RocketMQ | 5.x | 异步消息（通知、统计） |
| 前端 | Vue 3 + Element Plus | 3.x | 管理后台 |
| 前端 | uni-app | - | 学生/家长移动端 |
| 容器化 | Docker + K8s | - | 部署与编排 |
| CI/CD | Jenkins / GitLab CI | - | 自动化部署 |
| 监控 | Prometheus + Grafana | - | 系统监控 |
| 日志 | ELK (ES + Logstash + Kibana) | - | 日志收集分析 |

### 3.3 关键技术点

1. **分布式事务**：成绩录入涉及多服务调用时，使用 Seata AT 模式保证数据一致性
2. **数据权限**：基于 MyBatis-Plus 拦截器实现行级数据权限，自动注入租户/学校/班级过滤条件
3. **高并发成绩发布**：成绩发布瞬间高并发查询，通过 Redis 多级缓存 + 本地缓存兜底
4. **异步统计**：成绩录入后通过 RocketMQ 异步触发统计计算，避免阻塞主流程
5. **分库分表**：当单表数据量超过 500 万时，按学校 ID 进行分库，按学年分表

---

## 四、数据库设计（核心表）

### 4.1 ER 关系概览

```
学校(School) 1──N 年级(Grade) 1──N 班级(Class) 1──N 学生(Student)
                                    │                  │
教师(Teacher) N──N 班级(Class)       │                  │
                                    │                  │
                              考试(Exam) N──N 科目(Subject)
                                    │
                              成绩(Score) ── 关联学生+考试+科目
```

### 4.2 核心表结构

```sql
-- 学校表
CREATE TABLE school (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    school_code VARCHAR(32) UNIQUE NOT NULL COMMENT '学校编码',
    school_name VARCHAR(100) NOT NULL,
    region_code VARCHAR(20) COMMENT '区域编码',
    address VARCHAR(255),
    contact_phone VARCHAR(20),
    status TINYINT DEFAULT 1 COMMENT '0-禁用 1-启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 用户表（统一用户）
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(200) NOT NULL COMMENT 'BCrypt加密',
    phone VARCHAR(20) UNIQUE,
    real_name VARCHAR(50),
    avatar VARCHAR(500),
    user_type TINYINT NOT NULL COMMENT '1-管理员 2-教师 3-学生 4-家长',
    school_id BIGINT COMMENT '所属学校',
    status TINYINT DEFAULT 1,
    last_login_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 角色表
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(50) UNIQUE NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    school_id BIGINT COMMENT 'NULL表示平台级角色',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 学年学期表
CREATE TABLE academic_term (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    year VARCHAR(20) NOT NULL COMMENT '如 2025-2026',
    term TINYINT NOT NULL COMMENT '1-上学期 2-下学期',
    start_date DATE,
    end_date DATE,
    status TINYINT DEFAULT 1 COMMENT '0-已结束 1-进行中',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 年级表
CREATE TABLE grade (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    grade_name VARCHAR(50) NOT NULL COMMENT '如 高一、高二',
    grade_code INT NOT NULL COMMENT '年级编码，用于排序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 班级表
CREATE TABLE class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    grade_id BIGINT NOT NULL,
    class_name VARCHAR(50) NOT NULL,
    class_code INT NOT NULL,
    term_id BIGINT NOT NULL,
    head_teacher_id BIGINT COMMENT '班主任',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 学生表
CREATE TABLE student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNIQUE COMMENT '关联用户表',
    student_no VARCHAR(30) UNIQUE NOT NULL COMMENT '学号',
    school_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    gender TINYINT COMMENT '1-男 2-女',
    birth_date DATE,
    enrollment_date DATE COMMENT '入学日期',
    status TINYINT DEFAULT 1 COMMENT '0-离校 1-在读 2-休学',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 班级学生关联表
CREATE TABLE class_student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    UNIQUE KEY uk_class_student (class_id, student_id)
);

-- 教师表
CREATE TABLE teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT UNIQUE,
    teacher_no VARCHAR(30) UNIQUE NOT NULL COMMENT '工号',
    school_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    gender TINYINT,
    subject_id BIGINT COMMENT '主教科目',
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 科目表
CREATE TABLE subject (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    subject_name VARCHAR(50) NOT NULL,
    subject_code VARCHAR(20),
    full_score DECIMAL(5,1) DEFAULT 100 COMMENT '满分',
    pass_score DECIMAL(5,1) DEFAULT 60 COMMENT '及格分',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 考试表
CREATE TABLE exam (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    exam_name VARCHAR(100) NOT NULL COMMENT '如 2025年秋季期中考试',
    exam_type TINYINT NOT NULL COMMENT '1-月考 2-期中 3-期末 4-模拟 5-其他',
    exam_date DATE,
    grade_ids VARCHAR(500) COMMENT '参与年级ID列表',
    status TINYINT DEFAULT 0 COMMENT '0-未开始 1-录入中 2-审核中 3-已发布',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 考试-科目关联表
CREATE TABLE exam_subject (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    full_score DECIMAL(5,1) DEFAULT 100,
    sort_order INT DEFAULT 0,
    UNIQUE KEY uk_exam_subject (exam_id, subject_id)
);

-- 成绩表（核心大表）
CREATE TABLE score (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    score DECIMAL(5,1) COMMENT '得分',
    is_absent TINYINT DEFAULT 0 COMMENT '是否缺考',
    rank_in_class INT COMMENT '班级排名',
    rank_in_grade INT COMMENT '年级排名',
    rank_in_school INT COMMENT '校级排名',
    recorder_id BIGINT COMMENT '录入人',
    status TINYINT DEFAULT 0 COMMENT '0-待审核 1-已通过 2-已驳回',
    recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME,
    UNIQUE KEY uk_exam_student_subject (exam_id, student_id, subject_id),
    KEY idx_school_exam (school_id, exam_id),
    KEY idx_class_exam (class_id, exam_id),
    KEY idx_student (student_id)
) COMMENT '成绩表';

-- 成绩变更日志
CREATE TABLE score_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    score_id BIGINT NOT NULL,
    old_score DECIMAL(5,1),
    new_score DECIMAL(5,1),
    change_reason VARCHAR(500),
    operator_id BIGINT NOT NULL,
    approve_status TINYINT COMMENT '0-待审批 1-通过 2-拒绝',
    approver_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    approved_at DATETIME
);

-- 通知消息表
CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    type TINYINT COMMENT '1-成绩发布 2-成绩预警 3-系统通知',
    is_read TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## 五、接口设计（核心 API）

### 5.1 API 规范

- RESTful 风格，统一前缀 `/api/v1`
- 统一响应格式：`{ "code": 200, "message": "success", "data": {} }`
- 认证方式：JWT Token（Access Token + Refresh Token）
- 分页参数：`pageNum`、`pageSize`，返回 `total`、`pages`

### 5.2 核心接口清单

| 模块 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 认证 | `/api/v1/auth/login` | POST | 登录 |
| 认证 | `/api/v1/auth/logout` | POST | 登出 |
| 认证 | `/api/v1/auth/refresh` | POST | 刷新Token |
| 考试 | `/api/v1/exams` | GET/POST | 考试列表/创建 |
| 考试 | `/api/v1/exams/{id}` | GET/PUT | 考试详情/修改 |
| 成绩 | `/api/v1/scores/import` | POST | 批量导入成绩 |
| 成绩 | `/api/v1/scores/batch` | POST | 批量录入成绩 |
| 成绩 | `/api/v1/scores/review` | POST | 成绩审核 |
| 成绩 | `/api/v1/scores/publish` | POST | 成绩发布 |
| 成绩 | `/api/v1/scores/student/{id}` | GET | 学生成绩查询 |
| 成绩 | `/api/v1/scores/exam/{id}/class/{id}` | GET | 班级某次考试成绩 |
| 统计 | `/api/v1/stats/student/{id}/trend` | GET | 学生成绩趋势 |
| 统计 | `/api/v1/stats/class/{id}/exam/{id}` | GET | 班级考试统计 |
| 统计 | `/api/v1/stats/school/{id}/exam/{id}` | GET | 学校考试统计 |
| 统计 | `/api/v1/stats/region/exam/{id}` | GET | 区域考试统计 |
| 导出 | `/api/v1/export/score-sheet` | GET | 导出成绩单 |
| 导出 | `/api/v1/export/stat-report` | GET | 导出统计报表 |

---

## 六、项目里程碑与排期

### 6.1 团队配置（建议）

| 角色 | 人数 | 职责 |
|------|------|------|
| 项目经理 | 1 | 项目管理、进度跟踪、需求协调 |
| 架构师 | 1 | 技术架构设计、技术选型、代码审查 |
| 后端开发 | 3-4 | 微服务开发、接口联调 |
| 前端开发 | 2-3 | Web 管理端 + 移动端开发 |
| 测试工程师 | 2 | 功能测试、性能测试 |
| 运维工程师 | 1 | 环境搭建、CI/CD、监控 |
| UI 设计师 | 1 | 界面设计、交互设计 |

### 6.2 里程碑规划

```
Phase 1: 基础搭建（第1-3周）
├── W1: 需求评审、技术方案评审、开发规范制定
├── W2: 项目骨架搭建、CI/CD 流水线、开发环境搭建
└── W3: 用户/权限模块开发、基础数据模块开发

Phase 2: 核心业务（第4-7周）
├── W4: 考试管理 + 成绩录入（手动）
├── W5: 成绩批量导入 + 审核流程
├── W6: 成绩发布 + 查询 + 修改审批
└── W7: 通知模块 + 数据权限完善

Phase 3: 统计分析（第8-10周）
├── W8: 个人/班级统计 + 图表展示
├── W9: 年级/校级/区域级统计 + 排名
└── W10: 报表导出（PDF/Excel）+ 预警机制

Phase 4: 前端开发（第4-10周，与后端并行）
├── W4-5: 管理后台 - 基础页面 + 成绩录入页
├── W6-7: 管理后台 - 查询/审核页面
├── W8-9: 数据统计可视化页面
└── W10: 移动端（学生/家长端）核心页面

Phase 5: 测试与优化（第11-13周）
├── W11: 功能测试 + Bug 修复
├── W12: 性能测试 + 压调优化
└── W13: 安全测试 + 渗透测试

Phase 6: 上线部署（第14-15周）
├── W14: 预发布环境验证 + UAT
└── W15: 正式上线 + 监控保障
```

**总工期预估：15 周（约 3.5 个月）**

---

## 七、非功能性需求

### 7.1 性能要求

| 指标 | 目标值 |
|------|--------|
| 接口平均响应时间 | < 200ms |
| 成绩批量导入（1000条） | < 5s |
| 统计报表生成 | < 3s |
| 系统并发用户数 | ≥ 5000 |
| 成绩发布峰值 QPS | ≥ 10000 |

### 7.2 可用性要求

- 系统可用性 ≥ 99.9%（全年停机 < 8.76 小时）
- 数据库主从热备，RPO = 0，RTO < 5 分钟
- 服务熔断降级：核心查询接口降级后仍可返回缓存数据

### 7.3 安全要求

- 密码 BCrypt 加密存储，强制密码复杂度
- 接口鉴权 + 数据权限双重隔离
- 敏感操作（成绩修改/删除）全链路审计日志
- 防 SQL 注入、XSS、CSRF 等 OWASP Top 10 防护
- 数据传输 HTTPS 加密

### 7.4 可扩展性

- 微服务架构，各服务独立扩缩容
- 预留多租户扩展能力
- 预留国际化（i18n）接口
- 成绩规则可配置（如等级制/百分制切换）

---

## 八、风险管理

| 风险项 | 可能性 | 影响 | 应对策略 |
|--------|--------|------|----------|
| 成绩发布高并发导致系统崩溃 | 中 | 高 | 多级缓存 + 限流 + 降级预案 |
| 数据量大导致查询变慢 | 高 | 中 | 分库分表 + 读写分离 + ES 辅助查询 |
| 需求变更频繁 | 中 | 中 | 敏捷迭代 + 需求冻结机制 |
| 数据迁移风险（如有旧系统） | 中 | 高 | 双写过渡 + 数据校验脚本 |
| 团队成员变动 | 低 | 高 | 完善文档 + 代码 Review + 知识共享 |

---

## 九、项目目录结构（建议）

```
student-score-system/
├── score-gateway/              # API 网关
├── score-common/               # 公共模块（工具类、常量、DTO）
├── score-auth/                 # 认证授权服务
├── score-user/                 # 用户服务
├── score-base/                 # 基础数据服务（学校/年级/班级/科目）
├── score-grade/                # 成绩管理服务
├── score-stat/                 # 统计分析服务
├── score-notification/         # 通知消息服务
├── score-admin-web/            # 前端管理后台（Vue 3）
├── score-student-app/          # 学生/家长移动端（uni-app）
├── score-docs/                 # 项目文档
│   ├── api/                    # API 文档
│   ├── design/                 # 设计文档
│   └── deploy/                 # 部署文档
├── docker/                     # Docker 编排文件
├── sql/                        # 数据库脚本
└── scripts/                    # 运维脚本
```

---

## 十、下一步行动

1. **确认本计划**：审阅以上各章节，确认功能范围和技术选型是否符合预期
2. **搭建项目骨架**：创建 Maven 多模块项目，配置 Spring Boot + Spring Cloud
3. **初始化数据库**：执行建表脚本，配置数据源
4. **开发第一个模块**：从用户认证模块开始，打通前后端联调
5. **迭代推进**：按里程碑逐步交付各功能模块

