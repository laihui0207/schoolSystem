<template>
  <div class="class-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="班级名称">
          <el-input
            v-model="searchForm.className"
            placeholder="请输入班级名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="学校">
          <el-select
            v-model="searchForm.schoolId"
            placeholder="请选择学校"
            clearable
            @change="handleSchoolChange"
          >
            <el-option
              v-for="item in schoolList"
              :key="item.id"
              :label="item.schoolName"
              :value="item.id!"
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
              v-for="item in gradeList"
              :key="item.id"
              :label="item.gradeName"
              :value="item.id!"
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
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="className" label="班级名称" min-width="120" />
        <el-table-column prop="classCode" label="班级编码" width="100" />
        <!-- ponytail: 后端 ClassResponse 确实回了这些展示名，但 termName 用的是
             「上学期/下学期」，与前端 TERM_NAMES 的「第一学期/第二学期」不一致。
             下拉列表本来就要加载，统一用前端映射保证各页面文案一致 -->
        <el-table-column label="所属学校" min-width="150">
          <template #default="{ row }">{{ schoolName(row.schoolId) }}</template>
        </el-table-column>
        <el-table-column label="所属年级" width="120">
          <template #default="{ row }">{{ gradeName(row.gradeId) }}</template>
        </el-table-column>
        <el-table-column label="学期" width="150">
          <template #default="{ row }">{{ termName(row.termId) }}</template>
        </el-table-column>
        <el-table-column label="班主任" width="100">
          <template #default="{ row }">{{ teacherName(row.headTeacherId) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as Class)">
              编辑
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row as Class)">
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
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="loadData"
        style="margin-top: 20px"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="form.className" placeholder="请输入班级名称" />
        </el-form-item>
        <!-- 后端 ClassUpdateRequest 不接受 schoolId/gradeId/termId，编辑时禁用 -->
        <el-form-item label="所属学校" prop="schoolId">
          <el-select
            v-model="form.schoolId"
            placeholder="请选择学校"
            style="width: 100%"
            :disabled="isEdit"
            @change="handleFormSchoolChange"
          >
            <el-option
              v-for="item in schoolList"
              :key="item.id"
              :label="item.schoolName"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属年级" prop="gradeId">
          <el-select
            v-model="form.gradeId"
            placeholder="请选择年级"
            style="width: 100%"
            :disabled="isEdit || !form.schoolId"
          >
            <el-option
              v-for="item in formGradeList"
              :key="item.id"
              :label="item.gradeName"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学期" prop="termId">
          <el-select
            v-model="form.termId"
            placeholder="请选择学期"
            style="width: 100%"
            :disabled="isEdit || !form.schoolId"
          >
            <el-option
              v-for="item in formTermList"
              :key="item.id"
              :label="termLabel(item)"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级编码" prop="classCode">
          <el-input-number
            v-model="form.classCode"
            :min="1"
            :max="99"
            placeholder="同年级下唯一"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="班主任" prop="headTeacherId">
          <el-select
            v-model="form.headTeacherId"
            placeholder="请选择班主任"
            style="width: 100%"
            clearable
            :disabled="!form.schoolId"
          >
            <el-option
              v-for="item in formTeacherList"
              :key="item.id"
              :label="item.name"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getClassList,
  createClass,
  updateClass,
  deleteClass,
  type Class
} from '@/api/class'
import { getSchoolList, type School } from '@/api/school'
import { getGradeList, getGradesBySchool, type Grade } from '@/api/grade'
import { getAllTerms, termLabel, type Term } from '@/api/term'
import { getTeacherList, type Teacher } from '@/api/teacher'
import { handleError } from '@/utils/errorHandler'

type TableData = Class

const loading = ref(false)
const submitLoading = ref(false)

