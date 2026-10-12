# API 接口参考

学生成绩管理系统后端接口清单，覆盖 18 个模块、约 83 个接口。内容依据 `src/main/java/com/school/module/*/controller` 下的控制器源码整理。

## 通用约定

### 服务地址与路径前缀

- 服务端口：`8080`
- 全局上下文路径（`application.yml` → `server.servlet.context-path`）：`/api/v1`

Spring 会给每个控制器的 `@RequestMapping` 再拼上该上下文路径，得到**运行时实际 URL**。本文档所有路径均已按「上下文路径 + 控制器映射」列出**实际可访问的 URL**。

### 路径前缀（2026-10-12 已统一）

所有控制器的 `@RequestMapping` 只写业务资源名（如 `/schools`、`/scores`、`/auth`、`/chat`），`/api/v1` 由上下文路径统一提供，运行时实际 URL 一律为 `/api/v1/<资源>`。前端 `request` 的 `baseURL` 为 `/api/v1`，开发环境经 Vite 代理原样转发到 `http://localhost:8080`。

> 控制器映射、安全白名单、前端 `url:` 中都不要再出现 `/api` 或 `/v1` 字样。MockMvc 测试不走上下文路径，请求 `/schools` 而非 `/api/v1/schools`。

### 统一响应格式 Result&lt;T&gt;

除文件流接口（导入导出、PDF）外，所有接口返回统一信封 `Result<T>`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1699999999999
}
```

字段：`code`（Integer，成功为 200）、`message`（String）、`data`（T，业务数据）、`timestamp`（Long）。

前端 `request` 拦截器在 `code === 200 || code === 0` 时直接返回 `data`，业务代码拿到的是拆包后的数据。

### 分页参数与响应

分页查询接口的查询参数统一继承 `BasePageQuery`（通过 URL query 传递）：

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| pageNum | Long | 1 | 页码 |
| pageSize | Long | 10 | 每页条数 |
| orderBy | String | - | 排序字段 |
| isAsc | Boolean | false | 是否升序 |

分页响应为 MyBatis-Plus 的 `Page<T>` 包在 `Result` 中（注意：并非项目内 `PageResult`，后者已定义但控制器未使用）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

> 前端类型 `PageResponse<T>` 使用 `records`/`total`/`pageNum`/`pageSize` 字段；后端 `Page<T>` 的分页字段名为 `current`/`size`。取列表数据用 `records`、总数用 `total` 两端一致。

### 认证

除白名单外所有接口需在请求头携带 JWT：`Authorization: Bearer <accessToken>`。白名单（无需认证）：`/auth/login`、`/auth/register`、`/auth/captcha`、以及 Swagger/Druid 相关路径。

---

## 1. 认证模块（auth）

控制器 `AuthController`，实际前缀 `/api/v1/auth`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | 登录 | body `LoginRequest` | `LoginResponse` |
| POST | `/api/v1/auth/logout` | 登出 | header `Authorization` | `Void` |
| POST | `/api/v1/auth/refresh` | 刷新令牌 | query `refreshToken` | `LoginResponse` |
| GET | `/api/v1/auth/captcha` | 获取验证码 | - | `CaptchaResponse` |

请求/响应体：

- `LoginRequest`：`username`(必填)、`password`(必填)、`captcha`(必填)、`captchaKey`(必填)
- `LoginResponse`：`accessToken`、`refreshToken`、`tokenType`(="Bearer")、`expiresIn`、`userInfo{ id, username, realName, avatar, userType, schoolId }`
- `CaptchaResponse`：`captchaKey`、`captchaImage`(Base64)、`expiresIn`

```json
// POST /api/v1/auth/login 请求
{ "username": "admin", "password": "admin123", "captcha": "8f3k", "captchaKey": "uuid-xxxx" }
```

---

## 2. 考试管理（exam）

控制器 `ExamController`，实际前缀 `/api/v1/exams`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/exams` | 创建考试 | body `ExamCreateRequest` | `ExamResponse` |
| GET | `/api/v1/exams/{id}` | 考试详情 | path `id` | `ExamResponse` |
| PUT | `/api/v1/exams/{id}` | 更新考试 | path `id`, body `ExamUpdateRequest` | `ExamResponse` |
| PATCH | `/api/v1/exams/{id}/status` | 更新状态 | path `id`, query `status` | `Void` |
| DELETE | `/api/v1/exams/{id}` | 删除考试 | path `id` | `Void` |
| GET | `/api/v1/exams` | 分页列表 | query `ExamQueryRequest` | `Page<ExamResponse>` |
| POST | `/api/v1/exams/{id}/start-input` | 开始录入 | path `id` | `Void` |
| POST | `/api/v1/exams/{id}/submit-review` | 提交审核 | path `id` | `Void` |
| POST | `/api/v1/exams/{id}/publish` | 发布成绩 | path `id` | `Void` |

