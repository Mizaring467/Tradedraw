// Sonda read-only: mide volatilidad REAL de varios rics reutilizando la sesion
// ya valida de la app (cookie + device id leidos del dispositivo).
// Objetivo: confirmar si Z-CRY/IDX esta pegado por diseno y si otro activo oscila.
// NO opera. NO modifica nada en el dispositivo.
const fs = require('fs');
const { execSync } = require('child_process');

const URL = 'wss://as.binomo.com';
const WAIT_MS = 16000;

function readPref(name) {
  const xml = execSync(
    `adb shell "run-as com.example.tradedraw cat shared_prefs/TradeDraw_WSConfig.xml"`,
    { encoding: 'utf8', maxBuffer: 1 << 20 }
  );
  const m = xml.match(new RegExp(`<string name="${name}">([\\s\\S]*?)</string>`));
  if (!m) return '';
  return m[1]
    .replace(/&quot;/g, '"').replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&apos;/g, "'");
}

const cookie = readPref('ws_cookie_header');
const deviceId = readPref('ws_device_id');
console.log(`cookie len=${cookie.length} device=${deviceId}`);

const RICS = process.argv.slice(2).length ? process.argv.slice(2) : ['Z-CRY/IDX'];

function explore(ric) {
  return new Promise((resolve) => {
    const rates = [];
    const ricsSeen = new Set();
    let opened = false, err = null, sample = null, frames = 0, closeInfo = null;

    let ws;
    try {
      ws = new WebSocket(URL, {
        headers: {
          'Origin': 'https://binomo.com',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36',
          'Accept-Language': 'en-US,en;q=0.9',
          'Cookie': cookie,
        },
      });
    } catch (e) { return resolve({ ric, err: String(e), rates }); }

    ws.onopen = () => {
      opened = true;
      ws.send(JSON.stringify({ action: 'subscribe', rics: [ric] }));
      ws.send(JSON.stringify(['1', '1', `asset:${ric}`, 'phx_join', {}]));
      ws.send(JSON.stringify(['1', '1', 'rates', 'phx_join', { ric }]));
    };
    ws.onmessage = (ev) => {
      const t = String(ev.data);
      frames++;
      if (!sample) sample = t.slice(0, 700);
      for (const m of t.matchAll(/"ric":"([^"]+)"/g)) ricsSeen.add(m[1]);
      const m = t.match(/"rate":([0-9.]+)/);
      if (m) rates.push(parseFloat(m[1]));
    };
    ws.onerror = (e) => { err = err || String(e.message || e.type); };
    ws.onclose = (e) => { closeInfo = `${e.code} ${e.reason}`; };

    setTimeout(() => {
      try { ws.close(); } catch {}
      resolve({ ric, opened, err, sample, rates, frames, closeInfo, ricsSeen: [...ricsSeen] });
    }, WAIT_MS);
  });
}

(async () => {
  for (const ric of RICS) {
    const r = await explore(ric);
    console.log('\u2500'.repeat(72));
    console.log(`RIC    : ${r.ric}`);
    console.log(`OPEN   : ${r.opened}   ERR: ${r.err}   CLOSE: ${r.closeInfo}   FRAMES: ${r.frames}`);
    console.log(`RICS   : ${r.ricsSeen.join(', ') || '(ninguno)'}`);
    if (r.rates.length) {
      const uniq = [...new Set(r.rates)];
      const min = Math.min(...uniq), max = Math.max(...uniq);
      const range = max - min;
      console.log(`RATES  : total=${r.rates.length} unicos=${uniq.length}`);
      console.log(`  min=${min}  max=${max}`);
      console.log(`  rango_abs=${range.toExponential(4)}  rango_rel=${(range / min).toExponential(4)}`);
      console.log(`  muestra=${uniq.slice(0, 6).join(', ')}`);
    } else {
      console.log('RATES  : sin rate');
    }
    if (r.sample) console.log(`SAMPLE : ${r.sample}`);
  }
  process.exit(0);
})();