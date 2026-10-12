<template>
  <div class="job-management">
    <el-card>
      <!-- 筛选栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="任务名称">
          <el-input
            v-model="searchForm.jobName"
            placeholder="请输入任务名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="任务编码">
          <el-input
            v-model="searchForm.jobCode"
            placeholder="如 cleanOperationLogs"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="handleAdd">新建任务</el-button>
        </el-form-item>
      </el-form>

      <!-- 任务表格 -->
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="jobName" label="任务名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="jobCode" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column label="处理器" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.handlerBean }}</span>
            <!-- ponytail: 预置的 backupJobHandler 在备份模块未就绪时容器里没有，后端用 handlerAvailable=false 标出 -->
            <el-tag v-if="row.handlerAvailable === false" type="danger" size="small" class="tag-gap">未就绪</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cron" label="cron" width="150">
          <template #default="{ row }">
            <code class="cron-code">{{ row.cron }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row as JobItem)"
            />
          </template>
        </el-table-column>
        <el-table-column label="上次运行" width="230">
          <template #default="{ row }">
            <template v-if="row.lastRunAt">
              <el-tag :type="row.lastRunStatus === 1 ? 'success' : 'danger'" size="small">
                {{ row.lastRunStatus === 1 ? '成功' : '失败' }}
              </el-tag>
              <span class="tag-gap">{{ row.lastRunAt }}</span>
            </template>
            <span v-else class="muted">从未运行</span>
          </template>
        </el-table-column>
        <el-table-column label="下次运行" width="170">
          <template #default="{ row }">
            {{ row.status === 1 && row.nextRunAt ? row.nextRunAt : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="VideoPlay" @click="handleRun(row as JobItem)">立即执行</el-button>
            <el-button link type="info" :icon="Document" @click="openLogs(row as JobItem)">日志</el-button>
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as JobItem)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row as JobItem)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px"
        @size-change="handleSearch"
        @current-change="loadData"
      />
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="jobName">
          <el-input v-model="form.jobName" placeholder="如：清理操作日志" maxlength="100" />
        </el-form-item>
        <el-form-item label="任务编码" prop="jobCode">
          <el-input v-model="form.jobCode" placeholder="字母开头，如 cleanOperationLogs" maxlength="50" />
        </el-form-item>
        <el-form-item label="处理器" prop="handlerBean">
          <el-select
            v-model="form.handlerBean"
            placeholder="请选择处理器 Bean"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option v-for="name in handlerOptions" :key="name" :label="name" :value="name" />
          </el-select>
          <div v-if="form.handlerBean && !handlers.includes(form.handlerBean)" class="form-tip warning">
            当前处理器在容器中不存在，保存会被后端拒绝；请等待对应模块就绪
          </div>
        </el-form-item>
        <el-form-item label="cron" prop="cron">
          <div class="cron-row">
            <el-input v-model="form.cron" placeholder="秒 分 时 日 月 周，如 0 0 3 * * ?" />
            <el-select
              v-model="cronTemplate"
              placeholder="常用模板"
              clearable
              style="width: 180px"
              @change="applyCronTemplate"
            >
              <el-option v-for="t in CRON_TEMPLATES" :key="t.cron" :label="`${t.label}（${t.cron}）`" :value="t.cron" />
            </el-select>
          </div>
          <div class="form-tip">Spring 6 位格式：秒 分 时 日 月 周；「日」与「周」二选一，另一个用 ? 占位</div>
        </el-form-item>
        <el-form-item label="参数" prop="params">
          <el-input
            v-model="form.params"
            type="textarea"
            :rows="3"
            placeholder='JSON 对象，如 {"retainDays": 90}；留空表示无参数'
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 执行日志抽屉 -->
    <el-drawer v-model="logsVisible" :title="logsTitle" size="760px">
      <div class="logs-toolbar">
        <el-select v-model="logsQuery.status" placeholder="全部状态" clearable style="width: 140px" @change="handleLogsSearch">
          <el-option label="成功" :value="JOB_LOG_STATUS.SUCCESS" />
          <el-option label="失败" :value="JOB_LOG_STATUS.FAIL" />
          <el-option label="执行中" :value="JOB_LOG_STATUS.RUNNING" />
        </el-select>
        <el-button :icon="Refresh" @click="loadLogs">刷新</el-button>
      </div>
      <el-table v-loading="logsLoading" :data="logs" stripe border size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <!-- ponytail: message 是 handler 摘要或异常原文，整段 <pre> 展示便于排障 -->
            <pre class="raw-block" :class="{ error: row.status === JOB_LOG_STATUS.FAIL }">{{ row.message || '-' }}</pre>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="startedAt" label="开始时间" width="165" />
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="logTagType(row.status)" size="small">{{ row.statusName || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggeredBy" label="触发" width="110" show-overflow-tooltip />
        <el-table-column prop="message" label="摘要" min-width="160" show-overflow-tooltip />
      </el-table>
      <el-pagination
        v-model:current-page="logsQuery.pageNum"
        v-model:page-size="logsQuery.pageSize"
        :total="logsQuery.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        size="small"
        style="margin-top: 16px"
        @size-change="handleLogsSearch"
        @current-change="loadLogs"
      />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Document, VideoPlay } from '@element-plus/icons-vue'
import {
  getJobList,
  getJobHandlers,
  createJob,
  updateJob,
  deleteJob,
  updateJobStatus,
  runJob,
  getJobLogs,
  CRON_TEMPLATES,
  JOB_LOG_STATUS,
  type JobItem,
  type JobSaveRequest,
  type JobLogItem
} from '@/api/job'
import { handleError } from '@/utils/errorHandler'

/** 「立即执行」后多久刷新一次日志与列表（任务通常几百毫秒内结束） */
const RUN_REFRESH_DELAY_MS = 2000

// ------------------------------------------------------------------
// 列表
// ------------------------------------------------------------------

const loading = ref(false)
const tableData = ref<JobItem[]>([])

const searchForm = reactive<{ jobName: string; jobCode: string; status?: number }>({
  jobName: '',
  jobCode: '',
  status: undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const loadData = async () => {
  loading.value = true
  try {
    const page = await getJobList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      jobName: searchForm.jobName || undefined,
      jobCode: searchForm.jobCode || undefined,
      status: searchForm.status
    })
    tableData.value = page.records ?? []
    pagination.total = page.total ?? 0
  } catch (error) {
    handleError(error, '加载定时任务失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.jobName = ''
  searchForm.jobCode = ''
  searchForm.status = undefined
  handleSearch()
}

// ------------------------------------------------------------------
// 处理器下拉
// ------------------------------------------------------------------

const handlers = ref<string[]>([])

/** 下拉选项：容器里的 handler + 当前表单里可能不在容器中的旧值（编辑预置任务时仍可见） */
const handlerOptions = computed(() => {
  const current = form.handlerBean
  return current && !handlers.value.includes(current) ? [...handlers.value, current] : handlers.value
})

const loadHandlers = async () => {
  try {
    handlers.value = (await getJobHandlers()) ?? []
  } catch (error) {
    handleError(error, '加载任务处理器列表失败')
  }
}

// ------------------------------------------------------------------
// 新建 / 编辑
// ------------------------------------------------------------------

interface JobForm extends JobSaveRequest {
  id?: number
  status: number
}

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const cronTemplate = ref('')

const defaultForm = (): JobForm => ({
  id: undefined,
  jobName: '',
  jobCode: '',
  handlerBean: '',
  cron: '',
  params: '',
  status: 0,
  remark: ''
})

const form = reactive<JobForm>(defaultForm())

const validateCron = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  const parts = (value ?? '').trim().split(/\s+/).filter(Boolean)
  if (parts.length !== 6) {
    callback(new Error('cron 需为 6 段：秒 分 时 日 月 周'))
  } else {
    callback()
  }
}

const validateParams = (_rule: unknown, value: string | undefined, callback: (error?: Error) => void) => {
  if (!value || !value.trim()) {
    callback()
    return
  }
  try {
    const parsed: unknown = JSON.parse(value)
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      callback(new Error('参数必须是 JSON 对象，如 {"retainDays": 90}'))
    } else {
      callback()
    }
  } catch {
    callback(new Error('参数不是合法的 JSON'))
  }
}

const rules: FormRules = {
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  jobCode: [
    { required: true, message: '请输入任务编码', trigger: 'blur' },
    { pattern: /^[A-Za-z][A-Za-z0-9_-]*$/, message: '须以字母开头，只能包含字母、数字、下划线、中划线', trigger: 'blur' }
  ],
  handlerBean: [{ required: true, message: '请选择任务处理器', trigger: 'change' }],
  cron: [
    { required: true, message: '请输入 cron 表达式', trigger: 'blur' },
    { validator: validateCron, trigger: 'blur' }
  ],
  params: [{ validator: validateParams, trigger: 'blur' }]
}

const applyCronTemplate = (value: string) => {
  if (value) {
    form.cron = value
  }
}

const handleAdd = () => {
  dialogTitle.value = '新建任务'
  Object.assign(form, defaultForm())
  cronTemplate.value = ''
  dialogVisible.value = true
}

const handleEdit = (row: JobItem) => {
  dialogTitle.value = '编辑任务'
  Object.assign(form, {
    id: row.id,
    jobName: row.jobName,
    jobCode: row.jobCode,
    handlerBean: row.handlerBean,
    cron: row.cron,
    params: row.params ?? '',
    status: row.status,
    remark: row.remark ?? ''
  })
  cronTemplate.value = CRON_TEMPLATES.some(t => t.cron === row.cron) ? row.cron : ''
  dialogVisible.value = true
}

const toRequest = (): JobSaveRequest => ({
  jobName: form.jobName.trim(),
  jobCode: form.jobCode.trim(),
  handlerBean: form.handlerBean.trim(),
  cron: form.cron.trim(),
  params: form.params?.trim() || undefined,
  status: form.status,
  remark: form.remark?.trim() || undefined
})

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateJob(form.id, toRequest())
        ElMessage.success('更新成功')
      } else {
        await createJob(toRequest())
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch {
      // ponytail: request.ts 已弹出后端的具体校验信息（cron 非法 / 编码重复 / 处理器不存在），这里不再重复提示
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  Object.assign(form, defaultForm())
  cronTemplate.value = ''
}

// ------------------------------------------------------------------
// 启停 / 删除 / 立即执行
// ------------------------------------------------------------------

const handleStatusChange = async (row: JobItem) => {
  const nextStatus = row.status
  const previousStatus: 0 | 1 = nextStatus === 1 ? 0 : 1
  try {
    await updateJobStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 1 ? '任务已启用' : '任务已停用')
    // 重新拉列表以拿到后端算出的 nextRunAt
    loadData()
  } catch (error) {
    handleError(error, '状态更新失败')
    row.status = previousStatus
  }
}

