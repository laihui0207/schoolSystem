<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { userStore } from '@/store/user'
import { PAGES } from '@/utils/navigate'

const LOGIN_PATH = PAGES.login.slice(1)

// ponytail: 启动页是登录页（pages[0]），已有会话且确实落在登录页时才切到首页；
// H5 直接打开 #/pages/trend/index 这类深链接时不能被劫持回首页。
// token 是否仍有效交给首个请求的 401 → refresh → 失败跳登录 链路处理，启动时不额外探测
onLaunch((options?: { path?: string }) => {
  const launchPath = options?.path ?? LOGIN_PATH
  if (userStore.isLoggedIn.value && launchPath === LOGIN_PATH) {
    uni.switchTab({ url: PAGES.home })
  }
})
</script>

<style lang="scss">
@use '@/styles/common.scss';
</style>
