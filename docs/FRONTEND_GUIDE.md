# 前端开发规范

本文档面向学生成绩管理系统前端（`frontend/`）的开发者，约定项目结构、编码规范与通用开发模式。所有示例均来自当前代码库的真实实现，可直接参考套用。

技术栈：Vue 3.3（`<script setup lang="ts">`）、TypeScript 5.3、Vite 5、Element Plus 2.5、Pinia 2.1、Vue Router 4.2、Axios 1.6、ECharts 5.4。单元测试用 Vitest，端到端测试用 Playwright。

---

## 1. 项目结构

### 目录结构与职责

```
frontend/
├── src/
│   ├── api/            # 接口层：按后端模块拆分，每个文件封装一类资源的 CRUD
│   │   ├── auth.ts     # 认证（登录/登出/刷新令牌）
│   │   ├── school.ts   # 学校
│   │   ├── score.ts    # 成绩
│   │   └── ...         # class / exam / grade / stat / student / subject / teacher / term
│   ├── layouts/        # 布局组件（MainLayout：侧边栏 + 顶栏）
│   ├── router/         # 路由与全局路由守卫
│   ├── stores/         # Pinia 全局状态（user 等）
│   ├── styles/         # 全局样式（index.scss）
│   ├── types/          # 全局 TypeScript 类型（common.ts）
│   ├── utils/          # 工具：request（axios 封装）、errorHandler（错误处理）
│   ├── views/          # 页面，按业务域建目录，页面文件统一为 index.vue
│   ├── App.vue         # 根组件
│   └── main.ts         # 入口：注册 Pinia / Router / Element Plus / 图标
├── tests/              # unit（Vitest）与 e2e（Playwright）
├── tsconfig.json       # TypeScript 配置（strict 模式）
├── vite.config.ts      # 构建与开发服务器（端口 3000，代理 /api/v1 → :8080）
└── vitest.config.ts    # 单元测试配置（jsdom + 80% 覆盖率阈值）
```

按业务域组织（api / views / stores 内均按领域分目录），而非按文件类型堆叠。遵循「小文件、高内聚、低耦合」原则，单文件建议 200-400 行，最多不超过 800 行。

### 文件命名规范

- 页面组件：`views/<域>/index.vue`，路由指向该 `index.vue`（例：`views/school/index.vue`）。
- 接口模块：`api/<域>.ts`，文件名与后端模块对应，小写。
- Store：`stores/<域>.ts`，导出 `use<域>Store`（例：`useUserStore`）。
- 类型文件：`types/*.ts`，接口用 `PascalCase`。
- 组件名（如需自建组件）：`PascalCase.vue`。
- 变量/函数：`camelCase`；常量：`UPPER_SNAKE_CASE`。

---

## 2. 代码规范

### TypeScript 使用规范

项目开启 `strict: true`，并额外启用 `noUnusedLocals`、`noUnusedParameters`、`noFallthroughCasesInSwitch`（见 `tsconfig.json`）。构建脚本为 `vue-tsc && vite build`，类型检查不通过则构建失败。

- 禁用 `any`。用具体类型、泛型或 `unknown`（配合类型收窄）替代。当前代码中残留的少量 `any`（如测试 fixture）属技术债，不应作为范例。
- 优先 `interface` 定义对象结构，联合/工具类型用 `type`。
- 使用路径别名 `@/`（映射到 `src/`），避免 `../../` 相对路径：

```ts
import request from '@/utils/request'
import type { PageResponse } from '@/types/common'
```

- 类型可就近声明：接口模块内定义该资源的实体与查询 DTO；被多处复用的类型（如 `UserInfo`）可从 store 导出再被 api 引用。

### Vue 组件编写规范

- 统一使用 `<script setup lang="ts">` 单文件组件，段落顺序：`<template>` → `<script setup>` → `<style scoped lang="scss">`。
- 样式一律加 `scoped`，避免全局污染；全局样式集中在 `src/styles/index.scss`。
- Element Plus 组件与 `vue`/`vue-router`/`pinia` 的常用 API 已由 `unplugin-auto-import` / `unplugin-vue-components` 自动导入，无需手动 import `ref`、`computed`、`useRouter`、`<el-*>` 等。
- 但以下需显式导入：`ElMessage`、`ElMessageBox`、图标（`@element-plus/icons-vue`）、类型（`FormInstance`、`FormRules`）。

