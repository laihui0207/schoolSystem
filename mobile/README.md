# 成绩查询 — 学生 / 家长移动端（uni-app）

独立于 `frontend/` 的移动端工程：**uni-app（Vue 3 + Vite 5 + TypeScript strict）**，复用后端 `/api/v1` 接口，
一套代码编译到 H5 与微信小程序。只面向学生（`userType=4`）与家长（`userType=5`）。

```
mobile/
├─ src/
│  ├─ pages/
│  │  ├─ login/index.vue          账号密码登录（图形验证码）/ 手机验证码（后端存在 /auth/login/phone 时才显示）
│  │  ├─ index/index.vue          首页：最近一次已发布考试总分/排名、未读通知数、快捷入口
│  │  ├─ score/list.vue           已发布考试列表（GET /exams?status=3）
│  │  ├─ score/detail.vue         各科成绩 + 单科/总分名次（GET /stats/personal + GET /scores）
│  │  ├─ trend/index.vue          历次总分折线 + 各科得分率雷达（GET /stats/student/{id}/trend）
│  │  ├─ notification/index.vue   通知列表、标记已读、tabBar 未读角标
│  │  └─ mine/index.vue           个人信息、家长切换孩子、下载成绩单 PDF、退出
│  ├─ api/                        每个后端资源一个文件，类型来自 src/types
│  ├─ components/                 ChildSwitcher（家长切孩子）、TrendCharts（H5 echarts）、TrendBars（全端条形）
│  ├─ composables/                useCurrentStudent（当前被查看的 student.id）、useUnreadBadge、useChildren
│  ├─ store/user.ts               会话（token / userInfo / 选中孩子），持久化到 uni storage
│  ├─ utils/request.ts            uni.request 封装：Bearer、Result 解包、401 → refresh → 重放
│  ├─ types/index.ts              与后端 DTO 一一对应的 TS 类型
│  ├─ pages.json                  路由与 tabBar（首页 / 成绩 / 消息 / 我的）
│  └─ manifest.json               应用配置（小程序 appid 在这里填）
├─ scripts/gen-tabbar-icons.mjs   生成 tabBar PNG 图标（已生成，无需重跑）
└─ .env.example                   API 地址示例
```

## 开发（H5）

```bash
cd mobile
npm install
npm run dev:h5            # http://localhost:5173（端口被占会顺延，看终端输出）
```

后端按仓库根 README 启动在 `http://localhost:8080`，context-path 为 `/api/v1`。
`SecurityConfig` 已配置 `allowedOriginPatterns("*")`，H5 开发直接跨域请求后端，**不走 Vite 代理**。

登录账号：seed 学生 `seed-20000001…` 密码 `123456`（见根 CLAUDE.md）。dev profile 关闭了图形验证码，
验证码框里随便填即可；验证码接口不可用时验证码行会自动隐藏。

## 修改 API 地址

默认 `http://localhost:8080/api/v1`。通过 Vite 环境变量覆盖：

```bash
cp .env.example .env.development      # 或 .env.production
# 编辑 VITE_API_BASE=https://your-host/api/v1
```

- `npm run dev:*` 读取 `.env.development`，`npm run build:*` 读取 `.env.production`。
- 地址末尾不要带 `/`；必须包含 `/api/v1`（后端 context-path）。
- 「我的」页底部会显示当前生效的服务地址，便于真机排查。

## 构建

```bash
npm run build:h5          # 产物 dist/build/h5，静态托管即可（hash 路由，不需要服务端 rewrite）
npm run build:mp-weixin   # 产物 dist/build/mp-weixin，用微信开发者工具导入
npm run type-check        # vue-tsc --noEmit，必须零错误
```

## 微信小程序

1. `src/manifest.json` → `mp-weixin.appid` 填入小程序 AppID。
2. 微信公众平台 → 开发管理 → 开发设置 → **服务器域名**：把后端域名加入 `request 合法域名` 与 `downloadFile 合法域名`
   （下载成绩单 PDF 用 `uni.downloadFile`）。域名必须是 HTTPS 且已备案；本地调试在开发者工具里勾选「不校验合法域名」。
3. 后端 CORS 对小程序无意义（小程序不发 Origin 校验），`SecurityConfig` 的 `allowedOriginPatterns *` 已足够覆盖 H5。
4. **已知限制**：`wx.request` 不支持 `PATCH`，而「标记已读」只有 `PATCH /notifications/{id}/read`、
   `PATCH /notifications/read-all`。小程序端这两个操作会失败（H5 不受影响）。需要后端为这两个端点增加 `POST` 别名，
   或在 `src/api/notification.ts` 里切到别名。
5. 小程序端趋势页不加载 echarts（`TrendCharts.vue` 仅 `#ifdef H5` 引入），用 `TrendBars.vue` 的条形视图代替。

## 身份与数据范围

- 学生：所有查询用 `userInfo.studentId`（`student` 表主键），**不是** `userInfo.id`。
- 家长：用 `userInfo.childStudentIds[选中项]`，选择持久化在 storage；没有孩子时各页面提示「联系学校绑定」。
  后端 `canViewStudent` 负责校验家长只能看自己的孩子。
- 后端对学生/家长强制只返回已发布考试（`status=3`）的成绩，前端只是再筛一次。

## 请求层约定（`src/utils/request.ts`）

- 后端信封 `Result{code,message,data}`：`code===200` 返回 `data`，否则 toast `message` 并 reject `ApiError{code,httpStatus}`。
- HTTP 401：用 `refreshToken` 调 `POST /auth/refresh?refreshToken=` 一次（并发共用同一个 Promise），成功后重放原请求；
  失败清会话 `reLaunch` 到登录页。`/auth/login|refresh|logout` 自身的 401 不触发刷新。
- 二进制（PDF）不走该封装：H5 用 `fetch` 带 Bearer 头取 blob 再触发下载；App/小程序用 `uni.downloadFile` + `uni.openDocument`。
- 新增接口：在 `src/api/<resource>.ts` 里加函数，类型放 `src/types/index.ts`，字段名以后端 Java DTO 为准。

## 与后端的契约缺口（需要后端配合时）

- `PersonalStatResponse.SubjectScore` 没有班级均分；`/stats/class` 对学生/家长是 403。
  前端已预留 `classAverage?: number`，后端补上该字段后成绩详情会自动出现「班均」列。
- 手机验证码登录依赖 `POST /auth/login/phone` 与 `POST /auth/sms/send?phone=`；端点不存在时登录页自动隐藏该 Tab。
- 小程序 `PATCH` 限制见上。
