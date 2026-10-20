import request from '@/utils/request'

export interface CampusOption {
  campusId: number
  schoolId: number
  schoolName: string
  campusName: string
}
export interface SchoolOption {
  id: number
  name: string
  status: number
}

export const listSchools = () =>
  request({ url: '/admin/campuses/schools', method: 'get' })
export const listCampuses = () =>
  request({ url: '/admin/campuses', method: 'get' })
export const createSchool = (name: string) =>
  request({ url: '/admin/campuses/schools', method: 'post', data: { name } })
export const createCampus = (schoolId: number, name: string) =>
  request({ url: '/admin/campuses', method: 'post', data: { schoolId, name } })
