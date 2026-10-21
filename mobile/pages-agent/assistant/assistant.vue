<template>
  <view class="ai-page">
    <uni-nav-bar title="顺手 · 智能助手" leftIcon="left" statusBar :border="false"
      backgroundColor="#FAFAF8" @clickLeft="goBack" />
    <view class="mode-tabs">
      <view :class="['mode-tab', { active: mode === 'delegation' }]" @click="switchMode('delegation')">智能委托</view>
      <view :class="['mode-tab', { active: mode === 'route' }]" @click="switchMode('route')">顺路接单</view>
    </view>
    <scroll-view class="conversation" scroll-y :scroll-into-view="scrollAnchor">
      <view class="intro">
        <text class="intro-title">{{ mode === 'delegation' ? '把需求说清楚，交给顺手' : '从哪里出发，顺路接点单' }}</text>
        <text class="note">智能建议仅供参考；订单、身份、价格和扣款均由原有 Java 系统最终校验。</text>
      </view>

      <view v-for="(msg, i) in messages" :key="i" :class="['bubble', msg.role]">
        <text selectable>{{ msg.text }}</text>
      </view>
      <view v-if="busy" class="bubble agent"><text>正在核实校区任务和业务条件…</text></view>

      <view v-if="mode === 'delegation'" class="guided">
        <text class="label">本人收货地址（仅提交地址簿 ID；姓名、电话不会发给模型）</text>
        <picker v-if="addresses.length" :range="addresses" range-key="detail" @change="pickAddress">
          <view class="select-field">{{ selectedAddress ? selectedAddress.detail : '点击选择地址' }} ▾</view>
        </picker>
        <text v-else class="hint">尚无可用地址，请先添加收货地址。</text>
        <text class="link" @click="goAddress">管理收货地址 ›</text>

        <text class="label">公开任务费用分项（先补齐，再由 Java 计算正式报价）</text>
        <view class="fee-inputs">
          <view class="fee-field">
            <text class="hint">小费 ¥</text>
            <input type="digit" v-model="fee.tip" placeholder="0.00"/>
          </view>
          <view class="fee-field">
            <text class="hint">配送费 ¥</text>
            <input type="digit" v-model="fee.delivery" placeholder="0.00"/>
          </view>
          <view class="fee-field">
            <text class="hint">商品预算 ¥</text>
            <input type="digit" v-model="fee.goods" placeholder="0.00"/>
          </view>
        </view>
        <view class="secondary-btn" @click="sendFeeDetails">发送费用分项（不代表支付）</view>
      </view>

      <view v-if="mode === 'delegation' && status === 'choose_mode'" class="result-card">
        <text class="card-title">选择办理方式</text>
        <view v-for="offer in offers" :key="offer.offerId" class="offer-line">
          <text class="name">{{ offer.title }}</text>
          <text class="meta">服务费 ¥{{ moneyText(offer.servicePrice) }} · {{ offer.category }}</text>
          <view class="primary-btn" @click="selectOffer(offer)">选择此服务</view>
        </view>
        <view class="secondary-btn" @click="chooseMode('public')">没有合适服务，生成公开任务草稿</view>
        <text class="hint">这里只选择方式；并未创建订单或扣款。</text>
      </view>

      <view v-if="mode === 'delegation' && draftPrefill && !prepared" class="result-card">
        <text class="card-title">草稿参数（尚未校验价格）</text>
        <text class="detail">方式：{{ draftPrefill.mode === 'PUBLIC_TASK' ? '发布公开任务' : '预约学生服务' }}</text>
        <text v-if="draftPrefill.task" class="detail">描述：{{ draftPrefill.task.publicDesc }}</text>
        <text class="hint">请由 Java 服务重新校验报酬、余额、校区和地址后，获取正式报价。</text>
        <view class="primary-btn" :class="{ disabled: busy }" @click="previewDraft">获取 Java 正式报价</view>
      </view>

      <view v-if="prepared" class="result-card">
        <text class="card-title">Java 校验后的草稿报价</text>
        <text class="detail">业务：{{ prepared.description }} · {{ prepared.mode }}</text>
        <text class="detail">服务费：¥{{ moneyText(prepared.serviceFee) }}</text>
        <text class="detail">商品预算：¥{{ moneyText(prepared.shoppingBudget) }}</text>
        <text class="total">预付总额：¥{{ moneyText(prepared.total) }}</text>
        <text class="hint">确认凭证有效期至：{{ prepared.expiresAt }}</text>
        <text class="warning">{{ prepared.warning }}</text>
        <view class="primary-btn" :class="{ disabled: busy }" @click="confirmDraft">
          明确确认并输入支付密码
        </view>
        <view class="secondary-btn" @click="checkReceipt">查询确认状态</view>
        <text class="hint">请求中断时先查询同一草稿状态，不创建新的交易。</text>
      </view>

      <view v-if="mode === 'route' && (verified.length || unverified.length)" class="result-card">
        <text class="card-title">顺路候选 · 仅为预检结果</text>
        <view v-for="item in verified" :key="'v'+item.taskId" class="task-line">
          <text class="name">{{ item.type }} · {{ item.publicDesc }}</text>
          <text class="detail">{{ item.pickupArea }} → {{ item.deliveryArea }}</text>
          <text class="meta">估算绕路 {{ item.estimatedDetourMinutes }} 分钟 · 匹配分 {{ item.score }}</text>
          <text class="meta">服务报酬 ¥{{ moneyText(item.serviceFee) }}（不含商品预算）</text>
          <text class="hint">{{ item.reason }}</text>
          <view class="primary-btn" @click="chooseTask(item)">选择此任务</view>
        </view>
        <view v-for="item in unverified" :key="'u'+item.taskId" class="task-line">
          <text class="name">{{ item.type }} · {{ item.publicDesc }}</text>
          <text class="detail">{{ item.pickupArea }} → {{ item.deliveryArea }}</text>
          <text class="warning">{{ formatCandidateDescription(item) }}</text>
          <text class="hint">报酬 ¥{{ moneyText(item.serviceFee) }} · 未计算匹配分</text>
          <view class="secondary-btn" @click="chooseTask(item)">已知风险，查看接单确认</view>
        </view>
      </view>

      <view v-if="mode === 'route' && selectedTask" class="result-card">
        <text class="card-title">最后确认接单</text>
        <text class="detail">任务 #{{ selectedTask.taskId }} · {{ selectedTask.publicDesc }}</text>
        <text class="warning">此处尚未抢单；任务可能被其他同学先接走。</text>
        <view class="primary-btn" :class="{ disabled: busy }" @click="acceptTask">明确确认，由 Java 执行抢单</view>
      </view>

      <view v-if="limitations.length" class="limits">
        <text v-for="(line, i) in limitations" :key="i" class="hint">· {{ line }}</text>
      </view>
      <view id="bottom-of-agent" class="end-space"></view>
    </scroll-view>

    <view class="composer safe-area-bottom">
      <input class="text-input" v-model="message" :disabled="busy || Boolean(prepared)"
        :placeholder="mode === 'delegation' ? '如：帮我打印三十页送到教学楼…' : '如：明天下午五点从图书馆回宿舍…'"
        maxlength="1000" confirm-type="send" @confirm="send" />
      <view class="send-btn" :class="{ disabled: busy || Boolean(prepared) }" @click="send">发送</view>
    </view>
    <PayPasswordDialog />
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { agentApi, addressApi } from '@/api'
import PayPasswordDialog from '@/components/pay-password-dialog/pay-password-dialog.vue'
import { promptPayPassword } from '@/utils/pay-password'
import {
  validateAgentReply, moneyText, validFeeString, quoteIsReady,
  receiptIsSuccessful, formatCandidateDescription, isVerifiedMatch,
} from '@/utils/agent-contract.mjs'
import { showToast } from '@/utils/toast'

