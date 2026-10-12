# 测试框架配置文档

## 概述

本项目配置了完整的测试框架，包括单元测试（Vitest）和端到端测试（Playwright）。

## 测试框架技术栈

- **Vitest**: 单元测试框架，与 Vite 无缝集成
- **@vue/test-utils**: Vue 组件测试工具
- **Playwright**: 端到端测试框架
- **jsdom/happy-dom**: DOM 模拟环境

## 目录结构

```
frontend/
├── tests/
│   ├── unit/                    # 单元测试
│   │   ├── components/          # 组件测试
│   │   ├── stores/              # 状态管理测试
│   │   └── utils/               # 工具函数测试
│   ├── e2e/                     # E2E测试
│   │   ├── login.spec.ts        # 登录流程测试
│   │   └── fixtures/            # 测试固件
│   │       └── auth.ts          # 认证相关固件
│   └── setup.ts                 # 测试环境配置
├── vitest.config.ts             # Vitest配置
└── playwright.config.ts         # Playwright配置
```

## 测试命令

### 单元测试

```bash
# 运行所有单元测试
npm run test

# 以UI模式运行测试
npm run test:ui

# 生成覆盖率报告
npm run test:coverage

# 监听模式（开发时使用）
npm run test -- --watch
```

### E2E测试

```bash
# 运行所有E2E测试
npm run test:e2e

# 以UI模式运行E2E测试
npm run test:e2e:ui

# 调试模式运行E2E测试
npm run test:e2e:debug

# 仅在Chromium运行测试
npm run test:e2e -- --project=chromium
```

## 安装Playwright浏览器

首次运行E2E测试前需要安装浏览器：

```bash
cd frontend
npx playwright install
```

## 编写测试

### 单元测试示例

#### 测试工具函数

```typescript
// tests/unit/utils/example.spec.ts
import { describe, it, expect } from 'vitest'
import { yourFunction } from '@/utils/example'

describe('yourFunction', () => {
  it('should return correct result', () => {
    const result = yourFunction('input')
    expect(result).toBe('expected')
  })
})
```

#### 测试Vue组件

```typescript
// tests/unit/components/Button.spec.ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Button from '@/components/Button.vue'

describe('Button Component', () => {
  it('should render text correctly', () => {
    const wrapper = mount(Button, {
      props: { text: 'Click me' }
    })
    expect(wrapper.text()).toContain('Click me')
  })

  it('should emit click event', async () => {
    const wrapper = mount(Button)
    await wrapper.trigger('click')
    expect(wrapper.emitted()).toHaveProperty('click')
  })
})
```

#### 测试Pinia Store

```typescript
// tests/unit/stores/user.spec.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'

describe('User Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should set token correctly', () => {
    const store = useUserStore()
    store.setToken('test-token')
    expect(store.token).toBe('test-token')
  })
})
```

### E2E测试示例

#### 基础测试

```typescript
// tests/e2e/example.spec.ts
import { test, expect } from '@playwright/test'

test('should navigate to home page', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveTitle(/学生成绩管理系统/)
})
```

#### 使用认证Fixture

```typescript
// tests/e2e/protected.spec.ts
import { test, expect } from './fixtures/auth'

test('should access protected page', async ({ authenticatedPage }) => {
  await authenticatedPage.goto('/dashboard')
  await expect(authenticatedPage.locator('.dashboard')).toBeVisible()
})
```

## 覆盖率要求

项目配置了80%的覆盖率阈值：

- 行覆盖率: 80%
- 函数覆盖率: 80%
- 分支覆盖率: 80%
- 语句覆盖率: 80%

运行 `npm run test:coverage` 后，报告将生成在 `coverage/` 目录。

## Mock配置

### 全局Mock

在 `tests/setup.ts` 中配置了以下全局Mock：

- Element Plus 消息组件
- Vue Router

### 自定义Mock示例

```typescript
import { vi } from 'vitest'

// Mock axios
vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    // ...
  }
}))
```

## 常见问题

### 1. 测试中组件样式不生效

Vitest使用jsdom，不会加载真实CSS。如果需要测试样式，考虑使用E2E测试。

### 2. Element Plus组件未正确渲染

确保在测试中正确导入和注册Element Plus组件，或使用全局注册。

### 3. Playwright浏览器启动失败

运行 `npx playwright install` 重新安装浏览器。

### 4. 测试超时

增加超时时间：

```typescript
// Vitest
test('long running test', async () => {
  // test code
}, 10000) // 10秒

// Playwright
test('long running test', async ({ page }) => {
  test.setTimeout(30000) // 30秒
  // test code
})
```

## 最佳实践

1. **测试隔离**: 每个测试应该独立运行，不依赖其他测试
2. **使用beforeEach**: 在每个测试前重置状态
3. **清晰的测试名称**: 使用描述性的测试名称
4. **遵循AAA模式**: Arrange（准备）、Act（执行）、Assert（断言）
5. **避免测试实现细节**: 测试行为而非实现
6. **保持测试简单**: 一个测试只验证一个行为
7. **使用Fixtures**: 复用常见的测试设置

## CI/CD集成

测试可以轻松集成到CI/CD流程中：

```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: npm ci
      - run: npm run test:coverage
      - run: npx playwright install
      - run: npm run test:e2e
```

## 更多资源

- [Vitest 文档](https://vitest.dev/)
- [Vue Test Utils 文档](https://test-utils.vuejs.org/)
- [Playwright 文档](https://playwright.dev/)
