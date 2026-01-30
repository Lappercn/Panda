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

export function createChatShare(sessionId) {
  return request({
    url: `/api/chat/sessions/${encodeURIComponent(sessionId)}/share`,
    method: 'post'
  })
}

export function resolveChatShare(shareToken) {
  return request({
    url: '/api/chat/share/resolve',
    method: 'get',
    params: { shareToken }
  })
}
