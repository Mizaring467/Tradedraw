# R23C — VENTANA 04 DE 06 · VALIDACIÓN ESTADÍSTICA YOLO

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Build congelada:** `ac079fd` · versionCode=14 · buildId=R23-YOLO-ITER2
**Modo:** AUTONOMOUS · Submodo: YOLO · Cuenta DEMO
**Ventana:** 20,0 min · 118 muestras de `/status`

---

## 1. RESULTADOS

| Métrica | Valor |
|---|---|
| **N** (resoluciones W/L) | **5** |
| **W** (ganadas) | **3** |
| **L** (perdidas) | **2** |
| **VOID** | **0** |
| **WR** | **60,0 %** |
| Payout observado | **82,0 %** |
| **WR_BE** (`1/(1+payout)`) | **54,9 %** |

> `trades.csv` = 5 filas, todas `"CONFIRMADA BUY/SELL saldo movido"` → N concuerda con el motor
> (W 13→16, L 16→18). Ventana **ganadora** (60 % > 54,9 %).

---

## 2. RACHAS

| Métrica | Valor |
|---|---|
| `max_win_streak` | **2** |
| `max_loss_streak` | **1** |

Secuencia cronológica: **L, W, W, L, W**. No hubo racha ≥ 5.

---

## 3. BALANCE

| Métrica | Valor |
|---|---|
| Saldo inicial | 38.400.737,28 COP |
| Saldo final | 38.546.736,64 COP |
| **Δ Balance neto** | **+145.999,36 COP** |
| Máx. drawdown de ventana | **−200.000,00 COP** (−0,521 %) |

---

## 4. DISTRIBUCIÓN

| Métrica | Valor |
|---|---|
| BUY count / WR | **2 / 100 %** (2W-0L) |
| SELL count / WR | **3 / 33 %** (1W-2L) |
| Régimen predominante | **UPTREND** (76/118; DOWNTREND 42) |
| Activo operado | **Z-CRY/IDX** (118/118) |

---

## 5. SEGURIDAD Y TELEMETRÍA

| Control | Resultado |
|---|---|
| Verificación DEMO | ✅ **118 / 118** `isDemoAccount==true` (0 reales) |
| `action = UNKNOWN` | ✅ **0** |
| Eventos `END root` en trades | ✅ **0** |
| Vetos `ADAPTIVE_BLOCK` | **37** (normalizado respecto a w03) |
| Vetos `OPERACIÓN EN CURSO` (RISK_LIMIT) | **27** |
| Vetos `COOLDOWN` | **0** |
| Submodo YOLO sostenido | ✅ 118/118 |

Salud: **READY 66 / BLOCKED 27 / DEGRADED 25**. `COOLDOWN_ACTIVE` máximo = **178 s** (persiste el
cooldown anómalo >35 s en YOLO).

---

## 6. BUILD

| Campo | Valor |
|---|---|
| `gitSha` | `ac079fd` |
| `versionCode` | 14 |
| `buildId` | `R23-YOLO-ITER2` |
| Submodo | `YOLO` (118/118) |

---

## 7. ACUMULADO R23C (tras ventana 04)

| | N | W | L | WR |
|---|---|---|---|---|
| Ventana 01 | 4 | 1 | 3 | 25,0 % |
| Ventana 02 | 6 | 3 | 3 | 50,0 % |
| Ventana 03 | 3 | 2 | 1 | 66,7 % |
| **Ventana 04** | 5 | 3 | 2 | 60,0 % |
| **Acumulado** | **18** | **9** | **9** | **50,0 %** |

Meta N ≥ 30 — faltan 12. **WR acumulado = 50,0 %** (por debajo del WR_BE 54,9 %).

---

## 8. ARTEFACTOS

`live_samples.jsonl` (118) · `status_samples.jsonl` (118) · `events.jsonl` (curl) · `ticks.jsonl` (curl)
· `ticks_live.csv` · `decisions.csv` (69) · `trades.csv` (5) · `replay_comparison.json`/`_report.md`
· `monitor_stdout.log`.
