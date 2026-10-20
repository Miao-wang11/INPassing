<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="head">
          <strong>售后争议 / 仲裁管理</strong>
          <el-button @click="reload" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-alert type="warning" :closable="false" show-icon
        title="仅超级管理员可作出仲裁；全额退款只处理已送达待确认、尚未结算的模拟余额订单。"/>
      <div class="filters">
        <el-select v-model="status" clearable placeholder="所有状态" style="width:190px" @change="reload">
          <el-option label="待仲裁（冻结结算）" :value="1"/>
          <el-option label="驳回争议（恢复确认）" :value="2"/>
          <el-option label="已模拟全额退款" :value="3"/>
        </el-select>
      </div>
      <el-table :data="items" v-loading="loading" stripe>
        <el-table-column label="售后单" prop="id" width="100"/>
        <el-table-column label="订单" prop="orderId" width="100"/>
        <el-table-column label="任务" prop="taskId" width="100"/>
        <el-table-column label="申请人" prop="applicantId" width="100"/>
        <el-table-column label="原因" prop="reason" show-overflow-tooltip min-width="240"/>
        <el-table-column label="状态" min-width="160">
          <template #default="{row}">
            <el-tag :type="row.status===1?'warning':row.status===2?'info':'success'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" prop="createdAt" min-width="185"/>
        <el-table-column label="操作" width="115">
          <template #default="{row}">
            <el-button type="primary" link @click="openCase(row)">查看/仲裁</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size"
        :total="total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next"
        @change="load" class="pagination"/>
    </el-card>
    <el-dialog v-model="visible" title="售后证据与裁定" width="700px">
      <template v-if="selected">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="争议编号">{{ selected.id }}</el-descriptions-item>
          <el-descriptions-item label="订单编号">{{ selected.orderId }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ selected.applicantId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(selected.status) }}</el-descriptions-item>
          <el-descriptions-item label="申请理由" :span="2">{{ selected.reason }}</el-descriptions-item>
          <el-descriptions-item label="历史处理结果" :span="2">{{ selected.resolutionNote || '尚未处理' }}</el-descriptions-item>
          <el-descriptions-item label="凭证" :span="2">
            <div v-if="selected.evidenceUrls?.length">
              <el-link v-for="(url,i) of selected.evidenceUrls" :key="i" :href="url" target="_blank"
                rel="noopener noreferrer" type="primary" style="margin-right:14px">凭证{{ i+1 }}</el-link>
            </div>
            <span v-else>无图片凭证</span>
          </el-descriptions-item>
        </el-descriptions>
        <h4>操作历史</h4>
        <el-timeline>
          <el-timeline-item v-for="ev in events" :key="ev.id" :timestamp="ev.createdAt">
            {{ ev.actorType }} #{{ ev.actorId }} · {{ ev.eventType }} · {{ ev.detail }}
          </el-timeline-item>
        </el-timeline>
        <template v-if="selected.status===1 && isSuperAdmin">
          <el-form label-position="top">
            <el-form-item label="仲裁结论">
              <el-radio-group v-model="decision">
                <el-radio value="REJECT">驳回争议，恢复正常结算</el-radio>
                <el-radio value="REFUND">模拟全额退款，并终止订单</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="仲裁理由（必填）">
              <el-input v-model="note" type="textarea" :rows="4" maxlength="500" show-word-limit
                placeholder="写明核验依据、双方责任以及处理理由"/>
            </el-form-item>
            <el-alert v-if="decision==='REFUND'" type="warning" :closable="false"
              title="模拟全额退回委托者余额，不会给接单人结算。涉及已经购买的实物需线下协调，本阶段不支持部分赔付。"/>
          </el-form>
        </template>
      </template>
      <template #footer>
        <el-button @click="visible=false">关闭</el-button>
        <el-button v-if="selected?.status===1 && isSuperAdmin" type="danger" :loading="saving"
          :disabled="!note.trim()" @click="submit">确认仲裁</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { ElMessage,ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { listDisputes,getDispute,getDisputeEvents,decideDispute } from '@/api/disputes'
import type { Dispute } from '@/api/disputes'

const store=useAuthStore()
const isSuperAdmin=computed(()=>store.adminInfo?.role===1)
const status=ref<number|undefined>(undefined)
const page=ref(1),size=ref(10),total=ref(0),loading=ref(false),saving=ref(false)
const items=ref<Dispute[]>([]),selected=ref<Dispute|null>(null),visible=ref(false)
const events=ref<Array<{id:number;actorType:string;actorId:number;eventType:string;detail:string;createdAt:string}>>([])
const decision=ref<'REJECT'|'REFUND'>('REJECT')
const note=ref('')
const statusLabel=(v:number)=>({1:'待仲裁（冻结结算）',2:'已驳回',3:'已全额退款'}[v]||'未知')

async function load(){
  loading.value=true
  try {
    const res=await listDisputes({status:status.value,page:page.value,size:size.value}) as any
    items.value=res?.records||[];total.value=res?.total||0
  }finally{loading.value=false}
}
function reload(){page.value=1;load()}
async function openCase(row:Dispute){
  const [d,e]=await Promise.all([getDispute(row.id),getDisputeEvents(row.id)])
  selected.value=d as Dispute
  events.value=e as typeof events.value
  note.value='';decision.value='REJECT';visible.value=true
}
async function submit(){
  if(!selected.value||!note.value.trim()||saving.value)return
  await ElMessageBox.confirm('请确认已核验双方证据。仲裁提交后不能撤回。','确认执行',{
    type:'warning',confirmButtonText:'确认裁定'
  })
  saving.value=true
  try{
    await decideDispute(selected.value.id,decision.value,note.value.trim())
    ElMessage.success('仲裁处理完成')
    visible.value=false
    await load()
  }finally{saving.value=false}
}
onMounted(load)
</script>
<style scoped>
.head,.filters{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}
.pagination{margin-top:20px;display:flex;justify-content:flex-end}
</style>
