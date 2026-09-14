# 学生成绩管理系统 - 前端

基于 Vue 3 + TypeScript + Element Plus 的现代化前端应用。

## 技术栈

- **框架**: Vue 3 (Composition API)
- **语言**: TypeScript
- **构建工具**: Vite
- **UI组件库**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router
- **HTTP客户端**: Axios
- **图表**: ECharts

## 项目结构

```
frontend/
├── src/
│   ├── api/              # API接口
│   ├── assets/           # 静态资源
│   ├── components/       # 公共组件
│   ├── layouts/          # 布局组件
│   ├── router/           # 路由配置
│   ├── stores/           # 状态管理
│   ├── styles/           # 全局样式
│   ├── utils/            # 工具函数
│   ├── views/            # 页面视图
│   │   ├── login/        # 登录页
│   │   ├── dashboard/    # 首页
│   │   ├── school/       # 学校管理
│   │   ├── exam/         # 考试管理
│   │   ├── score/        # 成绩管理
│   │   ├── stat/         # 统计分析
│   │   └── system/       # 系统管理
│   ├── App.vue
│   └── main.ts
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## 快速开始

### 安装依赖

```bash
cd frontend
npm install
```

### 开发环境

```bash
npm run dev
```

访问: http://localhost:3000

### 生产构建

```bash
npm run build
```

## 功能模块

### 已完成
- ✅ 登录页面
- ✅ 主布局
- ✅ Dashboard首页
- ✅ 路由配置
- ✅ 状态管理
- ✅ API封装

### 待开发
- ⏳ 基础数据管理（学校、学期、年级、班级、科目、学生、教师）
- ⏳ 考试管理
- ⏳ 成绩管理
- ⏳ 统计分析
- ⏳ 系统管理

## API配置

后端API地址配置在 `vite.config.ts`:

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    }
  }
}
```

## 默认账号

```
用户名: admin
密码: admin123
```

## 开发规范

- 使用 Composition API
- 使用 TypeScript 类型定义
- 组件命名采用 PascalCase
- 文件命名采用 kebab-case
- 样式使用 SCSS

## 注意事项

1. 确保后端服务已启动（http://localhost:8080）
2. Node.js 版本需要 >= 16
3. 推荐使用 pnpm 或 npm
