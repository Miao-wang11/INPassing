/**
 * 全局支付密码弹窗 — 所有页面统一的支付密码输入入口
 *
 * 使用方式:
 *   import { promptPayPassword } from '@/utils/pay-password'
 *   const pw = await promptPayPassword('发布任务')
 *   if (!pw) return // 用户取消
 *
 * 弹窗自动使用数字键盘 (type="number" password)，仅允许纯数字输入。
 */
import { ref } from 'vue'

const visible = ref(false)
const title = ref('支付密码')
const hint = ref('')
let resolver = null

/** 弹出支付密码输入框，返回用户输入的密码或 null */
export function promptPayPassword(action) {
  return new Promise((resolve) => {
    hint.value = action ? `请输入支付密码以确认${action}` : '请输入支付密码'
    resolver = resolve
    visible.value = true
  })
}

/** 对话框 confirm 回调 */
export function confirmPayPassword(pw) {
  visible.value = false
  if (resolver) {
    resolver(pw || null)
    resolver = null
  }
}

/** 对话框 cancel 回调 */
export function cancelPayPassword() {
  visible.value = false
  if (resolver) {
    resolver(null)
    resolver = null
  }
}

/** 供 PayPasswordDialog 组件使用的响应式状态 */
export function useDialogState() {
  return { visible, title, hint }
}
