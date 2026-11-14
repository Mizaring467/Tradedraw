# R23C — FASE 3 · VENTANA 02 DE 03 (YOLO ENDURECIDO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Build:** versionCode=15 · versionName=1.5-r23c-phase3 · buildId=**R23C-PHASE3** · gitSha=b08459e
**Modo:** AUTONOMOUS · Submodo: YOLO · Cuenta DEMO
**Endurecimiento:** SELL confluencia ≥80 pts · mecha contra-tendencia SELL ≥45 % · MT_REJECTION PUT ≥40 %
**Ventana:** 20,0 min · 116 muestras de `/status`

---

## 1. RESULTADOS

| Métrica | Valor |
|---|---|
| **N** (resoluciones W/L) | **2** |
| **W** | **1** |
| **L** | **1** |
| **VOID** | **0** |
| **WR** | **50,0 %** |
| Payout observado | **83,0 %** |
| **WR_BE** | **54,95 %** |

> `trades.csv` = 2 filas, ambas `"CONFIRMADA BUY saldo movido"` → N concuerda con el motor.

---

## 2. RACHAS

| Métrica | Valor |
|---|---|
| `max_win_streak` | **1** |
| `max_loss_streak` | **1** |
| Secuencia | **W, L** |

---

## 3. BALANCE

| Métrica | Valor |
|---|---|
| Saldo inicial | 38.359.736,32 COP |
| Saldo final | 38.342.735,36 COP |
| **Δ Balance neto** | **−17.000,96 COP** |
| Máx. drawdown de ventana | **−100.001,28 COP** (−0,261 %) |

---

## 4. DISTRIBUCIÓN Y ACUMULADO FASE 3

### Ventana 02
| Dirección | Conteo | W | L | WR |
|---|---|---|---|---|
| **BUY** | **2** | 1 | 1 | 50 % |
| **SELL** | **0** | 0 | 0 | N/A |
| Régimen predominante | **UPTREND** (72/116) | | | |
| Activo | **Z-CRY/IDX** (116/116) | | | |

### Acumulado Fase 3 (w01 + w02)
| Dirección | Conteo | W | L | WR |
|---|---|---|---|---|
| BUY | 2 | 1 | 1 | 50 % |
| SELL | 1 | 1 | 0 | 100 % |
| **Total** | **3** | **2** | **1** | **66,7 %** |

- **Proporción SELL acumulada = 1/3 = 33,3 %** (vs 60,9 % en baseline Fase 1) → el endurecimiento
  **sí reduce la sobrerrepresentación de SELL**, aunque n=3 es aún insuficiente.
- **Supresión por confluencia:** `suppressedByConfluence` acumulado alto (las señales SELL son
  filtradas con más agresividad, acorde al diseño).
- Δ Balance acumulado Fase 3 = **+65.999,36 COP**.

---

## 5. SEGURIDAD Y TELEMETRÍA

| Control | Resultado |
|---|---|
| Verificación DEMO | ✅ **116 / 116** `isDemoAccount=true` (0 reales) |
| `action = UNKNOWN` | ✅ **0** |
| Eventos `END root` en trades | ✅ **0** |
| Vetos `ADAPTIVE_BLOCK` | **116** ⚠️ (máximo de toda la fase) |
| Vetos `OPERACIÓN EN CURSO` (RISK_LIMIT) | **2** |
| Vetos `COOLDOWN` | **0** |
| **Nivel máx. `COOLDOWN_ACTIVE`** | **178 s** ⚠️ |
| Submodo YOLO sostenido | ✅ 116/116 |

Salud: READY 84 / BLOCKED 13 / DEGRADED 19.

⚠️ **Regresión observada:** el `COOLDOWN_ACTIVE` volvió a **178 s** en esta ventana (en w01 fue 4 s).
La anomalía es **intermitente**, no persistente ni corregida. `ADAPTIVE_BLOCK` en su máximo (116).

---

## 6. BUILD

| Campo | Valor |
|---|---|
| `versionCode` | **15** |
| `buildId` | **R23C-PHASE3** |
| `gitSha` | b08459e |
| Submodo | `YOLO` (116/116) |

---

## 7. PARIDAD LIVE ↔ REPLAY (fase 2b) — ✅ PRIMERA VENTANA EN PASS

| Métrica | Valor |
|---|---|
| Trades Live | 2 |
| Trades Replay | 9 |
| Pares | 1 |
| **Exact matches** | **1** |
| **Tasa** | **100,0 % → PASS** |

> **Hito:** primer **PASS** de paridad Live↔Replay en toda la serie R23C (todas las ventanas previas
> habían sido FAIL 0 %). Con 1 par, sin embargo, no es concluyente (n=1).

---

## 8. ARTEFACTOS

`live_samples.jsonl` (116) · `status_samples.jsonl` (116) · `events.jsonl` (curl) · `ticks.jsonl` (curl)
· `ticks_live.csv` (**4923 ticks**) · `decisions.csv` (120) · `trades.csv` (2) ·
`replay_comparison.json`/`_report.md` (PASS) · `monitor_stdout.log`.

---

## 9. NOTA DE VALIDACIÓN

Seguridad intacta y **primer PASS de paridad**, pero **N=2 sigue siendo insuficiente**. La frecuencia
de disparo permanece muy baja (2 trades/20 min) pese a que el endurecimiento sólo afecta a SELL. El
cooldown anómalo (178 s) reapareció. Se recomienda la ventana 03 para completar la serie.
