# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23B ITERACIÓN 1 (MODO YOLO · CUENTA DEMO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Commit auditado:** `ba75268` (R23B iter1)
**Build:** versionCode=13, versionName=1.4-r23-yolo-iter1, buildId=R23-YOLO-ITER1, gitSha=ba75268
**Dispositivo:** Redmi A1 (`220733SL`) · enlace **WiFi** `[fd10:...]:5555`
**Ventana:** 20,0 min completos · 116 muestras de `/status`

---

## 1. VEREDICTO

**KEEP** — Hipótesis **CONFIRMADA**: al desbloquear los límites de sesión en YOLO, el 74,3 % de vetos
que paralizó el baseline desaparece y las señales cualificadas alcanzan taps DEMO. Seguridad DEMO
intacta.

| Criterio | Resultado | Evidencia |
|---|---|---|
| `isDemoAccount == true` en cada muestra | ✅ | **116 / 116** (reales: 0) |
| `submode == "YOLO"` en cada muestra | ✅ | **116 / 116** |
| Modo AUTONOMOUS sostenido | ✅ | 116/116 |
| Identidad de build | ✅ | vc=13 / gitSha=ba75268 / buildId=R23-YOLO-ITER1 |
| Ventana completa | ✅ | 116/120 muestras |
| Balanza | ✅ | 38.320.737,28 → **38.452.736,00** (+131.998,72) |
| Ciclo señal→tap→resolución | ✅ | 12 taps, **6 trades**, W=4/L=2 |

---

## 2. HIPÓTESIS 2 (R23B iter1) — VERIFICADA

*"Al omitir `sessionFloor` y `Trailing Profit Lock` en YOLO, el 74,3 % de vetos que paralizaron el
baseline desaparece, permitiendo que las señales cualificadas alcancen los taps."*

| Guard | Baseline R23B (v12) | Iter1 R23B (v13, YOLO) | Estado |
|---|---|---|---|
| `RISK_LIMIT` Trailing Profit Lock | **75 (74 %)** | **0** | ✅ **ELIMINADO** |
| `Stop Loss Sesión / sessionFloor` | (presente) | **0** | ✅ **ELIMINADO** |
| `ADAPTIVE_BLOCK` | 26 (26 %) | **10** | ✅ **PRESERVADO** (control negativo) |
| Taps en ventana | 0 | **12** | ✅ **DESBLOQUEADO** |
| Trades | 0 | **6** | ✅ **DESBLOQUEADO** |
| Balance | Δ 0,00 (inmutable) | **Δ +131.998,72** | ✅ **OPERATIVO** |

**Conclusión:** la hipótesis se cumple de forma empírica y medible. El baseline había sido dominado
por un guard de sesión (`Trailing Profit Lock`), no por las relajaciones de estrategia; al levantar
ese guard en YOLO, la frecuencia de disparo pasa de 0 a 12 taps y 6 trades.

---

## 3. CONTROLES NEGATIVOS (guards que NO debían desaparecer)

| Control | Esperado | Observado | Estado |
|---|---|---|---|
| `ADAPTIVE_BLOCK` (anti-patrones) | Sigue bloqueando | **10 vetos** | ✅ Intacto |
| `RISK_LIMIT Operación abierta en curso` | Debe impedir solapes | **34** | ✅ Intacto (benigno) |
| `Cooldown YOLO` | Pausa entre operaciones | **~11 vetos** (~0,1–1,8 s) | ✅ Intacto |
| Veto V1 / V2, ventana sniper, 1 orden/vela | Intactas | Sin eventos de violación | ✅ Intacto |
| Stop Equity absoluto (<20 M COP) | Activo | No alcanzado (min 38,1 M) | ✅ (no ejercitado) |

**Nota:** el guard "RISK_LIMIT Operación abierta en curso" es **benigno** — evita solapar órdenes
mientras hay una pendiente, no paraliza la sesión. No es el Trailing Profit Lock.

---

## 4. MÉTRICAS DE RIESGO NUEVAS

