import request from '@/utils/request'

export function listLogs(params: {
  module?: string
  adminId?: number
  start?: string
  end?: string
  page?: number
  size?: number
}) {
  return request({ url: '/admin/logs', method: 'get', params })
}
