// Sonda read-only: descubre que protocolo habla as.binomo.com / ws.binomo.com
// Sin credenciales. Solo handshake + subscribe + escucha. NO opera.
const WebSocket = require('ws');

const HOSTS = ['wss://as.binomo.com/', 'wss://ws.binomo.com/?v=2&vsn=2.0.0'];
const HEADERS = {
  'Origin': 'https://binomo.com',
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36',
  'Accept-Language': 'en-US,en;q=0.9',
};

// Variante 1: Phoenix Channels V2 (array wire) — join topic "asset:Z-CRY/IDX"
const PHX_JOINS = [
  [null, '1', 'phoenix', 'heartbeat', {}],
  ['2', '3', 'asset:Z-CRY/IDX', 'phx_join', {}],
];

// Variante 2: objeto crudo (lo que probo la app del usuario)
const OBJ_SUB = { action: 'subscribe', data: [{ ric: 'Z-CRY/IDX' }] };
// Variante 3: la forma del repo hert0t (rics plural)
const HER0T_SUB = { action: 'subscribe', rics: ['Z-CRY/IDX'] };

function probe(url, label, sendFn, timeoutMs = 9000) {
  return new Promise((resolve) => {
    const log = { url, label, opened: false, status: null, err: null, msgs: [] };
    let ws;
    try {
      ws = new WebSocket(url, { headers: HEADERS, handshakeTimeout: 12000 });
    } catch (e) { log.err = String(e); return resolve(log); }

    ws.on('unexpected-response', (_req, res) => {
      log.status = res.statusCode;
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        log.err = `HTTP ${res.statusCode}: ${Buffer.concat(chunks).toString().slice(0, 200)}`;
        resolve(log);
      });
    });
    ws.on('open', () => {
      log.opened = true;
      if (sendFn) { try { sendFn(ws); } catch (e) { log.err = 'send: ' + e; } }
    });
    ws.on('message', (d) => { if (log.msgs.length < 12) log.msgs.push(d.toString().slice(0, 500)); });
    ws.on('error', (e) => { log.err = log.err || String(e.message); });
    ws.on('close', (c, r) => { log.close = `${c} ${r}`; });
    setTimeout(() => { try { ws.close(); } catch {} resolve(log); }, timeoutMs);
  });
}

(async () => {
  const out = [];
  for (const h of HOSTS) {
    out.push(await probe(h, 'PHX_V2_array', (ws) => {
      PHX_JOINS.forEach((m) => ws.send(JSON.stringify(m)));
    }));
    out.push(await probe(h, 'OBJ_action_subscribe_data_ric', (ws) => ws.send(JSON.stringify(OBJ_SUB))));
    out.push(await probe(h, 'OBJ_action_subscribe_rics', (ws) => ws.send(JSON.stringify(HER0T_SUB))));
  }
  for (const r of out) {
    console.log('─'.repeat(70));
    console.log(`URL   : ${r.url}`);
    console.log(`SEND  : ${r.label}`);
    console.log(`OPEN  : ${r.opened}  HTTP: ${r.status}`);
    console.log(`ERR   : ${r.err}`);
    console.log(`CLOSE : ${r.close}`);
    console.log(`MSGS  : ${r.msgs.length}`);
    r.msgs.forEach((m, i) => console.log(`   [${i}] ${m}`));
  }
  process.exit(0);
})();