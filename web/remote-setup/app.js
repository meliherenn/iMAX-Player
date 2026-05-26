// app.js - iMAX Player Remote Setup Web Controller

// Define Supabase project credentials. The user will replace these.
const SUPABASE_URL = "https://apkurmmvlpqsznybnxyq.supabase.co";
const SUPABASE_ANON_KEY = "sb_publishable_QU2LmMC06cBEpcabFqjTJg_vIrEFNUm";

let supabaseClient = null;
if (SUPABASE_URL !== "YOUR_SUPABASE_URL" && SUPABASE_ANON_KEY !== "YOUR_SUPABASE_ANON_KEY") {
  const { createClient } = window.supabase;
  supabaseClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
}

const form = document.querySelector("#setupForm");
const tabs = [...document.querySelectorAll(".tab")];
const fields = [...document.querySelectorAll(".mode-fields")];
const pairingCode = document.querySelector("#pairingCode");
const pairingState = document.querySelector("#pairingState");
const resultCard = document.querySelector("#resultCard");
const resultTitle = document.querySelector("#resultTitle");
const resultText = document.querySelector("#resultText");
const fileInput = document.querySelector("#m3uFile");
const fileTitle = document.querySelector("#fileTitle");
const fileMeta = document.querySelector("#fileMeta");

const setup = {
  mode: "xtream",
  file: null,
  payload: null,
};

const params = new URLSearchParams(window.location.search);
const codeFromUrl = params.get("code") || params.get("pair") || "";

if (codeFromUrl) {
  pairingCode.value = codeFromUrl.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  updatePairingState();
  if (supabaseClient && pairingCode.value.length >= 6) {
    checkPairingStatusDb();
  }
}

tabs.forEach((tab) => {
  tab.addEventListener("click", () => setMode(tab.dataset.mode));
});

let pairingCheckTimeout = null;

pairingCode.addEventListener("input", () => {
  pairingCode.value = pairingCode.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  updatePairingState();

  clearTimeout(pairingCheckTimeout);
  if (supabaseClient && pairingCode.value.length >= 6) {
    pairingCheckTimeout = setTimeout(checkPairingStatusDb, 500);
  }
});

fileInput.addEventListener("change", async () => {
  const [file] = fileInput.files;
  if (!file) {
    setup.file = null;
    fileTitle.textContent = "M3U dosyası seç";
    fileMeta.textContent = "Dosya sadece bu oturumda okunur.";
    return;
  }

  const text = await file.text();
  setup.file = {
    name: file.name,
    size: file.size,
    content: text,
    lineCount: text.split(/\r?\n/).length,
  };
  fileTitle.textContent = file.name;
  fileMeta.textContent = `${formatBytes(file.size)} • ${setup.file.lineCount} satır`;
});

document.querySelector("#clearButton").addEventListener("click", () => {
  form.reset();
  setMode("xtream");
  setup.file = null;
  setup.payload = null;
  fileTitle.textContent = "M3U dosyası seç";
  fileMeta.textContent = "Dosya sadece bu oturumda okunur.";
  updatePairingState();
  setResult("Hazır", "TV kodu ve kaynak bilgileri girildiğinde kurulum paketi hazırlanır.", "");
});

document.querySelector("#copyPayload").addEventListener("click", async () => {
  const payload = setup.payload || buildPayload(new FormData(form));
  await navigator.clipboard.writeText(JSON.stringify(payload, null, 2));
  setResult("Kopyalandı", "Kurulum paketi panoya kopyalandı.", "success");
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  try {
    const payload = buildPayload(new FormData(form));
    setup.payload = payload;
    validatePayload(payload);

    if (!supabaseClient) {
      throw new Error("Supabase yapılandırılmamış! Lütfen app.js dosyasındaki SUPABASE_URL ve SUPABASE_ANON_KEY değerlerini güncelleyin.");
    }

    setResult("Gönderiliyor...", "Kurulum paketi TV'ye gönderiliyor...", "loading");

    const code = payload.pairingCode.toUpperCase();
    const { data, error } = await supabaseClient
      .from("tv_pairings")
      .update({
        payload: payload,
        status: "completed"
      })
      .eq("pairing_code", code);

    if (error) {
      throw new Error(`Supabase hatası: ${error.message}`);
    }

    setResult("TV’ye gönderildi", "iMAX Player cihazı kaynak bilgilerini alabilir.", "success");
  } catch (error) {
    setResult("Kontrol gerekli", error.message, "error");
  }
});

