<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="760px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="roster-import">
      <el-alert type="info" :closable="false" show-icon class="tips">
        <template #title>
          先下载模板按列填写再上传；任一行校验失败则整批不导入。
          「是否开通账号」为「是」的行会创建登录账号，用户名为学号/工号，初始密码 {{ DEFAULT_PASSWORD }}。
        </template>
      </el-alert>

      <div class="toolbar">
        <el-button :icon="Download" :loading="downloading" @click="handleDownloadTemplate">下载模板</el-button>
        <!-- ponytail: 超管无归属学校，后端 resolveSchoolId 要求显式指定；校管理员由后端取当前学校，不显示选择器 -->
        <el-select
          v-if="isSuperAdmin"
          v-model="schoolId"
          placeholder="选择目标学校（必选）"
          clearable
          style="width: 240px"
        >
          <el-option v-for="s in schools" :key="s.id" :label="s.schoolName" :value="s.id!" />
        </el-select>
      </div>

      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept=".xlsx,.xls"
        :disabled="importing"
        :on-change="handleFileChange"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将 Excel 文件拖到此处，或<em>点击选择</em></div>
        <div class="upload-tip">仅支持 .xlsx / .xls，不超过 10MB</div>
      </el-upload>

      <div v-if="file" class="file-info">
        <el-icon><Document /></el-icon>
        <span class="file-name">{{ file.name }}</span>
        <span class="file-size">{{ formatFileSize(file.size) }}</span>
        <el-button link type="danger" :disabled="importing" @click="handleRemoveFile">移除</el-button>
      </div>

      <template v-if="result">
        <el-alert
          v-if="result.failCount > 0"
          type="error"
          :closable="false"
          show-icon
          :title="`校验未通过：${result.failCount} 行有误，本次未导入任何数据`"
        />
        <el-alert
          v-else
          type="success"
          :closable="false"
          show-icon
          :title="`导入成功：共 ${result.successCount} 条`"
        />

        <el-alert
          v-for="(w, i) in result.warnings"
          :key="i"
          type="warning"
          :closable="false"
          :title="w"
          class="warning-item"
        />

        <el-table
          v-if="result.failures.length"
          :data="result.failures"
          border
          size="small"
          max-height="260"
          class="result-table"
        >
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column label="学号/工号" width="150">
            <template #default="{ row }">{{ failureNo(row as RosterImportFailure) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="失败原因" min-width="220" />
        </el-table>

        <template v-if="result.createdAccounts.length">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            class="warning-item"
            :title="`已开通 ${result.createdAccounts.length} 个登录账号，初始密码均为 ${DEFAULT_PASSWORD}，请提醒首次登录后修改`"
          />
          <el-table :data="result.createdAccounts" border size="small" max-height="220" class="result-table">
            <el-table-column prop="row" label="行号" width="70" />
            <el-table-column prop="username" label="用户名" width="160" />
            <el-table-column prop="realName" label="姓名" width="120" />
            <el-table-column prop="initialPassword" label="初始密码" />
          </el-table>
        </template>
      </template>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button
        type="primary"
        :loading="importing"
        :disabled="!file || (isSuperAdmin && !schoolId)"
        @click="handleImport"
      >
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { Download, Document, UploadFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { saveBlobAsFile, type RosterImportResult, type RosterImportFailure } from '@/api/student'
import { handleError } from '@/utils/errorHandler'

/**
 * 通用名册导入对话框：学生/教师页面各自传入模板与导入 API。
 * 对应后端 RosterImportResult：任一行失败整批不落库，failCount > 0 时只展示失败明细。
 */
interface SchoolOption {
  id?: number
  schoolName: string
}

interface Props {
  modelValue: boolean
  title: string
  templateApi: () => Promise<Blob>
  importApi: (file: File, schoolId?: number) => Promise<RosterImportResult>
  templateFileName?: string
  /** 超管选学校用；校管理员可不传 */
  schools?: SchoolOption[]
  /** 打开时预选的学校（一般取搜索栏当前选中） */
  defaultSchoolId?: number
}

const props = withDefaults(defineProps<Props>(), {
  templateFileName: '导入模板.xlsx',
  schools: () => [],
  defaultSchoolId: undefined
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 整批导入成功后触发，父页面据此刷新列表 */
  success: [result: RosterImportResult]
}>()

const DEFAULT_PASSWORD = '123456'
const MAX_FILE_SIZE = 10 * 1024 * 1024

const userStore = useUserStore()
const isSuperAdmin = computed(() => userStore.userInfo?.userType === 1)

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const file = ref<File | null>(null)
const result = ref<RosterImportResult | null>(null)
const schoolId = ref<number | undefined>(props.defaultSchoolId)
const downloading = ref(false)
const importing = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (open) schoolId.value = props.defaultSchoolId
  }
)

const failureNo = (f: RosterImportFailure) => f.studentNo ?? f.teacherNo ?? '-'

const formatFileSize = (size: number): string => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(2)} MB`
}

const handleFileChange = (uploadFile: UploadFile) => {
  const raw = uploadFile.raw
  if (!raw) return
  if (raw.size > MAX_FILE_SIZE) {
    ElMessage.error('文件大小不能超过 10MB')
    return
  }
  const lower = raw.name.toLowerCase()
  if (!lower.endsWith('.xlsx') && !lower.endsWith('.xls')) {
    ElMessage.error('只支持 .xlsx 或 .xls 文件')
    return
  }
  file.value = raw
  result.value = null
}

const handleRemoveFile = () => {
  file.value = null
  result.value = null
}

const handleDownloadTemplate = async () => {
  downloading.value = true
  try {
    await saveBlobAsFile(await props.templateApi(), props.templateFileName)
  } catch (e) {
    handleError(e)
  } finally {
    downloading.value = false
  }
}

const handleImport = async () => {
  if (!file.value) return
  if (isSuperAdmin.value && !schoolId.value) {
    ElMessage.warning('请选择目标学校')
    return
  }
  importing.value = true
  try {
    const res = await props.importApi(file.value, isSuperAdmin.value ? schoolId.value : undefined)
    result.value = res
    if (res.failCount === 0) {
      ElMessage.success(`导入成功，共 ${res.successCount} 条`)
      emit('success', res)
    } else {
      ElMessage.warning(`校验未通过：${res.failCount} 行有误，请修正后重新上传`)
    }
  } catch {
    // ponytail: 后端业务错误（文件为空 / 行数超限 / 学号被占用整批回滚…）已由 request 拦截器弹出具体 message，这里不再覆盖
  } finally {
    importing.value = false
  }
}

const handleClose = () => {
  file.value = null
  result.value = null
  visible.value = false
}
</script>

<style scoped lang="scss">
.roster-import {
  .tips {
    margin-bottom: 12px;
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
  }

  .upload-icon {
    font-size: 48px;
    color: var(--el-text-color-secondary);
    margin-bottom: 8px;
  }

  .upload-text {
    color: var(--el-text-color-regular);

    em {
      color: var(--el-color-primary);
      font-style: normal;
    }
  }

  .upload-tip {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .file-info {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 12px;
    padding: 8px 12px;
    background: var(--el-fill-color-light);
    border-radius: 4px;

    .file-name {
      font-weight: 500;
    }

    .file-size {
      color: var(--el-text-color-secondary);
      font-size: 12px;
      flex: 1;
    }
  }

  .warning-item {
    margin-top: 8px;
  }

  .result-table {
    margin-top: 12px;
  }

  :deep(.el-upload-dragger) {
    padding: 24px;
  }
}
</style>