const searchForm = reactive({
  className: '',
  schoolId: undefined as number | undefined,
  gradeId: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableData = ref<TableData[]>([])
const schoolList = ref<School[]>([])
const gradeList = ref<Grade[]>([])
const allGrades = ref<Grade[]>([])
const formGradeList = ref<Grade[]>([])
const termList = ref<Term[]>([])
const teacherList = ref<Teacher[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

// id 必须显式列出：resetForm 走 Object.assign，缺 key 就清不掉，
// 「编辑」后再点「新增」会带着上次的 id 去改旧记录
const defaultForm = (): Class => ({
  id: undefined,
  className: '',
  gradeId: undefined,
  schoolId: undefined,
  classCode: undefined,
  termId: undefined,
  headTeacherId: undefined
})

const form = reactive<Class>(defaultForm())

const isEdit = computed(() => !!form.id)

// 展示名统一走前端映射（见表格列注释），不依赖后端 ClassResponse 的 *Name 字段
const schoolName = (id?: number) =>
  schoolList.value.find((s) => s.id === id)?.schoolName || '-'
// 用 allGrades 而不是 gradeList：后者是搜索栏按学校过滤的，清空筛选就空了，
// 表格年级列会整列变 '-'
const gradeName = (id?: number) =>
  allGrades.value.find((g) => g.id === id)?.gradeName || '-'
const termName = (id?: number) => {
  const t = termList.value.find((x) => x.id === id)
  return t ? termLabel(t) : '-'
}
const teacherName = (id?: number) =>
  teacherList.value.find((t) => t.id === id)?.name || '-'

// 弹窗里的学期/班主任按已选学校过滤，避免把别校的学期或教师挂到本校班级上。
// ponytail: termList/teacherList 进页面已全量加载（表格映射要用），这里只过滤不再请求
const formTermList = computed(() =>
  form.schoolId ? termList.value.filter((t) => t.schoolId === form.schoolId) : []
)
const formTeacherList = computed(() =>
  form.schoolId ? teacherList.value.filter((t) => t.schoolId === form.schoolId) : []
)

const rules: FormRules = {
  className: [
    { required: true, message: '请输入班级名称', trigger: 'blur' }
  ],
  schoolId: [
    { required: true, message: '请选择学校', trigger: 'change' }
  ],
  gradeId: [
    { required: true, message: '请选择年级', trigger: 'change' }
  ],
  // 后端 @NotNull，漏发直接 400
  termId: [
    { required: true, message: '请选择学期', trigger: 'change' }
  ],
  classCode: [
    { required: true, message: '请输入班级编码', trigger: 'blur' }
  ]
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    const res = await getClassList(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    handleError(error, '加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadSchoolList = async () => {
  try {
    const res = await getSchoolList({
      pageNum: 1,
      pageSize: 1000
    }) as { records: School[] }
    schoolList.value = res.records || []
  } catch (error) {
    handleError(error, '加载学校列表失败')
  }
}

const loadTermList = async () => {
  try {
    termList.value = await getAllTerms()
  } catch (error) {
    handleError(error, '加载学期列表失败')
  }
}

// 表格的班主任列要按 headTeacherId 映射名字，所以进页面就全量加载一次
const loadTeacherList = async () => {
  try {
    const res = await getTeacherList({ pageNum: 1, pageSize: 1000 })
    teacherList.value = res.records || []
  } catch (error) {
    handleError(error, '加载教师列表失败')
  }
}

// 表格的年级列同理：搜索栏的 gradeList 只在选了学校后才有值，
// 这里进页面就全量加载，保证列表页年级名不空
const loadAllGrades = async () => {
  try {
    const res = await getGradeList({ pageNum: 1, pageSize: 1000 })
    allGrades.value = res.records || []
  } catch (error) {
    handleError(error, '加载年级列表失败')
  }
}

const loadGradesBySchool = async (schoolId: number) => {
  try {
    const res = await getGradesBySchool(schoolId)
    return res || []
  } catch (error) {
    handleError(error, '加载年级列表失败')
    return []
  }
}

const handleSchoolChange = async (schoolId: number | undefined) => {
  searchForm.gradeId = undefined
  if (schoolId) {
    gradeList.value = await loadGradesBySchool(schoolId)
  } else {
    gradeList.value = []
  }
}

// 换学校要连带清掉 termId/headTeacherId：学期和班主任下拉都是按学校过滤的，
// 不清就会留着别校的旧选项值提交上去
const handleFormSchoolChange = async (schoolId: number | undefined) => {
  form.gradeId = undefined
  form.termId = undefined
  form.headTeacherId = undefined
  formGradeList.value = schoolId ? await loadGradesBySchool(schoolId) : []
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, {
    className: '',
    schoolId: undefined,
    gradeId: undefined
  })
  gradeList.value = []
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '新增班级'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: Class) => {
  dialogTitle.value = '编辑班级'
  Object.assign(form, { ...row })
  if (form.schoolId) {
    loadGradesBySchool(form.schoolId).then(grades => {
      formGradeList.value = grades
    })
  }
  dialogVisible.value = true
}

const handleDelete = (row: Class) => {
  ElMessageBox.confirm('确定要删除这条记录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteClass(row.id!)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      handleError(error, '删除失败')
    }
  }).catch(() => {
    // 用户取消删除
  })
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
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
        // ponytail: 拦截器已弹出后端的具体校验信息，这里不再覆盖成「提交失败」
        // 失败时保持弹窗打开，用户可直接改表单重试
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}

// teacherList/termList 是表格映射的数据源，这里不能清，否则关一次弹窗整列变 '-'
const resetForm = () => {
  Object.assign(form, defaultForm())
  formGradeList.value = []
}

onMounted(() => {
  loadData()
  loadSchoolList()
  loadAllGrades()
  loadTermList()
  loadTeacherList()
})
</script>

<style scoped lang="scss">
.class-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
