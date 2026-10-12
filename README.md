# 学生成绩管理系统

基于 Spring Boot + Vue 3 的学生成绩管理系统，支持多学校、多年级、多班级的成绩管理和统计分析。

## 项目状态

- 后端：约 90%。18 个业务模块、约 83 个 REST 接口、22 张数据表全部就绪；PDF 报表、部分统计（年级/趋势）为框架级实现。
- 前端：约 50%。登录、主布局、首页、以及学校/学生/班级/年级/科目/教师/学期等基础数据页已完成 CRUD；成绩录入、统计图表、系统管理等页面待开发。
- 测试：后端配置 JaCoCo（目标 80%），前端配置 Vitest + Playwright（目标 80%）；实际覆盖率尚未达标。

详细进展见 `docs/`（建议以 `docs/FINAL_PROJECT_SUMMARY.md` 为准）。接口清单见 `docs/API_REFERENCE.md`，前端规范见 `docs/FRONTEND_GUIDE.md`。

## 技术栈

### 后端
- Java 17、Spring Boot 3.1.12
- MyBatis-Plus 3.5.7、Druid 1.2.23（连接池）
- PostgreSQL 16、Redis 7
- Flyway（数据库版本管理）
- Spring Security + JWT（jjwt 0.12.5，认证授权）
- Knife4j 4.4.0（API 文档）
- EasyExcel（导入导出）、iText7（PDF）、Hutool、MapStruct、Lombok

### 前端
- Vue 3.3 + TypeScript 5.3
- Element Plus 2.5（UI）、Pinia 2.1（状态）、Vue Router 4.2（路由）
- Axios 1.6（HTTP）、ECharts 5.4（图表）
- Vite 5（构建）、Vitest（单测）、Playwright（E2E）

### 工具
- Maven 3.8+
- Node.js 18+
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

### 5. 运行后端测试

```bash
# 运行所有测试
mvn test

# 生成测试覆盖率报告
mvn clean test jacoco:report

# 查看覆盖率报告
open target/site/jacoco/index.html
```

## 前端开发

前端位于 `frontend/`，基于 Vue 3 + Vite。

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器（默认端口 3000，已配置 /api 代理到 http://localhost:8080）
npm run dev

# 生产构建（先 vue-tsc 类型检查再打包）
npm run build

# 预览构建产物
npm run preview

# 代码检查
npm run lint
```

开发前请阅读 `docs/FRONTEND_GUIDE.md`（项目结构、编码规范、CRUD 模板、状态管理与接口调用约定）。

### 运行前端测试

```bash
cd frontend

# 单元测试（Vitest）
npm run test

# 单元测试覆盖率（输出 coverage/index.html）
npm run test:coverage

# 端到端测试（Playwright，需先启动 dev 服务器）
npm run test:e2e
```

> 说明：`tests/` 下部分脚手架测试与当前实现尚未对齐（详见 `docs/FRONTEND_GUIDE.md` 测试小节），运行前端 E2E 前需确认端口与路由。

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
├── docker/                          # Docker 配置（PostgreSQL + Redis）
│   ├── docker-compose.yml
│   └── init-scripts/                # 数据库初始化脚本
├── src/
│   ├── main/
│   │   ├── java/com/school/
│   │   │   ├── common/              # 公共模块（base/constant/exception/result）
│   │   │   ├── config/              # 配置类（Security/Redis/MybatisPlus）
│   │   │   ├── security/            # 安全认证（JWT 过滤器与处理器）
│   │   │   ├── module/              # 业务模块（每个模块含 controller/service/mapper/entity/dto）
│   │   │   │   ├── auth/            # 认证
│   │   │   │   ├── school/ term/ grade/ class/ subject/ teacher/ student/  # 基础数据
│   │   │   │   ├── exam/ score/ scorechange/ warning/   # 考试与成绩
│   │   │   │   ├── stat/            # 统计分析
│   │   │   │   ├── export/ pdf/     # 导入导出与报表
│   │   │   │   ├── notification/    # 通知消息
│   │   │   │   └── dict/ config/    # 字典与系统配置
│   │   │   └── SchoolSystemApplication.java
│   │   └── resources/
│   │       ├── db/migration/        # Flyway 迁移脚本（V1~V8）
│   │       ├── mapper/              # MyBatis Mapper XML
│   │       ├── application.yml      # 主配置（context-path=/api/v1，端口 8080）
│   │       ├── application-dev.yml
│   │       └── application-prod.yml
│   └── test/                        # 后端测试（JUnit 5 + Mockito）
├── frontend/                        # Vue 3 + Vite 管理后台
│   ├── src/
│   │   ├── api/                     # 接口层（按后端模块拆分）
│   │   ├── views/                   # 页面（按业务域）
│   │   ├── stores/ router/ utils/ types/ layouts/ styles/
│   │   └── main.ts
│   └── tests/                       # 前端测试（unit: Vitest / e2e: Playwright）
├── docs/                            # 项目文档
│   ├── API_REFERENCE.md             # 后端接口清单
│   ├── FRONTEND_GUIDE.md            # 前端开发规范
│   └── ...                          # 进展与总结报告
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
- [x] Docker 环境配置
- [x] 数据库表设计与 Flyway 配置
- [x] 用户认证模块（JWT + 验证码）
- [x] 基础数据模块（学校/学期/年级/班级/科目/教师/学生）

### Phase 2: 核心业务 ✅
- [x] 考试管理（四状态流转）
- [x] 成绩录入（单条 / 批量）
- [x] 成绩审核
- [x] 成绩发布与排名计算
- [x] 成绩变更审批、成绩预警

### Phase 3: 统计与扩展 ✅
- [x] 个人成绩分析
- [x] 班级成绩统计
- [x] 年级成绩统计（框架级）
- [x] 数据导入导出（Excel）
- [x] 通知消息、数据字典、系统配置
- [ ] PDF 成绩单（框架已就绪，待完善）

### Phase 4: 前端开发（进行中）
- [x] 管理后台基础框架（布局、路由、状态、请求封装）
- [x] 登录、首页
- [x] 基础数据 CRUD 页面（学校/学生/班级/年级/科目/教师/学期）
- [ ] 成绩录入 / 审核 / 查询页面
- [ ] 统计分析页面（ECharts）
- [ ] 系统管理页面
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
