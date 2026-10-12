<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <span class="title">学生成绩管理系统</span>
        </div>
      </template>

      <el-tabs v-model="activeTab" stretch class="login-tabs">
        <el-tab-pane label="账号密码" name="account" />
        <el-tab-pane label="手机验证码" name="phone" />
      </el-tabs>

      <!-- 账号密码登录：保持原表单不动，e2e 依赖这里的 placeholder 与「登录」按钮 -->
      <el-form
        v-if="activeTab === 'account'"
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        size="large"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item prop="captcha">
          <div class="captcha-row">
            <el-input
              v-model="loginForm.captcha"
              placeholder="请输入验证码"
              prefix-icon="Key"
              clearable
              @keyup.enter="handleLogin"
            />
            <img
              v-if="captchaImage"
              :src="captchaImage"
              alt="验证码"
              class="captcha-img"
              title="点击刷新"
              @click="loadCaptcha"
            />
            <div v-else class="captcha-placeholder" @click="loadCaptcha">加载中...</div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            style="width: 100%"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 手机验证码登录 -->
      <el-form
        v-else
        ref="phoneFormRef"
        :model="phoneForm"
        :rules="phoneRules"
        class="login-form"
        size="large"
      >
        <el-form-item prop="phone">
          <el-input
            v-model="phoneForm.phone"
            placeholder="请输入手机号"
            prefix-icon="Iphone"
            maxlength="11"
            clearable
          />
        </el-form-item>

        <el-form-item prop="code">
          <div class="captcha-row">
            <el-input
              v-model="phoneForm.code"
              placeholder="请输入短信验证码"
              prefix-icon="Message"
              maxlength="6"
              clearable
              @keyup.enter="handlePhoneLogin"
            />
            <el-button
              class="sms-btn"
              :disabled="loginSms.seconds > 0 || loginSms.sending"
              :loading="loginSms.sending"
              @click="sendLoginCode"
            >
              {{ smsButtonText(loginSms) }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            style="width: 100%"
            :loading="phoneLoading"
            @click="handlePhoneLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        还没有账号？
        <el-link type="primary" :underline="false" @click="openRegister">注册账号</el-link>
      </div>
    </el-card>

    <!-- 学生 / 家长自助注册 -->
    <el-dialog
      v-model="registerVisible"
      title="注册账号"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetRegister"
    >
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-width="80px"
      >
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="registerForm.phone" placeholder="用作登录账号" maxlength="11" clearable />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <div class="captcha-row">
            <el-input v-model="registerForm.code" placeholder="6 位短信验证码" maxlength="6" clearable />
            <el-button
              class="sms-btn"
              :disabled="registerSms.seconds > 0 || registerSms.sending"
              :loading="registerSms.sending"
              @click="sendRegisterCode"
            >
              {{ smsButtonText(registerSms) }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="至少 8 位，含字母和数字"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="再次输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="姓名" prop="realName">
          <el-input v-model="registerForm.realName" placeholder="真实姓名" maxlength="50" clearable />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="registerForm.role">
            <el-radio value="student">学生</el-radio>
            <el-radio value="parent">家长</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="学校" prop="schoolId">
          <el-select
            v-model="registerForm.schoolId"
            placeholder="请选择学校"
            filterable
            style="width: 100%"
            :loading="schoolsLoading"
          >
            <el-option
              v-for="school in schools"
              :key="school.id"
              :label="school.schoolName"
              :value="school.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="registerForm.role === 'student'" label="学号" prop="studentNo">
          <el-input
            v-model="registerForm.studentNo"
            placeholder="选填；与本校学号及姓名一致时自动关联档案"
            maxlength="30"
            clearable
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="registerLoading" @click="handleRegister">
          注册并登录
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  getCaptcha,
  sendSmsCode,
  loginByPhone,
  register as registerApi,
  getPublicSchools
} from '@/api/auth'
import type { PublicSchool, RegisterRole, SmsScene } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

// 与后端 RegisterRequest / PhoneLoginRequest 上的 @Pattern 保持一致
const PHONE_PATTERN = /^1[3-9]\d{9}$/
const SMS_CODE_PATTERN = /^\d{6}$/
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)\S{8,72}$/
const DEFAULT_SEND_INTERVAL = 60