请求/响应体：

- `ExamCreateRequest`：`schoolId`(必填)、`termId`(必填)、`examName`(必填)、`examType`(必填；1-月考/2-期中/3-期末/4-模拟/5-其他)、`examDate`、`gradeIds`(必填)、`subjects`(必填；每项 `{ subjectId, fullScore, sortOrder }`)
- `ExamQueryRequest`（继承分页）：`schoolId`、`termId`、`gradeId`、`examName`、`examType`、`status`、`startDate`、`endDate`
- `ExamResponse`：`id`、`schoolId/schoolName`、`termId/termName`、`examName`、`examType/examTypeName`、`examDate`、`gradeIds/gradeNames`、`status`(0-未开始/1-录入中/2-审核中/3-已发布)/`statusName`、`subjects[]`、`createdAt`、`updatedAt`

考试状态流转：`未开始(0) → 录入中(1) → 审核中(2) → 已发布(3)`，分别对应 start-input、submit-review、publish 三个动作。

---

## 3. 成绩管理（score）

控制器 `ScoreController`，实际前缀 `/api/v1/scores`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/scores/input` | 单条录入 | body `ScoreInputRequest` | `ScoreResponse` |
| POST | `/api/v1/scores/batch-input` | 批量录入 | body `ScoreBatchInputRequest` | `Integer`（条数） |
| GET | `/api/v1/scores/{id}` | 成绩详情 | path `id` | `ScoreResponse` |
| GET | `/api/v1/scores` | 分页列表 | query `ScoreQueryRequest` | `Page<ScoreResponse>` |
| GET | `/api/v1/scores/student/{studentId}/exam/{examId}` | 学生某场考试成绩 | path `studentId`,`examId` | `List<ScoreResponse>` |
| POST | `/api/v1/scores/review` | 审核成绩 | body `ScoreReviewRequest` | `Void` |
| POST | `/api/v1/scores/calculate-rankings` | 计算排名 | query `examId`,`subjectId`(可选) | `Void` |
| DELETE | `/api/v1/scores/{id}` | 删除成绩 | path `id` | `Void` |

请求/响应体：

- `ScoreInputRequest`：`examId`、`studentId`、`subjectId`、`classId`、`schoolId`（均必填）、`score`、`isAbsent`(0-否/1-是)
- `ScoreBatchInputRequest`：`examId`(必填)、`scores[]`（每项 `{ studentId, subjectId, classId, schoolId, score, isAbsent }`）
- `ScoreReviewRequest`：`scoreIds[]`(必填)、`result`(必填；1-通过/2-驳回)、`rejectReason`
- `ScoreQueryRequest`（继承分页）：`examId`、`studentId`、`subjectId`、`classId`、`schoolId`、`status`(0-待审核/1-已通过/2-已驳回)、`studentName`、`studentNo`
- `ScoreResponse`：`id`、`examId/examName`、`studentId/studentName/studentNo`、`subjectId/subjectName`、`classId/className`、`schoolId/schoolName`、`score`、`fullScore`、`isAbsent`、`rankInClass/rankInGrade/rankInSchool`、`status/statusName`、`recordedAt/reviewedAt`、`createdAt/updatedAt`

---

## 4. 统计分析（stat）

控制器 `StatController`，实际前缀 `/api/v1/stats`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| GET | `/api/v1/stats/personal` | 个人成绩统计 | query `examId`,`studentId` | `PersonalStatResponse` |
| GET | `/api/v1/stats/student/{studentId}/trend` | 学生成绩趋势 | path `studentId`, query `subjectId`(可选) | `StudentTrendResponse` |
| GET | `/api/v1/stats/class` | 班级成绩统计 | query `examId`,`classId` | `ClassStatResponse` |
| GET | `/api/v1/stats/grade` | 年级成绩统计 | query `examId`,`gradeId` | `GradeStatResponse` |

响应体要点：

- `PersonalStatResponse`：`studentId/studentName/studentNo`、`examId/examName`、`totalScore`、`averageScore`、`rankInClass/rankInGrade`、`subjectScores[]`
- `StudentTrendResponse`：`examTrends[]`（各次考试总分/均分/排名趋势）、`subjectRadars[]`（各科得分率雷达）
- `ClassStatResponse`：`totalStudents/actualStudents/absentStudents`、`averageScore/maxScore/minScore`、`passCount/passRate`、`excellentCount/excellentRate`、`subjectStats[]`、`scoreRanges[]`（分数段分布）
- `GradeStatResponse`：`averageScore`、`passRate`、`excellentRate`、`classComparisons[]`（班级对比排名）、`subjectStats[]`

---

## 5. 学校管理（school）

控制器 `SchoolController`，实际前缀 `/api/v1/schools`。多租户核心，其它数据多以 `schoolId` 归属。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/schools` | 创建学校 | body `SchoolCreateRequest` | `SchoolResponse` |
| GET | `/api/v1/schools/{id}` | 学校详情 | path `id` | `SchoolResponse` |
| PUT | `/api/v1/schools/{id}` | 更新学校 | path `id`, body `SchoolUpdateRequest` | `SchoolResponse` |
| PATCH | `/api/v1/schools/{id}/status` | 更新状态 | path `id`, query `status` | `Void` |
| GET | `/api/v1/schools` | 分页列表 | query `SchoolQueryRequest` | `Page<SchoolResponse>` |

