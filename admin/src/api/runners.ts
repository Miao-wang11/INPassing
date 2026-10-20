import request from '@/utils/request'

export function listRunners(params: {
  verifyStatus?: number
  keyword?: string
  page?: number
  size?: number
}) {
  return request({ url: '/admin/runners', method: 'get', params })
}

export function getRunnerDetail(profileId: number) {
  return request({ url: `/admin/runners/${profileId}`, method: 'get' })
}

export function reviewRunnerCertification(profileId: number, verifyStatus: number, remark?: string) {
  return request({ url: `/admin/runners/${profileId}/review`, method: 'put', params: { verifyStatus, remark } })
}

export function toggleRunnerBan(profileId: number, banned: boolean) {
  return request({ url: `/admin/runners/${profileId}/ban`, method: 'put', params: { banned } })
}
