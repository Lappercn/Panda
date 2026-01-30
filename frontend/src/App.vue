<template>
  <div v-if="initializing" class="init-screen">
    <el-icon class="is-loading"><Loading /></el-icon>
    <div class="init-text">正在初始化...</div>
  </div>

  <el-container v-else-if="shareMode" class="share-container">
    <el-main class="share-main">
      <ChatWindow :key="shareToken" :share-token="shareToken" />
    </el-main>
  </el-container>

  <AuthView v-else-if="!userStore.loggedIn" @success="handleAuthSuccess" />

  <el-container v-else class="app-container">
    <el-aside width="300px" class="app-sidebar">
      <div class="sidebar-header">
        <div class="logo">🐼 Panda 知识库</div>
      </div>

      <div class="sidebar-content">
        <FileManager :key="userStore.userId" @preview-file="handlePreview" />
      </div>

      <div class="sidebar-footer">
        <div class="user-control">
          <el-avatar :size="32" :src="'https://api.dicebear.com/7.x/avataaars/svg?seed=' + userStore.userId"></el-avatar>
          <div class="user-info">
            <div class="user-label">已登录</div>
            <div class="user-value">{{ userStore.email || userStore.userId }}</div>
          </div>
          <el-button size="small" plain @click="handleLogout">退出</el-button>
        </div>
      </div>
    </el-aside>

    <el-main class="app-main">
      <div class="content-wrapper">
        <FilePreview
          v-if="showPreview"
          :node-id="previewFile.nodeId"
          :file-name="previewFile.name"
          :ocr-url="previewFile.ocrUrl"
          :file-url="previewFile.fileUrl"
          @close="handleClosePreview"
        />
        <ChatWindow v-else :key="userStore.userId" />
      </div>
    </el-main>
  </el-container>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useUserStore } from './stores/user'
import FileManager from './components/FileManager.vue'
import ChatWindow from './components/ChatWindow.vue'
import FilePreview from './components/FilePreview.vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import AuthView from './components/AuthView.vue'
import { getMe, logout } from './api/auth'

const userStore = useUserStore()
const initializing = ref(true)
const shareMode = ref(false)
const shareToken = ref('')

const showPreview = ref(false)
const previewFile = ref({})

const initAuth = async () => {
  const url = new URL(window.location.href)
  shareToken.value = url.searchParams.get('shareToken') || ''
  try {
    const res = await getMe()
    userStore.setUser(res.data)
    shareMode.value = false
  } catch {
    userStore.clearUser()
    shareMode.value = !!shareToken.value
  } finally {
    initializing.value = false
  }
}

onMounted(() => {
  initAuth()
})

const handleAuthSuccess = () => {
  handleClosePreview()
}

const handleLogout = async () => {
  try {
    await logout()
  } finally {
    userStore.clearUser()
    handleClosePreview()
    ElMessage.success('已退出登录')
  }
}

const handlePreview = (fileData) => {
  previewFile.value = fileData
  showPreview.value = true
}

const handleClosePreview = () => {
  showPreview.value = false
  previewFile.value = {}
}
</script>

<style scoped>
.init-screen {
  height: 100vh;
  width: 100vw;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  background-color: var(--ni-bg-light);
  color: var(--ni-text-main);
}

.init-text {
  font-size: 13px;
  color: var(--ni-text-light);
}

.app-container {
  height: 100vh;
  width: 100vw;
  background-color: var(--ni-bg-light);
}

.share-container {
  height: 100vh;
  width: 100vw;
  background-color: var(--ni-bg-light);
}

.share-main {
  padding: 0;
}

.app-sidebar {
  background-color: var(--ni-bg-dark);
  border-right: 1px solid var(--ni-bg-dark);
  display: flex;
  flex-direction: column;
  color: var(--ni-text-sidebar);
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.logo {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 0; /* Remove padding for edge-to-edge list */
}

/* Custom Scrollbar for sidebar */
.sidebar-content::-webkit-scrollbar {
  width: 4px;
}
.sidebar-content::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}
.sidebar-content::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  background-color: rgba(0, 0, 0, 0.2);
}

.user-control {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  flex: 1;
  overflow: hidden;
}

.user-label {
  font-size: 10px;
  color: var(--ni-text-light);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.user-value {
  font-size: 13px;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-main {
  padding: 0;
  background-color: var(--ni-bg-light);
  position: relative;
}

.content-wrapper {
  height: 100%;
  width: 100%;
  overflow: hidden;
  background-color: var(--ni-bg-light);
  display: flex;
  flex-direction: column;
}
</style>