const mode = ref('delegation')
const session = ref(null)
const message = ref('')
const messages = ref([])
const addresses = ref([])
const selectedAddress = ref(null)
const fee = reactive({ tip: '', delivery: '0', goods: '0' })
const status = ref('idle')
const offers = ref([])
const verified = ref([])
const unverified = ref([])
const limitations = ref([])
const draftPrefill = ref(null)
const prepared = ref(null) // token stays in memory only; never in localStorage or Python
const selectedTask = ref(null)
const busy = ref(false)
const scrollAnchor = ref('bottom-of-agent')

onLoad(options => { if (options && options.mode === 'route') mode.value = 'route' })
onShow(() => { loadAddresses() })

function goBack() { uni.navigateBack() }
function goAddress() { uni.navigateTo({ url: '/pages-address/address-list/address-list' }) }

async function loadAddresses() {
  try {
    addresses.value = (await addressApi.getAddressList()) || []
    if (selectedAddress.value && !addresses.value.some(a => a.id === selectedAddress.value.id)) {
      selectedAddress.value = null
    }
  } catch (e) {
    addresses.value = []
  }
}
function pickAddress(e) {
  selectedAddress.value = addresses.value[Number(e.detail.value)] || null
  if (selectedAddress.value) {
    sendText('地址簿ID ' + selectedAddress.value.id)
  }
}

