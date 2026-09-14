<template>
  <div class="exam-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>考试管理</span>
          <el-button type="primary" @click="handleAdd">新增考试</el-button>
        </div>
      </template>

      <el-table :data="tableData" stripe>
        <el-table-column prop="examName" label="考试名称" />
        <el-table-column prop="examDate" label="考试日期" />
        <el-table-column prop="examType" label="考试类型" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="success" @click="handleStartInput(row)">开始录入</el-button>
            <el-button link type="warning" @click="handlePublish(row)">发布</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="loadData"
        style="margin-top: 20px"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const tableData = ref<any[]>([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const getStatusType = (status: number) => {
  const types: any = { 0: 'info', 1: 'warning', 2: '', 3: 'success' }
  return types[status] || 'info'
}

const getStatusText = (status: number) => {
  const texts: any = { 0: '未开始', 1: '录入中', 2: '审核中', 3: '已发布' }
  return texts[status] || '未知'
}

const loadData = () => {
  // TODO: 加载数据
  tableData.value = []
}

const handleAdd = () => {
  // TODO: 新增考试
  ElMessage.info('功能开发中...')
}

const handleEdit = (row: any) => {
  // TODO: 编辑考试
  ElMessage.info('功能开发中...')
}

const handleStartInput = (row: any) => {
  // TODO: 开始录入
  ElMessage.info('功能开发中...')
}

const handlePublish = (row: any) => {
  // TODO: 发布成绩
  ElMessage.info('功能开发中...')
}

const handleDelete = (row: any) => {
  // TODO: 删除
  ElMessage.info('功能开发中...')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
