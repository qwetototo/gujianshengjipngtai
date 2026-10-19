<template>
  <section v-if="!loggedIn" class="login-page" :class="{ 'login-dark': darkMode }">
    <button class="login-theme" type="button" @click="toggleTheme">
      <Sun v-if="darkMode" :size="16" />
      <Moon v-else :size="16" />
      {{ darkMode ? '浅色' : '深色' }}
    </button>
    <div class="login-shell" :class="{ 'login-dark': darkMode }">
      <section class="login-brand">
        <div class="brand-mark"><Cpu :size="26" :stroke-width="1.8" /></div>
        <div class="brand-copy">
          <p class="brand-kicker">DEVICE FIRMWARE OPERATIONS</p>
          <h1><span>OTA</span> 固件管理平台</h1>
          <p class="brand-description">固件发布、版本留存与设备升级信息，一处管理。</p>
        </div>
        <div class="brand-highlights">
          <div><strong>版本可追溯</strong><span>最新版本与历史版本清晰归档</span></div>
          <div><strong>升级清单</strong><span>按规范生成设备端版本信息</span></div>
          <div><strong>文件有校验</strong><span>自动计算摘要并提供下载</span></div>
        </div>
      </section>

      <form class="login-form" @submit.prevent="login">
        <div class="login-heading">
          <span class="login-eyebrow">ADMINISTRATOR</span>
          <h2>登录控制台</h2>
          <p>使用管理员账号继续</p>
        </div>
        <label for="admin-account">账号</label>
        <input id="admin-account" v-model.trim="loginUser" required autocomplete="username" placeholder="admin">
        <label for="admin-password">密码</label>
        <div class="password-field">
          <input id="admin-password" v-model="loginPass" required :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入管理员密码">
          <button class="password-toggle" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
            <EyeOff v-if="showPassword" :size="17" />
            <Eye v-else :size="17" />
          </button>
        </div>
        <div class="login-options">
          <label class="remember-option"><input v-model="rememberMe" type="checkbox">保持登录</label>
          <button class="text-button" type="button" @click="showForgotPassword = true">忘记密码？</button>
        </div>
        <button class="login-submit" type="submit" :disabled="busy">
          {{ busy ? '正在登录…' : '登录控制台' }}
        </button>
        <p class="login-footnote">此平台仅设置一个管理员账号</p>
      </form>
    </div>

    <div v-if="showForgotPassword" class="modal-backdrop" @click.self="showForgotPassword = false">
      <section class="reset-dialog" role="dialog" aria-modal="true" aria-labelledby="reset-title">
        <button class="dialog-close" type="button" aria-label="关闭" @click="showForgotPassword = false"><X :size="18" /></button>
        <div class="reset-icon"><KeyRound :size="20" /></div>
        <h2 id="reset-title">重置管理员密码</h2>
        <p>密码不会以明文保存在数据库。忘记密码时，请按以下步骤在服务器本机重置：</p>
        <ol>
          <li>停止后端服务，并将运行环境中的 <code>OTA_ADMIN_PASSWORD</code> 改为新密码。</li>
          <li>连接项目数据库，执行：<code>DELETE FROM admin_user WHERE username = 'admin';</code></li>
          <li>重新启动后端，系统会用新密码生成哈希并重新创建唯一的 admin 账号。</li>
        </ol>
        <button class="dialog-confirm" type="button" @click="showForgotPassword = false">知道了</button>
      </section>
    </div>
  </section>

  <section v-else-if="detail" class="detail-page">
    <header class="detail-header">
      <button class="btn-muted" @click="detail = null"><ArrowLeft :size="16" />返回列表</button>
      <h1>固件详情</h1>
    </header>
    <main class="detail-container">
      <article class="detail-card">
        <div class="detail-title">
          <span>{{ detail.product }}</span>
          <span class="ver">{{ detail.version }}</span>
        </div>
        <p class="detail-sub">固件详情信息</p>
        <dl>
          <dt>product</dt><dd class="mono">{{ detail.product }}</dd>
          <dt>hardware_id</dt><dd class="mono">{{ detail.hardware_id || '-' }}</dd>
          <dt>partition_id</dt><dd class="mono">{{ detail.partition_id || '-' }}</dd>
          <dt>sdk_compat</dt><dd class="mono">{{ detail.sdk_compat || '-' }}</dd>
          <dt>target_build</dt><dd class="mono">{{ detail.target_build || '-' }}</dd>
          <dt>version_code</dt><dd class="mono">{{ detail.version_code ?? '-' }}</dd>
          <dt>work_size</dt><dd class="mono">{{ detail.work_size ?? '-' }}</dd>
          <dt>版本简述</dt><dd>{{ detail.description }}</dd>
          <dt>release_notes</dt><dd>{{ detail.release_notes || '-' }}</dd>
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
      <div class="sidebar-logo">OTA 固件管理平台</div>
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
            <label>
              <span class="field-label">产品标识 <code>product</code></span>
              <small class="field-help">OTA 用它区分产品系列，请填写约定的英文标识，例如 df_motion。</small>
              <input v-model.trim="form.product" required placeholder="例如 df_motion">
            </label>
            <label>
              <span class="field-label">硬件型号 <code>hardware_id</code></span>
              <small class="field-help">填写本固件适用的硬件型号，必须与设备端识别值一致。</small>
              <input v-model.trim="form.hardware_id" required placeholder="例如 EC200UCN_AA">
            </label>
            <label>
              <span class="field-label">分区方案 <code>partition_id</code></span>
              <small class="field-help">填写打包时使用的分区配置标识，需与固件包匹配。</small>
              <input v-model.trim="form.partition_id" required placeholder="例如 8910_8m_novolte">
            </label>
            <label>
              <span class="field-label">SDK 兼容标识 <code>sdk_compat</code></span>
              <small class="field-help">说明固件适用的模组与 SDK 组合，请按构建信息填写。</small>
              <input v-model.trim="form.sdk_compat" required placeholder="例如 EC200UCN_AA_8915DM_novolte_8m_v1">
            </label>
            <label>
              <span class="field-label">构建标识 <code>target_build</code></span>
              <small class="field-help">标记这一次固件构建，建议直接从构建说明中复制。</small>
              <input v-model.trim="form.target_build" required placeholder="例如 df_motion_20261014_blefull3">
            </label>
            <label>
              <span class="field-label">固件版本 <code>version</code></span>
              <small class="field-help">设备当前版本与用户可见的版本号，例如 1.1.2。</small>
              <input v-model.trim="form.version" required placeholder="例如 1.1.2">
            </label>
            <label>
              <span class="field-label">版本比较序号 <code>version_code</code></span>
              <small class="field-help">用于设备判断是否升级；必须大于设备当前版本对应的序号。</small>
              <input v-model.number="form.version_code" required type="number" min="1" max="4294967295" placeholder="例如 10102">
            </label>
            <label>
              <span class="field-label">升级工作区大小（字节） <code>work_size</code></span>
              <small class="field-help">从 SDK 打包报告读取；这是设备升级所需空间，不是 .pack 文件大小。</small>
              <input v-model.number="form.work_size" required type="number" min="0" max="4294967295" placeholder="例如 65536">
            </label>
            <label>
              <span class="field-label">发布人</span>
              <small class="field-help">记录本次固件发布的负责人。</small>
              <input v-model.trim="form.uploader" required placeholder="填写姓名或工号">
            </label>
            <label>
              <span class="field-label">固件包</span>
              <small class="field-help">选择本次发布的完整升级包，仅支持 .pack 文件。</small>
              <input required type="file" accept=".pack" @change="form.file = $event.target.files[0]">
            </label>
            <label class="full">
              <span class="field-label">版本简述</span>
              <small class="field-help">平台中供维护人员查看的简短说明。</small>
              <textarea v-model.trim="form.description" placeholder="例如：修复定位漂移问题"></textarea>
            </label>
            <label class="full">
              <span class="field-label">设备端更新说明 <code>release_notes</code>（可选）</span>
              <small class="field-help">写入版本清单、可供设备端读取，最多 1024 字节。</small>
              <textarea v-model.trim="form.release_notes" maxlength="1024" placeholder="例如：优化蓝牙连接稳定性"></textarea>
            </label>
          </div>
          <div class="form-actions">
            <button class="btn-primary" :disabled="busy">{{ busy ? '上传中...' : '上传' }}</button>
          <span>仅允许 .pack 文件；固定清单字段由系统填写，文件大小、MD5 和 SHA-256 由服务器计算。</span>
          </div>
        </form>
      </section>

      <section v-if="page === 'list'">
        <h2 class="page-title">固件列表</h2>
        <div class="list-card">
          <div class="list-header">
            <h3>所有版本</h3>
            <div class="list-tools">
              <select v-model="selectedProduct" aria-label="按 product 筛选">
                <option value="">全部产品</option>
                <option v-for="product in products" :key="product" :value="product">{{ product }}</option>
              </select>
              <span>{{ filteredFirmware.length }} 个版本</span>
            </div>
          </div>
          <table v-if="filteredFirmware.length">
            <thead><tr><th>版本状态</th><th>产品</th><th>版本</th><th>版本简述</th><th>大小</th><th>上传时间</th><th>上传人</th><th>下载地址</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in filteredFirmware" :key="item.id">
                <td><span class="version-state" :class="item.latest ? 'latest' : 'historical'">{{ item.latest ? '最新版本' : '历史版本' }}</span></td>
                <td><button class="tag-link" @click="detail = item">{{ item.product }}</button></td>
                <td><strong>{{ item.version }}</strong></td>
                <td><button class="desc" :title="item.description" @click="detail = item">{{ item.description }}</button></td>
                <td class="muted">{{ formatSize(item.file_size) }}</td>
                <td class="muted">{{ formatTime(item.upload_time) }}</td>
                <td>{{ item.uploader }}</td>
                <td class="url-cell"><a :href="downloadUrl(item)" target="_blank">{{ downloadUrl(item) }}</a><button @click="copy(downloadUrl(item))">复制</button></td>
                <td><button class="btn-del" @click="remove(item)"><Trash2 :size="14" />删除</button></td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">{{ firmware.length ? '该产品暂无固件' : '暂无固件，请先上传' }}</div>
        </div>
      </section>

      <section v-if="page === 'logs'">
        <h2 class="page-title">操作日志</h2>
        <div class="list-card">
          <div class="filters">
            <select v-model="logAction"><option value="">全部</option><option value="upload">上传</option><option value="download">下载</option><option value="delete">删除</option><option value="storage_warning">磁盘预警</option><option value="storage_recovered">空间恢复</option></select>
            <input v-model.trim="logKeyword" placeholder="产品 / 版本 / 操作人">
            <button @click="loadAll"><RefreshCw :size="14" />刷新</button>
            <span>{{ filteredLogs.length }} 条记录</span>
          </div>
          <table v-if="filteredLogs.length">
            <thead><tr><th>时间</th><th>操作类型</th><th>产品</th><th>版本</th><th>操作人</th><th>详情</th></tr></thead>
            <tbody>
              <tr v-for="log in filteredLogs" :key="log.id">
                <td class="muted">{{ formatTime(log.occurred_at) }}</td>
                <td><span class="badge" :class="log.action">{{ actionLabel[log.action] || log.action }}</span></td>
                <td>{{ log.product }}</td><td>{{ log.version }}</td><td>{{ log.operator }}</td><td>{{ log.detail }}</td>
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
import { Cpu, Eye, EyeOff, KeyRound, Moon, Sun, Trash2, X } from '@lucide/vue'

