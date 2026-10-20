/**
 * 全局聊天 Store — 单例 STOMP 连接 + 联系人 + 消息缓存
 *
 * 所有聊天页面共享此 Store 的 WebSocket 连接和状态。
 */
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { chatApi } from '@/api'
import { StompClient } from '@/utils/stomp'
import { WS_URL, BASE_URL } from '@/utils/config'
import { showToast } from '@/utils/toast'
import { getToken, getRefreshToken, setToken, setRefreshToken, removeToken, removeRefreshToken } from '@/utils/request'

export const useChatStore = defineStore('chat', () => {
  // ---- state ----
  const stompClient = ref(null)
  const wsConnected = ref(false)
  const _connecting = ref(false)  // 防止并发连接请求
  const currentUserId = ref(null)
  const contacts = ref([])
  const messageCache = ref(new Map())  // peerUserId → messages[]
  const hasMore = ref(new Map())       // peerUserId → boolean
  const currentPage = ref(new Map())   // peerUserId → page number

  // ---- getters ----
  const unreadTotal = computed(() =>
    contacts.value.reduce((sum, c) => sum + (c.unreadCount || 0), 0)
  )

  function getMessages(peerUserId) {
    return messageCache.value.get(peerUserId) || []
  }

  // ---- actions ----
  let _retryTimer = null
  let _retryCount = 0
  const MAX_RETRIES = 3

  /** 尝试用 refresh token 换取新 access token，失败返回 null */
  async function refreshUserToken() {
    const refreshToken = getRefreshToken()
    if (!refreshToken) return null

    return new Promise((resolve) => {
      uni.request({
        url: BASE_URL + '/user/refresh',
        method: 'POST',
        data: {},
        header: {
          'Content-Type': 'application/json',
          'X-Refresh-Token': refreshToken
        },
        success(res) {
          const { statusCode, data: body } = res
          if (statusCode === 200 && body.code === 1) {
            const { token, refreshToken: newRefreshToken } = body.data
            setToken(token)
            setRefreshToken(newRefreshToken)
            resolve(token)
          } else {
            resolve(null)
          }
        },
        fail() {
          resolve(null)
        }
      })
    })
  }

  async function connectStomp(token, userId) {
    if (stompClient.value && wsConnected.value) return
    if (_connecting.value) return  // 正在连接中，跳过重复请求
    _connecting.value = true

    // 优先从持久化存储读取最新 token，参数仅作为 fallback
    const effectiveToken = getToken() || token
    if (!effectiveToken) { console.warn('[ChatStore] connectStomp called without token'); _connecting.value = false; return }

    if (userId) currentUserId.value = userId
    console.log('[ChatStore] Connecting STOMP...')
    const client = new StompClient()
    try {
      await client.connect(`${WS_URL}/chat?token=${encodeURIComponent(effectiveToken)}`)
      wsConnected.value = true
      stompClient.value = client
      _connecting.value = false
      _retryCount = 0
      console.log('[ChatStore] STOMP connected, subscribing...')

      client.subscribe('/user/queue/chat', (body) => {
        try {
          const msg = JSON.parse(body)
          console.log('[ChatStore] Received message:', msg.messageId)
          handleIncomingMessage(msg)
        } catch (e) { /* ignore */ }
      })
      console.log('[ChatStore] Subscribed to /user/queue/chat')
    } catch (e) {
      wsConnected.value = false
      _connecting.value = false
      // 确保失败的 socket 被关闭，释放微信 WebSocket 连接配额
      try { client.disconnect() } catch (_) { /* ignore */ }
      console.error('[ChatStore] STOMP connect failed:', e)

      // wcwss exceed: 微信 WebSocket 连接数已满，重试只会加深问题
      const errMsg = e?.errMsg || e?.message || ''
      if (errMsg.includes('exceed max concurrent')) {
        console.warn('[ChatStore] WebSocket 连接数已满，放弃本次连接')
        disconnectStomp()
        return
      }

      // 超过最大重试次数，放弃
      _retryCount++
      if (_retryCount > MAX_RETRIES) {
        console.warn('[ChatStore] STOMP 重试次数已达上限，放弃连接')
        _retryCount = 0
        disconnectStomp()
        return
      }

      // 尝试刷新 token 后再重连
      try {
        const newToken = await refreshUserToken()
        if (newToken) {
          console.log(`[ChatStore] Token refreshed, retrying STOMP (${_retryCount}/${MAX_RETRIES})`)
          _retryTimer = setTimeout(() => {
            if (!wsConnected.value) connectStomp()
          }, 3000)
        } else {
          throw new Error('REFRESH_FAILED')
        }
      } catch (_) {
        console.warn('[ChatStore] Token refresh failed, giving up')
        _retryCount = 0
        disconnectStomp()
        clearCache()
        removeToken()
        removeRefreshToken()
        showToast('登录已过期，请重新登录', { duration: 2000 })
        setTimeout(() => {
          uni.reLaunch({ url: '/pages/login/login' })
        }, 1500)
      }
    }
  }

  function disconnectStomp() {
    if (_retryTimer) { clearTimeout(_retryTimer); _retryTimer = null }
    if (stompClient.value) {
      try { stompClient.value.disconnect() } catch (e) { /* ignore */ }
      stompClient.value = null
    }
    wsConnected.value = false
    _connecting.value = false
  }

  function handleIncomingMessage(msg) {
    // 处理删除/撤回 action（对方操作后的推送）
    if (msg.messageAction === 1) {
      removeMessageFromCache(msg.messageId)
      return
    }
    if (msg.messageAction === 2) {
      markMessageRecalled(msg.messageId, msg)
      return
    }

    // 判断是否为发送者自己的消息回显（服务端推送回 messageId）
    const isSelf = currentUserId.value && msg.senderId === currentUserId.value
    // 自己发的消息按 receiverId 缓存（会话对象是接收方），他人发的消息按 senderId 缓存
    const peerId = isSelf ? msg.receiverId : msg.senderId
    if (!peerId) return

    const cache = messageCache.value.get(peerId) || []
    // 去重：messageId 匹配
    if (msg.messageId && cache.some(m => m.messageId === msg.messageId)) return

    if (isSelf) {
      // 自己的消息回显：替换匹配的本地乐观消息（按 receiverId + content 匹配）
      const localIdx = cache.findIndex(m =>
        !m.messageId && m.senderId === msg.senderId && m.content === msg.content
      )
      if (localIdx > -1) {
        cache[localIdx] = { ...msg, localId: cache[localIdx].localId, showTime: false }
        messageCache.value.set(peerId, cache)
        messageCache.value = new Map(messageCache.value)
      }
      // 自己发的消息不回显到缓存（已在 addLocalMessage 中添加），无需更新联系人
      return
    }

    // 他人发来的消息：更新消息缓存
    const localIdx = cache.findIndex(m =>
      !m.messageId && m.senderId === msg.senderId && m.content === msg.content
    )
    if (localIdx > -1) {
      cache[localIdx] = { ...msg, localId: cache[localIdx].localId, showTime: false }
    } else {
      cache.push(msg)
    }
    messageCache.value.set(peerId, cache)
    messageCache.value = new Map(messageCache.value)

    // 更新联系人列表
    const idx = contacts.value.findIndex(c => c.userId === peerId)
    const unread = (idx >= 0 ? contacts.value[idx].unreadCount || 0 : 0) + 1

    if (idx >= 0) {
      const c = { ...contacts.value[idx] }
      c.lastMessage = msg.content || ''
      c.lastMessageTime = msg.createdAt || ''
      c.unreadCount = unread
      contacts.value.splice(idx, 1)
      contacts.value.unshift(c)
    } else {
      contacts.value.unshift({
        userId: peerId,
        nickname: msg.senderNickname || '',
        avatarUrl: msg.senderAvatar || '',
        lastMessage: msg.content || '',
        lastMessageTime: msg.createdAt || '',
        unreadCount: 1
      })
    }
  }

  // ---- 消息操作（删除 / 撤回） ----

  function removeMessageFromCache(messageId) {
    for (const [peerId, cache] of messageCache.value) {
      const idx = cache.findIndex(m => m.messageId === messageId)
      if (idx > -1) {
        cache.splice(idx, 1)
        messageCache.value.set(peerId, [...cache])
        messageCache.value = new Map(messageCache.value)
        return
      }
    }
  }

  function markMessageRecalled(messageId, pushData) {
    for (const [peerId, cache] of messageCache.value) {
      const idx = cache.findIndex(m => m.messageId === messageId)
      if (idx > -1) {
        cache[idx] = {
          ...cache[idx],
          content: '[消息已撤回]',
          isRecalled: true,
          ...pushData
        }
        messageCache.value.set(peerId, [...cache])
        messageCache.value = new Map(messageCache.value)
        // 更新联系人最后消息
        const contact = contacts.value.find(c => c.userId === peerId)
        if (contact) contact.lastMessage = '[消息已撤回]'
        return
      }
    }
  }

  async function deleteMessage(messageId) {
    await chatApi.deleteMessage(messageId)
    removeMessageFromCache(messageId)
  }

  async function recallMessage(messageId) {
    await chatApi.recallMessage(messageId)
    markMessageRecalled(messageId, { content: '[消息已撤回]', isRecalled: true })
  }

  async function batchDelete(messageIds) {
    let failed = 0
    for (const id of messageIds) {
      try {
        await chatApi.deleteMessage(id)
        removeMessageFromCache(id)
      } catch (e) {
        failed++
        console.warn('[ChatStore] batchDelete failed for', id, e.message)
      }
    }
    const ok = messageIds.length - failed
    if (failed > 0) {
      showToast(`已删除 ${ok} 条，${failed} 条失败`)
    }
  }

  async function loadContacts() {
    try {
      const data = await chatApi.getContacts()
      const freshList = (data || []).map(c => ({
        ...c,
        initial: (c.nickname || '用').charAt(0)
      }))
      // 合并已有的未读数（WebSocket 实时更新的可能更新）
      for (const fresh of freshList) {
        const existing = contacts.value.find(c => c.userId === fresh.userId)
        if (existing && existing.unreadCount > fresh.unreadCount) {
          fresh.unreadCount = existing.unreadCount
        }
      }
      // 保留 contacts 中不全在 freshList 里的联系人（如 WebSocket 新联系人还没被后端返回）
      const freshIds = new Set(freshList.map(c => c.userId))
      const onlyLocal = contacts.value.filter(c => !freshIds.has(c.userId))
      contacts.value = [...onlyLocal, ...freshList].sort(
        (a, b) => new Date(b.lastMessageTime || 0).getTime() - new Date(a.lastMessageTime || 0).getTime()
      )
    } catch (e) {
      console.warn('[ChatStore] loadContacts failed:', e.message)
    }
  }

  async function loadHistory(peerUserId, page = 1, size = 20) {
    try {
      const data = await chatApi.getChatHistory(peerUserId, { page, size })
      const list = (data || []).map(m => ({
        ...m,
        localId: m.messageId || ('hist-' + Date.now() + '-' + Math.random()),
        showTime: true
      })).reverse()

      const hasMorePages = list.length >= size
      hasMore.value.set(peerUserId, hasMorePages)

      let cache = messageCache.value.get(peerUserId) || []
      const existingIds = new Set(cache.map(m => m.messageId).filter(Boolean))
      // 去重：messageId 匹配 或 content+senderId 匹配（乐观本地消息）
      const newMessages = list.filter(m => {
        if (m.messageId && existingIds.has(m.messageId)) return false
        // 检查是否有匹配的本地乐观消息
        const dup = cache.find(c =>
          !c.messageId &&
          c.senderId === m.senderId &&
          c.content === m.content
        )
        if (dup) {
          // 用 DB 消息替换本地消息
          const idx = cache.indexOf(dup)
          cache[idx] = { ...m, localId: dup.localId, showTime: false }
          return false
        }
        return true
      })
      cache = [...newMessages, ...cache]
      cache.sort((a, b) => new Date(a.createdAt || 0).getTime() - new Date(b.createdAt || 0).getTime())
      messageCache.value.set(peerUserId, cache)
      messageCache.value = new Map(messageCache.value) // 触发响应式

      currentPage.value.set(peerUserId, page)
      return cache
    } catch (e) {
      console.error('Load history failed:', e)
      return messageCache.value.get(peerUserId) || []
    }
  }

  async function sendMessage(receiverId, content, messageType = 1) {
    if (!stompClient.value || !stompClient.value.connected) {
      throw new Error('消息未连接')
    }
    const senderId = null // store 不存 userId，由调用方传入或从 chat-detail 传入

    stompClient.value.send('/app/chat.send', {}, JSON.stringify({
      receiverId,
      content,
      messageType
    }))
  }

  function addLocalMessage(msg) {
    const peerId = msg.receiverId
    const cache = messageCache.value.get(peerId) || []
    const exists = cache.some(m => m.localId === msg.localId)
    if (!exists) {
      cache.push({ ...msg, showTime: false })
      messageCache.value.set(peerId, cache)
      messageCache.value = new Map(messageCache.value)
    }
    // 更新联系人
    const idx = contacts.value.findIndex(c => c.userId === peerId)
    if (idx >= 0) {
      const c = { ...contacts.value[idx] }
      c.lastMessage = msg.content || ''
      c.lastMessageTime = msg.createdAt || ''
      contacts.value.splice(idx, 1)
      contacts.value.unshift(c)
    }
  }

  function removeLocalMessage(localId, peerUserId) {
    const cache = messageCache.value.get(peerUserId)
    if (!cache) return
    const idx = cache.findIndex(m => m.localId === localId)
    if (idx > -1) {
      cache.splice(idx, 1)
      messageCache.value.set(peerUserId, [...cache])
      messageCache.value = new Map(messageCache.value)
    }
  }

  async function markRead(senderId) {
    try {
      await chatApi.markChatRead(senderId)
    } catch (e) {
      console.warn('[ChatStore] markRead failed:', e.message)
    }
    // 本地清零未读数
    const c = contacts.value.find(c => c.userId === senderId)
    if (c) c.unreadCount = 0
  }

  function clearCache() {
    messageCache.value = new Map()
    contacts.value = []
    hasMore.value = new Map()
    currentPage.value = new Map()
    currentUserId.value = null
  }

  return {
    stompClient, wsConnected, contacts, messageCache,
    unreadTotal,
    getMessages,
    connectStomp, disconnectStomp, handleIncomingMessage,
    loadContacts, loadHistory, sendMessage,
    addLocalMessage, removeLocalMessage, markRead,
    deleteMessage, recallMessage, batchDelete,
    clearCache
  }
})
