# 功能测试报告

**项目**: 学校管理系统 (schoolSystem)
**测试日期**: 2026-09-13
**测试范围**: 管理员功能 / 教师功能 / 数据隔离 & 异常处理
**测试方法**: 黑盒功能测试 + 权限越权测试 + 异常路径测试
**测试环境**: 后端 Spring Boot + 前端 Vue 3 + Element Plus

---

## 1. 测试概述

本次测试由三个独立测试 Agent 并行执行，覆盖系统全部主要功能模块：

| 测试组 | 负责范围 | 用例数 | 通过 | 失败 | 警告 |
|--------|---------|--------|------|------|------|
| Agent-1 | 管理员全功能 | 23 | 18 | 3 | 5 |
| Agent-2 | 教师功能 | 22 | 13 | 6 | 3 |
| Agent-3 | 数据隔离 & 异常 | 17 | 10 | 3 | 2 |
| **合计** | | **62** | **41** | **12** | **10** |

**总通过率**: 66.1%（仅计 PASS/FAIL，不含 WARN）

---

## 2. 角色覆盖说明

| 角色 | 测试覆盖情况 |
|------|------------|
| 超级管理员 | 完整覆盖：学校、学期、年级、班级、科目、教师、学生、用户、考试、字典、配置、退出 |
| 教师 | 覆盖：成绩录入、批量录入、成绩审核、成绩查询、统计分析、导入导出 |
| 数据隔离 | 跨角色越权尝试、路由守卫、Token 失效、异常响应 |

---

## 3. 测试用例详情

### 3.1 管理员功能测试（Agent-1）

| Case | 页面 | 结果 | 说明 |
|------|------|------|------|
| A01 | 登录 | ✅ PASS | JWT 存储、重定向正常 |
| A02 | 登录 | ⚠️ WARN | 登录页明文展示测试账号 admin/admin123 |
| A03 | 登录 | ✅ PASS | 验证码错误后自动刷新 |
| A04 | 学校管理 | ✅ PASS | 分页加载正常 |
| A05 | 学校管理 | ✅ PASS | 新增表单校验+成功提示 |
| A06 | 学校管理 | ✅ PASS | 编辑回显+保存提示 |
| A07 | 学校管理 | ✅ PASS | 状态切换+失败回滚 |
| A08 | 学期管理 | ⚠️ WARN | 无独立"当前学期"视图，依赖状态筛选 |
| A09 | 学期管理 | ✅ PASS | 结束学期+按钮隐藏 |
| A10 | 年级管理 | ✅ PASS | CRUD+删除确认弹窗 |
| A11 | 班级管理 | ❌ FAIL | 班主任分配走 `PUT /classes/{id}` 而非 `PATCH /classes/{id}/head-teacher` |
| A12 | 班级管理 | ⚠️ WARN | 删除有学生的班级无前端专用拦截，依赖后端错误透传 |
| A13 | 科目管理 | ✅ PASS | CRUD 含格式校验+及格分≤满分校验 |
| A14 | 教师管理 | ❌ FAIL | 科目字段非必填，无独立分配接口 |
| A15 | 学生管理 | ✅ PASS | 新增+分配班级 assign-class 接口正确 |
| A16 | 学生管理 | ❌ FAIL | 缺失转班功能，transfer-class 接口未调用 |
| A17 | 用户管理 | ⚠️ WARN | 前端路由无角色守卫，仅靠后端控制 |
| A18 | 用户管理 | ✅ PASS | 重置密码 `PATCH /users/{id}/password` 正确 |
| A19 | 考试管理 | ✅ PASS | 三步向导表单校验完整 |
| A20 | 考试详情 | ✅ PASS | 生命周期流转按钮逻辑完整 |
| A21 | 数据字典 | ✅ PASS | 两层 CRUD 完整 |
| A22 | 系统配置 | ✅ PASS | 编辑+保存+列表刷新 |
| A23 | 退出登录 | ✅ PASS | token 清除+路由跳转 /login（finally 保障） |

### 3.2 教师功能测试（Agent-2）

