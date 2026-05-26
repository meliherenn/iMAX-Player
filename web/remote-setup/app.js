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
const apiBase = params.get("api") || localStorage.getItem("imaxRemoteSetupApi") || "";

if (codeFromUrl) {
  pairingCode.value = codeFromUrl.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  updatePairingState();
}

tabs.forEach((tab) => {
  tab.addEventListener("click", () => setMode(tab.dataset.mode));
});

pairingCode.addEventListener("input", () => {
  pairingCode.value = pairingCode.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  updatePairingState();
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

    if (apiBase) {
      const response = await fetch(`${apiBase.replace(/\/$/, "")}/api/pairings/${payload.pairingCode}/playlist`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(`Sunucu ${response.status} döndürdü`);
      }

      setResult("TV’ye gönderildi", "iMAX Player cihazı kaynak bilgilerini alabilir.", "success");
      return;
    }

    localStorage.setItem(`imaxSetup:${payload.pairingCode}`, JSON.stringify(payload));
    setResult("Paket hazır", "QR entegrasyonunda bu paket TV’ye güvenli eşleştirme ile gönderilecek.", "success");
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
  pairingState.classList.toggle("ready", ready);
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
  resultCard.classList.remove("success", "error");
  if (state) resultCard.classList.add(state);
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