function resetFlow() {
  session.value = null
  messages.value = []
  status.value = 'idle'
  offers.value = []
  verified.value = []
  unverified.value = []
  limitations.value = []
  draftPrefill.value = null
  prepared.value = null
  selectedTask.value = null
}
function switchMode(next) {
  if (busy.value || next === mode.value) return
  if (prepared.value) {
    showToast('请先查询或完成当前草稿；切换模式不会自动取消已生成的草稿')
    return
  }
  mode.value = next
  resetFlow()
}

async function sendText(text) {
  if (busy.value || prepared.value || !text || !text.trim()) return
  if (/支付密码|paypassword|pay_password/i.test(text)) {
    showToast('支付密码只能在 Java 确认弹窗中输入')
    return
  }
  busy.value = true
  messages.value.push({ role: 'user', text: text.trim() })
  message.value = ''
  try {
    const fn = mode.value === 'route' ? agentApi.talkRoute : agentApi.talkDelegation
    const data = validateAgentReply(await fn({ message: text.trim(),
      ...(session.value ? { session_id: session.value } : {}) }))
    applyReply(data)
  } catch (e) {
    appendAgent('暂时无法获取智能建议，未执行任何交易。可以稍后重试。')
  } finally {
    busy.value = false
  }
}
function send() { return sendText(message.value) }
function appendAgent(text) { messages.value.push({ role: 'agent', text: text || '请继续补充信息。' }) }
function applyReply(data) {
  session.value = data.session_id
  status.value = data.status
  offers.value = Array.isArray(data.offers) ? data.offers : []
  verified.value = Array.isArray(data.verified) ? data.verified : []
  unverified.value = Array.isArray(data.unverified) ? data.unverified : []
  limitations.value = Array.isArray(data.limitations) ? data.limitations : []
  draftPrefill.value = data.status === 'ready_for_java_preview' ? data.preview_input : null
  selectedTask.value = data.status === 'ready_for_java_accept' ? data.selected : null
  appendAgent(data.answer || (data.status === 'choose_mode' ? '请选择服务或公开任务' : '请从列表选择任务'))
}
async function sendFeeDetails() {
  if (busy.value || mode.value !== 'delegation') return
  if (![fee.tip, fee.delivery, fee.goods].every(validFeeString)) {
    showToast('金额须为有效非负数字，最多两位小数')
    return
  }
  return sendText('小费' + fee.tip + '元，配送费' + fee.delivery
    + '元，商品费' + fee.goods + '元')
}

async function chooseMode(choice, offerId) {
  if (busy.value || status.value !== 'choose_mode' || !session.value) return
  busy.value = true
  try {
    const reply = validateAgentReply(await agentApi.talkDelegation({
      session_id: session.value, choice,
      ...(choice === 'offer' ? { offer_id: Number(offerId) } : {}),
    }))
    applyReply(reply)
  } catch (e) {
    appendAgent('选项提交失败，请重新查询或补充信息。未创建任何交易。')
  } finally { busy.value = false }
}
function selectOffer(offer) { return chooseMode('offer', offer.offerId) }

