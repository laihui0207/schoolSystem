<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getCaptcha, login, loginByPhone, probePhoneLogin, sendSmsCode } from '@/api/auth'
import { userStore } from '@/store/user'
import type { LoginResponse } from '@/types'
import { USER_TYPE_PARENT, USER_TYPE_STUDENT } from '@/types'
import { errorMessage, toHome, toast } from '@/utils/navigate'

type Tab = 'account' | 'phone'

const SMS_COUNTDOWN_SECONDS = 60
const PHONE_PATTERN = /^1\d{10}$/

const tab = ref<Tab>('account')
const phoneLoginAvailable = ref(false)
const loading = ref(false)

/* ---------- 账号密码 ---------- */
const form = ref({ username: '', password: '', captcha: '' })
const captchaKey = ref('')
const captchaImage = ref('')
/** 验证码接口不可用（后端未启动 Redis 等）时隐藏验证码行，直接提交由后端决定 */
const captchaUnavailable = ref(false)

const loadCaptcha = async (): Promise<void> => {
  try {
    const data = await getCaptcha()
    captchaKey.value = data.captchaKey
    captchaImage.value = data.captchaImage
    captchaUnavailable.value = false
  } catch {
    captchaKey.value = ''
    captchaImage.value = ''
    captchaUnavailable.value = true
  }
}

const finishLogin = (res: LoginResponse): void => {
  userStore.setSession(res)
  const type = res.userInfo.userType
  if (type !== USER_TYPE_STUDENT && type !== USER_TYPE_PARENT) {
    toast('移动端仅面向学生与家长，部分功能不可用')
  } else {
    toast('登录成功', 'success')
  }
  toHome()
}

const submitAccount = async (): Promise<void> => {
  const { username, password, captcha } = form.value
  if (!username.trim()) return toast('请输入用户名')
  if (!password) return toast('请输入密码')
  // ponytail: dev 环境后端 app.captcha.enabled=false 时任意值都能过；这里只在验证码图片成功展示时才要求填写
  if (captchaImage.value && !captcha.trim()) return toast('请输入验证码')
  loading.value = true
  try {
    const res = await login({
      username: username.trim(),
      password,
      captcha: captcha.trim() || undefined,
      captchaKey: captchaKey.value || undefined
    })
    finishLogin(res)
  } catch (err) {
    toast(errorMessage(err, '登录失败'))
    form.value = { ...form.value, captcha: '' }
    void loadCaptcha()
  } finally {
    loading.value = false
  }
}

/* ---------- 手机验证码（E 组端点存在时才显示） ---------- */
const phoneForm = ref({ phone: '', code: '' })
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const stopTimer = (): void => {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

const startCountdown = (): void => {
  countdown.value = SMS_COUNTDOWN_SECONDS
  stopTimer()
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) stopTimer()
  }, 1000)
}

const sendCode = async (): Promise<void> => {
  if (countdown.value > 0) return
  const phone = phoneForm.value.phone.trim()
  if (!PHONE_PATTERN.test(phone)) return toast('请输入正确的手机号')
  try {
    await sendSmsCode(phone)
    toast('验证码已发送', 'success')
    startCountdown()
  } catch (err) {
    toast(errorMessage(err, '发送失败'))
  }
}

const submitPhone = async (): Promise<void> => {
  const phone = phoneForm.value.phone.trim()
  const code = phoneForm.value.code.trim()
  if (!PHONE_PATTERN.test(phone)) return toast('请输入正确的手机号')
  if (!code) return toast('请输入验证码')
  loading.value = true
  try {
    finishLogin(await loginByPhone({ phone, code }))
  } catch (err) {
    toast(errorMessage(err, '登录失败'))
  } finally {
    loading.value = false
  }
}

const smsButtonText = computed(() => (countdown.value > 0 ? `${countdown.value}s` : '获取验证码'))

onMounted(() => {
  void loadCaptcha()
  // ponytail: 探测 /auth/login/phone 是否存在；404 → 隐藏 Tab。探测失败一律视为不存在，不阻塞账号登录
  probePhoneLogin()
    .then((ok) => {
      phoneLoginAvailable.value = ok
    })
    .catch(() => {
      phoneLoginAvailable.value = false
    })
})

onBeforeUnmount(stopTimer)
</script>