- `SchoolCreateRequest`：`schoolName`(必填)、`schoolCode`(必填，`^[A-Z0-9]+$`)、`schoolType`、`province`、`city`、`district`、`address`、`contactPerson`、`contactPhone`(`^1[3-9]\d{9}$`)
- `SchoolQueryRequest`（继承分页）：`schoolName`、`schoolCode`、`schoolType`、`province`、`city`、`district`、`status`

---

## 6. 班级管理（class）

控制器 `ClassController`，实际前缀 `/api/v1/classes`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/classes` | 创建班级 | body `ClassCreateRequest` | `ClassResponse` |
| GET | `/api/v1/classes/{id}` | 班级详情 | path `id` | `ClassResponse` |
| PUT | `/api/v1/classes/{id}` | 更新班级 | path `id`, body `ClassUpdateRequest` | `ClassResponse` |
| PATCH | `/api/v1/classes/{id}/head-teacher` | 设置班主任 | path `id`, query `headTeacherId` | `Void` |
| DELETE | `/api/v1/classes/{id}` | 删除班级 | path `id` | `Void` |
| GET | `/api/v1/classes` | 分页列表 | query `ClassQueryRequest` | `Page<ClassResponse>` |

- `ClassCreateRequest`：`schoolId`(必填)、`gradeId`(必填)、`className`(必填)、`classCode`(必填)、`termId`(必填)、`headTeacherId`
- `ClassQueryRequest`（继承分页）：`schoolId`、`gradeId`、`termId`、`className`、`headTeacherId`

---

## 7. 学生管理（student）

控制器 `StudentController`，实际前缀 `/api/v1/students`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/students` | 创建学生 | body `StudentCreateRequest` | `StudentResponse` |
| GET | `/api/v1/students/{id}` | 学生详情 | path `id` | `StudentResponse` |
| PUT | `/api/v1/students/{id}` | 更新学生 | path `id`, body `StudentUpdateRequest` | `StudentResponse` |
| PATCH | `/api/v1/students/{id}/status` | 更新状态 | path `id`, query `status` | `Void` |
| DELETE | `/api/v1/students/{id}` | 删除学生 | path `id` | `Void` |
| GET | `/api/v1/students` | 分页列表 | query `StudentQueryRequest` | `Page<StudentResponse>` |
| POST | `/api/v1/students/{studentId}/assign-class` | 分配班级 | path `studentId`, query `classId` | `Void` |
| POST | `/api/v1/students/{studentId}/transfer-class` | 转班 | path `studentId`, query `fromClassId`,`toClassId` | `Void` |
| DELETE | `/api/v1/students/{studentId}/class/{classId}` | 移出班级 | path `studentId`,`classId` | `Void` |
| GET | `/api/v1/students/by-class/{classId}` | 按班级查学生 | path `classId` | `List<StudentResponse>` |

