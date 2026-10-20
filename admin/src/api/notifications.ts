import request from '@/utils/request'

export function sendNotification(data: { userIds: number[]; type: number; title: string; content: string }) {
  return request({ url: '/admin/notifications/send', method: 'post', data })
}

export function broadcastNotification(data: { type: number; title: string; content: string }) {
  return request({ url: '/admin/notifications/broadcast', method: 'post', data })
}
