import request from './request'

export function listDirectory(parentId) {
  return request({
    url: '/api/fs/list',
    method: 'get',
    params: { parentId }
  })
}

export function createDirectory(parentId, name) {
  return request({
    url: '/api/fs/mkdir',
    method: 'post',
    params: { parentId, name }
  })
}

export function deleteNode(nodeId) {
  return request({
    url: '/api/fs/delete',
    method: 'delete',
    params: { nodeId }
  })
}

export function batchDeleteNodes(nodeIds) {
  return request({
    url: '/api/fs/batch-delete',
    method: 'post',
    data: nodeIds
  })
}

export function saveOcrResult(nodeId, jsonContent) {
  return request({
    url: '/api/fs/save-ocr',
    method: 'post',
    data: { nodeId, jsonContent }
  })
}

export function getDownloadUrl(nodeId) {
  return request({
    url: '/api/fs/download',
    method: 'get',
    params: { nodeId }
  })
}

export function moveNode(nodeId, newParentId) {
  return request({
    url: '/api/fs/move',
    method: 'post',
    params: { nodeId, newParentId }
  })
}

export function getPreviewData(nodeId) {
  return request({
    url: '/api/fs/preview',
    method: 'get',
    params: { nodeId }
  })
}

export function retryProcess(nodeId) {
  return request({
    url: '/api/fs/retry',
    method: 'post',
    params: { nodeId }
  })
}

// 文件上传通常需要特殊处理 Content-Type，request 拦截器通常能自动处理 FormData
export function uploadFile(parentId, file) {
  const formData = new FormData()
  formData.append('file', file)
  if (parentId) {
    formData.append('parentId', parentId)
  }
  
  return request({
    url: '/api/fs/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