const handleDelete = (row: JobItem) => {
  ElMessageBox.confirm(`确定删除任务「${row.jobName}」吗？执行日志会保留。`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteJob(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      handleError(error, '删除失败')
    }
  }).catch(() => {})
}

const handleRun = (row: JobItem) => {
  ElMessageBox.confirm(`立即执行任务「${row.jobName}」？将以系统身份异步运行一次。`, '立即执行', {
    type: 'info',
    confirmButtonText: '执行',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      const logId = await runJob(row.id)
      ElMessage.success(`已提交执行（日志 ID ${logId}）`)
      await openLogs(row)
      // 任务异步跑完后再刷一次日志与列表（上次运行 / 结果）
      window.setTimeout(() => {
        loadLogs()
        loadData()
      }, RUN_REFRESH_DELAY_MS)
    } catch (error) {
      handleError(error, '提交执行失败')
    }
  }).catch(() => {})
}

// ------------------------------------------------------------------
// 日志抽屉
// ------------------------------------------------------------------

const logsVisible = ref(false)
const logsLoading = ref(false)
const logs = ref<JobLogItem[]>([])
const currentJob = ref<JobItem | null>(null)

const logsQuery = reactive<{ pageNum: number; pageSize: number; total: number; status?: number }>({
  pageNum: 1,
  pageSize: 10,
  total: 0,
  status: undefined
})