type ActiveTab = 'account' | 'phone'
const activeTab = ref<ActiveTab>('account')

// ---------------------------------------------------------------- 账号密码登录（原逻辑不动）

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')

const loginForm = reactive({
  username: '',
  password: '',
  captcha: ''
})

const loginRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
}

const loadCaptcha = async () => {
  try {
    const data = await getCaptcha()
    captchaKey.value = data.captchaKey
    captchaImage.value = data.captchaImage
  } catch {
    ElMessage.error('验证码加载失败，请刷新重试')
  }
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userStore.login(loginForm.username, loginForm.password, loginForm.captcha, captchaKey.value)
        ElMessage.success('登录成功')
        router.push('/')
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : '登录失败'
        ElMessage.error(message)
        // 登录失败后刷新验证码
        loadCaptcha()
        loginForm.captcha = ''
      } finally {
        loading.value = false
      }
    }
  })
}

// ---------------------------------------------------------------- 短信倒计时（登录 Tab 与注册弹窗各一份状态）

interface SmsCountdown {
  seconds: number
  sending: boolean
  timer: ReturnType<typeof setInterval> | null
}

const createCountdown = (): SmsCountdown => reactive({ seconds: 0, sending: false, timer: null })

const stopCountdown = (state: SmsCountdown) => {
  if (state.timer) {
    clearInterval(state.timer)
  }
  state.timer = null
  state.seconds = 0
}

const startCountdown = (state: SmsCountdown, seconds: number) => {
  stopCountdown(state)
  state.seconds = seconds
  state.timer = setInterval(() => {
    state.seconds -= 1
    if (state.seconds <= 0) {
      stopCountdown(state)
    }
  }, 1000)
}

const smsButtonText = (state: SmsCountdown) =>
  state.seconds > 0 ? `${state.seconds}s 后重发` : '获取验证码'

const requestSmsCode = async (state: SmsCountdown, phone: string, scene: SmsScene) => {
  if (!PHONE_PATTERN.test(phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  state.sending = true
  try {
    const data = await sendSmsCode({ phone, scene })
    ElMessage.success('验证码已发送，请注意查收')
    // ponytail: 倒计时秒数以后端 send-interval 为准，改配置不用同步改前端
    startCountdown(state, data.sendInterval || DEFAULT_SEND_INTERVAL)
  } catch {
    // request.ts 已按后端 message 弹过错误（频控 / 每日上限），这里不重复提示，也不启动倒计时
  } finally {
    state.sending = false
  }
}

// ---------------------------------------------------------------- 手机验证码登录

const phoneFormRef = ref<FormInstance>()
const phoneLoading = ref(false)
const loginSms = createCountdown()

const phoneForm = reactive({
  phone: '',
  code: ''
})

const phoneRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: PHONE_PATTERN, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入短信验证码', trigger: 'blur' },
    { pattern: SMS_CODE_PATTERN, message: '验证码为 6 位数字', trigger: 'blur' }
  ]
}

const sendLoginCode = () => requestSmsCode(loginSms, phoneForm.phone, 'login')

const handlePhoneLogin = async () => {
  if (!phoneFormRef.value) return
  const valid = await phoneFormRef.value.validate().catch(() => false)
  if (!valid) return

  phoneLoading.value = true
  try {
    const data = await loginByPhone({ phone: phoneForm.phone, code: phoneForm.code })
    // ponytail: 后端返回与密码登录完全相同的 LoginResponse，直接复用 store 的 setTokens，不另加 store 方法
    userStore.setTokens(data.accessToken, data.refreshToken, data.userInfo)
    ElMessage.success('登录成功')
    router.push('/')
  } catch {
    // 错误已由 request.ts 提示；验证码一次性，清空让用户重新输入
    phoneForm.code = ''
  } finally {
    phoneLoading.value = false
  }
}

// ---------------------------------------------------------------- 自助注册

