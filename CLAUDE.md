# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

学生成绩管理系统 — multi-school student score management. Backend: Spring Boot 3.3.6 / Java 17, MyBatis-Plus 3.5.8, PostgreSQL 16, Redis 7, Flyway 11, Spring Security + JWT, Spring AI 1.0.0 (`src/`). Frontend: Vue 3 + TypeScript (strict) + Vite 5 + Element Plus + Pinia (`frontend/`). Comments, log messages, validation messages and UI copy are Chinese. README.md still says Spring Boot 3.1.12; `pom.xml` is the truth.

**History warning.** On 2026-09-14 a `git filter-repo --path <file>` wiped the repo and it was rebuilt from Claude session transcripts (tag `before-recover-0915` is the rollback point; `.claude/recover_*.py` are the rebuild scripts). The working tree carries a large uncommitted diff. Before any history-rewriting or working-tree-resetting git command (`reset --hard`, `checkout -- .`, `clean`, `filter-repo`, force push) spell out exactly what would be lost and wait for explicit confirmation.

## Commands

### Infrastructure

```bash
cd docker && docker-compose up -d        # PostgreSQL 16 :5432 + Redis 7 :6379
docker exec -it school-postgres psql -U school_admin -d school_system
docker exec -it school-redis redis-cli -a redis_pass_2024
```

Credentials: `school_admin` / `school_pass_2024`; Redis `redis_pass_2024`. The init script also creates `school_system_test` for integration tests.

### Backend

```bash
mvn spring-boot:run                      # the ONLY way to get devtools hot reload
mvn compile                              # second terminal: triggers a ~2s hot restart
mvn clean package -DskipTests && java -jar target/school-system-1.0.0-SNAPSHOT.jar
```

- Port 8080, file log at `logs/school-system.log`, Druid console `http://localhost:8080/api/v1/druid/` (admin/admin).
- Launching from the IDE runs on `[main]` and devtools never reloads; `mvn spring-boot:run` runs on `[restartedMain]`. If a change "compiles but does nothing", look for `Restarting due to ... (0 additions, 1 deletion, ...)` — the restart raced Maven's delete-then-write. `application-dev.yml` widens `quiet-period` to 1s for this.
- Knife4j/springdoc are disabled in the dev profile and `SwaggerConfig`'s `@Configuration` is commented out, so `/doc.html` is not available by default.
- `application-dev.yml` and `application-prod.yml` are **gitignored** but exist locally. Dev profile is where captcha is turned off and SQL logging is on. A fresh clone runs with captcha enabled.
- AI needs `DEEPSEEK_API_KEY` in a root `.env` (spring-dotenv) or `AI_PROVIDER=ollama` with a local Ollama.

### Backend tests

```bash
mvn test
mvn test -Dtest=SchoolServiceTest
mvn test -Dtest='ScoreServiceTest#shouldInputScore_WhenValidData_ThenSuccess'
mvn -o test                              # offline
# unit tests only (no Docker): exclude the four @SpringBootTest classes
mvn -o test -Dtest='!SchoolControllerTest,!SubjectServiceTest,!AcademicTermServiceTest,!SchoolSystemApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false
mvn clean test jacoco:report             # target/site/jacoco/index.html
```

- The 8 Mockito test classes need no infrastructure. Call `TestAuth.asSuperAdmin()` / `TestAuth.asSchoolAdmin(id)` in `@BeforeEach` and `TestAuth.clear()` in `@AfterEach`; service write paths read `schoolId` from the SecurityContext and throw `UNAUTHORIZED` without it.
- `@SpringBootTest` classes need Docker. `@ActiveProfiles("test")` uses local `school_system_test` + Redis db 1; `AbstractPostgresIT` subclasses start a Testcontainers `postgres:16-alpine` but still need local Redis (RedisConfig and the JWT filter hard-depend on it). Migrations are PG dialect; H2 cannot run them. On this machine Testcontainers currently fails with a 400 from the Docker Desktop named pipe even though the CLI works, so V14/V15 and the Spring context have only been verified by compile + unit tests, not on a live database.
- `jacoco:check` (80% instruction coverage) is bound to `verify`, so `mvn verify` / `mvn install` fail on coverage. Use `mvn test` or `mvn package -DskipTests`.

### Database