async function previewDraft() {
  if (busy.value || !draftPrefill.value || prepared.value) return
  busy.value = true
  try {
    const result = await agentApi.previewDelegation(draftPrefill.value)
    if (!quoteIsReady(result)) throw new Error('invalid Java quote')
    prepared.value = result
    draftPrefill.value = null
    appendAgent('Java 已核验草稿金额。请阅读全部费用和有效期后决定是否确认。尚未扣款。')
  } catch (e) {
    appendAgent('Java 未能生成合法草稿。请检查校区、地址、费用及服务是否下架。')
  } finally { busy.value = false }
}

function askModal(title, content) {
  return new Promise(resolve => uni.showModal({
    title, content, confirmText: '继续确认', success: res => resolve(Boolean(res.confirm)),
    fail: () => resolve(false),
  }))
}
async function checkReceipt() {
  if (!prepared.value || busy.value) return
  busy.value = true
  try {
    const receipt = await agentApi.getDelegationReceipt(prepared.value.operationId)
    if (receiptIsSuccessful(receipt)) {
      prepared.value = null
      appendAgent('操作已由 Java 确认成功。任务 ID：' + receipt.taskId
        + (receipt.orderId ? '，订单 ID：' + receipt.orderId : ''))
    } else {
      appendAgent(receipt?.status === 'PREPARED'
        ? 'Java 显示此草稿尚未成交，可使用同一个草稿再次确认（过期则不能提交）。'
        : '当前交易状态尚不能确认，请勿新建草稿重复支付。')
    }
  } catch (e) {
    appendAgent('暂时无法核实交易状态。请稍后用当前草稿重查，避免重复发布。')
  } finally { busy.value = false }
}
async function confirmDraft() {
  if (busy.value || !quoteIsReady(prepared.value)) return
  const approved = await askModal('明确确认模拟预付',
    '业务：' + prepared.value.description + '\n总额 ¥'
    + moneyText(prepared.value.total) + '\n确认后才由 Java 校验支付密码和执行原业务。')
  if (!approved || busy.value) return
  const password = await promptPayPassword('模拟预付 ¥' + moneyText(prepared.value.total))
  if (!password || busy.value) return
  busy.value = true
  try {
    // Password is sent ONLY in authenticated Java HTTP request, not to Agent or local storage.
    const receipt = await agentApi.confirmDelegation(
      prepared.value.operationId, prepared.value.confirmToken, password)
    if (receiptIsSuccessful(receipt)) {
      appendAgent('委托成功！任务 ID：' + receipt.taskId
        + (receipt.orderId ? '，订单 ID：' + receipt.orderId : ''))
      prepared.value = null
    } else {
      appendAgent('确认结果未知，先查询 Java 操作状态，不要创建新草稿。')
    }
  } catch (e) {
    appendAgent('确认请求未得到可靠成功响应，可能已提交。请查询同一个草稿的状态。')
  } finally { busy.value = false }
}

async function chooseTask(item) {
  if (busy.value || status.value !== 'choose_task' || !session.value) return
  let acknowledge = false
  if (!isVerifiedMatch(item)) {
    acknowledge = await askModal('路线没有核实',
      '该任务没有可信的绕路时间和评分，也不保证重量适合携带。要继续选择吗？')
    if (!acknowledge) return
  }
  busy.value = true
  try {
    const data = validateAgentReply(await agentApi.talkRoute({
      session_id: session.value, task_id: Number(item.taskId),
      acknowledge_unverified: acknowledge,
    }))
    applyReply(data)
  } catch (e) {
    appendAgent('任务选择失败，请刷新候选列表；尚未接单。')
  } finally { busy.value = false }
}
async function acceptTask() {
  if (busy.value || !selectedTask.value) return
  const task = selectedTask.value
  const ok = await askModal('最后确认抢单',
    '任务 #' + task.taskId + '\n' + task.pickupArea + ' → ' + task.deliveryArea
    + '\n以 Java 实时资格、状态和库存判断为准。')
  if (!ok || busy.value) return
  busy.value = true
  try {
    const res = await agentApi.acceptSuggestedTask(task.taskId)
    if (!Number.isInteger(Number(res?.orderId)) || Number(res.orderId) <= 0)
      throw new Error('No authoritative Java order ID')
    selectedTask.value = null
    appendAgent('接单成功！订单 ID：' + res.orderId)
    uni.navigateTo({ url: '/pages-order/order-delivering/order-delivering?taskId='
      + task.taskId + '&role=runner' })
  } catch (e) {
    selectedTask.value = null
    verified.value = []
    unverified.value = []
    status.value = 'stale'
    appendAgent('任务可能已被抢走、失效或接单资格变化。请发起新的推荐查询；未确认成功订单。')
  } finally { busy.value = false }
}
</script>

