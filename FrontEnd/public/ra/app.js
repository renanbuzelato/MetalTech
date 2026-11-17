(function () {
  const C = window.CONFIG;
  const E = C.ENDPOINTS;

  const LARGURA_BARRA = 2;

  const COR = { NORMAL: "#4caf50", ATENCAO: "#ffc107", CRITICO: "#f44336", ANOMALIA: "#e040fb" };
  const MSG = {
    ATENCAO: "ATENCAO: reservatorio proximo do limite",
    CRITICO: "CRITICO: reservatorio quase cheio",
    ANOMALIA: "ANOMALIA: enchimento fora do padrao"
  };

  const $ = (s) => document.querySelector(s);
  const marcador = $("#marcador");

  const CHAVE_TOKEN = "metaltech:token";
  const CHAVE_API_URL = "metaltech:apiBaseUrl";
  let timer = null;           
  let marcadorVisivel = false; 
  let emAndamento = false;     
  let nivelMock = 20;
  let cacheConfig = null;    
  let cacheConfigEm = 0;

  const lista = (r) => (Array.isArray(r) ? r : (r && r.data) || []);

  const lerToken = () => {
    if (C.API_TOKEN) return C.API_TOKEN;
    try {
      return localStorage.getItem(CHAVE_TOKEN) || sessionStorage.getItem(CHAVE_TOKEN)
        || sessionStorage.getItem("metaltech-ra:token");
    } catch { return null; }
  };
  const guardarToken = (t) => {
    try { sessionStorage.setItem(CHAVE_TOKEN, t); } catch {  }
  };
  const limparToken = () => {
    try {
      localStorage.removeItem(CHAVE_TOKEN);
      sessionStorage.removeItem(CHAVE_TOKEN);
      sessionStorage.removeItem("metaltech-ra:token");
    } catch {}
  };
  const obterBaseApi = () => {
    try { return localStorage.getItem(CHAVE_API_URL) || C.API_BASE; }
    catch { return C.API_BASE; }
  };

  // ---------- Comunicação com a API ----------

  async function http(caminho, { method = "GET", body, query, auth = true } = {}) {
    const url = new URL(obterBaseApi().replace(/\/$/, "") + caminho, window.location.origin);
    Object.entries(query || {}).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, v);
    });

    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const token = lerToken();
    if (auth && token) headers.Authorization = C.TOKEN_TIPO + " " + token;

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

      let dados = null;
      if (bruto) {
        try { dados = JSON.parse(bruto); }
        catch { if (r.ok) throw new Error("A API respondeu algo que não é JSON"); }
      }

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
      alerts: n >= 50 && n < 60
        ? [{ id: "AL-MOCK", title: "Enchimento acima do padrão", status: "Em aberto", severity: "Atenção", variation: 11 }]
        : [],
      settings: { alertThresholds: { attention: C.LIMIAR_ATENCAO, critical: C.LIMIAR_CRITICO, variationInFiveMinutes: 10 } }
    };
  }

  async function buscarBruto() {
    if (C.USAR_MOCK) return gerarMock();

    const pacote = await http(E.raData, { auth: false });
    return {
      readings: pacote.readings,
      production: pacote.production,
      alerts: pacote.alerts,
      settings: pacote.settings
    };
  }

  function montarStatus(b) {
    const leituras = lista(b.readings);
    if (!leituras.length) throw new Error("Aguardando as primeiras leituras da esteira");

    const ultima = leituras.reduce((a, x) => (new Date(x.timestamp) > new Date(a.timestamp) ? x : a));

    const producao = lista(b.production);
    const soma = (campo) => producao.reduce((s, i) => s + Number(i[campo] || 0), 0);

    const limites = (b.settings && b.settings.alertThresholds) || {};
    const atencao = Number(limites.attention) || C.LIMIAR_ATENCAO;
    const critico = Number(limites.critical) || C.LIMIAR_CRITICO;

    const nivel = Math.min(Math.max(Number(ultima.reservoirLevel) || 0, 0), 100);

    const anomalia = lista(b.alerts).some((a) => a.status !== "Resolvido" && a.variation != null);

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

  // ---------- Interface ----------

  function setHud(ponto, classe, texto) {
    $("#pt" + ponto).className = "ponto " + classe;
    $("#lbl" + ponto).textContent = texto;
  }

  function mostrarAlerta(texto, cor) {
    $("#alerta").setAttribute("visible", !!texto);
    $("#txtAlerta").setAttribute("value", texto || "");
    $("#alertaFundo").setAttribute("color", cor || "#f44336");
  }

  function atualizarCena(d) {
    const nivel = d.reservatorio.nivelPercentual;
    const status = d.reservatorio.status;

    const larg = Math.max((nivel / 100) * LARGURA_BARRA, 0.01);

    $("#txtMetal").setAttribute("value", "Metalicas: " + d.contagem.metalicas);
    $("#txtNaoMetal").setAttribute("value", "Nao metalicas: " + d.contagem.naoMetalicas);
    $("#txtNivel").setAttribute("value", "Nivel do reservatorio: " + nivel.toFixed(1) + "%");

    const barra = $("#barraNivel");
    barra.setAttribute("width", larg);
    barra.setAttribute("position", (-1 + larg / 2) + " 0.04 0.4");
    barra.setAttribute("color", COR[status] || COR.NORMAL);

    mostrarAlerta(MSG[status] || "", COR[status]);

    const idadeS = d.timestamp ? (Date.now() - new Date(d.timestamp).getTime()) / 1000 : 0;
    if (idadeS > C.DADOS_ANTIGOS_S) {
      setHud("Api", "aviso", "Dados desatualizados (" + Math.round(idadeS) + " s)");
    } else {
      setHud("Api", "ok", "Atualizado " + new Date().toLocaleTimeString("pt-BR"));
    }
  }

  async function ciclo() {
    if (emAndamento) return;
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
    ciclo();                                   
    timer = setInterval(ciclo, C.INTERVALO_MS);
  }
  function pararCiclo() { clearInterval(timer); timer = null; }

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

  const params = new URLSearchParams(window.location.search);
  const VERSAO = "v8-2026-11-11";
  let modoDebug = params.has("debug");
  const errosJs = [];
  window.addEventListener("error", (e) => errosJs.push(e.message));
  window.addEventListener("unhandledrejection", (e) => errosJs.push(String(e.reason)));

  let visao = C.VISAO;
  try { visao = localStorage.getItem("metaltech-ra:visao") || visao; } catch { /* usa o padrão */ }
  if (params.get("visao")) visao = params.get("visao");

  function escreverDebug(video, canvas, escala) {
    const trilha = video && video.srcObject && video.srcObject.getVideoTracks
      ? (video.srcObject.getVideoTracks()[0] || {}).readyState : "sem stream";
    $("#debug").style.display = "block";
    $("#debug").textContent =
      "versão: " + VERSAO + "\n" +
      "https: " + window.isSecureContext + "\n" +
      "janela: " + window.innerWidth + "x" + window.innerHeight + "\n" +
      "video: " + (video
        ? video.videoWidth + "x" + video.videoHeight + " | pronto=" + video.readyState +
          " | pausado=" + video.paused + " | css=" + video.offsetWidth + "x" + video.offsetHeight
        : "elemento não existe") + "\n" +
      "estilo do vídeo: " + (video ? video.style.width + " x " + video.style.height + " | margens " + video.style.marginLeft + " / " + video.style.marginTop : "-") + "\n" +
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
      if (visao === "ampla" && video.videoWidth && video.offsetWidth && video.offsetHeight &&
          video.style.marginTop !== "" && video.style.marginLeft !== "") {
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
  setInterval(aplicarVisao, 500);
  const DICA_PADRAO = $("#dica").textContent;
  let dicaDeErro = false;

  function mostrarDicaErro(texto) {
    dicaDeErro = true;
    $("#dica").textContent = texto;
    $("#dica").classList.remove("oculto");
  }

  function avisoCamera() {
    const ua = navigator.userAgent || "";
    if (!window.isSecureContext) return "Abra o link com https:// (a câmera só funciona em endereço seguro).";

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia)
      return "Este navegador não libera a câmera. Abra no Chrome (Android) ou no Safari (iPhone).";

    if (/FBAN|FBAV|Instagram|MicroMessenger|Line\/|; wv\)/i.test(ua))
      return "Você abriu dentro de outro app. Copie o link e abra no Chrome ou no Safari.";
    return "";
  }

  const problemaCamera = avisoCamera();
  if (problemaCamera) mostrarDicaErro(problemaCamera);

  let precisaToque = false;

  function tentarTocarVideo() {
    const v = document.querySelector("#arjs-video");
    if (!v || !v.srcObject) return;

    if (!v.dataset.ouvindo) {
      v.dataset.ouvindo = "1";
      ["loadedmetadata", "playing"].forEach((ev) =>
        v.addEventListener(ev, () => window.dispatchEvent(new Event("resize"))));
    }

    if (!v.paused) return;
    v.muted = true;
    const p = v.play();
    if (p && p.catch) {
      p.catch((e) => {
        if (e && e.name === "NotAllowedError") {
          precisaToque = true;
          $("#btnIniciar").classList.remove("oculto");
          mostrarDicaErro("Toque na tela para iniciar a câmera.");
        }
      });
    }
  }
  ["touchend", "click"].forEach((ev) =>
    document.addEventListener(ev, () => { precisaToque = false; $("#btnIniciar").classList.add("oculto"); tentarTocarVideo(); }, { passive: true })
  );
  setInterval(() => { if (!precisaToque) tentarTocarVideo(); }, 500);

  setTimeout(() => {
    const v = document.querySelector("#arjs-video");
    if (v && v.videoWidth) return;
    modoDebug = true;            
    $("#btnIniciar").classList.remove("oculto");
    if (v && v.srcObject) {
      mostrarDicaErro("A câmera foi liberada, mas a imagem não começou. Toque na tela para iniciar.");
    } else {
      mostrarDicaErro("A câmera não iniciou. Permita o acesso à câmera para este site (ícone ao lado do endereço) e recarregue a página.");
    }
  }, 8000);

  setInterval(() => {
    const v = document.querySelector("#arjs-video");
    if (dicaDeErro && v && v.videoWidth && !v.paused) {
      dicaDeErro = false;
      $("#btnIniciar").classList.add("oculto");
      $("#dica").textContent = DICA_PADRAO;
      if (marcadorVisivel) $("#dica").classList.add("oculto");
    }
  }, 500);

  $("#txtTitulo").setAttribute("value", "ESTEIRA " + C.STATION_ID);
})();
