<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <div class="brand">
        <div class="brand-title">🐼 Panda 知识库</div>
        <div class="brand-sub">登录后即可使用知识库问答与文件管理</div>
      </div>

      <el-tabs v-model="tab" class="tabs" stretch>
        <el-tab-pane label="登录" name="login">
          <el-segmented v-model="loginMode" :options="loginModes" class="seg" />

          <el-form :model="loginForm" class="form" label-position="top">
            <el-form-item label="邮箱">
              <el-input v-model="loginForm.email" placeholder="name@example.com" />
            </el-form-item>

            <el-form-item v-if="loginMode === 'password'" label="密码">
              <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" />
            </el-form-item>

            <el-form-item v-else label="验证码">
              <div class="code-row">
                <el-input v-model="loginForm.code" placeholder="6 位验证码" />
                <el-button :disabled="loginSendDisabled" @click="sendLoginCode">
                  {{ loginSendText }}
                </el-button>
              </div>
            </el-form-item>

            <el-button type="primary" :loading="submitting" class="primary" @click="submitLogin">
              登录
            </el-button>

            <div class="links">
              <el-link type="primary" :underline="false" @click="tab = 'reset'">忘记密码</el-link>
              <el-link type="primary" :underline="false" @click="tab = 'register'">去注册</el-link>
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" class="form" label-position="top">
            <el-form-item label="邮箱">
              <el-input v-model="registerForm.email" placeholder="name@example.com" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="registerForm.password" type="password" show-password placeholder="至少 8 位" />
            </el-form-item>
            <el-form-item label="验证码">
              <div class="code-row">
                <el-input v-model="registerForm.code" placeholder="6 位验证码" />
                <el-button :disabled="registerSendDisabled" @click="sendRegisterCode">
                  {{ registerSendText }}
                </el-button>
              </div>
            </el-form-item>

            <el-button type="primary" :loading="submitting" class="primary" @click="submitRegister">
              注册并登录
            </el-button>

            <div class="links">
              <el-link type="primary" :underline="false" @click="tab = 'login'">已有账号？去登录</el-link>
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="找回密码" name="reset">
          <el-form :model="resetForm" class="form" label-position="top">
            <el-form-item label="邮箱">
              <el-input v-model="resetForm.email" placeholder="name@example.com" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="至少 8 位" />
            </el-form-item>
            <el-form-item label="验证码">
              <div class="code-row">
                <el-input v-model="resetForm.code" placeholder="6 位验证码" />
                <el-button :disabled="resetSendDisabled" @click="sendResetCode">
                  {{ resetSendText }}
                </el-button>
              </div>
            </el-form-item>

            <el-button type="primary" :loading="submitting" class="primary" @click="submitReset">
              重置密码
            </el-button>

            <div class="links">
              <el-link type="primary" :underline="false" @click="tab = 'login'">返回登录</el-link>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="note">验证码有效期以邮件内容为准，发送可能有延迟，请耐心等待。</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { loginByEmailCode, loginByPassword, registerByEmailCode, resetPassword, sendEmailCode } from '../api/auth'
import { useUserStore } from '../stores/user'

const emit = defineEmits(['success'])

const userStore = useUserStore()

const tab = ref('login')
const submitting = ref(false)

const loginModes = [
  { label: '密码登录', value: 'password' },
  { label: '验证码登录', value: 'code' }
]
const loginMode = ref('password')

const loginForm = ref({ email: '', password: '', code: '' })
const registerForm = ref({ email: '', password: '', code: '' })
const resetForm = ref({ email: '', newPassword: '', code: '' })

const timers = new Map()
const cooldowns = ref({
  login: 0,
  register: 0,
  reset: 0
})

const loginSendDisabled = computed(() => cooldowns.value.login > 0 || submitting.value)
const registerSendDisabled = computed(() => cooldowns.value.register > 0 || submitting.value)
const resetSendDisabled = computed(() => cooldowns.value.reset > 0 || submitting.value)

const loginSendText = computed(() => (cooldowns.value.login > 0 ? `${cooldowns.value.login}s` : '发送验证码'))
const registerSendText = computed(() => (cooldowns.value.register > 0 ? `${cooldowns.value.register}s` : '发送验证码'))
const resetSendText = computed(() => (cooldowns.value.reset > 0 ? `${cooldowns.value.reset}s` : '发送验证码'))

