import request from '@/utils/request'

export function listEmployees(params: { page?: number; size?: number }) {
  return request({ url: '/admin/employees', method: 'get', params })
}

export function getEmployee(id: number) {
  return request({ url: `/admin/employees/${id}`, method: 'get' })
}

export function createEmployee(data: any) {
  return request({ url: '/admin/employees', method: 'post', data })
}

export function updateEmployee(id: number, data: any) {
  return request({ url: `/admin/employees/${id}`, method: 'put', data })
}

export function toggleEmployeeStatus(id: number, enabled: boolean) {
  return request({ url: `/admin/employees/${id}/status`, method: 'put', params: { enabled } })
}

export function resetEmployeePassword(id: number, data: { newPassword: string }) {
  return request({ url: `/admin/employees/${id}/password`, method: 'put', data })
}

export function deleteEmployee(id: number) {
  return request({ url: `/admin/employees/${id}`, method: 'delete' })
}
