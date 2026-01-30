import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const userId = ref(localStorage.getItem('panda_user_id') || '')
  const email = ref(localStorage.getItem('panda_user_email') || '')
  const loggedIn = computed(() => !!userId.value)

  function setUserId(id) {
    userId.value = id
    localStorage.setItem('panda_user_id', id)
  }

  function setUser(payload) {
    if (!payload) return
    if (payload.userId) {
      userId.value = payload.userId
      localStorage.setItem('panda_user_id', payload.userId)
    }
    if (payload.email) {
      email.value = payload.email
      localStorage.setItem('panda_user_email', payload.email)
    }
  }

  function clearUser() {
    userId.value = ''
    email.value = ''
    localStorage.removeItem('panda_user_id')
    localStorage.removeItem('panda_user_email')
  }

  return { userId, email, loggedIn, setUserId, setUser, clearUser }
})