```ts
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
```

### Composition API 使用规范

- 响应式：列表数组、可空对象等用 `ref`；表单、查询条件、分页等聚合对象用 `reactive`。
- 副作用（首屏加载）放在 `onMounted` 中触发。
- 逻辑按「状态声明 → 数据加载 → 事件处理」分组，保持函数短小（建议 < 50 行）。
- 表单实例通过模板引用获取：`const formRef = ref<FormInstance>()`。

---

## 3. 状态管理规范

使用 Pinia，采用组合式（setup）写法：`defineStore('id', () => { ... })`，在函数体内用 `ref` 声明状态、用普通函数声明动作，最后 `return` 需要暴露的成员。

`src/stores/user.ts` 是标准范例：

```ts
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const login = async (username: string, password: string) => {
    const data = await loginApi({ username, password })
    token.value = data.token
    userInfo.value = data.userInfo
    localStorage.setItem('token', data.token)  // 令牌持久化
    return data
  }

  const logout = async () => {
    try { await logoutApi() }
    catch (error) { handleError(error, '登出失败') }
    finally {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
      router.push('/login')
    }
  }

  return { token, userInfo, login, logout }
})
```

要点：
- 令牌手动持久化到 `localStorage`（key 为 `token`），并在 `ref` 初始化时读回；`userInfo` 不持久化，刷新后需重新拉取。
- `UserInfo` 接口从本文件导出，供 api 层 `import type` 复用。

### 何时使用全局状态

- 放入 store：跨页面共享且生命周期长于单个页面的数据——登录令牌、当前用户信息、全局权限/字典等。
- 不放入 store：某个页面自身的列表数据、表单、分页、加载态等，应作为该组件的局部 `ref`/`reactive`（见第 5 节 CRUD 模板），避免全局状态膨胀。

---

## 4. API 调用规范

### request 工具

`src/utils/request.ts` 默认导出一个已配置好的 axios 实例（`request`），本身可直接当函数调用：

```ts
const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',    // 与后端 context-path 一致；开发环境经 Vite 代理原样转发到 http://localhost:8080
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})
export default service
```

拦截器行为（务必理解，直接影响返回值类型）：
- 请求拦截器：若 `useUserStore().token` 存在，自动注入 `Authorization: Bearer <token>`。
- 响应拦截器（成功）：取 `res = response.data`，当 `res.code === 200 || res.code === 0` 时**返回 `res.data`（已拆掉外层信封）**；否则 `ElMessage.error(res.message)` 并 reject。
- 响应拦截器（失败）：按 `error.response.status` 处理 401/403/404/500；401 会 `logout()` 并跳转 `/login`；无响应时提示网络错误。

> 关键点：拦截器在运行时已返回拆包后的业务数据，但 TypeScript 静态类型仍是 `AxiosResponse`。因此调用处必须通过泛型或类型断言补齐类型（见下）。

### 接口模块写法

每个 `api/*.ts`：`import request from '@/utils/request'`，就近声明实体与查询接口，导出箭头函数常量，函数体 `return request({ url, method, params|data })`。

```ts
import request from '@/utils/request'
import type { PageQuery } from '@/types/common'

export interface School {
  id?: number            // id 可选，同一接口兼顾新增与编辑
  schoolName: string
  schoolCode: string
  status: number
}

export interface SchoolQuery extends PageQuery {
  schoolName?: string
  status?: number
}

/** 分页查询学校列表 */
export const getSchoolList = (params: SchoolQuery) =>
  request({ url: '/schools', method: 'get', params })

/** 新增学校 */
export const createSchool = (data: School) =>
  request({ url: '/schools', method: 'post', data })

/** 更新学校 */
export const updateSchool = (id: number, data: School) =>
  request({ url: `/schools/${id}`, method: 'put', data })

/** 删除学校 */
export const deleteSchool = (id: number) =>
  request({ url: `/schools/${id}`, method: 'delete' })
```

