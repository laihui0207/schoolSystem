<template>
  <div class="backup-page">
    <el-card>
      <el-alert type="info" :closable="false" show-icon class="tips">
        <template #title>
          备份策略由后端 app.backup.mode 决定（auto 依次探测 pg_dump → docker → 纯 SQL）。
          只有本系统生成的 .sql.gz 可以在线恢复；pg_dump 的 .dump 产物需用 pg_restore 手工恢复。
        </template>
      </el-alert>

      <!-- 工具栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="进行中" :value="0" />
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.backupType" placeholder="全部" clearable style="width: 120px">
            <el-option label="手动" :value="1" />
            <el-option label="定时" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="toolbar-right">
          <el-button type="success" :icon="FolderChecked" :loading="triggering" @click="handleBackupNow">
            立即备份
          </el-button>
          <el-button :icon="Upload" @click="uploadVisible = true">上传备份</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="fileName" label="文件" min-width="240" show-overflow-tooltip />
        <el-table-column prop="fileSizeText" label="大小" width="100" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.backupType === 2 ? 'warning' : 'info'" size="small">{{ row.backupTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="backupMode" label="模式" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggeredBy" label="触发人" width="110" show-overflow-tooltip />
        <el-table-column prop="startedAt" label="开始时间" width="170" />
        <el-table-column prop="finishedAt" label="完成时间" width="170" />
        <el-table-column prop="message" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status !== 1"
              @click="handleDownload(row as BackupRecord)"
            >
              下载
            </el-button>
            <el-button
              link
              type="warning"
              :disabled="!row.restorable || restoring"
              @click="handleRestore(row as BackupRecord)"
            >
              恢复
            </el-button>
            <el-button
              link
              type="danger"
              :disabled="row.status === 0"
              @click="handleDelete(row as BackupRecord)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传备份文件" width="520px" :close-on-click-modal="false" @close="resetUpload">
      <el-alert type="warning" :closable="false" show-icon class="tips">
        <template #title>仅接受本系统生成的 .sql.gz（首行标记 "-- school-system sql backup"），上传后即可用于在线恢复。</template>
      </el-alert>
      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept=".gz"
        :disabled="uploading"
        :on-change="handleFileChange"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将 .sql.gz 文件拖到此处，或<em>点击选择</em></div>
      </el-upload>
      <div v-if="uploadFile" class="file-info">
        <el-icon><Document /></el-icon>
        <span class="file-name">{{ uploadFile.name }}</span>
        <span class="file-size">{{ formatSize(uploadFile.size) }}</span>
      </div>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!uploadFile" @click="handleUpload">开始上传</el-button>
      </template>
    </el-dialog>

    <!-- 恢复结果 -->
    <el-dialog v-model="resultVisible" title="恢复完成" width="560px">
      <el-descriptions v-if="restoreResult" :column="1" border>
        <el-descriptions-item label="备份 ID">{{ restoreResult.backupId }}</el-descriptions-item>
        <el-descriptions-item label="恢复前自动备份 ID">{{ restoreResult.preRestoreBackupId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行语句数">{{ restoreResult.executedStatements }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ formatDuration(restoreResult.elapsedMs) }}</el-descriptions-item>
        <el-descriptions-item label="已恢复的表">
          <span class="table-list">{{ restoreResult.restoredTables.join(', ') || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="跳过的表">
          <span class="table-list">{{ restoreResult.skippedTables.join(', ') || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { Search, Refresh, FolderChecked, Upload, UploadFilled, Document } from '@element-plus/icons-vue'
import {
  getBackupList,
  triggerBackup,
  downloadBackup,
  deleteBackup,
  restoreBackup,
  uploadBackup,
  RESTORE_CONFIRM_WORD,
  type BackupRecord,
  type BackupQuery,
  type BackupRestoreResult
} from '@/api/backup'
import { handleError } from '@/utils/errorHandler'

const loading = ref(false)
const triggering = ref(false)
const restoring = ref(false)
const uploading = ref(false)
const uploadVisible = ref(false)
const resultVisible = ref(false)
const tableData = ref<BackupRecord[]>([])
const uploadFile = ref<File | null>(null)
const restoreResult = ref<BackupRestoreResult | null>(null)

const searchForm = reactive<{ status?: number; backupType?: number }>({
  status: undefined,
  backupType: undefined
})

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

/** 有「进行中」记录时每 3 秒刷新一次，直到全部结束 */
const POLL_INTERVAL_MS = 3000
let pollTimer: ReturnType<typeof setTimeout> | null = null

const buildQuery = (): BackupQuery => ({
  pageNum: pagination.pageNum,
  pageSize: pagination.pageSize,
  status: searchForm.status,
  backupType: searchForm.backupType
})

const stopPolling = () => {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

const schedulePollIfRunning = () => {
  stopPolling()
  // ponytail: 手动备份是异步的，接口只返回 status=0；列表里还有进行中的就轮询，没有就停，避免常驻定时器
  if (tableData.value.some(r => r.status === 0)) {
    pollTimer = setTimeout(() => loadData(), POLL_INTERVAL_MS)
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const page = await getBackupList(buildQuery())
    tableData.value = page.records ?? []
    pagination.total = page.total ?? 0
    schedulePollIfRunning()
  } catch (error) {
    handleError(error, '加载备份记录失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.status = undefined
  searchForm.backupType = undefined
  handleSearch()
}

const handleBackupNow = async () => {
  triggering.value = true
  try {
    const record = await triggerBackup()
    ElMessage.success(`已开始备份（记录 #${record.id}），完成后列表会自动刷新`)
    handleSearch()
  } catch (error) {
    handleError(error, '触发备份失败')
  } finally {
    triggering.value = false
  }
}

const handleDownload = async (row: BackupRecord) => {
  try {
    await downloadBackup(row.id, row.fileName)
  } catch (error) {
    handleError(error, '下载失败')
  }
}

const handleDelete = async (row: BackupRecord) => {
  try {
    await ElMessageBox.confirm(`确定删除备份「${row.fileName}」？文件与记录都会被删除。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteBackup(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch (error) {
    handleError(error, '删除失败')
  }
}

const handleRestore = async (row: BackupRecord) => {
  // ponytail: 恢复会 TRUNCATE 全部业务表，二次确认要求手动输入 RESTORE，与后端 confirm 校验一致
  try {
    await ElMessageBox.prompt(
      `即将用备份「${row.fileName}」覆盖当前全部业务数据（操作日志、备份记录除外），恢复前会自动再做一次备份。` +
        `请输入 ${RESTORE_CONFIRM_WORD} 确认：`,
      '危险操作：恢复数据库',
      {
        type: 'error',
        confirmButtonText: '确认恢复',
        cancelButtonText: '取消',
        inputPattern: new RegExp(`^${RESTORE_CONFIRM_WORD}$`),
        inputErrorMessage: `必须输入 ${RESTORE_CONFIRM_WORD}`
      }
    )
  } catch {
    return
  }
  restoring.value = true
  try {
    restoreResult.value = await restoreBackup(row.id)
    resultVisible.value = true
    ElMessage.success('恢复完成')
    loadData()
  } catch (error) {
    handleError(error, '恢复失败')
  } finally {
    restoring.value = false
  }
}

const handleFileChange = (file: UploadFile) => {
  uploadFile.value = file.raw ?? null
}

const resetUpload = () => {
  uploadFile.value = null
}

const handleUpload = async () => {
  if (!uploadFile.value) return
  uploading.value = true
  try {
    const record = await uploadBackup(uploadFile.value)
    ElMessage.success(`上传成功（记录 #${record.id}）`)
    uploadVisible.value = false
    handleSearch()
  } catch (error) {
    handleError(error, '上传失败')
  } finally {
    uploading.value = false
  }
}

const statusTagType = (status?: number): 'success' | 'danger' | 'info' => {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const formatDuration = (ms?: number): string => {
  if (ms === undefined || ms === null) return '-'
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${ms} ms`
}

onMounted(() => {
  loadData()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped lang="scss">
.backup-page {
  .tips {
    margin-bottom: 16px;
  }

  .search-form {
    margin-bottom: 16px;
  }

  .toolbar-right {
    float: right;
    margin-right: 0;
  }

  .upload-icon {
    font-size: 48px;
    color: #c0c4cc;
  }

  .upload-text em {
    color: #409eff;
    font-style: normal;
  }

  .file-info {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 12px;
    color: #606266;

    .file-size {
      color: #909399;
      font-size: 12px;
    }
  }

  .table-list {
    word-break: break-all;
    font-size: 12px;
  }
}
</style>
