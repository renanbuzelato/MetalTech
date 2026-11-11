import { metalTechData } from '../data/metaltechData'

const config = metalTechData.api
const STORAGE = config.demo.storagePrefix
const readStore = (key, fallback) => {
  try {
    const value = localStorage.getItem(`${STORAGE}${key}`)
    return value ? JSON.parse(value) : fallback
  } catch {
    return fallback
  }
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
  try {
    return JSON.parse(localStorage.getItem(sessionStorageKey) || sessionStorage.getItem(sessionStorageKey) || 'null')
  } catch {
    return null
  }
}

const saveSession = (session, token, remember = true) => {
  const persistent = remember ? localStorage : sessionStorage
  const temporary = remember ? sessionStorage : localStorage
  persistent.setItem(sessionStorageKey, JSON.stringify(session))
  if (token) persistent.setItem(tokenKey, token)
  temporary.removeItem(sessionStorageKey)
  temporary.removeItem(tokenKey)
}

const unwrapList = (result) => Array.isArray(result) ? result : result?.data || []
const unwrapObject = (result) => result?.data ?? result

async function http(path, { method = 'GET', body, auth = true, query } = {}) {
  let url
  try {
    url = new URL(`${getBaseUrl().replace(/\/$/, '')}${path}`)
  } catch (error) {
    throw new Error('A URL configurada para a API é inválida.', { cause: error })
  }
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value)
  })

  const headers = { Accept: 'application/json', ...config.headers }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth && getToken()) headers.Authorization = `${config.auth.tokenType} ${getToken()}`

  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), config.timeoutMs)
  try {
    const response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
      credentials: config.credentials,
    })
    const raw = await response.text()
    let payload = null
    if (raw) {
      try {
        payload = JSON.parse(raw)
      } catch {
        payload = { message: raw }
      }
    }

    if (response.status === 401) {
      localStorage.removeItem(tokenKey)
      sessionStorage.removeItem(tokenKey)
      localStorage.removeItem(sessionStorageKey)
      sessionStorage.removeItem(sessionStorageKey)
    }
    if (!response.ok) {
      const error = new Error(payload?.message || payload?.error || `Erro HTTP ${response.status}`)
      error.status = response.status
      throw error
    }
    return payload
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('A API demorou para responder. Tente novamente.', { cause: error })
    if (error instanceof TypeError) throw new Error('Não foi possível conectar à API. Verifique URL, servidor e CORS.', { cause: error })
    throw error
  } finally {
    window.clearTimeout(timeout)
  }
}

const demoUsers = () => readStore('users', metalTechData.account.users)
const demoAlerts = () => readStore('alerts', metalTechData.operation.alerts)
const demoSettings = () => readStore('settings', metalTechData.configuration)

const endpoint = (group, name) => config.endpoints[group][name]