- `StudentCreateRequest`：`studentNo`(必填)、`schoolId`(必填)、`name`(必填)、`gender`(1-男/2-女)、`birthDate`、`enrollmentDate`、`userId`
- `StudentQueryRequest`（继承分页）：`schoolId`、`classId`、`studentNo`、`name`、`gender`、`status`(0-离校/1-在读/2-休学)

---

## 8. 教师管理（teacher）

控制器 `TeacherController`，实际前缀 `/api/v1/teachers`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/teachers` | 创建教师 | body `TeacherCreateRequest` | `TeacherResponse` |
| GET | `/api/v1/teachers/{id}` | 教师详情 | path `id` | `TeacherResponse` |
| PUT | `/api/v1/teachers/{id}` | 更新教师 | path `id`, body `TeacherUpdateRequest` | `TeacherResponse` |
| PATCH | `/api/v1/teachers/{id}/status` | 更新状态 | path `id`, query `status` | `Void` |
| PATCH | `/api/v1/teachers/{id}/subject` | 设置任教科目 | path `id`, query `subjectId` | `Void` |
| DELETE | `/api/v1/teachers/{id}` | 删除教师 | path `id` | `Void` |
| GET | `/api/v1/teachers` | 分页列表 | query `TeacherQueryRequest` | `Page<TeacherResponse>` |

- `TeacherCreateRequest`：`teacherNo`(必填)、`schoolId`(必填)、`name`(必填)、`gender`(1-男/2-女)、`subjectId`、`userId`
- `TeacherQueryRequest`（继承分页）：`schoolId`、`teacherNo`、`name`、`gender`、`subjectId`、`status`(0-离职/1-在职)

---

## 9. 年级管理（grade）

控制器 `GradeController`，实际前缀 `/api/v1/grades`（注意三段前缀）。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/grades` | 创建年级 | body `GradeCreateRequest` | `GradeResponse` |
| GET | `/api/v1/grades/{id}` | 年级详情 | path `id` | `GradeResponse` |
| PUT | `/api/v1/grades/{id}` | 更新年级 | path `id`, body `GradeUpdateRequest` | `GradeResponse` |
| DELETE | `/api/v1/grades/{id}` | 删除年级 | path `id` | `Void` |
| GET | `/api/v1/grades` | 分页列表 | query `GradeQueryRequest` | `Page<GradeResponse>` |
| GET | `/api/v1/grades/school/{schoolId}` | 按学校查年级 | path `schoolId` | `List<GradeResponse>` |

- `GradeCreateRequest`：`schoolId`(必填)、`gradeName`(必填)、`gradeCode`(必填)
- `GradeQueryRequest`（继承分页）：`schoolId`、`gradeName`、`gradeCode`

---

## 10. 科目管理（subject）

控制器 `SubjectController`，实际前缀 `/api/v1/subjects`（注意三段前缀）。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/subjects` | 创建科目 | body `SubjectCreateRequest` | `SubjectResponse` |
| GET | `/api/v1/subjects/{id}` | 科目详情 | path `id` | `SubjectResponse` |
| PUT | `/api/v1/subjects/{id}` | 更新科目 | path `id`, body `SubjectUpdateRequest` | `SubjectResponse` |
| DELETE | `/api/v1/subjects/{id}` | 删除科目 | path `id` | `Void` |
| GET | `/api/v1/subjects` | 分页列表 | query `SubjectQueryRequest` | `Page<SubjectResponse>` |
| GET | `/api/v1/subjects/school/{schoolId}` | 按学校查科目 | path `schoolId` | `List<SubjectResponse>` |

- `SubjectCreateRequest`：`subjectName`(必填,≤50)、`subjectCode`(必填,≤20,`^[A-Z0-9]+$`)、`fullScore`(必填,>0)、`passScore`(必填,>0)、`schoolId`(必填)
- `SubjectQueryRequest`（继承分页）：`subjectName`、`subjectCode`、`schoolId`

---

## 11. 学期管理（term）

控制器 `AcademicTermController`，实际前缀 `/api/v1/terms`（注意三段前缀）。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/terms` | 创建学期 | body `AcademicTermCreateRequest` | `AcademicTermResponse` |
| GET | `/api/v1/terms/{id}` | 学期详情 | path `id` | `AcademicTermResponse` |
| PUT | `/api/v1/terms/{id}` | 更新学期 | path `id`, body `AcademicTermUpdateRequest` | `AcademicTermResponse` |
| PATCH | `/api/v1/terms/{id}/end` | 结束学期 | path `id` | `Void` |
| GET | `/api/v1/terms` | 分页列表 | query `AcademicTermQueryRequest` | `Page<AcademicTermResponse>` |
| GET | `/api/v1/terms/current` | 当前学期 | query `schoolId` | `AcademicTermResponse` |
| GET | `/api/v1/terms/school/{schoolId}` | 按学校查学期 | path `schoolId` | `List<AcademicTermResponse>` |

