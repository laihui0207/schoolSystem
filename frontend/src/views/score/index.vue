<template>
  <div class="score-input">
    <el-card>
      <!-- 步骤一：选择考试 + 班级 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="选择考试">
          <el-select
            v-model="selectedExamId"
            placeholder="请选择录入中的考试"
            style="width: 260px"
            clearable
            @change="onExamChange"
          >
            <el-option
              v-for="exam in examList"
              :key="exam.id"
              :label="`${exam.examName}（${exam.termName ?? exam.semester ?? ''}）`"
              :value="exam.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选择班级">
          <el-select
            v-model="selectedClassId"
            placeholder="请先选择考试"
            style="width="200px""
            clearable
            :disabled="!selectedExamId"
            @change="onClassChange"
          >
            <el-option
              v-for="cls in classList"
              :key="cls.id"
              :label="cls.className"
              :value="cls.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="tableLoading"
            :disabled="!selectedExamId || !selectedClassId"
            @click="loadScores"
          >
            加载成绩
          </el-button>
          <el-upload
            :show-file-list="false"
            accept=".xlsx,.xls"
            :before-upload="handleImport"
          >
            <el-button type="warning" :loading="importLoading" :disabled="!selectedExamId || !selectedClassId">导入成绩</el-button>
          </el-upload>
        </el-form-item>
      </el-form>

      <!-- 步骤三：成绩录入表格 -->
      <template v-if="scoreRows.length > 0">
        <el-table
          v-loading="tableLoading"
          :data="scoreRows"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column prop="studentNo" label="学号" width="120" />
          <el-table-column prop="studentName" label="姓名" width="100" />
          <el-table-column
            v-for="subject in subjects"
            :key="subject.subjectId"
            :label="subject.subjectName"
            min-width="120"
          >
            <template #default="{ row }">
              <el-input
                v-model.number="row.scores[subject.subjectId]"
                type="number"
                min="0"
                max="150"
                placeholder="0-150"
                style="width: 100%"
              />
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top: 16px; text-align: right">
          <el-button
            type="primary"
            :loading="saveLoading"
            @click="handleBatchSave"
          >
            批量保存
          </el-button>
        </div>
      </template>

      <el-empty v-else-if="loaded && scoreRows.length === 0" description="暂无学生数据" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getExamList } from '@/api/exam'
import { getClassList } from '@/api/class'
import { getClassScoreList, batchInputScore } from '@/api/score'
import { importScores } from '@/api/export'

const importLoading = ref(false)

const handleImport = async (file: File) => {
  if (!selectedExamId.value || !selectedClassId.value) return false
  importLoading.value = true
  try {
    // ponytail: schoolId hardcoded to 1 — pass real schoolId when multi-school support lands
    const count = await importScores(
      { examId: selectedExamId.value, classId: selectedClassId.value, schoolId: 1 },
      file
    )
    ElMessage.success(`导入成功，共 ${count} 条`)
    loadScores()
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
  return false // prevent el-upload auto-post
}

// ---- state ----
const examList = ref<any[]>([])
const classList = ref<any[]>([])
const selectedExamId = ref<number | undefined>(undefined)
const selectedClassId = ref<number | undefined>(undefined)

// Each row: { studentId, studentNo, studentName, scores: { [subjectId]: number } }
const scoreRows = ref<any[]>([])
// Columns derived from first loaded row
const subjects = ref<{ subjectId: number; subjectName: string }[]>([])

const tableLoading = ref(false)
const saveLoading = ref(false)
const loaded = ref(false)

// ---- init ----
onMounted(async () => {
  try {
    const res: any = await getExamList({ status: 1, pageNum: 1, pageSize: 100 })
    examList.value = res?.records ?? res ?? []
  } catch {
    ElMessage.error('考试列表加载失败')
  }

  try {
    const res: any = await getClassList({ pageNum: 1, pageSize: 1000 })
    classList.value = res?.records ?? []
  } catch {
    ElMessage.error('班级列表加载失败')
  }
})

// ---- handlers ----
function onExamChange() {
  selectedClassId.value = undefined
  scoreRows.value = []
  subjects.value = []
  loaded.value = false
}

function onClassChange() {
  scoreRows.value = []
  subjects.value = []
  loaded.value = false
}

async function loadScores() {
  if (!selectedExamId.value || !selectedClassId.value) return
  tableLoading.value = true
  loaded.value = false
  try {
    const res: any = await getClassScoreList(selectedExamId.value, selectedClassId.value)
    const list: any[] = Array.isArray(res) ? res : res?.records ?? res?.list ?? []

    if (list.length === 0) {
      scoreRows.value = []
      subjects.value = []
      loaded.value = true
      return
    }

    // Derive subject columns from first entry's subjectScores array (or scores map)
    // ponytail: backend shape unknown at design time; handle both array and map forms
    const first = list[0]
    if (Array.isArray(first.subjectScores)) {
      subjects.value = first.subjectScores.map((s: any) => ({
        subjectId: s.subjectId,
        subjectName: s.subjectName
      }))
      scoreRows.value = list.map((item: any) => {
        const scores: Record<number, number | null> = {}
        item.subjectScores.forEach((s: any) => {
          scores[s.subjectId] = s.score ?? null
        })
        return { studentId: item.studentId, studentNo: item.studentNo, studentName: item.studentName, scores }
      })
    } else {
      // fallback: treat each key beyond known fields as a subject name
      scoreRows.value = list
      loaded.value = true
    }
    loaded.value = true
  } catch {
    ElMessage.error('成绩加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function handleBatchSave() {
  if (!selectedExamId.value || !selectedClassId.value) return
  saveLoading.value = true
  try {
    const scores = scoreRows.value.flatMap((row: any) =>
      subjects.value
        .filter(s => row.scores[s.subjectId] !== null && row.scores[s.subjectId] !== undefined && row.scores[s.subjectId] !== '')
        .map(s => ({
          examId: selectedExamId.value,
          classId: selectedClassId.value,
          studentId: row.studentId,
          subjectId: s.subjectId,
          score: Number(row.scores[s.subjectId])
        }))
    )
    await batchInputScore({ scores })
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败，请重试')
  } finally {
    saveLoading.value = false
  }
}
</script>

<style scoped lang="scss">
.score-input {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
