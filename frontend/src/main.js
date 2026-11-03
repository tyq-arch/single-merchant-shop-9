import { createApp } from 'vue'
import { createPinia } from 'pinia'

// Element Plus 组件与样式由 unplugin-vue-components 按需引入（见 vite.config.js）。
// 这里只显式引入「服务式 API」的样式：ElMessage / ElMessageBox / v-loading。
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/loading/style/css'

import App from './App.vue'
import router from './router'
import './styles/variables.css'
import './styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
