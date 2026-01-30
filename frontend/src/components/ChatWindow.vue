<template>
  <div class="chat-window">
    <div class="chat-header">
      <div class="chat-title">
        <el-icon><Service /></el-icon>
        <span>{{ headerTitle }}</span>
        <el-tag v-if="isShareMode" size="small" effect="plain" type="warning">只读分享</el-tag>
      </div>
      <div class="chat-header-actions">
        <el-button
          v-if="!isShareMode"
          size="small"
          plain
          :icon="Plus"
          :disabled="loading"
          @click="handleNewSession"
        >
          新对话
        </el-button>
        <el-button size="small" plain :icon="ChatLineRound" @click="sessionsDrawerOpen = true">会话</el-button>
        <el-button
          v-if="!isShareMode"
          size="small"
          plain
          :icon="Share"
          :disabled="!currentSessionId || loading"
          @click="handleShare"
        >
          分享
        </el-button>
        <el-tag v-if="loading" size="small" effect="plain" type="info">生成中...</el-tag>
      </div>
    </div>
    
    <div class="messages" ref="msgContainer">
      <div v-if="messages.length === 0" class="empty-chat">
        <div class="empty-icon">👋</div>
        <p>今天有什么可以帮您的？</p>
      </div>
      
      <div 
        v-for="(msg, index) in messages" 
        :key="index" 
        :class="['message-row', msg.role]"
      >
        <div class="avatar">
          <el-avatar 
            :size="36" 
            :shape="msg.role === 'bot' ? 'square' : 'circle'"
            :src="msg.role === 'bot' ? 'https://api.dicebear.com/7.x/bottts/svg?seed=panda' : 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + userStore.userId" 
            :style="{ backgroundColor: msg.role === 'bot' ? 'transparent' : '#fff' }"
          />
        </div>
        <div class="content">
          <div class="bubble">
             <div v-if="!msg.content && msg.role === 'bot' && loading && index === messages.length - 1" class="typing-indicator">
               <span></span><span></span><span></span>
             </div>
             <div v-else v-html="renderMarkdown(msg.content)"></div>
          </div>
        </div>
      </div>
    </div>

    <div class="input-wrapper">
      <div class="status-bar-container" v-if="currentStatus">
          <div class="status-pill">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>{{ currentStatus }}</span>
          </div>
      </div>
      <div class="input-container">
        <el-input
          v-model="input"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="基于知识库向我提问..."
          @keydown.enter.prevent="sendMessage"
          :disabled="loading"
          class="chat-input"
        />
        <div class="input-actions">
           <el-tooltip content="正在压缩上下文" v-if="compressingMemory">
             <div class="status-indicator warning">
               <el-icon class="is-loading"><Loading /></el-icon>
             </div>
           </el-tooltip>
           <el-tooltip content="深度思考中..." v-if="deepThinking">
             <div class="status-indicator thinking">
               <el-icon class="is-loading"><Loading /></el-icon>
             </div>
           </el-tooltip>
           <el-button 
             type="primary" 
             circle 
             :icon="Position"
             :loading="loading" 
             @click="sendMessage"
             class="send-btn"
           />
        </div>
      </div>
      <div class="footer-note">内容由 AI 生成，可能存在误差，请核实。</div>
    </div>

    <el-drawer v-model="sessionsDrawerOpen" size="320px" append-to-body>
      <template #header>
        <div class="sessions-drawer-header">
          <div class="sessions-drawer-title">对话列表</div>
          <el-button v-if="!isShareMode" size="small" plain :icon="Plus" :disabled="loading" @click="handleNewSession">
            新对话
          </el-button>
        </div>
      </template>
      <div class="sessions-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          @click="selectSession(s)"
        >
          <div class="session-title">{{ s.title || '新对话' }}</div>
        </div>
        <div v-if="sessions.length === 0" class="sessions-empty">暂无会话</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount, onMounted, computed } from 'vue'
