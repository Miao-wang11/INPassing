/**
 * 聊天模块 API
 *
 * 实时消息走 WebSocket (STOMP):
 *   endpoint:  /ws/chat
 *   subscribe: /user/queue/chat
 *   send:      /app/chat.send
 */
import { get, put, del } from '@/utils/request'

/** 联系人列表 */
export function getContacts() {
  return get('/chat/contacts')
}

/**
 * 聊天记录
 * @param {number} userId - 对方用户ID
 * @param {object} params - page, size
 */
export function getChatHistory(userId, params = {}) {
  return get(`/chat/history/${userId}`, params)
}

/**
 * 标记已读 (将 senderId 发给当前用户的消息全部标为已读)
 * @param {number} senderId
 */
export function markChatRead(senderId) {
  return put(`/chat/read/${senderId}`)
}

/**
 * 删除自己发送的消息
 * @param {number} messageId
 */
export function deleteMessage(messageId) {
  return del(`/chat/message/${messageId}`)
}

/**
 * 撤回自己发送的消息（5分钟内）
 * @param {number} messageId
 */
export function recallMessage(messageId) {
  return put(`/chat/message/recall/${messageId}`)
}
