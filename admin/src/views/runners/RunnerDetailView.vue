<template>
  <div class="page" :class="{ entered }">
    <el-page-header @back="$router.back()" title="返回" class="anim-header">
      <template #content>
        <span class="page-title">跑腿员详情 — {{ detail.nickname || detail.realName || '--' }}</span>
      </template>
    </el-page-header>

    <div class="detail-blocks" v-loading="loading">
      <!-- 基本信息 -->
      <el-card class="detail-block anim-block color-card" style="--anim-order: 0; --card-color: #5B9BD5; --card-bg: #EFF5FB">
        <template #header>
          <span class="card-title-text"><el-icon class="card-title-icon"><InfoFilled /></el-icon>基本信息</span>
        </template>
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="档案ID">{{ detail.profileId }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ detail.nickname }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
          <el-descriptions-item label="真实姓名" :span="2">{{ detail.realName || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 认证与状态 -->
      <el-card class="detail-block anim-block color-card" style="--anim-order: 1; --card-color: #C8925D; --card-bg: #FDF3EB">
        <template #header>
          <span class="card-title-text"><el-icon class="card-title-icon"><Checked /></el-icon>认证与状态</span>
        </template>
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="认证状态">
            <span class="status-tag" :style="{ color: certStyle(detail.verifyStatus).color, background: certStyle(detail.verifyStatus).bgColor }">{{ certStyle(detail.verifyStatus).label }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="在线状态">
            <span class="status-tag" :style="detail.isOnline ? { color: '#2EB89E', background: '#EDFAF7' } : { color: '#8492A6', background: '#EFF2F7' }">{{ detail.isOnline ? '在线' : '离线' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="接单权限">
            <span class="status-tag" :style="detail.isBanned ? { color: '#E87474', background: '#FEF0F0' } : { color: '#2EB89E', background: '#EDFAF7' }">{{ detail.isBanned ? '已禁止' : '正常' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="认证备注" :span="2">{{ detail.verifyRemark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 信用与评分 -->
      <el-card class="detail-block anim-block color-card" :style="{ '--anim-order': 2, '--card-color': creditLevel.color, '--card-bg': creditLevel.bgColor }">
        <template #header>
          <div class="card-header">
            <span class="card-title-text">信用与评分</span>
            <span class="credit-score-badge" :style="{ color: creditLevel.color, background: creditLevel.bgColor }">{{ creditLevel.label }}</span>
          </div>
        </template>
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="信用分">
            <span class="credit-score-value" :style="{ color: creditLevel.color }">{{ detail.creditScore }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="平均评分">{{ detail.avgRating != null ? Number(detail.avgRating).toFixed(1) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="历史接单">{{ detail.totalOrders }}</el-descriptions-item>
          <el-descriptions-item label="成功完成">{{ detail.successOrders }}</el-descriptions-item>
          <el-descriptions-item label="完成率" :span="2">
            {{ detail.totalOrders > 0 ? (detail.successOrders / detail.totalOrders * 100).toFixed(1) + '%' : '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 接单数据 -->
      <el-card class="detail-block anim-block color-card" style="--anim-order: 3; --card-color: #8B6BAE; --card-bg: #F6F1FA">
        <template #header>
          <span class="card-title-text"><el-icon class="card-title-icon"><DataBoard /></el-icon>接单数据</span>
        </template>
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="当前接单数">{{ detail.currentOrders }}</el-descriptions-item>
          <el-descriptions-item label="最大接单数">{{ detail.maxConcurrentOrders }}</el-descriptions-item>
          <el-descriptions-item label="累计收入" :span="2">
            <span class="income">¥{{ (detail.totalEarnings ?? 0).toFixed(2) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { InfoFilled, Checked, DataBoard } from '@element-plus/icons-vue'
import { getRunnerDetail } from '@/api/runners'

const route = useRoute()
const loading = ref(false)
const entered = ref(false)
const detail = reactive<any>({})

const certStyleMap: Record<number, { color: string; bgColor: string; label: string }> = {
  0: { color: '#8492A6', bgColor: '#EFF2F7', label: '未认证' },
  1: { color: '#C8925D', bgColor: '#FDF3EB', label: '审核中' },
  2: { color: '#2EB89E', bgColor: '#EDFAF7', label: '已认证' },
  3: { color: '#E87474', bgColor: '#FEF0F0', label: '认证驳回' },
}

function certStyle(s: number) {
  return certStyleMap[s] ?? { color: '#8492A6', bgColor: '#EFF2F7', label: '-' }
}

const creditLevel = computed(() => {
  const score = detail.creditScore ?? 100
  if (score >= 80) return { color: '#2EB89E', bgColor: '#EDFAF7', label: '信用良好' }
  if (score >= 60) return { color: '#C8925D', bgColor: '#FDF3EB', label: '信用一般' }
  return { color: '#E87474', bgColor: '#FEF0F0', label: '信用风险' }
})

onMounted(async () => {
  loading.value = true
  try {
    const res = await getRunnerDetail(Number(route.params.id)) as any
    Object.assign(detail, res)
  } finally { loading.value = false }
  await nextTick()
  entered.value = true
})
</script>

<style scoped>
.detail-blocks {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-block :deep(.el-descriptions__label) {
  width: 9em;
  white-space: nowrap;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title-text {
  font-weight: 600;
  color: var(--text-primary);
}

.card-title-icon {
  margin-right: 6px;
  font-size: 15px;
  vertical-align: -2px;
  color: var(--text-secondary);
}

/* ===== 独立配色卡片 ===== */
.color-card {
  border-left: 3px solid var(--card-color);
}

.color-card :deep(.el-card__header) {
  background: var(--card-bg);
  border-bottom-color: var(--card-color);
  border-bottom-width: 1px;
  transition: background 0.3s ease;
}

.credit-score-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
}

/* ===== 状态标签 ===== */
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
}

/* ===== 信用分大字 ===== */
.credit-score-value {
  font-size: 22px;
  font-weight: 700;
}

/* ===== 累计收入 ===== */
.income {
  font-size: 18px;
  font-weight: bold;
  color: var(--brand-accent);
}

/* ===== 入场动画 ===== */
.anim-header,
.anim-block {
  opacity: 0;
}

.entered .anim-header {
  animation: slideInLeft 0.4s var(--ease-out) both;
}

.entered .anim-block {
  animation: fadeUp 0.45s calc(0.05s + var(--anim-order, 0) * 0.06s) var(--ease-out) both;
}

@keyframes slideInLeft {
  from { opacity: 0; transform: translateX(-16px); }
  to   { opacity: 1; transform: translateX(0); }
}

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(16px); }
  to   { opacity: 1; transform: translateY(0); }
}
</style>
