<template>
  <div class="file-manager">
    <div class="toolbar">
      <el-button v-if="!readOnly" type="primary" class="upload-btn" :icon="Upload" @click="triggerUpload">上传文件</el-button>
      <div class="action-buttons">
        <el-tooltip v-if="!readOnly" content="移动选中项">
          <el-button circle :icon="Rank" size="small" :disabled="selectedIds.length === 0" @click="openMoveDialogForSelected" />
        </el-tooltip>
        <el-tooltip v-if="!readOnly" content="新建文件夹">
          <el-button circle :icon="FolderAdd" size="small" @click="showMkdirDialog" />
        </el-tooltip>
        <el-tooltip content="刷新">
          <el-button circle :icon="Refresh" size="small" @click="refresh" />
        </el-tooltip>
      </div>
      <input
        v-if="!readOnly"
        type="file" 
        ref="fileInput" 
        style="display: none" 
        @change="handleFileChange"
      >
    </div>

    <!-- Breadcrumb -->
    <div class="breadcrumb-container">
      <div class="breadcrumb-item" :class="{ active: currentParentId === '0' }" @click="navigate('0', '根目录')">
        <el-icon><HomeFilled /></el-icon>
      </div>
      <template v-for="(item, index) in breadcrumbs" :key="index">
        <span class="separator">/</span>
        <div class="breadcrumb-item" @click="navigate(item.id, item.name)">
          {{ item.name }}
        </div>
      </template>
    </div>
    
    <!-- AI Progress Bar -->
    <div v-if="processingCount > 0" class="progress-bar">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>AI 正在学习中: {{ processingCount }} 个文件... 请耐心等待</span>
    </div>

    <!-- File List (Custom List) -->
    <div class="file-list-container" v-loading="loading && processingCount === 0">
      <div v-if="fileList.length === 0" class="empty-state">
        暂无文件
      </div>
      
      <div 
        v-for="file in fileList" 
        :key="file.id" 
        class="file-item"
        :class="{ selected: isSelected(file.id), 'drag-over': dragOverId === file.id }"
        @click="handleItemClick(file)"
        :draggable="!readOnly"
        @dragstart="onDragStart($event, file)"
        @dragend="onDragEnd"
        @dragover.prevent="onDragOver($event, file)"
        @dragleave="onDragLeave"
        @drop.prevent="onDrop($event, file)"
      >
        <div v-if="!readOnly" class="file-select" @click.stop>
          <el-checkbox :model-value="isSelected(file.id)" @change="(v) => setSelected(file.id, v)" />
        </div>
        <div class="file-icon">
          <el-icon v-if="file.type === 'DIRECTORY'"><Folder /></el-icon>
          <el-icon v-else><Document /></el-icon>
        </div>
        <div class="file-info">
          <div class="file-name" :title="file.name">{{ file.name }}</div>
          
          <!-- Status Icons -->
          <div class="file-status" v-if="file.type === 'FILE'">
             <el-tooltip content="AI 学习中..." v-if="file.processingStatus === 'PROCESSING' || file.processingStatus === 'PENDING'">
               <el-icon class="is-loading status-processing"><Loading /></el-icon>
             </el-tooltip>
             <el-tooltip content="AI 学习完成" v-else-if="file.processingStatus === 'COMPLETED'">
               <el-icon class="status-success"><CircleCheckFilled /></el-icon>
             </el-tooltip>
             <el-tooltip :content="'AI 学习失败: ' + (file.failReason || '未知错误')" v-else-if="file.processingStatus === 'FAILED'">
               <el-icon class="status-failed"><WarningFilled /></el-icon>
             </el-tooltip>
          </div>
        </div>
        
        <div v-if="!readOnly" class="file-actions">
          <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, file)">
            <span class="el-dropdown-link" @click.stop>
              <el-icon><MoreFilled /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="retry" v-if="file.processingStatus === 'FAILED'">重试 AI 解析</el-dropdown-item>
                <el-dropdown-item command="download" v-if="file.type === 'FILE'">下载</el-dropdown-item>
                <el-dropdown-item command="move">移动</el-dropdown-item>
                <el-dropdown-item command="delete" divided style="color: #f56c6c">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <!-- Dialogs -->
    <el-dialog v-if="!readOnly" v-model="mkdirVisible" title="新建文件夹" width="300px">
      <el-input v-model="newDirName" placeholder="请输入文件夹名称" @keyup.enter="handleMkdir" />
      <template #footer>
        <el-button @click="mkdirVisible = false">取消</el-button>
        <el-button type="primary" @click="handleMkdir">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-if="!readOnly" v-model="moveVisible" title="移动到..." width="300px">
      <div class="move-tree">
        <el-tree
          ref="moveTreeRef"
          node-key="id"
          :data="moveTreeData"
          :props="moveTreeProps"
          lazy
          :load="loadMoveTreeNode"
          highlight-current
          :expand-on-click-node="false"
          :default-expanded-keys="['0']"
          @current-change="handleMoveTargetChange"
        />
      </div>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" :loading="moveSubmitting" :disabled="!moveTargetId || selectedIds.length === 0" @click="confirmMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { Upload, FolderAdd, Refresh, Folder, Document, HomeFilled, MoreFilled, Loading, CircleCheckFilled, WarningFilled, Rank } from '@element-plus/icons-vue'
