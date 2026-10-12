import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// https://vitejs.dev/config/
// ponytail: 不在这里配 proxy —— 移动端真机/小程序没有 dev server 代理可用，
// API 地址统一走 VITE_API_BASE（见 .env.example），H5 开发时后端已 allowedOriginPatterns * 允许跨域
export default defineConfig({
  plugins: [uni()],
  css: {
    preprocessorOptions: {
      scss: {
        // uni 固定 vite 5.2，只能走 sass legacy API；屏蔽其弃用告警避免刷屏
        silenceDeprecations: ['legacy-js-api']
      }
    }
  }
})
