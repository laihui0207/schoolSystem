<template>
  <div class="score-query">
    <el-card>
      <template #header>成绩查询</template>

      <el-form :model="query" inline>
        <el-form-item v-if="isSuperAdmin" label="学校">
          <el-select v-model="query.schoolId" placeholder="请选择学校" clearable style="width:180px" @change="onSchoolChange">
            <el-option v-for="s in schools" :key="s.id" :label="s.schoolName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isStudent" label="班级">
          <el-select v-model="query.classId" placeholder="请选择班级" clearable style="width:160px" :disabled="isSuperAdmin && !query.schoolId">
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试">
          <el-select v-model="query.examId" placeholder="请选择考试" clearable style="width:200px">
            <el-option v-for="e in exams" :key="e.id" :label="e.examName" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目">
          <el-select v-model="query.subjectId" placeholder="请选择科目" clearable style="width:140px">
            <el-option v-for="s in subjects" :key="s.id" :label="s.subjectName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isStudent" label="学号">
          <el-input v-model="query.studentNo" placeholder="学号" clearable style="width:130px" />
        </el-form-item>
        <el-form-item v-if="!isStudent" label="姓名">
          <el-input v-model="query.studentName" placeholder="姓名" clearable style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <!-- ponytail: F-01 / 契约 X — 学生无导出权限（后端 /export/scores 对学生 FORBIDDEN），按钮一并隐藏 -->
          <el-button v-if="!isStudent" type="success" :loading="exporting" :disabled="!query.examId" @click="handleExport">导出 Excel</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="examName" label="考试" min-width="140" />
        <el-table-column prop="className" label="班级" width="120" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="studentName" label="姓名" width="100" />
        <el-table-column prop="subjectName" label="科目" width="100" />
        <el-table-column prop="score" label="得分" width="80" align="center" />
        <el-table-column prop="fullScore" label="满分" width="80" align="center" />
        <el-table-column label="缺考" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isAbsent ? 'danger' : 'success'" size="small">
              {{ row.isAbsent ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- ponytail: 契约 R — rankIn* 是该行科目的单科名次，totalRankIn* 是该生本场总分名次，列头分开标明 -->
        <el-table-column prop="rankInClass" label="科目班排" width="90" align="center" />
        <el-table-column prop="rankInGrade" label="科目年排" width="90" align="center" />
        <el-table-column prop="totalRankInClass" label="总分班排" width="90" align="center" />
        <el-table-column prop="totalRankInGrade" label="总分年排" width="90" align="center" />
        <el-table-column prop="statusName" label="状态" width="90" align="center" />
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getScoreList, type ScoreResponse } from '@/api/score'
import { getExamList } from '@/api/exam'
import { getSubjectList } from '@/api/subject'
import { getClassList } from '@/api/class'
import { exportScores } from '@/api/export'
import { getSchoolList } from '@/api/school'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isSuperAdmin = userStore.userInfo?.userType === 1
const isStudent = userStore.userInfo?.userType === 4

const loading = ref(false)
const list = ref<ScoreResponse[]>([])
const total = ref(0)
const exams = ref<any[]>([])
const subjects = ref<any[]>([])
const classes = ref<any[]>([])
const schools = ref<any[]>([])

const query = reactive({
  schoolId: isSuperAdmin ? undefined as number | undefined : userStore.userInfo?.schoolId,
  examId: undefined as number | undefined,
  classId: undefined as number | undefined,
  subjectId: undefined as number | undefined,
  studentNo: '',
  studentName: '',
  pageNum: 1,
  pageSize: 10
})

const loadData = async () => {
  // ponytail: 契约 2 — 学生自查用 userInfo.studentId（学生档案 id）而非 userInfo.id（用户 id）；未关联档案时不发请求
  const studentId = userStore.userInfo?.studentId ?? undefined
  if (isStudent && !studentId) {
    list.value = []
    total.value = 0
    ElMessage.warning('当前账号未关联学生档案')
    return
  }

  loading.value = true
  try {
    // ponytail: student role — backend also enforces this, frontend mirrors for clarity
    const params = isStudent
      ? { ...query, studentId }
      : query
    const res = await getScoreList(params)
    list.value = res?.records ?? []
    total.value = res?.total ?? 0
  } finally {
    loading.value = false
  }
}

const onSchoolChange = async (schoolId: number) => {
  query.classId = undefined
  query.subjectId = undefined
  query.examId = undefined
  classes.value = []
  subjects.value = []
  exams.value = []
  if (!schoolId) return
  const [classRes, subjectRes, examRes] = await Promise.all([
    getClassList({ schoolId, pageNum: 1, pageSize: 1000 }) as any,
    getSubjectList({ schoolId, pageNum: 1, pageSize: 1000 }) as any,
    getExamList({ schoolId, pageNum: 1, pageSize: 1000 }) as any
  ])
  classes.value = classRes?.records ?? []
  subjects.value = subjectRes?.records ?? []
  exams.value = examRes?.records ?? []
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const exporting = ref(false)

const handleExport = async () => {
  if (!query.examId) return
  exporting.value = true
  try {
    await exportScores({ examId: query.examId, classId: query.classId })
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

const handleReset = () => {
  query.examId = undefined
  query.classId = undefined
  query.subjectId = undefined
  query.studentNo = ''
  query.studentName = ''
  if (isSuperAdmin) query.schoolId = undefined
  query.pageNum = 1
  loadData()
}

onMounted(async () => {
  const schoolId = userStore.userInfo?.schoolId
  if (isSuperAdmin) {
    const schoolRes = await getSchoolList({ pageNum: 1, pageSize: 1000 }) as any
    schools.value = schoolRes?.records ?? []
  } else if (schoolId) {
    const [classRes, subjectRes, examRes] = await Promise.all([
      getClassList({ schoolId, pageNum: 1, pageSize: 1000 }) as any,
      getSubjectList({ schoolId, pageNum: 1, pageSize: 1000 }) as any,
      getExamList({ schoolId, pageNum: 1, pageSize: 1000 }) as any
    ])
    classes.value = classRes?.records ?? []
    subjects.value = subjectRes?.records ?? []
    exams.value = examRes?.records ?? []
  }
  loadData()
})
</script>

<style scoped lang="scss">
.score-query {
  .pagination {
    margin-top: 16px;
    justify-content: flex-end;
  }
}
</style>