const logsTitle = computed(() =>
  currentJob.value ? `执行日志 - ${currentJob.value.jobName}（${currentJob.value.jobCode}）` : '执行日志'
)

const loadLogs = async () => {
  if (!currentJob.value) return
  logsLoading.value = true
  try {
    const page = await getJobLogs(currentJob.value.id, {
      pageNum: logsQuery.pageNum,
      pageSize: logsQuery.pageSize,
      status: logsQuery.status
    })
    logs.value = page.records ?? []
    logsQuery.total = page.total ?? 0
  } catch (error) {
    handleError(error, '加载执行日志失败')
  } finally {
    logsLoading.value = false
  }
}

const handleLogsSearch = () => {
  logsQuery.pageNum = 1
  loadLogs()
}

const openLogs = async (row: JobItem) => {
  currentJob.value = row
  logsQuery.pageNum = 1
  logsQuery.status = undefined
  logsVisible.value = true
  await loadLogs()
}

const logTagType = (status: number): 'success' | 'danger' | 'warning' => {
  if (status === JOB_LOG_STATUS.SUCCESS) return 'success'
  if (status === JOB_LOG_STATUS.FAIL) return 'danger'
  return 'warning'
}

const formatDuration = (ms?: number): string => {
  if (ms === undefined || ms === null) return '-'
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${ms} ms`
}

onMounted(() => {
  loadData()
  loadHandlers()
})
</script>

<style scoped lang="scss">
.job-management {
  .search-form {
    margin-bottom: 16px;
  }

  .tag-gap {
    margin-left: 6px;
  }

  .muted {
    color: #909399;
  }

  .cron-code {
    font-family: Menlo, Consolas, monospace;
    font-size: 12px;
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 3px;
  }

  .cron-row {
    display: flex;
    gap: 8px;
    width: 100%;
  }

  .form-tip {
    width: 100%;
    font-size: 12px;
    line-height: 1.5;
    color: #909399;
    margin-top: 4px;

    &.warning {
      color: #e6a23c;
    }
  }

  .logs-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }

  .raw-block {
    margin: 0 12px;
    max-height: 240px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-all;
    font-size: 12px;
    line-height: 1.5;
    background: #f5f7fa;
    padding: 8px;
    border-radius: 4px;

    &.error {
      color: #f56c6c;
      background: #fef0f0;
    }
  }
}
</style>
