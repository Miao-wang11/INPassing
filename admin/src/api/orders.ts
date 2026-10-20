import request from '@/utils/request'

export function listOrders(params: { status?: number; page?: number; size?: number }) {
  return request({ url: '/admin/orders', method: 'get', params })
}

export function getOrderDetail(id: number) {
  return request({ url: `/admin/orders/${id}`, method: 'get' })
}

export function updateOrderStatus(id: number, status: number) {
  return request({ url: `/admin/orders/${id}/status`, method: 'put', params: { status } })
}