<style scoped>
.ai-page { width: 100%; height: 100vh; display:flex; flex-direction:column; background:var(--background) }
.mode-tabs { display:flex; margin:14rpx 28rpx; padding:8rpx; border-radius:24rpx; background:var(--surface) }
.mode-tab { flex:1; text-align:center; font-size:28rpx; padding:18rpx; color:var(--text-secondary) }
.mode-tab.active { border-radius:18rpx; background:var(--surface-raised); color:var(--primary); font-weight:700 }
.conversation { flex:1; min-height:0; width:100%; box-sizing:border-box; padding:0 28rpx }
.intro { display:flex; flex-direction:column; padding:20rpx 8rpx 30rpx; gap:8rpx }
.intro-title { font-weight:700; font-size:35rpx; color:var(--text-primary) }
.bubble { display:flex; margin-bottom:22rpx; padding:20rpx 24rpx; border-radius:26rpx; font-size:27rpx; line-height:1.55; max-width:90%; overflow-wrap:anywhere }
.bubble.agent { background:var(--surface-raised); color:var(--text-primary); align-self:flex-start }
.bubble.user { background:var(--primary-container); color:var(--on-primary-container); margin-left:auto }
.guided, .result-card { margin:22rpx 0; border-radius:28rpx; background:var(--surface-raised); padding:28rpx; display:flex; flex-direction:column; gap:14rpx }
.card-title { font-size:30rpx; font-weight:700; color:var(--text-primary) }
.label { font-size:25rpx; font-weight:600; color:var(--text-secondary); margin-top:10rpx }
.select-field { padding:20rpx; background:var(--surface); border-radius:18rpx; color:var(--text-primary); font-size:26rpx }
.link { color:var(--primary); font-size:24rpx }
.fee-inputs { display:flex; gap:12rpx }
.fee-field { flex:1; min-width:0 }
.fee-field input { margin-top:8rpx; background:var(--surface); border-radius:16rpx; padding:12rpx; font-size:27rpx }
.detail { font-size:26rpx; color:var(--text-primary); line-height:1.5 }
.meta, .hint, .note { font-size:23rpx; color:var(--text-secondary); line-height:1.5 }
.name { font-size:27rpx; font-weight:650; color:var(--text-primary) }
.total { font-size:32rpx; color:var(--primary); font-weight:750 }
.warning { font-size:24rpx; color:#A15B00; line-height:1.5 }
.primary-btn, .secondary-btn { border-radius:20rpx; padding:20rpx; text-align:center; font-weight:600; font-size:26rpx }
.primary-btn { background:var(--primary); color:#fff }
.secondary-btn { background:var(--surface); color:var(--primary); border:1px solid var(--outline-light) }
.disabled { opacity:.5; pointer-events:none }
.offer-line, .task-line { display:flex; flex-direction:column; gap:12rpx; border-top:1px solid var(--outline-light); padding:20rpx 0 }
.limits { display:flex; flex-direction:column; gap:8rpx; padding:10rpx }
.end-space { height:70rpx }
.composer { border-top:1px solid var(--outline-light); background:var(--surface-raised); padding:16rpx 24rpx 28rpx; display:flex; align-items:center; gap:12rpx }
.text-input { flex:1; background:var(--surface); padding:22rpx; border-radius:26rpx; font-size:26rpx; min-width:0 }
.send-btn { background:var(--primary); color:#fff; padding:22rpx 26rpx; border-radius:24rpx; font-size:26rpx }
</style>