const API = import.meta.env.VITE_API_BASE_URL || ''
const authToken = ref(sessionStorage.getItem('otaToken') || localStorage.getItem('otaToken') || '')
const loggedIn = ref(Boolean(authToken.value))
const loginUser = ref('admin')
const loginPass = ref('')
const currentUser = ref(sessionStorage.getItem('otaUsername') || localStorage.getItem('otaUsername') || '')
const rememberMe = ref(Boolean(localStorage.getItem('otaToken')))
const showPassword = ref(false)
const showForgotPassword = ref(false)
const darkMode = ref(localStorage.getItem('otaTheme') === 'dark')
const page = ref('upload')
const detail = ref(null)
const firmware = ref([])
const logs = ref([])
const selectedProduct = ref('')
const busy = ref(false)
const toastText = ref('')
const storageStatus = ref(null)
let storageTimer
const logAction = ref('')
const logKeyword = ref('')
const actionLabel = { upload: '上传', download: '下载', delete: '删除', storage_warning: '磁盘预警', storage_recovered: '空间恢复' }
const form = reactive({ product: '', hardware_id: '', partition_id: '', sdk_compat: '', target_build: '', version: '', version_code: null, work_size: null, release_notes: '', uploader: '', description: '', file: null })

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
    return `${log.product} ${log.version} ${log.operator} ${log.detail}`.toLowerCase().includes(keyword)
  })
})

