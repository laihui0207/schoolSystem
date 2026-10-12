<template>
  <div class="teacher-management">
    <el-card>
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="教师姓名">
          <el-input v-model="searchForm.name" placeholder="请输入教师姓名" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="工号">
          <el-input v-model="searchForm.teacherNo" placeholder="请输入工号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="学校">
          <el-select v-model="searchForm.schoolId" placeholder="请选择学校" clearable>
            <el-option v-for="item in schoolList" :key="item.id" :label="item.schoolName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目">
          <el-select v-model="searchForm.subjectId" placeholder="请选择科目" clearable>
            <el-option v-for="item in subjectList" :key="item.id" :label="item.subjectName" :value="item.id!" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="teacherNo" label="工号" width="120" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="gender" label="性别" width="80">
          <template #default="{ row }">
            {{ row.gender === 1 ? '男' : row.gender === 2 ? '女' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="schoolName" label="所属学校" min-width="150" />
        <el-table-column prop="subjectName" label="主教科目" min-width="120" />
        <el-table-column label="身份" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.headTeacher" type="warning">班主任</el-tag>
            <el-tag v-else type="info">任课老师</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '在职' : '离职' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as Teacher)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row as Teacher)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" @close="handleDialogClose">
      <el-tabs v-model="activeTab">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="margin-top:12px">
            <el-form-item label="工号" prop="teacherNo">
              <el-input v-model="form.teacherNo" placeholder="请输入工号" :disabled="isEdit" />
            </el-form-item>
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入姓名" />
            </el-form-item>
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="form.gender">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="所属学校" prop="schoolId">
              <el-select v-model="form.schoolId" placeholder="请选择学校" style="width:100%" :disabled="isEdit">
                <el-option v-for="item in schoolList" :key="item.id" :label="item.schoolName" :value="item.id!" />
              </el-select>
            </el-form-item>
            <el-form-item label="主教科目" prop="subjectId">
              <el-select v-model="form.subjectId" placeholder="请选择科目" clearable style="width:100%">
                <el-option v-for="item in subjectList" :key="item.id" :label="item.subjectName" :value="item.id!" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="isEdit" label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">在职</el-radio>
                <el-radio :value="0">离职</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 班级绑定（仅编辑时） -->
        <el-tab-pane v-if="isEdit" label="班级绑定" name="classes">
          <div style="margin-top:12px">
            <!-- 新增分配行 -->
            <el-form :inline="true" @submit.prevent="handleAddAssignment">
              <el-form-item label="班级">
                <el-select v-model="newAssignment.classId" placeholder="选择班级" clearable style="width:160px">
                  <el-option v-for="c in classList" :key="c.id" :label="c.className" :value="c.id!" />
                </el-select>
              </el-form-item>
              <el-form-item label="科目">
                <el-select v-model="newAssignment.subjectId" placeholder="选择科目" clearable style="width:140px">
                  <el-option v-for="s in subjectList" :key="s.id" :label="s.subjectName" :value="s.id!" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :icon="Plus" @click="handleAddAssignment">添加</el-button>
              </el-form-item>
            </el-form>

            <el-table :data="classAssignments" border size="small" style="margin-top:8px">
              <el-table-column prop="className" label="班级" />
              <el-table-column prop="subjectName" label="科目" />
              <el-table-column label="班主任" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.isHeadTeacher" type="warning" size="small">是</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button
                    v-if="!row.isHeadTeacher"
                    link type="primary" size="small"
                    @click="handleSetHeadTeacher(row as ClassAssignment)"
                  >设为班主任</el-button>
                  <el-tag v-else type="warning" size="small" style="margin-right:4px">班主任</el-tag>
                  <el-button link type="danger" size="small" @click="handleRemoveAssignment(row as ClassAssignment)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="activeTab === 'basic'" type="primary" :loading="submitLoading" @click="handleSubmit">
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
  getTeacherList, createTeacher, updateTeacher, deleteTeacher,
  getClassAssignments, assignClass, removeClassAssignment,
  type Teacher, type ClassAssignment
} from '@/api/teacher'
import { getSchoolList, type School } from '@/api/school'
import { getAllSubjects, type Subject } from '@/api/subject'
import { getClassList, setHeadTeacher, type Class } from '@/api/class'

const loading = ref(false)
const submitLoading = ref(false)
const activeTab = ref('basic')