约定：
- HTTP 方法用小写字符串（`'get'`/`'post'`/`'put'`/`'delete'`/`'patch'`）。
- 路径参数用模板字符串拼接；查询参数走 `params`，请求体走 `data`。
- 查询 DTO 应 `extends PageQuery` 复用分页字段，不要重复声明 `pageNum`/`pageSize`。

### 类型定义要求

需要类型的返回值有两种等价写法，二选一并保持一致：

```ts
// 写法 A：调用处断言（列表分页页面常用）
const res = await getSchoolList(params) as PageResponse<School>

// 写法 B：接口声明泛型（auth 模块采用）
export const login = (data: LoginParams) =>
  request<LoginResponse>({ url: '/auth/login', method: 'post', data })
```

### 错误处理

统一使用 `src/utils/errorHandler.ts`，不要在业务代码里散落 `console.log`：

```ts
export function handleError(error: unknown, customMessage?: string): void
export async function handleAsyncError<T>(
  asyncFn: () => Promise<T>,
  errorMessage?: string
): Promise<T | null>
```

`handleError` 会收窄 `error` 类型取出可读信息并 `ElMessage.error` 提示。业务中的标准用法是在 `catch` 块调用：

```ts
try {
  // ...业务
} catch (error) {
  handleError(error, '加载数据失败')
}
```

---

## 5. 组件开发模式

### CRUD 页面标准模板

`src/views/school/index.vue` 是标准的增删改查页面范例，其余管理页（student、term、grade、class、subject、teacher）均沿用同一结构。模板由「搜索栏 + 加载态表格 + 分页 + 新增/编辑对话框」构成。

状态声明：

```ts
const loading = ref(false)          // 表格加载态
const submitLoading = ref(false)    // 对话框提交态
const searchForm = reactive({ schoolName: '', status: undefined as number | undefined })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<School[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')         // '新增学校' / '编辑学校'
const formRef = ref<FormInstance>()
```

数据加载与分页（列表接口返回 `PageResponse<T>`，取 `records` 与 `total`）：

```ts
const loadData = async () => {
  loading.value = true
  try {
    const params = { ...searchForm, pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    const res = await getSchoolList(params) as PageResponse<School>
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    handleError(error, '加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.pageNum = 1; loadData() }
onMounted(() => { loadData() })
```

分页组件的 `@size-change` 与 `@current-change` 均回到 `handleSearch`（或直接 `loadData`）重新拉取。

### 表单验证模式

用 Element Plus 的 `FormRules` 声明规则，`el-form` 绑定 `:rules` 与 `ref`，各 `el-form-item` 设置 `prop`：

```ts
const rules: FormRules = {
  schoolName: [{ required: true, message: '请输入学校名称', trigger: 'blur' }],
  schoolCode: [{ required: true, message: '请输入学校代码', trigger: 'blur' }]
}
```

带格式校验时用正则（参考 student 页面）：

```ts
const rules: FormRules = {
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}
```

提交时通过 `formRef.value.validate` 校验后再调接口：

```ts
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateSchool(form.id, form)   // 有 id → 更新
        ElMessage.success('更新成功')
      } else {
        await createSchool(form)            // 无 id → 新增
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      handleError(error, '提交失败')
    } finally {
      submitLoading.value = false
    }
  })
}
```

### 分页处理模式

- 用 `reactive({ pageNum: 1, pageSize: 10, total: 0 })` 维护分页状态。
- 搜索时重置到第 1 页再加载：`handleSearch` 中 `pagination.pageNum = 1`。
- 列表接口统一返回 `PageResponse<T>`：从 `records` 取数据、`total` 取总数。
- `el-pagination` 绑定 `:total="pagination.total"`、`:current-page`、`:page-size`，`@current-change`/`@size-change` 触发重新加载。

删除确认模式（`ElMessageBox.confirm(...).then(...)`）：

```ts
const handleDelete = (row: School) => {
  ElMessageBox.confirm('确定要删除这条记录吗？', '提示', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteSchool(row.id!)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      handleError(error, '删除失败')
    }
  })
}
```

---

## 6. 最佳实践

### 深拷贝与数据隔离

编辑时把表格行数据拷贝进表单，必须深拷贝，避免响应式表单与列表行相互引用（改表单即改表格）：

