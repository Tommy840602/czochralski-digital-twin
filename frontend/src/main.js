import './styles/globals.css'
import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { initTheme } from './composables/useTheme.js'

// 掛載前套用記憶的主題（預設深色），避免首屏閃爍
initTheme()

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
