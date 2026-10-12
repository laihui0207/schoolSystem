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
        <!-- ponytail: 家长端——孩子下拉来自 /parents/me/children；后端 listScores 对家长强制 studentId ∈ childStudentIds 且只看已发布 -->
        <el-form-item v-if="isParent" label="孩子">
          <el-select v-model="query.studentId" placeholder="请选择孩子" style="width:180px" @change="handleSearch">
            <el-option v-for="c in children" :key="c.studentId" :label="`${c.name ?? ''}（${c.studentNo ?? ''}）`" :value="c.studentId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isStudent && !isParent" label="班级">
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
        <el-form-item v-if="!isStudent && !isParent" label="学号">
          <el-input v-model="query.studentNo" placeholder="学号" clearable style="width:130px" />
        </el-form-item>
        <el-form-item v-if="!isStudent && !isParent" label="姓名">
          <el-input v-model="query.studentName" placeholder="姓名" clearable style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <!-- ponytail: F-01 / 契约 X — 学生无导出权限（后端 /export/scores 对学生 FORBIDDEN），按钮一并隐藏 -->
          <el-button v-if="!isStudent && !isParent" type="success" :loading="exporting" :disabled="!query.examId" @click="handleExport">导出 Excel</el-button>
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
        <!-- ponytail: 成绩单 PDF——学生只对本人行可见，管理员/教师对每行可见；未发布/越权由后端拒绝并提示 -->
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canDownloadPdf(row)"
              link
              type="primary"
              size="small"
              :loading="pdfLoadingId === row.id"
              @click="handleScorePdf(row)"
            >成绩单 PDF</el-button>
          </template>
        </el-table-column>
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
import { exportScores, downloadScoreReportPdf } from '@/api/export'
import { getSchoolList } from '@/api/school'
import { useUserStore } from '@/stores/user'
import { useRoute } from 'vue-router'
import { getMyChildren, type ChildBrief } from '@/api/parent'
import { handleError } from '@/utils/errorHandler'

const userStore = useUserStore()
const isSuperAdmin = userStore.userInfo?.userType === 1
const isStudent = userStore.userInfo?.userType === 4
// ponytail: userType 5 = PARENT；家长查成绩必须带孩子的 studentId，后端 listScores 强制 ∈ childStudentIds 且只看已发布
const isParent = userStore.userInfo?.userType === 5
const route = useRoute()
const children = ref<ChildBrief[]>([])

const loading = ref(false)
const list = ref<ScoreResponse[]>([])
const total = ref(0)
const exams = ref<any[]>([])
const subjects = ref<any[]>([])
const classes = ref<any[]>([])
const schools = ref<any[]>([])

const query = reactive({
  schoolId: isSuperAdmin ? undefined as number | undefined : userStore.userInfo?.schoolId,
  studentId: undefined as number | undefined,
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
  if (isParent && !query.studentId) {
    list.value = []
    total.value = 0
    ElMessage.warning(children.value.length ? '请先选择孩子' : '当前账号尚未绑定孩子，请联系学校管理员')
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
    // 失败提示已由 request 拦截器 / api/export.ts 的 ensureBinary 给出后端原文，这里不再重复弹「导出失败」
  } finally {
    exporting.value = false
  }
}

const pdfLoadingId = ref<number | null>(null)

// ponytail: el-table 插槽里的 row 是 DefaultRow（索引签名）而非 ScoreResponse，参数用 Partial 接收并显式判空，避免新增 vue-tsc 错误
const canDownloadPdf = (row: Partial<ScoreResponse>): boolean => {
  if (!row.examId || !row.studentId) return false
  if (isStudent) return row.studentId === userStore.userInfo?.studentId
  if (isParent) return children.value.some(c => c.studentId === row.studentId)
  return true
}

const handleScorePdf = async (row: Partial<ScoreResponse>) => {
  if (!row.examId || !row.studentId) return
  pdfLoadingId.value = row.id ?? null
  try {
    await downloadScoreReportPdf(row.examId, row.studentId, row.studentNo)
  } catch {
    // 后端业务错误（成绩未发布 / 越权）已在 ensureBinary 中提示
  } finally {
    pdfLoadingId.value = null
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
  if (isParent) {
    // ponytail: 「我的孩子」页带 ?studentId= 跳转过来时默认选中该孩子，否则选第一个
    try {
      children.value = await getMyChildren()
    } catch (error) {
      handleError(error, '加载孩子列表失败')
    }
    const fromRoute = Number(route.query.studentId)
    const preferred = children.value.find(c => c.studentId === fromRoute) ?? children.value[0]
    query.studentId = preferred?.studentId
  }
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
