<template>
  <!-- 悬浮按钮 -->
  <div class="chat-fab" @click="toggleOpen" title="智能助手">
    <el-icon :size="24"><ChatDotRound /></el-icon>
  </div>

  <!-- 对话面板 -->
  <Transition name="chat-slide">
    <div v-if="isOpen" class="chat-panel">
      <!-- 头部 -->
      <div class="chat-header">
        <span>智能助手</span>
        <div class="chat-header-actions">
          <el-tooltip content="清除对话">
            <el-icon class="action-icon" @click="clearSession"><Delete /></el-icon>
          </el-tooltip>
          <el-icon class="action-icon" @click="toggleOpen"><Close /></el-icon>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="chat-empty">
          <p>你好！我是 SchoolSystem 智能助手</p>
          <p>你可以问我：</p>
          <div class="chat-suggestions">
            <span v-for="s in suggestions" :key="s" class="suggestion" @click="sendSuggestion(s)">
              {{ s }}
            </span>
          </div>
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          :class="['message', msg.role]"
        >
          <div class="message-content">
            <!-- ponytail: D-ai-3 说明文字/图表/下载按钮三块并列渲染而不是 v-if/v-else 互斥，否则带说明文字的图表消息会把文字丢掉 -->
            <!-- 说明文字 -->
            <div v-if="msg.content" class="text-content" v-html="renderText(msg.content)" />
            <!-- 图表 -->
            <div v-if="msg.chartOption" class="chart-wrapper">
              <div
                :ref="el => mountChart(el as HTMLElement | null, msg.chartOption!)"
                style="height:300px"
              />
            </div>
            <!-- 下载按钮 -->
            <div v-if="msg.downloadUrl" class="download-wrapper">
              <el-button type="primary" size="small" @click="download(msg.downloadUrl!)">
                <el-icon><Download /></el-icon> 下载报表
              </el-button>
            </div>
          </div>
          <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
        </div>

        <!-- 流式加载中 -->
        <div v-if="streaming" class="message assistant">
          <div class="message-content">
            <div class="text-content" v-html="renderText(streamingDisplayContent)" />
            <span class="cursor">▌</span>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          :disabled="streaming"
          resize="none"
          @keydown.enter.exact.prevent="send"
        />
        <el-button
          type="primary"
          :loading="streaming"
          :disabled="!inputText.trim()"
          @click="send"
        >
          发送
        </el-button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, computed } from 'vue'
import { ChatDotRound, Delete, Close, Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { createChatStream, getChatHistory, clearChatSession } from '@/api/chat'
import { service } from '@/utils/request'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  chartOption?: EChartsOption
  downloadUrl?: string
}

const isOpen = ref(false)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
const streamingContent = ref('')
const messagesRef = ref<HTMLElement>()

// ponytail: 函数式 ref 直接 init，沿用 stat/index.vue 的 echarts 用法，不引入 vue-echarts
const mountChart = (el: HTMLElement | null, option: object) => {
  if (!el) return
  const inst = echarts.getInstanceByDom(el) ?? echarts.init(el)
  inst.setOption(option as echarts.EChartsOption)
}

// ponytail: sessionId 用 timestamp 生成，够用
const sessionId = ref(Date.now().toString())

interface ChartJsonSpan {
  /** 纯 JSON 文本（不含 ```json 围栏） */
  json: string
  /** 应从原文移除的区间起点（含围栏） */
  start: number
  /** 应从原文移除的区间终点（含围栏，开区间） */
  end: number
}

const CHART_MARKER = '"__chart__"'
const CHART_PLACEHOLDER = '正在生成图表...'

// 从 start 处的 { 开始做括号计数，跳过字符串内的 { } 与转义引号；返回配对 } 之后的下标，未配对返回 -1
const findBalancedObjectEnd = (content: string, start: number): number => {
  let depth = 0
  let inString = false
  for (let i = start; i < content.length; i++) {
    const ch = content[i]
    if (inString) {
      if (ch === '\\') i++
      else if (ch === '"') inString = false
      continue
    }
    if (ch === '"') inString = true
    else if (ch === '{') depth++
    else if (ch === '}' && --depth === 0) return i + 1
  }
  return -1
}

