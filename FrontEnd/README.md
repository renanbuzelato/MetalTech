# MetalTech — Frontend

Aplicação React + Vite para monitoramento industrial. O frontend está preparado para operar em dois modos:

- `demo`: funcionalidades testáveis sem servidor, com persistência no `localStorage` do navegador.
- `rest`: chamadas HTTP para a API do backend, com token Bearer, tratamento de falhas e sincronização periódica de todas as telas. Quando configurado, também tenta receber eventos via SSE ou WebSocket.

> **Importante:** o modo demonstração não é autenticação segura e não substitui o backend. No modo REST, a aplicação depende de uma API real que implemente o contrato abaixo. Os endereços no arquivo de dados são fictícios e precisam ser substituídos.

## Executar

```bash
npm install
npm run dev
```

Validação de código:

```bash
npm run lint
npm run build
```

## Login para demonstração

Para testar sem backend, altere `api.mode` para `demo` em `src/data/metaltechData.jsx`. Use o e-mail `rhuan.oliveira@metaltech.com.br` e a senha `MetalTech@2026` (configurada em `api.demo.password`). Os dados criados/editados ficam persistidos no navegador atual. O modo demo valida a senha para evitar que qualquer combinação de credenciais entre na aplicação. Para limpar o estado demo, remova as chaves `metaltech:*` no armazenamento local do navegador.

## Ativar API REST

Edite `src/data/metaltechData.jsx`:

```jsx
api: {
  mode: 'rest',
  baseUrl: 'https://SEU-DOMINIO-REAL/api/v1',
  // ...
}
```

Substitua também `configuration.integration.apiUrl` pelo mesmo endereço. Ao trocar do modo demo para REST, faça login novamente; uma sessão de demonstração antiga é descartada automaticamente. O campo “Endereço da API” em Configurações permite alterar a URL durante a execução; ao salvar, a URL escolhida fica guardada no navegador e passa a ser usada pelo cliente HTTP.

O backend deve habilitar CORS para o domínio do frontend. O cliente envia e espera JSON, exceto exportações geradas no próprio frontend. O login valida campos obrigatórios e formato de e-mail no cliente; credenciais incorretas devem retornar HTTP 401 ou 403.

## Contrato REST esperado

Todos os caminhos são relativos a `api.baseUrl`. Salvo indicação contrária, as rotas protegidas recebem `Authorization: Bearer <token>` e `Accept: application/json`.

### Autenticação

| Método | Rota | Entrada | Resposta esperada |
|---|---|---|---|
| POST | `/auth/login` | `{ "email": "...", "password": "..." }` | `{ "token": "...", "user": { "id", "name", "email", "role", "status" } }` |
| POST | `/auth/logout` | sem body | 204 ou `{ "success": true }` |
| GET | `/auth/me` | — | objeto do usuário autenticado |
| POST | `/auth/refresh` | `{ "refreshToken": "..." }` | `{ "token": "...", "refreshToken": "..." }` |

O login atual lê `token` e `user` (ou os nomes definidos em `auth.responseTokenField` e `auth.responseUserField`). O backend deve devolver o token no corpo do login. O frontend não armazena senha.

### Usuários

| Método | Rota | Entrada | Resposta |
|---|---|---|---|
| GET | `/users` | query opcional | `User[]` ou `{ "data": User[] }` |
| POST | `/users` | `{ "name", "email", "role" }` | objeto `User` criado |
| PATCH | `/users/:id` | campos alterados: `name`, `email`, `role`, `status` | objeto `User` atualizado |
| DELETE | `/users/:id` | — | 204 ou `{ "success": true }` |

`role` esperado: `Administrador`, `Analista` ou `Operador`.

### Estações e sensores

| Método | Rota | Entrada / resposta esperada |
|---|---|---|
| GET | `/stations` | lista de estações |
| GET | `/stations/:id` | estação com `id`, `name`, `location`, `status`, `controllerId`, `shift` |
| POST | `/stations` | cadastro com os campos da estação |
| PATCH | `/stations/:id` | campos da estação alterados |
| DELETE | `/stations/:id` | remove estação |
| GET | `/sensors` | lista de sensores: `id`, `type`, `technology`, `value`, `unit`, `status`, `lastSeenAt` |
| GET | `/sensors/readings/latest` | última leitura de cada sensor |
| GET | `/sensors/readings` | query: `stationId`, `sensorId`, `from`, `to`, `page`, `limit` |
| GET | `/sensors/status` | status e último sinal dos sensores |

