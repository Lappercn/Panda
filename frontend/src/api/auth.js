import request from './request'

export function sendEmailCode(email, purpose) {
  return request({
    url: '/api/auth/send-code',
    method: 'post',
    data: { email, purpose }
  })
}

export function registerByEmailCode(email, password, code) {
  return request({
    url: '/api/auth/register',
    method: 'post',
    data: { email, password, code }
  })
}

export function loginByPassword(email, password) {
  return request({
    url: '/api/auth/login/password',
    method: 'post',
    data: { email, password }
  })
}

export function loginByEmailCode(email, code) {
  return request({
    url: '/api/auth/login/code',
    method: 'post',
    data: { email, code }
  })
}

export function resetPassword(email, newPassword, code) {
  return request({
    url: '/api/auth/password/reset',
    method: 'post',
    data: { email, newPassword, code }
  })
}

export function getMe() {
  return request({
    url: '/api/auth/me',
    method: 'get'
  })
}

export function logout() {
  return request({
    url: '/api/auth/logout',
    method: 'post'
  })
}
