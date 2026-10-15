# R23 — COORDINACIÓN DE AUTOMEJORA CONTROLADA CON VALIDACIÓN LIVE

## 1. Identificación de la Ronda y Baseline
- **Ronda:** R23
- **Baseline inicial:** R22 (`e3253c0`)
- **Dispositivo Físico:** Xiaomi Redmi A1 (`220733SL`, Serial Hardware: `UGU8KFUKM7E6CE8D`)
- **Conexión ADB:** Inalámbrica TCP/IP sobre IPv6 (`[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`)
- **Modo Operativo Obligatorio:** `SHADOW MODE` (Cuenta DEMO, 0 taps, 0 órdenes reales, balance inmutable)

---

## 2. Registro de Iteraciones

| Iteración | SHA base | Hipótesis | Cambio | SHA candidato | Resultado live | Decisión |
|---|---|---|---|---|---|---|
| (Inicial) | `e3253c0` | Baseline R22: Infraestructura y paridad live-replay validadas al 100%, pero 18/18 vetos por V2_RANGE en Crypto IDX (sin SHADOW_DECISION aprobada). | N/A (Punto de partida) | `e3253c0` | 95 muestras, 18 VETO V2, 0 SHADOW_DECISION, 0 taps | BASELINE |
| **Iter 1** | `e3253c0` | Trazabilidad de Build en Runtime (gitSha, buildId, buildTimestamp en /status) + Telemetría Cuantitativa de V2_RANGE (rangoRelativoMinimo y rangoRelativoActual) para auditar la distancia al umbral durante 20 min live sin relajar guards. | Exponer metadatos de build y rango relativo en /status, bump a versionCode 6 (v1.4-r23-iter1) | `f5f9eaf` | 116 muestras (20 min), balance intacto 38.054.735,36 COP, taps=0, 140/140 vetos por V2_RANGE (rango 1.09e-10 a 6.62e-10 vs umbral 1.0e-05), trazabilidad de build 100% verificada en runtime. Reporte: `loops/r23/iter_1/LIVE_REPORT.md` | **KEEP** |
| **Iter 2** | `2ef2b72` | Exposición de `/shadow_journal` (lectura directa en disco) y booleano `volatilidadSuficiente` en `/status.feed` para auditoría inmediata de persistencia física de decisiones shadow sin extraer archivos por ADB ni depender de ring buffers de memoria. | Endpoint `/shadow_journal`, campo `volatilidadSuficiente` en `/status`, bump a versionCode 7 (v1.4-r23-iter2) | `101a183` | 116 muestras (20 min), balance intacto 38.054.735,36 COP, taps=0, 17 decisiones (todas VETO V2_RANGE). Control negativo 100% consistente (9/9 vetos correlacionados con volatilidadSuficiente=false). `/shadow_journal` verificado operativo (0 entradas). Reporte: `loops/r23/iter_2/LIVE_REPORT.md` y `VALIDATOR_FINDINGS.md` | **KEEP** |
| **Iter 3** | `fd74445` | Determinismo reproducible en `decision_id` (`UUID.nameUUIDFromBytes("$asset-$ms-$action")`) en motor live (`TradingEngine.kt`) y motor de replay (`ReplayBacktestTest.kt`) con normalización por `AssetRicMapper` y resolución a milisegundos para eliminar colisiones intra-segundo y habilitar emparejamiento exacto Live ↔ Replay. | `generateDeterministicDecisionId` en `TradingEngine.kt`, emisión de `decision_id` en `ReplayBacktestTest.kt`, bump a versionCode 8 (v1.4-r23-iter3) | `c08c842` | 116 muestras (20 min), balance intacto 38.054.735,36 COP, taps=0, 26 decisiones (todas VETO V2_RANGE). 37/37 UUID v3 válidos y únicos en /events (0 colisiones intra-segundo, eliminadas las 8 de Iter2). Control negativo 37/37 consistente. `/shadow_journal` operativo. Reporte: `loops/r23/iter_3/LIVE_REPORT.md` y `VALIDATOR_FINDINGS.md` | **KEEP** |
| **Iter 4** | `59bdeb6` | Exposición de ticks en tiempo real vía endpoint `/ticks` (buffer circular en memoria con formato JSON/CSV) para alimentar el replay offline determinista con datos idénticos de la sesión live y habilitar por primera vez la medición cuantitativa de paridad Live ↔ Replay. | Endpoint `/ticks`, buffer circular de ticks en `TradeDrawHttpBridge.kt`, bump a versionCode 9 (v1.4-r23-iter4) | `def14f8` | 118 muestras (20 min), balance intacto 38.054.735,36 COP, taps=0, 61 decisiones (todas VETO V2_RANGE). `/ticks` verificado con 2000 ticks sin redondeo, ReplayBacktestTest ejecutado exit 0, 3/3 decisiones emparejadas por decision_id v3 (divergencia espuria en comparador por falta de columna asset en replay_journal). Reportes: `loops/r23/iter_4/LIVE_REPORT.md` y `VALIDATOR_FINDINGS.md` | **KEEP** |
| **R23A-Iter 1** | `83b8f95` | Clasificación de umbral de volatilidad mínima por familia de activo (`IDX` -> `1e-11`, otros -> `1e-5`, alineado con `BinomoWebSocketClient`) + fail-closed guard `isDemoAccount == true` en `TradingEngine` y `AutoTradeAccessibilityService` para habilitar el ciclo operativo autónomo real en cuenta DEMO. | Clasificación de umbral en `TradingEngine.kt`, guard `isDemoAccount` fail-closed, columna `asset` en `ReplayBacktestTest.kt`, bump a versionCode 10 (v1.4-r23-auto-iter1) | `7a37032` | 117 muestras (20 min), modo AUTONOMOUS sostenido, isDemoAccount=true 117/117 (0 cuentas reales, fail-closed OK). Pipeline autónomo operó de extremo a extremo: 28 señales, 4 taps DEMO, 2 trades confirmados (W=1, L=1, payout 82%), saldo DEMO 38.054.735,36 -> 38.036.736,00 COP (Δ -17.999,36 COP). Trailing Profit Lock activo (+82.001 COP). Paridad de asset Live=Replay=Z-CRY/IDX resuelta. Test 10/10 PASS. Reporte: `loops/r23_autonomous/iter_1/LIVE_REPORT.md` y `VALIDATOR_FINDINGS.md` | **KEEP** |
| **R23A-Iter 2** | `7a37032` | Sincronización de escala monetaria de inversión (`investmentAmount` en COP acoplado a `observedOrderAmount` de Binomo) + corrección de paridad determinista (alineación de `market_regime` y `candle_second` en tooling de validación) para unificar el dimensionamiento de riesgo real en cuenta DEMO. | Acoplamiento de stake a escala COP en `TradingEngine`/`RiskManager`, fix de tooling en `scripts/validation/`, bump a versionCode 11 (v1.4-r23-auto-iter2) | (En diseño por AGY / asignación a Hermes) | En preparación de tarea para Hermes | PLANNING |