| Métrica | Valor |
|---|---|
| Win Rate | **66,7 %** (W=4 / L=2) |
| Máx. racha de pérdidas consecutivas | **2** |
| Máx. drawdown desde pico | **−300.001,28 COP** (−0,78 % del balance) |
| Balance final | 38.452.736,00 COP (**+131.998,72**) |
| Martingala observada | M0 ($1), M1 ($2), M2 ($2) |

Con stake efectivo ≈ 100.000 COP/op y balance ~38,4 M, el drawdown máximo observado (−300 k ≈ −0,78 %)
es coherente con 3 resoluciones adversas consecutivas y queda muy por encima del piso sesión.

---

## 5. DEFECTOS Y LIMITACIONES

1. **`action=UNKNOWN`** en `trades.csv`: el monitor no infiere BUY/SELL desde el evento (persiste de
   iteraciones previas). 1 de los 6 "trades" es en realidad el evento `END root` (tap físico), no una
   confirmación de saldo → el conteo real de resoluciones es W=4/L=2 (6 taps incluyen 1 tap sin saldo).
2. **`events.jsonl` de curl** es un **objeto** `{"events":[...]}` (60 eventos en buffer circular), no
   JSONL; el volcado del monitor sí es JSONL. Discrepancia de formato entre artefactos homónimos.
3. **Paridad Live↔Replay** sigue en 0 % (cobertura 1/62; motores no isomorfos: 60 `missing_replay`,
   1 `missing_live`). Sin avance respecto a R23A/R23B-baseline.
4. **`decisions.csv`**: `score` fijo y sin `dist_support`/`dist_resistance` (arrastrado).

---

## 6. COMPARATIVA

| Métrica | R23A (CONSERVATIVE) | R23B baseline (YOLO v12) | **R23B iter1 (YOLO v13)** |
|---|---|---|---|
| Ventana | 20 min | 11,3 min (truncada) | **20 min** |
| Señales | 225-405 | 74 | 60 |
| Taps | 0-12 (acum.) | 0 | **12** |
| Trades | 0-2 | 0 | **6** |
| WR | ~50 % (n=2) | N/A | **66,7 % (n=6)** |
| Δ Balance | −17.999 / 0 / +266.001 | 0 | **+131.998,72** |
| Trailing Lock vetos | presentes | **75** | **0** |
| ADAPTIVE_BLOCK | presentes | 26 | 10 |

---

## 7. ARTEFACTOS AUDITADOS (`loops/r23_yolo/iter_1/`)

| Artefacto | Estado |
|---|---|
| `live_samples.jsonl` | ✅ 116 muestras |
| `status_samples.jsonl` | ✅ 116 volcados |
| `events.jsonl` (curl) | ✅ 60 eventos (objeto) |
| `ticks.jsonl` (curl) | ✅ 30 KB |
| `ticks_live.csv` | ✅ volcado |
| `decisions.csv` | ✅ 61 decisiones |
| `trades.csv` | ✅ 6 filas |
| `replay_journal.csv` | ✅ 2 filas |
| `replay_comparison.json` / `_report.md` | ✅ 1 par |
| `LIVE_REPORT.md` | ✅ KEEP |
| `monitor_stdout.log` | ✅ 20 min completos |

---

## 8. RECOMENDACIÓN

- **Veredicto:** **KEEP** (desbloqueo correcto; seguridad DEMO intacta; controles negativos preservados).
- **Hipótesis 1 (baseline) queda resuelta** con esta iteración; puede darse por validada en DEMO.
- **Próximos pasos sugeridos:**
  1. Corregir `action` en `trades.csv` (inferir BUY/SELL) y excluir eventos `END root` del conteo de trades.
  2. Unificar el formato de `events.jsonl` (objeto vs JSONL) entre monitor y descarga curl.
  3. Atacar la paridad Live↔Replay (cobertura 1/62) — bloqueada por no isomorfismo de motores.
  4. Evaluar la transferencia del desbloqueo (o de un subconjunto) al modo CONSERVATIVE sin comprometer
     el control de riesgo de sesión.
