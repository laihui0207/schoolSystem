<template>
  <div class="class-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="学校">
          <el-select
            v-model="searchForm.schoolId"
            placeholder="请选择学校"
            clearable
            @change="onSearchSchoolChange"
          >
            <el-option
              v-for="s in schoolOptions"
              :key="s.id"
              :label="s.schoolName"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="年级">
          <el-select
            v-model="searchForm.gradeId"
            placeholder="请选择年级"
            clearable
            :disabled="!searchForm.schoolId"
          >
            <el-option
              v-for="g in searchGradeOptions"
              :key="g.id"
              :label="g.gradeName"
              :value="g.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="schoolName" label="学校名称" min-width="140"
          :formatter="(row: Class) => schoolMap[row.schoolId!] || row.schoolName || '-'" />
        <el-table-column prop="gradeName" label="年级名称" width="120"
          :formatter="(row: Class) => gradeMap[row.gradeId!] || row.gradeName || '-'" />
        <el-table-column prop="className" label="班级名称" min-width="120" />
        <el-table-column prop="termName" label="学期" min-width="160"
          :formatter="(row: Class) => termMap[row.termId!] || row.termName || '-'" />
        <el-table-column prop="headTeacherName" label="班主任" width="120"
          :formatter="(row: Class) => teacherMap[row.headTeacherId!] || row.headTeacherName || '-'" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="学校" prop="schoolId">
          <el-select
            v-model="form.schoolId"
            placeholder="请选择学校"
            style="width: 100%"
            :disabled="!!form.id"
            @change="onFormSchoolChange"
          >
            <el-option
              v-for="s in schoolOptions"
              :key="s.id"
              :label="s.schoolName"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="年级" prop="gradeId">
          <el-select
            v-model="form.gradeId"
            placeholder="请先选择学校"
            style="width: 100%"
            :disabled="!!form.id || !form.schoolId"
          >
            <el-option
              v-for="g in formGradeOptions"
              :key="g.id"
              :label="g.gradeName"
              :value="g.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学期" prop="termId">
          <el-select
            v-model="form.termId"
            placeholder="请先选择学校"
            style="width: 100%"
            :disabled="!!form.id || !form.schoolId"
          >
            <el-option
              v-for="t in formTermOptions"
              :key="t.id"
              :label="termLabel(t)"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="form.className" placeholder="请输入班级名称" />
        </el-form-item>
        <el-form-item label="班级编码" prop="classCode">
          <el-input-number v-model="form.classCode" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="班主任">
          <el-select
            v-model="form.headTeacherId"
            placeholder="请选择班主任（可选）"
            clearable
            style="width: 100%"
            :disabled="!form.schoolId"
          >
            <el-option
              v-for="t in formTeacherOptions"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { getClassList, createClass, updateClass, deleteClass, type Class } from '@/api/class'
import { getSchoolList, type School } from '@/api/school'
import { getGradesBySchool, type Grade } from '@/api/grade'
import { getTermList, termLabel, type Term } from '@/api/term'
import { getTeacherList, type Teacher } from '@/api/teacher'

// ── state ──────────────────────────────────────────────────────────────────
const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const tableData = ref<Class[]>([])
const schoolOptions = ref<School[]>([])
const searchGradeOptions = ref<Grade[]>([])

// per-form dropdowns (loaded when school changes in dialog)
const formGradeOptions = ref<Grade[]>([])
const formTermOptions = ref<Term[]>([])
const formTeacherOptions = ref<Teacher[]>([])

const searchForm = reactive({ schoolId: undefined as number | undefined, gradeId: undefined as number | undefined })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const form = reactive<Class>({
  id: undefined,
  schoolId: undefined,
  gradeId: undefined,
  termId: undefined,
  className: '',
  classCode: undefined,
  headTeacherId: undefined
})

