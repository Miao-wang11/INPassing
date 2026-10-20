/**
 * API 模块统一导出
 *
 * 使用方式:
 *   import { userApi, taskApi, orderApi, commonApi } from '@/api'
 *   const info = await userApi.getUserInfo()
 */

export * as userApi from './user'
export * as taskApi from './task'
export * as orderApi from './order'
export * as runnerApi from './runner'
export * as addressApi from './address'
export * as transactionApi from './transaction'
export * as reviewApi from './review'
export * as notificationApi from './notification'
export * as chatApi from './chat'
export * as commonApi from './common'

export { getToken, setToken, removeToken } from '@/utils/request'
