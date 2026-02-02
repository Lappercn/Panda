<template>
  <div class="file-preview">
    <div class="preview-toolbar">
      <span class="file-name">{{ fileName }}</span>
      <div class="actions">
        <template v-if="!isEditing">
            <el-button v-if="!readOnly" type="primary" link :icon="Edit" @click="toggleEdit">编辑</el-button>
            <el-button type="primary" link @click="zoomIn">放大</el-button>
            <el-button type="primary" link @click="zoomOut">缩小</el-button>
            <el-button type="info" link @click="resetView">重置</el-button>
        </template>
        <template v-else>
            <el-button type="success" link :icon="Check" @click="handleSave">保存</el-button>
            <el-button type="info" link :icon="CloseBold" @click="cancelEdit">取消</el-button>
        </template>
        <el-button type="danger" size="small" :icon="Close" circle @click="$emit('close')" title="关闭预览"></el-button>
      </div>
    </div>
    
    <div 
      class="preview-container" 
      v-loading="loading" 
      ref="containerRef"
      @mousedown="startDrag"
      @mousemove="onDrag"
      @mouseup="stopDrag"
      @mouseleave="stopDrag"
    >
      <div v-if="error" class="error-msg">
        <el-empty :description="error" />
      </div>
      
      <div 
        v-else 
        class="document-canvas" 
        :style="{ transform: `scale(${scale})`, transformOrigin: 'top center' }"
        @dblclick="handleDoubleClick"
      >
        <div v-for="(page, pIndex) in pages" :key="pIndex" class="page-sheet" :style="getPageStyle(page)">
          <template v-for="(box, bIndex) in page.boxes" :key="bIndex">
              <div 
                v-if="!isEditing"
                class="text-box" 
                :style="getBoxStyle(box)"
                :title="box.text"
              >
                {{ box.text }}
              </div>
              <input
                v-else
                v-model="box.text"
                class="edit-input"
                :style="getBoxStyle(box)"
              />
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { Close, Edit, Check, CloseBold } from '@element-plus/icons-vue'
import { saveOcrResult } from '../api/fs'
import { ElMessage } from 'element-plus'