const isChartObject = (json: string): boolean => {
  try {
    const parsed: unknown = JSON.parse(json)
    return typeof parsed === 'object' && parsed !== null && '__chart__' in parsed
  } catch (_) {
    return false
  }
}

// ponytail: 之前的惰性正则 /\{[\s\S]*?"__chart__"[\s\S]*?\}/ 在第一个 } 就截断，嵌套的 ECharts option 永远 JSON.parse 失败；
// 改为先定位 "__chart__"，从它前面最近的 { 起逐个向外用括号计数找配对的 }（```json 围栏内同样用括号计数而不是正则），
// 直到解析出一个含 __chart__ 的对象；围栏标记一并纳入移除区间
const extractChartJson = (content: string): ChartJsonSpan | null => {
  const marker = content.indexOf(CHART_MARKER)
  if (marker < 0) return null
  let start = content.lastIndexOf('{', marker)
  while (start >= 0) {
    const end = findBalancedObjectEnd(content, start)
    if (end > marker) {
      const json = content.slice(start, end)
      if (isChartObject(json)) {
        const fenceOpen = content.slice(0, start).match(/```(?:json)?\s*$/i)
        const fenceClose = content.slice(end).match(/^\s*```/)
        return {
          json,
          start: fenceOpen ? start - fenceOpen[0].length : start,
          end: fenceClose ? end + fenceClose[0].length : end,
        }
      }
    }
    start = start > 0 ? content.lastIndexOf('{', start - 1) : -1
  }
  return null
}

const withoutChartMarker = (parsed: Record<string, unknown>): EChartsOption =>
  Object.fromEntries(Object.entries(parsed).filter(([key]) => key !== '__chart__')) as EChartsOption

// 流式显示时把 JSON 块替换成占位文字，避免用户看到原始 JSON
const streamingDisplayContent = computed(() => {
  const content = streamingContent.value
  const chart = extractChartJson(content)
  if (chart) {
    return (content.slice(0, chart.start) + CHART_PLACEHOLDER + content.slice(chart.end)).trim()
  }
  // JSON 还没流完整（括号未配对）：从围栏或第一个 { 起全部替换为占位文字
  const marker = content.indexOf(CHART_MARKER)
  if (marker < 0) return content.trim()
  const candidates = [content.lastIndexOf('```', marker), content.indexOf('{')].filter(i => i >= 0 && i <= marker)
  const cut = candidates.length ? Math.min(...candidates) : marker
  return (content.slice(0, cut) + CHART_PLACEHOLDER).trim()
})

const suggestions = [
  '查看班级考试统计',
  '分析学生成绩趋势',
  '生成成绩对比图表',
  '导出班级成绩报表',
]

