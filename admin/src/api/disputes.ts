import request from '@/utils/request'

export interface Dispute {
  id:number; orderId:number; taskId:number; applicantId:number
  reason:string; evidenceUrls:string[]; status:number
  resolutionNote?:string; decidedBy?:number; decidedAt?:string; createdAt?:string
}
export const listDisputes = (params:{status?:number;page:number;size:number}) =>
  request({url:'/admin/disputes',method:'get',params})
export const getDispute = (id:number) =>
  request({url:`/admin/disputes/${id}`,method:'get'})
export const getDisputeEvents = (id:number) =>
  request({url:`/admin/disputes/${id}/events`,method:'get'})
export const decideDispute = (id:number,decision:'REJECT'|'REFUND',note:string) =>
  request({url:`/admin/disputes/${id}/decision`,method:'post',data:{decision,note}})
