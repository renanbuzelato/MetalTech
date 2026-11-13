import { useEffect, useMemo, useState } from 'react'
import { metalTechData as data } from './data/metaltechData'
import { api, getStoredSession, isDemoMode } from './services/api'
import './App.css'

const number = (value) => value == null ? '—' : Number(value || 0).toLocaleString('pt-BR')
const percent = (value) => value == null ? '—' : `${Number(value || 0)}%`
const dateTime = (value) => new Date(value).toLocaleString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
const dateLabel = (value) => new Date(value).toLocaleDateString('pt-BR')
const avg = (values) => values.length ? values.reduce((sum, value) => sum + Number(value || 0), 0) / values.length : 0
const median = (values) => {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

function App() {
  // Sementes locais são utilizadas exclusivamente no modo demo. No REST, a UI inicia vazia e aguarda a API.
  const demo = isDemoMode()
  const [station, setStation] = useState(() => demo ? data.operation.station : {})
  const [readingsData, setReadingsData] = useState(() => demo ? data.operation.readings : [])
  const [hourly, setHourly] = useState(() => demo ? data.operation.hourlyProduction : [])
  const [reservoirAnalytics, setReservoirAnalytics] = useState(() => demo ? data.operation.hourlyProduction.map((item) => ({ timestamp: item.hour, level: item.reservoirLevel })) : [])
  const [sensors, setSensors] = useState(() => demo ? data.operation.sensors : [])
  const [alerts, setAlerts] = useState(() => demo ? data.operation.alerts : [])
  const [reservoirData, setReservoirData] = useState(() => demo ? data.operation.reservoir : {})
  const [rolePermissionRows, setRolePermissionRows] = useState(() => demo ? data.account.permissions : [])
  const [overviewData, setOverviewData] = useState(null)
  const [user, setUser] = useState(() => getStoredSession())


  const [page, setPage] = useState('overview')
  const [filter, setFilter] = useState('Todas')
  const [historyType, setHistoryType] = useState('Todas')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState(() => (demo ? data.operation.readings[0] : null))
  const [selectedAlert, setSelectedAlert] = useState(() => (demo ? data.operation.alerts[0] : null))
  const [comment, setComment] = useState('')
  const [toast, setToast] = useState('')
  const [users, setUsers] = useState(() => demo ? data.account.users : [])
  const [busy, setBusy] = useState(false)
  const [loginError, setLoginError] = useState('')
  const [loginNotice, setLoginNotice] = useState('')
  const [apiStatus, setApiStatus] = useState(isDemoMode() ? 'demo' : 'checking')
  const [apiErrors, setApiErrors] = useState([])
  const [invite, setInvite] = useState({ name: '', email: '', role: 'Operador' })
  const [settings, setSettings] = useState(() => demo ? ({
    ...data.configuration.alertThresholds,
    ...data.configuration.collection,
    ...data.configuration.integration,
    ...data.configuration.station,
  }) : {})
  const thresholds = { attention: Number(settings?.attention ?? 0), critical: Number(settings?.critical ?? 0) }
  const reservoir = {
    ...reservoirData,
    currentLevel: reservoirData.currentLevel ?? readingsData[0]?.reservoirLevel ?? null,
    distance: reservoirData.distance ?? readingsData[0]?.distance ?? null,
    capacity: reservoirData.capacity ?? settings.reservoirCapacity ?? null,
    attentionThreshold: settings.attention ?? reservoirData.attentionThreshold ?? null,
    criticalThreshold: settings.critical ?? reservoirData.criticalThreshold ?? null,
  }
  const canAdmin = user?.role === 'Administrador'
  const permissionKeys = [
    'overview', 
    'station', 
    'analytics', 
    'history', 
    'alerts', 
    'users', 
    'settings', 
    'exportData', 
    'manageAlerts', 
    'manageUsers', 
    'manageSettings'
  ]
  const rolePermissions = {
    Administrador: permissionKeys,
    Analista: [
      'overview', 
      'station', 
      'analytics', 
      'history', 
      'alerts', 
      'exportData'
    ],
    Operador: [
      'overview', 
      'station', 
      'history', 
      'alerts', 
      'manageAlerts'
    ],
  }
  const canAccess = (key) => {
    // Permissões individuais explícitas sempre prevalecem sobre as permissões do perfil.
    if (Array.isArray(user?.permissions)) return user.permissions.includes(key)
    if (user?.permissions && typeof user.permissions === 'object' && key in user.permissions) {
      return Boolean(user.permissions[key])
    }

    if (isDemoMode()) return (rolePermissions[user?.role] ?? []).includes(key)

    // No REST, usa exclusivamente a matriz de perfis recebida de GET /permissions/roles.
    // Essa matriz já é carregada no estado rolePermissionRows, mas antes não era consultada.
    const roleField = {
      Administrador: 'administrator',
      Analista: 'analyst',
      Operador: 'operator',
    }[user?.role]
    if (!roleField) return false

    const permissionRow = rolePermissionRows.find((row) =>
      row?.resource === key || row?.key === key || row?.permission === key
    )
    return Boolean(permissionRow?.[roleField])
  }
  const [showLogin, setShowLogin] = useState(() => !getStoredSession())
  const [login, setLogin] = useState({ email: '', password: '', remember: true })
  const [authView, setAuthView] = useState('login')
  const [registration, setRegistration] = useState({ name: '', email: '', password: '', confirmPassword: '' })
  const [registrationBusy, setRegistrationBusy] = useState(false)
  const [permissionUserId, setPermissionUserId] = useState('')
  const [permissionDraft, setPermissionDraft] = useState({})

  const notify = (message) => {
    setToast(message)
    window.setTimeout(() => setToast(''), 2600)
  }

  useEffect(() => {
    if (!user) return undefined
    let active = true
    let refreshing = false

    const applySnapshot = (snapshot = {}) => {
      if (!active || !snapshot || typeof snapshot !== 'object') return
      if (snapshot.currentUser && typeof snapshot.currentUser === 'object') setUser(snapshot.currentUser)
      if (Array.isArray(snapshot.users)) setUsers(snapshot.users)
      if (Array.isArray(snapshot.alerts)) {
        setAlerts(snapshot.alerts)
        setSelectedAlert((current) => snapshot.alerts.find((item) => item.id === current?.id) || snapshot.alerts[0] || null)
      }
      if (Array.isArray(snapshot.readings)) {
        setReadingsData(snapshot.readings)
        setSelected((current) => snapshot.readings.find((item) => item.id === current?.id) || snapshot.readings[0] || null)
      }
      if (Array.isArray(snapshot.sensors)) setSensors(snapshot.sensors)
      if (snapshot.reservoir && typeof snapshot.reservoir === 'object') setReservoirData(snapshot.reservoir)
      if (Array.isArray(snapshot.rolePermissions)) setRolePermissionRows(snapshot.rolePermissions)
      if (snapshot.overview && typeof snapshot.overview === 'object') setOverviewData(snapshot.overview.data ?? snapshot.overview)
      if (Array.isArray(snapshot.production)) setHourly(snapshot.production)
      if (Array.isArray(snapshot.reservoirAnalytics)) setReservoirAnalytics(snapshot.reservoirAnalytics)
      if (snapshot.station && typeof snapshot.station === 'object') setStation(snapshot.station)
      if (snapshot.settings && typeof snapshot.settings === 'object') {
        const remoteSettings = snapshot.settings
        setSettings((current) => ({
          ...current,
          ...remoteSettings.alertThresholds,
          ...remoteSettings.collection,
          ...remoteSettings.integration,
          ...remoteSettings.station,
        }))
      }
    }

    const refresh = async () => {
      if (refreshing) return
      refreshing = true
      try {
        const snapshot = await api.refreshAll()
        applySnapshot(snapshot)
        if (active) setApiErrors(snapshot.errors ?? [])
        if (active) setApiStatus(isDemoMode() ? 'demo' : snapshot.errors?.length ? 'partial' : 'connected')
      } catch {
        if (active) setApiStatus('error')
      } finally {
        refreshing = false
      }
    }

    refresh()
    const stopRealtime = api.subscribeRealtime({
      onMessage: (message) => applySnapshot(message?.data ?? message),
      onStatus: (status) => {
        if (active && status === 'connected') setApiStatus('connected')
        if (active && status === 'fallback') setApiStatus('checking')
      },
    })
    // O polling permanece ativo como fallback e também sincroniza cadastros/configurações.
    const intervalMs = Math.max(2, Number(settings.refreshSeconds) || 5) * 1000
    const timer = window.setInterval(refresh, intervalMs)

    return () => {
      active = false
      window.clearInterval(timer)
      stopRealtime?.()
    }
  }, [user, settings.refreshSeconds])

  const pageInfo = {
    overview: [
      'Visão geral', 
      'Acompanhe os indicadores da sua operação em tempo real.'
    ],
    station: [
      'Estação e sensores', 
      'Detalhes da linha, leituras e classificação das peças.'
    ],
    analytics: [
      'Análises estatísticas', 
      'Transforme as leituras do turno em decisões operacionais.'
    ],
    history: [
      'Histórico de leituras', 
      'Rastreabilidade de cada leitura e classificação da esteira.'
    ],
    alerts: [
      'Central de alertas', 
      'Priorize ocorrências e acompanhe as ações da equipe.'
    ],
    users: [
      'Usuários e permissões', 
      'Gerencie os integrantes e seus níveis de acesso.'
    ],
    settings: [
      'Conexão', 
      'Integração, segurança, critérios de alerta e permissões individuais.'
    ],
  }
  const navItems = [
    [
      'overview', 
      '▦', 'Visão geral'
    ], [
      'station', 
      '♧', 
      'Estação e sensores'
    ], [
      'analytics', 
      '⌁', 
      'Análises'
    ],
    [
      'history', 
      '◴', 
      'Histórico'
    ], [
      'alerts', 
      '⚠', 
      'Alertas'
    ], [
      'users', 
      '♙', 
      'Usuários e acesso'
    ], [
      'settings', 
      '☷', 
      'Conexão'
    ],
  ]
  const [heading, description] = pageInfo[page] ?? pageInfo.overview
  const selectedPermissionUser = users.find((item) => String(item.id) === String(permissionUserId))
  const defaultPermissionMap = Array.isArray(selectedPermissionUser?.permissions)
    ? selectedPermissionUser.permissions.reduce((acc, key) => ({ ...acc, [key]: true }), {})
    : selectedPermissionUser?.permissions && typeof selectedPermissionUser.permissions === 'object'
      ? selectedPermissionUser.permissions
      : (isDemoMode() ? (rolePermissions[selectedPermissionUser?.role] ?? rolePermissions.Operador) : []).reduce((acc, key) => ({ ...acc, [key]: true }), {})
  const selectedPermissions = permissionDraft[permissionUserId] ?? defaultPermissionMap

  // No modo REST, os totais e percentuais vêm exclusivamente de /analytics/overview.
  // A série /analytics/production continua sendo usada nos gráficos e estatísticas horárias.
  const totals = useMemo(() => {
    if (!demo) {
      if (!overviewData) return null
      return {
        total: overviewData.totalPieces ?? null,
        metallic: overviewData.metallicPieces ?? null,
        nonMetallic: overviewData.nonMetallicPieces ?? null,
        metallicPercent: overviewData.metallicPercent ?? null,
        nonMetallicPercent: overviewData.nonMetallicPercent ?? null,
      }
    }
    const metallic = hourly.reduce((sum, item) => sum + Number(item.metallic || 0), 0)
    const nonMetallic = hourly.reduce((sum, item) => sum + Number(item.nonMetallic || 0), 0)
    const total = metallic + nonMetallic
    return {
      metallic, nonMetallic, total,
      metallicPercent: total ? Math.round(metallic / total * 100) : 0,
      nonMetallicPercent: total ? Math.round(nonMetallic / total * 100) : 0,
    }
  }, [demo, hourly, overviewData])

  const visibleReadings = useMemo(() => readingsData.filter((reading) =>
    (historyType === 'Todas' || reading.classification === historyType) &&
    `${reading.id} ${dateTime(reading.timestamp)}`.toLowerCase().includes(search.toLowerCase())), [historyType, search, readingsData])

  const visibleAlerts = alerts.filter((alert) => filter === 'Todas' ||
    (filter === 'Ativas' ? alert.status !== 'Resolvido' : alert.status === 'Resolvido'))
  const activeAlerts = alerts.filter((alert) => alert.status !== 'Resolvido').length
  const resolvedAlerts = alerts.filter((alert) => alert.status === 'Resolvido').length
  const onlineSensors = sensors.filter((sensor) => sensor.status === 'Online').length
  const lastReading = readingsData[0]

  const changeSetting = (key, value) => setSettings((current) => ({ ...current, [key]: value }))
  const field = (label, key, type = 'text') => 
        <label className="field" key={key}>
              <span>{label}</span>
              <input type={type} value={settings[key] ?? ''} onChange={(event) => changeSetting(key, type === 'number' ? Number(event.target.value) : event.target.value)} />
        </label>
  const action = (label, fn) => <button className="primary" onClick={fn}>{label}</button>
  const handleLogin = async (event) => {
    event?.preventDefault()
    if (busy) return

    const email = login.email.trim()
    if (!email || !login.password) {
      setLoginError('Preencha o e-mail e a senha para continuar.')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
      setLoginError('Informe um endereço de e-mail válido.')
      return
    }

    setLoginError('')
    setBusy(true)
    try {
      const authenticated = await api.login({ ...login, email, remember: login.remember })
      setUser(authenticated)
      setShowLogin(false)
      notify(isDemoMode() ? 'Acesso demonstrativo iniciado' : 'Login realizado com sucesso')
    } catch (error) {
      setLoginError(error.message || 'Não foi possível entrar. Tente novamente.')
    } finally {
      setBusy(false)
    }
  }
  const handleRegistration = async (event) => {
    event?.preventDefault()
    if (registrationBusy) return
    const name = registration.name.trim()
    const email = registration.email.trim().toLowerCase()
    if (!name || !email || !registration.password || !registration.confirmPassword) {
      setLoginError('Preencha todos os campos para criar sua conta.')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
      setLoginError('Informe um endereço de e-mail válido.')
      return
    }
    if (registration.password.length < 8) {
      setLoginError('A senha deve ter pelo menos 8 caracteres.')
      return
    }
    if (registration.password !== registration.confirmPassword) {
      setLoginError('As senhas informadas não coincidem.')
      return
    }
    setLoginError('')
    setRegistrationBusy(true)
    try {
      await api.register({ name, email, password: registration.password })
      setRegistration({ name: '', email: '', password: '', confirmPassword: '' })
      setAuthView('login')
      setLogin({ ...login, email, password: '' })
      setLoginError('')
      setLoginNotice('Cadastro enviado. Aguarde a aprovação de um administrador antes de acessar.')
    } catch (error) {
      setLoginError(error.message || 'Não foi possível concluir o cadastro.')
    } finally {
      setRegistrationBusy(false)
    }
  }
  const handleLogout = async () => { 
    await api.logout(); 
    setUser(null); 
    setShowLogin(true); 
    setPage('overview'); 
    setAuthView('login'); 
    notify('Sessão encerrada') 
  }
  const saveSettings = async () => {
    setBusy(true)
    try {
      await api.saveSettings({
        alertThresholds: { attention: settings.attention, critical: settings.critical, variationInFiveMinutes: settings.variationInFiveMinutes },
        collection: { intervalSeconds: settings.intervalSeconds, refreshSeconds: settings.refreshSeconds },
        integration: { apiUrl: settings.apiUrl, controllerId: settings.controllerId },
        station: { name: settings.name, location: settings.location, reservoirCapacity: settings.reservoirCapacity, reservoirHeight: settings.reservoirHeight }
      })
      notify(isDemoMode() ? 'Configurações salvas neste navegador' : 'Configurações salvas na API')
    } catch (error) { notify(error.message) } finally { setBusy(false) }
  }
  const exportCsv = async () => {
    try {
      const rows = await api.exportReadings({ stationId: station.id })
      const headers = [
        'id', 
        'timestamp', 
        'classification', 
        'inductive', 
        'capacitive', 
        'distance', 
        'reservoirLevel', 
        'status'
      ]
      const csv = [headers.join(';'), ...rows.map((row) => headers.map((key) => JSON.stringify(row[key] ?? '')).join(';'))].join('\n')
      const url = URL.createObjectURL(new Blob(['\ufeff', csv], { type: 'text/csv;charset=utf-8' }))
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'metaltech-leituras.csv'; anchor.click(); URL.revokeObjectURL(url)
      notify('Arquivo CSV exportado')
    } catch (error) { notify(error.message) }
  }
  const pageAction = {
    overview: () => canAccess('exportData') ? <button className="outline" onClick={exportCsv}>↓ Exportar resumo</button> : null,
    analytics: () => canAccess('exportData') ? <button className="primary" onClick={exportCsv}>↓ Exportar relatório</button> : null,
    history: () => canAccess('exportData') ? <button className="outline" onClick={exportCsv}>↓ Exportar CSV</button> : null,
    users: () => canAccess('manageUsers') ? action('Convidar usuário', () => document.getElementById('invite-name')?.focus()) : null,
    settings: () => canAccess('manageSettings') ? <button className="primary" disabled={busy} onClick={saveSettings}>{busy ? 'Salvando...' : 'Salvar alterações'}</button> : null,
  }

  if (showLogin || !user) return (
    <div className="login-shell">
      <section className="login-brand">
        <div className="brand"><b>MetalTech</b><small>INDUSTRIAL INTELLIGENCE</small></div>
        <div className="login-copy"><span>INTELIGÊNCIA INDUSTRIAL</span><h1><span>Do sensor<br /></span><span>à decisão.</span></h1><p>Sua operação conectada. Seus indicadores, sempre por perto.</p></div>
        <div className="login-foot">TECNOLOGIA PARA UMA INDÚSTRIA MAIS INTELIGENTE</div>
      </section>
      <section className="login-form">
        {authView === 'login' ? <form className="login-card" noValidate onSubmit={handleLogin}>
          <div className="eyebrow">BEM-VINDO DE VOLTA</div>
          <h2>Acesse sua plataforma</h2>
          <p>Entre com suas credenciais para acompanhar a operação.</p>
          <label className="field">
              <span>E-mail corporativo</span>
              <input type="email" autoComplete="username" required disabled={busy} aria-invalid={Boolean(loginError)} placeholder="nome@empresa.com.br" value={login.email} onChange={(event) => { setLogin({ ...login, email: event.target.value }); setLoginError('') }} />
          </label>
          <label className="field">
              <span>Senha</span>
              <input type="password" autoComplete="current-password" required disabled={busy} aria-invalid={Boolean(loginError)} placeholder="Digite sua senha" value={login.password} onChange={(event) => { setLogin({ ...login, password: event.target.value }); setLoginError('') }} />
          </label>
          <label className="remember">
              <input type="checkbox" checked={login.remember} disabled={busy} onChange={(event) => setLogin({ ...login, remember: event.target.checked })} /> Manter conectado
          </label>
          {loginError && <p className="login-error" role="alert">{loginError}</p>}
          {loginNotice && <p className="login-notice" role="status">{loginNotice}</p>}
          <button className="primary" type="submit" disabled={busy}>{busy ? 'Autenticando...' : 'Entrar na plataforma'}</button>
          <div className="auth-switch">
              <span>Não possui acesso?</span>
              <button type="button" onClick={() => { setAuthView('register'); setLoginError('') }}>Criar uma conta</button>
          </div>
          <small className="muted">
              {isDemoMode() ? `Modo demonstração · ${data.session.user.email} · senha: ${data.api.demo.password}` : 'MetalTech · © MetalTech'}
          </small>
        </form> : <form className="login-card" noValidate onSubmit={handleRegistration}>
          <div className="eyebrow">NOVO ACESSO</div><h2>Solicitar cadastro</h2><p>Preencha seus dados. O acesso será liberado após aprovação do administrador.</p>
          <label className="field">
              <span>Nome completo</span>
              <input autoComplete="name" required disabled={registrationBusy} placeholder="Seu nome" value={registration.name} onChange={(event) => { setRegistration({ ...registration, name: event.target.value }); setLoginError('') }} />
          </label>
          <label className="field">
              <span>E-mail corporativo</span>
              <input type="email" autoComplete="email" required disabled={registrationBusy} placeholder="nome@empresa.com.br" value={registration.email} onChange={(event) => { setRegistration({ ...registration, email: event.target.value }); setLoginError('') }} />
          </label>
          <label className="field">
              <span>Senha (mínimo 8 caracteres)</span>
              <input type="password" autoComplete="new-password" required disabled={registrationBusy} value={registration.password} onChange={(event) => { setRegistration({ ...registration, password: event.target.value }); setLoginError('') }} />
          </label>
          <label className="field">
              <span>Confirmar senha</span>
              <input type="password" autoComplete="new-password" required disabled={registrationBusy} value={registration.confirmPassword} onChange={(event) => { setRegistration({ ...registration, confirmPassword: event.target.value }); setLoginError('') }} />
          </label>
          {loginError && <p className="login-error" role="alert">{loginError}</p>}
          <button className="primary" type="submit" disabled={registrationBusy}>{registrationBusy ? 'Enviando cadastro...' : 'Solicitar acesso'}</button>
          <div className="auth-switch"><span>Já possui uma conta?</span><button type="button" onClick={() => { setAuthView('login'); setLoginError('') }}>
              Voltar ao login</button>
          </div>
        </form>}
      </section>
    </div>
  )

  return (
    <div className="app-shell">
      <div className="app-layout">
        <aside className="sidebar">
          <div className="brand"><b>MetalTech</b><small>INDUSTRIAL INTELLIGENCE</small></div>
          <div className="workspace"><small>WORKSPACE</small><span>{station.location ?? 'Estação não informada pela API'}</span></div>
          <nav aria-label="Navegação principal">
            {navItems.filter(([id]) => canAccess(id)).map(([id, icon, label]) => (
              <button key={id} className={page === id ? 'nav-item active' : 'nav-item'} onClick={() => { setPage(id); setFilter('Todas') }}>
                <i>{icon}</i><span>{label}</span>{id === 'alerts' && activeAlerts > 0 && <em>{activeAlerts}</em>}
              </button>
            ))}
            {navItems.every(([id]) => !canAccess(id)) && (
              <p className="sidebar-permission-warning" role="status">
                Nenhuma página foi liberada para este usuário. Verifique as permissões retornadas por /auth/me e /permissions/roles.
              </p>
            )}
          </nav>
          <div className="sidebar-bottom">
            <div className="integration">
              <b><i />
                {apiStatus === 'demo' ? 'Modo demonstração' : apiStatus === 'connected' ? 'API conectada' : apiStatus === 'partial' ? 'API parcialmente conectada' : apiStatus === 'error' ? 'API indisponível' : 'Verificando API'}
              </b>
              <small>{onlineSensors}/{sensors.length} sensores online</small>
            </div>
            <div className="profile">
              <b>{user.name}</b>
              <small>{user.role} · <button onClick={handleLogout}>Sair ↗</button>
            </small>
            </div></div>
        </aside>

        <main className="main-area">
          <header className="topbar"><div className="crumb">Operação&nbsp; / &nbsp;{heading}</div><div className="top-right"><div className="live"><b>● Dados operacionais</b><small>{lastReading ? dateTime(lastReading.timestamp) : 'Sem leituras'}</small></div><button className="bell" onClick={() => setPage('alerts')}>♧</button><div className="top-user"><b>{user.name}</b><small>{user.role}</small></div></div></header>

          <div className="content">
            <div className="page-content">
              <div className={`page-${page}`}>
                <div className="page-heading"><div><h1>{heading}</h1><p>{description}</p></div>{pageAction[page]?.()}</div>

                {page === 'overview' && <>
                  <div className="metric-grid">
                    <Metric label="Total de peças" value={number(totals?.total)} note={`${hourly.length} registros horários`} />
                    <Metric label="Peças metálicas" value={number(totals?.metallic)} note={`${totals?.metallicPercent ?? "—"}% do total`} accent />
                    <Metric label="Peças não metálicas" value={number(totals?.nonMetallic)} note={`${totals?.nonMetallicPercent ?? "—"}% do total`} />
                    <Metric label="Nível do reservatório" value={percent(reservoir.currentLevel)} note={`Atenção a partir de ${reservoir.attentionThreshold}%`} warn={reservoir.currentLevel >= reservoir.attentionThreshold} />
                  </div>
                    <ReservoirCard reservoir={reservoir} />
                  <div className="two-col"></div>
                  <div className="two-col"><Card title="Leituras recentes"><DataTable rows={readingsData.slice(0, 5)} onSelect={setSelected} selected={selected} /></Card>
                    <Card title="Saúde da integração"><div className="health"><b><i className="dot teal" /> Sistema operacional</b><span className="tag success">{onlineSensors === sensors.length ? 'Conectado' : 'Instável'}</span></div>
                      {sensors.map((sensor) => <div className="health-row" key={sensor.id}><span>{sensorLabel(sensor.type)}</span><b>{sensor.status}</b></div>)}
                      <div className="health-row"><span>Última sincronização</span><b>{lastReading ? dateTime(lastReading.timestamp) : '—'}</b></div>
                    </Card>
                  </div>
                </>}

                {page === 'station' && <>
                  <Card className="station-summary"><div><small>{station.id}</small><h2>{settings.name}</h2><p className="muted">{settings.location} · {station.shift}</p></div><div className="summary-status"><span className="tag success">● {station.status}</span><small>Última leitura: {lastReading ? dateTime(lastReading.timestamp) : '—'}</small></div></Card>
                  <div className="two-col station-cols"><Card title="Classificação da última peça"><p className="muted">Leitura #{lastReading?.id ?? '—'} · {lastReading ? dateTime(lastReading.timestamp) : 'Aguardando leitura'}</p><div className="classification-result"><strong>{lastReading?.classification ?? 'Sem classificação'}</strong><span className={`tag ${lastReading?.classification === 'Metálica' ? 'success' : 'neutral'}`}>Resultado dos sensores</span></div><p className="classification-rule">Indutivo ativo + capacitivo ativo = metálica. Indutivo inativo + capacitivo ativo = não metálica.</p></Card><ReservoirCard reservoir={reservoir} /></div>
                  <div className="sensor-grid">{sensors.map((sensor) => <Sensor key={sensor.id} sensor={sensor} />)}</div>
                  <div className="notice"><div><b>Coleta automatizada</b><p>Envio HTTP a cada {settings.intervalSeconds ?? '—'} s · Controlador {station.controllerId ?? '—'} · {settings.protocol ?? '—'}</p></div></div>
                </>}

                {page === 'analytics' && <>
                  <div className="filter-bar"><select aria-label="Estação"><option>{station.name}</option></select><select aria-label="Período"><option>Turno atual</option></select><span className="muted">{hourly.length} amostras horárias</span></div>
                  <div className="analytics-grid"><Card title="Evolução do nível do reservatório"><p className="chart-help">Percentual de ocupação medido ao longo do turno. Use as referências abaixo para identificar quando o nível exige atenção.</p><LineChart series={[{ label: 'Nível do reservatório', color: 'teal', values: reservoirAnalytics.map((item) => item.level ?? item.reservoirLevel) }]} labels={reservoirAnalytics.map((item) => item.timestamp ? dateTime(item.timestamp) : item.hour ?? '')} unit="%" fixedMax={100} /><div className="analysis-reference"><span>● Atenção: {reservoir.attentionThreshold}%</span><span>● Crítico: {reservoir.criticalThreshold}%</span></div><small className="chart-footnote">Eixo vertical: ocupação (%) · Eixo horizontal: horário</small></Card>
                    <Card title="Composição da produção"><p className="chart-help">Distribuição das peças classificadas no período selecionado. As barras mostram a participação de cada tipo no total.</p><div className="composition-total"><strong>{number(totals?.total)}</strong><span>peças no período</span></div><HorizontalBar label="Metálicas" value={`${number(totals?.metallic)} · ${totals?.metallicPercent ?? "—"}%`} percent={totals?.metallicPercent ?? 0} /><HorizontalBar label="Não metálicas" value={`${number(totals?.nonMetallic)} · ${totals?.nonMetallicPercent ?? "—"}%`} percent={totals?.nonMetallicPercent ?? 0} blue /><div className="legend composition-legend"><span><i className="dot teal" /> Metálicas</span><span><i className="dot blue" /> Não metálicas</span></div><small className="chart-footnote">Percentuais fornecidos pelo consolidado da API.</small></Card>
                  </div>
                  <Card title="Estatística descritiva"><p className="muted">Indicadores por hora e nível do reservatório · {hourly.length} amostras. Média, mediana, mínimo e máximo facilitam comparar produção e estabilidade.</p><StatsTable hourly={hourly} reservoirSeries={reservoirAnalytics} /></Card>
                </>}

                {page === 'history' && <>
                  <div className="metric-grid three">
                    <Metric label="Total de peças no turno" value={number(totals?.total)} note="Resumo do turno informado pela API" />
                    <Metric label="Peças metálicas" value={number(totals?.metallic)} note={`${totals?.metallicPercent ?? "—"}% do total`} accent />
                    <Metric label="Peças não metálicas" value={number(totals?.nonMetallic)} note={`${totals?.nonMetallicPercent ?? "—"}% do total`} />
                  </div>
                  <div className="filter-bar"><span className="filter-context">{station.name} · {readingsData[0] ? dateLabel(readingsData[0].timestamp) : 'Sem leituras'}</span><select value={historyType} onChange={(event) => setHistoryType(event.target.value)} aria-label="Tipo de peça"><option>Todas</option><option>Metálica</option><option>Não metálica</option></select><input aria-label="Buscar leitura" placeholder="Buscar por ID da leitura" value={search} onChange={(event) => setSearch(event.target.value)} /><span className="muted">{visibleReadings.length} resultados</span></div>
                  <div className="history-layout"><Card title="Leituras da esteira" extra={<small className="muted">{visibleReadings.length} registros encontrados</small>}><DataTable rows={visibleReadings} onSelect={setSelected} selected={selected} /></Card>
                    <Card title="Detalhe da leitura selecionada">{selected ? <><div className="reading-title"><h2>#{selected.id}</h2><span className="tag success">{selected.status}</span></div><small className="muted">{dateLabel(selected.timestamp)} · {dateTime(selected.timestamp)}</small>
                      {[['Estação', station.name], ['Classificação', selected.classification], ['Indutivo', selected.inductive ? '1 · Metal detectado' : '0 · Metal não detectado'], ['Capacitivo', selected.capacitive ? '1 · Material presente' : '0 · Sem material'], ['Distância', `${selected.distance} cm`], ['Nível calculado', percent(selected.reservoirLevel)]].map(([label, value]) => <div className="detail-row" key={label}><span>{label}</span><b>{value}</b></div>)}
                      <div className="trace"><b>Rastreabilidade</b><p>Origem: {station.controllerId} → API REST</p><strong>Identificador: {selected.id}</strong></div></> : <p className="muted">Selecione uma leitura para consultar os detalhes.</p>}</Card>
                  </div>
                  <div className="notice simple">Classificação automática baseada nos estados dos sensores indutivo e capacitivo.</div>
                </>}

                {page === 'alerts' && <>
                  <div className="metric-grid three"><Metric label="Alertas ativos" value={number(activeAlerts)} note="Exigem acompanhamento" warn={activeAlerts > 0} /><Metric label="Nível crítico" value={number(alerts.filter((alert) => alert.severity === 'Crítico' && alert.status !== 'Resolvido').length)} note={`Limite crítico: ${reservoir.criticalThreshold}%`} /><Metric label="Resolvidos" value={number(resolvedAlerts)} note="Ocorrências finalizadas" /></div>
                  <div className="alerts-layout"><Card title="Ocorrências do turno" extra={<div className="tabs">{['Todas', 'Ativas', 'Resolvidas'].map((tab) => <button className={filter === tab ? 'selected' : ''} onClick={() => setFilter(tab)} key={tab}>{tab}</button>)}</div>}>
                    <div className="alert-list">{visibleAlerts.map((alert) => <button className="alert-row" key={alert.id} onClick={() => setSelectedAlert(alert)}><span className={alert.status === 'Em aberto' ? 'alert-mark' : 'resolved-mark'} /><span><b>{alert.title}</b><small>{dateTime(alert.occurredAt)} · {station.name}</small></span><em className={alert.status === 'Em aberto' ? 'tag warning' : 'tag success'}>{alert.status}</em></button>)}</div>
                  </Card><Card title="Tratamento do alerta">{selectedAlert ? <><span className="tag warning">{selectedAlert.severity} · #{selectedAlert.id}</span><h2 className="alert-detail-title">{selectedAlert.title}</h2><p className="muted">{dateTime(selectedAlert.occurredAt)} · {dateLabel(selectedAlert.occurredAt)}</p>
                    <div className="detail-row"><span>Nível atual</span><b className="tank-orange">{selectedAlert.reservoirLevel == null ? '—' : percent(selectedAlert.reservoirLevel)}</b></div><div className="detail-row"><span>Critério de disparo</span><b>Reservatório ≥ {thresholds.attention}%</b></div><p className="muted">Ação recomendada</p><p>{selectedAlert.reservoirLevel >= thresholds.attention ? `Planeje o esvaziamento antes de atingir ${thresholds.critical}%.` : 'Verifique a ocorrência e acompanhe a operação.'}</p>
                    <textarea placeholder="Adicionar observação da equipe..." value={comment} onChange={(event) => setComment(event.target.value)} />{selectedAlert.status === 'Resolvido' ? <button className="primary" disabled>Alerta resolvido</button> : canAccess('manageAlerts') ? action(selectedAlert.status === 'Em aberto' ? 'Reconhecer alerta' : 'Resolver alerta', async () => { const nextStatus = selectedAlert.status === 'Em aberto' ? 'Reconhecido' : 'Resolvido'; try { const updated = await api.updateAlert(selectedAlert.id, { status: nextStatus, comment, acknowledgedAt: nextStatus === 'Reconhecido' ? new Date().toISOString() : undefined, resolvedAt: nextStatus === 'Resolvido' ? new Date().toISOString() : undefined }); setAlerts((current) => current.map((item) => item.id === updated.id ? updated : item)); setSelectedAlert(updated); setComment(''); notify(`Alerta ${nextStatus.toLowerCase()}`) } catch (error) { notify(error.message) } }) : <p className="muted">Acesso somente para consulta. Seu perfil não pode alterar o status deste alerta.</p>}<small className="muted tiny">O reconhecimento deverá ser persistido pela API quando a integração estiver disponível.</small></> : <p className="muted">Selecione um alerta.</p>}</Card></div>
                </>}

                {page === 'users' && <>
                  <div className="users-layout"><Card title="Equipe da fábrica" extra={<small className="muted">{users.length} usuários · {users.filter((item) => item.status === 'Ativo').length} ativos · {users.filter((item) => item.status === 'Pendente').length} convites pendentes</small>}>
                    <table className="data-table user-table"><thead><tr><th>USUÁRIO</th><th>PERFIL</th><th>STATUS</th><th>AÇÕES</th></tr></thead><tbody>{users.map((item) => <tr key={item.id ?? item.email}><td><b>{item.name}</b><small>{item.email}</small></td><td>{item.role}</td><td><span className={item.status === 'Ativo' ? 'tag success' : 'tag warning'}>{item.status}</span></td><td>{canAccess('manageUsers') ? <>{item.status !== 'Ativo' && <button className="text-action" onClick={async () => { try { await api.updateUser(item.id, { status: 'Ativo' }); setUsers(await api.listUsers()); notify('Acesso do usuário aprovado') } catch (error) { notify(error.message) } }}>Aprovar acesso</button>} <button className="text-action" onClick={async () => { const name = window.prompt('Nome do usuário:', item.name); if (name === null) return; const email = window.prompt('E-mail:', item.email); if (email === null) return; const role = window.prompt('Perfil (Operador, Analista ou Administrador):', item.role); if (role === null) return; if (!name.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || !['Operador', 'Analista', 'Administrador'].includes(role)) { notify('Confira nome, e-mail e perfil'); return } try { await api.updateUser(item.id, { name: name.trim(), email: email.trim(), role }); setUsers(await api.listUsers()); notify('Usuário atualizado') } catch (error) { notify(error.message) } }}>Editar</button> <button className="text-action" onClick={async () => { if (!window.confirm(`Excluir ${item.name}?`)) return; try { await api.deleteUser(item.id); setUsers(await api.listUsers()); notify('Usuário excluído') } catch (error) { notify(error.message) } }}>Excluir</button></> : <span className="muted">Consulta</span>}</td></tr>)}</tbody></table>
                  </Card>{canAccess('manageUsers') && <Card title="Convidar usuário"><p className="muted">Envie um convite com o perfil adequado.</p><label className="field"><span>Nome</span><input id="invite-name" value={invite.name} onChange={(event) => setInvite({ ...invite, name: event.target.value })} placeholder="Nome completo" /></label><label className="field"><span>E-mail corporativo</span><input type="email" value={invite.email} onChange={(event) => setInvite({ ...invite, email: event.target.value })} placeholder="nome@metaltech.com.br" /></label><label className="field"><span>Perfil de acesso</span><select value={invite.role} onChange={(event) => setInvite({ ...invite, role: event.target.value })}>{['Operador', 'Analista', 'Administrador'].map((role) => <option key={role}>{role}</option>)}</select></label>{action('Enviar convite', async () => { if (!invite.name.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(invite.email)) { notify('Informe nome e e-mail válido'); return } setBusy(true); try { await api.createUser(invite); setUsers(await api.listUsers()); setInvite({ name: '', email: '', role: 'Operador' }); notify(isDemoMode() ? 'Usuário salvo neste navegador' : 'Convite enviado pela API') } catch (error) { notify(error.message) } finally { setBusy(false) } })}</Card>}</div>
                  {canAdmin && <Card title="Permissões individuais de usuários"><p className="muted">Selecione um integrante e personalize as áreas que ele pode acessar. As permissões individuais prevalecem sobre as permissões padrão do perfil.</p>
                      <label className="field permission-user-select"><span>Usuário</span><select value={permissionUserId} onChange={(event) => { setPermissionUserId(event.target.value); setPermissionDraft({}) }}><option value="">Selecione um usuário</option>{users.filter((item) => item.id !== user.id).map((item) => <option key={item.id} value={item.id}>{item.name} · {item.role}</option>)}</select></label>
                      {selectedPermissionUser && <><div className="permission-check-grid">{[['overview', 'Visão geral'], ['station', 'Estação e sensores'], ['analytics', 'Análises estatísticas'], ['history', 'Histórico de leituras'], ['alerts', 'Central de alertas'], ['users', 'Usuários e permissões'], ['settings', 'Conexão'], ['exportData', 'Exportar dados e relatórios'], ['manageAlerts', 'Reconhecer e resolver alertas'], ['manageUsers', 'Cadastrar e editar usuários'], ['manageSettings', 'Alterar configurações']].map(([key, label]) => <label className="permission-check" key={key}><input type="checkbox" checked={Boolean(selectedPermissions[key])} onChange={(event) => setPermissionDraft((current) => ({ ...current, [permissionUserId]: { ...selectedPermissions, [key]: event.target.checked } }))} /><span>{label}</span></label>)}</div><button className="primary" disabled={busy} onClick={async () => { setBusy(true); try { const permissions = permissionKeys.filter((key) => selectedPermissions[key]); await api.updateUser(selectedPermissionUser.id, { permissions }); const refreshed = await api.listUsers(); setUsers(refreshed); setPermissionDraft({}); notify('Permissões atualizadas com sucesso') } catch (error) { notify(error.message) } finally { setBusy(false) } }}>Salvar permissões</button></>}
                    </Card>}
                </>}

                {page === 'settings' && <>
                    <Card title="Integração da plataforma"><div className="card-status"><span className={`tag ${apiStatus === 'connected' || apiStatus === 'demo' ? 'success' : 'warning'}`}>● {apiStatus === 'connected' ? 'Conectada' : apiStatus === 'demo' ? 'Demonstração' : apiStatus === 'error' ? 'Indisponível' : 'Verificando'}</span></div><p className="muted">O dashboard e os dispositivos conectados utilizam a mesma API.</p>{field('Endereço da API', 'apiUrl')}<div className="form-grid">{field('Identificador do controlador', 'controllerId')}{field('Intervalo de atualização (s)', 'refreshSeconds', 'number')}</div><button className="soft" onClick={async () => { try { const result = await api.health(); setApiStatus(isDemoMode() ? 'demo' : 'connected'); notify(result?.message || 'API respondeu corretamente') } catch (error) { setApiStatus('error'); notify(error.message) } }}>Testar conexão</button>{apiErrors.length > 0 && <div className="api-errors" role="status"><b>Rotas que falharam nesta sincronização</b>{apiErrors.map((item) => <small key={item.resource}>{item.resource}: {item.message}</small>)}</div>}</Card>
                  <div className="settings-grid">
                  </div>
                </>}

                <footer className="footer"><span>MetalTech / Plataforma de inteligência industrial</span><span>{isDemoMode() ? 'Modo demonstração · dados locais' : 'API REST · dados remotos'}</span></footer>
              </div>
            </div>
          </div>
        </main>
      </div>
      {toast && <div className="toast">✓ {toast}</div>}
    </div>
  )
}

function sensorLabel(type) {
  return ({ distance: 'Sensor de distância', inductive: 'Sensor indutivo', capacitive: 'Sensor capacitivo' })[type] ?? type
}
function Metric({ label, value, note, accent, warn }) { return <div className="metric card"><span>{label}</span><strong className={accent ? 'teal-text' : warn ? 'orange-text' : ''}>{value}</strong><small>{note}</small></div> }
function Card({ title, extra, children, className = '' }) { return <section className={`card panel ${className}`}><div className="panel-head"><h3>{title}</h3>{extra}</div>{children}</section> }
function Progress({ value }) { return <div className="progress"><i style={{ width: `${Math.max(0, Math.min(100, value))}%` }} /></div> }
function ReservoirCard({ reservoir }) {
  const status = reservoir.currentLevel >= reservoir.criticalThreshold ? 'Crítico' : reservoir.currentLevel >= reservoir.attentionThreshold ? 'Atenção' : 'Normal'
  const stored = reservoir.capacity == null || reservoir.currentLevel == null ? null : reservoir.capacity * reservoir.currentLevel / 100
  return <Card title="Reservatório de saída">
            <div className="tank-number">
              {percent(reservoir.currentLevel)} 
              <span className={`tag ${status === 'Normal' ? 'success' : 'warning'}`}>
                {status}
                </span>
            </div>
              <Progress value={reservoir.currentLevel} /><small>Distância até as peças: {reservoir.distance} cm</small><p className="muted tiny">{status === 'Crítico' ? 'Esvaziamento imediato recomendado.' : status === 'Atenção' ? `Esvaziar antes de atingir ${reservoir.criticalThreshold}%.` : 'Nível dentro da faixa normal.'}</p></Card>
}
function Sensor({ sensor }) {
  const title = sensorLabel(sensor.type)
  const value = sensor.type === 'distance' ? `${sensor.value} ${sensor.unit}` : sensor.value ? 'Ativo' : 'Inativo'
  const detail = sensor.type === 'distance' ? 'Nível do reservatório' : sensor.type === 'inductive' ? 'Detecção de metal' : 'Presença de material'
  return <Card title={title} extra={<span className={`tag ${sensor.status === 'Online' ? 'success' : 'warning'}`}>● {sensor.status}</span>}><small className="muted">Identificador: {sensor.id}</small><div className="sensor-value">{value}</div><b className="sensor-detail">{detail}</b><small className="muted tiny">Último sinal: {dateTime(sensor.lastSeenAt)}</small></Card>
}
function DataTable({ rows, onSelect, selected }) { return <table className="data-table"><thead><tr>{['HORÁRIO', 'ID DA LEITURA', 'TIPO DA PEÇA', 'NÍVEL', 'STATUS'].map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{rows.map((reading) => <tr key={reading.id} className={selected?.id === reading.id ? 'row-selected' : ''} onClick={() => onSelect?.(reading)}><td>{dateTime(reading.timestamp)}</td><td><b>#{reading.id}</b></td><td><span className={reading.classification === 'Metálica' ? 'tag teal-tag' : 'tag blue-tag'}>{reading.classification}</span></td><td>{percent(reading.reservoirLevel)}</td><td className="teal-text">{reading.status}</td></tr>)}</tbody></table> }
function LineChart({ series = [], labels = [], unit = '', fixedMax = null }) {
  const allValues = series.flatMap((item) => (item.values || []).map(Number).filter(Number.isFinite))
  const max = fixedMax ?? Math.max(1, ...allValues) * 1.12
  const min = fixedMax === 100 ? 0 : Math.min(0, ...allValues)
  const x = (index, length) => 42 + (index / Math.max(length - 1, 1)) * 530
  const y = (value) => 150 - ((Number(value) - min) / Math.max(max - min, 1)) * 125
  const ticks = fixedMax === 100 ? [100, 75, 50, 25, 0] : [max, max * .75, max * .5, max * .25, 0]
  const hasData = series.some((item) => (item.values || []).length)
  if (!hasData) return <div className="chart-empty">Aguardando dados da API para montar o gráfico.</div>
  return <div className="chart-visual">
    <svg className="line-chart" viewBox="0 0 600 178" role="img" aria-label="Gráfico de linhas com evolução por horário">
      {ticks.map((tick, index) => <g key={index}><line x1="42" x2="575" y1={y(tick)} y2={y(tick)} stroke="#e7edf2" strokeWidth="1" /><text x="34" y={y(tick) + 3} textAnchor="end" fill="#8798aa" fontSize="10">{Math.round(tick)}{unit === '%' ? '%' : ''}</text></g>)}
      {series.map((item) => {
        const values = item.values || []
        const points = values.map((value, index) => `${x(index, values.length)},${y(value)}`).join(' ')
        return <g key={item.label}>
          <polyline points={points} fill="none" stroke={item.color === 'blue' ? '#5878cf' : '#00877f'} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" strokeDasharray={item.color === 'blue' ? '6 4' : undefined} />
          {values.map((value, index) => <circle key={`${item.label}-${index}`} cx={x(index, values.length)} cy={y(value)} r="3.5" fill={item.color === 'blue' ? '#5878cf' : '#00877f'} stroke="#fff" strokeWidth="1.5"><title>{`${item.label}: ${value}${unit === '%' ? '%' : ` ${unit}`}${labels[index] ? ` · ${labels[index]}` : ''}`}</title></circle>)}
        </g>
      })}
      {labels.map((label, index) => <text key={`${label}-${index}`} x={x(index, labels.length)} y="171" textAnchor="middle" fill="#8798aa" fontSize="10">{label}</text>)}
    </svg>
  </div>
}
function HorizontalBar({ label, value, percent: amount, blue }) { return <div className="hbar-row"><span>{label}</span><div><i style={{ width: `${amount}%`, background: blue ? '#5878cf' : '#00877f' }} /></div><b>{value}</b></div> }
function StatsTable({ hourly, reservoirSeries = [] }) {
  const numericValues = (key) => hourly
    .map((item) => item?.[key])
    .filter((value) => value !== null && value !== undefined && value !== '')
    .map(Number)
    .filter(Number.isFinite)
  const describe = (values) => {
    if (!values.length) return { average: null, median: null, count: 0, min: null, max: null, deviation: null }
    const average = avg(values)
    const deviation = Math.sqrt(values.reduce((sum, value) => sum + (value - average) ** 2, 0) / values.length)
    return {
      average,
      median: median(values),
      count: values.length,
      min: Math.min(...values),
      max: Math.max(...values),
      deviation,
    }
  }
  const metallic = describe(numericValues('metallic'))
  const nonMetallic = describe(numericValues('nonMetallic'))
  const levelValues = reservoirSeries.length
    ? reservoirSeries.map((item) => item?.level ?? item?.reservoirLevel).filter((value) => value !== null && value !== undefined && value !== '').map(Number).filter(Number.isFinite)
    : numericValues('reservoirLevel')
  const level = describe(levelValues)
  const rows = [
    ['Média', metallic.average, nonMetallic.average, level.average],
    ['Mediana (Q2)', metallic.median, nonMetallic.median, level.median],
    ['Amostras válidas', metallic.count, nonMetallic.count, level.count],
    ['Mínimo', metallic.min, nonMetallic.min, level.min],
    ['Máximo', metallic.max, nonMetallic.max, level.max],
    ['Desvio padrão', metallic.deviation, nonMetallic.deviation, level.deviation],
  ]
  const format = (value) => value == null ? '—' : Number(value).toLocaleString('pt-BR', { maximumFractionDigits: 2 })
  if (!hourly.length) return <div className="chart-empty">Aguardando dados de produção da API para calcular as estatísticas.</div>
  return <>
    <table className="stats-table">
      <thead><tr><th>MEDIDA</th><th>METÁLICAS (PEÇAS/H)</th><th>NÃO METÁLICAS (PEÇAS/H)</th><th>NÍVEL (%)</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={row[0]}>{row.map((value, index) => <td key={`${row[0]}-${index}`}>{index === 0 ? value : format(value)}</td>)}</tr>)}</tbody>
    </table>
    <p className="chart-footnote">Cálculos de produção realizados sobre /analytics/production e nível sobre /analytics/reservoir (com fallback para reservoirLevel dos registros de produção quando a série dedicada não estiver disponível). Campos ausentes são ignorados; “Amostras válidas” informa quantos valores entraram em cada cálculo. O desvio padrão representa a dispersão dos valores em relação à média.</p>
  </>
}

export default App
