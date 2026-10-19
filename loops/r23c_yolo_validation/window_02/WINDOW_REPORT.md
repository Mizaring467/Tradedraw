# R23C — VENTANA 02 DE 06 · VALIDACIÓN ESTADÍSTICA YOLO

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Build congelada:** `ac079fd` · versionCode=14 · buildId=R23-YOLO-ITER2
**Modo:** AUTONOMOUS · Submodo: YOLO · Cuenta DEMO
**Ventana:** 20,0 min · 118 muestras de `/status`

---

## 1. RESULTADOS

| Métrica | Valor |
|---|---|
| **N** (resoluciones W/L) | **6** |
| **W** (ganadas) | **3** |
| **L** (perdidas) | **3** |
| **VOID** | **0** |
| **WR** | **50,0 %** |
| Payout observado | **82,0 %** |
| **WR_BE** (`1/(1+payout)`) | **54,9 %** |

> **Fix `6322090` verificado:** ahora `trades.csv` contiene exactamente **6 filas, todas
> `"CONFIRMADA SELL saldo movido"`**, y **N concuerda con los contadores del motor** (W 7→10, L 10→13).
> Ya no se cuelan eventos de reposo ni taps `ENVIADA`.

---

## 2. RACHAS

| Métrica | Valor |
|---|---|
| `max_win_streak` | **2** |
| `max_loss_streak` | **2** |

Secuencia cronológica de resoluciones: **L, L, W, W, L, W**. No hubo racha ≥ 5.
(`currentLossStreak` alcanzó 4 durante la ventana, arrastrando derrotas previas; dentro de la ventana
el máximo fue 2.)

---

## 3. BALANCE

| Métrica | Valor |
|---|---|
| Saldo inicial | 38.608.737,28 COP |
| Saldo final | 38.554.736,64 COP |
| **Δ Balance neto** | **−54.000,64 COP** |
| Máx. drawdown de ventana | **−300.001,28 COP** (−0,777 %) |

Pico = inicial; valle ≈ 38.308.736,00 en la fase de 2 derrotas consecutivas. Cierre casi neutro
(−54 k) pese al drawdown intermedio.

---

## 4. DISTRIBUCIÓN

| Métrica | Valor |
|---|---|
| BUY count / WR | **0 / N/A** |
| SELL count / WR | **6 / 50,0 %** (3W-3L) |
| Régimen predominante | **UPTREND** (99/118; DOWNTREND 10, SIDEWAYS 9) |
| Activo operado | **Z-CRY/IDX** (118/118) |

> **Todas las órdenes de la ventana fueron SELL**, pese a régimen mayoritariamente UPTREND. En ventana
> 01 también dominó SELL (4/6). Relevante para la Hipótesis de sesgo direccional.

---

## 5. SEGURIDAD Y TELEMETRÍA

| Control | Resultado |
|---|---|
| Verificación DEMO | ✅ **118 / 118** `isDemoAccount==true` (0 reales) |
| `action = UNKNOWN` | ✅ **0** |
| Eventos `END root` en trades | ✅ **0** |
| Vetos `ADAPTIVE_BLOCK` | **57** |
| Vetos `OPERACIÓN EN CURSO` (RISK_LIMIT) | **28** |
| Vetos `COOLDOWN` | **2** |
| Submodo YOLO sostenido | ✅ 118/118 |

⚠️ **Hallazgo de salud:** el motor estuvo **DEGRADED (46) / BLOCKED (41) / READY (31)** muestras.
Causas registradas: `ORDER_PENDING` (40), `COOLDOWN_ACTIVE` (~38), `WS_DISCONNECTED` (1).
Y **se observó un `COOLDOWN_ACTIVE: 180s`** — en YOLO el cooldown de pérdida debería ser 35 s, no 180 s
(ver `VALIDATOR_FINDINGS` de fases previas): posible guard de sesión de CONSERVATIVE filtrándose en YOLO.
La martingala alcanzó **M4 ($2)**.

---

## 6. BUILD

| Campo | Valor |
|---|---|
| `gitSha` | `ac079fd` |
| `versionCode` | 14 |
| `buildId` | `R23-YOLO-ITER2` |
| Submodo | `YOLO` (118/118) |

---

## 7. ARTEFACTOS

`live_samples.jsonl` (118) · `status_samples.jsonl` (118) · `events.jsonl` (curl) · `ticks.jsonl` (curl)
· `ticks_live.csv` · `decisions.csv` (95) · `trades.csv` (6, ya limpio) · `replay_comparison.json`/`_report.md`
· `monitor_stdout.log`.
