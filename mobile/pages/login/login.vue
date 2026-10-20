<template>
  <view class="page">
    <view v-if="checking" class="checking-screen">
      <text class="checking-text">加载中…</text>
    </view>

    <template v-else>
      <!-- 品牌区 -->
      <view class="brand-section">
        <image class="brand-logo animate-bounce-in" src="/static/icons/logo.png" mode="aspectFill" />
        <text class="brand-title animate-fade-up delay-2">小i跑腿</text>
        <text class="brand-subtitle animate-fade-up delay-3">助力您的"校园最后一公里"</text>
      </view>

      <!-- 微信一键登录 -->
      <view class="wechat-area animate-scale-pop delay-4">
        <view class="wechat-btn" @click="onWechatLogin">
          <uni-icons type="weixin" size="32" color="#fff"></uni-icons>
          <text class="wechat-btn-text">{{ submitting ? '登录中…' : '微信一键登录' }}</text>
        </view>
        <text class="wechat-hint">首次登录将自动为您创建账号</text>
      </view>

      <!-- 其他登录方式 -->
      <view class="other-section animate-fade-up delay-5">
        <view class="divider-row">
          <view class="divider-line"></view>
          <text class="divider-text">其他登录方式</text>
          <view class="divider-line"></view>
        </view>
        <view class="account-link" @click="onAccountLogin">
          <iconpark-icon name="mail-account" size="20" color="#8F8D88" />
          <text class="account-link-text">账户密码 / 验证码登录</text>
          <iconpark-icon name="right" size="14" color="#C0BEB8" />
        </view>
      </view>

      <view class="agreement-text">
        <text>登录即表示同意</text>
        <text class="agreement-link">《用户协议》</text>
        <text>和</text>
        <text class="agreement-link">《隐私政策》</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useStore } from '@/store/index.js'
import { getToken } from '@/utils/request'

const store = useStore()

const checking = ref(true)
const submitting = ref(false)

onMounted(async () => {
  const token = getToken()
  if (!token) {
    checking.value = false
    return
  }
  store.token = token
  try {
    await store.fetchUserInfo()
    uni.reLaunch({ url: '/pages/index/index' })
  } catch (e) {
    store.clearLogin()
    checking.value = false
  }
})

async function onWechatLogin() {
  submitting.value = true
  try {
    const loginRes = await new Promise((resolve, reject) => {
      // #ifdef MP-WEIXIN
      uni.login({ provider: 'weixin', success: resolve, fail: reject })
      // #endif
      // #ifndef MP-WEIXIN
      reject(new Error('非微信环境'))
      // #endif
    })
    await store.wechatLogin(loginRes.code)
    await store.fetchUserInfo()
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 600)
  } catch (e) {
    const msg = e?.message || '微信登录失败'
    uni.showToast({ title: msg, icon: 'none', duration: 3000 })
  }
  submitting.value = false
}

function onAccountLogin() {
  uni.navigateTo({ url: '/pages/account-login/account-login' })
}
</script>

<style scoped>
.page { width: 100%; min-height: 100vh; background: var(--background); display: flex; flex-direction: column; align-items: center; padding: 0 48rpx; box-sizing: border-box; }

.checking-screen { width: 100%; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
.checking-text { font-size: 28rpx; color: var(--outline); }

/* 品牌区 */
.brand-section { display: flex; flex-direction: column; align-items: center; padding-top: 160rpx; padding-bottom: 80rpx; }
.brand-logo { width: 180rpx; height: 180rpx; border-radius: 50%; margin-bottom: 28rpx; box-shadow: var(--shadow-md); }
.brand-title { font-size: 52rpx; font-weight: 700; color: var(--text-primary); letter-spacing: 2rpx; }
.brand-subtitle { font-size: 28rpx; color: var(--text-secondary); margin-top: 10rpx; }

/* 微信登录 */
.wechat-area { width: 100%; display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.wechat-btn { width: 100%; height: 104rpx; background: #07c160; border-radius: 52rpx; display: flex; align-items: center; justify-content: center; gap: 16rpx; box-shadow: 0 8rpx 24rpx rgba(7,193,96,0.35); }
.wechat-btn:active { background: #06ad56; transform: scale(0.98); }
.wechat-btn-text { font-size: 34rpx; font-weight: 600; color: #fff; }
.wechat-hint { font-size: 24rpx; color: var(--text-tertiary); }

/* 其他登录方式 */
.other-section { width: 100%; margin-top: 80rpx; }
.divider-row { display: flex; align-items: center; gap: 20rpx; margin-bottom: 28rpx; }
.divider-line { flex: 1; height: 1rpx; background: var(--outline-variant); }
.divider-text { font-size: 24rpx; color: var(--text-tertiary); flex-shrink: 0; }
.account-link { display: flex; align-items: center; justify-content: center; gap: 10rpx; padding: 28rpx; background: var(--surface-raised); border-radius: var(--radius-card); box-shadow: var(--shadow-sm); }
.account-link:active { background: var(--surface); }
.account-link-text { font-size: 28rpx; color: var(--text-secondary); font-weight: 500; }

.agreement-text { margin-top: 64rpx; font-size: 22rpx; color: var(--text-tertiary); text-align: center; padding-bottom: 60rpx; }
.agreement-link { color: var(--primary); }
</style>
