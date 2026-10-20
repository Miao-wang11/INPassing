<template>
  <view class="form-card">
    <view class="card-title">接单限制</view>
    <view class="chip-row">
      <view class="chip" :class="{ 'chip--active': modelValue === undefined }" @click="$emit('update:modelValue', undefined)">不限</view>
      <view class="chip" :class="{ 'chip--active': modelValue === '男' }" @click="$emit('update:modelValue', '男')">仅男生</view>
      <view class="chip" :class="{ 'chip--active': modelValue === '女' }" @click="$emit('update:modelValue', '女')">仅女生</view>
    </view>
  </view>
</template>

<script setup>
defineProps({ modelValue: { type: [String, undefined], default: undefined } })
defineEmits(['update:modelValue'])
</script>

<style scoped>
.form-card{background:var(--surface-raised);border-radius:var(--radius-lg);padding:28rpx;margin-bottom:20rpx;box-shadow:var(--shadow-sm);border:1rpx solid var(--outline-light)}
.chip-row{display:flex;flex-wrap:wrap;gap:14rpx}
.chip{padding:14rpx 28rpx;border-radius:48rpx;font-size:26rpx;font-weight:500;color:var(--text-secondary);background:var(--surface);text-align:center}
.chip--active{background:var(--primary);color:#fff;font-weight:600}
</style>
