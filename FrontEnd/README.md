# MetalTech — Frontend

Aplicação React + Vite para monitoramento industrial. O frontend está preparado para operar em dois modos:

- `demo`: funcionalidades testáveis sem servidor, com persistência no `localStorage` do navegador.
- `rest`: chamadas HTTP para a API do backend, com token Bearer, tratamento de falhas e atualização periódica da telemetria.

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

O modo inicial está em `demo`. Use o e-mail de um usuário cadastrado em `src/data/metaltechData.jsx` (por exemplo `rhuan.oliveira@metaltech.com.br`) e qualquer senha não vazia. Os dados criados/editados ficam persistidos no navegador atual. Para limpar o estado demo, remova as chaves `metaltech:*` no armazenamento local do navegador.

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

O backend deve habilitar CORS para o domínio do frontend. O cliente envia e espera JSON, exceto exportações geradas no próprio frontend.

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

## Estrutura de integração

- `src/data/metaltechData.jsx`: dados de demonstração, URL base, modo, rotas e documentação inline dos endpoints.
- `src/services/api.js`: cliente HTTP, token Bearer, erros de rede/timeout, autenticação, CRUD de usuários, alertas, configurações e fallback demo.
- `src/App.jsx`: telas e estados da interface; carrega dados pelo serviço, realiza login/logout, atualiza telemetria periodicamente em modo REST, altera usuários/configurações e trata alertas.

## O que depende do backend

O login seguro, a persistência multiusuário, controle de permissões no servidor, CRUD definitivo, leituras reais de sensores, auditoria e atualização real-time só serão efetivos quando a API e os dispositivos estiverem disponíveis. O frontend envia as operações e espera as respostas descritas, mas não pode criar esses serviços no lugar do backend.

Para produção, o backend também deve validar permissões em **todas** as rotas; esconder opções no frontend não é uma medida de segurança suficiente. Tokens devem expirar e ser renováveis; recomenda-se cookie `HttpOnly`, `Secure`, `SameSite` para sessão web quando a arquitetura permitir.
