<template>
  <div class="page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="发送通知" name="send">
        <el-card>
          <template #header>
            <span>发送通知</span>
          </template>
          <el-form :model="sendForm" label-width="80px">
            <el-form-item label="发送方式">
              <el-radio-group v-model="sendForm.isBroadcast">
                <el-radio :value="false">指定用户</el-radio>
                <el-radio :value="true">全部广播</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="!sendForm.isBroadcast" label="选择用户">
              <div class="user-select-area">
                <div class="search-row">
                  <el-input v-model="userSearchKeyword" placeholder="搜索用户名或昵称" style="width:260px" @keyup.enter="searchUsers" />
                  <el-button @click="searchUsers" :loading="userSearchLoading">搜索</el-button>
                </div>
                <el-select
                  v-if="searchResults.length > 0"
                  v-model="selectedUserIds"
                  multiple
                  filterable
                  placeholder="点击选择用户"
                  style="width:100%;margin-top:8px"
                  @change="onUserSelect"
                >
                  <el-option v-for="u in searchResults" :key="u.id" :label="`${u.nickname} (${u.username})`" :value="u.id" />
                </el-select>
                <div v-if="selectedUsers.length > 0" class="selected-tags">
                  <el-tag
                    v-for="u in selectedUsers"
                    :key="u.id"
                    closable
                    style="margin:4px 8px 4px 0"
                    @close="removeSelectedUser(u.id)"
                  >{{ u.nickname }} ({{ u.username }})</el-tag>
                  <span class="tag-count">已选 {{ selectedUsers.length }} 人</span>
                </div>
                <span v-if="!sendForm.isBroadcast && selectedUsers.length === 0" class="field-hint">搜索并选择用户，支持多选</span>
              </div>
            </el-form-item>
            <el-form-item label="通知类型">
              <el-select v-model="sendForm.type" style="width:240px">
                <el-option v-for="(label, key) in NOTIFICATION_TYPES" :key="key" :label="label" :value="Number(key)" />
              </el-select>
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="sendForm.title" style="width:400px" />
            </el-form-item>
            <el-form-item label="内容">
              <el-input v-model="sendForm.content" type="textarea" :rows="4" style="width:400px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitSend" :loading="sending">发送</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="发送记录" name="history">
        <el-card>
          <el-table :data="historyData" v-loading="historyLoading" stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-detail" v-if="row.requestParams">
                  <h4>通知详情</h4>
                  <pre class="params-json">{{ formatJson(row.requestParams) }}</pre>
                </div>
                <div v-else class="expand-empty">暂无详情</div>
              </template>
            </el-table-column>
            <el-table-column prop="adminName" label="操作人" width="100" />
            <el-table-column label="发送方式" width="90">
              <template #default="{ row }">
                <el-tag :type="row.action === '广播' ? 'warning' : ''" size="small">{{ row.action }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="时间" width="180" />
          </el-table>

          <el-pagination
            v-model:current-page="historyQuery.page" v-model:page-size="historyQuery.size"
            :total="historyTotal" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
            @change="fetchHistory" class="pagination"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sendNotification, broadcastNotification } from '@/api/notifications'
import { listLogs } from '@/api/logs'
import { listUsers } from '@/api/users'
import { NOTIFICATION_TYPES } from '@/utils/constants'

const activeTab = ref('send')
const sending = ref(false)
const sendForm = reactive({ isBroadcast: false, type: 1, title: '', content: '' })

// 用户选择
const userSearchKeyword = ref('')
const userSearchLoading = ref(false)
const searchResults = ref<any[]>([])
const selectedUserIds = ref<number[]>([])
const selectedUsers = ref<any[]>([])

async function searchUsers() {
  if (!userSearchKeyword.value.trim()) { ElMessage.warning('请输入搜索关键词'); return }
  userSearchLoading.value = true
  try {
    const res = await listUsers({ keyword: userSearchKeyword.value, size: 20 }) as any
    searchResults.value = res.records || []
    if (searchResults.value.length === 0) ElMessage.info('未找到匹配用户')
  } finally { userSearchLoading.value = false }
}

function onUserSelect(ids: number[]) {
  const currentIds = new Set(selectedUsers.value.map(u => u.id))
  // 新增
  for (const id of ids) {
    if (!currentIds.has(id)) {
      const u = searchResults.value.find((r: any) => r.id === id)
      if (u) selectedUsers.value.push({ id: u.id, nickname: u.nickname, username: u.username })
    }
  }
  // 移除（从 select 取消选中）
  for (const u of selectedUsers.value) {
    if (!ids.includes(u.id)) {
      selectedUsers.value = selectedUsers.value.filter(s => s.id !== u.id)
    }
  }
}

function removeSelectedUser(id: number) {
  selectedUsers.value = selectedUsers.value.filter(u => u.id !== id)
  selectedUserIds.value = selectedUserIds.value.filter(uid => uid !== id)
}

async function submitSend() {
  const title = sendForm.isBroadcast ? '确认向全部用户发送该通知？' : `确认向 ${selectedUsers.value.length} 个用户发送该通知？`
  await ElMessageBox.confirm(title, '提示', { type: 'warning' })
  sending.value = true
  try {
    if (sendForm.isBroadcast) {
      await broadcastNotification({ type: sendForm.type, title: sendForm.title, content: sendForm.content })
      ElMessage.success('广播成功')
    } else {
      if (selectedUsers.value.length === 0) { ElMessage.warning('请先选择用户'); return }
      const ids = selectedUsers.value.map(u => u.id)
      await sendNotification({ userIds: ids, type: sendForm.type, title: sendForm.title, content: sendForm.content })
      ElMessage.success('发送成功')
      selectedUsers.value = []
      selectedUserIds.value = []
      searchResults.value = []
      userSearchKeyword.value = ''
    }
    sendForm.title = ''; sendForm.content = ''
    activeTab.value = 'history'
    fetchHistory()
  } finally { sending.value = false }
}

// --- 发送记录 ---
const historyLoading = ref(false)
const historyData = ref<any[]>([])
const historyTotal = ref(0)
const historyQuery = reactive({ page: 1, size: 10 })

function formatJson(str: string) {
  try { return JSON.stringify(JSON.parse(str), null, 2) } catch { return str }
}

async function fetchHistory() {
  historyLoading.value = true
  try {
    const res = await listLogs({ module: '消息管理', page: historyQuery.page, size: historyQuery.size }) as any
    historyData.value = res.records
    historyTotal.value = res.total
  } finally { historyLoading.value = false }
}

watch(activeTab, (tab) => { if (tab === 'history') fetchHistory() })
</script>

<style scoped>
.pagination { margin-top: 20px; display: flex; justify-content: center; }
:deep(.el-table) { border-radius: var(--radius-sm); }
:deep(.el-table th) { background: var(--neutral-surface); color: var(--text-secondary); font-weight: 600; }
.expand-detail { padding: 8px 16px; }
.expand-detail h4 { margin: 0 0 8px; font-size: 14px; color: var(--text-primary); }
.params-json { background: var(--neutral-surface); padding: 12px; border-radius: var(--radius-sm); font-size: 13px; line-height: 1.6; white-space: pre-wrap; word-break: break-all; margin: 0; max-height: 300px; overflow-y: auto; }
.expand-empty { padding: 16px; color: var(--text-tertiary); font-size: 13px; text-align: center; }
.user-select-area { width: 100%; }
.search-row { display: flex; gap: 8px; align-items: center; }
.selected-tags { margin-top: 8px; }
.tag-count { font-size: 13px; color: #909399; margin-left: 4px; }
.field-hint { font-size: 12px; color: #c0c4cc; }
</style>
