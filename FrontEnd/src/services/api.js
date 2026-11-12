import { metalTechData } from '../data/metaltechData'

const config = metalTechData.api
const STORAGE = config.demo.storagePrefix
const readStore = (key, fallback) => {
  try { const value = localStorage.getItem(`${STORAGE}${key}`); return value ? JSON.parse(value) : fallback } catch { return fallback }
}
const writeStore = (key, value) => localStorage.setItem(`${STORAGE}${key}`, JSON.stringify(value))
const tokenKey = `${STORAGE}token`
const sessionStorageKey = `${STORAGE}session`
const apiUrlKey = `${STORAGE}apiBaseUrl`
const getBaseUrl = () => localStorage.getItem(apiUrlKey) || config.baseUrl
export const isDemoMode = () => config.mode === 'demo'
export const getToken = () => localStorage.getItem(tokenKey) || sessionStorage.getItem(tokenKey)
export const getStoredSession = () => {
  const token = getToken()
  if (!token || (config.mode === 'rest' && token === 'demo-session')) return null
  try { return JSON.parse(localStorage.getItem(sessionStorageKey) || sessionStorage.getItem(sessionStorageKey) || 'null') } catch { return null }
}
const saveSession = (session, token, remember = true) => {
  const persistent = remember ? localStorage : sessionStorage
  const temporary = remember ? sessionStorage : localStorage
  persistent.setItem(sessionStorageKey, JSON.stringify(session))
  if (token) persistent.setItem(tokenKey, token)
  temporary.removeItem(sessionStorageKey); temporary.removeItem(tokenKey)
}

async function http(path, { method = 'GET', body, auth = true, query } = {}) {
  const url = new URL(`${getBaseUrl().replace(/\/$/, '')}${path}`)
  Object.entries(query || {}).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value) })
  const headers = { Accept: 'application/json', ...config.headers }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth && getToken()) headers.Authorization = `${config.auth.tokenType} ${getToken()}`
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), config.timeoutMs)
  try {
    const response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal, credentials: config.credentials })
    const raw = await response.text()
    const payload = raw ? JSON.parse(raw) : null
    if (response.status === 401) { localStorage.removeItem(tokenKey); sessionStorage.removeItem(tokenKey); localStorage.removeItem(sessionStorageKey); sessionStorage.removeItem(sessionStorageKey) }
    if (!response.ok) { const error = new Error(payload?.message || payload?.error || `Erro HTTP ${response.status}`); error.status = response.status; throw error }
    return payload
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('A API demorou para responder. Tente novamente.', { cause: error })
    if (error instanceof TypeError) throw new Error('Não foi possível conectar à API. Verifique URL, servidor e CORS.', { cause: error })
    throw error
  } finally { window.clearTimeout(timeout) }
}
const demoUsers = () => readStore('users', metalTechData.account.users)
const demoAlerts = () => readStore('alerts', metalTechData.operation.alerts)
const demoSettings = () => readStore('settings', metalTechData.configuration)

