<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入Excel成绩"
    width="700px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="import-container">
      <!-- 上传区域 -->
      <el-upload
        v-show="!uploadedFile"
        class="upload-area"
        drag
        :auto-upload="false"
        :show-file-list="false"
        :accept="'.xlsx,.xls'"
        :on-change="handleFileChange"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将Excel文件拖到此处，或<em>点击上传</em></div>
        <div class="upload-tip">仅支持 .xlsx 或 .xls 格式，文件大小不超过5MB</div>
      </el-upload>

      <!-- 文件信息 -->
      <div v-if="uploadedFile" class="file-info">
        <div class="file-name">
          <el-icon><Document /></el-icon>
          <span>{{ uploadedFile.name }}</span>
          <el-button link type="danger" @click="handleRemoveFile">删除</el-button>
        </div>
        <div class="file-size">文件大小: {{ formatFileSize(uploadedFile.size) }}</div>
      </div>

      <!-- 解析预览 -->
      <div v-if="previewData.length > 0" class="preview-area">
        <div class="preview-header">
          <span>数据预览 (共 {{ previewData.length }} 条)</span>
          <el-tag v-if="validCount > 0" type="success">有效: {{ validCount }}</el-tag>
          <el-tag v-if="errorCount > 0" type="danger">错误: {{ errorCount }}</el-tag>
        </div>

        <el-table :data="previewData" border max-height="300" size="small">
          <el-table-column type="index" label="行号" width="60" />
          <el-table-column prop="studentNo" label="学号" width="120" />
          <el-table-column prop="studentName" label="姓名" width="100" />
          <el-table-column prop="score" label="分数" width="80">
            <template #default="{ row }">
              <span :class="{ 'error-text': row.error }">
                {{ row.score ?? '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="isAbsent" label="缺考" width="80">
            <template #default="{ row }">
              {{ row.isAbsent ? '是' : '否' }}
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="!row.error" type="success" size="small">正常</el-tag>
              <el-tooltip v-else :content="row.error" placement="top">
                <el-tag type="danger" size="small">错误</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 错误列表 -->
      <div v-if="errors.length > 0" class="error-list">
        <div class="error-header">
          <el-icon color="#f56c6c"><WarningFilled /></el-icon>
          <span>数据校验错误</span>
        </div>
        <ul>
          <li v-for="(error, index) in errors.slice(0, 10)" :key="index">
            {{ error }}
          </li>
        </ul>
        <div v-if="errors.length > 10" class="more-errors">
          还有 {{ errors.length - 10 }} 条错误...
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button v-if="uploadedFile && !previewData.length" type="primary" @click="handleParse">
        解析数据
      </el-button>
      <el-button
        v-if="previewData.length > 0"
        type="primary"
        :loading="importing"
        :disabled="errorCount > 0"
        @click="handleImport"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Document, WarningFilled } from '@element-plus/icons-vue'
import type { UploadFile } from 'element-plus'
import { importScores, type BatchInputResult } from '@/api/score'
import { handleError } from '@/utils/errorHandler'
import { notifyBatchInputResult } from '@/utils/batchResult'
import * as XLSX from 'xlsx'

interface Props {
  visible: boolean
  examId?: number
  classId?: number
  subjectId?: number
  fullScore?: number
}

interface PreviewItem {
  studentNo: string
  studentName: string
  score?: number
  isAbsent: boolean
  remark?: string
  error?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  /** 导入完成（可能部分失败），携带后端 BatchInputResult */
  success: [result: BatchInputResult]
}>()

const uploadedFile = ref<File | null>(null)
const previewData = ref<PreviewItem[]>([])
const errors = ref<string[]>([])
const importing = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const validCount = computed(() => {
  return previewData.value.filter(item => !item.error).length
})

const errorCount = computed(() => {
  return previewData.value.filter(item => item.error).length
})

const handleFileChange = (file: UploadFile) => {
  const rawFile = file.raw
  if (!rawFile) return

  if (rawFile.size > 5 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 5MB')
    return
  }

  const isExcel = rawFile.name.endsWith('.xlsx') || rawFile.name.endsWith('.xls')
  if (!isExcel) {
    ElMessage.error('只支持 .xlsx 或 .xls 格式')
    return
  }

  uploadedFile.value = rawFile
  previewData.value = []
  errors.value = []
}

const handleRemoveFile = () => {
  uploadedFile.value = null
  previewData.value = []
  errors.value = []
}

const formatFileSize = (size: number): string => {
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB'
  return (size / (1024 * 1024)).toFixed(2) + ' MB'
}

const handleParse = async () => {
  if (!uploadedFile.value) return

  try {
    const data = await readExcelFile(uploadedFile.value)
    validateAndPreview(data)
  } catch (error) {
    handleError(error, '解析文件失败')
  }
}

const readExcelFile = (file: File): Promise<unknown[][]> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data = e.target?.result
        const workbook = XLSX.read(data, { type: 'binary' })
        const firstSheet = workbook.Sheets[workbook.SheetNames[0]]
        const jsonData = XLSX.utils.sheet_to_json(firstSheet, { header: 1 }) as unknown[][]
        resolve(jsonData)
      } catch (error) {
        reject(error)
      }
    }
    reader.onerror = reject
    reader.readAsBinaryString(file)
  })
}

