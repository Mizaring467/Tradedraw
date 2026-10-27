# R25 · W02 (reintento) — VENTANA VÁLIDA

**Fecha:** 2026-10-27 00:52–01:12 (America/Bogota) · **Build:** `692d6f3` v19 · `buildId R23H-BUY-EXHAUSTION`
**Dispositivo:** Redmi A1 `220733SL` · batería 67 % al abrir · keepalive de pantalla activo (PID 30216)
**Modo:** `AUTONOMOUS` / `CONSERVATIVE` · DEMO verificada **113 veces** (real: 0)

## 1. Continuidad

| Métrica | Valor |
| :--- | :--- |
| Muestras `/status` | **113 / 113** |
| Ciclos sin respuesta | **0** |
| Huecos del keepalive | **0** |
| Cierre seguro | `DISABLED` en **+6,7 s** |
| Taps / trades / decisiones | 8 / **0** / 56 |
| Balance | 36.865.735,68 → 36.865.735,68 (Δ 0,00; DD 0,00 %) |
| Ticks | 2.894 |

El intento anterior de esta ventana quedó archivado en `window_02_intento1_invalido/` (caída de Wi-Fi) y **no consume cupo** según `PREREGISTRO_R25.md` §3.

## 2. V1–V4

| Criterio | Resultado | Evidencia |
| :--- | :--- | :--- |
| **V1** | **SÍ** | Δ(`totalWins`+`totalLosses`) = **0** en las 113 muestras (1/3 constante) y `trades.csv` con **0** trades; `tradesLossSinEvidencia=0`; `manualIncrements=0` |
| **V2** | **SÍ** | **56/56** filas de `decisions.csv` con ambas columnas S/R numéricas (100 %) |
| **V3** | **SÍ** | `/adaptive_state`: `firmas=3` == `len(lossSignatures)=3` |
| **V4** | **SÍ** | **0** ocurrencias `NO_PROCESADA` en `/events` (60 eventos) y `noProcesadaDuracionMaxMs` ausente |

## 3. Guardas

Sin `MODE_CHANGE` ajeno al bridge, sin cambio de PID, `isDemoAccount=true` en las 113 muestras.
Estado final: `mode=DISABLED`, `orderState=NINGUNA`, `hasPendingTrade=null`.
