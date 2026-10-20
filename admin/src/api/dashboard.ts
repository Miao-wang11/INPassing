import request from '@/utils/request'

export function getDashboard() {
  return request({ url: '/admin/dashboard', method: 'get' })
}
