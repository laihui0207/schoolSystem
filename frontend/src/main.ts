import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'

// 函数式调用的组件（ElMessageBox / ElMessage / ElLoading）不出现在模板里，
// unplugin-vue-components 的 resolver 扫不到，样式不会被按需注入，
// 弹窗会失去宽度和定位。这三行必须手动保留，不是冗余。
// ponytail: 只引用到的三个；ElNotification 目前 0 处使用，用到再加一行。
// 走 sass 变体而非 style/css：本地安装未提供预编译的 theme-chalk/*.css，
// 且与 vite.config.ts 里 resolver 的 importStyle: 'sass' 保持一致。
import 'element-plus/es/components/message-box/style/index'
import 'element-plus/es/components/message/style/index'
import 'element-plus/es/components/loading/style/index'

import '@/styles/index.scss'

const app = createApp(App)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
})

app.mount('#app')
