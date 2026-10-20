import { ref, onUnmounted } from 'vue'

/**
 * 短信验证码发送倒计时 composable，统一管理 60s 冷却与定时器清理。
 * @returns {{ countdown: Ref<number>, startCooldown: () => void }}
 */
export function useSmsCooldown() {
  const countdown = ref(0)
  let timer = null

  function startCooldown() {
    countdown.value = 60
    if (timer) clearInterval(timer)
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
  }

  onUnmounted(() => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  })

  return { countdown, startCooldown }
}
