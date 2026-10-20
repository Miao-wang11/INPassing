<template>
  <view class="page">
    <uni-nav-bar backgroundColor="#FAFAF8" :border="false" statusBar>
      <template v-slot:left>
        <view class="nav-avatar-wrap">
          <view class="nav-avatar">
            <image v-if="store.avatarUrl" class="nav-avatar-img" :src="store.avatarUrl" mode="aspectFill" />
            <text v-else class="nav-avatar-text">{{ store.avatarText }}</text>
          </view>
        </view>
      </template>
      <view class="nav-title-wrap"><text class="nav-title-text">消息</text></view>
      <template v-slot:right>
        <view class="nav-btn" @click="onNotify">
          <iconpark-icon name="notification-filled" size="24" color="#FF6B4A" />
        </view>
      </template>
    </uni-nav-bar>

    <scroll-view class="main-scroll" :style="{ height: scrollHeight + 'px' }" scroll-y enhanced :show-scrollbar="false">
      <view class="section-header"><text class="section-label">通知</text></view>

      <uni-card :isShadow="false" :margin="'0'" :padding="'0'" :spacing="'0'" :border="false" class="chat-list-card">
        <view class="notify-entry" @click="goNotifications(1)">
          <view class="notify-entry-icon" :style="notifyIconStyle('sound', '#FF6B4A', '#FFF0ED')" />
          <view class="notify-entry-body">
            <text class="notify-entry-name">系统通知</text>
            <text class="notify-entry-desc">认证、安全与平台动态</text>
          </view>
          <uni-badge v-if="sysUnreadCount > 0" :text="String(sysUnreadCount)" name="error" size="small" />
        </view>
        <view class="notify-entry" @click="goNotifications(2)">
          <view class="notify-entry-icon" :style="notifyIconStyle('delivery', '#E8734A', '#FFF2ED')" />
          <view class="notify-entry-body">
            <text class="notify-entry-name">物流通知</text>
            <text class="notify-entry-desc">配送进度、取件与送达通知</text>
          </view>
          <uni-badge v-if="orderUnreadCount > 0" :text="String(orderUnreadCount)" name="error" size="small" />
        </view>
        <view class="notify-entry" @click="goNotifications(3)">
          <view class="notify-entry-icon" :style="notifyIconStyle('fireworks', '#8B6BAE', '#F6F1FA')" />
          <view class="notify-entry-body">
            <text class="notify-entry-name">活动提醒</text>
            <text class="notify-entry-desc">优惠、活动与平台福利</text>
          </view>
          <uni-badge v-if="activityUnreadCount > 0" :text="String(activityUnreadCount)" name="error" size="small" />
        </view>
      </uni-card>

      <view class="section-header"><text class="section-label">最近消息</text></view>

      <template v-if="!store.isCertified">
        <view class="empty-contacts">
          <iconpark-icon name="locked-filled" size="36" color="#D4D2CC" />
          <text class="empty-text">认证后可使用聊天功能</text>
          <text class="empty-sub" @click="goCertify">去认证</text>
        </view>
      </template>

      <uni-card v-else :isShadow="false" :margin="'0'" :padding="'0'" :spacing="'0'" :border="false" class="chat-list-card">
        <view v-if="displayedContacts.length === 0 && !loadingContacts" class="empty-contacts">
          <text class="empty-text">暂无聊天记录</text>
        </view>

        <view v-for="(contact, index) in displayedContacts" :key="'c-' + contact.userId" class="chat-item animate-fade-up" :style="{ animationDelay: (index * 0.06) + 's' }" @click="onChat(contact)">
          <view class="chat-avatar-wrap">
            <image v-if="contact.avatarUrl" class="chat-avatar-img" :src="normalizeUrl(contact.avatarUrl)" mode="aspectFill" />
            <view v-else class="chat-avatar">{{ contact.initial }}</view>
          </view>
          <view class="chat-body">
            <view class="chat-top">
              <text class="chat-name">{{ contact.nickname }}</text>
              <text class="chat-time" :class="{ 'chat-time--new': contact.unreadCount > 0 }">{{ contact.lastMessageTime }}</text>
            </view>
            <view class="chat-bottom">
              <text class="chat-preview">{{ contact.lastMessage || '暂无消息' }}</text>
              <uni-badge v-if="contact.unreadCount > 0" :text="String(contact.unreadCount)" name="error" size="small" />
            </view>
          </view>
        </view>
      </uni-card>

      <view class="bottom-placeholder"></view>
    </scroll-view>

    <custom-tabbar :selected="3" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useStore } from '@/store/index.js'
