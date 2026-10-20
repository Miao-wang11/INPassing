<template>
  <view class="screen">
    <uni-nav-bar title="服务橱窗" leftIcon="left" @clickLeft="goBack" statusBar backgroundColor="#FAFAF8"/>
    <scroll-view scroll-y class="body">
      <view class="heading">
        <text class="h1">学生互助 · 我能帮你</text>
        <text class="hint">查看同校区学生提供的服务，预约后由指定同学接单</text>
      </view>
      <view class="tabs">
        <view :class="{active:tab==='hall'}" @click="setTab('hall')">服务广场</view>
        <view :class="{active:tab==='mine'}" @click="setTab('mine')">我的服务</view>
      </view>
      <view class="controls">
        <picker :range="categories" @change="pickCategory" v-if="tab==='hall'">
          <view class="picker">{{ selectedCategory || '全部类别' }} ▾</view>
        </picker>
        <view class="primary small" @click="startCreate">+ 发布服务</view>
      </view>
      <view v-if="loading" class="empty">读取服务中…</view>
      <view v-else-if="!offers.length" class="empty">暂无可显示服务</view>
      <view v-for="item in offers" :key="item.id" class="offer">
        <view class="offer-top">
          <text class="name">{{ item.title }}</text>
          <text class="price">¥{{ Number(item.servicePrice).toFixed(2) }}</text>
        </view>
        <text class="meta">{{ item.category }} · {{ item.nickname }} · {{ item.subType || '通用服务' }}</text>
        <text class="desc">{{ item.description }}</text>
        <text class="meta">服务时间：{{ pretty(item.availableFrom) }} 至 {{ pretty(item.availableUntil) }}</text>
        <view class="offer-actions">
          <template v-if="tab==='mine'">
            <text class="link" @click="startEdit(item)">编辑</text>
            <text class="link" @click="changeStatus(item)">{{ item.status===1 ? '下架' : '重新上架' }}</text>
          </template>
          <view v-else class="primary small" @click="startBook(item)">委托这位同学</view>
        </view>
      </view>

      <view v-if="tab==='hall' && page < maxPage" class="load" @click="loadMore">加载更多</view>

      <view v-if="editing" class="sheet">
        <view class="sheet-title">{{ editingId ? '编辑服务' : '发布我能提供的服务' }}</view>
        <view class="field-label">服务类别</view>
        <picker :range="categories.slice(1)" @change="form.category=categories[Number($event.detail.value)+1]">
          <view class="field">{{ form.category }} ▾</view>
        </picker>
        <view class="field-label">标题</view>
        <input class="field" maxlength="80" v-model="form.title" placeholder="例如今晚可代取快递"/>
        <view class="field-label">服务介绍（请勿填写取件码、手机号）</view>
        <textarea class="field textarea" maxlength="500" v-model="form.description" placeholder="服务地点、范围、要求"/>
        <view class="field-label">服务报酬（元）</view>
        <input class="field" type="digit" v-model="form.servicePrice" placeholder="例如 3.50"/>
        <view class="field-label">服务开始</view>
        <view class="time-row">
          <picker mode="date" :value="form.fromDate" @change="form.fromDate=$event.detail.value">
            <view class="field">{{ form.fromDate }}</view>
          </picker>
          <picker mode="time" :value="form.fromTime" @change="form.fromTime=$event.detail.value">
            <view class="field">{{ form.fromTime }}</view>
          </picker>
        </view>
        <view class="field-label">服务结束</view>
        <view class="time-row">
          <picker mode="date" :value="form.untilDate" @change="form.untilDate=$event.detail.value">
            <view class="field">{{ form.untilDate }}</view>
          </picker>
          <picker mode="time" :value="form.untilTime" @change="form.untilTime=$event.detail.value">
            <view class="field">{{ form.untilTime }}</view>
          </picker>
        </view>
        <view class="actions">
          <view class="secondary" @click="editing=false">取消</view>
          <view class="primary" :class="{disabled:saving}" @click="saveOffer">{{ saving ? '保存中' : '保存服务' }}</view>
        </view>
      </view>

      <view v-if="bookingOffer" class="sheet">
        <view class="sheet-title">预约 · {{ bookingOffer.title }}</view>
        <text class="desc">指定服务者：{{ bookingOffer.nickname }}。服务费 ¥{{ Number(bookingOffer.servicePrice).toFixed(2) }}，平台使用模拟余额预付。</text>
        <view class="field-label">具体取货/办事地点</view>
        <input class="field" maxlength="255" v-model="booking.pickupAddress" placeholder="请填写详细地点"/>
        <view class="field-label">送达地址</view>
        <picker :range="addresses" range-key="detail" @change="pickAddress">
          <view class="field">{{ addressLabel }}</view>
        </picker>
        <text class="link" @click="goAddress">没有地址？先添加收货地址</text>
        <view class="field-label">预估代购物品预算（元，无则填 0）</view>
        <input class="field" type="digit" v-model="booking.shoppingBudget"/>
        <text class="hint">当前按预估预算模拟转付；实际采购价格核验和差额处理尚未开发。</text>
        <view class="field-label">私密备注</view>
        <textarea class="field textarea" maxlength="256" v-model="booking.privateNote" placeholder="仅订单参与方使用的信息"/>
        <view class="field-label">支付密码</view>
        <input class="field" type="password" v-model="booking.payPassword" placeholder="输入模拟支付密码"/>
        <view class="actions">
          <view class="secondary" @click="bookingOffer=null">取消</view>
          <view class="primary" :class="{disabled:saving}" @click="confirmBooking">{{ saving ? '委托中' : '确认委托并预付' }}</view>
        </view>
      </view>
      <view style="height:80rpx"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { serviceOfferApi, addressApi } from '@/api'

