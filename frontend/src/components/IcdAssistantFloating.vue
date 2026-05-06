<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatRound, Close, Minus, Position, Reading } from '@element-plus/icons-vue'

/**
 * 全局浮动 ICD 编码助手。挂载在 App.vue，跨路由保持。
 *
 * 设计要点：
 * - 不用 el-dialog（模态会挡页面）；用绝对定位的 div + 顶层 z-index。
 * - 拖动只在标题栏热区（避免选文/输入时误触）。
 * - 位置 / 打开状态写 localStorage，刷新或切页保持。
 * - FAB 与面板互斥显示，并位置可拖（顺手）。
 */

interface Msg {
  role: 'user' | 'assistant'
  text: string
}

const STORAGE_KEY = 'icdAssistant.state.v1'
const PANEL_W = 380
const PANEL_H = 520
const FAB_SIZE = 52
const MARGIN = 16

interface PersistState {
  open: boolean
  // 面板与 FAB 各自记一组 x,y（相对视口左上角）
  panelX: number
  panelY: number
  fabX: number
  fabY: number
}

function defaultState(): PersistState {
  // FAB 默认右下；面板默认在 FAB 上方
  const fabX = window.innerWidth - FAB_SIZE - MARGIN
  const fabY = window.innerHeight - FAB_SIZE - MARGIN
  const panelX = window.innerWidth - PANEL_W - MARGIN
  const panelY = window.innerHeight - PANEL_H - MARGIN
  return { open: false, panelX, panelY, fabX, fabY }
}

function loadState(): PersistState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return defaultState()
    const s = JSON.parse(raw) as Partial<PersistState>
    const fb = defaultState()
    return {
      open: !!s.open,
      panelX: typeof s.panelX === 'number' ? s.panelX : fb.panelX,
      panelY: typeof s.panelY === 'number' ? s.panelY : fb.panelY,
      fabX:   typeof s.fabX   === 'number' ? s.fabX   : fb.fabX,
      fabY:   typeof s.fabY   === 'number' ? s.fabY   : fb.fabY,
    }
  } catch {
    return defaultState()
  }
}

const state = ref<PersistState>(loadState())

watch(state, (v) => {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(v)) } catch {}
}, { deep: true })

// 视口缩小时把面板/FAB 拖回可见区域
function clampToViewport() {
  state.value.panelX = clamp(state.value.panelX, MARGIN, window.innerWidth  - PANEL_W - MARGIN)
  state.value.panelY = clamp(state.value.panelY, MARGIN, window.innerHeight - PANEL_H - MARGIN)
  state.value.fabX   = clamp(state.value.fabX,   MARGIN, window.innerWidth  - FAB_SIZE - MARGIN)
  state.value.fabY   = clamp(state.value.fabY,   MARGIN, window.innerHeight - FAB_SIZE - MARGIN)
}
function clamp(v: number, lo: number, hi: number) {
  if (hi < lo) return lo
  return Math.max(lo, Math.min(hi, v))
}

onMounted(() => { window.addEventListener('resize', clampToViewport) })
onBeforeUnmount(() => { window.removeEventListener('resize', clampToViewport) })

// ---------- 拖动逻辑 ----------
type DragTarget = 'panel' | 'fab' | null
const dragTarget = ref<DragTarget>(null)
const dragOffset = ref({ x: 0, y: 0 })
const fabPressedAt = ref<{ x: number; y: number; t: number } | null>(null)
const FAB_CLICK_THRESHOLD_PX = 4
const FAB_CLICK_THRESHOLD_MS = 300

function onPanelHeaderMousedown(e: MouseEvent) {
  if (e.button !== 0) return
  dragTarget.value = 'panel'
  dragOffset.value = { x: e.clientX - state.value.panelX, y: e.clientY - state.value.panelY }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  e.preventDefault()
}

function onFabMousedown(e: MouseEvent) {
  if (e.button !== 0) return
  dragTarget.value = 'fab'
  dragOffset.value = { x: e.clientX - state.value.fabX, y: e.clientY - state.value.fabY }
  fabPressedAt.value = { x: e.clientX, y: e.clientY, t: Date.now() }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  e.preventDefault()
}

