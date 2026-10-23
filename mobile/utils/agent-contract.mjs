/**
 * Pure Stage 5 contract helpers. Keep effects, JWT, pay password and confirm
 * tokens out of localStorage and LangGraph's chat messages.
 */
const resultStatuses = new Set([
  'needs_clarification', 'lookup_unavailable', 'choose_mode', 'choose_task',
  'invalid_choice', 'no_candidates', 'budget_exhausted', 'ready_for_java_preview',
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
    && card.estimatedDetourMinutes >= 0)
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

/** Only typed, bounded evidence references are displayed; never render tool HTML. */
const evidenceFields = Object.freeze({
  serviceFee: '服务报酬（小费+配送费）',
  estimatedDetourMinutes: '额外绕行分钟',
  type: '任务类型',
  pickupArea: '取件位置',
  deliveryArea: '送达位置',
  expireTime: '接单截止时间',
})
export function evidenceLabel(field) { return evidenceFields[field] || '其他证据' }

export function safeRouteComparison(value) {
  if (!Array.isArray(value)) return []
  return value.slice(0, 6).filter(item =>
    item && Number.isInteger(item.taskId) && item.taskId > 0
    && ['recommended', 'excluded'].includes(item.kind)
    && typeof item.reason === 'string' && item.reason.length <= 300
    && Array.isArray(item.evidence) && item.evidence.length <= 6
    && item.evidence.every(ref => ref && ref.taskId === item.taskId
      && ['get_task_detail', 'estimate_task_detour'].includes(ref.tool)
      && Object.prototype.hasOwnProperty.call(evidenceFields, ref.field)
      && (typeof ref.value === 'string' || typeof ref.value === 'number'))
  )
}