export const api = {
  async login(credentials) {
    if (!isDemoMode()) {
      const result = await http(config.endpoints.auth.login.path, { method: config.endpoints.auth.login.method, body: credentials, auth: false })
      const token = result?.[config.auth.responseTokenField] || result?.token || result?.accessToken
      const user = result?.[config.auth.responseUserField] || result?.user
      if (!token || !user) throw new Error('Resposta de login inválida: esperado token e usuário.')
      saveSession(user, token, credentials.remember !== false); return user
    }
    if (!credentials.email || !credentials.password) throw new Error('Informe e-mail e senha.')
    const user = demoUsers().find((item) => item.email.toLowerCase() === credentials.email.trim().toLowerCase())
    if (!user) throw new Error('Usuário não encontrado no modo demonstração. Use um e-mail cadastrado.')
    const session = { ...user, demo: true }; saveSession(session, 'demo-session', credentials.remember !== false); return session
  },
  async logout() {
    if (!isDemoMode() && getToken()) { try { await http(config.endpoints.auth.logout.path, { method: config.endpoints.auth.logout.method }) } catch { /* limpa sessão local mesmo se API indisponível */ } }
    localStorage.removeItem(tokenKey); sessionStorage.removeItem(tokenKey); localStorage.removeItem(sessionStorageKey); sessionStorage.removeItem(sessionStorageKey)
  },
  async me() { return isDemoMode() ? getStoredSession() : http(config.endpoints.auth.me.path) },
  async getStation() { if (isDemoMode()) return metalTechData.operation.station; return http(config.endpoints.stations.detail.path.replace(':id', encodeURIComponent(metalTechData.operation.station.id)), { method: config.endpoints.stations.detail.method }) },
  async listSensors() { if (isDemoMode()) return metalTechData.operation.sensors; const result = await http(config.endpoints.sensors.list.path, { method: config.endpoints.sensors.list.method }); return Array.isArray(result) ? result : result?.data || [] },
  async listReadings(query = {}) { if (isDemoMode()) return readStore('readings', metalTechData.operation.readings); const result = await http(config.endpoints.readings.list.path, { method: config.endpoints.readings.list.method, query }); return Array.isArray(result) ? result : result?.data || [] },
  async listProduction(query = {}) { if (isDemoMode()) return metalTechData.operation.hourlyProduction; const result = await http(config.endpoints.analytics.production.path, { method: config.endpoints.analytics.production.method, query }); return Array.isArray(result) ? result : result?.data || [] },
  async getSettings() { if (isDemoMode()) return demoSettings(); return http(config.endpoints.settings.get.path, { method: config.endpoints.settings.get.method }) },
  async listUsers() {
    if (isDemoMode()) return demoUsers()
    const result = await http(config.endpoints.users.list.path, { method: config.endpoints.users.list.method }); return Array.isArray(result) ? result : result?.data || []
  },
  async createUser(payload) {
    if (isDemoMode()) { const users = demoUsers(); if (users.some((item) => item.email.toLowerCase() === payload.email.toLowerCase())) throw new Error('Já existe um usuário com este e-mail.'); const created = { ...payload, id: `LOCAL-${Date.now()}`, status: 'Pendente' }; writeStore('users', [...users, created]); return created }
    return http(config.endpoints.users.create.path, { method: config.endpoints.users.create.method, body: payload })
  },
  async updateUser(id, payload) {
    if (isDemoMode()) { const updated = demoUsers().map((item) => item.id === id ? { ...item, ...payload } : item); writeStore('users', updated); return updated.find((item) => item.id === id) }
    return http(config.endpoints.users.update.path.replace(':id', encodeURIComponent(id)), { method: config.endpoints.users.update.method, body: payload })
  },
  async deleteUser(id) {
    if (isDemoMode()) { writeStore('users', demoUsers().filter((item) => item.id !== id)); return { success: true } }
    return http(config.endpoints.users.delete.path.replace(':id', encodeURIComponent(id)), { method: config.endpoints.users.delete.method })
  },
  async listAlerts() {
    if (isDemoMode()) return demoAlerts()
    const result = await http(config.endpoints.alerts.list.path, { method: config.endpoints.alerts.list.method }); return Array.isArray(result) ? result : result?.data || []
  },
  async updateAlert(id, payload) {
    if (isDemoMode()) { const updated = demoAlerts().map((item) => item.id === id ? { ...item, ...payload } : item); writeStore('alerts', updated); return updated.find((item) => item.id === id) }
    return http(config.endpoints.alerts.update.path.replace(':id', encodeURIComponent(id)), { method: config.endpoints.alerts.update.method, body: payload })
  },
  async saveSettings(payload) {
    if (payload?.integration?.apiUrl) localStorage.setItem(apiUrlKey, payload.integration.apiUrl)
    if (isDemoMode()) { const current = demoSettings(); const next = { ...current, alertThresholds: { ...current.alertThresholds, ...payload.alertThresholds }, collection: { ...current.collection, ...payload.collection }, integration: { ...current.integration, ...payload.integration }, station: { ...current.station, ...payload.station } }; writeStore('settings', next); return next }
    return http(config.endpoints.settings.update.path, { method: config.endpoints.settings.update.method, body: payload })
  },
  async health() { return isDemoMode() ? { status: 'demo', message: 'Modo demonstração: dados locais persistidos neste navegador.' } : http(config.endpoints.health.path, { method: config.endpoints.health.method, auth: false }) },
  async exportReadings(query = {}) { if (isDemoMode()) return readStore('readings', metalTechData.operation.readings); return http(config.endpoints.readings.export.path, { method: config.endpoints.readings.export.method, query }) }
}
