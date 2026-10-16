# R23C — FASE 3 · VENTANA 03 DE 03 (FINAL) · CIERRE DE SERIE

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Build:** versionCode=15 · versionName=1.5-r23c-phase3 · buildId=**R23C-PHASE3** · gitSha=b08459e
**Modo:** AUTONOMOUS · Submodo: YOLO · Cuenta DEMO
**Endurecimiento:** SELL confluencia ≥80 pts · mecha contra-tendencia SELL ≥45 % · MT_REJECTION PUT ≥40 %
**Ventana:** 20,0 min · 116 muestras de `/status`

---

## 1. RESULTADOS W03

| Métrica | Valor |
|---|---|
| **N** (resoluciones capturadas en-ventana) | **0** |
| **N** (efectivas, trade confirmado en `trades.csv`) | **1** |
| **W** | **0** |
| **L** | **1** |
| **VOID** | **0** |
| **WR** | **0 %** (n=1) |
| Payout | **82 %** |
| **WR_BE** | **54,95 %** |

> ⚠️ **Discrepancia N:** el único trade (`CONFIRMADA BUY saldo movido`) **resolvió como LOSS DESPUÉS
> de cerrar la ventana** → los contadores in-window marcaron W=0/L=0, pero `trades.csv`=1 y el saldo
> ya había caído −99.998,72. Verificado: al consultar el motor post-cierre, `W=2 L=2`.

---

## 2. RACHAS

| Métrica | Valor |
|---|---|
| `max_win_streak` | **0** |
| `max_loss_streak` | **1** |
| Secuencia (por resolución) | **L** |

---

## 3. BALANCE

| Métrica | Valor |
|---|---|
| Saldo inicial | 38.342.735,36 COP |
| Saldo final | 38.242.736,64 COP |
| **Δ Balance neto** | **−99.998,72 COP** |
| Máx. drawdown **real** | **−99.998,72 COP** (−0,261 %) |
| ~~Máx. drawdown crudo~~ | ~~−37.342.732,36 (−97,4 %)~~ ⚠️ **artefacto** |

> ⚠️ **Glitch de telemetría:** **1 muestra** reportó `balance = 1.000.003,00` (revertida al siguiente
> muestreo), generando un drawdown **falso** de −37,3 M COP (−97,4 %). **No es una pérdida real**;
> el balance nunca abandonó el rango 38,2-38,3 M salvo ese muestreo espurio. Se recomienda filtrar
> outliers en el cálculo de drawdown.

---

## 4. DISTRIBUCIÓN Y CIERRE ACUMULADO FASE 3 (w01 + w02 + w03)

### Ventana 03
| Dirección | Conteo | W | L | WR |
|---|---|---|---|---|
| **BUY** | **1** | 0 | 1 | 0 % |
| **SELL** | **0** | 0 | 0 | N/A |
| Régimen | **DOWNTREND** (80/116) | | | |

### Consolidado Fase 3
| Ventana | N | W | L | WR | Δ COP | SELL |
|---|---|---|---|---|---|---|
| w01 | 1 | 1 | 0 | 100 % | +83.000,32 | 1 |
| w02 | 2 | 1 | 1 | 50 % | −17.000,96 | 0 |
| **w03** | **1** | **0** | **1** | **0 %** | **−99.998,72** | **0** |
| **TOTAL** | **4** | **2** | **2** | **50,0 %** | **−33.999,36** | **1** |

### Desglose direccional consolidado
| Dirección | Conteo | W | L | WR |
|---|---|---|---|---|
| BUY | 3 | 1 | 2 | 33,3 % |
| SELL | 1 | 1 | 0 | 100 % (n=1) |
| **Total** | **4** | **2** | **2** | **50,0 %** |

### Efecto del endurecimiento SELL
| Métrica | Fase 1 (baseline) | **Fase 3** |
|---|---|---|
| Proporción de volumen SELL | **60,9 %** | **25,0 %** (1/4) |
| WR de SELL | 42,86 % | 100 % (n=1) |
| WR global | 47,8 % | **50,0 %** |
| Δ neto | −114.000,00 | **−33.999,36** |

- ✅ **El endurecimiento SÍ mitigó la sobrerrepresentación de SELL** (60,9 % → 25,0 %).
- ⚠️ **Pero el WR global sigue bajo WR_BE** (50,0 % < 54,95 %) → **no rentable**, aunque la pérdida
  neta se redujo 3× respecto a Fase 1 (−114 k → −34 k COP DEMO).
- ⚠️ **El volumen desplazado a BUY no rindió mejor**: BUY cayó a 33,3 % de WR en Fase 3 (vs el patrón
  "BUY gana" de Fase 1). Con n=4 es **no concluyente**.

- **Supresión por confluencia:** acumulado alto en la fase (el SELL filtrado con más agresividad,
  conforme al diseño asimétrico).

---

## 5. SEGURIDAD Y TELEMETRÍA

| Control | Resultado |
|---|---|
| Verificación DEMO | ✅ **116 / 116** `isDemoAccount=true` (0 reales) |
| `action = UNKNOWN` | ✅ **0** |
| Eventos `END root` en trades | ✅ **0** |
| Vetos `ADAPTIVE_BLOCK` | **100** |
| Vetos `OPERACIÓN EN CURSO` (RISK_LIMIT) | **4** |
| Vetos `COOLDOWN` | **0** |
| **Nivel máx. `COOLDOWN_ACTIVE`** | **0 s** ✅ |
| Submodo YOLO sostenido | ✅ 116/116 |

Salud: READY 109 / BLOCKED 7. `COOLDOWN_ACTIVE` máx = **0 s** (excelente).

---

## 6. BUILD Y PARIDAD

| Campo | Valor |
|---|---|
| `versionCode` | **15** |
| `buildId` | **R23C-PHASE3** |
| `gitSha` | b08459e |
| Submodo | `YOLO` (116/116) |

**Paridad Live↔Replay (fase 2b):** 1 trade live vs 12 replay → **1 par, 1 exact match = 100 % PASS** ✅
(segundo PASS consecutivo; sigue siendo n=1, no concluyente).

---

## 7. ARTEFACTOS

`live_samples.jsonl` (116) · `status_samples.jsonl` (116) · `events.jsonl` (curl) · `ticks.jsonl` (curl)
· `ticks_live.csv` (**4881 ticks**) · `decisions.csv` (118) · `trades.csv` (1) ·
`replay_comparison.json`/`_report.md` (PASS) · `monitor_stdout.log`.

---

## 8. NOTA DE VALIDACIÓN

Seguridad 100 % intacta y paridad en PASS, pero la serie tiene **N=4 total** (insuficiencia muestral
severa), el endurecimiento **redujo el sesgo SELL** sin alcanzar rentabilidad (WR 50 % < WR_BE 54,95 %),
y aparecieron **dos defectos de telemetría**: (a) resolución de trades fuera de la ventana de captura
(subcuenta N), y (b) glitch puntual de balance que infla el drawdown. Ambos deben corregirse antes de
concluir estadísticamente la Fase 3.