const props = defineProps({
  nodeId: String,
  fileName: String,
  ocrUrl: String,
  fileUrl: String,
  readOnly: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

const loading = ref(false)
const error = ref('')
const pages = ref([]) // [{ width, height, boxes: [] }]
const scale = ref(1.0)

const containerRef = ref(null)
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const startScroll = ref({ x: 0, y: 0 })

const isEditing = ref(false)
const originalPages = ref([])

onMounted(() => {
  loadOcrData()
})

watch(() => props.ocrUrl, () => {
  loadOcrData()
  isEditing.value = false
})

const toggleEdit = () => {
    if (props.readOnly) return
    originalPages.value = JSON.parse(JSON.stringify(pages.value))
    isEditing.value = true
}

const handleDoubleClick = () => {
  if (props.readOnly) return
  if (!isEditing.value) {
    toggleEdit()
  }
}

const cancelEdit = () => {
    pages.value = JSON.parse(JSON.stringify(originalPages.value))
    isEditing.value = false
}

const handleSave = async () => {
    if (props.readOnly) return
    if (!props.nodeId) {
        ElMessage.error('无法保存：缺少文件ID')
        return
    }
    
    loading.value = true
    try {
        const jsonContent = JSON.stringify({ pages: pages.value })
        const res = await saveOcrResult(props.nodeId, jsonContent)
        if (res.code === 200) {
            ElMessage.success('保存成功')
            isEditing.value = false
        } else {
            ElMessage.error('保存失败: ' + res.message)
        }
    } catch (e) {
        console.error(e)
        ElMessage.error('保存失败')
    } finally {
        loading.value = false
    }
}

const zoomIn = () => {
  applyZoom(0.1)
}

const zoomOut = () => {
  applyZoom(-0.1)
}

const applyZoom = (delta) => {
  const newScale = Math.min(Math.max(scale.value + delta, 0.1), 5.0)
  scale.value = newScale
}

const startDrag = (e) => {
  if (isEditing.value) return // 编辑模式下可能需要选择文本，避免拖动冲突
  isDragging.value = true
  dragStart.value = { x: e.clientX, y: e.clientY }
  if (containerRef.value) {
    startScroll.value = { x: containerRef.value.scrollLeft, y: containerRef.value.scrollTop }
    containerRef.value.style.cursor = 'grabbing'
  }
}

const onDrag = (e) => {
  if (!isDragging.value || !containerRef.value) return
  const dx = e.clientX - dragStart.value.x
  const dy = e.clientY - dragStart.value.y
  
  // 拖动方向与滚动方向相反
  containerRef.value.scrollLeft = startScroll.value.x - dx
  containerRef.value.scrollTop = startScroll.value.y - dy
}

const stopDrag = () => {
  isDragging.value = false
  if (containerRef.value) {
    containerRef.value.style.cursor = 'default'
  }
}

const resetView = () => {
  scale.value = 1.0
  if (containerRef.value) {
      containerRef.value.scrollTop = 0
      containerRef.value.scrollLeft = 0
  }
}

const centerContent = async () => {
  // 既然使用了原生滚动，默认就是居中(CSS margin auto)或靠左上
  // 这里只需重置滚动条
  await nextTick()
  if (containerRef.value) {
      containerRef.value.scrollTop = 0
  }
}

const loadOcrData = async () => {
  if (!props.ocrUrl) {
    error.value = '暂无 OCR 数据'
    return
  }
  
  loading.value = true
  error.value = ''
  pages.value = []
  scale.value = 1.0
  
  try {
    const isPresigned = props.ocrUrl.includes('X-Amz-Signature=') || props.ocrUrl.includes('X-Amz-Algorithm=')
    let url = props.ocrUrl
    if (!isPresigned) {
      const u = new URL(url, window.location.origin)
      u.searchParams.set('t', String(Date.now()))
      const shareToken = new URL(window.location.href).searchParams.get('shareToken')
      if (shareToken && !u.searchParams.has('shareToken')) {
        u.searchParams.set('shareToken', shareToken)
      }
      url = u.toString()
    }
    const res = await fetch(url, { cache: 'no-store' })
    const text = await res.text()
    let json
    try {
      json = JSON.parse(text)
    } catch (e) {
      error.value = 'OCR 数据格式错误'
      loading.value = false
      return
    }
    
    parseOcrJson(json)
    
    // 数据加载完成后
    centerContent()
    
  } catch (e) {
    console.error(e)
    error.value = '加载 OCR 数据失败'
  } finally {
    loading.value = false
  }
}

const parseOcrJson = (json) => {
  if (json && Array.isArray(json.pages) && json.pages.length > 0 && json.pages[0] && Array.isArray(json.pages[0].boxes)) {
    json.pages.forEach(page => {
      const boxes = normalizeBoxes(page.boxes)
      if (boxes.length === 0) return
      const dim = {
        width: typeof page.width === 'number' ? page.width : calculateDimensions(boxes).width,
        height: typeof page.height === 'number' ? page.height : calculateDimensions(boxes).height
      }
      pages.value.push({
        width: dim.width,
        height: dim.height,
        boxes
      })
    })
    if (pages.value.length === 0) error.value = '未解析到有效的文字信息'
    return
  }

  if (json && Array.isArray(json.boxes)) {
    const boxes = normalizeBoxes(json.boxes)
    if (boxes.length > 0) {
      const dim = calculateDimensions(boxes)
      pages.value.push({
        width: dim.width,
        height: dim.height,
        boxes
      })
    } else {
      error.value = '未解析到有效的文字信息'
    }
    return
  }

  // 1. DocumentService 生成的多页结构 (PDF)
  // 结构: { pages: [ { results: [...] }, ... ] } 或直接 { pages: [ { words_result: [...] } ] }
  if (json.pages && Array.isArray(json.pages)) {
    json.pages.forEach(page => {
        if (page.error) return
        
        const boxes = extractBoxes(page)
        if (boxes.length > 0) {
            const dim = calculateDimensions(boxes)
            pages.value.push({
                width: dim.width,
                height: dim.height,
                boxes: boxes
            })
        }
    })
    
    if (pages.value.length === 0) {
        error.value = 'PDF 解析未包含有效文本'
    }
    return
  }
  
  // 2. 单页结构 (Office / Image)
  const boxes = extractBoxes(json)
  if (boxes.length > 0) {
      const dim = calculateDimensions(boxes)
      pages.value.push({
          width: dim.width,
          height: dim.height,
          boxes: boxes
      })
  } else {
      error.value = '未解析到有效的文字信息'
  }
}

const extractBoxes = (json) => {
  let items = []
  // 兼容百度 OCR 不同接口的返回字段
  if (json.results && Array.isArray(json.results)) {
      items = json.results // doc_analysis_office
  } else if (json.words_result && Array.isArray(json.words_result)) {
      items = json.words_result // general_basic / accurate
  }
  
  const boxes = []
  items.forEach(item => {
      let text = ""
      let loc = null
      
      // 情况 A: item = { words: { word: "..." }, location: {...} } (Office)
      // 或者是 item.words.word + item.words.location
      if (item.words && typeof item.words === 'object' && item.words.word) {
          text = item.words.word
          if (item.location) loc = item.location
          else if (item.words.location) loc = item.words.location
          else if (item.words.words_location) loc = item.words.words_location
      } 
      // 情况 B: item = { words: "...", location: {...} } (General)
      else if (item.words && typeof item.words === 'string') {
          text = item.words
          loc = item.location
      }
      
      if (text && loc) {
          boxes.push({
              text: text,
              left: loc.left,
              top: loc.top,
              width: loc.width,
              height: loc.height
          })
      }
  })
  return boxes
}

const normalizeBoxes = (boxes) => {
  const out = []
  boxes.forEach(b => {
    if (!b) return
    const text = typeof b.text === 'string' ? b.text : ''
    const left = Number(b.left)
    const top = Number(b.top)
    const width = Number(b.width)
    const height = Number(b.height)
    if (!text) return
    if (!Number.isFinite(left) || !Number.isFinite(top) || !Number.isFinite(width) || !Number.isFinite(height)) return
    out.push({ text, left, top, width, height })
  })
  return out
}

const calculateDimensions = (boxes) => {
    let maxWidth = 0
    let maxHeight = 0
    boxes.forEach(box => {
        const right = box.left + box.width
        const bottom = box.top + box.height
        if (right > maxWidth) maxWidth = right
        if (bottom > maxHeight) maxHeight = bottom
    })
    return { width: maxWidth + 100, height: maxHeight + 100 }
}

const getPageStyle = (page) => {
    return {
        width: page.width + 'px',
        height: page.height + 'px',
        position: 'relative',
        margin: '20px auto',
        backgroundColor: '#fff',
        boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
        border: '1px solid #e0e0e0'
    }
}

const getBoxStyle = (box) => {
    // 动态计算字体大小，尽量填满高度
    const fontSize = Math.floor(box.height * 0.75) 
    
    return {
        position: 'absolute',
        left: box.left + 'px',
        top: box.top + 'px',
        width: box.width + 'px',
        height: box.height + 'px',
        fontSize: Math.max(10, fontSize) + 'px',
        lineHeight: box.height + 'px',
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        color: '#333',
        fontFamily: 'SimSun, "Songti SC", serif', // 衬线体更像文档
        cursor: 'text',
        userSelect: 'text'
    }
}
</script>

<style scoped>
.file-preview {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  position: relative;
}

.preview-toolbar {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
  z-index: 10;
}

.file-name {
  font-weight: 600;
  font-size: 16px;
  color: #303133;
}

.preview-container {
  flex: 1;
  overflow: auto;
  position: relative;
  background-color: #525659; /* PDF Viewer gray */
  display: flex;
  justify-content: center;
  padding: 20px;
  cursor: grab;
}

.preview-container:active {
  cursor: grabbing;
}

.document-canvas {
  transition: transform 0.1s;
  box-shadow: 0 4px 15px rgba(0,0,0,0.3);
}

.page-sheet {
  background: white;
  position: relative;
  margin-bottom: 20px;
}

.text-box {
  position: absolute;
  border: 1px solid transparent;
  white-space: nowrap;
  font-family: monospace;
  cursor: text;
  color: transparent;
}

.text-box:hover {
  background-color: rgba(64, 158, 255, 0.1);
  border-color: rgba(64, 158, 255, 0.5);
  color: #409EFF;
}

.edit-input {
  position: absolute;
  border: 1px solid #409EFF;
  background: rgba(255, 255, 255, 0.9);
  padding: 0;
  margin: 0;
  outline: none;
  font-family: inherit;
  font-size: inherit;
  color: #303133;
}

.error-msg {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}
</style>
