import request from '@/utils/request'

export function listUsers(params: {
  status?: number
  isCertify?: number
  keyword?: string
  page?: number
  size?: number
}) {
  return request({ url: '/admin/users', method: 'get', params })
}

export function getUserDetail(userId: number) {
  return request({ url: `/admin/users/${userId}`, method: 'get' })
}

export function toggleUserStatus(userId: number, enabled: boolean) {
  return request({ url: `/admin/users/${userId}/status`, method: 'put', params: { enabled } })
}

export function reviewUserCertification(userId: number, isCertify: number, remark?: string) {
  return request({ url: `/admin/users/${userId}/certify`, method: 'put', params: { isCertify, remark } })
}
