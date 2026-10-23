import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { safeRouteComparison, evidenceLabel, validateAgentReply } from '../utils/agent-contract.mjs'

const vue = readFileSync(new URL('../pages-agent/assistant/assistant.vue', import.meta.url), 'utf8')

test('model comparison contract preserves only grounded typed entries', () => {
  const rows = safeRouteComparison([
    { taskId: 7, kind: 'recommended', reason: '绕路4.7分钟，报酬8元', evidence: [
      { taskId: 7, tool: 'estimate_task_detour', field: 'estimatedDetourMinutes', value: 4.7 },
      { taskId: 7, tool: 'get_task_detail', field: 'serviceFee', value: 8 },
    ]},
    { taskId: 8, kind: 'excluded', reason: '绕路17.3分钟超过10分钟', evidence: [
      { taskId: 8, tool: 'estimate_task_detour', field: 'estimatedDetourMinutes', value: 17.3 },
    ]},
    { taskId: 999, kind: 'excluded', reason: '伪造', evidence: [
      { taskId: 8, tool: 'get_task_detail', field: 'serviceFee', value: 100 },
    ]},
  ])
  assert.equal(rows.length, 2)
  assert.equal(rows[1].kind, 'excluded')
  assert.match(evidenceLabel(rows[0].evidence[0].field), /绕行/)
})

test('route response accepts comparison but rejects unsupported status', () => {
  const data = validateAgentReply({
    session_id: '19c50e37-583c-4930-b92e-0c5cf8532ea6',
    status: 'choose_task', comparison: [], summary: '选择依据', verified: [],
  })
  assert.equal(data.summary, '选择依据')
  assert.throws(() => validateAgentReply({ session_id: data.session_id, status: 'fabricated' }))
})

test('uni-app renders summary, each evidence ref, unknowns, and still uses Java confirmation', () => {
  for (const text of ['routeSummary', 'routeComparison', 'evidenceLabel(ref.field)',
                      'item.unknowns', 'explanationDegraded', 'confirmedRoute',
                      'chooseTask(item)', 'acceptTask()', 'agentApi.acceptSuggestedTask(task.taskId)',
                      "data.status === 'ready_for_java_accept'"]) {
    assert.ok(vue.includes(text), 'missing route UI binding: ' + text)
  }
  assert.match(vue, /mode === 'delegation'/)
})
