<template>
  <el-container class="main-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '200px'" class="sidebar">
      <div class="logo">
        <span v-if="!isCollapse">成绩管理系统</span>
        <span v-else>成绩</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :unique-opened="true"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <el-sub-menu index="basic">
          <template #title>
            <el-icon><School /></el-icon>
            <span>基础数据</span>
          </template>
          <el-menu-item index="/school">学校管理</el-menu-item>
          <el-menu-item index="/student">学生管理</el-menu-item>
          <el-menu-item index="/teacher">教师管理</el-menu-item>
          <el-menu-item index="/subject">科目管理</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="exam-score">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>考试与成绩</span>
          </template>
          <el-menu-item index="/exam">考试管理</el-menu-item>
          <el-menu-item index="/score">成绩录入</el-menu-item>
          <el-menu-item index="/score/query">成绩查询</el-menu-item>
          <el-menu-item index="/score/analysis">成绩分析</el-menu-item>
          <el-menu-item index="/score/score-change">成绩变更</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/stat">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>统计分析</template>
        </el-menu-item>

        <el-sub-menu index="message">
          <template #title>
            <el-icon><Bell /></el-icon>
            <span>消息中心</span>
          </template>
          <el-menu-item index="/notification">通知管理</el-menu-item>
          <el-menu-item index="/warning">成绩预警</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system">系统管理</el-menu-item>
          <el-menu-item index="/system/config">系统配置</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- 主体区域 -->
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-icon" @click="toggleCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </div>

        <div class="header-right">
          <el-badge :value="unreadCount" class="notification-badge">
            <el-icon :size="20"><Bell /></el-icon>
          </el-badge>

          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :icon="UserFilled" />
              <span class="username">{{ userStore.userInfo?.username || 'Admin' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区域 -->
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  HomeFilled,
  School,
  Document,
  DataAnalysis,
  Setting,
  Fold,
  Expand,
  Bell,
  UserFilled
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const unreadCount = ref(0)

const activeMenu = computed(() => route.path)

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const handleCommand = (command: string) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'password':
      router.push('/password')
      break
    case 'logout':
      handleLogout()
      break
  }
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
  })
}
</script>

<style scoped lang="scss">
.main-layout {
  height: 100vh;

  .sidebar {
    background-color: #304156;
    transition: width 0.3s;

    .logo {
      height: 60px;
      line-height: 60px;
      text-align: center;
      font-size: 18px;
      font-weight: bold;
      color: #fff;
      background-color: #2b2f3a;
    }

    :deep(.el-menu) {
      border-right: none;
      background-color: #304156;

      .el-menu-item,
      .el-sub-menu__title {
        color: #bfcbd9;

        &:hover {
          background-color: #263445 !important;
          color: #fff;
        }
      }

      .el-menu-item.is-active {
        background-color: #409eff !important;
        color: #fff;
      }
    }
  }

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background-color: #fff;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    padding: 0 20px;

    .header-left {
      .collapse-icon {
        font-size: 20px;
        cursor: pointer;
        &:hover {
          color: #409eff;
        }
      }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 20px;

      .notification-badge {
        cursor: pointer;
      }

      .user-info {
        display: flex;
        align-items: center;
        gap: 10px;
        cursor: pointer;

        .username {
          font-size: 14px;
        }
      }
    }
  }

  .el-main {
    background-color: #f0f2f5;
  }
}
</style>
