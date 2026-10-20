import { get,post } from '@/utils/request'
export const openDispute=(orderId,data)=>post('/disputes/orders/'+orderId,data)
export const myDisputes=()=>get('/disputes/mine')
export const disputeDetail=(id)=>get('/disputes/'+id)
export const disputeEvents=(id)=>get('/disputes/'+id+'/events')