- `AcademicTermCreateRequest`：`schoolId`(必填)、`year`(必填,`^\d{4}-\d{4}$`)、`term`(必填；1-第一学期/2-第二学期)、`startDate`(必填)、`endDate`(必填)
- `AcademicTermQueryRequest`（继承分页）：`schoolId`、`year`、`term`、`status`

---

## 12. 成绩变更（scorechange）

控制器 `ScoreChangeController`，实际前缀 `/api/v1/score-changes`。用于成绩发布后的变更申请与审批。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/score-changes/apply` | 申请变更 | body `ScoreChangeApplyRequest`, query `applicantId` | `ScoreChangeLogResponse` |
| POST | `/api/v1/score-changes/approve` | 审批变更 | body `ScoreChangeApproveRequest`, query `approverId` | `Void` |
| GET | `/api/v1/score-changes/{id}` | 变更日志详情 | path `id` | `ScoreChangeLogResponse` |
| GET | `/api/v1/score-changes` | 分页列表 | query `ScoreChangeQueryRequest` | `Page<ScoreChangeLogResponse>` |
| DELETE | `/api/v1/score-changes/{id}/cancel` | 撤销申请 | path `id`, query `applicantId` | `Void` |

- `ScoreChangeApplyRequest`：`scoreId`(必填)、`newScore`(必填)、`changeReason`(必填)
- `ScoreChangeApproveRequest`：`changeLogIds[]`(必填)、`result`(必填；1-通过/2-驳回)、`rejectReason`
- `ScoreChangeQueryRequest`（继承分页）：`examId`、`studentId`、`applicantId`、`status`

---

## 13. 成绩预警（warning）

控制器 `ScoreWarningController`，实际前缀 `/api/v1/warnings`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/warnings/check-exam` | 触发考试成绩预警检查 | query `examId` | `Void` |
| GET | `/api/v1/warnings/trend/{studentId}` | 学生成绩趋势分析 | path `studentId`, query `subjectId`(可选) | `Map<String,Object>` |

---

## 14. 通知消息（notification）