import { listDirectory, createDirectory, uploadFile, deleteNode, getDownloadUrl, moveNode, getPreviewData, retryProcess } from '../api/fs'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'

const emit = defineEmits(['preview-file'])

defineProps({
  readOnly: { type: Boolean, default: false }
})

const loading = ref(false)
const fileList = ref([])
const currentParentId = ref('0')
const breadcrumbs = ref([]) 
const fileInput = ref(null)

// Polling
let pollTimer = null

const selectedIds = ref([])
const dragOverId = ref(null)
const fileDragActive = ref(false)

// Computed
const processingCount = computed(() => {
  return fileList.value.filter(f => f.processingStatus === 'PENDING' || f.processingStatus === 'PROCESSING').length
})

// Dialogs
const mkdirVisible = ref(false)
const newDirName = ref('')
const moveVisible = ref(false)
const moveTargetId = ref('')
const moveSubmitting = ref(false)
const moveTreeRef = ref(null)
const moveTreeData = ref([{ id: '0', label: '根目录', leaf: false }])
const moveTreeProps = { label: 'label', children: 'children', isLeaf: 'leaf' }

onMounted(() => {
  loadFiles()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

const startPolling = () => {
  pollTimer = setInterval(() => {
    // Only poll if there are processing items or if we want to catch new updates
    // For simplicity, we can check if there are any pending items in the current list
    // But since other users might upload, polling is generally good.
    // To reduce load, we can check processingCount.
    if (processingCount.value > 0) {
        refreshFileListSilent()
    }
  }, 5000)
}

const stopPolling = () => {
  if (pollTimer) clearInterval(pollTimer)
}

const formatDate = (date) => {
  return dayjs(date).format('MM-DD HH:mm')
}

const loadFiles = async () => {
  loading.value = true
  try {
    const res = await listDirectory(currentParentId.value)
    if (res.code === 200) {
      fileList.value = res.data
      selectedIds.value = selectedIds.value.filter(id => fileList.value.some(n => n.id === id))
    }
  } finally {
    loading.value = false
  }
}

const refreshFileListSilent = async () => {
    const res = await listDirectory(currentParentId.value)
    if (res.code === 200) {
      fileList.value = res.data
    }
}

const refresh = () => loadFiles()

const navigate = (id, name) => {
  if (id === '0') {
    breadcrumbs.value = []
    currentParentId.value = '0'
  } else {
     const index = breadcrumbs.value.findIndex(b => b.id === id)
     if (index !== -1) {
         breadcrumbs.value = breadcrumbs.value.slice(0, index + 1)
     }
  }
  
  if (id !== currentParentId.value) {
     currentParentId.value = id
     selectedIds.value = []
     loadFiles()
  } else if (id === '0' && breadcrumbs.value.length === 0) {
     selectedIds.value = []
     loadFiles()
  }
}

const handleItemClick = async (file) => {
  if (fileDragActive.value) return
  if (file.type === 'DIRECTORY') {
    breadcrumbs.value.push({ id: file.id, name: file.name })
    currentParentId.value = file.id
    loadFiles()
  } else {
    try {
      const res = await getPreviewData(file.id)
      if (res.code === 200) {
        const previewData = { ...res.data, nodeId: file.id }
        emit('preview-file', previewData)
      } else {
        ElMessage.warning('无法预览: ' + res.message)
      }
    } catch(e) {
      console.error(e)
      ElMessage.error('预览失败')
    }
  }
}

const handleCommand = (command, file) => {
  if (command === 'retry') handleRetry(file)
  if (command === 'download') handleDownload(file)
  if (command === 'move') handleMove(file)
  if (command === 'delete') handleDelete(file)
}

const handleRetry = async (row) => {
    try {
        const res = await retryProcess(row.id)
        if (res.code === 200) {
            ElMessage.success('已触发重新解析')
            refreshFileListSilent()
        } else {
            ElMessage.error(res.message)
        }
    } catch (e) {
        ElMessage.error('请求失败')
    }
}

const triggerUpload = () => fileInput.value.click()

const showMkdirDialog = () => {
  newDirName.value = ''
  mkdirVisible.value = true
}

const handleFileChange = async (e) => {
  const files = e.target.files
  if (!files.length) return
  
  const file = files[0] // Get file object
  
  loading.value = true
  try {
    const res = await uploadFile(currentParentId.value, file) // Pass file directly
    if (res.code === 200) {
      ElMessage.success('上传成功')
      loadFiles()
    } else {
      ElMessage.error(res.message)
    }
  } catch(e) {
    ElMessage.error('上传失败')
  } finally {
    loading.value = false
    e.target.value = ''
  }
}

const handleMkdir = async () => {
  const name = newDirName.value.trim()
  if (!name) return
  try {
    const res = await createDirectory(currentParentId.value, name)
    if (res.code === 200) {
      ElMessage.success('创建成功')
      mkdirVisible.value = false
      newDirName.value = ''
      loadFiles()
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除吗？', '提示', { 
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning' 
    })
    const res = await deleteNode(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadFiles()
    }
  } catch {}
}

const handleMove = (row) => {
  selectedIds.value = [row.id]
  openMoveDialogForSelected()
}

const confirmMove = async () => {
  const newParentId = moveTargetId.value
  if (!newParentId || selectedIds.value.length === 0) return
  try {
    moveSubmitting.value = true
    const ids = [...new Set(selectedIds.value)].filter(id => id && id !== newParentId)
    if (ids.length === 0) return
    let ok = 0
    for (const id of ids) {
      const res = await moveNode(id, newParentId)
      if (res.code === 200) ok++
    }
    if (ok > 0) {
      ElMessage.success(`已移动 ${ok} 项`)
    }
    moveVisible.value = false
    moveTargetId.value = ''
    selectedIds.value = []
    await loadFiles()
  } catch (e) {
    ElMessage.error('移动失败')
  } finally {
    moveSubmitting.value = false
  }
}

const handleDownload = async (row) => {
  const res = await getDownloadUrl(row.id)
  if (res.code === 200) {
    window.open(res.data, '_blank')
  } else {
    ElMessage.error(res.message)
  }
}

const isSelected = (id) => selectedIds.value.includes(id)

const setSelected = (id, v) => {
  if (v) {
    if (!selectedIds.value.includes(id)) selectedIds.value = [...selectedIds.value, id]
  } else {
    selectedIds.value = selectedIds.value.filter(x => x !== id)
  }
}

const openMoveDialogForSelected = () => {
  moveTreeData.value = [{ id: '0', label: '根目录', leaf: false }]
  moveTargetId.value = currentParentId.value || '0'
  moveVisible.value = true
  if (moveTreeRef.value && typeof moveTreeRef.value.setCurrentKey === 'function') {
    moveTreeRef.value.setCurrentKey(moveTargetId.value)
  }
}

const handleMoveTargetChange = (data) => {
  if (!data || !data.id) return
  moveTargetId.value = data.id
}

const loadMoveTreeNode = async (node, resolve) => {
  const parentId = node.level === 0 ? '0' : node.data?.id
  if (!parentId) return resolve([])
  try {
    const res = await listDirectory(parentId)
    if (res.code !== 200) return resolve([])
    const dirs = (res.data || [])
      .filter(n => n.type === 'DIRECTORY')
      .map(n => ({ id: n.id, label: n.name, leaf: false }))
    resolve(dirs)
  } catch {
    resolve([])
  }
}

const onDragStart = (e, file) => {
  fileDragActive.value = true
  if (!isSelected(file.id)) selectedIds.value = [file.id]
  dragOverId.value = null
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('application/json', JSON.stringify(selectedIds.value))
  }
}

