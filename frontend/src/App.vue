<template>
  <section v-if="!loggedIn" class="login-page">
    <form class="login-box" @submit.prevent="login">
      <h1>固件管理后台</h1>
      <input v-model.trim="loginUser" placeholder="管理员账号" autocomplete="username">
      <input v-model="loginPass" type="password" placeholder="管理员密码" autocomplete="current-password">
      <button type="submit" :disabled="busy">{{ busy ? '登录中...' : '登录' }}</button>
      <p>请使用已配置的管理员账号登录</p>
    </form>
  </section>

  <section v-else-if="detail" class="detail-page">
    <header class="detail-header">
      <button class="btn-muted" @click="detail = null"><ArrowLeft :size="16" />返回列表</button>
      <h1>固件详情</h1>
    </header>
    <main class="detail-container">
      <article class="detail-card">
        <div class="detail-title">
          <span>{{ detail.device_model }}</span>
          <span class="ver">{{ detail.version }}</span>
        </div>
        <p class="detail-sub">固件详情信息</p>
        <dl>
          <dt>设备类型</dt><dd>{{ detail.device_model }}</dd>
          <dt>版本</dt><dd class="mono">{{ detail.version }}</dd>
          <dt>版本简述</dt><dd>{{ detail.description }}</dd>
          <dt>文件大小</dt><dd>{{ formatSize(detail.file_size) }}（{{ detail.file_size }} 字节）</dd>
          <dt>MD5</dt><dd class="mono">{{ detail.md5 }}</dd>
          <dt>上传时间</dt><dd>{{ formatTime(detail.upload_time) }}</dd>
          <dt>上传人</dt><dd>{{ detail.uploader }}</dd>
          <dt>下载地址</dt>
          <dd class="url-row">
            <a :href="downloadUrl(detail)" target="_blank">{{ downloadUrl(detail) }}</a>
            <button class="btn-muted" @click="copy(downloadUrl(detail))"><CopyIcon :size="14" />复制</button>
          </dd>
          <dt>version.json</dt>
          <dd class="url-row">
            <a :href="versionUrl(detail)" target="_blank">{{ versionUrl(detail) }}</a>
            <button class="btn-muted" @click="copy(versionUrl(detail))"><CopyIcon :size="14" />复制</button>
          </dd>
        </dl>
      </article>
    </main>
  </section>

  <section v-else class="layout">
    <aside class="sidebar">
      <div class="sidebar-logo">固件管理后台</div>
      <nav>
        <button :class="{ active: page === 'upload' }" @click="page = 'upload'"><ArrowUpFromLine :size="17" />上传固件</button>
        <button :class="{ active: page === 'list' }" @click="page = 'list'"><ListIcon :size="17" />固件列表</button>
        <button :class="{ active: page === 'logs' }" @click="page = 'logs'"><FileClock :size="17" />操作日志</button>
      </nav>
      <div class="sidebar-user">
        <span>{{ currentUser }}</span>
        <button @click="logout"><LogOut :size="15" />退出登录</button>
      </div>
    </aside>

    <main class="content">
      <div v-if="storageStatus?.warning" class="storage-warning" role="alert">
        <strong>存储空间预警</strong>
        <span>{{ storageStatus.message }}</span>
      </div>
      <section v-if="page === 'upload'">
        <h2 class="page-title">上传固件</h2>
        <form class="upload-card" @submit.prevent="upload">
          <div class="form-grid">
            <label>设备类型 <input v-model.trim="form.device_model" required placeholder="如 EC200UCN_AA"></label>
            <label>上传版本 <input v-model.trim="form.version" required placeholder="如 1.0.0"></label>
            <label>上传人 <input v-model.trim="form.uploader" required placeholder="如 zhangsan"></label>
            <label>固件文件 <input required type="file" accept=".pack" @change="form.file = $event.target.files[0]"></label>
            <label class="full">版本简述 <textarea v-model.trim="form.description" placeholder="如：修复定位漂移问题"></textarea></label>
          </div>
          <div class="form-actions">
            <button class="btn-primary" :disabled="busy">{{ busy ? '上传中...' : '上传' }}</button>
          <span>仅允许 .pack 文件；同一设备类型上传会覆盖旧版本并删除旧文件；版本号默认从版本中自动提取（如 1.0.0 → 1）；大小和 MD5 由服务器计算。</span>
          </div>
        </form>
      </section>

      <section v-if="page === 'list'">
        <h2 class="page-title">固件列表</h2>
        <div class="list-card">
          <div class="list-header">
            <h3>所有版本</h3>
            <div class="list-tools">
              <select v-model="selectedDeviceType" aria-label="按设备类型筛选">
                <option value="">全部设备类型</option>
                <option v-for="deviceType in deviceTypes" :key="deviceType" :value="deviceType">{{ deviceType }}</option>
              </select>
              <span>{{ filteredFirmware.length }} 个版本</span>
            </div>
          </div>
          <table v-if="filteredFirmware.length">
            <thead><tr><th>设备类型</th><th>版本</th><th>版本简述</th><th>大小</th><th>上传时间</th><th>上传人</th><th>下载地址</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in filteredFirmware" :key="item.id">
                <td><button class="tag-link" @click="detail = item">{{ item.device_model }}</button></td>
                <td><span class="ver-flow"><strong>{{ item.version }}</strong><small v-if="item.previous_version">上一版 {{ item.previous_version }}</small></span></td>
                <td><button class="desc" :title="item.description" @click="detail = item">{{ item.description }}</button></td>
                <td class="muted">{{ formatSize(item.file_size) }}</td>
                <td class="muted">{{ formatTime(item.upload_time) }}</td>
                <td>{{ item.uploader }}</td>
                <td class="url-cell"><a :href="downloadUrl(item)" target="_blank">{{ downloadUrl(item) }}</a><button @click="copy(downloadUrl(item))">复制</button></td>
                <td><button v-if="item.previous_version" class="btn-del" @click="rollbackFirmware(item)"><RotateCcw :size="14" />回滚</button> <button class="btn-del" @click="remove(item)"><Trash2 :size="14" />删除</button></td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">{{ firmware.length ? '该设备类型暂无固件' : '暂无固件，请先上传' }}</div>
        </div>
      </section>

      <section v-if="page === 'logs'">
        <h2 class="page-title">操作日志</h2>
        <div class="list-card">
          <div class="filters">
            <select v-model="logAction"><option value="">全部</option><option value="upload">上传</option><option value="download">下载</option><option value="delete">删除</option><option value="storage_warning">磁盘预警</option><option value="storage_recovered">空间恢复</option></select>
            <input v-model.trim="logKeyword" placeholder="设备类型 / 版本 / 操作人">
            <button @click="loadAll"><RefreshCw :size="14" />刷新</button>
            <span>{{ filteredLogs.length }} 条记录</span>
          </div>
          <table v-if="filteredLogs.length">
            <thead><tr><th>时间</th><th>操作类型</th><th>设备类型</th><th>版本</th><th>操作人</th><th>详情</th></tr></thead>
            <tbody>
              <tr v-for="log in filteredLogs" :key="log.id">
                <td class="muted">{{ formatTime(log.occurred_at) }}</td>
                <td><span class="badge" :class="log.action">{{ actionLabel[log.action] || log.action }}</span></td>
                <td>{{ log.device_model }}</td><td>{{ log.version }}</td><td>{{ log.operator }}</td><td>{{ log.detail }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">暂无日志</div>
        </div>
      </section>
    </main>
  </section>

  <div v-show="toastText" class="toast">{{ toastText }}</div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { RotateCcw, Trash2 } from '@lucide/vue'

const API = import.meta.env.VITE_API_BASE_URL || ''
const authToken = ref(sessionStorage.getItem('otaToken') || '')
const loggedIn = ref(Boolean(authToken.value))
const loginUser = ref('admin')
const loginPass = ref('')
const currentUser = ref(sessionStorage.getItem('otaUsername') || '')
const page = ref('upload')
const detail = ref(null)
const firmware = ref([])
const logs = ref([])
const selectedDeviceType = ref('')
const busy = ref(false)
const toastText = ref('')
const storageStatus = ref(null)
let storageTimer
const logAction = ref('')
const logKeyword = ref('')
const actionLabel = { upload: '上传', download: '下载', delete: '删除', rollback: '回滚', storage_warning: '磁盘预警', storage_recovered: '空间恢复' }
const form = reactive({ device_model: '', version: '', uploader: '', description: '', file: null })

onMounted(() => {
  if (loggedIn.value) loadAll().catch(() => clearSession())
  storageTimer = window.setInterval(() => {
    if (loggedIn.value) refreshMonitoring().catch(() => {})
  }, 60000)
})

onUnmounted(() => window.clearInterval(storageTimer))

const filteredLogs = computed(() => {
  const keyword = logKeyword.value.toLowerCase()
  return logs.value.filter(log => {
    if (logAction.value && log.action !== logAction.value) return false
    if (!keyword) return true
    return `${log.device_model} ${log.version} ${log.operator} ${log.detail}`.toLowerCase().includes(keyword)
  })
})

const deviceTypes = computed(() => [...new Set(firmware.value.map(item => item.device_model).filter(Boolean))]
  .sort((left, right) => left.localeCompare(right, 'zh-CN')))
const filteredFirmware = computed(() => selectedDeviceType.value
  ? firmware.value.filter(item => item.device_model === selectedDeviceType.value)
  : firmware.value)
watch(deviceTypes, types => {
  if (selectedDeviceType.value && !types.includes(selectedDeviceType.value)) {
    selectedDeviceType.value = ''
  }
})

async function login() {
  if (!loginUser.value || !loginPass.value) return showToast('请输入管理员账号和密码')
  busy.value = true
  try {
    const res = await fetch(`${API}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: loginUser.value, password: loginPass.value })
    })
    const result = await res.json()
    if (!res.ok) throw new Error(result.message || '登录失败')
    authToken.value = result.token
    currentUser.value = result.username
    sessionStorage.setItem('otaToken', result.token)
    sessionStorage.setItem('otaUsername', result.username)
    form.uploader = currentUser.value
    loggedIn.value = true
    await loadAll()
  } catch (error) {
    showToast(error.message || '登录失败，请检查服务连接')
  } finally {
    busy.value = false
  }
}

function clearSession() {
  authToken.value = ''
  sessionStorage.removeItem('otaToken')
  sessionStorage.removeItem('otaUsername')
  loggedIn.value = false
  detail.value = null
  loginPass.value = ''
}

async function logout() {
  try {
    if (authToken.value) await fetch(`${API}/api/auth/logout`, { method: 'POST', headers: { Authorization: `Bearer ${authToken.value}` } })
  } finally {
    clearSession()
  }
}

async function apiFetch(path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (authToken.value) headers.set('Authorization', `Bearer ${authToken.value}`)
  const res = await fetch(`${API}${path}`, { ...options, headers })
  if (res.status === 401) clearSession()
  return res
}

async function loadAll() {
  const [fwRes, logRes, storageRes] = await Promise.all([
    apiFetch('/api/admin/firmware'), apiFetch('/api/logs'), apiFetch('/api/system/storage')
  ])
  if (!fwRes.ok || !logRes.ok || !storageRes.ok) throw new Error('加载控制台数据失败')
  firmware.value = await fwRes.json()
  logs.value = await logRes.json()
  storageStatus.value = await storageRes.json()
}

async function refreshMonitoring() {
  const [storageRes, logsRes] = await Promise.all([apiFetch('/api/system/storage'), apiFetch('/api/logs')])
  if (storageRes.ok) storageStatus.value = await storageRes.json()
  if (logsRes.ok) logs.value = await logsRes.json()
}

async function upload() {
  if (!form.file) return showToast('请选择固件文件')
  const deviceKey = form.device_model.trim().toLowerCase()
  const existing = firmware.value.find(item => (item.device_model || '').toLowerCase() === deviceKey)
  if (existing && !confirm(`设备 ${existing.device_model} 当前为 ${existing.version}，继续上传会将其保存为可回滚的上一版，确定吗？`)) return
  busy.value = true
  const body = new FormData()
  for (const key of ['device_model', 'version', 'description', 'uploader']) body.append(key, form[key])
  body.append('file', form.file)
  try {
    const res = await apiFetch('/api/admin/firmware/upload', { method: 'POST', body })
    if (!res.ok) return showToast((await res.json()).message || '上传失败')
    Object.assign(form, { device_model: '', version: '', description: '', file: null, uploader: currentUser.value })
    await loadAll()
    page.value = 'list'
    showToast('上传成功')
  } catch (error) {
    showToast(error.message || '上传失败，请检查服务连接')
  } finally {
    busy.value = false
  }
}

async function remove(item) {
  if (!confirm(`确定删除 ${item.device_model} ${item.version} 吗？`)) return
  try {
    const res = await apiFetch(`/api/admin/firmware/${item.id}?operator=${encodeURIComponent(currentUser.value)}`, { method: 'DELETE' })
    if (!res.ok) throw new Error('删除失败')
    await loadAll()
    showToast('已删除')
  } catch (error) {
    showToast(error.message || '删除失败，请检查服务连接')
  }
}

async function rollbackFirmware(item) {
  if (!confirm(`确定将 ${item.device_model} 从 ${item.version} 回滚到 ${item.previous_version} 吗？回滚后当前版会成为新的上一版。`)) return
  try {
    const res = await apiFetch(`/api/admin/firmware/${item.id}/rollback?operator=${encodeURIComponent(currentUser.value)}`, { method: 'POST' })
    if (!res.ok) throw new Error((await res.json()).message || '回滚失败')
    await loadAll()
    showToast('已回滚到上一版')
  } catch (error) {
    showToast(error.message || '回滚失败')
  }
}

function downloadUrl(item) {
  return `/api/firmware/download/${item.id}`
}

function versionUrl(item) {
  return `/api/firmware/version?device_model=${encodeURIComponent(item.device_model)}&current_version=`
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function formatTime(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

async function copy(text) {
  await navigator.clipboard.writeText(text)
  showToast('已复制')
}

function showToast(text) {
  toastText.value = text
  setTimeout(() => { toastText.value = '' }, 1800)
}
</script>
