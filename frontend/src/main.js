import { createApp } from 'vue'
import { ArrowUpFromLine, FileClock, List, LogOut, RefreshCw, Trash2, Copy, ArrowLeft } from '@lucide/vue'
import './style.css'
import App from './App.vue'

const app = createApp(App)
app.component('ArrowUpFromLine', ArrowUpFromLine)
app.component('FileClock', FileClock)
app.component('ListIcon', List)
app.component('LogOut', LogOut)
app.component('RefreshCw', RefreshCw)
app.component('Trash2', Trash2)
app.component('CopyIcon', Copy)
app.component('ArrowLeft', ArrowLeft)
app.mount('#app')
