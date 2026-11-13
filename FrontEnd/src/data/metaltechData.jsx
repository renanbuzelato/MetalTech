export const metalTechData = {
  // API: altere mode para "rest" e informe baseUrl quando o backend estiver disponível.
  // Cada endpoint documenta método, payload esperado e finalidade da integração.
  api: {
    mode: 'rest', // 'demo' = persistência local; 'rest' = comunicação HTTP real.
    baseUrl: 'http://10.110.12.66:8080/api/v1',
    timeoutMs: 12000,
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json'
    },
    auth: {
      tokenType: 'Bearer',
      responseTokenField: 'token',
      responseUserField: 'user',
      // Login: POST { email, password } -> { token, user }.
    },
    demo: {
      storagePrefix: 'metaltech:',
      password: 'MetalTech@2026', // senha única para testar o modo demo; não é autenticação de produção.
    },
    realtime: {
      mode: 'auto', // 'auto' tenta SSE/WebSocket e mantém polling como fallback; 'polling' força REST periódico.
      ssePath: '/events', // contrato opcional: evento JSON com campos users, alerts, readings, sensors, production, settings e station.
      websocketUrl: '', // opcional: ex. wss://servidor/api/v1/events?token={token}; tem prioridade sobre SSE.
    },
    // CONTRATO REST OBRIGATÓRIO NO MODO 'rest': implemente todas as rotas abaixo no backend.
    // Listas aceitam [] ou { data: [] }; objetos aceitam { ... } ou { data: { ... } }.
    // Nenhum dado de operation/account/configuration é fallback no modo REST: estes blocos são sementes exclusivas do modo demo.
    //
    // FORMATOS DE RESPOSTA ESPERADOS (JSON):
    // User: { id, name, email, role, status, permissions: string[] | Record<string, boolean> }.
    // Station: { id, name, location, status, shift, controllerId }.
    // Sensor: { id, type, technology, value, unit, status, lastSeenAt }.
    // Reading: { id, timestamp, classification, inductive, capacitive, distance, reservoirLevel, status }.
    // Production item: { hour, metallic, nonMetallic, reservoirLevel }.
    // Alert: { id, title, occurredAt, status, severity, reservoirLevel, stationId }.
    // Settings: { alertThresholds: { attention, critical, variationInFiveMinutes }, collection: { intervalSeconds, refreshSeconds }, integration: { apiUrl, controllerId, protocol, version }, station: { name, location, reservoirCapacity, reservoirHeight }, security: { twoFactorEnabled, auditEnabled, sessionTimeoutMinutes } }.
    // Reservoir status: { currentLevel, distance, capacity, attentionThreshold, criticalThreshold, storedPieces, updatedAt }.
    // Login: POST /auth/login body { email, password }, response { token, user }. Register: POST /auth/register body { name, email, password }, response { message, user? }.
    // Listagens aceitam filtros via query string e retornam [] ou { data: [] }; detalhes retornam objeto ou { data: objeto }.
    // Erros devem retornar HTTP 4xx/5xx e JSON { message, code?, details? }.
    // Realtime: SSE GET /events ou WebSocket deve publicar JSON { type?, data: { users?, alerts?, readings?, sensors?, production?, settings?, station?, reservoir?, rolePermissions?, overview?, currentUser? } }.
    endpoints: {
      health: { method: 'GET', path: '/health' }, // disponibilidade da API
      auth: {
        login: { method: 'POST', path: '/auth/login' }, // body: { email, password }; response: { token, user }
        register: { method: 'POST', path: '/auth/register' }, // cadastro público: { name, email, password }; resposta { message }; criar usuário com status Pendente e perfil Operador
        logout: { method: 'POST', path: '/auth/logout' }, // invalida token/sessão
        me: { method: 'GET', path: '/auth/me' }, // usuário autenticado
        refresh: { method: 'POST', path: '/auth/refresh' }, // body: { refreshToken }; response: { token, refreshToken }
      },
      users: {
        list: { method: 'GET', path: '/users' }, // response: User[] ou { data: User[] }
        create: { method: 'POST', path: '/users' }, // body: { name, email, role }; response: User
        update: { method: 'PATCH', path: '/users/:id' }, // :id = usuário; body: { name?, email?, role?, status?, permissions? }
        delete: { method: 'DELETE', path: '/users/:id' }, // :id = usuário
      },
      stations: {
        current: { method: 'GET', path: '/stations/current' }, // estação selecionada para a sessão; response: { id, name, location, status, shift, controllerId }
        list: { method: 'GET', path: '/stations' }, // lista de estações
        detail: { method: 'GET', path: '/stations/:id' }, // detalhe e configuração de uma estação
        create: { method: 'POST', path: '/stations' }, // body: dados da estação
        update: { method: 'PATCH', path: '/stations/:id' }, // body: campos alterados
        delete: { method: 'DELETE', path: '/stations/:id' }, // remove estação
      },
      sensors: {
        list: { method: 'GET', path: '/sensors' }, // response: Sensor[]
        readings: { method: 'GET', path: '/sensors/readings' }, // query: stationId, sensorId, from, to, page, limit
        latest: { method: 'GET', path: '/sensors/readings/latest' }, // última leitura por sensor
        status: { method: 'GET', path: '/sensors/status' }, // status/lastSeenAt dos sensores
      },
      readings: {
        list: { method: 'GET', path: '/readings' }, // query: stationId, classification, from, to, page, limit; response: Reading[]
        detail: { method: 'GET', path: '/readings/:id' }, // detalhe e rastreabilidade da leitura
        export: { method: 'GET', path: '/readings/export' }, // query: mesmos filtros; resposta JSON[] (frontend gera CSV)
      },
      reservoir: { status: { method: 'GET', path: '/reservoir/status' } }, // response: { currentLevel, distance, capacity, attentionThreshold, criticalThreshold, storedPieces, updatedAt }
      permissions: { roles: { method: 'GET', path: '/permissions/roles' } }, // matriz de permissões padrão; response: { data: [{ resource, operator, analyst, administrator }] }
      analytics: {
        overview: { method: 'GET', path: '/analytics/overview' }, // response: { totalPieces, metallicPieces, nonMetallicPieces, metallicPercent, nonMetallicPercent, period, updatedAt }
        production: { method: 'GET', path: '/analytics/production' }, // query: stationId, from, to, interval; response: [{ hour, metallic, nonMetallic, reservoirLevel }]
        reservoir: { method: 'GET', path: '/analytics/reservoir' }, // query: stationId, from, to, interval; response: [{ timestamp, level, distance }] — alimenta o gráfico e a estatística do reservatório
      },
      alerts: {
        list: { method: 'GET', path: '/alerts' }, // query: status, severity, stationId, from, to; response: Alert[]
        detail: { method: 'GET', path: '/alerts/:id' }, // detalhe da ocorrência
        update: { method: 'PATCH', path: '/alerts/:id' }, // body: { status, comment, acknowledgedAt, resolvedAt }
      },
      settings: {
        get: { method: 'GET', path: '/settings' }, // response: Settings conforme schema documentado acima
        update: { method: 'PUT', path: '/settings' }, // body: Settings; response: Settings atualizado
      },
      audit: { list: { method: 'GET', path: '/audit-logs' } }, // query: actorId, action, from, to, page, limit
    },
  },
  session: {
    user: { 
      id: 'USR-001',
      name: 'Rhuan Oliveira',
      email: 'rhuan.oliveira@metaltech.com.br', 
      role: 'Administrador', 
      status: 'Ativo' },
  },
  operation: {
    station: { 
      id: 'ET-001', 
      name: 'Esteira de triagem 01', 
      location: 'Linha A · Fábrica São Paulo', 
      status: 'Em operação', 
      shift: 'Turno 06:00–14:32', 
      controllerId: 'CLP-001' 
    },
    reservoir: { 
      capacity: 1000, 
      currentLevel: 78, 
      distance: 22, 
      attentionThreshold: 75, 
      criticalThreshold: 90 
    },
    sensors: [
      { 
        id: 'SD-001', 
        type: 'distance', 
        technology: 'Ultrassônico', 
        value: 22, 
        unit: 'cm', 
        status: 'Online', 
        lastSeenAt: '2026-11-10T14:32:05-03:00' 
      },
      { 
        id: 'SI-001', 
        type: 'inductive', 
        technology: 'Detecção de metal', 
        value: true, 
        unit: null, 
        status: 'Online', 
        lastSeenAt: '2026-11-10T14:32:05-03:00' 
      },
      { 
        id: 'SC-001', 
        type: 'capacitive', 
        technology: 'Presença de material', 
        value: true, 
        unit: null, 
        status: 'Online', 
        lastSeenAt: '2026-11-10T14:32:05-03:00' 
      },
    ],
    readings: [
      { 
        id: 'LT-1280', 
        timestamp: '2026-11-10T14:32:05-03:00', 
        classification: 'Metálica', 
        inductive: true, 
        capacitive: true, 
        distance: 22, 
        reservoirLevel: 78, 
        status: 'Recebida' 
      },
      { 
        id: 'LT-1279', 
        timestamp: '2026-11-10T14:32:03-03:00', 
        classification: 'Não metálica', 
        inductive: false, 
        capacitive: true, 
        distance: 22, 
        reservoirLevel: 
        78, 
        status: 'Recebida' 
      },
      { 
        id: 'LT-1278', 
        timestamp: '2026-11-10T14:32:01-03:00', 
        classification: 'Metálica', 
        inductive: true, 
        capacitive: true, 
        distance: 22, 
        reservoirLevel: 78, 
        status: 'Recebida' 
      },
      { 
        id: 'LT-1277', 
        timestamp: '2026-11-10T14:31:59-03:00', 
        classification: 'Metálica', 
        inductive: true, 
        capacitive: true, 
        distance: 22, 
        reservoirLevel: 78, 
        status: 'Recebida' 
      },
      { 
        id: 'LT-1276', 
        timestamp: '2026-11-10T14:31:57-03:00', 
        classification: 'Não metálica', 
        inductive: false, 
        capacitive: true, 
        distance: 22, 
        reservoirLevel: 78, 
        status: 'Recebida' 
      },
      { 
        id: 'LT-1275', 
        timestamp: '2026-11-10T14:31:55-03:00', 
        classification: 'Metálica', 
        inductive: true, 
        capacitive: true, 
        distance: 22, 
        reservoirLevel: 78, 
        status: 'Recebida' 
      },
      { 
        id: 'LT-1274', 
        timestamp: '2026-11-10T14:31:53-03:00', 
        classification: 'Metálica', 
        inductive: true, 
        capacitive: true, 
        distance: 22, 
        reservoirLevel: 78, 
        status: 'Recebida' 
      },
      { 
        id: 'LT-1273', 
        timestamp: '2026-11-10T14:31:51-03:00', 
        classification: 'Não metálica', 
        inductive: false, 
        capacitive: true, 
        distance: 22, 
        reservoirLevel: 78, 
        status: 'Recebida' 
      },
      { 
        id: 'LT-1272', 
        timestamp: '2026-11-10T14:31:49-03:00', 
        classification: 'Metálica', 
        inductive: true, 
        capacitive: true, 
        distance: 22, 
        reservoirLevel: 78, 
        status: 'Recebida' 
      },
    ],
    hourlyProduction: [
      { 
        hour: '06:00', 
        metallic: 90, 
        nonMetallic: 48, 
        reservoirLevel: 20 
      },
      { 
        hour: '07:00', 
        metallic: 96, 
        nonMetallic: 52, 
        reservoirLevel: 28 
      },
      { 
        hour: '08:00', 
        metallic: 102, 
        nonMetallic: 54, 
        reservoirLevel: 36 
      },
      { 
        hour: '09:00', 
        metallic: 108, 
        nonMetallic: 58, 
        reservoirLevel: 44 
      },
      { 
        hour: '10:00', 
        metallic: 104, 
        nonMetallic: 56, 
        reservoirLevel: 52 
      },
      { 
        hour: '11:00', 
        metallic: 112, 
        nonMetallic: 60, 
        reservoirLevel: 60 
      },
      { 
        hour: '12:00', 
        metallic: 116, 
        nonMetallic: 62, 
        reservoirLevel: 68 
      },
      { 
        hour: '13:00', 
        metallic: 104, 
        nonMetallic: 58, 
        reservoirLevel: 76 
      },
    ],
    alerts: [
      { 
        id: 'AL-078', 
        title: 'Reservatório próximo da capacidade', 
        occurredAt: '2026-11-10T14:32:00-03:00', 
        status: 'Em aberto', 
        severity: 'Atenção', 
        reservoirLevel: 78 
      },
      { 
        id: 'AL-077', 
        title: 'Enchimento acima do padrão', 
        occurredAt: '2026-11-10T14:29:00-03:00', 
        status: 'Em aberto', 
        severity: 'Atenção', 
        variation: 11 
      },
      { 
        id: 'AL-076', 
        title: 'Comunicação restabelecida', 
        occurredAt: '2026-11-10T12:18:00-03:00', 
        status: 'Resolvido', 
        severity: 'Informativo' 
      },
      { 
        id: 'AL-075', 
        title: 'Reservatório esvaziado', 
        occurredAt: '2026-11-10T10:45:00-03:00', 
        status: 'Resolvido', 
        severity: 'Informativo', 
        reservoirLevel: 22 
      },
      { 
        id: 'AL-074', 
        title: 'Leitura inválida descartada', 
        occurredAt: '2026-11-10T08:52:00-03:00', 
        status: 'Resolvido', 
        severity: 'Aviso' 
      },
    ],
  },
  account: {
    users: [
      //senha: MetalTech@2026
      { 
        id: 'USR-000', 
        name: 'Rhuan Oliveira', 
        email: 'rhuan.oliveira@metaltech.com.br', 
        role: 'Administrador', 
        status: 'Ativo' 
      },
      { 
        id: 'USR-001', 
        name: 'Carla Oliveira', 
        email: 'carla.oliveira@metaltech.com.br', 
        role: 'Administrador', 
        status: 'Ativo' 
      },
      { 
        id: 'USR-002', 
        name: 'João Santos', 
        email: 'joao.santos@metaltech.com.br', 
        role: 'Operador', 
        status: 'Ativo' 
      },
      { 
        id: 'USR-003', 
        name: 'Ana Souza', 
        email: 'ana.souza@metaltech.com.br', 
        role: 'Analista', 
        status: 'Ativo' 
      },
      { 
        id: 'USR-004', 
        name: 'Bruno Lima', 
        email: 'bruno.lima@metaltech.com.br', 
        role: 'Operador', 
        status: 'Pendente' 
      },
    ],
    permissions: [
      { 
        resource: 'Monitorar e consultar leituras', 
        operator: 'Permitido', 
        analyst: 'Permitido', 
        administrator: 'Permitido' 
      },
      { 
        resource: 'Reconhecer alertas', 
        operator: 'Permitido', 
        analyst: 'Somente leitura', 
        administrator: 'Permitido' 
      },
      { 
        resource: 'Exportar análises e históricos', 
        operator: 'Não permitido', 
        analyst: 'Permitido', 
        administrator: 'Permitido' 
      },
      { 
        resource: 'Editar regras, estações e usuários', 
        operator: 'Não permitido', 
        analyst: 'Não permitido', 
        administrator: 'Permitido' 
      },
    ],
  },
  configuration: {
    alertThresholds: { 
      attention: 75, 
      critical: 90, 
      variationInFiveMinutes: 10 
    },
    collection: { 
      intervalSeconds: 2, 
      refreshSeconds: 2 
    },
    integration: { 
      apiUrl: 'https://api-ficticia.metaltech.com.br/v1', 
      controllerId: 'CLP-001', 
      protocol: 'HTTPS', 
      version: 'v1' 
    },
    station: { 
      name: 'Esteira de triagem 01', 
      location: 'Linha A · Fábrica São Paulo', 
      reservoirCapacity: 1000, 
      reservoirHeight: 100 
    },
    security: { 
      twoFactorEnabled: true, 
      auditEnabled: true, 
      sessionTimeoutMinutes: 30 
    },
  },
};

export default metalTechData;