### Leituras e análises

| Método | Rota | Entrada / resposta esperada |
|---|---|---|
| GET | `/readings` | query: `stationId`, `classification`, `from`, `to`, `page`, `limit`; lista de leituras |
| GET | `/readings/:id` | leitura detalhada e rastreabilidade |
| GET | `/readings/export` | JSON array de leituras filtradas; o frontend gera o CSV |
| GET | `/analytics/overview` | KPIs consolidados |
| GET | `/analytics/production` | query: `stationId`, `from`, `to`, `interval`; lista `{ hour, metallic, nonMetallic, reservoirLevel }` |
| GET | `/analytics/reservoir` | série histórica do nível do reservatório |

Cada leitura deve conter: `id`, `timestamp` ISO-8601, `classification`, `inductive`, `capacitive`, `distance`, `reservoirLevel` e `status`.

### Alertas, configurações e auditoria

| Método | Rota | Entrada / resposta esperada |
|---|---|---|
| GET | `/alerts` | query: `status`, `severity`, `stationId`, `from`, `to`; lista de alertas |
| GET | `/alerts/:id` | detalhe do alerta |
| PATCH | `/alerts/:id` | `{ "status": "Reconhecido" ou "Resolvido", "comment": "...", "acknowledgedAt": "...", "resolvedAt": "..." }` |
| GET | `/settings` | `{ alertThresholds, collection, integration, station }` |
| PUT | `/settings` | persiste o objeto de configuração acima |
| GET | `/health` | endpoint público de saúde; `{ "status": "ok" }` |
| GET | `/audit-logs` | query: `actorId`, `action`, `from`, `to`, `page`, `limit` |

## Atualização em tempo real

O frontend mantém polling REST como fallback e atualiza, no mesmo ciclo, usuários, alertas, leituras, sensores, produção, estação e configurações. Se uma rota falhar, as demais respostas bem-sucedidas ainda são aplicadas; o status da integração sinaliza conexão parcial. O intervalo vem de `configuration.collection.refreshSeconds`.

Em `api.realtime`, configure uma das opções:

- `mode: 'polling'`: usa somente chamadas REST periódicas.
- `mode: 'auto'` com `ssePath: '/events'`: tenta Server-Sent Events. O backend deve publicar mensagens `message` em JSON com um ou mais campos: `users`, `alerts`, `readings`, `sensors`, `production`, `settings` e `station`.
- `websocketUrl: 'wss://.../{token}'`: quando preenchido, tem prioridade sobre SSE. O marcador `{token}` é substituído pelo token da sessão. O servidor deve enviar mensagens JSON com o mesmo formato de snapshot.

Se a conexão em tempo real falhar ou não estiver disponível, o polling continua ativo. Para SSE com autenticação, prefira sessão por cookie `HttpOnly`/`Secure`/`SameSite`; o navegador não permite configurar diretamente o cabeçalho Authorization no `EventSource` nativo.

## Estrutura de integração

- `src/data/metaltechData.jsx`: dados de demonstração, URL base, modo, rotas e documentação inline dos endpoints.
- `src/services/api.js`: cliente HTTP, token Bearer, erros de rede/timeout, autenticação, CRUD de usuários, alertas, configurações, snapshot completo e conexão opcional SSE/WebSocket.
- `src/App.jsx`: telas e estados da interface; valida o login, realiza login/logout, sincroniza todas as telas por polling e aplica eventos recebidos em tempo real, além de alterar usuários/configurações e tratar alertas.

## O que depende do backend

O login seguro, a persistência multiusuário, controle de permissões no servidor, CRUD definitivo, leituras reais de sensores, auditoria e atualização real-time só serão efetivos quando a API e os dispositivos estiverem disponíveis. O frontend envia as operações e espera as respostas descritas, mas não pode criar esses serviços no lugar do backend.

