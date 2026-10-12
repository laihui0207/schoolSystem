<script setup lang="ts">
import { onMounted } from 'vue'
import { useChildren } from '@/composables/useChildren'
import { userStore } from '@/store/user'

/**
 * 家长端孩子切换条：只在家长且绑定了 ≥1 个孩子时渲染。
 * 切换后写入 store（持久化），并 emit change 让页面重新拉数据。
 */
const emit = defineEmits<{ (e: 'change', studentId: number): void }>()

const { options, loadAll } = useChildren()
const selected = userStore.selectedChildId

const pick = (studentId: number): void => {
  if (studentId === selected.value) return
  userStore.selectChild(studentId)
  emit('change', studentId)
}

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <view v-if="userStore.isParent.value && options.length > 0" class="switcher">
    <text class="switcher-label">查看孩子</text>
    <scroll-view scroll-x class="switcher-scroll">
      <view class="chip-row switcher-row">
        <view
          v-for="opt in options"
          :key="opt.studentId"
          class="chip"
          :class="{ active: opt.studentId === selected }"
          @tap="pick(opt.studentId)"
        >
          {{ opt.label }}
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped lang="scss">
.switcher {
  margin-bottom: 20rpx;
}

.switcher-label {
  font-size: 24rpx;
  color: #8a8f99;
  margin-bottom: 12rpx;
  display: block;
}

.switcher-scroll {
  white-space: nowrap;
}

.switcher-row {
  flex-wrap: nowrap;
}
</style>
