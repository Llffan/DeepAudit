<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatRound, Close, Position } from '@element-plus/icons-vue'

const visible = ref(false)

interface Msg {
  role: 'user' | 'assistant'
  text: string
}

const messages = ref<Msg[]>([
  {
    role: 'assistant',
    text: '你好！我是病案导出助手。告诉我要导出的病案主键 ID，我会把数据库里的真实病案首页导出成 PDF。\n例如：\n• "把 ID=42 的病案导出成 PDF"\n• "导出病案 #100"\n• "ID 5 的病案打印一份"',
  },
])
const input = ref('')
const loading = ref(false)
const bodyRef = ref<HTMLElement | null>(null)

function open() {
  visible.value = true
}
defineExpose({ open })

function scrollToBottom() {
  nextTick(() => {
    if (bodyRef.value) {
      bodyRef.value.scrollTop = bodyRef.value.scrollHeight
    }
  })
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', text })
  input.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const res = await fetch('/api/testing-assistant/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text }),
    })
    const body = (await res.json()) as { reply?: string }
    messages.value.push({ role: 'assistant', text: body.reply ?? '（无回复）' })
  } catch (err) {
    ElMessage.error('请求失败')
    messages.value.push({ role: 'assistant', text: '请求失败，请稍后重试。' })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="病案导出助手"
    width="480px"
    :append-to-body="true"
    draggable
    class="ta-dialog"
  >
    <template #header>
      <div class="ta-header">
        <el-icon><ChatRound /></el-icon>
        <span>病案导出助手</span>
        <span class="ta-subtitle">DeepSeek · 按 ID 导出真实病案 PDF</span>
      </div>
    </template>

    <div ref="bodyRef" class="ta-body">
      <div
        v-for="(msg, i) in messages"
        :key="i"
        class="ta-msg"
        :class="msg.role"
      >
        <div class="ta-bubble">{{ msg.text }}</div>
      </div>
      <div v-if="loading" class="ta-msg assistant">
        <div class="ta-bubble ta-typing">
          <span /><span /><span />
        </div>
      </div>
    </div>

    <template #footer>
      <div class="ta-footer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          placeholder="告诉我要导出的病案 ID，Enter 发送…"
          resize="none"
          :disabled="loading"
          @keydown="onKeydown"
          class="ta-input"
        />
        <el-button
          type="primary"
          :icon="Position"
          :loading="loading"
          :disabled="!input.trim()"
          circle
          @click="send"
        />
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.ta-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 1rem;
}
.ta-subtitle {
  font-size: 0.75rem;
  color: #999;
  font-weight: 400;
  margin-left: 4px;
}

.ta-body {
  height: 360px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 2px;
}

.ta-msg {
  display: flex;
}
.ta-msg.user {
  justify-content: flex-end;
}
.ta-msg.assistant {
  justify-content: flex-start;
}

.ta-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 0.875rem;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.ta-msg.user .ta-bubble {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.ta-msg.assistant .ta-bubble {
  background: #f4f6f8;
  color: #222;
  border-bottom-left-radius: 4px;
}

/* 三点 loading 动画 */
.ta-typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 12px 16px;
}
.ta-typing span {
  display: block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #aaa;
  animation: bounce 1.2s infinite;
}
.ta-typing span:nth-child(2) { animation-delay: 0.2s; }
.ta-typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); }
  40%            { transform: translateY(-6px); }
}

.ta-footer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.ta-input {
  flex: 1;
}
</style>
