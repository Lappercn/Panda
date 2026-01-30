import axios from 'axios'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: '/', // 使用代理
  withCredentials: true,
  timeout: 60000 // 请求超时时间
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.userId) {
      config.headers['X-User-ID'] = userStore.userId
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    const res = response.data
    // 假设后端统一返回 { code: 200, data: ..., message: ... }
    if (res.code && res.code !== 200) {
      ElMessage.error(res.message || 'Error')
      return Promise.reject(new Error(res.message || 'Error'))
    } else {
      return res
    }
  },
  error => {
    console.log('err' + error)
    ElMessage.error(error.message)
    return Promise.reject(error)
  }
)

export default service