```bash
mvn flyway:info | flyway:migrate | flyway:validate | flyway:repair   # plugin hardcodes the local DB
docker exec -i school-postgres psql -U school_admin -d school_system \
  < src/main/resources/db/seed/seed_test_data.sql                     # seed, not Flyway-managed
```

- Migrations `V1`–`V15` in `src/main/resources/db/migration/`. Never edit an applied migration (checksums); add `V16__...`. `validate-on-migrate` is off, `baseline-on-migrate` is on. V14 makes `uk_exam_subject` a partial index on `deleted = 0`; V15 adds the `total_rank_in_*` columns. Both are `IF NOT EXISTS`-safe. Update `src/main/resources/ai/db-schema.md` alongside any schema change.
- Seed is idempotent for schools whose `school_code` starts with `SEED-` (5 schools, 18k students, 720k scores). Accounts: `admin` / `admin123`; seed teachers `seed-t000001…`, students `seed-20000001…`, password `123456`.

### Frontend

```bash
cd frontend && npm install
npm run dev                              # :3000, proxies /api/v1 → :8080
npm run build                            # vue-tsc && vite build — type errors fail the build
npx vue-tsc --noEmit                     # ~31 pre-existing errors; compare counts, don't eyeball
npx vitest run tests/unit/stores/user.spec.ts
npx playwright test tests/e2e/login.spec.ts --project=chromium
```

- `vitest`, `@vitest/coverage-v8`, `happy-dom`, `@vue/test-utils`, `@playwright/test` and `eslint` are **not declared** in `package.json` and there is no ESLint config, so `npm run test`, `test:e2e` and `lint` fail until you add them (`npx playwright install` for browsers). `xlsx` is also undeclared but imported by `ScoreImport.vue`, so the Excel import component cannot build.
- The spec files under `tests/unit` and `tests/e2e` plus `vitest.config.ts` are untracked in git. They match the current store/router (the old `{token}` and `/score-input` mismatches were already fixed), so commit them rather than rewriting.
- `frontend/tests/backend-smoke.mjs` hits every endpoint: `TOKEN=<jwt> node tests/backend-smoke.mjs` (needs `/tmp/ids.json`). Logout must be the last call in any script: it deletes `token:<jwt>` from Redis and every later request gets 401.

## URL prefix (unified 2026-10-12)

`server.servlet.context-path` is `/api/v1` and every controller's `@RequestMapping` is the bare resource (`/schools`, `/scores`, `/auth`, `/chat`, …), so runtime URLs are `http://localhost:8080/api/v1/<resource>`. The frontend `request.ts` baseURL, the Vite proxy key, `api/chat.ts`, the smoke scripts and `ExportTools` all assume exactly this.

- Never put `/api` or `/v1` in a `@RequestMapping`, in `SecurityConfig.WHITE_LIST`, or in a frontend `url:`. The context path supplies it once.
- MockMvc does not apply the context path, so controller tests request `/schools`, not `/api/v1/schools`.
- Log lines from before this date show runs under both `''` and `/api/v1`; ignore them when debugging.

## Architecture

### Request pipeline and identity