function setMode(mode) {
  setup.mode = mode;
  tabs.forEach((tab) => {
    const active = tab.dataset.mode === mode;
    tab.classList.toggle("active", active);
    tab.setAttribute("aria-selected", String(active));
  });
  fields.forEach((group) => {
    group.classList.toggle("active", group.dataset.fields === mode);
  });
}

function updatePairingState() {
  const ready = pairingCode.value.trim().length >= 4;
  pairingState.textContent = ready ? "Hazır" : "Bekliyor";
  pairingState.className = "status-chip";
  if (ready) pairingState.classList.add("ready");
}

async function checkPairingStatusDb() {
  const code = pairingCode.value.trim().toUpperCase();
  if (!supabaseClient || code.length < 4) {
    updatePairingState();
    return;
  }

  try {
    const { data, error } = await supabaseClient
      .from("tv_pairings")
      .select("status")
      .eq("pairing_code", code)
      .maybeSingle();

    if (error) throw error;

    if (data) {
      if (data.status === "pending") {
        pairingState.textContent = "TV Bağlı";
        pairingState.className = "status-chip ready";
      } else if (data.status === "completed") {
        pairingState.textContent = "Gönderildi";
        pairingState.className = "status-chip ready";
      }
    } else {
      pairingState.textContent = "Kod Bulunamadı";
      pairingState.className = "status-chip error";
    }
  } catch (e) {
    console.error("Eşleşme kontrolü başarısız:", e);
    updatePairingState();
  }
}

function buildPayload(data) {
  const name = String(data.get("name") || "").trim();
  const epgUrl = String(data.get("epgUrl") || "").trim();
  const pairing = pairingCode.value.trim();

  const payload = {
    version: 1,
    source: "imax-remote-setup",
    pairingCode: pairing,
    playlist: {
      name,
      type: setup.mode,
      epgUrl,
      rememberOnStart: data.get("rememberOnStart") === "on",
      epgAutoSync: data.get("epgAutoSync") === "on",
    },
  };

  if (setup.mode === "xtream") {
    payload.playlist.serverUrl = String(data.get("serverUrl") || "").trim();
    payload.playlist.username = String(data.get("username") || "").trim();
    payload.playlist.password = String(data.get("password") || "");
  }

  if (setup.mode === "m3u") {
    payload.playlist.url = String(data.get("m3uUrl") || "").trim();
  }

  if (setup.mode === "file") {
    payload.playlist.fileName = setup.file?.name || "";
    payload.playlist.fileSize = setup.file?.size || 0;
    payload.playlist.fileContent = setup.file?.content || "";
  }

  return payload;
}

function validatePayload(payload) {
  if (!payload.pairingCode || payload.pairingCode.length < 4) {
    throw new Error("TV kodu en az 4 karakter olmalı.");
  }

  if (!payload.playlist.name) {
    throw new Error("Liste adı gerekli.");
  }

  if (payload.playlist.epgUrl && !isUrlLike(payload.playlist.epgUrl)) {
    throw new Error("EPG URL geçerli görünmüyor.");
  }

  if (payload.playlist.type === "xtream") {
    if (!isUrlLike(payload.playlist.serverUrl)) throw new Error("Sunucu URL gerekli.");
    if (!payload.playlist.username) throw new Error("Kullanıcı adı gerekli.");
    if (!payload.playlist.password) throw new Error("Şifre gerekli.");
  }

  if (payload.playlist.type === "m3u" && !isUrlLike(payload.playlist.url)) {
    throw new Error("M3U URL gerekli.");
  }

  if (payload.playlist.type === "file" && !payload.playlist.fileContent) {
    throw new Error("M3U dosyası seçilmeli.");
  }
}

function isUrlLike(value) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function setResult(title, text, state) {
  resultTitle.textContent = title;
  resultText.textContent = text;
  resultCard.className = "result-card";
  if (state) resultCard.classList.add(state);
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
