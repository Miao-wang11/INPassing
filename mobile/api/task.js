/**
 * 任务模块 API
 */
import { get, post, put } from '@/utils/request'

/**
 * 发布任务
 * @param {object} payload - 见 API 文档 3.1
 */
export function publishTask(payload) {
  return post('/task/publish', payload)
}

/**
 * 任务大厅 (仅认证跑腿员)
 * @param {object} params - type, subType, minReward, maxReward, lng, lat, page, size
 */
export function getTaskList(params = {}) {
  return get('/task/list', params)
}

/**
 * 我发布的任务
 * @param {object} params - status, page, size
 */
export function getMyTasks(params = {}, opts = {}) {
  return get('/task/mine', params, opts)
}

/** 任务详情 */
export function getTaskDetail(taskId, opts = {}) {
  return get(`/task/${taskId}`, {}, opts)
}

/** 取消任务 (仅待接单状态) */
export function cancelTask(taskId, reason) {
  return put(`/task/${taskId}/cancel`, { reason })
}

/**
 * 关键词搜索
 * @param {object} params - keyword, type, minReward, maxReward, page, size
 */
export function searchTasks(params = {}) {
  return get('/task/search', params)
}

/**
 * 条件筛选任务（地点、性别、报酬范围）
 * @param {object} params - type, pickupAddress, deliveryAddress, requireSex, minReward, maxReward, page, size
 */
export function filterTasks(params = {}) {
  return get('/task/filter', params)
}

/**
 * 附近任务
 * @param {object} params - lng (必填), lat (必填), radiusKm, page, size
 */
export function getNearbyTasks(params) {
  return get('/task/nearby', params)
}

/** 我的任务统计 */
export function getTaskStatistics() {
  return get('/task/statistics')
}
