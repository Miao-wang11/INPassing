import request from '@/utils/request'

export function listTransactions(params: {
  type?: number
  userId?: number
  start?: string
  end?: string
  page?: number
  size?: number
}) {
  return request({ url: '/admin/transactions', method: 'get', params })
}