```ts
const handleEdit = (row: School) => {
  dialogTitle.value = '编辑学校'
  Object.assign(form, JSON.parse(JSON.stringify(row)))  // 深拷贝，切断引用
  dialogVisible.value = true
}
```

> 说明：全局规范提倡「不可变、不直接 mutate」。本项目基于 Element Plus 的惯用法，对 `reactive` 的 `form`/`searchForm`/`pagination` 采用「就地更新」（`Object.assign` / 直接赋值）。关键在于**跨对象赋值时做深拷贝以隔离数据**（如上），以及对可能失败的就地修改做回滚（见下）。新建纯数据对象时仍应优先使用不可变写法。

需要「乐观更新 + 失败回滚」的就地修改（如表格内状态开关）：

```ts
const handleStatusChange = async (row: School) => {
  const previousStatus = row.status
  try {
    await updateSchoolStatus(row.id!, row.status)
    ElMessage.success('状态更新成功')
  } catch (error) {
    handleError(error, '状态更新失败')
    row.status = previousStatus   // 失败回滚
  }
}
```

### 表单重置（defaultForm 工厂）

用工厂函数生成初始值，配合 `Object.assign` 重置，保证每次「新增」都是干净状态：

```ts
const defaultForm = (): School => ({
  id: undefined, schoolName: '', schoolCode: '',
  address: '', contactPhone: '', contactPerson: '', status: 0
})
const form = reactive<School>(defaultForm())
const resetForm = () => { Object.assign(form, defaultForm()) }

const handleAdd = () => { dialogTitle.value = '新增学校'; resetForm(); dialogVisible.value = true }
```

对话框关闭时同时清校验和数据：

```ts
const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}
```

### Loading 状态管理

- 表格加载用 `loading`，绑定到 `v-loading="loading"`；对话框提交用独立的 `submitLoading`，绑定到提交按钮的 `:loading`。
- 一律在 `try/catch/finally` 中管理：`finally` 里复位 loading，确保异常时也能解除。

### 其它约定

- 生产代码不要保留 `console.log`；调试信息统一走 `handleError` 提示或移除。
- 不硬编码魔法值；状态码、类型枚举等应有清晰命名或来自后端字典。
- 深层嵌套不超过 4 层，函数保持短小、单一职责。

---

## 7. 测试

单元测试用 Vitest（`vitest.config.ts`：`jsdom` 环境、`globals: true`、`tests/setup.ts` 初始化、覆盖率阈值 80%）；端到端测试用 Playwright（`playwright.config.ts`：chromium/firefox/webkit，自动拉起 `npm run dev`）。

```bash
npm run test              # 运行单元测试
npm run test:coverage     # 覆盖率报告（输出 coverage/index.html）
npm run test:e2e          # 运行 E2E（需先启动 dev 服务器）
```

单元测试结构示例（`describe` / `it` / `expect`；store 测试先 `setActivePinia(createPinia())`）：

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'

describe('useUserStore', () => {
  beforeEach(() => { setActivePinia(createPinia()) })
  it('should initialize token from localStorage', () => {
    const store = useUserStore()
    expect(store.token).toBe('')
  })
})
```

> 现状提示：当前 `tests/` 下的部分脚手架测试与实现尚未对齐——例如 user store 的单测调用了 `setToken`/`setUserInfo`/`isLoggedIn`（实际 store 仅暴露 `token`/`userInfo`/`login`/`logout`）；request 单测依赖 `axios-mock-adapter`、`@testing-library/jest-dom`，但它们尚未加入 `devDependencies`；E2E 的 `baseURL` 为 5173 而 dev 实际端口是 3000，且引用了尚未实现的 `/score-input` 页面。新增测试时请以真实实现为准，并补齐相应依赖与端口配置。

---

## 附：关键文件索引

- axios 封装：`src/utils/request.ts`
- 错误处理：`src/utils/errorHandler.ts`
- 全局类型：`src/types/common.ts`
- 用户 store：`src/stores/user.ts`
- 路由与守卫：`src/router/index.ts`
- CRUD 参考页：`src/views/school/index.vue`（标准范例）、`src/views/student/index.vue`（含下拉联动、二级对话框）
- 接口层范例：`src/api/school.ts`、`src/api/auth.ts`、`src/api/score.ts`