interface RegisterForm {
  phone: string
  code: string
  password: string
  confirmPassword: string
  realName: string
  role: RegisterRole
  schoolId: number | undefined
  studentNo: string
}

const defaultRegisterForm = (): RegisterForm => ({
  phone: '',
  code: '',
  password: '',
  confirmPassword: '',
  realName: '',
  role: 'student',
  schoolId: undefined,
  studentNo: ''
})

const registerVisible = ref(false)
const registerLoading = ref(false)
const registerFormRef = ref<FormInstance>()
const registerSms = createCountdown()
const registerForm = reactive<RegisterForm>(defaultRegisterForm())
const schools = ref<PublicSchool[]>([])
const schoolsLoading = ref(false)

const registerRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: PHONE_PATTERN, message: '手机号格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入短信验证码', trigger: 'blur' },
    { pattern: SMS_CODE_PATTERN, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: PASSWORD_PATTERN, message: '密码至少 8 位，需同时包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value === registerForm.password) {
          callback()
        } else {
          callback(new Error('两次输入的密码不一致'))
        }
      },
      trigger: 'blur'
    }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名不能超过 50 个字符', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ],
  schoolId: [
    { required: true, message: '请选择学校', trigger: 'change' }
  ],
  studentNo: [
    { max: 30, message: '学号不能超过 30 个字符', trigger: 'blur' }
  ]
}

const loadSchools = async () => {
  schoolsLoading.value = true
  try {
    schools.value = await getPublicSchools()
  } catch {
    // 已由 request.ts 提示
  } finally {
    schoolsLoading.value = false
  }
}

const openRegister = async () => {
  registerVisible.value = true
  if (schools.value.length === 0) {
    await loadSchools()
  }
}

const resetRegister = () => {
  Object.assign(registerForm, defaultRegisterForm())
}

const sendRegisterCode = () => requestSmsCode(registerSms, registerForm.phone, 'register')

const handleRegister = async () => {
  if (!registerFormRef.value) return
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid || registerForm.schoolId === undefined) return

  const studentNo = registerForm.studentNo.trim()
  registerLoading.value = true
  try {
    const data = await registerApi({
      phone: registerForm.phone,
      code: registerForm.code,
      password: registerForm.password,
      realName: registerForm.realName.trim(),
      role: registerForm.role,
      schoolId: registerForm.schoolId,
      studentNo: registerForm.role === 'student' && studentNo ? studentNo : undefined
    })
    userStore.setTokens(data.accessToken, data.refreshToken, data.userInfo)
    ElMessage.success('注册成功，已自动登录')
    registerVisible.value = false
    router.push('/')
  } catch {
    registerForm.code = ''
  } finally {
    registerLoading.value = false
  }
}

onMounted(() => {
  loadCaptcha()
})

onBeforeUnmount(() => {
  stopCountdown(loginSms)
  stopCountdown(registerSms)
})
</script>

<style scoped lang="scss">
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

  .login-card {
    width: 450px;

    .card-header {
      text-align: center;

      .title {
        font-size: 24px;
        font-weight: bold;
        color: #303133;
      }
    }

    .login-tabs {
      margin-top: -8px;
    }

    .login-form {
      margin-top: 12px;
    }

    .login-footer {
      text-align: center;
      font-size: 13px;
      color: #909399;
    }
  }

  .captcha-row {
    display: flex;
    gap: 12px;
    width: 100%;

    .el-input {
      flex: 1;
    }

    .sms-btn {
      width: 120px;
      flex-shrink: 0;
    }

    .captcha-img {
      height: 40px;
      cursor: pointer;
      border-radius: 4px;
      border: 1px solid #dcdfe6;
      flex-shrink: 0;
    }

    .captcha-placeholder {
      height: 40px;
      width: 120px;
      display: flex;
      align-items: center;
      justify-content: center;
      border: 1px solid #dcdfe6;
      border-radius: 4px;
      color: #909399;
      font-size: 12px;
      cursor: pointer;
      flex-shrink: 0;
    }
  }
}
</style>
