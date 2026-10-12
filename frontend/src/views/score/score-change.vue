<template>
  <div class="score-change">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>成绩变更申请</span>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :model="queryForm" label-width="80px" class="filter-form">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="审批状态">
              <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 100%">
                <el-option label="待审批" :value="0" />
                <el-option label="已通过" :value="1" />
                <el-option label="已驳回" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label=" " label-width="20px">
              <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
              <el-button :icon="Refresh" @click="handleReset">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 列表 -->
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="examName" label="考试" min-width="140" show-overflow-tooltip />
        <el-table-column prop="studentName" label="学生" width="100" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="subjectName" label="科目" width="100" />
        <el-table-column prop="oldScore" label="原成绩" width="80" align="center" />
        <el-table-column prop="newScore" label="申请分数" width="90" align="center" />
        <el-table-column prop="changeReason" label="变更原因" min-width="150" show-overflow-tooltip />
        <el-table-column prop="applicantName" label="申请人" width="100" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="160" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0 && row.applicantId === userInfo?.id"
              link type="danger"
              @click="handleCancel(row)"
            >撤销</el-button>
            <el-button
              v-if="row.status === 0 && isAdmin"
              link type="success"
              @click="handleApprove(row)"
            >审批</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 审批对话框 -->
    <el-dialog v-model="approveDialogVisible" title="成绩变更审批" width="480px">
      <el-form :model="approveForm" label-width="90px">
        <el-form-item label="原成绩">{{ currentRow?.oldScore }}</el-form-item>
        <el-form-item label="申请分数">{{ currentRow?.newScore }}</el-form-item>
        <el-form-item label="变更原因">{{ currentRow?.changeReason }}</el-form-item>
        <el-form-item label="审批结果" required>
          <el-radio-group v-model="approveForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!approveForm.approved" label="驳回原因" required>
          <el-input v-model="approveForm.rejectReason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApprove">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  getScoreChangeList, approveScoreChange, cancelScoreChange,
  type ScoreChangeLog, type ScoreChangeQuery
} from '@/api/score-change'
import { handleError } from '@/utils/errorHandler'
import type { PageResponse } from '@/types/common'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
// ponytail: 契约 2 — 管理员判定统一用 userType（1 超管 / 2 校管），与路由守卫、MainLayout 口径一致
const isAdmin = computed(() => [1, 2].includes(userInfo.value?.userType ?? 0))

const loading = ref(false)
const tableData = ref<ScoreChangeLog[]>([])
const approveDialogVisible = ref(false)
const currentRow = ref<ScoreChangeLog | null>(null)

const queryForm = reactive<ScoreChangeQuery>({ pageNum: 1, pageSize: 20 })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const approveForm = reactive({ approved: true, rejectReason: '' })

const statusType = (s: number) => (['warning', 'success', 'danger'] as const)[s] ?? 'info'
const statusText = (s: number) => (['待审批', '已通过', '已驳回'])[s] ?? '未知'

const loadList = async () => {
  loading.value = true
  try {
    const res = await getScoreChangeList({
      ...queryForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }) as PageResponse<ScoreChangeLog>
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    handleError(error, '加载变更申请列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.pageNum = 1; loadList() }
const handleReset = () => { queryForm.status = undefined; pagination.pageNum = 1; loadList() }

const handleCancel = async (row: ScoreChangeLog) => {
  await ElMessageBox.confirm('确定要撤销该变更申请？', '撤销确认', { type: 'warning' }).catch(() => {})
  try {
    await cancelScoreChange(row.id, userInfo.value!.id)
    ElMessage.success('撤销成功')
    loadList()
  } catch (error) {
    handleError(error, '撤销失败')
  }
}

const handleApprove = (row: ScoreChangeLog) => {
  currentRow.value = row
  approveForm.approved = true
  approveForm.rejectReason = ''
  approveDialogVisible.value = true
}

const submitApprove = async () => {
  if (!approveForm.approved && !approveForm.rejectReason.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  try {
    // ponytail: 契约 P — 后端收 { changeLogIds, result 1|2, rejectReason }，审批人取 SecurityContext，不再传 approverId
    await approveScoreChange({
      changeLogIds: [currentRow.value!.id],
      result: approveForm.approved ? 1 : 2,
      rejectReason: approveForm.approved ? undefined : approveForm.rejectReason.trim()
    })
    ElMessage.success(approveForm.approved ? '已通过' : '已驳回')
    approveDialogVisible.value = false
    loadList()
  } catch (error) {
    handleError(error, '审批失败')
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.score-change {
  .filter-form { margin-bottom: 16px; }
  .pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
}
</style>
