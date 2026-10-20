import request from '@/utils/request'

export function getSettings() {
  return request({ url: '/admin/settings', method: 'get' })
}

export function updateSettings(data: { items: { configKey: string; configValue: string }[] }) {
  return request({ url: '/admin/settings', method: 'put', data })
}
