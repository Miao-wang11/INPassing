/**
 * 订单模块 API
 */
import { get, post, del } from '@/utils/request'

/** 跑腿员接单 (需要认证、在线、信用分≥60) */
export function acceptOrder(taskId) {
  return post(`/order/accept/${taskId}`)
}

/** 确认取货 */
export function confirmPickup(orderId, imageUrls) {
  return post(`/order/${orderId}/pickup`, { imageUrls })
}

/** 确认送达 */
export function confirmDeliver(orderId, imageUrls) {
  return post(`/order/${orderId}/deliver`, { imageUrls })
}

/** 确认完成 (仅发布者可操作) */
export function confirmOrder(orderId) {
  return post(`/order/${orderId}/confirm`)
}

/** 订单详情 (按订单ID) */
export function getOrderDetail(orderId, opts = {}) {
  return get(`/order/${orderId}`, {}, opts)
}

/** 订单详情 (按任务ID) */
export function getOrderByTask(taskId, opts = {}) {
  return get(`/order/task/${taskId}`, {}, opts)
}

/**
 * 我的接单列表
 * @param {object} params - status, page, size
 */
export function getMyOrders(params = {}, opts = {}) {
  return get('/order/mine', params, opts)
}

/** 配送员取消订单 (接单后5分钟内) */
export function cancelOrder(orderId, reason) {
  return post(`/order/${orderId}/cancel`, { reason })
}

/** 删除订单 (已完成超过7天，软删除) */
export function deleteOrder(orderId) {
  return del(`/order/${orderId}`)
}
