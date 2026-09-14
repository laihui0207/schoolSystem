# 学生成绩管理系统

基于Spring Boot的学生成绩管理系统，支持多学校、多年级、多班级的成绩管理和统计分析。

## 技术栈

### 后端
- Java 17
- Spring Boot 3.2.0
- MyBatis-Plus 3.5.5
- PostgreSQL 16
- Redis 7
- Flyway（数据库版本管理）
- JWT（认证）
- Knife4j（API文档）

### 前端
- Vue 3 + Element Plus（管理后台）
- uni-app（移动端）

### 工具
- Maven 3.8+
- Docker & Docker Compose
- Git

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- Node.js 18+（前端开发）

### 2. 启动数据库服务

```bash
cd docker
docker-compose up -d
```

查看服务状态：
```bash
docker-compose ps
```

数据库连接信息：
- PostgreSQL: `localhost:5432`
- 数据库名: `school_system`
- 用户名: `school_admin`
- 密码: `school_pass_2024`

Redis连接信息：
- 地址: `localhost:6379`
- 密码: `redis_pass_2024`

### 3. 编译项目

```bash
mvn clean install
```

### 4. 运行应用

```bash
mvn spring-boot:run
```

或者：
```bash
java -jar target/school-system-1.0.0-SNAPSHOT.jar
```

应用启动后访问：
- API文档: http://localhost:8080/api/v1/doc.html
- Swagger UI: http://localhost:8080/api/v1/swagger-ui.html

### 5. 运行测试

```bash
# 运行所有测试
mvn test

# 生成测试覆盖率报告
mvn clean test jacoco:report

# 查看覆盖率报告
open target/site/jacoco/index.html
```

## 数据库版本管理

项目使用Flyway进行数据库版本管理，迁移脚本位于 `src/main/resources/db/migration/`。

### Flyway命令

```bash
# 查看迁移状态
mvn flyway:info

# 执行迁移
mvn flyway:migrate

# 清空数据库（慎用！）
mvn flyway:clean

# 验证迁移
mvn flyway:validate

# 修复迁移记录
mvn flyway:repair
```

### 数据库迁移脚本命名规范

```
V{version}__{description}.sql
```

示例：
- `V1__Init_Base_Tables.sql` - 初始化基础表
- `V2__Init_School_Data_Tables.sql` - 初始化学校数据表
- `V3__Init_Score_Tables.sql` - 初始化成绩表

## 项目结构

```
schoolSystem/
├── docker/                          # Docker配置
│   ├── docker-compose.yml
│   ├── init-scripts/               # 数据库初始化脚本
│   └── README.md
├── src/
│   ├── main/
│   │   ├── java/com/school/
│   │   │   ├── common/             # 公共模块
│   │   │   │   ├── base/           # 基础类
│   │   │   │   ├── constant/       # 常量
│   │   │   │   ├── exception/      # 异常处理
│   │   │   │   └── result/         # 响应结果
│   │   │   ├── config/             # 配置类
│   │   │   ├── security/           # 安全认证
│   │   │   ├── module/             # 业务模块
│   │   │   │   ├── auth/           # 认证模块
│   │   │   │   ├── user/           # 用户管理
│   │   │   │   ├── base/           # 基础数据（学校、班级等）
│   │   │   │   ├── grade/          # 成绩管理
│   │   │   │   ├── stat/           # 统计分析
│   │   │   │   └── notification/   # 通知消息
│   │   │   └── SchoolSystemApplication.java
│   │   └── resources/
│   │       ├── db/migration/       # Flyway迁移脚本
│   │       ├── application.yml     # 主配置文件
│   │       ├── application-dev.yml # 开发环境配置
│   │       └── application-prod.yml # 生产环境配置
│   └── test/                       # 测试代码
├── admin-web/                      # Vue 3管理后台（待创建）
├── student-app/                    # uni-app移动端（待创建）
├── docs/                           # 项目文档
│   └── SchoolSystemPlan.md        # 项目计划书
├── pom.xml
└── README.md
```

## 开发规范

### 代码规范
- 遵循阿里巴巴Java开发规范
- 使用Lombok简化代码
- 所有实体类继承BaseEntity
- 统一使用Result包装响应结果
- 全局异常处理使用BusinessException

### 数据库规范
- 表名使用小写+下划线
- 所有表必须有：id（主键）、created_at、updated_at、deleted
- 使用逻辑删除，不物理删除数据
- 外键关系通过应用层维护

### 接口规范
- RESTful风格
- 统一前缀：`/api/v1`
- 统一响应格式：`Result<T>`
- 分页参数：pageNum、pageSize
- 使用JWT认证

### Git规范
- 主分支：main
- 开发分支：develop
- 功能分支：feature/xxx
- 修复分支：fix/xxx
- 提交信息格式：`<type>: <description>`
  - feat: 新功能
  - fix: 修复bug
  - refactor: 重构
  - docs: 文档
  - test: 测试
  - chore: 其他

## 测试要求

- 单元测试覆盖率 ≥ 80%
- 集成测试覆盖核心业务流程
- 使用JUnit 5 + Mockito
- 测试类命名：`{ClassName}Test`
- 测试方法命名：`should{ExpectedBehavior}_when{StateUnderTest}`

## API文档

启动项目后访问 Knife4j 文档：
- 开发环境：http://localhost:8080/api/v1/doc.html
- 在线调试：支持在线测试所有API接口

## 默认账号

- 用户名：`admin`
- 密码：`admin123`

## 常见问题

### 1. 数据库连接失败
确保Docker服务已启动：
```bash
cd docker
docker-compose ps
```

### 2. Flyway迁移失败
查看迁移状态：
```bash
mvn flyway:info
```

如果需要修复：
```bash
mvn flyway:repair
```

### 3. 端口冲突
修改 `application.yml` 中的端口配置：
```yaml
server:
  port: 8080  # 改为其他端口
```

## 开发计划

### Phase 1: 基础搭建 ✅
- [x] 项目骨架搭建
- [x] Docker环境配置
- [x] 数据库表设计
- [x] Flyway配置
- [ ] 用户认证模块
- [ ] 基础数据模块

### Phase 2: 核心业务
- [ ] 考试管理
- [ ] 成绩录入
- [ ] 成绩审核
- [ ] 成绩发布

### Phase 3: 统计分析
- [ ] 个人成绩分析
- [ ] 班级成绩统计
- [ ] 年级成绩统计
- [ ] 数据导出

### Phase 4: 前端开发
- [ ] 管理后台基础框架
- [ ] 成绩录入页面
- [ ] 统计分析页面
- [ ] 移动端开发

## 贡献指南

1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交Pull Request

## 许可证

本项目仅用于学习和研究目的。

## 联系方式

如有问题，请提交Issue。