const products = computed(() => [...new Set(firmware.value.map(item => item.product).filter(Boolean))]
  .sort((left, right) => left.localeCompare(right, 'zh-CN')))
const filteredFirmware = computed(() => selectedProduct.value
  ? firmware.value.filter(item => item.product === selectedProduct.value)
  : firmware.value)
watch(products, types => {
  if (selectedProduct.value && !types.includes(selectedProduct.value)) {
    selectedProduct.value = ''
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
    if (!res.ok) throw new Error(await responseMessage(res, '登录失败'))
    const result = await res.json()
    authToken.value = result.token
    currentUser.value = result.username
    clearStoredCredentials()
    const storage = rememberMe.value ? localStorage : sessionStorage
    storage.setItem('otaToken', result.token)
    storage.setItem('otaUsername', result.username)
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
  clearStoredCredentials()
  loggedIn.value = false
  detail.value = null
  loginPass.value = ''
}

function clearStoredCredentials() {
  for (const storage of [sessionStorage, localStorage]) {
    storage.removeItem('otaToken')
    storage.removeItem('otaUsername')
  }
}

function toggleTheme() {
  darkMode.value = !darkMode.value
  localStorage.setItem('otaTheme', darkMode.value ? 'dark' : 'light')
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
  const productKey = form.product.trim().toLowerCase()
  const existing = firmware.value.find(item => (item.product || '').toLowerCase() === productKey && item.latest)
  if (existing && !confirm(`产品 ${existing.product} 当前最新版本为 ${existing.version}，继续发布会将其保留为历史版本，确定吗？`)) return
  busy.value = true
  const body = new FormData()
  for (const key of ['product', 'hardware_id', 'partition_id', 'sdk_compat', 'target_build', 'version', 'version_code', 'work_size', 'release_notes', 'description', 'uploader']) body.append(key, form[key] ?? '')
  body.append('file', form.file)
  try {
    const res = await apiFetch('/api/admin/firmware/upload', { method: 'POST', body })
    if (!res.ok) return showToast(await responseMessage(res, '上传失败'))
    Object.assign(form, { product: '', hardware_id: '', partition_id: '', sdk_compat: '', target_build: '', version: '', version_code: null, work_size: null, release_notes: '', description: '', file: null, uploader: currentUser.value })
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
  if (!confirm(`确定删除 ${item.product} ${item.version} 吗？`)) return
  try {
    const res = await apiFetch(`/api/admin/firmware/${item.id}?operator=${encodeURIComponent(currentUser.value)}`, { method: 'DELETE' })
    if (!res.ok) throw new Error('删除失败')
    await loadAll()
    showToast('已删除')
  } catch (error) {
    showToast(error.message || '删除失败，请检查服务连接')
  }
}

function downloadUrl(item) {
  return `/api/firmware/download/${item.id}`
}

function versionUrl(item) {
  return `/api/firmware/version?product=${encodeURIComponent(item.product)}`
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

async function responseMessage(res, fallback) {
  const body = await res.text().catch(() => '')
  if (!body) return fallback
  try {
    const payload = JSON.parse(body)
    return payload.message || payload.error || fallback
  } catch {
    return body
  }
}

function showToast(text) {
  toastText.value = text
  setTimeout(() => { toastText.value = '' }, 1800)
}
</script>
