<template>
  <div class="page">
    <el-card>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="昵称/手机号" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="认证状态">
          <el-select v-model="query.verifyStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="未认证" :value="0" />
            <el-option label="审核中" :value="1" />
            <el-option label="已认证" :value="2" />
            <el-option label="认证驳回" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="profileId" label="档案ID" width="80" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="realName" label="真实姓名" width="100" />
        <el-table-column label="认证状态" width="100">
          <template #default="{ row }">
            <span class="status-tag" :style="{ color: certStyle(row.verifyStatus).color, background: certStyle(row.verifyStatus).bgColor }">{{ certStyle(row.verifyStatus).label }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="creditScore" label="信用分" width="80" />
        <el-table-column label="评分" width="70">
          <template #default="{ row }">{{ row.avgRating != null ? Number(row.avgRating).toFixed(1) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="totalOrders" label="历史接单" width="80" />
        <el-table-column prop="successOrders" label="成功完成" width="80" />
        <el-table-column label="累计收入" width="100">
          <template #default="{ row }">¥{{ (row.totalEarnings ?? 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="currentOrders" label="当前接单" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span v-if="row.isBanned" class="status-tag" :style="{ color: '#E87474', background: '#FEF0F0' }">已禁止</span>
            <span v-else-if="row.isOnline" class="status-tag" :style="{ color: '#2EB89E', background: '#EDFAF7' }">在线</span>
            <span v-else class="status-tag" :style="{ color: '#8492A6', background: '#EFF2F7' }">离线</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/runners/${row.profileId}`)">详情</el-button>
            <el-button v-if="row.verifyStatus === 2" :type="row.isBanned ? 'success' : 'danger'" link @click="toggleBan(row)">
              {{ row.isBanned ? '恢复接单' : '禁止接单' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page" v-model:page-size="query.size"
        :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        @change="fetchData" class="pagination"
      />
    </el-card>

  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRunners, toggleRunnerBan } from '@/api/runners'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const query = reactive({ keyword: '', verifyStatus: undefined as number | undefined, page: 1, size: 10 })
const certStyleMap: Record<number, { color: string; bgColor: string; label: string }> = {
  0: { color: '#8492A6', bgColor: '#EFF2F7', label: '未认证' },
  1: { color: '#C8925D', bgColor: '#FDF3EB', label: '审核中' },
  2: { color: '#2EB89E', bgColor: '#EDFAF7', label: '已认证' },
  3: { color: '#E87474', bgColor: '#FEF0F0', label: '认证驳回' },
}

function certStyle(s: number) {
  return certStyleMap[s] ?? { color: '#8492A6', bgColor: '#EFF2F7', label: '-' }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listRunners({ verifyStatus: query.verifyStatus, keyword: query.keyword || undefined, page: query.page, size: query.size }) as any
    tableData.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

function search() { query.page = 1; fetchData() }
function reset() { query.keyword = ''; query.verifyStatus = undefined; search() }

async function toggleBan(row: any) {
  const action = row.isBanned ? '恢复接单' : '禁止接单'
  await ElMessageBox.confirm(`确认${action}跑腿员 "${row.nickname}"？`, '提示', { type: 'warning' })
  await toggleRunnerBan(row.profileId, !row.isBanned)
  ElMessage.success('操作成功')
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.search-form { margin-bottom: 16px; }
.search-form :deep(.el-form-item__label) { color: var(--text-secondary); }
.pagination { margin-top: 20px; display: flex; justify-content: center; }
.status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}

.income { font-size: 18px; font-weight: bold; color: var(--brand-accent); }
:deep(.el-table) { border-radius: var(--radius-sm); }
:deep(.el-table th) { background: var(--neutral-surface); color: var(--text-secondary); font-weight: 600; }
</style>
