<template>
  <view class="screen">
    <uni-nav-bar title="订单售后" statusBar leftIcon="left" @clickLeft="back" backgroundColor="#FAFAF8"/>
    <scroll-view scroll-y class="content">
      <view class="intro">
        <text class="title">交易争议处理</text>
        <text class="hint">已送达待确认时可发起争议，管理员处理期间暂停自动结算。</text>
      </view>
      <view v-if="orderId && !created" class="card">
        <text class="caption">订单 #{{ orderId }} · 申请售后</text>
        <textarea class="input textarea" v-model="reason" maxlength="500"
          placeholder="请详细描述问题、时间以及处理诉求，至少填写一个字符" />
        <text class="hint">可上传已有图片的 HTTPS 地址，最多五个（每行一个）；请勿粘贴身份证、账号密码等敏感信息。</text>
        <textarea class="input textarea" v-model="links" placeholder="https://..."/>
        <view class="btn" :class="{disabled:busy}" @click="submit">
          {{ busy ? '提交中…' : '提交争议并冻结待确认结算' }}
        </view>
      </view>
      <view class="card">
        <view class="head"><text class="caption">我发起的售后</text><text class="refresh" @click="load">刷新</text></view>
        <view v-if="!cases.length" class="hint">暂无售后申请</view>
        <view v-for="item in cases" :key="item.id" class="case" @click="showCase(item)">
          <view class="head"><text>订单 #{{ item.orderId }}</text><text class="status">{{ label(item.status) }}</text></view>
          <text class="hint">{{ item.reason }}</text>
          <text class="hint">{{ item.createdAt }}</text>
          <text v-if="item.resolutionNote" class="hint">处理结果：{{ item.resolutionNote }}</text>
        </view>
      </view>
      <view v-if="selected" class="card">
        <text class="caption">售后 #{{ selected.id }} 操作记录</text>
        <view v-for="ev in events" :key="ev.id" class="case">
          <text>{{ ev.eventType }} · {{ ev.actorType }}</text>
          <text class="hint">{{ ev.detail }}</text>
          <text class="hint">{{ ev.createdAt }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>
<script setup>
import {ref} from 'vue'
import {onLoad,onShow} from '@dcloudio/uni-app'
import {disputeApi} from '@/api'
const orderId=ref(''),reason=ref(''),links=ref(''),busy=ref(false),created=ref(false)
const cases=ref([]),selected=ref(null),events=ref([])
const back=()=>uni.navigateBack()
const label=s=>s===1?'仲裁中·结算暂停':s===2?'已驳回·恢复正常确认':'已模拟全额退款'
onLoad(opt=>{orderId.value=opt?.orderId||''})
onShow(()=>load())
async function load(){
  try{cases.value=await disputeApi.myDisputes()||[]}catch(e){cases.value=[]}
}
async function submit(){
  if(busy.value)return
  const text=reason.value.trim()
  const evidence=links.value.split('\n').map(s=>s.trim()).filter(Boolean)
  if(!text||text.length>500||evidence.length>5){
    uni.showToast({title:'请填写原因，凭证不能超过5张',icon:'none'});return
  }
  busy.value=true
  try {
    await disputeApi.openDispute(orderId.value,{reason:text,evidenceUrls:evidence})
    created.value=true;reason.value='';links.value=''
    await load()
    uni.showToast({title:'售后申请已提交',icon:'success'})
  }catch(e){}finally{busy.value=false}
}
async function showCase(item){
  selected.value=item
  try{events.value=await disputeApi.disputeEvents(item.id)||[]}catch(e){events.value=[]}
}
</script>
<style scoped>
.screen{height:100vh;background:#FAFAF8;display:flex;flex-direction:column;color:#222}
.content{flex:1;min-height:0;box-sizing:border-box;padding:25rpx}
.intro{display:flex;flex-direction:column;gap:12rpx;margin:20rpx 0}
.title{font-size:36rpx;font-weight:700}.caption{font-size:31rpx;font-weight:600}
.hint{font-size:24rpx;color:#777;line-height:1.6;word-break:break-all}
.card{background:#fff;margin-bottom:22rpx;border-radius:20rpx;padding:26rpx;display:flex;flex-direction:column;gap:22rpx}
.input{background:#f6f6f6;border-radius:12rpx;padding:16rpx;box-sizing:border-box;width:100%}
.textarea{min-height:180rpx;font-size:27rpx}
.btn{background:#FF6B4A;color:white;border-radius:12rpx;padding:24rpx;text-align:center}
.disabled{opacity:.45}.head{display:flex;justify-content:space-between;gap:16rpx}
.refresh,.status{color:#FF6B4A;font-size:25rpx}.case{display:flex;flex-direction:column;gap:9rpx;padding:14rpx 0;border-bottom:1px solid #eee}
</style>
