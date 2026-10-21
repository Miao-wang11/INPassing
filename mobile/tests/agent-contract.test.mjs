import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  validateAgentReply, moneyText, validFeeString, quoteIsReady,
  isVerifiedMatch, receiptIsSuccessful, formatCandidateDescription,
} from '../utils/agent-contract.mjs'

test('Agent envelope accepts signed-Java-proxied valid status only', () => {
  assert.equal(validateAgentReply({ session_id: '7e5cff90-a8bd-4d7c-92d7-91418d103fa7',
    status: 'choose_mode' }).status, 'choose_mode')
  assert.throws(() => validateAgentReply({ code: 1, data: null }))
  assert.throws(() => validateAgentReply({ session_id: '1', status: 'choose_task' }))
  assert.throws(() => validateAgentReply({ session_id: '7e5cff90-a8bd-4d7c-92d7-91418d103fa7',
    status: 'PAYMENT_SUCCESS' }))
})

test('quote and fee validation do not silently accept NaN or negative prices', () => {
  assert.equal(moneyText('3.5'), '3.50')
  assert.equal(moneyText(undefined), '--')
  assert.equal(moneyText('-1'), '--')
  assert.equal(validFeeString('3.50'), true)
  assert.equal(validFeeString('3.123'), false)
  assert.equal(validFeeString('-1'), false)
  assert.equal(validFeeString('4e10'), false)
  assert.equal(quoteIsReady({ operationId: '178faf5c-caa3-45e9-86bd-7bc5beb29e40',
    confirmToken: 'a'.repeat(64), total: '3.00' }), true)
  assert.equal(quoteIsReady({ operationId: '178faf5c-caa3-45e9-86bd-7bc5beb29e40',
    confirmToken: '', total: 3 }), false)
})

test('route selection clearly flags unverified travel times', () => {
  const verified = { taskId: 1, routeVerified: true, estimatedDetourMinutes: 8,
    score: 72 }
  assert.equal(isVerifiedMatch(verified), true)
  assert.equal(isVerifiedMatch({ ...verified, estimatedDetourMinutes: null }), false)
  assert.match(formatCandidateDescription({ taskId: 2, routeVerified: false }), /路线未核实/)
  assert.match(formatCandidateDescription(verified), /8 分钟/)
})

test('receipt requires authoritative Java SUCCEEDED with real taskId', () => {
  assert.equal(receiptIsSuccessful({ status: 'SUCCEEDED', taskId: 12 }), true)
  assert.equal(receiptIsSuccessful({ status: 'PREPARED', taskId: 12 }), false)
  assert.equal(receiptIsSuccessful({ status: 'SUCCEEDED', taskId: null }), false)
})
