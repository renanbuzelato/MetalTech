/* =====================================================================
   app.js - Lógica do aplicativo de Realidade Aumentada
   Projeto Interdisciplinar II - MetalTech  |  pasta: ar-app/

   Resumo (para a apresentação):
     1. Busca na API REST (a mesma do dashboard) leituras, produção,
        configurações e alertas.
     2. Monta o "status da esteira" com a mesma regra do dashboard.
     3. Atualiza os elementos 3D que aparecem sobre o marcador.
     4. Mostra alertas quando o reservatório está perto do limite.
     5. Só consulta a API enquanto o marcador está sendo visto.

   Tecnologias: AR.js (rastreamento do marcador) + A-Frame (cena 3D)
   + JavaScript puro (fetch para consumir a API REST do Spring Boot).
   ===================================================================== */

// IIFE: evita que as variáveis abaixo "vazem" para o escopo global.
(function () {
  const C = window.CONFIG;
  const E = C.ENDPOINTS;

  // Largura total da barra de nível na cena 3D (unidades do A-Frame).
  const LARGURA_BARRA = 2;

  // Cores em semáforo (verde, amarelo, vermelho) + roxo para anomalia,
  // que é um caso diferente de "cheio". Textos 3D sem acento porque a
  // fonte padrão do A-Frame não cobre todos os caracteres acentuados.
  const COR = { NORMAL: "#4caf50", ATENCAO: "#ffc107", CRITICO: "#f44336", ANOMALIA: "#e040fb" };
  const MSG = {
    ATENCAO: "ATENCAO: reservatorio proximo do limite",
    CRITICO: "CRITICO: reservatorio quase cheio",
    ANOMALIA: "ANOMALIA: enchimento fora do padrao"
  };

  const $ = (s) => document.querySelector(s);
  const marcador = $("#marcador");

  const CHAVE_TOKEN = "metaltech-ra:token";
  let timer = null;            // setInterval do ciclo de atualização
  let marcadorVisivel = false; // a câmera está vendo o marcador agora?
  let emAndamento = false;     // evita pedidos acumulados se a API demorar
  let nivelMock = 20;
  let cacheConfig = null;      // últimas configurações lidas de /settings
  let cacheConfigEm = 0;

  // O dashboard aceita lista pura ou { data: [...] }. Fazemos igual, para
  // funcionar com qualquer um dos dois formatos que o Back-end escolher.
  const lista = (r) => (Array.isArray(r) ? r : (r && r.data) || []);

  // sessionStorage pode falhar (modo privado, por exemplo), então protegemos.
  const lerToken = () => {
    if (C.API_TOKEN) return C.API_TOKEN;
    try { return sessionStorage.getItem(CHAVE_TOKEN); } catch { return null; }
  };
  const guardarToken = (t) => { try { sessionStorage.setItem(CHAVE_TOKEN, t); } catch { /* segue sem guardar */ } };
  const limparToken = () => { try { sessionStorage.removeItem(CHAVE_TOKEN); } catch { /* nada a limpar */ } };

  // ---------- Comunicação com a API ----------

  // Função única para falar com a API REST: monta a URL com filtros,
  // coloca o token, aplica timeout e traduz os erros para mensagens claras
  // (mesma ideia do cliente HTTP do dashboard).
  async function http(caminho, { method = "GET", body, query, auth = true } = {}) {
    const url = new URL(C.API_BASE.replace(/\/$/, "") + caminho, window.location.origin);
    Object.entries(query || {}).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, v);
    });

    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const token = lerToken();
    if (auth && token) headers.Authorization = C.TOKEN_TIPO + " " + token;

    // AbortController cancela a requisição se passar do timeout.
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), C.TIMEOUT_MS);
    try {
      const r = await fetch(url, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: ctrl.signal
      });
      const bruto = await r.text();

      // Se a resposta não for JSON (ex.: página de erro em HTML), avisamos.
      let dados = null;
      if (bruto) {
        try { dados = JSON.parse(bruto); }
        catch { if (r.ok) throw new Error("A API respondeu algo que não é JSON"); }
      }

      // 401 = token vencido ou ausente: limpamos e pedimos login de novo.
      if (r.status === 401 && auth) {
        limparToken();
        mostrarLogin("Sessão expirada. Entre novamente.");
        throw new Error("Sessão expirada");
      }
      // fetch não lança erro em 404/500, então checamos "ok" manualmente.
      if (!r.ok) throw new Error((dados && (dados.message || dados.error)) || "Erro HTTP " + r.status);
      return dados;
    } catch (e) {
      if (e.name === "AbortError") throw new Error("A API demorou para responder");
      if (e instanceof TypeError) throw new Error("Não foi possível conectar à API (URL, servidor ou CORS)");
      throw e;
    } finally {
      clearTimeout(t);
    }
  }

  // ---------- Dados ----------

  // Dados falsos no MESMO formato que a API vai devolver (leituras, produção,
  // alertas e configurações). Como passam pela mesma função montarStatus()
  // da versão real, testamos a lógica de verdade sem depender do Back-end.
  function gerarMock() {
    nivelMock = nivelMock >= 100 ? 10 : nivelMock + Math.random() * 6;
    const n = Math.min(nivelMock, 100);
    const pecas = Math.floor(n * 10);
    return {
      readings: [{
        id: "LT-MOCK", timestamp: new Date().toISOString(), classification: "Metálica",
        reservoirLevel: n, distance: 22, status: "Recebida"
      }],
      production: [{
        hour: "06:00", metallic: Math.floor(pecas * 0.6), nonMetallic: Math.floor(pecas * 0.4), reservoirLevel: n
      }],
      // Entre 50% e 60% simulamos o alerta "enchimento acima do padrão",
      // só para mostrar o estado de ANOMALIA na demonstração.
      alerts: n >= 50 && n < 60
        ? [{ id: "AL-MOCK", title: "Enchimento acima do padrão", status: "Em aberto", severity: "Atenção", variation: 11 }]
        : [],
      settings: { alertThresholds: { attention: C.LIMIAR_ATENCAO, critical: C.LIMIAR_CRITICO, variationInFiveMinutes: 10 } }
    };
  }

  // Busca os quatro conjuntos de dados em paralelo (Promise.all), o que é
  // mais rápido do que um depois do outro.
  async function buscarBruto() {
    if (C.USAR_MOCK) return gerarMock();

    const filtro = { stationId: C.STATION_ID };
    const configVelha = !cacheConfig || (Date.now() - cacheConfigEm) > C.CACHE_CONFIG_S * 1000;

    const [readings, production, alerts, settings] = await Promise.all([
      http(E.readings, { query: { ...filtro, limit: 10 } }),
      http(E.production, { query: filtro }),
      // Alertas só complementam (detectam a anomalia). Se falharem, o RA
      // continua funcionando com contagem e nível.
      http(E.alerts, { query: filtro }).catch(() => []),
      configVelha ? http(E.settings) : Promise.resolve(cacheConfig)
    ]);

    if (configVelha) { cacheConfig = settings; cacheConfigEm = Date.now(); }
    return { readings, production, alerts, settings };
  }

  // Transforma as respostas da API no "status da esteira" usado pela cena.
  // Segue as mesmas regras do dashboard:
  //   - contagem = soma da produção por hora (metálicas e não metálicas)
  //   - nível    = reservoirLevel da leitura mais recente
  //   - status   = CRITICO se nível >= limite crítico; ATENCAO se >= limite de
  //                atenção; NORMAL caso contrário.
  // E acrescenta a ANOMALIA: há um alerta em aberto com o campo "variation"
  // (enchimento acima do padrão definido pela Ciência de Dados).
  // Toda a dependência do contrato fica nesta função: se o Back-end mudar
  // um nome de campo, só ajustamos aqui.
  function montarStatus(b) {
    const leituras = lista(b.readings);
    if (!leituras.length) throw new Error("Aguardando as primeiras leituras da esteira");

    // Pegamos a leitura de maior timestamp, assim não dependemos da ordem
    // em que o Back-end devolve a lista.
    const ultima = leituras.reduce((a, x) => (new Date(x.timestamp) > new Date(a.timestamp) ? x : a));

    const producao = lista(b.production);
    const soma = (campo) => producao.reduce((s, i) => s + Number(i[campo] || 0), 0);

    const limites = (b.settings && b.settings.alertThresholds) || {};
    const atencao = Number(limites.attention) || C.LIMIAR_ATENCAO;
    const critico = Number(limites.critical) || C.LIMIAR_CRITICO;

    // Limitamos a 0-100 para a barra nunca "vazar" do painel.
    const nivel = Math.min(Math.max(Number(ultima.reservoirLevel) || 0, 0), 100);

    // Alerta "ativo" = qualquer um que não esteja Resolvido (igual ao dashboard).
    const anomalia = lista(b.alerts).some((a) => a.status !== "Resolvido" && a.variation != null);

    // Ordem de prioridade: nível crítico > anomalia > atenção > normal.
    let status = "NORMAL";
    if (nivel >= critico) status = "CRITICO";
    else if (anomalia) status = "ANOMALIA";
    else if (nivel >= atencao) status = "ATENCAO";

    return {
      timestamp: ultima.timestamp,
      contagem: { metalicas: soma("metallic"), naoMetalicas: soma("nonMetallic") },
      reservatorio: { nivelPercentual: nivel, status }
    };
  }

  // ---------- Login ----------

  function mostrarLogin(msg) {
    $("#login").classList.remove("oculto");
    $("#erroLogin").textContent = msg || "";
  }
  function esconderLogin() { $("#login").classList.add("oculto"); }

  // O login usa o mesmo endpoint do dashboard: POST /auth/login devolve o
  // token. Guardamos só o token (nunca a senha) e apenas na sessão.
  $("#btnLogin").addEventListener("click", async () => {
    try {
      const r = await http(E.login, {
        method: "POST", auth: false,
        body: { email: $("#email").value.trim(), password: $("#senha").value }
      });
      const token = r && (r.token || r.accessToken);
      if (!token) throw new Error("Resposta de login inválida: esperado um token");
      guardarToken(token);
      $("#senha").value = "";
      esconderLogin();
      if (marcadorVisivel) iniciarCiclo();
    } catch (e) {
      $("#erroLogin").textContent = e.message;
    }
  });

  // ---------- Interface ----------

  // Atualiza uma linha da barra inferior (Marcador ou Api).
  function setHud(ponto, classe, texto) {
    $("#pt" + ponto).className = "ponto " + classe;
    $("#lbl" + ponto).textContent = texto;
  }

  // Mostra/esconde o painel de alerta flutuante. Texto vazio = some.
  function mostrarAlerta(texto, cor) {
    $("#alerta").setAttribute("visible", !!texto);
    $("#txtAlerta").setAttribute("value", texto || "");
    $("#alertaFundo").setAttribute("color", cor || "#f44336");
  }

  // Atualiza a cena de RA com o status montado. É aqui que os dados reais
  // da esteira viram visualização em realidade aumentada.
  function atualizarCena(d) {
    const nivel = d.reservatorio.nivelPercentual;
    const status = d.reservatorio.status;

    // Percentual (0-100) -> largura da barra 3D. Math.max evita largura
    // zero, que faria a barra sumir.
    const larg = Math.max((nivel / 100) * LARGURA_BARRA, 0.01);

    // Contagem por tipo, classificada pela Automação
    // (sensor indutivo = metálica; capacitivo = não metálica).
    $("#txtMetal").setAttribute("value", "Metalicas: " + d.contagem.metalicas);
    $("#txtNaoMetal").setAttribute("value", "Nao metalicas: " + d.contagem.naoMetalicas);
    $("#txtNivel").setAttribute("value", "Nivel do reservatorio: " + nivel.toFixed(1) + "%");

    // No A-Frame o box cresce a partir do centro. Para a barra "encher"
    // da esquerda para a direita, movemos a posição junto com a largura.
    // O -1 é a borda esquerda da barra de fundo.
    const barra = $("#barraNivel");
    barra.setAttribute("width", larg);
    barra.setAttribute("position", (-1 + larg / 2) + " 0.04 0.4");
    barra.setAttribute("color", COR[status] || COR.NORMAL);

    mostrarAlerta(MSG[status] || "", COR[status]);

    // "Idade" do dado: compara o timestamp da última leitura com a hora do
    // celular. Depende do relógio do celular estar correto.
    const idadeS = d.timestamp ? (Date.now() - new Date(d.timestamp).getTime()) / 1000 : 0;
    if (idadeS > C.DADOS_ANTIGOS_S) {
      setHud("Api", "aviso", "Dados desatualizados (" + Math.round(idadeS) + " s)");
    } else {
      setHud("Api", "ok", "Atualizado " + new Date().toLocaleTimeString("pt-BR"));
    }
  }

  // Um ciclo completo: busca e atualiza. O try/catch garante que uma falha
  // de rede não derrube o app: avisamos e tentamos no próximo ciclo.
  async function ciclo() {
    if (emAndamento) return; // a requisição anterior ainda não terminou
    if (!C.USAR_MOCK && !lerToken()) {
      mostrarLogin();
      setHud("Api", "aviso", "Faça login para ver os dados");
      return;
    }
    emAndamento = true;
    try {
      atualizarCena(montarStatus(await buscarBruto()));
    } catch (e) {
      console.error("Falha ao buscar dados:", e);
      setHud("Api", "erro", e.message || "Falha ao consultar a API");
      mostrarAlerta("Sem conexao com a API", "#607d8b");
    } finally {
      emAndamento = false;
    }
  }

  function iniciarCiclo() {
    pararCiclo();
    ciclo();                                    // atualiza na hora
    timer = setInterval(ciclo, C.INTERVALO_MS); // depois repete
  }
  function pararCiclo() { clearInterval(timer); timer = null; }

  // ---------- Ciclo de vida: só consulta a API com o marcador visível ----------
  // O AR.js dispara "markerFound" e "markerLost". Usamos esses eventos para
  // só fazer requisições quando o usuário está olhando para a esteira:
  // economiza bateria, dados móveis e carga no servidor.

  marcador.addEventListener("markerFound", () => {
    marcadorVisivel = true;
    $("#dica").classList.add("oculto");
    setHud("Marcador", "ok", "Marcador detectado");
    iniciarCiclo();
  });

  marcador.addEventListener("markerLost", () => {
    marcadorVisivel = false;
    $("#dica").classList.remove("oculto");
    setHud("Marcador", "", "Marcador não detectado");
    pararCiclo();
  });

  // ---------- Visão da câmera (evita o efeito de "zoom") ----------
  // O AR.js estica o vídeo da câmera para PREENCHER a tela inteira e corta o
  // que sobra. Em celular de tela comprida isso corta bastante as laterais e
  // parece que a câmera está com zoom. No modo "ampla" nós reduzimos o vídeo
  // E a cena 3D pelo MESMO fator (transform: scale), até a imagem inteira
  // caber na tela. Como os dois encolhem juntos, o painel continua alinhado
  // ao marcador. O que sobra de espaço fica preto.
  // Parâmetros opcionais na URL, úteis para testar no celular:
  //   ?visao=preenchida  -> força a visão preenchida
  //   ?debug             -> mostra um painel com o estado da câmera
  const params = new URLSearchParams(window.location.search);
  const modoDebug = params.has("debug");
  const errosJs = [];
  window.addEventListener("error", (e) => errosJs.push(e.message));
  window.addEventListener("unhandledrejection", (e) => errosJs.push(String(e.reason)));

  let visao = C.VISAO;
  try { visao = localStorage.getItem("metaltech-ra:visao") || visao; } catch { /* usa o padrão */ }
  if (params.get("visao")) visao = params.get("visao");

  // Painel de diagnóstico (só com ?debug): mostra o que o AR.js está fazendo.
  function escreverDebug(video, canvas, escala) {
    const trilha = video && video.srcObject && video.srcObject.getVideoTracks
      ? (video.srcObject.getVideoTracks()[0] || {}).readyState : "sem stream";
    $("#debug").style.display = "block";
    $("#debug").textContent =
      "https: " + window.isSecureContext + "\n" +
      "janela: " + window.innerWidth + "x" + window.innerHeight + "\n" +
      "video: " + (video
        ? video.videoWidth + "x" + video.videoHeight + " | pronto=" + video.readyState +
          " | pausado=" + video.paused + " | css=" + video.offsetWidth + "x" + video.offsetHeight
        : "elemento não existe") + "\n" +
      "trilha da câmera: " + trilha + "\n" +
      "canvas: " + (canvas ? canvas.offsetWidth + "x" + canvas.offsetHeight : "não existe") + "\n" +
      "visão: " + visao + " | escala: " + escala.toFixed(3) + "\n" +
      "erros: " + (errosJs.slice(-3).join(" | ") || "nenhum");
  }

  function aplicarVisao() {
    const video = document.querySelector("#arjs-video");
    const canvas = document.querySelector("a-scene canvas");

    let escala = 1;
    if (video && canvas) {
      // Só reduzimos depois que a câmera realmente começou (videoWidth > 0);
      // antes disso o vídeo ainda tem tamanho provisório. Limitamos a escala a
      // no mínimo 0,4 para a imagem nunca ficar minúscula.
      if (visao === "ampla" && video.videoWidth && video.offsetWidth && video.offsetHeight) {
        escala = Math.min(window.innerWidth / video.offsetWidth, window.innerHeight / video.offsetHeight, 1);
        escala = Math.max(escala, 0.4);
      }
      const t = "scale(" + escala.toFixed(4) + ")";
      if (video.style.transform !== t) {
        video.style.transform = t;
        canvas.style.transform = t;
      }
    }
    if (modoDebug) escreverDebug(video, canvas, escala);
  }

  function atualizarBotaoVisao() {
    $("#btnVisao").textContent = "Visão: " + (visao === "ampla" ? "ampla" : "preenchida");
  }

  $("#btnVisao").addEventListener("click", () => {
    visao = visao === "ampla" ? "preenchida" : "ampla";
    try { localStorage.setItem("metaltech-ra:visao", visao); } catch { /* sem persistência */ }
    atualizarBotaoVisao();
    aplicarVisao();
  });

  atualizarBotaoVisao();
  // Verifica a cada 0,5 s: cobre o início da câmera, a rotação da tela e o
  // redimensionamento do navegador (barra de endereço some/aparece).
  setInterval(aplicarVisao, 500);

  // ---------- Diagnóstico da câmera ----------
  // Se a câmera não abre, o usuário fica sem saber por quê. Então verificamos
  // as causas mais comuns e mostramos uma mensagem clara no topo da tela.
  const DICA_PADRAO = $("#dica").textContent;
  let dicaDeErro = false;

  function mostrarDicaErro(texto) {
    dicaDeErro = true;
    $("#dica").textContent = texto;
    $("#dica").classList.remove("oculto");
  }

  function avisoCamera() {
    const ua = navigator.userAgent || "";
    // 1) Sem HTTPS o navegador bloqueia a câmera.
    if (!window.isSecureContext) return "Abra o link com https:// (a câmera só funciona em endereço seguro).";
    // 2) Navegador sem suporte à API de câmera.
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia)
      return "Este navegador não libera a câmera. Abra no Chrome (Android) ou no Safari (iPhone).";
    // 3) Navegador embutido em outro app (WhatsApp, Instagram etc.) costuma bloquear.
    if (/FBAN|FBAV|Instagram|MicroMessenger|Line\/|; wv\)/i.test(ua))
      return "Você abriu dentro de outro app. Copie o link e abra no Chrome ou no Safari.";
    return "";
  }

  const problemaCamera = avisoCamera();
  if (problemaCamera) mostrarDicaErro(problemaCamera);

  // ---------- Iniciar o vídeo da câmera ----------
  // Problema que encontramos: a câmera era liberada (o celular até mostrava o
  // ícone de "câmera em uso"), mas a imagem ficava preta. O AR.js cria o
  // elemento <video> e liga a câmera, porém NÃO chama video.play(). Ele tenta
  // um "play" no primeiro toque na tela, mas esse código tem um defeito (a
  // função é passada sem o contexto correto e não faz nada). Se o navegador não
  // iniciar o vídeo sozinho, ele fica parado. Por isso chamamos play() nós
  // mesmos: automaticamente e, se o navegador exigir, no primeiro toque.
  let precisaToque = false;

  function tentarTocarVideo() {
    const v = document.querySelector("#arjs-video");
    if (!v || !v.srcObject || !v.paused) return;
    v.muted = true; // vídeo sem som: o navegador libera a reprodução automática
    const p = v.play();
    if (p && p.catch) {
      p.catch((e) => {
        // NotAllowedError = o navegador exige um toque do usuário.
        if (e && e.name === "NotAllowedError") {
          precisaToque = true;
          mostrarDicaErro("Toque na tela para iniciar a câmera.");
        }
      });
    }
  }

  // Um toque na tela é um "gesto do usuário", que o navegador aceita para iniciar o vídeo.
  ["touchend", "click"].forEach((ev) =>
    document.addEventListener(ev, () => { precisaToque = false; tentarTocarVideo(); }, { passive: true })
  );

  // Tenta iniciar sozinho a cada 0,5 s (até o vídeo existir e tocar). Se o
  // navegador pediu um toque, esperamos o toque em vez de insistir.
  setInterval(() => { if (!precisaToque) tentarTocarVideo(); }, 500);

  // Se em 8 s a imagem não apareceu, mostramos a causa mais provável.
  setTimeout(() => {
    const v = document.querySelector("#arjs-video");
    if (v && v.videoWidth) return; // já está funcionando
    if (v && v.srcObject) {
      mostrarDicaErro("A câmera foi liberada, mas a imagem não começou. Toque na tela para iniciar.");
    } else {
      mostrarDicaErro("A câmera não iniciou. Permita o acesso à câmera para este site (ícone ao lado do endereço) e recarregue a página.");
    }
  }, 8000);

  // Quando o vídeo finalmente aparece, volta a dica normal.
  setInterval(() => {
    const v = document.querySelector("#arjs-video");
    if (dicaDeErro && v && v.videoWidth && !v.paused) {
      dicaDeErro = false;
      $("#dica").textContent = DICA_PADRAO;
      if (marcadorVisivel) $("#dica").classList.add("oculto");
    }
  }, 500);

  // Título da cena com o id da estação configurada.
  $("#txtTitulo").setAttribute("value", "ESTEIRA " + C.STATION_ID);
})();
