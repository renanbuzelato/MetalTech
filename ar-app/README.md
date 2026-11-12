# ar-app — Realidade Aumentada (Eletiva I)

App WebAR (AR.js + A-Frame) que mostra, sobre um marcador, a contagem de peças
metálicas / não metálicas e o nível do reservatório da esteira. Consome a **mesma
API REST do dashboard React** (contrato em `FrontEnd/README.md`).

## Arquivos
| Arquivo | Função |
|---|---|
| `index.html` | Cena de RA, marcador, tela de login e interface |
| `app.js` | Chamadas à API, regra de status, atualização da cena e alertas |
| `config.js` | Único arquivo a editar para trocar de ambiente |
| `marcador/hiro.png` | Marcador de teste |

## Como rodar (celular)
App feito para uso no celular.
1. Com `USAR_MOCK: true` (padrão) o app funciona sem Back-end, com dados no mesmo
   formato da API.
2. A câmera exige **HTTPS**. O jeito mais simples: arrastar esta pasta em
   app.netlify.com/drop e abrir o link gerado no celular.
3. Permita a câmera e aponte para o marcador Hiro (`marcador/hiro.png`) aberto em
   outra tela ou impresso, a uns 40-50 cm.
4. Botão **Visão** (barra inferior): *ampla* mostra a imagem inteira da câmera, com
   barras pretas, sem zoom; *preenchida* ocupa a tela toda, mas o AR.js corta as
   bordas. A visão inicial é definida em `VISAO` no `config.js`.

## Diagnóstico no celular
- `teste-camera.html`: abre só a câmera, sem AR.js. Se ela funcionar ali e não no app,
  o problema está no app; se não funcionar, é permissão/navegador.
- Se a imagem ficar preta mas o celular mostra "câmera em uso": o vídeo foi liberado e não começou a tocar. O app chama `play()` sozinho e, se o navegador exigir, pede um toque na tela.
- `index.html?debug`: mostra um painel com o estado do vídeo, da câmera e dos erros.
- `index.html?visao=preenchida`: força a visão preenchida (sem reduzir a imagem).

## Como o RA usa a API
Não existe endpoint exclusivo do RA. O app monta o status da esteira com as rotas
que o dashboard já usa (caminhos relativos a `api.baseUrl`):

| Rota | Usada para | Campos lidos |
|---|---|---|
| `GET /readings?stationId=&limit=10` | nível atual do reservatório | `timestamp`, `reservoirLevel` (leitura mais recente) |
| `GET /analytics/production?stationId=` | contagem por tipo | soma de `metallic` e `nonMetallic` |
| `GET /settings` | limites de alerta (cache de 30 s) | `alertThresholds.attention`, `alertThresholds.critical` |
| `GET /alerts?stationId=` | detectar anomalia de enchimento | alerta com `status` ≠ `Resolvido` e campo `variation` |
| `POST /auth/login` | autenticação (só se não houver token) | resposta `{ token, user }` |

### Regra do status (igual à do dashboard)
1. nível ≥ crítico → **CRITICO**
2. alerta ativo com `variation` → **ANOMALIA**
3. nível ≥ atenção → **ATENCAO**
4. caso contrário → **NORMAL**

## Passando para a API real
1. `USAR_MOCK: false` e `API_BASE` com o mesmo valor de `api.baseUrl` do dashboard
   (`FrontEnd/src/data/metaltechData.jsx`). A URL atual é fictícia.
2. O Back-end precisa implementar as rotas acima, habilitar **CORS** também para o
   domínio onde o RA estiver hospedado e responder em **HTTPS**.
3. Sem `API_TOKEN`, o app mostra a tela de login e guarda só o token na sessão do
   navegador. Não coloque token ou senha fixos no código.
4. Alternativa que evita CORS e segundo túnel: copiar esta pasta para
   `backend/projeto/src/main/resources/static/ra/` e usar `API_BASE: "/v1"`
   (se o Spring servir a API nesse prefixo).

## Pontos de atenção para o grupo
- Hoje o Back-end em `backend/projeto` só tem `GET /sensor` (texto). Ele ainda não
  implementa o contrato `/v1/...` que o dashboard e o RA esperam.
- `reservoirLevel` é tratado como **percentual (0–100)**, como no dashboard.
- A anomalia depende de o Back-end gerar alertas com o campo `variation`
  (como no exemplo `AL-077` do dashboard).
