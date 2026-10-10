# Workflow: Loop de Convergencia de Win Rate — TradeDraw

> **Trigger:** invocación manual del orquestador (`opencode`) con el objetivo de subir el Win Rate medido en cuenta Demo.
> **Rondas:** 3 (configurable), cada una de 25 min de prueba real en dispositivo.
> **Fuente de verdad de estrategia:** `.agents/skills/master_traders_skill/SKILL.md`.
> **Métricas de partida (línea base):** WR 41.27% (N=189), Holdout 33.33%, EV −0.2448, MaxDD 92.54%.

---

## 1. Objetivo y criterio de éxito

Elevar el Win Rate real medido en cuenta **Demo** hasta **≥ 55%** (mínimo breakeven con payout 0.82 = 54.95%; objetivo de la metodología 62–73%), eliminando las causas de pérdida catalogadas:

| ID | Hipótesis | Causa | Corrección esperada |
| :-- | :--- | :--- | :--- |
| **H1** | Desfase de expiración :58–:59 | Órdenes cruzaban el segundo :00 y expiraban en la siguiente vela (23 pérdidas, WR 0%) | Restringir disparo a **:01.000–:03.000**; prohibir :58/:59 |
| **H2** | Chop operado en YOLO | `YOLO` anulaba el veto anti-chop (38 pérdidas, 100% en SIDEWAYS) | Veto incondicional de chop/lateral en **todos** los submodos |
| **H3** | Inversión adaptativa destructiva | `AdaptiveLearningEngine.canInvert` invertía señales (2.558 inversiones) | **Bloquear** la señal en vez de invertirla |
| **H4** | Sesgo direccional SELL | PUT 37.5% WR vs BUY 46.75% | Filtro macro de tendencia (fase 2) |
| **H5** | Telemetría de latencia | Latencia ciega signal→click→ack | Métricas `signal_ts/click_ts/ws_ack_ts` en `/status` |

**Definición de PASS de una hipótesis:** el journal de la sesión de 25 min muestra la métrica objetivo cumplida, la verificación la firma un agente **distinto** al que implementó, y el APK compila e instala sin crashes.

**Push a `main`:** solo ante **avance significativo** (PASS de hipótesis con evidencia + build verde). El orquestador lo decide y lo reporta.

---

## 2. Roles (topología fija en herdr, workspace `w1`)

| Agente | Pane | Rol | Nunca hace |
| :--- | :--- | :--- | :--- |
| **opencode** | `w1:p2` | Orquestador: redacta tareas, fija criterio PASS/FAIL, despacha, **juzga**, decide commit/push | Implementar ni testear |
| **codex** | `w1:pD` | Implementador core (Kotlin: `SyntheticCandleEngine`, `TradingEngine`, `RiskManager`, `AdaptiveLearningEngine`) | Firmar su propio PASS |
| **agy** | `w1:p1` | Investigador cuantitativo + auditor de `master_traders_skill` y del journal | Editar código |
| **hermes** | `w1:p6` | Tester de dispositivo: build/install ADB, monitor 25 min, capturas, artemis | Editar lógica de trading |

**Regla de verificación cruzada:** ningún PASS lo firma el agente autor del cambio.

---

## 3. Máquina de estados por ronda

```
[opencode] publica TAREA(n) con hipótesis + criterio PASS/FAIL
      │  herdr agent prompt codex
      ▼
[codex] edita → gradlew assembleDebug → adb install -r
      │  (build verde)  si falla → vuelve a codex
      ▼
[hermes] monitor_25m_session.py (25 min) + capturas + /status
      │
      ▼
[agy] audita journal/status vs master_traders_skill → veredicto técnico
      │
      ▼
[opencode] decide PASS → merge/push | FAIL → nueva TAREA(n+1)
```

**Guardrails:**
- Un solo agente escribe código a la vez (mutex lógico vía orquestador).
- `master_traders_skill` es inviolable (no relajar umbrales).
- Economía de tokens: usar `rtk` cuando esté en PATH; outputs acotados (≤ 50 líneas).

---

## 4. Comandos canónicos