const startCooldown = (key, seconds = 60) => {
  stopCooldown(key)
  cooldowns.value[key] = seconds
  const id = setInterval(() => {
    cooldowns.value[key] = Math.max(0, cooldowns.value[key] - 1)
    if (cooldowns.value[key] === 0) stopCooldown(key)
  }, 1000)
  timers.set(key, id)
}

const stopCooldown = (key) => {
  const id = timers.get(key)
  if (id) clearInterval(id)
  timers.delete(key)
}

onBeforeUnmount(() => {
  for (const id of timers.values()) clearInterval(id)
  timers.clear()
})

const sendRegisterCode = async () => {
  const email = registerForm.value.email?.trim()
  if (!email) {
    ElMessage.warning('请输入邮箱')
    return
  }
  submitting.value = true
  try {
    await sendEmailCode(email, 'REGISTER')
    ElMessage.success('验证码已发送')
    startCooldown('register', 60)
  } finally {
    submitting.value = false
  }
}

const sendLoginCode = async () => {
  const email = loginForm.value.email?.trim()
  if (!email) {
    ElMessage.warning('请输入邮箱')
    return
  }
  submitting.value = true
  try {
    await sendEmailCode(email, 'LOGIN')
    ElMessage.success('验证码已发送')
    startCooldown('login', 60)
  } finally {
    submitting.value = false
  }
}

const sendResetCode = async () => {
  const email = resetForm.value.email?.trim()
  if (!email) {
    ElMessage.warning('请输入邮箱')
    return
  }
  submitting.value = true
  try {
    await sendEmailCode(email, 'RESET_PASSWORD')
    ElMessage.success('验证码已发送')
    startCooldown('reset', 60)
  } finally {
    submitting.value = false
  }
}

const submitLogin = async () => {
  const email = loginForm.value.email?.trim()
  if (!email) return ElMessage.warning('请输入邮箱')

  submitting.value = true
  try {
    let res
    if (loginMode.value === 'password') {
      if (!loginForm.value.password) return ElMessage.warning('请输入密码')
      res = await loginByPassword(email, loginForm.value.password)
    } else {
      if (!loginForm.value.code) return ElMessage.warning('请输入验证码')
      res = await loginByEmailCode(email, loginForm.value.code)
    }
    userStore.setUser(res.data)
    ElMessage.success('登录成功')
    emit('success')
  } finally {
    submitting.value = false
  }
}

const submitRegister = async () => {
  const email = registerForm.value.email?.trim()
  if (!email) return ElMessage.warning('请输入邮箱')
  if (!registerForm.value.password) return ElMessage.warning('请输入密码')
  if (!registerForm.value.code) return ElMessage.warning('请输入验证码')

  submitting.value = true
  try {
    const res = await registerByEmailCode(email, registerForm.value.password, registerForm.value.code)
    userStore.setUser(res.data)
    ElMessage.success('注册成功')
    emit('success')
  } finally {
    submitting.value = false
  }
}

const submitReset = async () => {
  const email = resetForm.value.email?.trim()
  if (!email) return ElMessage.warning('请输入邮箱')
  if (!resetForm.value.newPassword) return ElMessage.warning('请输入新密码')
  if (!resetForm.value.code) return ElMessage.warning('请输入验证码')

  submitting.value = true
  try {
    await resetPassword(email, resetForm.value.newPassword, resetForm.value.code)
    ElMessage.success('密码已重置，请登录')
    tab.value = 'login'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-wrap {
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(1200px 600px at 50% -10%, rgba(var(--ni-primary-rgb), 0.25), transparent 60%),
    var(--ni-bg-light);
}

.auth-card {
  width: 420px;
  max-width: calc(100vw - 32px);
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--ni-border);
  border-radius: 16px;
  box-shadow: var(--ni-shadow-lg);
  padding: 18px 18px 14px;
  backdrop-filter: blur(12px);
}

.brand {
  margin-bottom: 10px;
}

.brand-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--ni-text-main);
}

.brand-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ni-text-light);
}

.tabs :deep(.el-tabs__header) {
  margin: 8px 0 8px;
}

.seg {
  margin-bottom: 10px;
}

.form {
  margin-top: 4px;
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.primary {
  width: 100%;
  margin-top: 6px;
}

.links {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
}

.note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--ni-text-light);
}
</style>
