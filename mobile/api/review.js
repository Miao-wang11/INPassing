/**
 * 评价模块 API
 */
import { get, post, put, del } from '@/utils/request'

/** 创建评价 */
export function createReview(payload) {
  return post('/review', payload)
}

/** 追加评价 */
export function followUpReview(reviewId, content) {
  return post(`/review/${reviewId}/followup?content=${encodeURIComponent(content)}`)
}

/** 修改评价 (7天内有效) */
export function updateReview(reviewId, payload) {
  return put(`/review/${reviewId}`, payload)
}

/** 删除评价 */
export function deleteReview(reviewId) {
  return del(`/review/${reviewId}`)
}

/** 查看用户收到的评价 */
export function getUserReviews(userId) {
  return get(`/review/user/${userId}`)
}

/** 查看任务下的评价 (含追加评价) */
export function getTaskReviews(taskId) {
  return get(`/review/task/${taskId}`)
}