import { useChatStore } from '@/store/chat.js'
import { notificationApi } from '@/api'
import CustomTabbar from '@/components/custom-tabbar/custom-tabbar.vue'
import { SERVER_ORIGIN } from '@/utils/config'

const notifySvgs = {
  sound: '<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48"><path d="M24 8L14 18H6V30H14L24 40V8Z" fill="C" stroke="C" stroke-width="2" stroke-linejoin="round"/><path d="M30 16C32.5 18.5 34 22 34 26C34 30 32.5 33.5 30 36" stroke="C" stroke-width="4" stroke-linecap="round"/></svg>',
  delivery: '<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48"><path d="M8 12H32V36H8V12Z" fill="none" stroke="C" stroke-width="4" stroke-linejoin="round"/><path d="M32 18H38L42 26V36H38" fill="none" stroke="C" stroke-width="4" stroke-linejoin="round"/><path d="M12 36C12 39.314 14.686 42 18 42C21.314 42 24 39.314 24 36" fill="none" stroke="C" stroke-width="4"/><path d="M32 36C32 39.314 34.686 42 38 42C41.314 42 44 39.314 44 36" fill="none" stroke="C" stroke-width="4"/></svg>',
  fireworks: '<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48"><path d="M6 42L14.7 17.3L31 34L6 42Z" stroke="C" stroke-width="4" stroke-linejoin="round"/><path d="M23 19L28 14C30.7 11.3 31 9 29 7" stroke="C" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/><path d="M29 25L34 20C37.3 16.7 40.7 16.7 44 20" stroke="C" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/><circle cx="20" cy="5" r="3" fill="C"/><circle cx="42" cy="4" r="3" fill="C"/><circle cx="42" cy="27" r="3" fill="C"/><circle cx="39" cy="36" r="3" fill="C"/></svg>',
};
const notifyIconStyle = (name, color, bg) => {
  const svg = notifySvgs[name] || notifySvgs.sound;
  const uri = 'data:image/svg+xml,' + encodeURIComponent(svg.replace(/"C"/g, '"' + color + '"'));
  return { backgroundColor: bg, backgroundImage: 'url(' + uri + ')', backgroundSize: '36rpx 36rpx', backgroundPosition: 'center', backgroundRepeat: 'no-repeat' };
};

function normalizeUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  if (url.startsWith('/')) return SERVER_ORIGIN + url
  return url
}

const store = useStore()
const chatStore = useChatStore()
const sysInfo = uni.getSystemInfoSync()
const scrollHeight = sysInfo.windowHeight - sysInfo.statusBarHeight - 44

const loadingContacts = ref(false)
const sysUnreadCount = ref(0)
const orderUnreadCount = ref(0)
const activityUnreadCount = ref(0)

const displayedContacts = computed(() => chatStore.contacts)

async function loadContacts() {
  loadingContacts.value = true
  try {
    await chatStore.loadContacts()
  } catch (e) { /* handled */ }
  loadingContacts.value = false
}

async function loadUnread() {
  try {
    const data = await notificationApi.getNotifications({ isRead: 0, page: 1, size: 100 }, { showLoading: false })
    const records = data?.records || []
    sysUnreadCount.value = records.filter(n => n.type === 1).length
    orderUnreadCount.value = records.filter(n => n.type === 2).length
    activityUnreadCount.value = records.filter(n => n.type === 3).length
  } catch (e) { /* ignore */ }
}

function onChat(contact) {
  const nickname = encodeURIComponent(contact.nickname || '')
  uni.navigateTo({
    url: `/pages/chat-detail/chat-detail?userId=${contact.userId}&nickname=${nickname}&avatar=${encodeURIComponent(contact.avatarUrl || '')}`
  })
}