const validateAndPreview = (data: unknown[][]) => {
  const result: PreviewItem[] = []
  const errorList: string[] = []

  if (data.length < 2) {
    ElMessage.error('文件内容为空')
    return
  }

  const headers = data[0] as string[]
  const expectedHeaders = ['学号', '姓名', '分数', '缺考', '备注']

  const headerValid = expectedHeaders.every((header, index) =>
    headers[index]?.toString().trim() === header
  )

  if (!headerValid) {
    ElMessage.error('文件格式错误，请使用标准模板')
    return
  }

  // ponytail: 之前只校验前 10 行，第 11 行起的错误数据会被原样提交给后端；整行空白（Excel 尾部空行）直接跳过
  for (let i = 1; i < data.length; i++) {
    const row = data[i] as (string | number)[]
    if (!row || row.every(cell => cell === undefined || cell === null || cell.toString().trim() === '')) {
      continue
    }
    const item: PreviewItem = {
      studentNo: row[0]?.toString().trim() || '',
      studentName: row[1]?.toString().trim() || '',
      score: row[2] !== undefined && row[2] !== '' ? Number(row[2]) : undefined,
      isAbsent: row[3]?.toString().trim() === '是' || row[3]?.toString().trim() === '1',
      remark: row[4]?.toString().trim() || ''
    }

    if (!item.studentNo) {
      item.error = '学号不能为空'
      errorList.push(`第 ${i + 1} 行: 学号不能为空`)
    } else if (!item.studentName) {
      item.error = '姓名不能为空'
      errorList.push(`第 ${i + 1} 行: 姓名不能为空`)
    } else if (!item.isAbsent && (item.score === undefined || item.score === null)) {
      item.error = '分数不能为空（除非标记为缺考）'
      errorList.push(`第 ${i + 1} 行: 分数不能为空`)
    } else if (item.score !== undefined && Number.isNaN(item.score)) {
      // ponytail: F-09 — Number('缺考') 得 NaN，NaN 与任何比较都是 false，之前会被当合法分数放行并显示 NaN
      item.error = '分数必须是数字'
      errorList.push(`第 ${i + 1} 行: 分数必须是数字`)
    } else if (item.score !== undefined && (item.score < 0 || item.score > (props.fullScore ?? 100))) {
      item.error = `分数范围必须在 0-${props.fullScore ?? 100} 之间`
      errorList.push(`第 ${i + 1} 行: 分数范围错误`)
    }

    result.push(item)
  }

  previewData.value = result
  errors.value = errorList

  if (errorList.length === 0) {
    ElMessage.success('数据校验通过')
  } else {
    ElMessage.warning(`发现 ${errorList.length} 条错误，请修正后重新上传`)
  }
}

const handleImport = async () => {
  if (!uploadedFile.value || !props.examId || !props.classId || !props.subjectId) {
    ElMessage.error('参数缺失')
    return
  }

  if (errorCount.value > 0) {
    ElMessage.error('存在错误数据，无法导入')
    return
  }

  importing.value = true
  try {
    const result = await importScores(
      uploadedFile.value,
      props.examId,
      props.classId,
      props.subjectId
    )
    // ponytail: 契约 1 — 按 BatchInputResult 展示成功条数与逐条失败原因，不再一句「导入成功」带过
    notifyBatchInputResult(result, '导入')
    emit('success', result)
    handleClose()
  } catch (error) {
    handleError(error, '导入失败')
  } finally {
    importing.value = false
  }
}

const handleClose = () => {
  uploadedFile.value = null
  previewData.value = []
  errors.value = []
  emit('update:visible', false)
}

watch(() => props.visible, (newVal) => {
  if (!newVal) {
    handleClose()
  }
})
</script>

<style scoped lang="scss">
.import-container {
  .upload-area {
    border: 2px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: all 0.3s;

    &:hover {
      border-color: #409eff;
    }

    :deep(.el-upload) {
      width: 100%;
    }

    :deep(.el-upload-dragger) {
      width: 100%;
      height: 180px;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
    }

    .upload-icon {
      font-size: 67px;
      color: #c0c4cc;
      margin-bottom: 16px;
    }

    .upload-text {
      color: #606266;
      font-size: 14px;
      margin-bottom: 8px;

      em {
        color: #409eff;
        font-style: normal;
      }
    }

    .upload-tip {
      color: #909399;
      font-size: 12px;
    }
  }

  .file-info {
    padding: 15px;
    background-color: #f5f7fa;
    border-radius: 4px;
    margin-bottom: 15px;

    .file-name {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
      font-size: 14px;

      .el-icon {
        color: #409eff;
        font-size: 18px;
      }

      span {
        flex: 1;
        font-weight: 500;
      }
    }

    .file-size {
      font-size: 12px;
      color: #909399;
      margin-left: 26px;
    }
  }

  .preview-area {
    margin-bottom: 15px;

    .preview-header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 10px;
      font-size: 14px;
      font-weight: 500;
    }

    .error-text {
      color: #f56c6c;
    }
  }

  .error-list {
    padding: 15px;
    background-color: #fef0f0;
    border: 1px solid #fde2e2;
    border-radius: 4px;

    .error-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 10px;
      font-size: 14px;
      font-weight: 500;
      color: #f56c6c;
    }

    ul {
      margin: 0;
      padding-left: 20px;
      list-style: disc;

      li {
        font-size: 13px;
        color: #606266;
        margin-bottom: 5px;
      }
    }

    .more-errors {
      margin-top: 10px;
      font-size: 12px;
      color: #909399;
      text-align: center;
    }
  }
}
</style>
