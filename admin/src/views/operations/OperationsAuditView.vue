<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="head">
          <strong>资金一致性与售后运营</strong>
          <el-button :loading="loading" @click="refresh">实时核验</el-button>
        </div>
      </template>
      <el-alert type="info" show-icon :closable="false"
        title="本面板仅验证数据库模拟余额流水，不代表真实支付机构对账。发现异常后必须人工核实，禁止直接修改余额。"/>
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6"><el-statistic title="待处理售后" :value="summary?.pendingCases ?? 0"/></el-col>
        <el-col :span="6"><el-statistic title="已驳回售后" :value="summary?.rejectedCases ?? 0"/></el-col>
        <el-col :span="6"><el-statistic title="模拟全额退款笔数" :value="summary?.refundedCases ?? 0"/></el-col>
        <el-col :span="6"><el-statistic title="售后模拟退款流水总额（元）" :value="summary?.totalDisputeRefunds ?? 0" :precision="2"/></el-col>
      </el-row>
      <div class="head"><h3>异常任务流水（最多前 100 项）</h3>
        <el-button type="primary" link @click="$router.push('/disputes')">进入售后仲裁</el-button></div>
      <el-table :data="records" v-loading="loading" stripe
        empty-text="当前查询范围内未发现资金规则不一致的任务">
        <el-table-column prop="taskId" label="任务 ID" width="105"/>
        <el-table-column prop="taskStatus" label="任务状态" width="95"/>
        <el-table-column label="任务预付额" width="115">
          <template #default="{row}">¥{{ displayMoney(row.expected) }}</template>
        </el-table-column>
        <el-table-column label="已扣款" width="105">
          <template #default="{row}">¥{{ displayMoney(row.charged) }}</template>
        </el-table-column>
        <el-table-column label="已退款" width="105">
          <template #default="{row}">¥{{ displayMoney(row.refunded) }}</template>
        </el-table-column>
        <el-table-column label="已结算" width="105">
          <template #default="{row}">¥{{ displayMoney(row.disbursed) }}</template>
        </el-table-column>
        <el-table-column label="异常说明" min-width="220" prop="issue"/>
        <el-table-column label="操作" width="100">
          <template #default="{row}">
            <el-button type="primary" link @click="inspect(row.taskId)">查看账本</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="visible" title="任务模拟资金核验" width="600px">
      <el-descriptions v-if="selected" :column="2" border>
        <el-descriptions-item label="任务ID">{{selected.taskId}}</el-descriptions-item>
        <el-descriptions-item label="状态">{{selected.taskStatus}}</el-descriptions-item>
        <el-descriptions-item label="预付总额">{{displayMoney(selected.expected)}}</el-descriptions-item>
        <el-descriptions-item label="真实扣款">{{displayMoney(selected.charged)}}</el-descriptions-item>
        <el-descriptions-item label="真实退款">{{displayMoney(selected.refunded)}}</el-descriptions-item>
        <el-descriptions-item label="真实结算">{{displayMoney(selected.disbursed)}}</el-descriptions-item>
        <el-descriptions-item label="扣款流水笔数">{{selected.debitRows}}</el-descriptions-item>
        <el-descriptions-item label="退款流水笔数">{{selected.refundRows}}</el-descriptions-item>
        <el-descriptions-item label="检查结果" :span="2">{{selected.consistent?'一致':selected.issue}}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button @click="visible=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {getOperationsSummary,listLedgerAnomalies,inspectTaskLedger} from '@/api/operations'
import type {OperationsSummary,LedgerCheck} from '@/api/operations'
const loading=ref(false),visible=ref(false),selected=ref<LedgerCheck|null>(null)
const summary=ref<OperationsSummary|null>(null),records=ref<LedgerCheck[]>([])
const displayMoney=(value:number|null|undefined)=>Number(value||0).toFixed(2)
async function refresh(){
  loading.value=true
  try{
    const [s,r]=await Promise.all([getOperationsSummary(),listLedgerAnomalies(100)])
    summary.value=s as OperationsSummary
    records.value=r as LedgerCheck[]
  }finally{loading.value=false}
}
async function inspect(taskId:number){
  selected.value=await inspectTaskLedger(taskId) as LedgerCheck
  visible.value=true
}
onMounted(refresh)
</script>
<style scoped>
.head{display:flex;align-items:center;justify-content:space-between;margin:16px 0}
.stat-row{margin:25px 0}.stat-row :deep(.el-col){border-right:1px solid #eee}
</style>