function goNotifications(type) {
  uni.navigateTo({ url: `/pages/notifications/notifications?type=${type || ''}` })
}

function goCertify() { uni.navigateTo({ url: '/pages/certify/certify' }) }
function onNotify() { goNotifications('') }

onShow(() => {
  loadUnread()
  if (store.isCertified) {
    loadContacts()
    if (!chatStore.wsConnected && store.token) {
      chatStore.connectStomp(store.token, store.userId)
    }
  }
})
</script>

<style scoped>
.page { width: 100%; height: 100vh; display: flex; flex-direction: column; background: var(--background); overflow: hidden; }

.nav-avatar-wrap { width: 68rpx; height: 68rpx; border-radius: 50%; background: var(--primary-container); display: flex; align-items: center; justify-content: center; }
.nav-avatar { width: 56rpx; height: 56rpx; border-radius: 50%; background: var(--primary); display: flex; align-items: center; justify-content: center; }
.nav-avatar-img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.nav-avatar-text { font-size: 26rpx; font-weight: 700; color: #fff; }
.nav-title-wrap { display: flex; align-items: center; justify-content: center; }
.nav-title-text { font-size: 34rpx; font-weight: 600; color: var(--text-primary); }
.nav-btn { width: 68rpx; height: 68rpx; display: flex; align-items: center; justify-content: center; border-radius: 50%; position: relative; }
.nav-btn:active { background: var(--primary-container); }

.main-scroll { box-sizing: border-box; width: 100%; padding: 0 32rpx; padding-bottom: 180rpx; }

/* Compact notification entries */
.notify-entry { display: flex; align-items: center; padding: 20rpx 28rpx; border-bottom: 1rpx solid var(--outline-light); }
.notify-entry:active { background: var(--surface-hover); }
.notify-entry-icon { width: 68rpx; height: 68rpx; border-radius: 50%; flex-shrink: 0; margin-right: 20rpx; }
.notify-entry-body { flex: 1; min-width: 0; }
.notify-entry-name { font-size: 28rpx; font-weight: 500; color: var(--text-primary); display: block; margin-bottom: 4rpx; }
.notify-entry-desc { font-size: 22rpx; color: var(--text-secondary); display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.section-header { margin-top: 48rpx; margin-bottom: 20rpx; }
.section-label { font-size: 24rpx; font-weight: 500; color: var(--text-tertiary); letter-spacing: 2rpx; padding-left: 8rpx; }

.chat-list-card { border-radius: var(--radius-card) !important; overflow: hidden; }
.chat-list-card :deep(.uni-card__content) { padding: 0 !important; }

.chat-item { display: flex; align-items: center; padding: 28rpx 28rpx; border-bottom: 1rpx solid var(--outline-light); }
.chat-item:last-child { border-bottom: none; }
.chat-item:active { background: var(--surface-hover); }
.chat-avatar-wrap { position: relative; width: 96rpx; height: 96rpx; flex-shrink: 0; }
.chat-avatar-img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.chat-avatar { width: 100%; height: 100%; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 34rpx; font-weight: 600; color: #fff; background: linear-gradient(135deg, var(--primary), var(--secondary)); }
.chat-body { flex: 1; margin-left: 24rpx; min-width: 0; }
.chat-top { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8rpx; }
.chat-name { font-size: 30rpx; font-weight: 500; color: var(--text-primary); }
.chat-time { font-size: 22rpx; color: var(--text-tertiary); flex-shrink: 0; }
.chat-time--new { color: var(--primary); font-weight: 600; }
.chat-bottom { display: flex; justify-content: space-between; align-items: center; }
.chat-preview { font-size: 26rpx; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; padding-right: 16rpx; }

.empty-contacts { padding: 60rpx 0; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12rpx; }
.empty-text { font-size: 26rpx; color: var(--text-tertiary); }
.empty-sub { font-size: 26rpx; color: var(--primary); font-weight: 500; }
.empty-sub:active { opacity: 0.6; }

.bottom-placeholder { height: 160rpx; }
</style>
