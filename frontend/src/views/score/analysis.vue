<template>
  <div class="score-analysis">
    <el-card>
      <template #header>成绩分析</template>

      <el-form :model="filter" inline>
        <el-form-item label="考试">
          <el-select v-model="filter.examId" placeholder="请选择考试" clearable style="width:200px" @change="onExamChange">
            <el-option v-for="e in exams" :key="e.id" :label="e.examName" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="filter.classId" placeholder="请选择班级" clearable style="width:160px">
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目">
          <el-select v-model="filter.subjectId" placeholder="请选择科目" clearable style="width:140px">
            <el-option v-for="s in subjects" :key="s.id" :label="s.subjectName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!canQuery" @click="loadAnalysis">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="stats" class="stat-cards">
        <el-row :gutter="16">
          <el-col :span="4" v-for="item in statItems" :key="item.label">
            <el-card shadow="never" class="stat-card">
              <div class="stat-value">{{ item.value }}</div>
              <div class="stat-label">{{ item.label }}</div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <div v-if="distribution.length" class="dist-section">
        <h3>分数段分布</h3>
        <el-table :data="distribution" border style="width:400px">
          <el-table-column prop="range" label="分数段" />
          <el-table-column prop="count" label="人数" width="100" align="center" />
          <el-table-column label="占比" width="120" align="center">
            <template #default="{ row }">
              {{ totalStudents ? ((row.count / totalStudents) * 100).toFixed(1) + '%' : '-' }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-empty v-if="!stats && !loading" description="请选择考试、班级、科目后查询" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getScoreStatistics, getScoreDistribution } from '@/api/score'
import { getExamList } from '@/api/exam'
import { getAllSubjects } from '@/api/subject'
import { getClassList } from '@/api/class'

const loading = ref(false)
const exams = ref<any[]>([])
const subjects = ref<any[]>([])
const classes = ref<any[]>([])
const stats = ref<any>(null)
const distribution = ref<any[]>([])

const filter = reactive({
  examId: undefined as number | undefined,
  classId: undefined as number | undefined,
  subjectId: undefined as number | undefined
})

const canQuery = computed(() => filter.examId && filter.classId && filter.subjectId)

const totalStudents = computed(() => distribution.value.reduce((s, r) => s + r.count, 0))

const statItems = computed(() => stats.value ? [
  { label: '应考人数', value: stats.value.totalCount },
  { label: '已录入', value: stats.value.enteredCount },
  { label: '平均分', value: stats.value.averageScore?.toFixed(1) ?? '-' },
  { label: '最高分', value: stats.value.highestScore },
  { label: '最低分', value: stats.value.lowestScore },
  { label: '及格率', value: stats.value.passRate != null ? (stats.value.passRate * 100).toFixed(1) + '%' : '-' },
] : [])

const onExamChange = async (examId: number) => {
  filter.classId = undefined
  classes.value = []
  if (!examId) return
  const exam = exams.value.find(e => e.id === examId)
  if (!exam?.gradeIds?.length) return
  const res = await getClassList({ gradeId: exam.gradeIds[0], pageNum: 1, pageSize: 1000 }) as any
  classes.value = res?.records ?? []
}

const loadAnalysis = async () => {
  if (!canQuery.value) return
  loading.value = true
  stats.value = null
  distribution.value = []
  try {
    const [s, d] = await Promise.all([
      getScoreStatistics(filter.examId!, filter.classId!, filter.subjectId!),
      getScoreDistribution(filter.examId!, filter.classId!, filter.subjectId!)
    ])
    stats.value = s
    distribution.value = (d as any[]) ?? []
  } catch {
    ElMessage.error('加载失败，请重试')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  filter.examId = undefined
  filter.classId = undefined
  filter.subjectId = undefined
  classes.value = []
  stats.value = null
  distribution.value = []
}

onMounted(async () => {
  const [examRes, subjectRes] = await Promise.all([
    getExamList({ pageNum: 1, pageSize: 1000 }) as any,
    getAllSubjects()
  ])
  exams.value = examRes?.records ?? []
  subjects.value = subjectRes ?? []
})
</script>

<style scoped lang="scss">
.score-analysis {
  .stat-cards {
    margin: 20px 0;
    .stat-card {
      text-align: center;
      padding: 8px 0;
      .stat-value {
        font-size: 24px;
        font-weight: bold;
        color: #409eff;
      }
      .stat-label {
        font-size: 13px;
        color: #909399;
        margin-top: 4px;
      }
    }
  }
  .dist-section {
    h3 {
      font-size: 15px;
      font-weight: bold;
      margin-bottom: 12px;
    }
  }
}
</style>
