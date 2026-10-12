// Grabador de ticks read-only. NO opera, NO toca la app, NO modifica el dispositivo.
//
// Reutiliza la sesión WS ya validada por la app (cookie + device id leídos de shared_prefs
// vía `run-as`), igual que _probe_asset_volatility.js. Solo escribe en disco local.
//
// Uso:
//   node scripts/ticks/record_ticks.js [ric ...] [--minutes 30] [--out data/ticks]
//
// Salida: data/ticks/<ric-saneado>_<YYYY-MM-DD>.csv con cabecera ts,ric,price
// El archivo se abre en modo append: relanzar el grabador el mismo día continúa el mismo CSV.
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const WS_URL = 'wss://as.binomo.com';
const DEFAULT_RICS = ['Z-CRY/IDX'];

function arg(flag, fallback) {
  const i = process.argv.indexOf(flag);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

const MINUTES = Number(arg('--minutes', 30));
const OUT_DIR = path.resolve(arg('--out', 'data/ticks'));

/** Recorre argv consumiendo cada flag junto a su valor; lo que queda son RICs. */
const RICS = (() => {
  const flagsWithValue = ['--minutes', '--out'];
  const argv = process.argv.slice(2);
  const out = [];
  for (let i = 0; i < argv.length; i++) {
    if (flagsWithValue.includes(argv[i])) { i++; continue; }
    if (argv[i].startsWith('--')) continue;
    out.push(argv[i]);
  }
  return out.length ? out : DEFAULT_RICS;
})();

function readPref(name) {
  const xml = execSync(
    'adb shell "run-as com.example.tradedraw cat shared_prefs/TradeDraw_WSConfig.xml"',
    { encoding: 'utf8', maxBuffer: 1 << 20 }
  );
  const m = xml.match(new RegExp(`<string name="${name}">([\\s\\S]*?)</string>`));
  if (!m) return '';
  return m[1]
    .replace(/&quot;/g, '"').replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&apos;/g, "'");
}

const cookie = readPref('ws_cookie_header');
if (!cookie) {
  console.error('FATAL: no se pudo leer ws_cookie_header del dispositivo (¿app instalada y sesión iniciada?)');
  process.exit(1);
}
console.log(`cookie len=${cookie.length} rics=${RICS.join(',')} minutos=${MINUTES}`);

fs.mkdirSync(OUT_DIR, { recursive: true });

function csvPathFor(ric) {
  const safe = ric.replace(/[^A-Za-z0-9._-]/g, '_');
  const day = new Date().toISOString().slice(0, 10);
  return path.join(OUT_DIR, `${safe}_${day}.csv`);
}

const streams = new Map();
for (const ric of RICS) {
  const p = csvPathFor(ric);
  const fresh = !fs.existsSync(p) || fs.statSync(p).size === 0;
  const fd = fs.openSync(p, 'a');
  if (fresh) fs.writeSync(fd, 'ts,ric,price\n');
  streams.set(ric, { fd, path: p, count: 0 });
  console.log(`RIC ${ric} -> ${p}`);
}

const deadline = Date.now() + MINUTES * 60 * 1000;
let stopped = false;

for (const ric of RICS) {
  openFeed(ric);
}

function openFeed(ric) {
  const ws = new WebSocket(WS_URL, {
    headers: {
      'Origin': 'https://binomo.com',
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36',
      'Accept-Language': 'en-US,en;q=0.9',
      'Cookie': cookie,
    },
  });

  ws.onopen = () => {
    console.log(`[${ric}] WS abierto, suscribiendo`);
    ws.send(JSON.stringify({ action: 'subscribe', rics: [ric] }));
  };

  ws.onmessage = (ev) => {
    // Formato observado: {"data":[{"ric":"Z-CRY/IDX","rate":641.86728657,...}], ...}
    // Se acepta cualquier frame que traiga ric+rate, sin asumir el envoltorio exacto.
    const text = String(ev.data);
    if (!text.includes('rate')) return;
    let parsed;
    try { parsed = JSON.parse(text); } catch { return; }
    const found = [];
    collectRates(parsed, found);
    const first = found.find((r) => r.ric === ric) || found[0];
    if (!first) return;
    const s = streams.get(ric);
    fs.writeSync(s.fd, `${first.ts},${ric},${first.rate}\n`);
    s.count++;
  };

  ws.onerror = (e) => console.error(`[${ric}] WS error: ${e.message || e.type}`);
  ws.onclose = (e) => {
    if (stopped) return;
    console.error(`[${ric}] WS cerrado (${e.code} ${e.reason}) — reconectando en 2s`);
    setTimeout(() => openFeed(ric), 2000);
  };
}

/**
 * Recorre el árbol JSON buscando objetos {ric, rate}. No asume profundidad ni envoltorio,
 * así que sigue funcionando si Binomo cambia el envoltorio de los frames.
 */
function collectRates(node, out) {
  if (!node || typeof node !== 'object') return;
  if (Array.isArray(node)) {
    for (const item of node) collectRates(item, out);
    return;
  }
  if (typeof node.rate === 'number') {
    out.push({
      ric: typeof node.ric === 'string' ? node.ric : (typeof node.field === 'string' ? node.field : ''),
      rate: node.rate,
      ts: Date.now(),
    });
  }
  for (const v of Object.values(node)) {
    if (v && typeof v === 'object') collectRates(v, out);
  }
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

setTimeout(shutdown, MINUTES * 60 * 1000);

function shutdown() {
  if (stopped) return;
  stopped = true;
  for (const [ric, s] of streams) {
    try { fs.closeSync(s.fd); } catch {}
    console.log(`[${ric}] ${s.count} ticks -> ${s.path}`);
  }
  process.exit(0);
}
