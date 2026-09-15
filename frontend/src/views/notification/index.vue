<template>
  <div class="notification-management">
    <el-card>
      <!-- 顶部：未读徽标 + 全部已读 -->
      <div class="toolbar">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" type="danger">
          <span class="unread-label">未读通知</span>
        </el-badge>
        <el-button type="primary" :icon="Check" @click="handleMarkAllAsRead">全部标记已读</el-button>
      </div>

      <!-- 筛选栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="类型">
          <el-select v-model="searchForm.type" placeholder="全部类型" clearable style="width: 140px">
            <el-option label="成绩发布" value="1" />
            <el-option label="成绩预警" value="2" />
            <el-option label="系统通知" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.isRead" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTagMap[row.type]?.tag">{{ typeTagMap[row.type]?.label || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isRead" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isRead === 1 ? 'info' : 'success'" size="small">
              {{ row.isRead === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.isRead !== 1"
              link
              type="primary"
              :icon="Check"
              @click="handleMarkAsRead(row)"
            >标记已读</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px"
        @size-change="handleSearch"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Check, Delete } from '@element-plus/icons-vue'
import {
  getNotificationList,
  markAsRead,
  markAllAsRead,
  deleteNotification,
  getUnreadCount
} from '@/api/notification'

const loading = ref(false)
const unreadCount = ref(0)

const typeTagMap: Record<string, { label: string; tag: 'primary' | 'warning' | 'info' }> = {
  '1': { label: '成绩发布', tag: 'primary' },
  '2': { label: '成绩预警', tag: 'warning' },
  '3': { label: '系统通知', tag: 'info' }
}

const searchForm = reactive({
  type: undefined as string | undefined,
  isRead: undefined as number | undefined
})

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])

const loadUnreadCount = async () => {
  try {
    const res: any = await getUnreadCount()
    unreadCount.value = res ?? 0
  } catch {}
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getNotificationList({
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('加载通知失败', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.type = undefined
  searchForm.isRead = undefined
  handleSearch()
}

const handleMarkAsRead = async (row: any) => {
  try {
    await markAsRead(row.id)
    row.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    ElMessage.success('已标记为已读')
  } catch (error) {
    console.error('标记已读失败', error)
  }
}

const handleMarkAllAsRead = async () => {
  try {
    await markAllAsRead()
    ElMessage.success('全部已标记为已读')
    unreadCount.value = 0
    loadData()
  } catch (error) {
    console.error('全部标记已读失败', error)
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm('确定要删除该通知吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteNotification(row.id)
      ElMessage.success('删除成功')
      loadData()
      loadUnreadCount()
    } catch (error) {
      console.error('删除失败', error)
    }
  })
}

onMounted(() => {
  loadData()
  loadUnreadCount()
})
</script>

<style scoped lang="scss">
.notification-management {
  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
    .unread-label {
      font-size: 14px;
      font-weight: 500;
    }
  }
  .search-form {
    margin-bottom: 12px;
  }
}
</style>