Para produção, o backend também deve validar permissões em **todas** as rotas; esconder opções no frontend não é uma medida de segurança suficiente. Tokens devem expirar e ser renováveis; recomenda-se cookie `HttpOnly`, `Secure`, `SameSite` para sessão web quando a arquitetura permitir.


## Cadastro público e permissões individuais

- A tela de login oferece **Criar uma conta**. O formulário valida nome, e-mail, senha mínima de 8 caracteres e confirmação.
- O cadastro usa `POST /auth/register` com `{ name, email, password }`. A API deve armazenar a senha com hash seguro, aplicar proteção contra abuso/rate limit e criar o usuário com `status: "Pendente"` e `role: "Operador"`. O cadastro não deve autenticar automaticamente.
- Na lista **Usuários e acesso**, o administrador pode aprovar usuários pendentes pelo botão **Aprovar acesso** (PATCH `/users/:id` com `{ status: "Ativo" }`). Usuários pendentes ou inativos não devem conseguir autenticar.
- Em **Configurações → Permissões individuais de usuários**, o administrador escolhe outro usuário, marca as páginas liberadas e salva via `PATCH /users/:id` com `{ permissions: ["overview", "station", "analytics", "history", "alerts", "users", "settings"] }` (somente as permissões concedidas devem ser enviadas).
- A API precisa validar a autorização em cada endpoint; esconder itens no menu do React não é uma barreira de segurança. Não permita que usuários alterem suas próprias permissões nem que um administrador perca o último acesso administrativo.
- O frontend interpreta `permissions` como uma lista de chaves de páginas, inclusive quando o perfil é Administrador. Quando esse campo não existe, aplica o padrão do perfil: Operador (visão geral, estação, histórico e alertas), Analista (inclui análises) e Administrador (todas as áreas).

## Revisão de campos e redução de redundância visual

- **Análises:** removidos histograma e boxplot do nível do reservatório, pois a amostra exibida é pequena e os gráficos repetiam a distribuição sem adicionar um critério operacional confiável. Permanecem evolução temporal, composição da produção e estatísticas descritivas.
- **Estação:** substituído o fluxo de quatro etapas por um resumo da classificação da última peça e sua regra. Os cartões dos sensores já mostram estado e valor, portanto repetir cada sensor no fluxograma ocupava espaço sem acrescentar telemetria.
- **Histórico:** removidos seletores de data e estação que só tinham uma opção e não filtravam dados. Data/estação aparecem como contexto; tipo e ID continuam como filtros reais.
- **Sensores:** removida a descrição de tecnologia repetida junto ao nome do sensor; o identificador permanece para manutenção e rastreabilidade.
- **Dados mantidos no contrato:** `distance` e `reservoirLevel` podem ser correlacionados, mas ambos foram mantidos no histórico porque o valor bruto em centímetros e o nível percentual são úteis para auditoria/calibração. `status` da leitura também foi mantido porque representa o estado de ingestão, não a classificação da peça.
- **Dados que devem ter fonte única no backend:** nome/controlador da estação e capacidade/limites devem vir do cadastro/configuração da estação. Leituras históricas devem preservar o snapshot recebido; indicadores agregados devem ser calculados no backend ou na camada de análise, sem persistir cópias calculadas como se fossem novas medições.

## Contrato de permissões

Chaves de acesso: páginas `overview`, `station`, `analytics`, `history`, `alerts`, `users`, `settings`; ações `exportData`, `manageAlerts`, `manageUsers` e `manageSettings`. A atualização das permissões deve retornar o usuário atualizado e a lista de permissões efetiva. A API deve também invalidar ou atualizar as permissões da sessão quando um administrador alterar acessos de alguém.

## Contrato obrigatório da API REST (modo `rest`)

O frontend não cria rotas HTTP no servidor. As rotas abaixo são o contrato que o backend deve implementar. A configuração canônica está em `src/data/metaltechData.jsx`, em `api.endpoints`, incluindo método, caminho e observações de payload/resposta. No modo `rest`, os dados de demonstração de `session`, `operation`, `account` e `configuration` não são usados como fallback visual.