const onDragEnd = () => {
  fileDragActive.value = false
  dragOverId.value = null
}

const onDragOver = (e, file) => {
  if (file.type !== 'DIRECTORY') return
  dragOverId.value = file.id
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
}

const onDragLeave = () => {
  dragOverId.value = null
}

const onDrop = async (e, file) => {
  dragOverId.value = null
  fileDragActive.value = false
  if (file.type !== 'DIRECTORY') return
  const targetId = file.id
  if (!targetId) return
  moveTargetId.value = targetId
  try {
    moveSubmitting.value = true
    const ids = [...new Set(selectedIds.value)].filter(id => id && id !== targetId)
    if (ids.length === 0) return
    let ok = 0
    for (const id of ids) {
      const res = await moveNode(id, targetId)
      if (res.code === 200) ok++
    }
    if (ok > 0) ElMessage.success(`已移动 ${ok} 项`)
    selectedIds.value = []
    await loadFiles()
  } catch {
    ElMessage.error('移动失败')
  } finally {
    moveSubmitting.value = false
  }
}
</script>

<style scoped>
.file-manager {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}

.upload-btn {
  flex: 1;
  background: var(--ni-primary);
  border: none;
  border-radius: 6px;
  height: 32px;
  font-size: 13px;
  transition: background 0.2s;
}

