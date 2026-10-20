/**
 * 资金模块 API
 */
import { get, post } from '@/utils/request'

/**
 * 交易流水查询
 * @param {object} params - type(1-5), start, end, page, size
 */
export function getTransactions(params = {}) {
  return get('/user/transactions', params)
}

/** 充值（需支付密码校验） */
export function recharge(amount, payPassword) {
  return post('/user/transactions/recharge', { amount, payPassword })
}

/** 收支汇总（全量，不受分页限制） */
export function getSummary() {
  return get('/user/transactions/summary')
}

/** 提现（需支付密码校验） */
export function withdraw(amount, payPassword) {
  return post('/user/transactions/withdraw', { amount, payPassword })
}