| Case | 页面 | 结果 | 说明 |
|------|------|------|------|
| T01 | 登录/菜单 | ❌ FAIL | MainLayout 菜单静态硬编码，教师可见全部菜单 |
| T02 | 成绩录入-选择区 | ⚠️ WARN | 下拉不预筛选教师范围，选错触发 403 体验差 |
| T03 | 成绩录入-保存 | ✅ PASS | `POST /scores/input` 正确，防重复提交 |
| T04 | 成绩录入-分数越界 | ✅ PASS | el-input-number min/max 强制截断 |
| T05 | 批量录入-加载 | ✅ PASS | `GET /scores/class-list` 填充表格 |
| T06 | 批量录入-批量提交 | ❌ FAIL | 循环调单条接口，未使用 `/scores/batch-input` |
| T07 | 批量录入-缺分数行 | ✅ PASS | 未填行静默跳过，行为合理 |
| T08 | 成绩审核列表-加载 | ✅ PASS | 后端 checkTeacherScope 有效 |
| T09 | 成绩审核详情-通过 | ✅ PASS | `POST /scores/review` 正确 |
| T10 | 成绩审核详情-驳回 | ✅ PASS | 前后端双重校验驳回原因非空 |
| T11 | 成绩查询-本班 | ⚠️ WARN | 下拉无教师范围预筛选，需选后等 403 反馈 |
| T12 | 成绩查询-他班 | ✅ PASS | 后端 403 拦截，前端 handleError 展示 |
| T13 | 成绩分析-分布图 | ✅ PASS | 用 el-table 展示，无 ECharts 白屏风险 |
| T14 | 统计-考试总览 | ✅ PASS | nextTick 保护图表初始化 |
| T15 | 统计-班级分析 | ❌ FAIL | `StatServiceImpl.getClassStat` 无 checkTeacherScope，存在越权风险 |
| T16 | 统计-学生趋势 | ✅ PASS | 趋势图 nextTick 保护 |
| T17 | 统计-科目对比 | ✅ PASS | 图表正常 |
| T18 | 成绩变更申请-发起 | ❌ FAIL | 前端无此页面/路由/API 封装 |
| T19 | 成绩变更申请-取消 | ❌ FAIL | 同 T18，成绩变更模块整体缺失 |
| T20 | 导出成绩 | ✅ PASS | 后端接口存在，前端无入口（功能可用但入口缺失） |
| T21 | 导入成绩 | ✅ PASS | ScoreImport.vue 完整，分数上限硬编码 100 |
| T22 | PDF 报告 | ✅ PASS | 后端接口存在，前端无入口 |

### 3.3 数据隔离 & 异常测试（Agent-3）

| Case | 类型 | 结果 | 说明 |
|------|------|------|------|
| D01 | 数据隔离 | ⚠️ WARN | 路由无角色守卫；`GET /users` 无 `@PreAuthorize` |
| D02 | 数据隔离 | ✅ PASS | `ScoreServiceImpl.checkTeacherScope` 全覆盖 |
| D03 | 数据隔离 | ✅ PASS | schoolId 强制过滤，超管放开全库（设计有意） |
| D04 | 数据隔离 | ✅ PASS | beforeEach 未登录重定向 /login |
| D05 | 数据隔离 | ✅ PASS | logout 删 Redis key，旧 token 即失效 |
| D06 | 加载异常 | ✅ PASS | axios 拦截器捕获 500，ElMessage.error，非白屏 |
| D07 | 加载异常 | ✅ PASS | 空数组时 el-table 显示"暂无数据" |
| D08 | 加载异常 | ❌ FAIL | 无 catch-all 路由，不存在路径内容区空白 |
| D09 | 加载异常 | ✅ PASS | axios timeout 30s，超时提示"网络错误" |
| D10 | 提示信息 | ⚠️ WARN | school 页无删除功能（设计有意），部分页面确认弹窗覆盖待验 |
| D11 | 提示信息 | ✅ PASS | ElMessage.success 中文文案正确 |
| D12 | 提示信息 | ✅ PASS | el-form rules 字段级校验 |
| D13 | 提示信息 | ✅ PASS | GlobalExceptionHandler 捕获唯一约束冲突 |
| D14 | 页面显示 | ✅ PASS | show-overflow-tooltip 长文本截断 |
| D15 | 页面显示 | ✅ PASS | v-loading 绑定 |
| D16 | 幽灵端点 | ⚠️ WARN | `GET /stats/personal` 需确认；`GET /auth/refresh` 前端未传参数会 400 |
| D17 | 路由守卫 | ❌ FAIL | 路由无角色守卫，HIGH 风险（同 D01） |

---

## 4. Bug 清单