export const api = {
  async login(credentials) {
    if (!isDemoMode()) {
      try {
        const route = endpoint('auth', 'login')
        const result = await http(route.path, { method: route.method, body: { email: credentials.email, password: credentials.password }, auth: false })
        const token = result?.[config.auth.responseTokenField] || result?.token || result?.accessToken
        const user = result?.[config.auth.responseUserField] || result?.user
        if (!token || !user) throw new Error('Resposta de login inválida: esperado token e usuário.')
        saveSession(user, token, credentials.remember !== false)
        return user
      } catch (error) {
        if (error.status === 401) throw new Error('E-mail ou senha incorretos.', { cause: error })
        if (error.status === 403) throw new Error(error.message || 'Seu cadastro ainda não foi aprovado.', { cause: error })
        throw error
      }
    }

    if (!credentials.email || !credentials.password) throw new Error('Informe e-mail e senha.')
    const user = demoUsers().find((item) => item.email.toLowerCase() === credentials.email.trim().toLowerCase())
    const demoPassword = config.demo.password
    if (!user || credentials.password !== (user.password || demoPassword)) throw new Error('E-mail ou senha incorretos.')
    if (user.status === 'Pendente') throw new Error('Seu cadastro ainda aguarda aprovação de um administrador.')
    if (user.status === 'Inativo') throw new Error('Este usuário está inativo. Procure um administrador.')
    const session = { ...user, demo: true }
    saveSession(session, 'demo-session', credentials.remember !== false)
    return session
  },

  async register(payload) {
    if (isDemoMode()) {
      const users = demoUsers()
      if (users.some((item) => item.email.toLowerCase() === payload.email.toLowerCase())) {
        throw new Error('Já existe um cadastro com este e-mail.')
      }
      const created = {
        id: `LOCAL-${Date.now()}`,
        name: payload.name,
        email: payload.email,
        password: payload.password,
        role: 'Operador',
        status: 'Pendente',
        permissions: [],
      }
      writeStore('users', [...users, created])
      return { message: 'Cadastro recebido e aguardando aprovação.' }
    }
    const route = endpoint('auth', 'register')
    return unwrapObject(await http(route.path, { method: route.method, body: payload, auth: false }))
  },

  async logout() {
    if (!isDemoMode() && getToken()) {
      try {
        const route = endpoint('auth', 'logout')
        await http(route.path, { method: route.method })
      } catch {
        // A sessão local é encerrada mesmo quando a API está indisponível.
      }
    }
    localStorage.removeItem(tokenKey)
    sessionStorage.removeItem(tokenKey)
    localStorage.removeItem(sessionStorageKey)
    sessionStorage.removeItem(sessionStorageKey)
  },

  async me() {
    if (isDemoMode()) return getStoredSession()
    const route = endpoint('auth', 'me')
    return unwrapObject(await http(route.path, { method: route.method }))
  },

  async getStation() {
    if (isDemoMode()) return metalTechData.operation.station
    const route = endpoint('stations', 'current')
    return unwrapObject(await http(route.path, { method: route.method }))
  },

  async getReservoirStatus() {
    if (isDemoMode()) return { ...metalTechData.operation.reservoir, currentLevel: metalTechData.operation.readings[0]?.reservoirLevel ?? metalTechData.operation.reservoir.currentLevel, distance: metalTechData.operation.readings[0]?.distance ?? metalTechData.operation.reservoir.distance }
    const route = endpoint('reservoir', 'status')
    return unwrapObject(await http(route.path, { method: route.method }))
  },

  async listRolePermissions() {
    if (isDemoMode()) return metalTechData.account.permissions
    const route = endpoint('permissions', 'roles')
    return unwrapList(await http(route.path, { method: route.method }))
  },

  async getOverview(query = {}) {
    if (isDemoMode()) return null
    const route = endpoint('analytics', 'overview')
    return unwrapObject(await http(route.path, { method: route.method, query }))
  },

  async listSensors() {
    if (isDemoMode()) return metalTechData.operation.sensors
    const route = endpoint('sensors', 'list')
    return unwrapList(await http(route.path, { method: route.method }))
  },

  async listReadings(query = {}) {
    if (isDemoMode()) return readStore('readings', metalTechData.operation.readings)
    const route = endpoint('readings', 'list')
    return unwrapList(await http(route.path, { method: route.method, query }))
  },

  async listProduction(query = {}) {
    if (isDemoMode()) return metalTechData.operation.hourlyProduction
    const route = endpoint('analytics', 'production')
    return unwrapList(await http(route.path, { method: route.method, query }))
  },

  async getSettings() {
    if (isDemoMode()) return demoSettings()
    const route = endpoint('settings', 'get')
    return unwrapObject(await http(route.path, { method: route.method }))
  },

  async listUsers() {
    if (isDemoMode()) return demoUsers()
    const route = endpoint('users', 'list')
    return unwrapList(await http(route.path, { method: route.method }))
  },

  async createUser(payload) {
    if (isDemoMode()) {
      const users = demoUsers()
      if (users.some((item) => item.email.toLowerCase() === payload.email.toLowerCase())) throw new Error('Já existe um usuário com este e-mail.')
      const created = { ...payload, id: `LOCAL-${Date.now()}`, status: 'Pendente' }
      writeStore('users', [...users, created])
      return created
    }
    const route = endpoint('users', 'create')
    return unwrapObject(await http(route.path, { method: route.method, body: payload }))
  },

  async updateUser(id, payload) {
    if (isDemoMode()) {
      const updated = demoUsers().map((item) => item.id === id ? { ...item, ...payload } : item)
      writeStore('users', updated)
      return updated.find((item) => item.id === id)
    }
    const route = endpoint('users', 'update')
    return unwrapObject(await http(route.path.replace(':id', encodeURIComponent(id)), { method: route.method, body: payload }))
  },

  async deleteUser(id) {
    if (isDemoMode()) {
      writeStore('users', demoUsers().filter((item) => item.id !== id))
      return { success: true }
    }
    const route = endpoint('users', 'delete')
    return http(route.path.replace(':id', encodeURIComponent(id)), { method: route.method })
  },

  async listAlerts() {
    if (isDemoMode()) return demoAlerts()
    const route = endpoint('alerts', 'list')
    return unwrapList(await http(route.path, { method: route.method }))
  },

  async updateAlert(id, payload) {
    if (isDemoMode()) {
      const updated = demoAlerts().map((item) => item.id === id ? { ...item, ...payload } : item)
      writeStore('alerts', updated)
      return updated.find((item) => item.id === id)
    }
    const route = endpoint('alerts', 'update')
    return unwrapObject(await http(route.path.replace(':id', encodeURIComponent(id)), { method: route.method, body: payload }))
  },

  async saveSettings(payload) {
    if (payload?.integration?.apiUrl) localStorage.setItem(apiUrlKey, payload.integration.apiUrl)
    if (isDemoMode()) {
      const current = demoSettings()
      const next = {
        ...current,
        alertThresholds: { ...current.alertThresholds, ...payload.alertThresholds },
        collection: { ...current.collection, ...payload.collection },
        integration: { ...current.integration, ...payload.integration },
        station: { ...current.station, ...payload.station },
      }
      writeStore('settings', next)
      return next
    }
    const route = endpoint('settings', 'update')
    return unwrapObject(await http(route.path, { method: route.method, body: payload }))
  },

  async health() {
    if (isDemoMode()) return { status: 'demo', message: 'Modo demonstração: dados locais persistidos neste navegador.' }
    const route = config.endpoints.health
    return http(route.path, { method: route.method, auth: false })
  },

  async exportReadings(query = {}) {
    if (isDemoMode()) return readStore('readings', metalTechData.operation.readings)
    const route = endpoint('readings', 'export')
    return unwrapList(await http(route.path, { method: route.method, query }))
  },

  // Snapshot único para manter todas as telas sincronizadas com o mesmo ciclo de atualização.
  async refreshAll() {
    const keys = ['users', 'alerts', 'readings', 'sensors', 'production', 'settings', 'station', 'reservoir', 'rolePermissions', 'overview', 'currentUser']
    const requests = [
      this.listUsers(), this.listAlerts(), this.listReadings(), this.listSensors(),
      this.listProduction(), this.getSettings(), this.getStation(), this.getReservoirStatus(),
      this.listRolePermissions(), this.getOverview(), this.me(),
    ]
    const results = await Promise.allSettled(requests)
    const snapshot = {}
    const errors = []

    results.forEach((result, index) => {
      if (result.status === 'fulfilled') snapshot[keys[index]] = result.value
      else errors.push({ resource: keys[index], message: result.reason?.message || 'Falha ao atualizar recurso.' })
    })

    snapshot.errors = errors
    return snapshot
  },

  // SSE ou WebSocket são opcionais. Sem configuração, a interface usa polling REST.
  subscribeRealtime({ onMessage, onStatus } = {}) {
    const realtime = config.realtime || {}
    if (isDemoMode() || realtime.mode === 'polling') return () => {}

    if (realtime.websocketUrl && typeof WebSocket !== 'undefined') {
      let socket
      try {
        const socketUrl = realtime.websocketUrl.replace('{token}', encodeURIComponent(getToken() || ''))
        socket = new WebSocket(socketUrl)
        socket.addEventListener('open', () => onStatus?.('connected'))
        socket.addEventListener('message', (event) => {
          try { onMessage?.(JSON.parse(event.data)) } catch { /* ignora mensagens fora do formato JSON */ }
        })
        socket.addEventListener('error', () => onStatus?.('fallback'))
        socket.addEventListener('close', () => onStatus?.('fallback'))
      } catch {
        onStatus?.('fallback')
      }
      return () => socket?.close()
    }

    if (realtime.ssePath && typeof EventSource !== 'undefined') {
      const url = new URL(`${getBaseUrl().replace(/\/$/, '')}${realtime.ssePath}`)
      if (realtime.tokenInQuery && getToken()) url.searchParams.set('access_token', getToken())
      const source = new EventSource(url, { withCredentials: config.credentials === 'include' })
      source.addEventListener('open', () => onStatus?.('connected'))
      source.addEventListener('message', (event) => {
        try { onMessage?.(JSON.parse(event.data)) } catch { /* ignora mensagens fora do formato JSON */ }
      })
      source.addEventListener('error', () => onStatus?.('fallback'))
      return () => source.close()
    }

    onStatus?.('fallback')
    return () => {}
  },
}
