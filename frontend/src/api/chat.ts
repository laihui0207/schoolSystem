import { useUserStore } from '@/stores/user'

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
