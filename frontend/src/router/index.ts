import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/stores/user'

/**
 * 旧会话判定：契约 2 之后登录响应一定带 headTeacher（后端是原始 boolean，non_null 序列化下必出现）；
 * role 为空的账号会被 non_null 省掉 role，所以不能只看 role，两者都缺才视为升级前的旧会话。
 */
const isLegacySession = (info: UserInfo | null): boolean =>
  !info || (!('headTeacher' in info) && info.role === undefined)

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
        meta: { title: '学校管理', icon: 'School', allowedTypes: [1, 2] }
      },
      {
        path: 'term',
        name: 'Term',
        component: () => import('@/views/term/index.vue'),
        meta: { title: '学期管理', icon: 'Calendar', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'grade',
        name: 'Grade',
        component: () => import('@/views/grade/index.vue'),
        meta: { title: '年级管理', icon: 'Notebook', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'class',
        name: 'Class',
        component: () => import('@/views/class/index.vue'),
        meta: { title: '班级管理', icon: 'OfficeBuilding', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'subject',
        name: 'Subject',
        component: () => import('@/views/subject/index.vue'),
        meta: { title: '科目管理', icon: 'Collection', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'teacher',
        name: 'Teacher',
        component: () => import('@/views/teacher/index.vue'),
        meta: { title: '教师管理', icon: 'User', allowedTypes: [1, 2] }
      },
      {
        path: 'student',
        name: 'Student',
        component: () => import('@/views/student/index.vue'),
        // ponytail: 学生/家长不能进管理页，补 allowedTypes 与其它基础管理路由一致
        meta: { title: '学生管理', icon: 'UserFilled', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'exam',
        name: 'Exam',
        component: () => import('@/views/exam/index.vue'),
        meta: { title: '考试管理', icon: 'Reading', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'exam/detail/:id',
        name: 'ExamDetail',
        component: () => import('@/views/exam/detail.vue'),
        meta: { title: '考试详情', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'score',
        name: 'Score',
        component: () => import('@/views/score/index.vue'),
        meta: { title: '成绩管理', icon: 'Document', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'score/input',
        name: 'ScoreInput',
        component: () => import('@/views/score/input.vue'),
        meta: { title: '成绩录入', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'score/input-table',
        name: 'ScoreInputTable',
        component: () => import('@/views/score/input-table.vue'),
        meta: { title: '成绩录入表格', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'score/review-list',
        name: 'ScoreReviewList',
        component: () => import('@/views/score/review-list.vue'),
        meta: { title: '成绩审核列表', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'score/review-detail',
        name: 'ScoreReviewDetail',
        component: () => import('@/views/score/review-detail.vue'),
        meta: { title: '成绩审核详情', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'score/query',
        name: 'ScoreQuery',
        component: () => import('@/views/score/query.vue'),
        meta: { title: '成绩查询' }
      },
      {
        path: 'score/analysis',
        name: 'ScoreAnalysis',
        component: () => import('@/views/score/analysis.vue'),
        meta: { title: '成绩分析', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'score/score-change',
        name: 'ScoreChange',
        component: () => import('@/views/score/score-change.vue'),
        meta: { title: '成绩变更申请', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'stat',
        name: 'Stat',
        component: () => import('@/views/stat/index.vue'),
        meta: { title: '统计分析', icon: 'DataAnalysis' }
      },
      {
        path: 'stat/exam-overview',
        name: 'ExamOverview',
        component: () => import('@/views/stat/exam-overview.vue'),
        meta: { title: '考试成绩总览', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'stat/class-analysis',
        name: 'ClassAnalysis',
        component: () => import('@/views/stat/class-analysis.vue'),
        meta: { title: '班级成绩分析', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'stat/student-trend',
        name: 'StudentTrend',
        component: () => import('@/views/stat/student-trend.vue'),
        meta: { title: '学生成绩趋势' }
      },
      {
        path: 'stat/subject-compare',
        name: 'SubjectCompare',
        component: () => import('@/views/stat/subject-compare.vue'),
        meta: { title: '科目成绩对比', allowedTypes: [1, 2, 3] }
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
        meta: { title: '成绩预警', icon: 'Warning', allowedTypes: [1, 2, 3] }
      },
      {
        path: 'system',
        name: 'System',
        component: () => import('@/views/system/index.vue'),
        meta: { title: '系统管理', icon: 'Setting', allowedTypes: [1, 2] }
      },
      {
        path: 'system/user',
        name: 'UserManage',
        component: () => import('@/views/system/user.vue'),
        meta: { title: '用户管理', allowedTypes: [1, 2] }
      },
      {
        path: 'system/dict',
        name: 'DictManage',
        component: () => import('@/views/system/dict.vue'),
        meta: { title: '数据字典', allowedTypes: [1, 2] }
      },
      {
        path: 'system/config',
        name: 'ConfigManage',
        component: () => import('@/views/system/config.vue'),
        meta: { title: '系统配置', allowedTypes: [1, 2] }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  if (to.path === '/login') {
    next()
    return
  }

  if (!userStore.token) {
    next('/login')
    return
  }

  // ponytail: F-10 — 升级前已登录的学生会话里 userInfo 没有 studentId/role，成绩查询会误报「未关联学生档案」；清掉本地态让其重登一次
  if (isLegacySession(userStore.userInfo)) {
    userStore.clearSession()
    ElMessage.warning('登录信息已更新，请重新登录')
    next('/login')
    return
  }

  // 角色守卫：meta.allowedTypes 存在时校验用户类型
  const allowedTypes = to.meta?.allowedTypes as number[] | undefined
  const userType = userStore.userInfo?.userType
  if (allowedTypes && userType !== undefined && !allowedTypes.includes(userType)) {
    next('/dashboard')
    return
  }

  next()
})

export default router
