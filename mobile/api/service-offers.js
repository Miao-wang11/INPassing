/** Campus service storefront and directed booking API. */
import { get,post,put } from '@/utils/request'
export const listOffers = (params = {}) => get('/service-offers',params)
export const myOffers = () => get('/service-offers/mine')
export const offerDetail = (id) => get('/service-offers/'+id)
export const createOffer = (payload) => post('/service-offers',payload)
export const editOffer = (id,payload) => put('/service-offers/'+id,payload)
export const setOfferStatus = (id,status) => put('/service-offers/'+id+'/status?status='+status,{})
export const bookOffer = (id,payload) => post('/service-offers/'+id+'/book',payload)
