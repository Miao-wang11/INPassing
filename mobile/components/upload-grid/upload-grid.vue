<template>
  <view class="ug-wrap">
    <view class="ug-row">
      <view v-for="(url, i) in modelValue" :key="i" class="ug-thumb" @click="onPreview(url)">
        <image v-if="getFileType(url) === 'image'" class="ug-thumb-img" :src="url" mode="aspectFill" />
        <view v-else class="ug-thumb-doc">
          <iconpark-icon :name="getDocIcon(url)" size="36" color="#8B8A86" />
          <text class="ug-thumb-doc-label">{{ getFileExt(url) }}</text>
        </view>
        <view class="ug-thumb-del" @click.stop="onRemove(i)">
          <iconpark-icon name="closeempty" size="14" color="#fff" />
        </view>
      </view>
      <view v-if="modelValue.length < maxCount" class="ug-add" @click="onAdd">
        <iconpark-icon name="plusempty" size="28" color="#D4D2CC" />
      </view>
    </view>
    <text class="ug-count">{{ modelValue.length }}/{{ maxCount }}</text>
  </view>
</template>

<script setup>
import { commonApi } from '@/api'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  maxCount: { type: Number, default: 3 },
  acceptFile: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

function onRemove(index) {
  const next = [...props.modelValue]
  next.splice(index, 1)
  emit('update:modelValue', next)
}

function getFileExt(url) {
  const parts = (url || '').split('.')
  return parts.length > 1 ? parts.pop().toLowerCase() : ''
}

function getFileType(url) {
  const ext = getFileExt(url)
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext)) return 'image'
  if (['pdf'].includes(ext)) return 'pdf'
  if (['doc', 'docx'].includes(ext)) return 'word'
  if (['xls', 'xlsx'].includes(ext)) return 'excel'
  if (['ppt', 'pptx'].includes(ext)) return 'ppt'
  if (['txt'].includes(ext)) return 'text'
  return 'other'
}

function getDocIcon(url) {
  const type = getFileType(url)
  const icons = { pdf: 'file-pdf', word: 'file-doc', excel: 'file-excel', ppt: 'file-ppt', text: 'file-text', other: 'file' }
  return icons[type] || 'file'
}

function onPreview(url) {
  const type = getFileType(url)
  if (type === 'image') {
    uni.previewImage({ urls: [url], current: url })
  } else {
    uni.downloadFile({
      url,
      success: (res) => {
        uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
      },
      fail: () => {
        uni.showToast({ title: '文件打开失败', icon: 'none' })
      }
    })
  }
}

async function onAdd() {
  const remaining = props.maxCount - props.modelValue.length
  if (remaining <= 0) return

  // #ifdef MP-WEIXIN
  if (props.acceptFile) {
    try {
      const actionRes = await new Promise((resolve) => {
        uni.showActionSheet({
          itemList: ['从相册选择图片', '从聊天文件选择'],
          success: resolve,
          fail: () => resolve(null)
        })
      })
      if (!actionRes) return
      if (actionRes.tapIndex === 0) {
        await chooseAndUpload(remaining)
      } else {
        const fileRes = await new Promise((resolve, reject) => {
          wx.chooseMessageFile({ count: Math.min(remaining, 1), type: 'file', success: resolve, fail: reject })
        })
        const file = fileRes.tempFiles[0]
        const url = await commonApi.uploadFile(file.path)
        if (url) {
          emit('update:modelValue', [...props.modelValue, url])
        }
      }
    } catch (e) { /* cancelled */ }
    return
  }
  // #endif

  await chooseAndUpload(remaining)
}

async function chooseAndUpload(count) {
  try {
    const res = await new Promise((resolve, reject) => {
      uni.chooseImage({
        count,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: resolve,
        fail: reject
      })
    })
    uni.showLoading({ title: '上传中…' })
    const urls = await commonApi.uploadFiles(res.tempFilePaths)
    uni.hideLoading({ fail: () => {} })
    if (urls && urls.length) {
      emit('update:modelValue', [...props.modelValue, ...urls])
    }
  } catch (e) {
    uni.hideLoading({ fail: () => {} })
  }
}
</script>

<style scoped>
.ug-wrap { width: 100% }
.ug-row { display: flex; flex-wrap: wrap; gap: 16rpx; align-items: flex-start }
.ug-thumb { width: 160rpx; height: 160rpx; border-radius: 16rpx; overflow: hidden; position: relative; flex-shrink: 0 }
.ug-thumb-img { width: 100%; height: 100%; object-fit: cover }
.ug-thumb-doc { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; background: var(--surface); gap: 8rpx }
.ug-thumb-doc-label { font-size: 20rpx; color: var(--text-tertiary); text-transform: uppercase }
.ug-thumb-del { position: absolute; top: 4rpx; right: 4rpx; width: 40rpx; height: 40rpx; border-radius: 50%; background: rgba(0,0,0,.55); display: flex; align-items: center; justify-content: center }
.ug-add { width: 160rpx; height: 160rpx; border-radius: 16rpx; border: 2rpx dashed var(--outline); display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: var(--surface) }
.ug-add:active { background: var(--surface-hover) }
.ug-count { display: block; font-size: 22rpx; color: var(--text-tertiary); margin-top: 12rpx }
</style>