### Autenticação e sessão

| Método | Rota | Payload / resposta esperada |
|---|---|---|
| POST | `/auth/login` | `{ email, password }` → `{ token, user }` |
| POST | `/auth/register` | `{ name, email, password }` → `{ message, user? }`; criar usuário pendente |
| POST | `/auth/logout` | Invalida a sessão/token |
| GET | `/auth/me` | Retorna o usuário autenticado completo |
| POST | `/auth/refresh` | `{ refreshToken }` → `{ token, refreshToken }` |

### Operação, sensores e leituras

| Método | Rota | Resposta esperada |
|---|---|---|
| GET | `/stations/current` | `{ id, name, location, status, shift, controllerId }` |
| GET | `/stations` | Lista de estações |
| GET | `/sensors` | Lista `{ id, type, technology, value, unit, status, lastSeenAt }` |
| GET | `/sensors/readings` | Leituras filtradas por estação/sensor/período |
| GET | `/sensors/readings/latest` | Última leitura por sensor |
| GET | `/sensors/status` | Estado e `lastSeenAt` de cada sensor |
| GET | `/reservoir/status` | `{ currentLevel, distance, capacity, attentionThreshold, criticalThreshold, storedPieces, updatedAt }` |
| GET | `/readings` | Lista `{ id, timestamp, classification, inductive, capacitive, distance, reservoirLevel, status }` |
| GET | `/readings/:id` | Detalhe e rastreabilidade da leitura |
| GET | `/readings/export` | Lista filtrada para exportação CSV |

### Análises e alertas

| Método | Rota | Resposta esperada |
|---|---|---|
| GET | `/analytics/overview` | KPIs consolidados: `totalPieces`, `metallicPieces`, `nonMetallicPieces`, percentuais, período e atualização |
| GET | `/analytics/production` | Série `[{ hour, metallic, nonMetallic, reservoirLevel }]` |
| GET | `/analytics/reservoir` | Série `[{ timestamp, level, distance }]` |
| GET | `/alerts` | Lista `{ id, title, occurredAt, status, severity, reservoirLevel, stationId }` |
| GET | `/alerts/:id` | Detalhe do alerta |
| PATCH | `/alerts/:id` | `{ status, comment?, acknowledgedAt?, resolvedAt? }` → alerta atualizado |

### Usuários, permissões e configurações

| Método | Rota | Payload / resposta esperada |
|---|---|---|
| GET | `/users` | Lista de usuários com `id`, `name`, `email`, `role`, `status`, `permissions` |
| POST | `/users` | `{ name, email, role }` → usuário criado |
| PATCH | `/users/:id` | Campos alterados, incluindo `permissions` → usuário atualizado |
| DELETE | `/users/:id` | Remove usuário |
| GET | `/permissions/roles` | Matriz `{ resource, operator, analyst, administrator }` |
| GET | `/settings` | Configurações agrupadas em `alertThresholds`, `collection`, `integration`, `station` e `security` |
| PUT | `/settings` | Recebe o mesmo objeto de configurações e retorna o objeto persistido |
| GET | `/audit-logs` | Histórico de auditoria filtrável |
| GET | `/health` | Estado de disponibilidade da API |

### Tempo real

O backend pode fornecer SSE em `GET /events` ou WebSocket conforme `api.realtime` em `metaltechData.jsx`. Cada evento deve enviar JSON no formato `{ type, data }`, com os recursos alterados em `data` (por exemplo `readings`, `sensors`, `production`, `alerts`, `users`, `settings`, `station` e `reservoir`). O polling REST continua ativo como fallback e sincroniza o snapshot completo.

**Envelope de resposta:** endpoints de lista podem responder `[]` ou `{ data: [] }`; endpoints de objeto podem responder `{ ... }` ou `{ data: { ... } }`. Erros devem usar status HTTP apropriado e JSON `{ message, code?, details? }`. Datas devem ser ISO-8601 com fuso horário. O backend deve aplicar autorização por usuário em todas as rotas protegidas; ocultar controles na interface não substitui autorização no servidor.
