# 测试框架快速开始指南

## 1. 首次设置

### 安装依赖

依赖已通过以下命令安装：

```bash
cd frontend

# 单元测试依赖
npm install -D vitest@2.1.8 @vue/test-utils jsdom @vitest/ui@2.1.8 @vitest/coverage-v8@2.1.8 happy-dom axios-mock-adapter @testing-library/jest-dom --legacy-peer-deps

# E2E测试依赖
npm install -D @playwright/test --legacy-peer-deps
```

### 安装Playwright浏览器

```bash
cd frontend
npx playwright install
```

这将下载Chromium、Firefox和WebKit浏览器。

## 2. 运行测试

### 运行单元测试

```bash
# 运行所有单元测试
npm run test

# 以监听模式运行（推荐开发时使用）
npm run test -- --watch

# 运行特定测试文件
npm run test tests/unit/utils/request.spec.ts

# 使用UI界面运行测试（可视化界面）
npm run test:ui
```

### 生成覆盖率报告

```bash
npm run test:coverage
```

报告将生成在 `coverage/` 目录，打开 `coverage/index.html` 查看详细报告。

### 运行E2E测试

⚠️ **重要**: E2E测试需要开发服务器运行

```bash
# 终端1: 启动开发服务器
npm run dev

# 终端2: 运行E2E测试
npm run test:e2e

# 或使用UI模式（推荐）
npm run test:e2e:ui

# 调试模式（逐步执行）
npm run test:e2e:debug
```

## 3. 测试文件说明

### 已创建的测试文件

```
tests/
├── unit/
│   ├── components/
│   │   └── example.spec.ts          # 组件测试模板
│   ├── stores/
│   │   └── user.spec.ts             # 用户Store测试
│   └── utils/
│       ├── request.spec.ts          # HTTP请求工具测试
│       └── helpers.spec.ts          # 工具函数测试模板
├── e2e/
│   ├── login.spec.ts                # 登录流程E2E测试
│   ├── score-input.spec.ts          # 成绩录入E2E测试
│   └── fixtures/
│       └── auth.ts                  # 认证fixture
├── setup.ts                         # 测试环境配置
└── README.md                        # 详细文档
```

## 4. 编写第一个测试

### 示例：测试一个简单函数

创建 `src/utils/math.ts`:

```typescript
export function add(a: number, b: number): number {
  return a + b
}
```

创建 `tests/unit/utils/math.spec.ts`:

```typescript
import { describe, it, expect } from 'vitest'
import { add } from '@/utils/math'

describe('add', () => {
  it('should add two numbers correctly', () => {
    expect(add(1, 2)).toBe(3)
    expect(add(-1, 1)).toBe(0)
    expect(add(0, 0)).toBe(0)
  })
})
```

运行测试：

```bash
npm run test -- math.spec.ts
```

## 5. 常用测试命令

```bash
# 单元测试
npm run test                    # 运行所有单元测试
npm run test:ui                 # UI模式
npm run test:coverage           # 生成覆盖率报告
npm run test -- --watch         # 监听模式
npm run test -- <file>          # 运行特定文件

# E2E测试
npm run test:e2e                # 运行所有E2E测试
npm run test:e2e:ui             # UI模式
npm run test:e2e:debug          # 调试模式
npm run test:e2e -- --project=chromium  # 仅在Chromium运行
```

## 6. 验证配置

运行以下命令验证测试框架配置正确：

```bash
# 验证单元测试
npm run test

# 验证覆盖率生成
npm run test:coverage

# 验证E2E测试（需要先启动dev服务器）
npm run dev          # 在另一个终端
npm run test:e2e     # 运行E2E测试
```

## 7. 查看测试报告

### 单元测试覆盖率报告

```bash
npm run test:coverage
# 打开 coverage/index.html 查看报告
```

### E2E测试报告

```bash
npm run test:e2e
# 如果有失败的测试，自动打开报告
# 或手动打开: npx playwright show-report
```

## 8. 下一步

1. 根据实际组件编写单元测试
2. 为关键用户流程编写E2E测试
3. 确保达到80%的代码覆盖率
4. 将测试集成到CI/CD流程

查看 `tests/README.md` 了解更多详细信息和最佳实践。

## 9. 故障排除

### 问题：Vitest找不到模块

解决：检查 `vitest.config.ts` 中的路径别名配置

### 问题：Playwright浏览器启动失败

解决：
```bash
npx playwright install --force
```

### 问题：测试超时

解决：在测试中增加超时时间或优化测试代码

### 问题：Mock不生效

解决：确保在 `tests/setup.ts` 中正确配置了Mock

## 10. 获取帮助

- Vitest文档: https://vitest.dev/
- Vue Test Utils: https://test-utils.vuejs.org/
- Playwright文档: https://playwright.dev/
- 项目内文档: `tests/README.md`
