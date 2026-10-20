/**
 * 地址簿模块 API
 */
import { get, post, put, del } from '@/utils/request'

/** 地址列表 */
export function getAddressList() {
  return get('/user/address/list')
}

/** 保存地址 */
export function saveAddress(payload) {
  return post('/user/address/save', payload)
}

/** 修改地址 */
export function updateAddress(id, payload) {
  return put(`/user/address/${id}`, payload)
}

/** 删除地址 */
export function deleteAddress(id) {
  return del(`/user/address/${id}`)
}

/** 设置默认地址 */
export function setDefaultAddress(id) {
  return put(`/user/address/${id}/default`)
}
