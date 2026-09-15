<template>
  <div class="warning-management">
    <!-- 触发预警检查 -->
    <el-card style="margin-bottom: 20px">
      <template #header>触发考试成绩预警检查</template>
      <el-form :inline="true">
        <el-form-item label="考试ID">
          <el-input-number v-model="examId" :min="1" placeholder="请输入考试ID" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="checkLoading" @click="handleCheckExam">触发检查</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 学生成绩趋势分析 -->
    <el-card>
      <template #header>学生成绩趋势分析</template>
      <el-form :inline="true">
        <el-form-item label="学生ID">
          <el-input-number v-model="trendForm.studentId" :min="1" placeholder="请输入学生ID" style="width: 180px" />
        </el-form-item>
        <el-form-item label="科目ID">
          <el-input-number v-model="trendForm.subjectId" :min="1" placeholder="可选" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="trendLoading" @click="handleAnalyzeTrend">分析趋势</el-button>
        </el-form-item>
      </el-form>

      <!-- 趋势结果 -->
      <div v-if="trendResult" class="trend-result">
        <el-descriptions :column="2" border>
          <el-descriptions-item
            v-for="(value, key) in trendResult"
            :key="key"
            :label="String(key)"
          >
            {{ formatValue(value) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else-if="trendQueried" description="暂无趋势数据" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const examId = ref<number | undefined>(undefined)
const checkLoading = ref(false)

const trendForm = reactive({
  studentId: undefined as number | undefined,
  subjectId: undefined as number | undefined
})
const trendLoading = ref(false)
const trendResult = ref<Record<string, any> | null>(null)
const trendQueried = ref(false)

const handleCheckExam = async () => {
  if (!examId.value) {
    ElMessage.warning('请输入考试ID')
    return
  }
  checkLoading.value = true
  try {
    await request({ url: '/warnings/check-exam', method: 'post', params: { examId: examId.value } })
    ElMessage.success('预警检查已触发')
  } catch (error) {
    console.error('触发预警检查失败', error)
  } finally {
    checkLoading.value = false
  }
}

const handleAnalyzeTrend = async () => {
  if (!trendForm.studentId) {
    ElMessage.warning('请输入学生ID')
    return
  }
  trendLoading.value = true
  trendQueried.value = false
  try {
    const res: any = await request({
      url: `/warnings/trend/${trendForm.studentId}`,
      method: 'get',
      params: trendForm.subjectId ? { subjectId: trendForm.subjectId } : {}
    })
    trendResult.value = res ?? null
    trendQueried.value = true
  } catch (error) {
    console.error('趋势分析失败', error)
    trendQueried.value = true
  } finally {
    trendLoading.value = false
  }
}

// ponytail: naive stringify for unknown Map<String,Object> shape
const formatValue = (val: any): string => {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}
</script>

<style scoped lang="scss">
.warning-management {
  .trend-result {
    margin-top: 20px;
  }
}
</style>
