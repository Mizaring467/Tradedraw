# R23C — VENTANA 06 DE 06 (FINAL) · VALIDACIÓN ESTADÍSTICA YOLO

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Build congelada:** `ac079fd` · versionCode=14 · buildId=R23-YOLO-ITER2
**Modo:** AUTONOMOUS · Submodo: YOLO · Cuenta DEMO
**Ventana:** 20,0 min · 111 muestras de `/status`

---

## 1. RESULTADOS

| Métrica | Valor |
|---|---|
| **N** (resoluciones W/L) | **2** |
| **W** (ganadas) | **2** |
| **L** (perdidas) | **0** |
| **VOID** | **0** |
| **WR** | **100,0 %** |
| Payout observado | **82,0 %** |
| **WR_BE** (`1/(1+payout)`) | **54,9 %** |

> `trades.csv` = 2 filas, ambas `"CONFIRMADA BUY/SELL saldo movido"` → N concuerda con el motor
> (W 17→19, L 21→21). Ventana **ganadora perfecta**.

---

## 2. RACHAS

| Métrica | Valor |
|---|---|
| `max_win_streak` | **2** |
| `max_loss_streak` | **0** |

Secuencia cronológica: **W, W**. No hubo racha ≥ 5.

---

## 3. BALANCE

| Métrica | Valor |
|---|---|
| Saldo inicial | 38.410.736,64 COP |
| Saldo final | 38.576.737,28 COP |
| **Δ Balance neto** | **+166.000,64 COP** |
| Máx. drawdown de ventana | **−100.001,28 COP** (−0,260 %) |

---

## 4. DISTRIBUCIÓN

| Métrica | Valor |
|---|---|
| BUY count / WR | **1 / 100 %** |
| SELL count / WR | **1 / 100 %** |
| Régimen predominante | **DOWNTREND** (78/111; UPTREND 30, SIDEWAYS 3) |
| Activo operado | **Z-CRY/IDX** (111/111) |

---

## 5. SEGURIDAD Y TELEMETRÍA

| Control | Resultado |
|---|---|
| Verificación DEMO | ✅ **111 / 111** `isDemoAccount==true` (0 reales) |
| `action = UNKNOWN` | ✅ **0** |
| Eventos `END root` en trades | ✅ **0** |
| Vetos `ADAPTIVE_BLOCK` | **93** (muy alto) |
| Vetos `OPERACIÓN EN CURSO` (RISK_LIMIT) | **10** |
| Vetos `COOLDOWN` | **4** |
| Submodo YOLO sostenido | ✅ 111/111 |

Salud: **READY 74 / BLOCKED 14 / DEGRADED 23**. `COOLDOWN_ACTIVE` máximo = **175 s** (persiste).

**Mejora de infraestructura (commit `a6d1418`, fase 2b):** la **captura incremental de ticks**
guardó **4901 ticks** durante la ventana completa (vs ~2000 del buffer en ventanas 01-05).

---

## 6. BUILD

| Campo | Valor |
|---|---|
| `gitSha` | `ac079fd` |
| `versionCode` | 14 |
| `buildId` | `R23-YOLO-ITER2` |
| Submodo | `YOLO` (111/111) |

---

## 7. ACUMULADO FINAL R23C (6 ventanas)

| Ventana | N | W | L | WR | Δ Balance |
|---|---|---|---|---|---|
| 01 | 4 | 1 | 3 | 25,0 % | −217.999,36 |
| 02 | 6 | 3 | 3 | 50,0 % | −54.000,64 |
| 03 | 3 | 2 | 1 | 66,7 % | +64.000,00 |
| 04 | 5 | 3 | 2 | 60,0 % | +145.999,36 |
| 05 | 3 | 0 | 3 | 0,0 % | −217.999,36 |
| **06** | **2** | **2** | **0** | **100,0 %** | **+166.000,64** |
| **TOTAL** | **23** | **11** | **12** | **47,8 %** | **−114.000,00** |

- **N acumulado = 23** (meta era ≥ 30; quedó **corta** por la baja frecuencia de disparo).
- **WR global = 47,8 %** vs **WR_BE = 54,9 %** → **NO rentable** (Δ global **−114.000,00 COP**).
- Varianza inter-ventana extrema (0 %–100 %), coherente con n≤6 por ventana.

**Paridad Live↔Replay (fase 2b, modo trades):** 2 trades live vs 10 replay → 1 par, 0 exact matches,
**0,0 % → FAIL**. La cobertura sigue siendo el cuello de botella estructural.

---

## 8. ARTEFACTOS

`live_samples.jsonl` (111) · `status_samples.jsonl` (111) · `events.jsonl` (curl) · `ticks.jsonl` (curl)
· `ticks_live.csv` (**4901 ticks**) · `decisions.csv` (111) · `trades.csv` (2) · `replay_comparison.json`
/ `replay_comparison_report.md` (modo trades) · `monitor_stdout.log`.
