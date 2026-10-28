# R25 · W01 — VENTANA VÁLIDA (transporte asegurado)

**Fecha:** 2026-10-27 23:59–00:19 (America/Bogota) · **Build:** `692d6f3` v19 `1.9-r23h-exhaustion` · `buildId R23H-BUY-EXHAUSTION`
**Dispositivo:** Redmi A1 `220733SL` · serial `192.168.1.196:5555` · batería 76 % al abrir
**Modo:** `AUTONOMOUS` / `CONSERVATIVE` · **cuenta DEMO** verificada 113 veces (real: 0)
**Watchdog de transporte:** PID 21931, 0 reposiciones durante la ventana (`~/forward_watchdog.log`)

## 1. Continuidad del arnés (la causa raíz de R24)

| Métrica | W02 R24 (rota) | W01 R25 (esta) |
| :--- | :--- | :--- |
| Muestras de `/status` | 19 de ~120 | **113 de 113** |
| Ciclos sin respuesta | 100 | **0** |
| Huecos de transporte | sí (forward caído) | **ninguno** |
| Cierre seguro | no se pudo ordenar `DISABLED` | **`DISABLED` en +1,7 s** |

## 2. Operación observada (informativa, fuera de alcance)

- 4 taps despachados, **2 trades** ejecutados (`SELL`), 38 decisiones.
- Balance 37.083.735,04 → 36.883.737,60 (Δ −199.997,44 COP; drawdown 0,54 %).
- Resultado: 0 W / 2 L. WR 0 % — **`INFORMATIVO — FUERA DE ALCANCE DE R25`** (2 trades no dimensionan nada).
- 2.892 ticks capturados; contrafactuales de `ADAPTIVE_BLOCK` registrados (2 `COUNTERFACTUAL_LOSS`).

## 3. Evaluación V1–V4 (esta ventana)

| Criterio | Resultado | Evidencia |
| :--- | :--- | :--- |
| **V1** contadores | **SÍ** | Δ(`totalWins`+`totalLosses`) = 0+2 = **2**; `trades.csv` tiene **2** trades resueltos, ambos `LOSS` con evidencia de débito en la cadena de saldos; `tradesLossSinEvidencia=0`; `manualIncrements=0` |
| **V2** S/R exportados | **SÍ** | **38/38** filas de `decisions.csv` con `distanceToSupportRatio` y `distanceToResistanceRatio` numéricos (100,00 %; umbral 99 %) |
| **V3** memoria adaptativa | **SÍ** | `/adaptive_state`: `firmas=2` == `len(lossSignatures)=2`, volcado parseable |
| **V4** `NO_PROCESADA` | **SÍ** | **0 ocurrencias** en la ventana (ni en `/events` ni `noProcesadaDuracionMaxMs`); sin ocurrencias que clasificar |
| **V5** paridad live↔replay | **PENDIENTE** | se evalúa una sola vez al cierre del cubo; con 2 trades live no alcanza el mínimo de 20 |

## 4. Guardas

- Sin `MODE_CHANGE` ajeno al bridge durante la ventana (`mode_log.json`: 3 eventos, el último `AUTONOMOUS->DISABLED source=bridge:/mode`).
- Sin crash ni cambio de PID.
- Estado final: `mode=DISABLED`, `orderState=NINGUNA`, `isDemoAccount=true`.

## 5. Artefactos

`status_samples.jsonl` (113), `live_samples.jsonl`, `decisions.csv` (38), `trades.csv` (2),
`ticks_live.csv` (2.892), `events.jsonl`, `mode_log.json`, `replay_comparison.json`, `LIVE_REPORT.md`,
`window_metadata.json`, `precondiciones.md`.