const toggleOpen = () => { isOpen.value = !isOpen.value }

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// ponytail: D-ai-7 __download__ 标记不再要求位于开头：在任意位置用 /__download__:(\S+)/ 提取，URL 取到空白为止，
// 标记移除后剩余文字作说明与下载按钮并列渲染（与 extractChartJson 同一思路）
const DOWNLOAD_MARKER = /__download__:(\S+)/
// 模型常把句号、右括号、反引号粘在 URL 末尾，这些不属于 URL
const TRAILING_PUNCTUATION = /[。，、！？；：）)\]】」》`'"*]+$/

interface DownloadSpan {
  /** 移除标记后的剩余文字 */
  text: string
  downloadUrl: string
}

const extractDownload = (content: string): DownloadSpan | null => {
  const match = DOWNLOAD_MARKER.exec(content)
  if (!match) return null
  const text = (content.slice(0, match.index) + content.slice(match.index + match[0].length)).trim()
  return { text, downloadUrl: match[1].replace(TRAILING_PUNCTUATION, '') }
}

const parseContent = (content: string): Omit<ChatMessage, 'role' | 'timestamp'> => {
  const download = extractDownload(content)
  const body = download ? download.text : content
  const downloadPart = download ? { downloadUrl: download.downloadUrl } : {}
  // 括号计数截取裸 JSON 或 ```json ... ``` 代码块中的 __chart__ JSON
  const chart = extractChartJson(body)
  if (chart) {
    try {
      const option = withoutChartMarker(JSON.parse(chart.json) as Record<string, unknown>)
      // 移除整个 JSON 块（含代码块标记），剩余作说明文字
      const text = (body.slice(0, chart.start) + body.slice(chart.end)).trim()
      return { content: text, chartOption: option, ...downloadPart }
    } catch (_) { /* 解析失败当普通文本 */ }
  }
  return { content: body, ...downloadPart }
}

const send = async () => {
  const text = inputText.value.trim()
  if (!text || streaming.value) return

  inputText.value = ''
  messages.value.push({ role: 'user', content: text, timestamp: new Date() })
  scrollToBottom()

  streaming.value = true
  streamingContent.value = ''

  const es = createChatStream(text, sessionId.value)
  let fullContent = ''

  es.onmessage = (event) => {
    fullContent += event.data
    streamingContent.value = fullContent
    scrollToBottom()
  }

  const finish = () => {
    es.close()
    streaming.value = false
    if (fullContent) {
      const parsed = parseContent(fullContent)
      messages.value.push({ role: 'assistant', timestamp: new Date(), ...parsed })
    }
    streamingContent.value = ''
    scrollToBottom()
  }

  // SSE 完成（后端关闭连接）
  es.addEventListener('close', finish)
  es.onerror = () => {
    if (!fullContent) ElMessage.error('助手响应失败，请重试')
    finish()
  }
}

const sendSuggestion = (text: string) => {
  inputText.value = text
  send()
}

interface DownloadPayload {
  blob: Blob
  filename: string | null
}

const DEFAULT_DOWNLOAD_NAME = '成绩报表.xlsx'

// 解析 Content-Disposition：优先 RFC 5987 的 filename*=utf-8''xxx（后端 ExportServiceImpl 用的就是这种），其次 filename="xxx"
const parseFilename = (disposition: string): string | null => {
  const encoded = disposition.match(/filename\*=utf-8''([^;]+)/i)
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim())
    } catch (_) {
      return encoded[1].trim()
    }
  }
  const plain = disposition.match(/filename="?([^";]+)"?/i)
  return plain ? plain[1].trim() : null
}

// Safari 在用户确认下载后才异步读取 blob: URL，释放得太早下载会静默失败（FileSaver.js 为此延迟释放）
const REVOKE_OBJECT_URL_DELAY_MS = 1000

const triggerBlobDownload = (blob: Blob, filename: string) => {
  const objectUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = filename
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  // ponytail: D-ai-8 之前 click 后同步 revokeObjectURL，Safari 下载还没开始读 blob 就被释放；延迟 1 秒再释放，并先把 a 从 DOM 移除
  setTimeout(() => {
    a.remove()
    URL.revokeObjectURL(objectUrl)
  }, REVOKE_OBJECT_URL_DELAY_MS)
}

// ponytail: 之前 a.href=url 直开新窗口，请求既没有 baseURL 前缀也不带 Authorization，必 401（跨组契约 4：后端只给相对 API 根的路径）；
// 改走 utils/request 的 axios 实例：baseURL(/api/v1) 与 token 由拦截器处理，blob 落地后再用 a.download 触发保存。
// 响应拦截器对 blob 只回传 response.data，拿不到响应头，所以用 transformResponse 把 Content-Disposition 一起带出来。
const download = async (url: string) => {
  try {
    const payload = await service.get<unknown, DownloadPayload>(url, {
      responseType: 'blob',
      transformResponse: [(data: Blob, headers) => ({
        blob: data,
        filename: parseFilename(String(headers['content-disposition'] ?? '')),
      })],
    })
    if (payload.blob.type.includes('application/json')) {
      // 后端 BusinessException 以 HTTP 200 + JSON 返回，blob 请求下拦截器不会解包，这里自己读 message
      const result = JSON.parse(await payload.blob.text()) as { message?: string }
      ElMessage.error(result.message || '下载失败')
      return
    }
    triggerBlobDownload(payload.blob, payload.filename ?? DEFAULT_DOWNLOAD_NAME)
  } catch (_) {
    // HTTP/网络错误已由 request.ts 响应拦截器统一提示，这里不再重复弹窗
  }
}

const clearSession = async () => {
  await ElMessageBox.confirm('确定清除本次对话记录？', '提示', { type: 'warning' })
  await clearChatSession(sessionId.value)
  sessionId.value = Date.now().toString()
  messages.value = []
}

const renderText = (text: string): string => {
  // 简单转义 + 换行处理，避免引入 marked 等依赖
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br/>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}

const formatTime = (d: Date): string => {
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

onMounted(async () => {
  // 加载历史对话
  try {
    const history = await getChatHistory(sessionId.value)
    messages.value = history.map(m => ({
      role: m.role as 'user' | 'assistant',
      timestamp: new Date(m.timestamp),
      ...parseContent(m.content)
    }))
  } catch (_) { /* 无历史，正常 */ }
})
</script>

<style scoped lang="scss">
.chat-fab {
  position: fixed;
  bottom: 32px;
  right: 32px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.5);
  z-index: 1000;
  transition: transform 0.2s;

  &:hover { transform: scale(1.1); }
}

.chat-panel {
  position: fixed;
  bottom: 96px;
  right: 32px;
  width: 420px;
  height: 600px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  z-index: 999;
  overflow: hidden;
}

.chat-header {
  padding: 14px 16px;
  background: #409eff;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .chat-header-actions {
    display: flex;
    gap: 10px;
  }

  .action-icon {
    cursor: pointer;
    opacity: 0.85;
    &:hover { opacity: 1; }
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-empty {
  color: #909399;
  font-size: 13px;
  text-align: center;
  margin-top: 20px;

  p { margin: 4px 0; }

  .chat-suggestions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    justify-content: center;
    margin-top: 12px;
  }

  .suggestion {
    background: #ecf5ff;
    color: #409eff;
    border-radius: 12px;
    padding: 4px 10px;
    font-size: 12px;
    cursor: pointer;
    &:hover { background: #d9ecff; }
  }
}

.message {
  display: flex;
  flex-direction: column;
  max-width: 85%;

  &.user {
    align-self: flex-end;
    align-items: flex-end;

    .message-content {
      background: #409eff;
      color: #fff;
      border-radius: 12px 2px 12px 12px;
    }
  }

  &.assistant {
    align-self: flex-start;

    .message-content {
      background: #f5f7fa;
      color: #303133;
      border-radius: 2px 12px 12px 12px;
    }
  }

  .message-content {
    padding: 10px 14px;
    font-size: 13px;
    line-height: 1.6;
    word-break: break-word;
  }

  .message-time {
    font-size: 11px;
    color: #c0c4cc;
    margin-top: 3px;
    padding: 0 4px;
  }

  .chart-wrapper {
    min-width: 300px;
  }

  .download-wrapper {
    padding: 4px 0;
  }

  .cursor {
    animation: blink 1s step-end infinite;
  }
}

.chat-input {
  padding: 12px;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 8px;
  align-items: flex-end;

  .el-textarea { flex: 1; }
  .el-button { height: 56px; }
}

.chat-slide-enter-active,
.chat-slide-leave-active {
  transition: all 0.25s ease;
}

.chat-slide-enter-from,
.chat-slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
