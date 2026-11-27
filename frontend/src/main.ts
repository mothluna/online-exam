import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus'
import {
  ArrowDown,
  Calendar,
  Check,
  DataAnalysis,
  Delete,
  Document,
  Download,
  EditPen,
  Files,
  HomeFilled,
  Plus,
  Reading,
  Timer,
  UploadFilled,
  User,
} from '@element-plus/icons-vue'
// 按需引入后需要基础变量；message/message-box/loading 是函数式或指令，需显式引样式
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-loading.css'
import './styles/index.css'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './utils/request'
import { useUserStore } from './stores/user'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// v-loading 是指令，不在模板组件解析范围内，需显式注册
app.use(ElLoading)

// 只注册实际用到的图标：菜单 icon 是字符串，靠 <component :is="item.icon" /> 运行时解析
const icons = {
  ArrowDown,
  Calendar,
  Check,
  DataAnalysis,
  Delete,
  Document,
  Download,
  EditPen,
  Files,
  HomeFilled,
  Plus,
  Reading,
  Timer,
  UploadFilled,
  User,
}
for (const [name, component] of Object.entries(icons)) {
  app.component(name, component)
}

// 401 统一处理：清本地状态并回登录页，request 层不需要认识 router
setUnauthorizedHandler(() => {
  const userStore = useUserStore()
  userStore.clear()
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.replace({ path: '/login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
