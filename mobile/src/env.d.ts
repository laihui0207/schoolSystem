/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 根地址（含 /api/v1），缺省 http://localhost:8080/api/v1 */
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}
