/**
 * Stage 5 student Agent endpoints. ONLY Java's existing JWT-protected API is used;
 * never call the Python service or pass userId, campusId or passwords to a model.
 */
import { get, post } from '@/utils/request'

const quiet = { showLoading: false, showError: false }

export const talkDelegation = (payload) => post('/agent/chat', payload, quiet)
export const talkRoute = (payload) => post('/agent/route-match', payload, quiet)
export const previewDelegation = (prefill) => post('/agent/drafts', prefill, quiet)
export const confirmDelegation = (operationId, confirmToken, payPassword) =>
  post('/agent/operations/' + encodeURIComponent(operationId) + '/confirm',
    { confirmToken, payPassword }, quiet)
export const getDelegationReceipt = (operationId) =>
  get('/agent/operations/' + encodeURIComponent(operationId), {}, quiet)

/** The ONLY matching write path; reuse Java TaskOrderService.acceptOrder. */
export const acceptSuggestedTask = (taskId) =>
  post('/agent/matches/accept/' + encodeURIComponent(taskId), {}, quiet)
