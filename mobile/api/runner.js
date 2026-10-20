/**
 * 跑腿员模块 API
 */
import { get, post, put } from '@/utils/request'

/** 申请成为配送员（需 isCertify=2） */
export function applyRunner() {
  return post('/runner/apply')
}

/** 获取跑腿员信息（需 isCertify=2） */
export function getRunnerProfile(opts = {}) {
  return get('/runner/profile', {}, opts)
}

/** 上线接单 */
export function goOnline() {
  return post('/runner/online')
}

/** 离线 */
export function goOffline() {
  return post('/runner/offline')
}

/** 设置最大接单数 (1-5) */
export function setMaxOrders(maxOrders) {
  return put('/runner/max-orders', { maxOrders })
}

/**
 * 跑腿员排行榜
 * @param {string} sortBy - 'orders' | 'rating' | 'completion'
 * @param {number} limit  - 返回前 N 名
 */
export function getLeaderboard(sortBy = 'orders', limit = 10) {
  return get('/runner/leaderboard', { sortBy, limit })
}

/** 跑腿员绩效 (userId 为跑腿员用户ID) */
export function getPerformance(userId, opts = {}) {
  return get(`/runner/performance/${userId}`, {}, opts)
}