---

## 3. Matriz de Reservas de Archivos Activas (Exclusividad Multi-Agente)

| Agente | Rol | Archivos Reservados | Propósito | Estado |
|---|---|---|---|---|
| AGY | Orquestador / Auditor / Build / ADB | `workflows/R23_AUTOMEJORA_COORDINATION.md`, `workflows/PREREGISTRO_R23A.md`, `scripts/validation/*` | Coordinación, decisiones, build y auditoría | ACTIVO |
| HERMES | Implementador | `app/build.gradle.kts`, `app/src/main/java/com/example/tradedraw/TradingEngine.kt`, `app/src/main/java/com/example/tradedraw/AutoTradeAccessibilityService.kt`, `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`, `app/src/test/java/com/example/tradedraw/AutonomousSafetyAndAssetScaleTest.kt`, `app/src/test/java/com/example/tradedraw/ReplayBacktestTest.kt`, `workflows/rounds/r23_autonomous_iter1_hermes.md` | Implementación de clasificación de umbral y guard de seguridad DEMO | COMPLETADO |
| OPENCODE | Validador LIVE (~20 min) | `loops/r23_autonomous/iter_1/*` | Validación en hardware real (Redmi A1, 20 min en DEMO) | VALIDANDO EN VIVO |

---

## 4. Principios y Protocolo de Iteración
1. **UNA HIPÓTESIS PRINCIPAL → UNA MODIFICACIÓN CONTROLADA → UNA VALIDACIÓN LIVE.**
2. **Exposición de Trazabilidad:** Cada APK candidata debe exponer inequívocamente su identidad (`gitSha`, `versionCode`, `versionName`) en `/status` para que OpenCode verifique `código candidato = APK instalada = proceso en ejecución`.
3. **Validación Live Obligatoria:** Mínimo ~20 minutos continuos por candidato. Sin prueba en hardware real, no hay decisión.
4. **Seguridad Absoluta:** Cualquier orden real o tap despachado aborta la iteración inmediatamente con veredicto FAIL.