function onMouseMove(e: MouseEvent) {
  if (!dragTarget.value) return
  const x = e.clientX - dragOffset.value.x
  const y = e.clientY - dragOffset.value.y
  if (dragTarget.value === 'panel') {
    state.value.panelX = clamp(x, 0, window.innerWidth  - PANEL_W)
    state.value.panelY = clamp(y, 0, window.innerHeight - PANEL_H)
  } else {
    state.value.fabX = clamp(x, 0, window.innerWidth  - FAB_SIZE)
    state.value.fabY = clamp(y, 0, window.innerHeight - FAB_SIZE)
  }
}

function onMouseUp(e: MouseEvent) {
  // 区分 FAB 拖动 vs 点击：位移 < 阈值且时长 < 阈值视为点击
  if (dragTarget.value === 'fab' && fabPressedAt.value) {
    const dx = Math.abs(e.clientX - fabPressedAt.value.x)
    const dy = Math.abs(e.clientY - fabPressedAt.value.y)
    const dt = Date.now() - fabPressedAt.value.t
    if (dx < FAB_CLICK_THRESHOLD_PX && dy < FAB_CLICK_THRESHOLD_PX && dt < FAB_CLICK_THRESHOLD_MS) {
      openPanel()
    }
  }
  dragTarget.value = null
  fabPressedAt.value = null
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
}

// ---------- 对话状态 ----------
const messages = ref<Msg[]>([
  {
    role: 'assistant',
    text: '你好！我是 ICD 编码助手。你可以这样问：\n• "腹腔镜阑尾切除是什么编码？"\n• "47.0900 是啥手术"\n• "急性胃肠炎对应的诊断编码"\n• "字典里有多少条手术编码"',
  },
])
const input = ref('')
const loading = ref(false)
const bodyRef = ref<HTMLElement | null>(null)

function scrollToBottom() {
  nextTick(() => {
    if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  })
}

