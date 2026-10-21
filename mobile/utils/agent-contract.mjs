/**
 * Pure Stage 5 contract helpers. Keep effects, JWT, pay password and confirm
 * tokens out of localStorage and LangGraph's chat messages.
 */
const resultStatuses = new Set([
  'needs_clarification', 'lookup_unavailable', 'choose_mode', 'choose_task',
  'invalid_choice', 'no_candidates', 'ready_for_java_preview',
  'ready_for_java_accept',
])
export const MODES = Object.freeze({ delegation: 'delegation', route: 'route' })

export function validateAgentReply(body) {
  if (!body || typeof body !== 'object'
      || typeof body.session_id !== 'string' || body.session_id.length < 16
      || !resultStatuses.has(body.status)) {
    throw new Error('智能服务返回的数据不完整，请重新进入页面')
  }
  return body
}

export function moneyText(value) {
  const n = Number(value)
  return Number.isFinite(n) && n >= 0 ? n.toFixed(2) : '--'
}

export function validFeeString(value) {
  return /^(?:0|[1-9]\d{0,4})(?:\.\d{1,2})?$/.test(String(value).trim())
}

export function quoteIsReady(draft) {
  return Boolean(draft && typeof draft.operationId === 'string'
    && /^[a-f0-9-]{36}$/.test(draft.operationId)
    && typeof draft.confirmToken === 'string'
    && /^[a-f0-9]{64}$/.test(draft.confirmToken)
    && Number.isFinite(Number(draft.total))
    && Number(draft.total) > 0)
}

export function isVerifiedMatch(card) {
  return Boolean(card && card.routeVerified === true
    && typeof card.taskId === 'number'
    && Number.isFinite(card.estimatedDetourMinutes)
    && Number.isFinite(card.score))
}

export function receiptIsSuccessful(receipt) {
  return receipt?.status === 'SUCCEEDED' && Number(receipt.taskId) > 0
}

export function formatCandidateDescription(card) {
  if (isVerifiedMatch(card)) {
    return '基于已核实路段估算绕路约 ' + card.estimatedDetourMinutes
      + ' 分钟（含假设交接时间），不是准时保证'
  }
  return '路线未核实，无法判断是否符合绕路上限；请自行确认'
}