`JwtAuthenticationFilter` reads `Authorization: Bearer` or `?token=` (for EventSource/SSE), validates the JWT, then requires `token:<jwt>` to exist in Redis (logout deletes it, each request refreshes the TTL to `app.jwt.expiration`), and loads a `LoginUser` principal carrying `userId`, `schoolId` (null for super admin), `userType`, `teacherId`, `studentId`, `headTeacher`. Sessions are STATELESS. `dispatcherTypeMatchers(ERROR, ASYNC).permitAll()` exists so unmatched routes return a real 404 instead of being wrapped as 401 by the `/error` forward. `/auth/refresh` is whitelisted (refresh happens after the access token expired) and rejects tokens whose `type` claim is not `refresh`. Refresh tokens are registered in Redis as `refresh:<jwt>` plus `user:refresh:<userId>` (one active refresh token per user, so a second login invalidates the first device's refresh token); refreshing rotates the pair and logout, password reset and disabling a user delete both keys. The frontend stores `refreshToken` in `localStorage` and retries once through `/auth/refresh` on a 401 before logging out. `?token=` is accepted only on `/chat/stream`. `LoginResponse.UserInfo` carries `studentId`, `teacherId`, `headTeacher`, `role`; the frontend must use `userInfo.studentId` (student table key), never `userInfo.id` (user table key), when querying a student's own data.

`@EnableMethodSecurity` is on but `@PreAuthorize` appears in only three controllers (exam, student, user). Authorization lives in services via `SecurityUtils` (`isSuperAdmin/isSchoolAdmin/isTeacher/isHeadTeacher/isStudent`) and `TeacherScopeService`.

Roles are a single int `user_type` mapped by `UserRole`: 1 super admin, 2 school admin, 3 teacher, 4 student, 5 parent (unimplemented). Head teacher is not a separate type; it is derived at login from `class.head_teacher_id`. The frontend router reuses the same ints in `meta.allowedTypes`. Teacher visibility = classes in `teacher_class` for their subject ∪ classes they head (all subjects). Only super admin, school admin and head teachers may review scores.

### Multi-tenancy by `school_id`

`SchoolTenantHandler` + `TenantLineInnerInterceptor` (registered **before** the pagination interceptor so COUNT queries are also scoped) appends `school_id = <current user's school>` to SQL touching `academic_term, grade, class, student, teacher, subject, exam, score`. It is skipped for super admins, unauthenticated calls (login, Flyway) and all other tables; `sys_user`/`sys_role` visibility is enforced in business code.

Conventions on top of the interceptor, all in `SecurityUtils`:
- Reads in hand-written XML: pass `SecurityUtils.currentSchoolIdForQuery()` (null only for super admin = unrestricted). Never forward a request-supplied `schoolId` to a mapper.
- Writes/recalculations (`calculateRankings`, review): `currentSchoolId()`, which throws for super admins — cross-school batch writes are deliberately impossible.
- Creates: `resolveSchoolId(requested)` — throws on mismatch instead of silently rewriting; super admins must specify one.
- Methods throw `BusinessException` rather than return null when identity is missing, because a null tenant silently widens a query to the whole database.

### Response and error contract

- Every JSON endpoint returns `Result<T>` `{code, message, data, timestamp}`; success is `code 200`. Files (Excel/PDF) stream raw.
- `BusinessException` → HTTP **200** with a business `code` from `ResultCode` (1001 unauthorized, 1101 forbidden, 2xxx user, 5xxx score, 6xxx exam, 7002 duplicate…). Bean-validation failures → HTTP 400 with the field messages joined by `, `. `MethodArgumentTypeMismatchException` → 404 with "该路径可能不存在" because `/scores/class-list` is swallowed by `/scores/{id}`. Everything else → 500 "系统错误".
- Pagination returns MyBatis-Plus `Page<T>` inside `Result` (`records`, `total`, `current`, `size`); `PageResult` exists but controllers don't use it. The frontend reads `records`/`total` only — `data.list` is always undefined.
- Query DTOs extend `BasePageQuery` (`pageNum`, `pageSize`, `orderBy`, `isAsc`). `orderBy` is regex-whitelisted in the setter because mappers interpolate `ORDER BY ${request.orderBy}`. The no-arg constructor initializes `pageNum=1`, `pageSize=10` explicitly because `@Builder.Default` alone leaves them null when Spring binds GET parameters.
- Notifications are a personal inbox: `GET /notifications` is forced to the current user, single-item read/delete check ownership, creating one is admin-only. `GET /stats/grade` without `gradeId` is the school-wide exam overview; teachers get results filtered to their classes instead of 403.
- Jackson drops nulls (`non_null`), uses GMT+8 and `yyyy-MM-dd HH:mm:ss`.
- Request DTOs must be `@Data @Builder @NoArgsConstructor @AllArgsConstructor`; `@Builder` alone has no no-arg constructor and `@RequestBody` fails at runtime.

### Persistence

- Module layout `com.school.module.<name>/{controller,service,mapper,entity,dto}`; XML in `src/main/resources/mapper/<name>/`. `@MapperScan("com.school.module.**.mapper")`; entity aliases from `com.school.module.*.entity`. The class module is `class_module` / entity `SchoolClass` because `class` is reserved (table is still `class`).
- Entities extend `BaseEntity` (`createdAt`, `updatedAt`, `deleted` auto-filled by `MybatisPlusConfig`); all deletes are logical; there are no FK constraints in DDL. Optimistic locking interceptor is registered.
- MapStruct is disabled (annotation-processor conflict); DTO mapping is hand-written `convertToResponse`. Don't declare `poi-ooxml`; EasyExcel 3.3.2 must bring its own POI 4.x or xlsx writes throw `NoSuchFieldError: Factory`.
- **PostgreSQL rules** (the mappers were ported from MySQL): quote camelCase aliases in `resultType="map"` queries (`sc.id AS "gradeId"`) — unquoted identifiers fold to lowercase and `map.get("gradeId")` is silently null; the three such queries are in `ScoreMapper.xml`. No `IFNULL`/`DATE_FORMAT`/`FIND_IN_SET`/`GROUP_CONCAT`. CSV membership on `exam.grade_ids` uses `= ANY(string_to_array(e.grade_ids, ',')::bigint[])` with an empty-string guard. The four-way `LIKE` in `ExamMapper.xml` is verified correct — leave it.

### Domain flows

- Exam status: 0 not started → 1 recording (`startScoreInput`) → 2 reviewing (`submitForReview`) → 3 published (`publishExam`). `updateExamStatus` only allows +1 forward, or one step back (≥1) for admins. `publishExam` is a closed loop in one transaction: refuse if any score is still pending → set 3 → `calculateRankings` for total and each subject → `sendScorePublishNotification` to the students' `user_id`s → `checkScoreWarnings`. Nothing else triggers rankings or notifications.
- `inputScore` validates: exam exists and is in status 1, subject belongs to the exam, 0 ≤ score ≤ exam_subject.full_score, student is in the class. Scores of a published exam cannot be overwritten or deleted directly; they go through `ScoreChangeService` (apply → approve by admin/head teacher → write-back + rankings + notification), which only accepts scores of exams in status 3.
- Score status: 0 pending → 1 approved / 2 rejected (reject needs a reason, stored into `remark` with a `【驳回】` prefix until a dedicated column exists). `ScoreReviewRequest` takes either `scoreIds` or the `(examId, classId, subjectId)` triplet. Publishing refuses while any score is not status 1.
- Rankings live in two column sets on `score`: `rank_in_*` is the per-subject rank of that row, `total_rank_in_*` (V15) is the student's total-score rank, identical on all their rows for that exam. `ScoreService.calculateAllRankings(examId)` loads the exam once, computes both sets in memory (ties share a rank, absent/null scores excluded), and issues exactly one `updateById` per row with a fresh object holding only id + the six rank fields. Never copy a stale `Score` into an update object; MyBatis-Plus `NOT_NULL` strategy will re-write old values. Grade rank groups by `class.grade_id`; super admins resolve the school from `exam.school_id`. `POST /scores/calculate-rankings` is admin-only.
- Batch input (`POST /scores/batch-input`) and Excel import (`POST /export/import-scores`) return `BatchInputResult {successCount, failCount, failures[]}`; per-row failures are collected, not logged and dropped. The request carries no `schoolId`; it comes from the matched student row. Import validates the whole sheet (school-scoped student lookup, duplicates, range, class membership) before writing. Template columns: 学号, 姓名, 分数, 缺考, 备注.
- Students see only their own scores (`currentStudentId()`) and only for published exams (`ScoreQueryRequest.publishedOnly` is forced server-side). The same two rules apply to `GET /scores/{id}`, `/stats/personal`, `/stats/student/{id}/trend` and `/warnings/trend`. `GET /stats/class|grade|subject` and `GET /export/scores` are admin/teacher only; teachers are filtered to their classes rather than rejected, and non-admins can only export published exams.
- Score-change approval (`POST /score-changes/approve`) takes `{changeLogIds, result, rejectReason}`; approver comes from the security context. Head teachers may approve only for classes they head, and nobody may approve their own application. List/detail are admin/teacher only and scoped by school (admin) or allowed classes (teacher) server-side.
- `score_statistics`, `parent_student` and `sys_operation_log` exist in migrations but nothing in Java or mapper XML touches them yet (only the AI prompt mentions them). PDF endpoints are placeholders.

### AI assistant (`com.school.ai`)

- `ChatController`: `GET /chat/stream` (SSE; token via query param), `POST /chat/message`, `GET /chat/history`, `DELETE /chat/session`. `ChatService` builds a `ChatClient` with seven `@Tool` beans (`SchoolDataTools`, `ChartTools`, `ExportTools`, `AnalysisTools`, `LookupTools`, `ContextTools`, `SqlQueryTool`).
- Identity crosses threads three ways: `LoginUser` is put in `toolContext("loginUser")`; `AiPermissionChecker.currentUser(toolContext)` re-installs it into `SecurityContextHolder` on the tool thread so downstream services' `SecurityUtils` work; `main()` sets `MODE_INHERITABLETHREADLOCAL` and `AiUserContext` registers a Reactor schedule hook as fallbacks. Every tool must call the permission checker first.
- The system prompt embeds `src/main/resources/ai/db-schema.md`. Update that file whenever a migration changes a table the model may query.
- `SqlQueryTool.executeSql` is admin-only (`assertIsAdmin`); the system prompt only mentions it for admin users. It strips comments, rejects multi-statement input, and parses the statement with JSqlParser: table names must be in the tenant allow-list (mirrors `SchoolTenantHandler.TENANT_TABLES` plus the link tables; `sys_user` only when joined to a tenant table), `*` / `alias.*` / `row_to_json` projections are refused when `sys_user` is involved, and for school admins the SQL that actually runs is `TenantLineInnerInterceptor.parserSingle(...)` output, which injects `school_id = <school>` into every table reference the same way the ORM does. Unparseable SQL is refused. Blacklists pg_* / information_schema / `password`. 200-row cap. `ChartTools` returns ECharts option JSON marked `"__chart__": true`; `ChatPanel.vue` extracts it with brace-matching (not a lazy regex). `ExportTools` returns `__download__:/export/...` paths relative to the API root; `ChatPanel` downloads them through the authenticated `request` instance as a blob.
- Conversation history lives in Redis `chat:session:<id>` (7-day TTL, last 20 turns fed back). Provider switch: `spring.ai.provider` = `deepseek` | `ollama`. `AiConfig` resolves DeepSeek through `@Qualifier("openAiChatModel")` while the DeepSeek starter registers `deepSeekChatModel`; if chat silently answers via Ollama, start there.

### Frontend

- `utils/request.ts` unwraps `Result` and returns `Promise<T>` of `data`; blob responses pass through; a non-200 business code shows `ElMessage.error(res.message)` and rejects; HTTP 401 logs out. Don't wrap calls in a generic `catch` that re-toasts "提交失败" — it hides the backend's specific validation message.
- `api/chat.ts` uses raw `fetch`/`EventSource` (SSE), not axios. Everything else goes through `request`.
- `vue`, `vue-router`, `pinia` APIs and `<el-*>` components are auto-imported; `ElMessage`, `ElMessageBox`, icons and `FormInstance`/`FormRules` types are not. The three `element-plus/es/components/*/style/index` imports in `main.ts` are required for function-style components and look redundant — keep them.
- Router guard: no token → `/login`; `meta.allowedTypes` not containing `userInfo.userType` → `/dashboard`. Token and `userInfo` persist in `localStorage`.
- `views/school/index.vue` is the canonical CRUD page (search bar, table, pagination, dialog, `defaultForm()` factory, deep-copy on edit). Derive TS interfaces from the backend `*Request`/`*Response` DTOs, not from guesses — the term module previously shipped with every field name wrong (backend is `year` + `term` with no `termName`).

## Conventions and things not to "clean up"

- Comments prefixed `ponytail:` record deliberate, non-obvious decisions. Read them before changing the code they annotate.
- Test naming: `{ClassName}Test`, methods `should{Action}_When{Condition}_Then{Outcome}` (e.g. `shouldInputScore_WhenValidData_ThenSuccess`).
- Commits: `<type>: <description>` with feat/fix/refactor/docs/test/chore/perf/ci.
- `docs/` is mostly historical progress reports and is partly wrong (e.g. `API_REFERENCE.md` describes a `/api/v1` context path and `/api/v1/grades` controller prefixes that no longer exist; `FRONTEND_GUIDE.md` shows `baseURL: '/api'`). `SchoolSystemPlan.md` is the original plan. Trust the code and the startup log over any document, including README.md.
- To obtain a captcha for scripts when it is enabled: `GET /auth/captcha` returns `captchaKey`; the plaintext code is readable from Redis at `captcha:code:<key>`.
