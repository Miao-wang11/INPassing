import request from '@/utils/request'
export interface OperationsSummary {
  pendingCases:number; rejectedCases:number; refundedCases:number; totalDisputeRefunds:number
}
export interface LedgerCheck {
  taskId:number; taskStatus:number; expected:number; charged:number
  refunded:number; disbursed:number; debitRows:number; refundRows:number
  consistent:boolean; issue:string
}
export const getOperationsSummary=()=>request({url:'/admin/operations/summary',method:'get'})
export const listLedgerAnomalies=(limit=100)=>request({url:'/admin/operations/anomalies',method:'get',params:{limit}})
export const inspectTaskLedger=(id:number)=>request({url:`/admin/operations/ledger/${id}`,method:'get'})
