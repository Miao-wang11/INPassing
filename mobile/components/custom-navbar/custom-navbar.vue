<template>
  <view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="nav-left" @click="onLeftClick">
      <slot name="left">
        <view v-if="showBack" class="nav-back">
          <view class="icon-arrow-back"></view>
        </view>
        <view v-else class="nav-avatar">
          <text class="nav-avatar-text">{{ avatarText }}</text>
        </view>
      </slot>
    </view>

    <text class="nav-title">{{ title }}</text>

    <view class="nav-right" @click="onRightClick">
      <slot name="right">
        <view class="nav-action">
          <view v-if="rightIcon === 'bell'" class="icon-bell"></view>
          <view v-else-if="rightIcon === 'help'" class="icon-help"></view>
          <view v-else-if="rightIcon === 'more'" class="icon-more"></view>
        </view>
      </slot>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  showBack: { type: Boolean, default: false },
  avatarText: { type: String, default: '张' },
  rightIcon: { type: String, default: 'bell' }
})

const emit = defineEmits(['leftClick', 'rightClick'])
const statusBarHeight = ref(44)

onMounted(() => {
  // #ifdef MP-WEIXIN
  const systemInfo = uni.getSystemInfoSync()
  statusBarHeight.value = systemInfo.statusBarHeight || 44
  // #endif
})

function onLeftClick() {
  if (props.showBack) {
    uni.navigateBack()
  }
  emit('leftClick')
}

function onRightClick() {
  emit('rightClick')
}
</script>

<style scoped>
.nav-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 96rpx;
  padding: 0 40rpx;
  padding-top: 44px;
  box-sizing: content-box;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.04);
}

.nav-left, .nav-right {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-back, .nav-action {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.nav-back:active, .nav-action:active {
  background: rgba(0, 0, 0, 0.04);
}

.nav-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  overflow: hidden;
  background: var(--primary-fixed);
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-avatar-text {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--primary);
}

.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--primary);
}

/* 返回箭头 */
.icon-arrow-back {
  width: 24rpx;
  height: 24rpx;
  border-left: 3rpx solid var(--on-surface-variant);
  border-bottom: 3rpx solid var(--on-surface-variant);
  transform: rotate(45deg);
}

/* 铃铛图标 */
.icon-bell {
  width: 44rpx;
  height: 44rpx;
  position: relative;
}
.icon-bell::before {
  content: '';
  position: absolute;
  top: 4rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 32rpx;
  height: 28rpx;
  border: 3rpx solid var(--primary);
  border-radius: 16rpx 16rpx 0 0;
  box-sizing: border-box;
}
.icon-bell::after {
  content: '';
  position: absolute;
  bottom: 6rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 40rpx;
  height: 6rpx;
  background: var(--primary);
  border-radius: 3rpx;
}

/* 帮助图标 */
.icon-help {
  width: 36rpx;
  height: 36rpx;
  border: 3rpx solid var(--on-surface-variant);
  border-radius: 50%;
  position: relative;
}
.icon-help::after {
  content: '?';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 22rpx;
  font-weight: 700;
  color: var(--on-surface-variant);
}

/* 更多图标 */
.icon-more {
  width: 36rpx;
  height: 36rpx;
  position: relative;
}
.icon-more::before,
.icon-more::after {
  content: '';
  position: absolute;
  width: 8rpx;
  height: 8rpx;
  background: var(--on-surface-variant);
  border-radius: 50%;
  left: 50%;
  transform: translateX(-50%);
}
.icon-more::before { top: 4rpx; }
.icon-more::after { bottom: 4rpx; }
</style>
