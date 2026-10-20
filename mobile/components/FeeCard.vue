<template>
  <view class="form-card form-card--pay">
    <view class="card-title">费用明细</view>
    <view class="fee-row">
      <text class="fee-label">{{ feeLabel }}</text>
      <text class="fee-value">¥ {{ baseFee.toFixed(2) }}</text>
    </view>
    <template v-if="taskType === 4">
      <view class="form-label" style="margin-top:18rpx">预估商品费</view>
      <view class="custom-tip-row">
        <text class="custom-tip-unit">¥</text>
        <input class="custom-tip-input" name="digit" v-model.number="productFeeModel" placeholder="输入预估商品费用" />
      </view>
      <view class="info-hint" style="margin-top:12rpx">
        <iconpark-icon name="info" size="18" color="#FF6B4A" />
        <text>配送员垫付商品费，送达后当面结算</text>
      </view>
      <view class="fee-divider"></view>
    </template>
    <view class="fee-divider" v-if="taskType !== 4"></view>
    <view class="form-label">小费（提高接单率）</view>
    <view class="chip-row tip-chips">
      <view class="chip" :class="{ 'chip--active': reward === 2 && !showCustomTip }" @click="$emit('update:reward', 2)">¥2</view>
      <view class="chip" :class="{ 'chip--active': reward === 5 && !showCustomTip }" @click="$emit('update:reward', 5)">¥5</view>
      <view class="chip" :class="{ 'chip--active': reward === 10 && !showCustomTip }" @click="$emit('update:reward', 10)">¥10</view>
      <view class="chip" :class="{ 'chip--active': showCustomTip }" @click="$emit('toggleCustomTip')">自定义</view>
    </view>
    <view class="custom-tip-row" v-if="showCustomTip">
      <text class="custom-tip-unit">¥</text>
      <input class="custom-tip-input" name="digit" v-model.number="customTipModel" placeholder="输入小费金额" />
    </view>
    <view class="fee-divider"></view>
    <view class="fee-row fee-row--total">
      <text class="fee-label">合计支付</text>
      <text class="fee-total">¥ {{ totalReward.toFixed(2) }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  taskType: { type: Number, required: true },
  subType: { type: [Number, String], default: null },
  baseFee: { type: Number, required: true },
  totalReward: { type: Number, required: true },
  reward: { type: Number, default: 0 },
  customTip: { type: Number, default: 0 },
  showCustomTip: { type: Boolean, default: false },
  estimatedProductFee: { type: Number, default: 0 },
})

const emit = defineEmits(['update:reward', 'update:customTip', 'update:estimatedProductFee', 'toggleCustomTip'])

const productFeeModel = computed({
  get: () => props.estimatedProductFee,
  set: (val) => emit('update:estimatedProductFee', isNaN(val) ? 0 : val)
})
const customTipModel = computed({
  get: () => props.customTip,
  set: (val) => emit('update:customTip', isNaN(val) ? 0 : val)
})

const feeLabel = computed(() =>
  props.taskType === 3 && props.subType === 35 ? '基础服务费' : '基础配送费')
</script>

<style scoped>
.form-card{background:var(--surface-raised);border-radius:var(--radius-lg);padding:28rpx;margin-bottom:20rpx;box-shadow:var(--shadow-sm);border:1rpx solid var(--outline-light)}
.form-card--pay{border:2rpx solid var(--primary-container)}
.form-label{font-size:24rpx;font-weight:500;color:var(--text-secondary);margin-bottom:10rpx;margin-top:18rpx}
.form-label:first-child{margin-top:0}
.fee-row{display:flex;align-items:center;justify-content:space-between;padding:8rpx 0}
.fee-row--total{margin-top:8rpx}
.fee-label{font-size:28rpx;color:var(--text-primary)}
.fee-value{font-size:28rpx;font-weight:600;color:var(--text-secondary)}
.fee-total{font-size:38rpx;font-weight:700;color:var(--primary)}
.fee-divider{height:1rpx;background:rgba(0,0,0,.04);margin:20rpx 0}
.custom-tip-row{display:flex;align-items:center;margin-top:16rpx;background:var(--surface);border-radius:20rpx;padding:14rpx 24rpx}
.custom-tip-unit{font-size:32rpx;font-weight:700;color:var(--primary);margin-right:8rpx}
.custom-tip-input{flex:1;font-size:28rpx;color:var(--text-primary);background:transparent}
.chip-row{display:flex;flex-wrap:wrap;gap:14rpx}
.chip{padding:14rpx 28rpx;border-radius:48rpx;font-size:26rpx;font-weight:500;color:var(--text-secondary);background:var(--surface);text-align:center}
.chip--active{background:var(--primary);color:#fff;font-weight:600}
.info-hint{display:flex;align-items:center;gap:10rpx;padding:16rpx;background:var(--primary-container);border-radius:12rpx}
.info-hint text{font-size:24rpx;color:var(--primary)}
</style>