const categories = ['全部类别','代取快递','代拿餐食','校内代办','代购物品','通用代办']
const selectedCategory = ref('')
const tab = ref('hall')
const loading = ref(false), saving = ref(false)
const offers=ref([]), page=ref(1), maxPage=ref(1)
const editing=ref(false), editingId=ref(null), bookingOffer=ref(null)
const addresses=ref([])
const booking=reactive({requestKey:'',deliveryAddressId:null,pickupAddress:'',shoppingBudget:'0.00',privateNote:'',payPassword:''})
const addressLabel=computed(()=> addresses.value.find(a=>a.id===booking.deliveryAddressId)?.detail || '请选择收货地址')

function timeBits(date) {
  const pad=n=>String(n).padStart(2,'0')
  return {date:date.getFullYear()+'-'+pad(date.getMonth()+1)+'-'+pad(date.getDate()),
          time:pad(date.getHours())+':'+pad(date.getMinutes())}
}
const dateStart=timeBits(new Date(Date.now()+60*60*1000))
const dateEnd=timeBits(new Date(Date.now()+3*60*60*1000))
const form=reactive({category:'校内代办', title:'',description:'',servicePrice:'3.00',
  fromDate:dateStart.date,fromTime:dateStart.time,untilDate:dateEnd.date,untilTime:dateEnd.time})