.upload-btn:hover {
  background: var(--ni-primary-hover);
}

.action-buttons .el-button {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid transparent;
  color: var(--ni-text-sidebar);
  transition: all 0.2s;
}

.action-buttons .el-button:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.breadcrumb-container {
  display: flex;
  align-items: center;
  padding: 0 4px 12px 4px;
  font-size: 12px;
  color: var(--ni-text-light);
  overflow-x: auto;
  scrollbar-width: none; /* Hide scrollbar for cleaner look */
}

.breadcrumb-container::-webkit-scrollbar {
  display: none;
}

.breadcrumb-item {
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: color 0.2s;
  white-space: nowrap;
}

.breadcrumb-item:hover {
  color: #fff;
}

.breadcrumb-item.active {
  color: #fff;
  font-weight: 500;
}

.separator {
  margin: 0 6px;
  color: rgba(255,255,255,0.2);
  font-size: 10px;
}

.file-list-container {
  flex: 1;
  overflow-y: auto;
  margin: 0 -8px; /* Negative margin to allow hover effect to reach edges */
  padding: 0 8px;
}

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: var(--ni-text-light);
  font-size: 13px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  opacity: 0.7;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--ni-text-sidebar);
  margin-bottom: 2px;
  height: 36px; /* Fixed height for consistency */
}

.file-item.selected {
  background-color: rgba(59, 130, 246, 0.18);
  outline: 1px solid rgba(59, 130, 246, 0.35);
}

.file-item.drag-over {
  background-color: rgba(59, 130, 246, 0.28);
  outline: 1px dashed rgba(59, 130, 246, 0.6);
}

.file-item:hover {
  background-color: var(--ni-bg-dark-hover);
  color: #fff;
}

.file-select {
  margin-right: 10px;
  display: flex;
  align-items: center;
}

.file-icon {
  margin-right: 12px;
  display: flex;
  align-items: center;
  font-size: 16px;
  color: var(--ni-text-light);
  transition: color 0.2s;
}

.file-item:hover .file-icon {
  color: var(--ni-primary); /* Highlight icon on hover */
}

.file-info {
  flex: 1;
  overflow: hidden;
  display: flex;
  align-items: center;
}

.file-name {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.2;
}

.file-actions {
  opacity: 0;
  transition: opacity 0.2s;
  display: flex;
  align-items: center;
}

.file-item:hover .file-actions {
  opacity: 1;
}

.el-dropdown-link {
  color: var(--ni-text-light);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex;
}

.el-dropdown-link:hover {
  background: rgba(255,255,255,0.1);
  color: #fff;
}

.move-tree {
  max-height: 360px;
  overflow: auto;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.02);
}

/* AI Progress Bar */
.progress-bar {
  background: rgba(var(--ni-primary-rgb), 0.1);
  border: 1px solid rgba(var(--ni-primary-rgb), 0.2);
  color: var(--ni-primary);
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

/* File Status Icons */
.file-status {
  margin-left: 8px;
  display: flex;
  align-items: center;
}

.status-processing {
  color: var(--ni-primary);
  font-size: 14px;
}

.status-success {
  color: #67c23a;
  font-size: 14px;
}

.status-failed {
  color: #f56c6c;
  font-size: 14px;
}
</style>
