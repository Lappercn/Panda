import request from './request'

export function listChatSessions(shareToken = '') {
  const params = {}
  if (shareToken) params.shareToken = shareToken
  return request({
    url: '/api/chat/sessions',
    method: 'get',
    params
  })
}

export function createChatSession() {
  return request({
    url: '/api/chat/sessions',
    method: 'post'
  })
}

export function getChatMessages(sessionId, limit = 50, shareToken = '') {
  const params = { limit }
  if (shareToken) params.shareToken = shareToken
  return request({
    url: `/api/chat/sessions/${encodeURIComponent(sessionId)}/messages`,
    method: 'get',
    params
  })
}

export function createChatShare(sessionId, ttlDays) {
  const params = {}
  if (typeof ttlDays === 'number') params.ttlDays = ttlDays
  return request({
    url: `/api/chat/sessions/${encodeURIComponent(sessionId)}/share`,
    method: 'post',
    params
  })
}

export function resolveChatShare(shareToken) {
  return request({
    url: '/api/chat/share/resolve',
    method: 'get',
    params: { shareToken }
  })
}