const rules: FormRules = {
  schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }],
  gradeId:  [{ required: true, message: '请选择年级', trigger: 'change' }],
  termId:   [{ required: true, message: '请选择学期', trigger: 'change' }],
  className:[{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  classCode:[{ required: true, message: '请输入班级编码', trigger: 'blur' }]
}

// ── lookup maps for table display (ponytail: backend returns null for name fields) ──
const schoolMap = computed(() =>
  Object.fromEntries(schoolOptions.value.map(s => [s.id!, s.schoolName]))
)
const gradeMap = ref<Record<number, string>>({})
const termMap = ref<Record<number, string>>({})
const teacherMap = ref<Record<number, string>>({})

// ── data loading ────────────────────────────────────────────────────────────
const loadData = async () => {
  loading.value = true
  try {
    const res = await getClassList({
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    tableData.value = res?.records ?? []
    pagination.total = res?.total ?? 0
    await buildDisplayMaps(res?.records ?? [])
  } catch {
    ElMessage.error('加载班级列表失败')
  } finally {
    loading.value = false
  }
}

/** Build gradeMap/termMap/teacherMap from current page rows */
const buildDisplayMaps = async (rows: Class[]) => {
  const missingGradeIds = [...new Set(rows.map(r => r.gradeId).filter((id): id is number => !!id && !gradeMap.value[id]))]
  const missingTermIds  = [...new Set(rows.map(r => r.termId).filter((id): id is number => !!id && !termMap.value[id]))]
  const missingTeacherIds = [...new Set(rows.map(r => r.headTeacherId).filter((id): id is number => !!id && !teacherMap.value[id]))]

  // grades — fetch by schoolIds already loaded; simpler: fetch grades for each unique schoolId in rows
  const schoolIds = [...new Set(rows.map(r => r.schoolId).filter((id): id is number => !!id))]
  await Promise.all(schoolIds.map(async sid => {
    const grades = await getGradesBySchool(sid)
    grades.forEach(g => { if (g.id) gradeMap.value[g.id] = g.gradeName })
  }))

  // terms
  if (missingTermIds.length) {
    const res = await getTermList({ pageNum: 1, pageSize: 200 })
    res?.records?.forEach(t => { if (t.id) termMap.value[t.id] = termLabel(t) })
  }

  // teachers — only fetch if we have gaps
  if (missingTeacherIds.length) {
    const schoolId = searchForm.schoolId
    const res = await getTeacherList({ pageNum: 1, pageSize: 500, ...(schoolId ? { schoolId } : {}) })
    res?.records?.forEach(t => { if (t.id) teacherMap.value[t.id] = t.name })
  }
}

const loadSchools = async () => {
  const res = await getSchoolList({ pageNum: 1, pageSize: 200 }) as any
  schoolOptions.value = res?.records ?? []
}

// ── search cascade ──────────────────────────────────────────────────────────
const onSearchSchoolChange = async (schoolId: number | undefined) => {
  searchForm.gradeId = undefined
  searchGradeOptions.value = schoolId ? await getGradesBySchool(schoolId) : []
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.schoolId = undefined
  searchForm.gradeId = undefined
  searchGradeOptions.value = []
  handleSearch()
}

// ── form cascade ─────────────────────────────────────────────────────────────
const onFormSchoolChange = async (schoolId: number | undefined) => {
  form.gradeId = undefined
  form.termId = undefined
  form.headTeacherId = undefined
  formGradeOptions.value = []
  formTermOptions.value = []
  formTeacherOptions.value = []
  if (!schoolId) return

  const [grades, terms, teachers] = await Promise.all([
    getGradesBySchool(schoolId),
    getTermList({ schoolId, pageNum: 1, pageSize: 200 }),
    getTeacherList({ schoolId, pageNum: 1, pageSize: 500 })
  ])
  formGradeOptions.value = grades
  formTermOptions.value = terms?.records ?? []
  formTeacherOptions.value = teachers?.records ?? []
}

// ── CRUD ────────────────────────────────────────────────────────────────────
const handleAdd = () => {
  dialogTitle.value = '新增班级'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: Class) => {
  dialogTitle.value = '编辑班级'
  resetForm()
  Object.assign(form, row)
  // load dropdowns for the school so headTeacher select is populated
  if (row.schoolId) {
    const [grades, terms, teachers] = await Promise.all([
      getGradesBySchool(row.schoolId),
      getTermList({ schoolId: row.schoolId, pageNum: 1, pageSize: 200 }),
      getTeacherList({ schoolId: row.schoolId, pageNum: 1, pageSize: 500 })
    ])
    formGradeOptions.value = grades
    formTermOptions.value = terms?.records ?? []
    formTeacherOptions.value = teachers?.records ?? []
  }
  dialogVisible.value = true
}

const handleDelete = (row: Class) => {
  ElMessageBox.confirm('确定要删除该班级吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteClass(row.id!)
      ElMessage.success('删除成功')
      loadData()
    } catch {
      ElMessage.error('删除失败')
    }
  })
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateClass(form.id, form)
        ElMessage.success('更新成功')
      } else {
        await createClass(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch {
      ElMessage.error(form.id ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}

const resetForm = () => {
  form.id = undefined
  form.schoolId = undefined
  form.gradeId = undefined
  form.termId = undefined
  form.className = ''
  form.classCode = undefined
  form.headTeacherId = undefined
  formGradeOptions.value = []
  formTermOptions.value = []
  formTeacherOptions.value = []
}

onMounted(() => {
  loadSchools()
  loadData()
})
</script>

<style scoped lang="scss">
.class-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