| Bug ID | 严重度 | 模块 | 描述 | 复现步骤 |
|--------|--------|------|------|---------|
| BUG-A1-001 | 🔴 HIGH | 登录 | 登录页明文展示测试账号 admin/admin123 | 1. 打开登录页 2. 查看页面文本或 HTML 注释 |
| BUG-D01 | 🔴 HIGH | 用户管理/权限 | `GET /users` 无 `@PreAuthorize`；前端路由无角色守卫 | 1. 以教师账号登录 2. 直接访问 `/users` 路由 3. 调用 `GET /api/v1/users` |
| BUG-T01 | 🔴 HIGH | 菜单/权限 | MainLayout 菜单静态硬编码，教师登录后可见全部管理菜单 | 1. 以教师账号登录 2. 查看左侧菜单，可见学校/用户等管理员菜单 |
| BUG-T02 | 🔴 HIGH | 统计分析 | `StatServiceImpl.getClassStat` 无 checkTeacherScope，教师可查看任意班级统计 | 1. 以教师账号登录 2. 调用 `GET /api/v1/stats/class?classId=<他班ID>` |
| BUG-A1-002 | 🟡 MEDIUM | 班级管理 | 班主任分配走 `PUT /classes/{id}` 全量更新，应走 `PATCH /classes/{id}/head-teacher` | 1. 进入班级管理 2. 分配班主任 3. 抓包观察请求方法和路径 |
| BUG-A1-003 | 🟡 MEDIUM | 学生管理 | 转班功能缺失，`transfer-class` 接口未调用，前端无转班入口 | 1. 进入学生管理 2. 查找转班操作入口，不存在 |
| BUG-T03 | 🟡 MEDIUM | 成绩录入 | 批量保存循环调单条接口，应使用 `/scores/batch-input` | 1. 进入批量录入页 2. 填入多行成绩 3. 点击保存，抓包可见循环请求 |
| BUG-T04 | 🟡 MEDIUM | 成绩导入 | Excel 导入分数上限硬编码 100，不适配满分非 100 的科目 | 1. 设置满分 150 的科目 2. 导入含 120 分的 Excel，校验拒绝 |
| BUG-T05 | 🟡 MEDIUM | 成绩变更 | 前端整个成绩变更申请模块（发起/取消）缺失 | 1. 以教师登录 2. 查找成绩变更申请入口，不存在 |
| BUG-T06 | 🔵 LOW | 成绩审核 | 审核历史操作人字段恒为空 | 1. 完成一条审核操作 2. 查看审核历史，操作人列为空 |
| BUG-D08 | 🔵 LOW | 路由 | 无 catch-all 路由，访问不存在路径内容区空白 | 1. 登录后访问 `/nonexistent-page` 2. 内容区空白，无 404 提示 |

---

## 5. 安全风险摘要

| 风险 | 级别 | 位置 | 状态 |
|------|------|------|------|
| 登录页明文展示测试账号凭据 | HIGH | 前端登录页 | 未修复 |
| 路由无角色守卫，教师可直接访问管理页面 URL | HIGH | `src/router/index.ts` | 未修复 |
| `GET /users` 接口无权限注解 | HIGH | `UserController` | 未修复 |
| 菜单硬编码，不随角色变化 | HIGH | `MainLayout` | 未修复 |
| `StatServiceImpl.getClassStat` 越权查询 | HIGH | `StatServiceImpl.java` | 未修复 |
| 幽灵端点 `GET /auth/refresh` 前端未正确传参 | MEDIUM | 前端 axios 拦截器 | 需确认 |

> 建议：HIGH 级安全风险应在下次发布前全部修复，不得带入生产环境。

---

## 6. 测试结论与建议

### 结论

系统核心数据流（登录、CRUD、成绩录入/审核、异常处理）基本可用，但存在 **4 个 HIGH 级安全漏洞**，集中在权限控制层：前端菜单和路由均未按角色隔离，后端部分接口缺失权限注解。此外，成绩变更申请模块前端整体缺失，属于功能性空白。

### 优先修复建议

1. **立即修复（发布阻断）**
   - BUG-T01: MainLayout 菜单改为根据 token 角色动态渲染
   - BUG-D01 / BUG-D17: router `beforeEach` 增加角色白名单守卫
   - BUG-T02: `StatServiceImpl.getClassStat` 补调 `checkTeacherScope`
   - BUG-A1-001: 删除登录页硬编码测试账号展示

2. **本迭代修复**
   - BUG-T05: 补全成绩变更申请前端页面及路由
   - BUG-T03: 批量录入改用 `/scores/batch-input` 单次请求
   - BUG-T04: Excel 导入分数上限改为读取科目 `maxScore` 字段

3. **下迭代跟进**
   - BUG-A1-002: 班主任分配改用专用 PATCH 端点
   - BUG-A1-003: 补全转班功能
   - BUG-D08: 添加 catch-all 路由并展示 404 页面
   - BUG-T06: 修复审核历史操作人字段为空
