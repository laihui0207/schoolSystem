import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    redirect: '/dashboard',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      },
      {
        path: 'school',
        name: 'School',
        component: () => import('@/views/school/index.vue'),
        meta: { title: '学校管理', icon: 'School' }
      },
      {
        path: 'term',
        name: 'Term',
        component: () => import('@/views/term/index.vue'),
        meta: { title: '学期管理', icon: 'Calendar' }
      },
      {
        path: 'exam',
        name: 'Exam',
        component: () => import('@/views/exam/index.vue'),
        meta: { title: '考试管理', icon: 'Reading' }
      },
      {
        path: 'score',
        name: 'Score',
        component: () => import('@/views/score/index.vue'),
        meta: { title: '成绩管理', icon: 'Document' }
      },
      {
        path: 'stat',
        name: 'Stat',
        component: () => import('@/views/stat/index.vue'),
        meta: { title: '统计分析', icon: 'DataAnalysis' }
      },
      {
        path: 'system',
        name: 'System',
        component: () => import('@/views/system/index.vue'),
        meta: { title: '系统管理', icon: 'Setting' }
      },
      {
        path: 'student',
        name: 'Student',
        component: () => import('@/views/student/index.vue'),
        meta: { title: '学生管理', icon: 'User' }
      },
      {
        path: 'teacher',
        name: 'Teacher',
        component: () => import('@/views/teacher/index.vue'),
        meta: { title: '教师管理', icon: 'Avatar' }
      },
      {
        path: 'class',
        name: 'Class',
        component: () => import('@/views/class/index.vue'),
        meta: { title: '班级管理', icon: 'Grid' }
      },
      {
        path: 'subject',
        name: 'Subject',
        component: () => import('@/views/subject/index.vue'),
        meta: { title: '科目管理', icon: 'Collection' }
      },
      {
        path: 'grade',
        name: 'Grade',
        component: () => import('@/views/grade/index.vue'),
        meta: { title: '年级管理', icon: 'Grid' }
      },
      {
        path: 'score/query',
        name: 'ScoreQuery',
        component: () => import('@/views/score/query.vue'),
        meta: { title: '成绩查询', icon: 'Search' }
      },
      {
        path: 'score/analysis',
        name: 'ScoreAnalysis',
        component: () => import('@/views/score/analysis.vue'),
        meta: { title: '成绩分析', icon: 'DataAnalysis' }
      },
      {
        path: 'score/score-change',
        name: 'ScoreChange',
        component: () => import('@/views/score/score-change.vue'),
        meta: { title: '成绩变更', icon: 'Edit' }
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config.vue'),
        meta: { title: '系统配置', icon: 'Setting' }
      },
      {
        path: 'notification',
        name: 'Notification',
        component: () => import('@/views/notification/index.vue'),
        meta: { title: '通知管理', icon: 'Bell' }
      },
      {
        path: 'warning',
        name: 'Warning',
        component: () => import('@/views/warning/index.vue'),
        meta: { title: '成绩预警', icon: 'Warning' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  if (to.path === '/login') {
    next()
  } else {
    if (userStore.token) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router