const searchForm = reactive({
  name: '', teacherNo: '',
  schoolId: undefined as number | undefined,
  subjectId: undefined as number | undefined
})

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<Teacher[]>([])
const schoolList = ref<School[]>([])
const subjectList = ref<Subject[]>([])
const classList = ref<Class[]>([])
const classAssignments = ref<ClassAssignment[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const newAssignment = reactive({ classId: undefined as number | undefined, subjectId: undefined as number | undefined })

const defaultForm = (): Teacher => ({ id: undefined, teacherNo: '', schoolId: 0, name: '', gender: 1, subjectId: undefined, status: 1 })
const form = reactive<Teacher>(defaultForm())
const isEdit = computed(() => !!form.id)

const rules: FormRules = {
  teacherNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getTeacherList({ ...searchForm, pageNum: pagination.pageNum, pageSize: pagination.pageSize })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadSchoolList = async () => {
  try {
    const res = await getSchoolList({ pageNum: 1, pageSize: 1000 }) as { records: School[] }
    schoolList.value = res.records || []
  } catch { ElMessage.error('加载学校列表失败') }
}

const loadSubjectList = async () => {
  try { subjectList.value = await getAllSubjects() }
  catch { ElMessage.error('加载科目列表失败') }
}

const loadClassList = async (schoolId: number) => {
  try {
    const res = await getClassList({ schoolId, pageNum: 1, pageSize: 1000 })
    classList.value = (res as any).records || []
  } catch { ElMessage.error('加载班级列表失败') }
}

const loadClassAssignments = async (teacherId: number) => {
  try { classAssignments.value = await getClassAssignments(teacherId) }
  catch { ElMessage.error('加载班级绑定失败') }
}

const handleSearch = () => { pagination.pageNum = 1; loadData() }
const handleReset = () => { Object.assign(searchForm, { name: '', teacherNo: '', schoolId: undefined, subjectId: undefined }); handleSearch() }

const handleAdd = () => {
  dialogTitle.value = '新增教师'
  resetForm()
  activeTab.value = 'basic'
  dialogVisible.value = true
}

const handleEdit = async (row: Teacher) => {
  dialogTitle.value = '编辑教师'
  Object.assign(form, { ...row })
  activeTab.value = 'basic'
  dialogVisible.value = true
  if (row.id) {
    await Promise.all([
      loadClassAssignments(row.id),
      row.schoolId ? loadClassList(row.schoolId) : Promise.resolve()
    ])
  }
}

const handleDelete = (row: Teacher) => {
  ElMessageBox.confirm('确定要删除这条记录吗？', '提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
    .then(async () => {
      try { await deleteTeacher(row.id!); ElMessage.success('删除成功'); loadData() }
      catch { ElMessage.error('删除失败') }
    }).catch(() => {})
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) { await updateTeacher(form.id, form); ElMessage.success('更新成功') }
      else { await createTeacher(form); ElMessage.success('创建成功') }
      dialogVisible.value = false
      loadData()
    } catch { ElMessage.error('提交失败') }
    finally { submitLoading.value = false }
  })
}

const handleAddAssignment = async () => {
  if (!newAssignment.classId || !newAssignment.subjectId || !form.id) {
    ElMessage.warning('请选择班级和科目')
    return
  }
  try {
    await assignClass(form.id, newAssignment.classId, newAssignment.subjectId)
    ElMessage.success('添加成功')
    newAssignment.classId = undefined
    newAssignment.subjectId = undefined
    await loadClassAssignments(form.id)
  } catch { ElMessage.error('添加失败') }
}

const handleSetHeadTeacher = async (row: ClassAssignment) => {
  if (!form.id) return
  try {
    await setHeadTeacher(row.classId, form.id)
    ElMessage.success('已设为班主任')
    await loadClassAssignments(form.id)
  } catch { ElMessage.error('设置失败') }
}

const handleRemoveAssignment = async (row: ClassAssignment) => {
  if (!form.id) return
  try {
    await removeClassAssignment(form.id, row.classId, row.subjectId)
    ElMessage.success('移除成功')
    await loadClassAssignments(form.id)
  } catch { ElMessage.error('移除失败') }
}

const handleDialogClose = () => { formRef.value?.resetFields(); resetForm() }
const resetForm = () => { Object.assign(form, defaultForm()); classAssignments.value = [] }

onMounted(() => { loadData(); loadSchoolList(); loadSubjectList() })
</script>

<style scoped lang="scss">
.teacher-management {
  .search-form { margin-bottom: 20px; }
}
</style>
