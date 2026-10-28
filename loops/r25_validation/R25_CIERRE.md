# R25 · CIERRE DEL CUBO — V1–V4 PASS EN 3 VENTANAS, V5 NO EVALUABLE

**Cubo:** R25 (instrumentación, estrategia congelada) · **Preregistro:** `workflows/PREREGISTRO_R25.md` (fijado antes de la primera ventana)
**Build:** `692d6f3` · v19 `1.9-r23h-exhaustion` · `buildId R23H-BUY-EXHAUSTION` · sin cambios de código durante el cubo
**Dispositivo:** Redmi A1 `220733SL` (Android 12) · serial `192.168.1.196:5555` · DEMO en todas las muestras
**Cerrado:** 2026-10-27 01:34 (America/Bogota)

---

## 1. Ventanas

| Ventana | Horario | Muestras | Taps | Trades | Decisiones | Balance (Δ) | Veredicto |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **W01** | 23:59–00:19 | 113/113 | 4 | 2 (0W/2L) | 38 | 37.083.735,04 → 36.883.737,60 (−199.997,44) | válida |
| W02 intento 1 | 00:25–00:45 | 109 | 8 | 2 (1W/1L) | 75 | — | **inválida** (caída de Wi-Fi; ver `W02_INCIDENTE_RED.md`) |
| **W02** | 00:52–01:12 | 113/113 | 8 | 0 | 56 | 36.865.735,68 → 36.865.735,68 (0,00) | válida |
| **W03** | 01:13–01:33 | 112/112 | 8 | 0 | 78 | 36.865.735,68 → 36.865.735,68 (0,00) | válida |

**3 ventanas válidas** (el máximo preregistrado). Los cierres seguros se ordenaron en +1,7 s, +6,7 s y +5,2 s del cierre nominal.

## 2. Criterios

| Criterio | W01 | W02 | W03 | Global |
| :--- | :--- | :--- | :--- | :--- |
| **V1** contadores vs ledger | SÍ (2==2, ambos LOSS con débito; `lossSinEvidencia=0`, `manualInc=0`) | SÍ (Δ0==0) | SÍ (Δ0==0) | **SÍ** |
| **V2** columnas S/R | SÍ 38/38 (100 %) | SÍ 56/56 (100 %) | SÍ 78/78 (100 %) | **SÍ** |
| **V3** memoria adaptativa | SÍ (2==2) | SÍ (3==3) | SÍ (3==3) | **SÍ** |
| **V4** `NO_PROCESADA` | SÍ (0 ocurrencias) | SÍ (0) | SÍ (0) | **SÍ** |
| **V5** paridad live↔replay | — | — | — | **NO EVALUABLE** |

**V5 no es evaluable por muestra:** el mínimo preregistrado es **≥ 20 trades live** y el cubo produjo **2** (ambos en W01). Con la tasa observada (~2 trades por ventana de 20 min) harían falta ≥ 10 ventanas, por encima del tope de 3. No se extiende ni se reinterpreta: así se reporta.

## 3. Veredicto del cubo

> **V1–V4: PASS en las 3 ventanas válidas. V5: NO EVALUABLE por muestra insuficiente.**
> Según `PREREGISTRO_R25.md` §7, `PASS-INSTRUMENTACIÓN` exige además V5 evaluable, por lo que el
> veredicto formal del cubo es **NO EVALUABLE (por V5)**, no PASS.

Cifras de resultado de las 2 operaciones (`INFORMATIVO — FUERA DE ALCANCE DE R25`): 0 W / 2 L, WR 0 %, Δ −199.997,44 COP en DEMO. No dimensionan edge.

## 4. Cambios de arnés introducidos (fuera de la app)

1. `scripts/validation/adb_forward_watchdog.sh` — repone el reenvío ADB; causa raíz de las ventanas ciegas de R24. Probado y activo en las tres ventanas.
2. Keepalive de pantalla (`input keyevent WAKEUP` cada 45 s) durante las ventanas, tras la caída de Wi-Fi del intento 1 de W02.
3. `window_02_intento1_invalido/` conserva las muestras del intento fallido para auditoría.

## 5. Pendiente estructural (no resuelto por este cubo)

- **Caducidad de `AUTONOMOUS`** si falta heartbeat del bridge (decisión 6 de R24): el intento 1 de W02 dejó el motor armado ~5 min sin red. No ocurrió ninguna operación, pero el mecanismo sigue ausente.
- **V5 requiere volumen**: hace falta una ronda con ≥20 trades live (p. ej. ventanas de mayor duración o un mínimo de trades por ventana), preregistrada como R26.

## 6. Estado final

`mode=DISABLED`, `orderState=NINGUNA`, `hasPendingTrade=null`, `isDemoAccount=true`, balance **36.865.735,68 COP**. Cero órdenes abiertas.

---

## ADENDA 2026-10-27 22:26 — el pendiente (a) queda cerrado en R26

La caducidad de `AUTONOMOUS` que §5 declaraba ausente **ya está implementada y certificada en vivo**:

- Código: `f491ca4` (v20, `R26-AUTONOMOUS-EXPIRY`) — `AutonomousWatchdog` + tick de 30 s + heartbeat en el bridge.
- Certificación P1: armado por bridge, silencio real → `DISABLED` con `source=engine:autonomous-expiry` a los **630 s** (tope 10 min + tick).
- Certificación P2: con heartbeat cada 121 s, a los **11 min** seguía `AUTONOMOUS` y sin evento de caducidad.
- Evidencia: [`loops/r26_validation/R26_REPORTE.md`](../r26_validation/R26_REPORTE.md) · preregistro [`workflows/PREREGISTRO_R26.md`](../../workflows/PREREGISTRO_R26.md).

El pendiente (b) — V5 con ≥ 20 trades live — **sigue abierto** y no se ve afectado por esta adenda. El
veredicto del cubo R25 no cambia: `NO EVALUABLE (por V5)`.