import { Service, Loading, Position, Plus, ChatLineRound, Share } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { marked } from 'marked'
import mermaid from 'mermaid'
import { ElMessage } from 'element-plus'
import { createChatSession, createChatShare, getChatMessages, listChatSessions, resolveChatShare } from '../api/chat'

// Initialize mermaid
mermaid.initialize({ 
  startOnLoad: false,
  theme: 'base',
  themeVariables: {
    primaryColor: '#3b82f6',
    primaryTextColor: '#fff',
    primaryBorderColor: '#2563eb',
    lineColor: '#64748b',
    secondaryColor: '#f1f5f9',
    tertiaryColor: '#fff'
  },
  securityLevel: 'loose'
})

// Configure marked
const renderer = new marked.Renderer()
const originalCodeRenderer = renderer.code.bind(renderer)

renderer.code = (code, language, isEscaped) => {
  if (language === 'mermaid') {
    return `<div class="mermaid">${code.text || code}</div>`
  }
  return originalCodeRenderer(code, language, isEscaped)
}

marked.setOptions({
  renderer: renderer,
  breaks: true,
  gfm: true
})

const normalizeMarkdownInput = (text) => {
  if (!text) return ''

  const parts = text.split(/(```[\s\S]*?```)/g)
  return parts
    .map(part => {
      if (part.startsWith('```')) return part

      let s = part

      s = s.replace(/(^|\n)(#{1,6})([^\s#])/g, '$1$2 $3')
      s = s.replace(/([^\n#])\s*(#{2,6})\s+/g, '$1\n\n$2 ')
      s = s.replace(/([^\n#])\s*(#{2,6})(?=[^\s#])/g, '$1\n\n$2 ')

      return s
    })
    .join('')
}

const renderMarkdown = (text) => {
  if (!text) return ''
  return marked.parse(normalizeMarkdownInput(text))
}

const renderMermaidGraphs = async () => {
  await nextTick()
  const nodes = document.querySelectorAll('.mermaid')
  if (nodes.length > 0) {
    try {
      await mermaid.run({ nodes })
    } catch (e) {
      console.error('Mermaid rendering failed:', e)
    }
  }
}

const input = ref('')
const messages = ref([])
const loading = ref(false)
const compressingMemory = ref(false)
const deepThinking = ref(false)
const currentStatus = ref('')
const msgContainer = ref(null)

const userStore = useUserStore()

const props = defineProps({
  shareToken: { type: String, default: '' }
})

const sessions = ref([])
const currentSessionId = ref('')
const currentSessionTitle = ref('')
const sessionsDrawerOpen = ref(false)
const activeEventSource = ref(null)

const isShareMode = computed(() => !!props.shareToken)
const headerTitle = computed(() => currentSessionTitle.value || '智能对话')

const baseGreeting = () => [{ role: 'bot', content: '你好！我是 Panda 智能助手。我已经索引了你的知识库，随时准备回答你的问题。' }]

const scrollToBottom = () => {
  nextTick(() => {
    if (msgContainer.value) {
      msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    }
  })
}

const stopStreaming = () => {
  if (activeEventSource.value) {
    try {
      activeEventSource.value.close()
    } catch {
    }
    activeEventSource.value = null
  }
  loading.value = false
  compressingMemory.value = false
  deepThinking.value = false
  currentStatus.value = ''
}

const refreshSessions = async () => {
  if (isShareMode.value) return
  const res = await listChatSessions()
  sessions.value = Array.isArray(res.data) ? res.data : []
  const selected = sessions.value.find(s => s.id === currentSessionId.value)
  if (selected) {
    currentSessionTitle.value = selected.title || '新对话'
  }
}

const loadSessionMessages = async (sessionId) => {
  const res = await getChatMessages(sessionId, 200, props.shareToken)
  const list = Array.isArray(res.data) ? res.data : []
  const mapped = list
    .filter(m => m && m.content)
    .map(m => {
      const role = m.role === 'assistant' ? 'bot' : (m.role === 'user' ? 'user' : 'bot')
      return { role, content: m.content }
    })
  messages.value = mapped.length ? mapped : baseGreeting()
  scrollToBottom()
  renderMermaidGraphs()
}

const selectSession = async (session) => {
  if (!session || !session.id) return
  stopStreaming()
  currentSessionId.value = session.id
  currentSessionTitle.value = session.title || '新对话'
  sessionsDrawerOpen.value = false
  await loadSessionMessages(currentSessionId.value)
}

const handleNewSession = async () => {
  if (isShareMode.value) return
  stopStreaming()
  const res = await createChatSession()
  const s = res.data
  if (!s || !s.id) return
  sessions.value = [s, ...sessions.value.filter(x => x && x.id !== s.id)]
  await selectSession(s)
}

const handleShare = async () => {
  if (!currentSessionId.value) return
  const res = await createChatShare(currentSessionId.value)
  const token = res?.data?.shareToken
  if (!token) return
  const url = `${window.location.origin}${window.location.pathname}?shareToken=${encodeURIComponent(token)}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('分享链接已复制')
  } catch {
    window.prompt('复制分享链接', url)
  }
}

const ensureSessionReady = async () => {
  if (currentSessionId.value) return
  if (isShareMode.value) return
  const res = await createChatSession()
  const s = res.data
  if (!s || !s.id) return
  sessions.value = [s, ...sessions.value]
  currentSessionId.value = s.id
  currentSessionTitle.value = s.title || '新对话'
}

const sendMessage = async () => {
  const text = input.value.trim()
  if (!text || loading.value) return

  await ensureSessionReady()

  // 1. Add User Message
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  deepThinking.value = false
  currentStatus.value = '正在思考...'
  scrollToBottom()

  // 2. Add Bot Placeholder
  const botMsgIndex = messages.value.push({ role: 'bot', content: '' }) - 1

  // 3. SSE Request
  const qs = new URLSearchParams()
  qs.set('question', text)
  qs.set('chatId', currentSessionId.value || 'default')
  if (userStore.userId) qs.set('userId', userStore.userId)
  if (props.shareToken) qs.set('shareToken', props.shareToken)
  const url = `/ai/rag/chat?${qs.toString()}`

  stopStreaming()
  const eventSource = new EventSource(url)
  activeEventSource.value = eventSource
  
  let fullText = ''
  let pendingText = ''
  let rafId = 0
  let finished = false

  const flushPending = () => {
    rafId = 0
    if (!pendingText) return
    fullText += pendingText
    pendingText = ''
    messages.value[botMsgIndex].content = fullText
    scrollToBottom()
  }

  const finish = () => {
    if (finished) return
    finished = true
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = 0
    }
    flushPending()
    eventSource.close()
    activeEventSource.value = null
    loading.value = false
    compressingMemory.value = false
    deepThinking.value = false
    currentStatus.value = ''
    renderMermaidGraphs()
    refreshSessions()
  }

  eventSource.onmessage = (event) => {
    const data = event.data
    
    // Check for custom protocol events
    if (data === '[MEMORY_COMPRESSING]') {
        compressingMemory.value = true
        currentStatus.value = '正在优化记忆...'
        return
    }
    if (data === '[MEMORY_COMPRESSED]') {
        compressingMemory.value = false
        currentStatus.value = '记忆优化完成'
        return
    }
    if (data === '[MODEL_THINKING]') {
        deepThinking.value = true
        currentStatus.value = '深度思考中...'
        return
    }
    if (data && data.startsWith('[STATUS]')) {
        currentStatus.value = data.substring(8)
        return
    }

    if (data === '[DONE]') {
      finish()
      return
    }

    if (data) {
      if (deepThinking.value) {
          deepThinking.value = false 
          currentStatus.value = '' // Generating text now
      }
      if (currentStatus.value) currentStatus.value = '' // Clear status on text generation
      
      pendingText += data
      if (!rafId) {
        rafId = requestAnimationFrame(flushPending)
      }
    }
  }

  eventSource.onerror = (err) => {
    if (finished) return
    console.error('SSE Error', err)
    if (!fullText && !pendingText) {
      messages.value[botMsgIndex].content = '❌ 连接断开或发生错误。'
      eventSource.close()
      activeEventSource.value = null
      loading.value = false
      compressingMemory.value = false
      deepThinking.value = false
      currentStatus.value = ''
      return
    }
    finish()
  }
}

onMounted(() => {
  const init = async () => {
    if (isShareMode.value) {
      const resolved = await resolveChatShare(props.shareToken)
      const sessionId = resolved?.data?.sessionId || ''
      const title = resolved?.data?.title || ''
      sessions.value = sessionId ? [{ id: sessionId, title }] : []
      currentSessionId.value = sessionId
      currentSessionTitle.value = title || '对话'
      if (sessionId) {
        await loadSessionMessages(sessionId)
      } else {
        messages.value = baseGreeting()
      }
      return
    }

    const res = await listChatSessions()
    sessions.value = Array.isArray(res.data) ? res.data : []
    if (sessions.value.length === 0) {
      const created = await createChatSession()
      const s = created.data
      if (s && s.id) {
        sessions.value = [s]
      }
    }
    if (sessions.value.length > 0) {
      await selectSession(sessions.value[0])
    } else {
      messages.value = baseGreeting()
    }
  }

  init()
})

onBeforeUnmount(() => {
  stopStreaming()
})
</script>

<style scoped>
.chat-window {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  background-color: var(--ni-bg-light);
}

.chat-header {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgba(243, 244, 246, 0.8); /* Semi-transparent bg-light */
  backdrop-filter: blur(10px);
  z-index: 10;
  border-bottom: 1px solid rgba(0,0,0,0.05);
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ni-text-main);
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sessions-drawer-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sessions-drawer-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ni-text-main);
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--ni-border);
  background: #fff;
  cursor: pointer;
  transition: all 0.15s ease;
}

.session-item:hover {
  border-color: rgba(var(--ni-primary-rgb), 0.4);
}

.session-item.active {
  border-color: var(--ni-primary);
  box-shadow: var(--ni-shadow-sm);
}

.session-title {
  font-size: 13px;
  color: var(--ni-text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sessions-empty {
  padding: 12px;
  color: var(--ni-text-light);
  font-size: 13px;
}

.messages {
  flex: 1;
  padding: 80px 20px 100px 20px; /* Top padding for header, bottom for input */
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
  scroll-behavior: smooth;
}

.empty-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--ni-text-light);
  opacity: 0.8;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  animation: wave 2s infinite;
}

@keyframes wave {
  0% { transform: rotate(0deg); }
  10% { transform: rotate(14deg); }
  20% { transform: rotate(-8deg); }
  30% { transform: rotate(14deg); }
  40% { transform: rotate(-4deg); }
  50% { transform: rotate(10deg); }
  60% { transform: rotate(0deg); }
  100% { transform: rotate(0deg); }
}

.message-row {
  display: flex;
  gap: 16px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}

.message-row.user {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
  margin-top: 4px;
}

.content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.message-row.user .content {
  align-items: flex-end;
}

.bubble {
  padding: 12px 18px;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 15px;
  box-shadow: var(--ni-shadow-sm);
  position: relative;
  word-wrap: break-word;
}

.message-row.user .bubble {
  background-color: var(--ni-primary);
  color: #fff;
  border-top-right-radius: 2px;
  border-bottom-right-radius: 12px;
  border-bottom-left-radius: 12px;
  border-top-left-radius: 12px;
}

.message-row.bot .bubble {
  background-color: #fff;
  color: var(--ni-text-main);
  border-top-left-radius: 2px;
  border-top-right-radius: 12px;
  border-bottom-right-radius: 12px;
  border-bottom-left-radius: 12px;
  border: 1px solid var(--ni-border);
}

/* Markdown Styles within Bubble */
.bubble :deep(p) {
  margin: 0 0 10px 0;
}
.bubble :deep(p:last-child) {
  margin: 0;
}
.bubble :deep(h1), .bubble :deep(h2), .bubble :deep(h3), .bubble :deep(h4), .bubble :deep(h5), .bubble :deep(h6) {
  margin: 16px 0 8px 0;
  font-weight: 600;
  line-height: 1.3;
}
.bubble :deep(h1) { font-size: 1.25em; }
.bubble :deep(h2) { font-size: 1.15em; }
.bubble :deep(h3) { font-size: 1.08em; }
.bubble :deep(h4) { font-size: 1.02em; }
.bubble :deep(h5), .bubble :deep(h6) { font-size: 1em; }
.bubble :deep(ul), .bubble :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}
.bubble :deep(li) {
  margin-bottom: 4px;
}
.bubble :deep(pre) {
  background: #1e293b; /* Dark bg for code blocks even in light bubble */
  color: #e2e8f0;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
.bubble :deep(pre code) {
  background: transparent;
  padding: 0;
}
.bubble :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.9em;
  background-color: rgba(0, 0, 0, 0.05);
  padding: 2px 4px;
  border-radius: 4px;
}
.message-row.user .bubble :deep(code) {
  background-color: rgba(255, 255, 255, 0.2);
  color: #fff;
}
.bubble :deep(blockquote) {
  margin: 10px 0;
  padding-left: 12px;
  border-left: 3px solid var(--ni-primary);
  color: var(--ni-text-light);
}

/* Think Tag Styling */
.bubble :deep(think) {
  display: block;
  background-color: #f8f9fa;
  border-left: 3px solid #e6a23c;
  padding: 12px;
  margin: 12px 0;
  font-size: 0.9em;
  color: #606266;
  font-family: monospace;
  white-space: pre-wrap;
  border-radius: 0 4px 4px 0;
}
.bubble :deep(think)::before {
  content: "🤔 深度思考过程";
  display: block;
  font-weight: bold;
  margin-bottom: 8px;
  color: #e6a23c;
  font-family: var(--el-font-family);
}

.status-bar-container {
  padding: 0 16px 8px 16px;
  display: flex;
  justify-content: center;
  animation: slideUp 0.3s ease;
}

.status-pill {
  background: rgba(var(--ni-primary-rgb), 0.1);
  color: var(--ni-primary);
  border: 1px solid rgba(var(--ni-primary-rgb), 0.2);
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

.input-wrapper {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: linear-gradient(to top, var(--ni-bg-light) 60%, transparent);
  z-index: 10;
}

.input-container {
  width: 100%;
  max-width: 800px;
  position: relative;
  background: #fff;
  border-radius: 12px;
  box-shadow: var(--ni-shadow-lg);
  border: 1px solid var(--ni-border);
  padding: 8px;
  display: flex;
  flex-direction: column;
}

.chat-input :deep(.el-textarea__inner) {
  box-shadow: none !important;
  border: none !important;
  resize: none;
  padding: 8px 12px;
  background: transparent !important;
  font-size: 14px;
  line-height: 1.5;
  color: var(--ni-text-main);
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 4px 8px 0;
  gap: 12px;
  border-top: 1px solid rgba(0,0,0,0.03);
  margin-top: 4px;
}

.status-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background-color: #f3f4f6;
  color: var(--ni-text-light);
}

.status-indicator.warning {
  color: #e6a23c;
  background-color: #fdf6ec;
}

.status-indicator.thinking {
  color: #409eff;
  background-color: #ecf5ff;
}

.send-btn {
  background-color: var(--ni-primary);
  border-color: var(--ni-primary);
  transition: all 0.2s;
  width: 32px;
  height: 32px;
}

.send-btn:hover {
  background-color: var(--ni-primary-hover);
  border-color: var(--ni-primary-hover);
  transform: translateY(-1px);
}

.footer-note {
  margin-top: 8px;
  font-size: 11px;
  color: var(--ni-text-light);
  text-align: center;
}

/* Mermaid Container */
:deep(.mermaid) {
  background: #fff;
  padding: 10px;
  border-radius: 8px;
  margin: 10px 0;
  display: flex;
  justify-content: center;
  border: 1px solid var(--ni-border);
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  background-color: var(--ni-text-light);
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%, 80%, 100% { 
    transform: scale(0);
  } 
  40% { 
    transform: scale(1.0);
  }
}
</style>
