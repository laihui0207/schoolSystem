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
            <!-- 图表 -->
            <div v-if="msg.chartOption" class="chart-wrapper">
              <v-chart :option="msg.chartOption" autoresize style="height:300px" />
            </div>
            <!-- 下载按钮 -->
            <div v-else-if="msg.downloadUrl" class="download-wrapper">
              <el-button type="primary" size="small" @click="download(msg.downloadUrl!)">
                <el-icon><Download /></el-icon> 下载报表
              </el-button>
            </div>
            <!-- 普通文本 -->
            <div v-else class="text-content" v-html="renderText(msg.content)" />
          </div>
          <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
        </div>

        <!-- 流式加载中 -->
        <div v-if="streaming" class="message assistant">
          <div class="message-content">
            <div class="text-content" v-html="renderText(streamingContent)" />
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
import { ref, nextTick, onMounted } from 'vue'
import { ChatDotRound, Delete, Close, Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart, RadarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, TitleComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { createChatStream, getChatHistory, clearChatSession } from '@/api/chat'

use([CanvasRenderer, BarChart, LineChart, PieChart, RadarChart,
  GridComponent, TooltipComponent, TitleComponent, LegendComponent])

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  chartOption?: object
  downloadUrl?: string
}

const isOpen = ref(false)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
const streamingContent = ref('')
const messagesRef = ref<HTMLElement>()

// ponytail: sessionId 用 timestamp 生成，够用
const sessionId = ref(Date.now().toString())

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

const parseContent = (content: string): Omit<ChatMessage, 'role' | 'timestamp'> => {
  // 检测 __download__ 标记
  if (content.startsWith('__download__:')) {
    return { content: '', downloadUrl: content.replace('__download__:', '') }
  }
  // 检测 ECharts JSON（含 __chart__ 标记）
  try {
    const trimmed = content.trim()
    if (trimmed.startsWith('{') && trimmed.includes('__chart__')) {
      const option = JSON.parse(trimmed)
      delete option.__chart__
      return { content: '', chartOption: option }
    }
  } catch (_) { /* 不是 JSON，当普通文本处理 */ }
  return { content }
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

  es.onerror = () => {
    es.close()
    streaming.value = false
    if (fullContent) {
      const parsed = parseContent(fullContent)
      messages.value.push({ role: 'assistant', timestamp: new Date(), ...parsed })
    } else {
      ElMessage.error('助手响应失败，请重试')
    }
    streamingContent.value = ''
    scrollToBottom()
  }

  // SSE 完成（后端关闭连接）
  es.addEventListener('close', () => {
    es.close()
    streaming.value = false
    if (fullContent) {
      const parsed = parseContent(fullContent)
      messages.value.push({ role: 'assistant', timestamp: new Date(), ...parsed })
    }
    streamingContent.value = ''
    scrollToBottom()
  })
}

const sendSuggestion = (text: string) => {
  inputText.value = text
  send()
}

const download = (url: string) => {
  const a = document.createElement('a')
  a.href = url
  a.target = '_blank'
  a.click()
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
      content: m.content,
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
