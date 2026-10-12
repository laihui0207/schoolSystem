import { useUserStore } from '@/stores/user'

// ponytail: 本文件用原生 fetch/EventSource（SSE 走不了 axios），所以无法复用 utils/request.ts 的拦截器与 baseURL；
// BASE 的前缀必须与 utils/request.ts 中 axios 实例的 baseURL（'/api/v1'）保持一致，改其中一处时另一处必须同步。
// request.ts 目前没有导出 baseURL 常量，这里只能手写；日后若导出，请改为 import 复用而不是再抄一份。
const BASE = '/api/v1/chat'

export interface MessageDto {
  role: 'user' | 'assistant'
  content: string
  timestamp: string
}

/**
 * SSE 流式对话
 * EventSource 不支持自定义 header，token 通过 query param 传递
 */
export function createChatStream(message: string, sessionId: string): EventSource {
  const userStore = useUserStore()
  const params = new URLSearchParams({
    message,
    sessionId,
    token: userStore.token
  })
  return new EventSource(`${BASE}/stream?${params.toString()}`)
}

export async function getChatHistory(sessionId: string): Promise<MessageDto[]> {
  const userStore = useUserStore()
  const res = await fetch(`${BASE}/history?sessionId=${sessionId}`, {
    headers: { Authorization: `Bearer ${userStore.token}` }
  })
  const json = await res.json()
  return json.data ?? []
}

export async function clearChatSession(sessionId: string): Promise<void> {
  const userStore = useUserStore()
  await fetch(`${BASE}/session?sessionId=${sessionId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${userStore.token}` }
  })
}
