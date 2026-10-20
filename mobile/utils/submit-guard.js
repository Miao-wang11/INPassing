/**
 * 防重复提交工具 — Composable
 *
 * 用法:
 *   const { lock, unlock, locked } = useSubmitLock()
 *
 *   async function onSubmit() {
 *     if (!lock()) return
 *     try {
 *       await someApi()
 *     } finally {
 *       unlock()
 *     }
 *   }
 *
 * 模板:
 *   <view :class="{ 'btn--disabled': locked }" @click="onSubmit">
 *     <text>{{ locked ? '提交中…' : '提交' }}</text>
 *   </view>
 *
 *   CSS: .btn--disabled { pointer-events: none; opacity: 0.6; }
 */
import { ref } from 'vue'

export function useSubmitLock() {
  const locked = ref(false)

  /** 尝试加锁，返回 true 表示可以继续操作 */
  function lock() {
    if (locked.value) return false
    locked.value = true
    return true
  }

  function unlock() {
    locked.value = false
  }

  return { lock, unlock, locked }
}
