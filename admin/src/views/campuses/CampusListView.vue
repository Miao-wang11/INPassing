<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="toolbar">
          <strong>学校与校区管理</strong>
          <el-button @click="loadAll" :loading="loading">刷新</el-button>
        </div>
      </template>

      <p class="hint">校区是由学生申请并经管理员审核确认的身份归属，不能通过个人资料任意更改。</p>
      <div v-if="canEdit" class="forms">
        <el-form inline @submit.prevent="addSchool">
          <el-form-item label="新增学校">
            <el-input v-model.trim="newSchool" maxlength="100" show-word-limit placeholder="填写学校名称" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" :disabled="!newSchool" @click="addSchool">添加学校</el-button>
          </el-form-item>
        </el-form>
        <el-form inline @submit.prevent="addCampus">
          <el-form-item label="新增校区">
            <el-select v-model="selectedSchoolId" placeholder="选择所属学校" style="width:200px">
              <el-option v-for="item in schools" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-input v-model.trim="newCampus" maxlength="100" placeholder="填写校区名称" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" :disabled="!selectedSchoolId || !newCampus" @click="addCampus">添加校区</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table :data="campuses" :loading="loading" stripe empty-text="尚未配置校区，请先添加学校及校区">
        <el-table-column prop="schoolName" label="学校" min-width="200" />
        <el-table-column prop="campusName" label="校区" min-width="180" />
        <el-table-column prop="campusId" label="校区ID" width="130" />
        <el-table-column prop="schoolId" label="学校ID" width="130" />
      </el-table>
      <p class="hint">历史已认证但未绑定校区的账号需人工核验后迁移，不能直接批量赋予权限。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { createCampus, createSchool, listCampuses, listSchools } from '@/api/campuses'
import type { CampusOption, SchoolOption } from '@/api/campuses'

const auth = useAuthStore()
const canEdit = computed(() => auth.adminInfo?.role === 1)
const loading = ref(false)
const saving = ref(false)
const schools = ref<SchoolOption[]>([])
const campuses = ref<CampusOption[]>([])
const newSchool = ref('')
const newCampus = ref('')
const selectedSchoolId = ref<number | null>(null)

async function loadAll() {
  loading.value = true
  try {
    const [schoolData, campusData] = await Promise.all([listSchools(), listCampuses()])
    schools.value = schoolData as unknown as SchoolOption[]
    campuses.value = campusData as unknown as CampusOption[]
  } finally {
    loading.value = false
  }
}
async function addSchool() {
  if (!newSchool.value || !canEdit.value) return
  saving.value = true
  try {
    await createSchool(newSchool.value)
    newSchool.value = ''
    ElMessage.success('学校创建成功')
    await loadAll()
  } finally {
    saving.value = false
  }
}
async function addCampus() {
  if (!selectedSchoolId.value || !newCampus.value || !canEdit.value) return
  saving.value = true
  try {
    await createCampus(selectedSchoolId.value, newCampus.value)
    newCampus.value = ''
    ElMessage.success('校区创建成功')
    await loadAll()
  } finally {
    saving.value = false
  }
}
onMounted(loadAll)
</script>

<style scoped>
.toolbar { display:flex; align-items:center; justify-content:space-between; }
.forms { margin:24px 0; }
.hint { margin:12px 0 18px; color:var(--text-secondary); font-size:13px; }
</style>
