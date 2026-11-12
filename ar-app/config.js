/* =====================================================================
   config.js - Configuração do aplicativo de Realidade Aumentada
   Projeto Interdisciplinar II - MetalTech  |  pasta: ar-app/

   Decisão de projeto: o RA consome a MESMA API REST do dashboard React
   (contrato descrito em FrontEnd/README.md). Não criamos um endpoint
   exclusivo para o RA: montamos o "status da esteira" no próprio app, a
   partir dos endpoints que já existem. Assim o RA e o dashboard sempre
   mostram os mesmos números.
   Todas as configurações ficam neste arquivo, separadas da lógica (app.js).
   ===================================================================== */
window.CONFIG = {
  // Enquanto o Back-end não implementa o contrato, usamos dados simulados
  // (mock) no mesmo formato que a API devolverá. Mudar para false quando
  // a API estiver no ar.
  USAR_MOCK: true,

  // Endereço base da API. É o mesmo valor de api.baseUrl no arquivo
  // FrontEnd/src/data/metaltechData.jsx (hoje ainda é um endereço fictício).
  // Precisa ser HTTPS para funcionar no celular.
  API_BASE: "https://api-ficticia.metaltech.com.br/v1",

  // Estação monitorada (mesmo id usado no dashboard).
  // É enviado como filtro "stationId" nas consultas.
  STATION_ID: "ET-001",

  // Caminhos dos endpoints, iguais aos do contrato do dashboard.
  ENDPOINTS: {
    login: "/auth/login",                 // POST { email, password } -> { token, user }
    readings: "/readings",                // última leitura: nível do reservatório
    production: "/analytics/production",  // contagem metálicas / não metálicas
    settings: "/settings",                // limites de atenção e crítico
    alerts: "/alerts"                     // alertas em aberto (anomalia de enchimento)
  },

  // As rotas são protegidas por token (Authorization: Bearer ...).
  // Se deixarmos API_TOKEN vazio, o app mostra uma tela de login e guarda o
  // token só durante a sessão do navegador. Evitamos gravar senha ou token
  // fixo no código, que ficaria exposto no repositório do Git.
  API_TOKEN: "",
  TOKEN_TIPO: "Bearer",

  // Visão inicial da câmera:
  //  "ampla"      -> mostra a imagem inteira da câmera (com barras pretas), sem zoom.
  //  "preenchida" -> ocupa a tela toda, mas o AR.js corta as bordas (efeito de zoom).
  // O usuário pode alternar no botão da barra inferior.
  VISAO: "ampla",

  // De quanto em quanto tempo o app consulta a API (ms). 2 s é o mesmo
  // intervalo de atualização padrão do dashboard (refreshSeconds).
  INTERVALO_MS: 2000,

  // Tempo máximo de espera por requisição. Em ambiente industrial é melhor
  // avisar o operador do que deixar o app travado esperando.
  TIMEOUT_MS: 4000,

  // Acima disso (segundos), a última leitura é considerada velha e o app
  // avisa "dados desatualizados" (ex.: a esteira parou de enviar).
  DADOS_ANTIGOS_S: 15,

  // Os limites de alerta mudam pouco (são configurados na tela de
  // Configurações do dashboard), então guardamos em cache por este tempo
  // para não pedir /settings a cada 2 segundos.
  CACHE_CONFIG_S: 30,

  // Limites usados se /settings não trouxer valores e no modo mock.
  // Valores padrão do dashboard: atenção 75%, crítico 90%.
  LIMIAR_ATENCAO: 75,
  LIMIAR_CRITICO: 90
};
