# R23C — VENTANA 03 DE 06 · VALIDACIÓN ESTADÍSTICA YOLO

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Build congelada:** `ac079fd` · versionCode=14 · buildId=R23-YOLO-ITER2
**Modo:** AUTONOMOUS · Submodo: YOLO · Cuenta DEMO
**Ventana:** 20,0 min · 118 muestras de `/status`

---

## 1. RESULTADOS

| Métrica | Valor |
|---|---|
| **N** (resoluciones W/L) | **3** |
| **W** (ganadas) | **2** |
| **L** (perdidas) | **1** |
| **VOID** | **0** |
| **WR** | **66,7 %** |
| Payout observado | **82,0 %** |
| **WR_BE** (`1/(1+payout)`) | **54,9 %** |

> `trades.csv` = 3 filas, todas `"CONFIRMADA BUY/SELL saldo movido"` → **N coincide** con los
> contadores del motor (W 10→12, L 13→14). Fix `6322090` sigue correcto.

---

## 2. RACHAS

| Métrica | Valor |
|---|---|
| `max_win_streak` | **2** |
| `max_loss_streak` | **1** |

Secuencia cronológica: **L, W, W**. No hubo racha ≥ 5.

---

## 3. BALANCE

| Métrica | Valor |
|---|---|
| Saldo inicial | 38.554.736,64 COP |
| Saldo final | 38.618.736,64 COP |
| **Δ Balance neto** | **+64.000,00 COP** |
| Máx. drawdown de ventana | **−200.000,00 COP** (−0,519 %) |

Ventana **ganadora** (WR 66,7 % > WR_BE 54,9 %).

---

## 4. DISTRIBUCIÓN

| Métrica | Valor |
|---|---|
| BUY count / WR | **2 / 100 %** (2W-0L) |
| SELL count / WR | **1 / 0 %** (0W-1L) |
| Régimen predominante | **DOWNTREND** (67/118; UPTREND 45, SIDEWAYS 6) |
| Activo operado | **Z-CRY/IDX** (118/118) |

> **Cambio de sesgo:** por primera vez dominan los **BUY (2/3)** y además ganan (2/2). Coincide con
> régimen mayoritariamente **DOWNTREND** en la ventana (giro respecto a ventanas 01-02 que fueron
> UPTREND y 100 % SELL). Apunta a que la dirección emitida **no** está invertida respecto al régimen.

---

## 5. SEGURIDAD Y TELEMETRÍA

| Control | Resultado |
|---|---|
| Verificación DEMO | ✅ **118 / 118** `isDemoAccount==true` (0 reales) |
| `action = UNKNOWN` | ✅ **0** |
| Eventos `END root` en trades | ✅ **0** |
| Vetos `ADAPTIVE_BLOCK` | **102** ⚠️ (máximo de la fase) |
| Vetos `OPERACIÓN EN CURSO` (RISK_LIMIT) | **8** |
| Vetos `COOLDOWN` | **2** |
| Submodo YOLO sostenido | ✅ 118/118 |

⚠️ **Dos señales de alarma (recurrentes):**
1. **`ADAPTIVE_BLOCK` = 102** en esta ventana (vs 37 en w01, 57 en w02). El motor de autoaprendizaje
   bloquea la gran mayoría de señales → la frecuencia real de YOLO queda fuertemente mermada (sólo 3
   trades en 20 min).
2. **`COOLDOWN_ACTIVE` máximo = 175 s** (de nuevo >35 s esperado en YOLO). Salud: READY 81 / BLOCKED 19
   / DEGRADED 18.

---

## 6. BUILD

| Campo | Valor |
|---|---|
| `gitSha` | `ac079fd` |
| `versionCode` | 14 |
| `buildId` | `R23-YOLO-ITER2` |
| Submodo | `YOLO` (118/118) |

---

## 7. ACUMULADO R23C (tras ventana 03)

| | N | W | L | WR |
|---|---|---|---|---|
| Ventana 01 | 4 | 1 | 3 | 25,0 % |
| Ventana 02 | 6 | 3 | 3 | 50,0 % |
| **Ventana 03** | 3 | 2 | 1 | 66,7 % |
| **Acumulado** | **13** | **6** | **7** | **46,2 %** |

Meta N ≥ 30 — faltan 17. Paridad Live↔Replay: 0 %.

---

## 8. ARTEFACTOS

`live_samples.jsonl` (118) · `status_samples.jsonl` (118) · `events.jsonl` (curl) · `ticks.jsonl` (curl)
· `ticks_live.csv` · `decisions.csv` (115) · `trades.csv` (3) · `replay_comparison.json`/`_report.md`
· `monitor_stdout.log`.