function openPanel() {
  state.value.open = true
  scrollToBottom()
}
function closePanel() { state.value.open = false }

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  messages.value.push({ role: 'user', text })
  input.value = ''
  loading.value = true
  scrollToBottom()
  try {
    const res = await fetch('/api/icd-assistant/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text }),
    })
    if (res.status === 503) {
      messages.value.push({ role: 'assistant', text: '后端 LLM 未配置（缺少 DEEPSEEK_API_KEY），编码助手不可用。请联系运维。' })
    } else {
      const body = (await res.json()) as { reply?: string }
      messages.value.push({ role: 'assistant', text: body.reply ?? '（无回复）' })
    }
  } catch {
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

function clearChat() {
  messages.value = [messages.value[0]] // 保留欢迎语
}

const panelStyle = computed(() => ({
  left:  state.value.panelX + 'px',
  top:   state.value.panelY + 'px',
  width: PANEL_W + 'px',
  height: PANEL_H + 'px',
}))
const fabStyle = computed(() => ({
  left: state.value.fabX + 'px',
  top:  state.value.fabY + 'px',
  width:  FAB_SIZE + 'px',
  height: FAB_SIZE + 'px',
}))
</script>

<template>
  <!-- 浮动面板：v-show 而非 v-if，保留滚动位置 -->
  <div
    v-show="state.open"
    class="ica-panel"
    :style="panelStyle"
    role="dialog"
    aria-label="ICD 编码助手"
  >
    <div class="ica-header" @mousedown="onPanelHeaderMousedown">
      <el-icon class="ica-header-icon"><Reading /></el-icon>
      <div class="ica-header-text">
        <div class="ica-title">ICD 编码助手</div>
        <div class="ica-subtitle">DeepSeek · 字典向量召回</div>
      </div>
      <div class="ica-header-actions">
        <el-button
          link
          size="small"
          class="ica-icon-btn"
          @click="clearChat"
          title="清空对话"
        >清空</el-button>
        <el-button
          link
          size="small"
          class="ica-icon-btn"
          :icon="Minus"
          @click="closePanel"
          title="最小化到悬浮按钮"
        />
        <el-button
          link
          size="small"
          class="ica-icon-btn"
          :icon="Close"
          @click="closePanel"
          title="关闭"
        />
      </div>
    </div>

    <div ref="bodyRef" class="ica-body">
      <div
        v-for="(msg, i) in messages"
        :key="i"
        class="ica-msg"
        :class="msg.role"
      >
        <div class="ica-bubble">{{ msg.text }}</div>
      </div>
      <div v-if="loading" class="ica-msg assistant">
        <div class="ica-bubble ica-typing">
          <span /><span /><span />
        </div>
      </div>
    </div>

    <div class="ica-footer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        placeholder="问 ICD 编码或名称…  Enter 发送 / Shift+Enter 换行"
        resize="none"
        :disabled="loading"
        @keydown="onKeydown"
        class="ica-input"
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
  </div>

  <!-- FAB 悬浮按钮 -->
  <div
    v-show="!state.open"
    class="ica-fab"
    :style="fabStyle"
    @mousedown="onFabMousedown"
    role="button"
    aria-label="打开 ICD 编码助手"
    title="ICD 编码助手（拖动移位 / 单击打开）"
  >
    <el-icon class="ica-fab-icon"><ChatRound /></el-icon>
  </div>
</template>

<style scoped>
/* ---------- 浮动面板 ---------- */
.ica-panel {
  position: fixed;
  z-index: 9000;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.18), 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.ica-header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #409eff 0%, #2563eb 100%);
  color: #fff;
  cursor: grab;
  user-select: none;
}
.ica-header:active { cursor: grabbing; }
.ica-header-icon { font-size: 18px; }
.ica-header-text { flex: 1; min-width: 0; }
.ica-title { font-weight: 600; font-size: 0.95rem; line-height: 1.2; }
.ica-subtitle { font-size: 0.7rem; opacity: 0.85; line-height: 1.2; margin-top: 2px; }
.ica-header-actions { display: flex; gap: 4px; align-items: center; }
.ica-icon-btn {
  color: rgba(255,255,255,0.9) !important;
  font-size: 12px;
}
.ica-icon-btn:hover { color: #fff !important; }

.ica-body {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fafbfc;
}

.ica-msg { display: flex; }
.ica-msg.user { justify-content: flex-end; }
.ica-msg.assistant { justify-content: flex-start; }

.ica-bubble {
  max-width: 85%;
  padding: 9px 12px;
  border-radius: 11px;
  font-size: 0.83rem;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.ica-msg.user .ica-bubble {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.ica-msg.assistant .ica-bubble {
  background: #fff;
  color: #222;
  border: 1px solid #ebeef5;
  border-bottom-left-radius: 4px;
}

.ica-typing {
  display: flex; align-items: center; gap: 5px;
  padding: 11px 14px;
}
.ica-typing span {
  display: block; width: 6px; height: 6px; border-radius: 50%;
  background: #aaa; animation: ica-bounce 1.2s infinite;
}
.ica-typing span:nth-child(2) { animation-delay: 0.2s; }
.ica-typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes ica-bounce {
  0%, 80%, 100% { transform: translateY(0); }
  40%            { transform: translateY(-5px); }
}

.ica-footer {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 10px 12px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}
.ica-input { flex: 1; }
.ica-input :deep(.el-textarea__inner) { font-size: 0.85rem; }

/* ---------- FAB ---------- */
.ica-fab {
  position: fixed;
  z-index: 9000;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #2563eb 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  box-shadow: 0 6px 18px rgba(64, 158, 255, 0.45);
  transition: transform 0.18s, box-shadow 0.18s;
  user-select: none;
}
.ica-fab:hover {
  transform: scale(1.06);
  box-shadow: 0 8px 22px rgba(64, 158, 255, 0.55);
}
.ica-fab:active { cursor: grabbing; transform: scale(0.96); }
.ica-fab-icon { font-size: 22px; }
</style>
