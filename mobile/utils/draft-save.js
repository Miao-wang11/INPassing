/**
 * 草稿自动保存组合式函数
 *
 * 监听表单 ref 变化，防抖写入 localStorage；页面加载时恢复，成功提交后清除。
 * storageKey 支持字符串（固定 key）或 computed ref（动态 key，用于多子页面隔离草稿）。
 *
 * 用法:
 *   const { clearDraft, restoreDraft } = useDraftSave('draft_key', { field1: ref1 })
 *   // 或动态 key: useDraftSave(computed(() => `draft_${type}_${sub}`), { ... })
 *   // onLoad 中: restoreDraft()
 *   // 提交成功后: clearDraft()
 */
import { watch } from 'vue'
import { onHide, onUnload } from '@dcloudio/uni-app'

const DRAFT_VERSION = 1
const DRAFT_TTL_MS = 30 * 60 * 1000 // 30分钟过期

export function useDraftSave(storageKey, formRefs, options = {}) {
  const { debounceMs = 2000 } = options
  let timer = null

  const resolveKey = () => typeof storageKey === 'string' ? storageKey : storageKey.value

  function persist() {
    const snapshot = { __v: DRAFT_VERSION, __t: Date.now() }
    for (const [key, ref] of Object.entries(formRefs)) {
      if (ref && ref.__v_isRef) {
        snapshot[key] = ref.value
      }
    }
    try {
      uni.setStorageSync(resolveKey(), snapshot)
    } catch (e) {
      console.warn('[draft-save] persist failed:', e.message)
    }
  }

  function scheduleSave() {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      persist()
      timer = null
    }, debounceMs)
  }

  const stopFns = []
  for (const ref of Object.values(formRefs)) {
    if (ref && ref.__v_isRef) {
      stopFns.push(watch(ref, scheduleSave, { deep: true }))
    }
  }

  onHide(() => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    persist()
  })

  onUnload(() => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    persist()
    dispose()
  })

  function dispose() {
    if (timer) clearTimeout(timer)
    stopFns.forEach(fn => fn())
  }

  function restoreDraft() {
    try {
      const key = resolveKey()
      const saved = uni.getStorageSync(key)
      if (!saved || saved.__v !== DRAFT_VERSION) {
        if (saved) clearDraft()
        return false
      }
      // 超过30分钟自动清除
      if (saved.__t && Date.now() - saved.__t > DRAFT_TTL_MS) {
        clearDraft()
        return false
      }
      for (const k of Object.keys(formRefs)) {
        if (k in saved && formRefs[k] && formRefs[k].__v_isRef) {
          formRefs[k].value = saved[k]
        }
      }
      return true
    } catch (e) {
      console.warn('[draft-save] restore failed:', e.message)
      return false
    }
  }

  function clearDraft() {
    try {
      uni.removeStorageSync(resolveKey())
    } catch (e) { console.warn('[draft-save] clear failed:', e.message) }
  }

  function hasDraft() {
    try {
      const saved = uni.getStorageSync(resolveKey())
      return saved && saved.__v === DRAFT_VERSION
    } catch (e) {
      console.warn('[draft-save] hasDraft failed:', e.message)
      return false
    }
  }

  return { clearDraft, restoreDraft, hasDraft, dispose }
}