<template>
  <view class="login">
    <view class="hero">
      <text class="hero-title">成绩查询</text>
      <text class="hero-sub">学生 / 家长移动端</text>
    </view>

    <view class="panel">
      <view v-if="phoneLoginAvailable" class="tabs">
        <view class="tab" :class="{ active: tab === 'account' }" @tap="tab = 'account'">账号登录</view>
        <view class="tab" :class="{ active: tab === 'phone' }" @tap="tab = 'phone'">手机验证码</view>
      </view>

      <view v-if="tab === 'account'">
        <view class="field">
          <input v-model="form.username" class="input" placeholder="用户名 / 学号" :maxlength="50" />
        </view>
        <view class="field">
          <input v-model="form.password" class="input" placeholder="密码" :password="true" :maxlength="64" />
        </view>
        <view v-if="!captchaUnavailable" class="field field-captcha">
          <input v-model="form.captcha" class="input" placeholder="验证码" :maxlength="6" />
          <image v-if="captchaImage" class="captcha-img" :src="captchaImage" mode="aspectFit" @tap="loadCaptcha" />
          <view v-else class="captcha-placeholder" @tap="loadCaptcha">加载中</view>
        </view>
        <button class="btn submit" :loading="loading" :disabled="loading" @tap="submitAccount">登 录</button>
      </view>

      <view v-else>
        <view class="field">
          <input v-model="phoneForm.phone" class="input" type="number" placeholder="手机号" :maxlength="11" />
        </view>
        <view class="field field-captcha">
          <input v-model="phoneForm.code" class="input" type="number" placeholder="短信验证码" :maxlength="6" />
          <view class="sms-btn" :class="{ disabled: countdown > 0 }" @tap="sendCode">{{ smsButtonText }}</view>
        </view>
        <button class="btn submit" :loading="loading" :disabled="loading" @tap="submitPhone">登 录</button>
      </view>

      <view class="tips muted">忘记密码或未绑定孩子，请联系学校管理员</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.login {
  min-height: 100vh;
  background: linear-gradient(180deg, #2f6fed 0%, #4f8bff 42%, #f5f6f8 42.1%);
  padding: calc(env(safe-area-inset-top) + 120rpx) 40rpx 60rpx;
  box-sizing: border-box;
}

.hero {
  color: #fff;
  margin-bottom: 60rpx;
}

.hero-title {
  display: block;
  font-size: 56rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}

.hero-sub {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  opacity: 0.85;
}

.panel {
  background: #fff;
  border-radius: 28rpx;
  padding: 44rpx 36rpx;
  box-shadow: 0 12rpx 40rpx rgba(47, 111, 237, 0.15);
}

.tabs {
  display: flex;
  margin-bottom: 36rpx;
  border-bottom: 1rpx solid #f0f1f3;
}

.tab {
  flex: 1;
  text-align: center;
  padding-bottom: 20rpx;
  font-size: 30rpx;
  color: #8a8f99;
  position: relative;

  &.active {
    color: #2f6fed;
    font-weight: 600;

    &::after {
      content: '';
      position: absolute;
      left: 30%;
      right: 30%;
      bottom: -1rpx;
      height: 4rpx;
      border-radius: 4rpx;
      background: #2f6fed;
    }
  }
}

.field {
  display: flex;
  align-items: center;
  background: #f5f6f8;
  border-radius: 16rpx;
  padding: 0 24rpx;
  height: 92rpx;
  margin-bottom: 24rpx;
}

.input {
  flex: 1;
  height: 92rpx;
  font-size: 30rpx;
}

.field-captcha {
  padding-right: 12rpx;
}

.captcha-img {
  width: 200rpx;
  height: 72rpx;
  border-radius: 10rpx;
  margin-left: 16rpx;
}

.captcha-placeholder,
.sms-btn {
  min-width: 180rpx;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  font-size: 26rpx;
  color: #2f6fed;
  margin-left: 16rpx;
  border-radius: 10rpx;
  background: #eef3ff;

  &.disabled {
    color: #8a8f99;
    background: #f0f1f3;
  }
}

.submit {
  margin-top: 12rpx;
}

.tips {
  margin-top: 32rpx;
  text-align: center;
  font-size: 24rpx;
}
</style>
