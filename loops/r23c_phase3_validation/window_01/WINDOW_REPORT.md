# R23C — FASE 3 · VENTANA 01 DE 03 (YOLO ENDURECIDO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Build:** versionCode=15 · versionName=1.5-r23c-phase3 · buildId=**R23C-PHASE3** · gitSha=b08459e
**Modo:** AUTONOMOUS · Submodo: YOLO · Cuenta DEMO
**Endurecimiento aplicado:** SELL confluencia ≥80 pts (BUY 75) · mecha contra-tendencia SELL ≥45 % en UPTREND (BUY 30 %) · MT_REJECTION PUT ≥40 % (CALL 30 %)
**Ventana:** 20,0 min · 116 muestras de `/status`

---

## 1. RESULTADOS

| Métrica | Valor |
|---|---|
| **N** (resoluciones W/L) | **1** |
| **W** | **1** |
| **L** | **0** |
| **VOID** | **0** |
| **WR** | **100,0 %** |
| Payout observado | **83,0 %** |
| **WR_BE** | **54,95 %** |

> `trades.csv` = 1 fila `"CONFIRMADA SELL saldo movido"` → N concuerda con el motor (W 0→1, L 0→0).
> ⚠️ **N=1 es estadísticamente inútil**: no permite inferencia de WR.

---

## 2. RACHAS

| Métrica | Valor |
|---|---|
| `max_win_streak` | **1** |
| `max_loss_streak` | **0** |
| Secuencia | **W** |

---

## 3. BALANCE

| Métrica | Valor |
|---|---|
| Saldo inicial | 38.276.736,00 COP |
| Saldo final | 38.359.736,32 COP |
| **Δ Balance neto** | **+83.000,32 COP** |
| Máx. drawdown de ventana | **−99.998,72 COP** (−0,261 %) |

---

## 4. DISTRIBUCIÓN Y EFECTO DEL ENDURECIMIENTO

| Dirección | Conteo | W | L | WR |
|---|---|---|---|---|
| **BUY** | **0** | 0 | 0 | N/A |
| **SELL** | **1** | 1 | 0 | 100 % (n=1) |
| Régimen predominante | **DOWNTREND** (112/116) | | | |
| Activo | **Z-CRY/IDX** (116/116) | | | |

### Comparativa vs baseline Fase 1
| Métrica | Fase 1 (baseline) | **Fase 3 w01** | Lectura |
|---|---|---|---|
| Proporción SELL | 60,9 % | 100 % (1/1) | Sin datos suficientes |
| WR de SELL | 42,86 % | 100 % (n=1) | No comparable (n=1) |
| Frecuencia (trades/20 min) | ~2-6 | **1** | ⚠️ **Muy baja** |

⚠️ **No se puede evaluar el efecto del endurecimiento**: con sólo 1 trade (SELL), y sin BUY, las
comparativas contra el baseline carecen de significancia. La muestra es insuficiente.

---

## 5. SEGURIDAD Y TELEMETRÍA

| Control | Resultado |
|---|---|
| Verificación DEMO | ✅ **116 / 116** `isDemoAccount==true` (0 reales) |
| `action = UNKNOWN` | ✅ **0** |
| Eventos `END root` en trades | ✅ **0** |
| Vetos `ADAPTIVE_BLOCK` | **59** |
| Vetos `OPERACIÓN EN CURSO` (RISK_LIMIT) | **2** |
| Vetos `COOLDOWN` | **0** |
| **Nivel máx. `COOLDOWN_ACTIVE`** | **4 s** ✅ |
| Submodo YOLO sostenido | ✅ 116/116 |

**Hallazgo positivo:** el `COOLDOWN_ACTIVE` máximo fue **4 s** — la anomalía de **175-180 s** observada
en todas las ventanas de Fase 1 **desapareció** en esta ventana. Salud: READY 109 / BLOCKED 6 /
DEGRADED 1 (mucho mejor que Fase 1).

---

## 6. BUILD

| Campo | Valor |
|---|---|
| `versionCode` | **15** |
| `buildId` | **R23C-PHASE3** |
| `gitSha` | b08459e |
| Submodo | `YOLO` (116/116) |

---

## 7. PARIDAD LIVE ↔ REPLAY (fase 2b)

| Métrica | Valor |
|---|---|
| Trades Live | 1 |
| Trades Replay | 8 |
| Pares | 0 |
| Exact matches | 0 |
| **Tasa** | **0,0 % → FAIL** |

---

## 8. ARTEFACTOS

`live_samples.jsonl` (116) · `status_samples.jsonl` (116) · `events.jsonl` (curl) · `ticks.jsonl` (curl)
· `ticks_live.csv` (**3354 ticks**) · `decisions.csv` (62) · `trades.csv` (1) ·
`replay_comparison.json`/`_report.md` · `monitor_stdout.log`.

---

## 9. NOTA DE VALIDACIÓN

La ventana cumple seguridad (DEMO 100 %, 0 UNKNOWN, 0 END root) y muestra una mejora clara en el
cooldown, pero **N=1 trade es insuficiente para evaluar el endurecimiento SELL**. Se recomienda
continuar con las ventanas 02 y 03 para acumular muestra.
