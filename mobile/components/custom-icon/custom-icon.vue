<template>
  <image
    v-if="dataUri"
    :src="dataUri"
    mode="aspectFit"
    :style="imgStyle"
  />
</template>

<script setup>
import { computed } from 'vue'
import { getIcon, toDataUri } from '@/utils/custom-icons'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [String, Number], default: 32 }
})

const dataUri = computed(() => {
  const svg = getIcon(props.name)
  return toDataUri(svg)
})

const imgStyle = computed(() => {
  const px = parseFloat(props.size) || 32
  return { width: px + 'rpx', height: px + 'rpx' }
})
</script>