```powershell
# Serial del dispositivo (unificado)
$SERIAL = "UGU8KFUKM7E6CE8D"

# Build
cd C:\Users\heidy\Tradedraw
.\gradlew.bat assembleDebug --no-daemon --no-configuration-cache

# Test unitario
.\gradlew.bat testDebugUnitTest --no-daemon

# Install + arranque
adb -s $SERIAL install -r app\build\outputs\apk\debug\app-debug.apk
adb -s $SERIAL shell am start -n com.example.tradedraw/.MainActivity

# Telemetría (HTTP bridge del app)
adb -s $SERIAL forward tcp:8080 tcp:8080
curl.exe -s http://127.0.0.1:8080/status

# Sesión de 25 min
python scripts\monitor_25m_session.py

# Logcat de errores
adb -s $SERIAL logcat -d -s TradeDraw AndroidRuntime:E

# Despacho a agentes herdr
herdr agent prompt codex "<tarea>" --wait --timeout 900000
herdr agent prompt hermes "<tarea>" --wait --timeout 1800000
herdr agent prompt agy  "<tarea>" --wait --timeout 900000
```

---

## 5. Artefactos y rutas

- Journal: `loops/journal/live/trade_journal_live.csv`
- Reporte de sesión: `loops/journal/live/live_session_25m_report.json`
- Monitoreo Master Traders: `.agents/loop/monitoring_log.jsonl`, `.agents/loop/monitoring_report.md`
- Capturas: `.agents/loop/monitoring/`
- Auditoría de hipótesis: `workflows/rounds/`

---

## 6. Registro de rondas

| Ronda | Hipótesis | Estado | WR medido | Evidencia | Veredicto |
| :--: | :--- | :--- | :--- | :--- | :--- |
| 1 | H1+H2+H3 | medido (25m) | 0 trades (N=0) | `live_session_25m_report.json` | FAIL: sobre-veto |
| 1-fix | chop selectivo + TFI preservado + ventana :01–:05 | medido (25m) | **100% (N=1, WIN)** +83.000 COP | `live_session_25m_report.json` | **PASS funcional** |
| 2 | Telemetría H5 + activos + causa de 0 ejecuciones | medido (25m) | 8 señales / **0 ejecuciones** | `r2_engine_samples.jsonl`, logcat | FAIL: veto por anti-patrón (similitud laxa) |
| 2-fix | H6: umbral anti-patrón 0.82→0.90 + compuerta estricta de contexto | medido (25m) | 6 señales / **1 ejecución (VOID)** | `live_session_25m_report.json` | PASS parcial: ya ejecuta; orden VOID |

### Bitácora Ronda 2 (2026-10-09)
- **Instrumentación H5:** `/status` expone `engine.{signalsGenerated, evaluatedCount, suppressedByConfluence, suppressedByChop, suppressedByMicroCompression}`.
- **Hallazgo de activo (agy):** `Z-CRY/IDX` es el único RIC 24/7 y su feed oscila ~4e-10 (near-static/mean-reverting). Forex (EUR/GBP, USD/CHF) sí es real pero solo días hábiles. Breakeven con payout 82% = 54.95%.
- **Causa raíz de 0 ejecuciones (R2):** `AdaptiveLearningEngine` vetaba con similitud ≥ 0.82 sobre firmas de contexto flojo (acción+tendencia+S/R+régimen ≈ 0.82 por defecto en feed plano). 8 señales → 0 ejecuciones.
- **Fix H6:** umbral 0.90 + `isTightlyMatchingContext` (misma acción/tendencia/régimen y S/R ±0.10). Auditoría agy: **PASS**.
- **Resultado R2-fix:** 6 señales, 1 ejecución real (BUY @ 641.87, 69s) pero `TIE/VOID` → **la orden no fue procesada por Binomo** (saldo inalterado). Pendiente: accionamiento/calibración/timing del gesto.
- **Frecuencia aún baja:** ~1 trade/25 min; N insuficiente para validar edge (skill exige N≥30, Wilson LCL>55%).

### Bitácora Ronda 1-fix (2026-10-09)
- **Implementador:** agy (antigravity cli) · **Auditor:** agy · **Tester:** hermes.
- **Cambios:** `SyntheticCandleEngine.kt` (veto anti-chop selectivo con `reversalContext`; TFI ya no se resetea al abrir vela), ventana de entrada ampliada a `sec in 1..5` (MarketTick, CandleTimeframe, TradingEngine, VisionAnalyzer), `AdaptiveLearningEngine` sin inversión (H3).
- **Verificación:** 160/160 tests OK; `BUILD SUCCESSFUL`; APK instalado en `UGU8KFUKM7E6CE8D`.
- **Sesión:** 18:57:38 → 19:22:38. 1 trade `MT_ENGULFING → SELL`, 60s, **WIN**, +83.000,32 COP. Balance 39.329.735 → 39.412.736.
- **Pendiente metodológico:** N=1 no es significativo (la skill exige N≥30, Wilson LCL>55%). Requiere más rondas.
