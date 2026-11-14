# R23C — VENTANA 05 DE 06 · VALIDACIÓN ESTADÍSTICA YOLO

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Build congelada:** `ac079fd` · versionCode=14 · buildId=R23-YOLO-ITER2
**Modo:** AUTONOMOUS · Submodo: YOLO · Cuenta DEMO
**Ventana:** 20,0 min · 117 muestras de `/status`

---

## 1. RESULTADOS

| Métrica | Valor |
|---|---|
| **N** (resoluciones W/L) | **3** |
| **W** (ganadas) | **0** |
| **L** (perdidas) | **3** |
| **VOID** | **0** |
| **WR** | **0,0 %** |
| Payout observado | **82,0 %** |
| **WR_BE** (`1/(1+payout)`) | **54,9 %** |

> `trades.csv` = 3 filas, todas `"CONFIRMADA BUY/SELL saldo movido"` → N concuerda con el motor
> (W 17→17, L 18→21). **Ventana perdedora (0 % de acierto).**

---

## 2. RACHAS

| Métrica | Valor |
|---|---|
| `max_win_streak` | **0** |
| `max_loss_streak` | **3** |

Secuencia cronológica: **L, L, L**. No hubo racha ≥ 5.

---

## 3. BALANCE

| Métrica | Valor |
|---|---|
| Saldo inicial | 38.628.736,00 COP |
| Saldo final | 38.410.736,64 COP |
| **Δ Balance neto** | **−217.999,36 COP** |
| Máx. drawdown de ventana | **−299.998,72 COP** (−0,777 %) |

---

## 4. DISTRIBUCIÓN

| Métrica | Valor |
|---|---|
| BUY count / WR | **2 / 0 %** (0W-2L) |
| SELL count / WR | **1 / 0 %** (0W-1L) |
| Régimen predominante | **DOWNTREND** (70/117; UPTREND 44, SIDEWAYS 3) |
| Activo operado | **Z-CRY/IDX** (117/117) |

> **Nota sobre el sesgo:** en esta ventana (DOWNTREND) los **BUY perdieron ambos** — rompe el patrón
> previo ("BUY gana / SELL pierde"). Con n=3 no es concluyente, pero debilita la hipótesis de sesgo
> direccional simple.

---

## 5. SEGURIDAD Y TELEMETRÍA

| Control | Resultado |
|---|---|
| Verificación DEMO | ✅ **117 / 117** `isDemoAccount==true` (0 reales) |
| `action = UNKNOWN` | ✅ **0** |
| Eventos `END root` en trades | ✅ **0** |
| Vetos `ADAPTIVE_BLOCK` | **50** |
| Vetos `OPERACIÓN EN CURSO` (RISK_LIMIT) | **10** |
| Vetos `COOLDOWN` | **0** |
| Submodo YOLO sostenido | ✅ 117/117 |

Salud: **READY 53 / BLOCKED 21 / DEGRADED 43**. `COOLDOWN_ACTIVE` máximo = **180 s** (persiste).

---

## 6. BUILD

| Campo | Valor |
|---|---|
| `gitSha` | `ac079fd` |
| `versionCode` | 14 |
| `buildId` | `R23-YOLO-ITER2` |
| Submodo | `YOLO` (117/117) |

---

## 7. ACUMULADO R23C (tras ventana 05)

| | N | W | L | WR |
|---|---|---|---|---|
| Ventana 01 | 4 | 1 | 3 | 25,0 % |
| Ventana 02 | 6 | 3 | 3 | 50,0 % |
| Ventana 03 | 3 | 2 | 1 | 66,7 % |
| Ventana 04 | 5 | 3 | 2 | 60,0 % |
| **Ventana 05** | 3 | 0 | 3 | 0,0 % |
| **Acumulado** | **21** | **9** | **12** | **42,9 %** |

Meta N ≥ 30 — faltan 9. **WR acumulado = 42,9 %**, claramente **por debajo del WR_BE 54,9 %**.

---

## 8. ARTEFACTOS

`live_samples.jsonl` (117) · `status_samples.jsonl` (117) · `events.jsonl` (curl) · `ticks.jsonl` (curl)
· `ticks_live.csv` · `decisions.csv` (63) · `trades.csv` (3) · `replay_comparison.json`/`_report.md`
· `monitor_stdout.log`.