控制器 `NotificationController`，实际前缀 `/api/v1/notifications`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/notifications` | 创建通知 | body `NotificationCreateRequest` | `NotificationResponse` |
| GET | `/api/v1/notifications/{id}` | 通知详情 | path `id` | `NotificationResponse` |
| GET | `/api/v1/notifications` | 分页列表 | query `NotificationQueryRequest` | `Page<NotificationResponse>` |
| PATCH | `/api/v1/notifications/{id}/read` | 标记已读 | path `id` | `Void` |
| PATCH | `/api/v1/notifications/batch-read` | 批量已读 | body `List<Long>`(ids) | `Void` |
| PATCH | `/api/v1/notifications/user/{userId}/read-all` | 全部已读 | path `userId` | `Void` |
| DELETE | `/api/v1/notifications/{id}` | 删除通知 | path `id` | `Void` |
| GET | `/api/v1/notifications/user/{userId}/unread-count` | 未读数量 | path `userId` | `Integer` |

- `NotificationCreateRequest`：`userId`(必填)、`title`(必填)、`content`、`type`(必填；1-成绩发布/2-成绩预警/3-系统通知)、`relatedId`
- `NotificationQueryRequest`（继承分页）：`userId`、`type`、`isRead`

---

## 15. 导入导出（export）

控制器 `ExportController`，实际前缀 `/api/v1/export`。基于 EasyExcel。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| GET | `/api/v1/export/scores` | 导出成绩 Excel | query `examId`,`classId`(可选) | 文件流（无 `Result` 包装） |
| GET | `/api/v1/export/score-template` | 下载导入模板 | - | 文件流 |
| POST | `/api/v1/export/import-scores` | 导入成绩 Excel | query `examId`,`classId`,`schoolId`, multipart `file` | `Integer`（条数） |

> 导出/模板接口直接向 `HttpServletResponse` 写二进制流，不返回 `Result` 信封；前端需按 blob 处理。

---

## 16. PDF 报表（pdf）

控制器 `PdfController`，实际前缀 `/api/v1/pdf`。基于 iText7（框架已就绪）。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| GET | `/api/v1/pdf/score-report` | 生成学生成绩单 PDF | query `examId`,`studentId` | PDF 文件流 |
| GET | `/api/v1/pdf/class-reports` | 批量生成班级成绩单 | query `examId`,`classId` | 文件流 |

> 同为文件流接口，不返回 `Result` 信封。

---

## 17. 数据字典（dict）

控制器 `DictController`，实际前缀 `/api/v1/dict`。请求体直接使用实体 `DictType` / `DictData`（无独立 DTO）。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| GET | `/api/v1/dict/data/{dictType}` | 按类型取字典数据 | path `dictType` | `List<DictData>` |
| POST | `/api/v1/dict/type` | 创建字典类型 | body `DictType` | `Void` |
| POST | `/api/v1/dict/data` | 创建字典数据 | body `DictData` | `Void` |
| PUT | `/api/v1/dict/type/{id}` | 更新字典类型 | path `id`, body `DictType` | `Void` |
| PUT | `/api/v1/dict/data/{id}` | 更新字典数据 | path `id`, body `DictData` | `Void` |
| DELETE | `/api/v1/dict/type/{id}` | 删除字典类型 | path `id` | `Void` |
| DELETE | `/api/v1/dict/data/{id}` | 删除字典数据 | path `id` | `Void` |

---

## 18. 系统配置（config）

控制器 `ConfigController`，实际前缀 `/api/v1/config`。请求体直接使用实体 `SysConfig`。

| 方法 | 路径 | 说明 | 参数 | 响应 |
|---|---|---|---|---|
| GET | `/api/v1/config/key/{configKey}` | 按 key 取配置值 | path `configKey` | `String` |
| GET | `/api/v1/config` | 全部配置 | - | `List<SysConfig>` |
| POST | `/api/v1/config` | 创建配置 | body `SysConfig` | `Void` |
| PUT | `/api/v1/config/{id}` | 更新配置 | path `id`, body `SysConfig` | `Void` |
| DELETE | `/api/v1/config/{id}` | 删除配置 | path `id` | `Void` |

---

## 附：接口总览

| 模块 | 控制器 | 实际前缀 | 接口数 |
|---|---|---|---|
| 认证 | AuthController | `/api/v1/auth` | 4 |
| 考试 | ExamController | `/api/v1/exams` | 9 |
| 成绩 | ScoreController | `/api/v1/scores` | 8 |
| 统计 | StatController | `/api/v1/stats` | 4 |
| 学校 | SchoolController | `/api/v1/schools` | 5 |
| 班级 | ClassController | `/api/v1/classes` | 6 |
| 学生 | StudentController | `/api/v1/students` | 10 |
| 教师 | TeacherController | `/api/v1/teachers` | 7 |
| 年级 | GradeController | `/api/v1/grades` | 6 |
| 科目 | SubjectController | `/api/v1/subjects` | 6 |
| 学期 | AcademicTermController | `/api/v1/terms` | 7 |
| 成绩变更 | ScoreChangeController | `/api/v1/score-changes` | 5 |
| 成绩预警 | ScoreWarningController | `/api/v1/warnings` | 2 |
| 通知 | NotificationController | `/api/v1/notifications` | 8 |
| 导入导出 | ExportController | `/api/v1/export` | 3 |
| PDF | PdfController | `/api/v1/pdf` | 2 |
| 字典 | DictController | `/api/v1/dict` | 7 |
| 配置 | ConfigController | `/api/v1/config` | 5 |

在线接口文档（Knife4j）：启动后端后访问 `http://localhost:8080/api/v1/doc.html`。
