<template>
  <div class="score-query">
    <el-card>
      <template #header>成绩查询</template>

      <el-form :model="query" inline>
        <el-form-item label="考试">
          <el-select v-model="query.examId" placeholder="请选择考试" clearable style="width:200px" @change="onExamChange">
            <el-option v-for="e in exams" :key="e.id" :label="e.examName" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="query.classId" placeholder="请选择班级" clearable style="width:160px">
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目">
          <el-select v-model="query.subjectId" placeholder="请选择科目" clearable style="width:140px">
            <el-option v-for="s in subjects" :key="s.id" :label="s.subjectName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学号">
          <el-input v-model="query.studentNo" placeholder="学号" clearable style="width:130px" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="query.studentName" placeholder="姓名" clearable style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
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
        <el-table-column prop="rankInClass" label="班级排名" width="90" align="center" />
        <el-table-column prop="rankInGrade" label="年级排名" width="90" align="center" />
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
import { getScoreList } from '@/api/score'
import { getExamList } from '@/api/exam'
import { getAllSubjects } from '@/api/subject'
import { getClassList } from '@/api/class'

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const exams = ref<any[]>([])
const subjects = ref<any[]>([])
const classes = ref<any[]>([])

const query = reactive({
  examId: undefined as number | undefined,
  classId: undefined as number | undefined,
  subjectId: undefined as number | undefined,
  studentNo: '',
  studentName: '',
  pageNum: 1,
  pageSize: 10
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getScoreList(query) as any
    list.value = res?.records ?? []
    total.value = res?.total ?? 0
  } finally {
    loading.value = false
  }
}

const onExamChange = async (examId: number) => {
  query.classId = undefined
  if (!examId) return
  const exam = exams.value.find(e => e.id === examId)
  if (!exam?.gradeIds?.length) return
  const res = await getClassList({ gradeId: exam.gradeIds[0], pageNum: 1, pageSize: 1000 }) as any
  classes.value = res?.records ?? []
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.examId = undefined
  query.classId = undefined
  query.subjectId = undefined
  query.studentNo = ''
  query.studentName = ''
  query.pageNum = 1
  loadData()
}

onMounted(async () => {
  const [examRes, subjectRes] = await Promise.all([
    getExamList({ pageNum: 1, pageSize: 1000 }) as any,
    getAllSubjects()
  ])
  exams.value = examRes?.records ?? []
  subjects.value = subjectRes ?? []
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
