<template>
  <div class="not-found">
    <div class="not-found-content" ref="contentRef">
      <div class="nf-code" ref="codeRef">404</div>
      <div class="nf-illustration" ref="illustRef">
        <svg viewBox="0 0 200 120" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="40" y="50" width="50" height="40" rx="4" fill="#FF6B4A" fill-opacity="0.15" stroke="#FF6B4A" stroke-width="1.5"/>
          <rect x="100" y="60" width="50" height="30" rx="4" fill="#2EC4B6" fill-opacity="0.12" stroke="#2EC4B6" stroke-width="1.5"/>
          <circle cx="160" cy="40" r="20" fill="#FFB347" fill-opacity="0.1" stroke="#FFB347" stroke-width="1.5" stroke-dasharray="4 3"/>
          <path d="M20 100 Q60 70 100 100 Q140 130 180 100" stroke="#D4D2CC" stroke-width="1"/>
        </svg>
      </div>
      <p class="nf-text" ref="textRef">页面不存在</p>
      <p class="nf-sub" ref="subRef">你访问的页面可能已被移除或链接错误</p>
      <el-button type="primary" size="large" class="nf-btn" ref="btnRef" @click="$router.replace('/dashboard')">
        返回首页
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import gsap from 'gsap'

const contentRef = ref<HTMLElement>()
const codeRef = ref<HTMLElement>()
const illustRef = ref<HTMLElement>()
const textRef = ref<HTMLElement>()
const subRef = ref<HTMLElement>()
const btnRef = ref<HTMLElement>()

onMounted(() => {
  const tl = gsap.timeline({ defaults: { ease: 'power3.out' } })

  if (codeRef.value) {
    tl.from(codeRef.value, { y: 30, opacity: 0, duration: 0.8 })
  }
  if (illustRef.value) {
    tl.from(illustRef.value, { scale: 0.8, opacity: 0, duration: 0.6, ease: 'back.out(1.4)' }, '-=0.3')
  }
  if (textRef.value) {
    tl.from(textRef.value, { y: 10, opacity: 0, duration: 0.5 }, '-=0.2')
  }
  if (subRef.value) {
    tl.from(subRef.value, { y: 8, opacity: 0, duration: 0.5 }, '-=0.2')
  }
  if (btnRef.value) {
    tl.from(btnRef.value, { y: 12, opacity: 0, duration: 0.5 }, '-=0.1')
  }

  // SVG 持续浮动
  if (illustRef.value) {
    gsap.to(illustRef.value, {
      y: -8, duration: 2.5, ease: 'sine.inOut', yoyo: true, repeat: -1,
    })
  }
})
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 200px);
  text-align: center;
}

.nf-code {
  font-size: 96px;
  font-weight: 800;
  background: linear-gradient(135deg, #FF6B4A, #FFB347);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1.1;
}

.nf-illustration {
  margin: 16px 0 24px;
}

.nf-illustration svg {
  width: 200px;
  height: auto;
}

.nf-text {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.nf-sub {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 0 0 28px;
}

.nf-btn {
  border-radius: 10px;
  font-weight: 600;
}
</style>