function pretty(s){return String(s||'').replace('T',' ').slice(0,16)}
function goBack(){uni.navigateBack()}
function goAddress(){uni.navigateTo({url:'/pages-address/address-list/address-list'})}
function setTab(t){tab.value=t;load()}
function pickCategory(e){const n=Number(e.detail.value);selectedCategory.value=n===0?'':categories[n];load()}
async function load(more=false){
  loading.value=true
  try{
    if(tab.value==='mine'){
      offers.value=await serviceOfferApi.myOffers() || []
    }else{
      if(!more) page.value=1
      const res=await serviceOfferApi.listOffers({page:page.value,size:10,category:selectedCategory.value||undefined})
      offers.value=more?[...offers.value,...(res.records||[])]:res.records||[]
      maxPage.value=Math.ceil((res.total||0)/10)
    }
  }catch(e){if(!more)offers.value=[]}
  finally{loading.value=false}
}
function loadMore(){if(page.value<maxPage.value){page.value++;load(true)}}
function startCreate(){
  editingId.value=null
  form.category='校内代办';form.title='';form.description='';form.servicePrice='3.00'
  const a=timeBits(new Date(Date.now()+60*60*1000)),b=timeBits(new Date(Date.now()+3*60*60*1000))
  form.fromDate=a.date;form.fromTime=a.time;form.untilDate=b.date;form.untilTime=b.time
  editing.value=true;bookingOffer.value=null
}
function startEdit(item){
  editingId.value=item.id
  form.category=item.category;form.title=item.title;form.description=item.description
  form.servicePrice=String(item.servicePrice)
  const a=String(item.availableFrom).split('T'),b=String(item.availableUntil).split('T')
  form.fromDate=a[0];form.fromTime=(a[1]||'').slice(0,5)
  form.untilDate=b[0];form.untilTime=(b[1]||'').slice(0,5)
  editing.value=true;bookingOffer.value=null
}
async function saveOffer(){
  if(saving.value)return
  if(!form.title.trim()||!form.description.trim()||!form.servicePrice){uni.showToast({title:'请完善服务信息',icon:'none'});return}
  saving.value=true
  try{
    const data={category:form.category,title:form.title.trim(),description:form.description.trim(),
      servicePrice:form.servicePrice, availableFrom:form.fromDate+'T'+form.fromTime+':00',
      availableUntil:form.untilDate+'T'+form.untilTime+':00'}
    if(editingId.value)await serviceOfferApi.editOffer(editingId.value,data)
    else await serviceOfferApi.createOffer(data)
    editing.value=false
    uni.showToast({title:'服务已保存',icon:'success'})
    tab.value='mine';await load()
  }catch(e){}
  finally{saving.value=false}
}
async function changeStatus(item){
  if(saving.value)return
  saving.value=true
  try{
    await serviceOfferApi.setOfferStatus(item.id,item.status===1?0:1)
    await load()
  }catch(e){}finally{saving.value=false}
}
async function startBook(item){
  bookingOffer.value=item;editing.value=false
  booking.requestKey='ss_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,13)
  booking.deliveryAddressId=null;booking.pickupAddress=''
  booking.shoppingBudget='0.00';booking.privateNote='';booking.payPassword=''
  try{addresses.value=await addressApi.getAddressList()||[]}catch(e){addresses.value=[]}
}
function pickAddress(e){const a=addresses.value[Number(e.detail.value)];if(a)booking.deliveryAddressId=a.id}
async function confirmBooking(){
  if(saving.value||!bookingOffer.value)return
  if(!booking.deliveryAddressId||!booking.pickupAddress.trim()||!booking.payPassword){
    uni.showToast({title:'补全地址与支付密码',icon:'none'});return
  }
  saving.value=true
  try{
    const receipt=await serviceOfferApi.bookOffer(bookingOffer.value.id,{...booking})
    bookingOffer.value=null
    uni.showToast({title:'委托创建成功',icon:'success'})
    setTimeout(()=>uni.switchTab({url:'/pages/orders/orders'}),500)
  }catch(e){}finally{saving.value=false}
}
onShow(()=>{load();if(bookingOffer.value)addressApi.getAddressList().then(a=>addresses.value=a||[]).catch(()=>{})})
</script>

<style scoped>
.screen{height:100vh;background:#FAFAF8;display:flex;flex-direction:column;color:#242424}
.body{flex:1;min-height:0;padding:22rpx 28rpx;box-sizing:border-box}
.heading{display:flex;flex-direction:column;gap:8rpx;margin-bottom:24rpx}
.h1{font-size:39rpx;font-weight:700}.hint{font-size:23rpx;color:#858585;line-height:1.6}
.tabs{display:flex;gap:40rpx;padding:20rpx 0;border-bottom:1px solid #eee}
.tabs view{padding:12rpx 0;color:#777}.tabs .active{color:#FF6B4A;font-weight:700;border-bottom:4rpx solid #FF6B4A}
.controls,.offer-top,.offer-actions,.actions,.time-row{display:flex;justify-content:space-between;align-items:center;gap:16rpx}
.controls{margin:24rpx 0}.picker,.field{background:#f6f6f6;border-radius:12rpx;padding:18rpx;font-size:27rpx}
.offer,.sheet{padding:26rpx;margin-bottom:20rpx;border-radius:20rpx;background:white;display:flex;flex-direction:column;gap:16rpx;box-shadow:0 2rpx 10rpx #00000008}
.name{font-size:31rpx;font-weight:600}.price{font-size:34rpx;color:#FF6B4A;font-weight:700}
.meta{font-size:23rpx;color:#888}.desc{font-size:26rpx;color:#555;line-height:1.6}
.offer-actions{justify-content:flex-end}.primary,.secondary{padding:20rpx 26rpx;border-radius:13rpx;text-align:center}
.primary{background:#FF6B4A;color:white}.secondary{background:#eeeeed}.small{font-size:24rpx;padding:14rpx 22rpx}
.link{color:#FF6B4A;font-size:25rpx;padding:12rpx}.disabled{opacity:.45}.sheet{border:2rpx solid #FF6B4A;margin-top:28rpx}
.sheet-title{font-size:32rpx;font-weight:700}.field-label{font-size:24rpx;margin-top:8rpx}
.textarea{width:auto;min-height:125rpx}.time-row{justify-content:flex-start}
.actions{justify-content:flex-end;margin-top:20rpx}.empty,.load{text-align:center;color:#888;padding:45rpx}
</style>
