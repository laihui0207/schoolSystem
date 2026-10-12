<script setup lang="ts">
defineProps<{ text: string }>()
</script>

<template>
  <view class="empty">
    <text>{{ text }}</text>
    <slot />
  </view>
</template>
