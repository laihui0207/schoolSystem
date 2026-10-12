<template>
  <div class="operation-log">
    <el-card>
      <!-- 筛选栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.username"
            placeholder="请输入用户名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="操作">
          <el-input
            v-model="searchForm.operation"
            placeholder="如：审核成绩"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :default-time="defaultTime"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="130" show-overflow-tooltip />
        <el-table-column prop="operation" label="操作" min-width="180" show-overflow-tooltip />
        <el-table-column prop="method" label="请求" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executeTime" label="耗时" width="100">
          <template #default="{ row }">{{ formatDuration(row.executeTime) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row as OperationLogItem)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px"
        @size-change="handleSearch"
        @current-change="loadData"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="操作日志详情" size="560px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="ID">{{ current.id }}</el-descriptions-item>
        <el-descriptions-item label="用户">
          {{ current.username || '-' }}<span v-if="current.userId" class="muted">（ID {{ current.userId }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="操作">{{ current.operation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求">{{ current.method || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ current.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="current.status === 1 ? 'success' : 'danger'">
            {{ current.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ formatDuration(current.executeTime) }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ current.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <!-- ponytail: params 是后端打码后的 JSON 原文，整段 <pre> 展示，便于审计复核 -->
          <pre class="raw-block">{{ prettyParams }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="current.errorMsg" label="错误信息">
          <pre class="raw-block error">{{ current.errorMsg }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import {
  getOperationLogList,
  getOperationLogDetail,
  type OperationLogItem,
  type OperationLogQuery
} from '@/api/log'
import { handleError } from '@/utils/errorHandler'

const loading = ref(false)
const tableData = ref<OperationLogItem[]>([])
const drawerVisible = ref(false)
const current = ref<OperationLogItem | null>(null)

const searchForm = reactive<{ username: string; operation: string; status?: number }>({
  username: '',
  operation: '',
  status: undefined
})

/** 时间范围 [start, end]，value-format 已格式化为 yyyy-MM-dd HH:mm:ss 字符串 */
const timeRange = ref<[string, string] | null>(null)
const defaultTime: [Date, Date] = [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const buildQuery = (): OperationLogQuery => ({
  pageNum: pagination.pageNum,
  pageSize: pagination.pageSize,
  username: searchForm.username || undefined,
  operation: searchForm.operation || undefined,
  status: searchForm.status,
  startTime: timeRange.value?.[0] || undefined,
  endTime: timeRange.value?.[1] || undefined
})

const loadData = async () => {
  loading.value = true
  try {
    const page = await getOperationLogList(buildQuery())
    tableData.value = page.records ?? []
    pagination.total = page.total ?? 0
  } catch (error) {
    handleError(error, '加载操作日志失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.username = ''
  searchForm.operation = ''
  searchForm.status = undefined
  timeRange.value = null
  handleSearch()
}

const handleDetail = async (row: OperationLogItem) => {
  // ponytail: 列表已含 params/errorMsg，但仍走详情接口以便单条权限校验与未来列表瘦身
  try {
    current.value = await getOperationLogDetail(row.id)
    drawerVisible.value = true
  } catch (error) {
    handleError(error, '加载日志详情失败')
  }
}

const formatDuration = (ms?: number): string => {
  if (ms === undefined || ms === null) return '-'
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${ms} ms`
}

const prettyParams = computed((): string => {
  const raw = current.value?.params
  if (!raw) return '-'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    // 被后端截断到 2000 字符的 JSON 可能不完整，原样展示
    return raw
  }
})

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.operation-log {
  .search-form {
    margin-bottom: 16px;
  }

  .muted {
    color: #909399;
    font-size: 12px;
    margin-left: 4px;
  }

  .raw-block {
    margin: 0;
    max-height: 320px;
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
