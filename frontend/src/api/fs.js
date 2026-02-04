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

const isProductionDomain = () => {
  const hostname = window.location.hostname
  return hostname === 'tongzhilian.cn' || hostname === 'www.tongzhilian.cn'
}

export async function uploadFile(parentId, file) {
  const pid = parentId || '0'

  if (isProductionDomain()) {
    try {
      const presignRes = await request({
        url: '/api/fs/presign-upload',
        method: 'post',
        data: { parentId: pid, fileName: file?.name }
      })

      const uploadUrl = presignRes?.data?.uploadUrl
      const objectName = presignRes?.data?.objectName
      if (uploadUrl && objectName) {
        const headers = {}
        if (file?.type) headers['Content-Type'] = file.type

        const putRes = await fetch(uploadUrl, { method: 'PUT', body: file, headers })
        if (!putRes.ok) throw new Error('direct upload failed')

        return request({
          url: '/api/fs/commit-upload',
          method: 'post',
          data: { parentId: pid, fileName: file?.name, objectName }
        })
      }
    } catch (_) {
    }
  }

  const formData = new FormData()
  formData.append('file', file)
  if (pid) {
    formData.append('parentId', pid)
  }

  return request({
    url: '/api/fs/upload',
    method: 'post',
    data: formData,
    timeout: 10 * 60 * 1000
  })
}
