# R22 — Coordinación y Reservas de Archivos Multi-Agente (Herdr)

**Fecha:** 2026-10-15  
**Workspace Herdr:** `w1`  
**Orquestador Principal:** `agy` (`w1:p1`)  
**Monitor / Auditor Independiente:** `hermes` (`w1:p6`)  
**Dispositivo Físico:** Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**SHA Base:** `e33f8cb`

---

## 1. Reglas Absolutas y Mutex de Archivos

1. **PROHIBIDO** que dos agentes editen simultáneamente el mismo archivo.
2. AGY es responsable de:
   - Código Kotlin de producción, configuración Gradle y tests unitarios.
   - Compilación e instalación de APK en el Redmi A1 vía ADB.
   - Verificación de runtime y control de modo (arranque en `DISABLED` -> `SHADOW`).
   - Cero uso de `AUTONOMOUS` durante R22.
3. HERMES es responsable de:
   - Monitorización independiente black-box y continua de la sesión LIVE.
   - Auditoría de `/status`, `/events`, `/mode_log`, journal, `shadow_journal`, logs, balance, vetos y latencia.
   - Ejecución del comparador `compare_live_replay.py` sobre datos de la sesión.
   - Redacción y entrega del reporte final `workflows/rounds/R22_LIVE_VALIDATION_HERMES.md`.
   - Comunicación final a AGY vía `herdr agent prompt agy`.
   - NO editar archivos reservados por AGY.

---

## 2. Tabla de Reservas y Asignaciones

| Frente | Responsable | Archivos reservados | Estado | SHA base |
|---|---|---|---|---|
| **Build, Versioning & ADB Install** | `agy` | `app/build.gradle.kts`, `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt` | **EN CURSO** | `e33f8cb` |
| **Runtime Verification & Bridge Control** | `agy` | Comandos ADB, control de modo `/mode?name=SHADOW` | **EN CURSO** | `e33f8cb` |
| **Live Monitoring & Telemetry Audit** | `hermes` | `workflows/rounds/R22_LIVE_VALIDATION_HERMES.md`, `scripts/validation/*` | **ASIGNADO** | `e33f8cb` |
| **Live ↔ Replay Comparison** | `hermes` | `workflows/rounds/r22_replay_*` | **ASIGNADO** | `e33f8cb` |

---

## 3. Protocolo de Ejecución R22

1. **Fase 1 (AGY):**
   - Bump de versión (`versionCode = 5`, `versionName = "1.4-r22"`, `buildConfig = true`).
   - Exposición de versión en `/status` para verificación irrefutable en runtime.
   - Compilación de APK limpia (`./gradlew assembleDebug`).
   - Verificación ADB del Redmi A1 (`UGU8KFUKM7E6CE8D`).
   - Instalación forzada vía `adb -s UGU8KFUKM7E6CE8D install -r app/build/outputs/apk/debug/app-debug.apk`.
   - Verificación de `dumpsys package`, `versionCode=5`, `lastUpdateTime` actual y reinicio del proceso.
2. **Fase 2 (AGY & Hermes):**
   - Arranque inicial en `DISABLED`.
   - Verificación de invariantes: `tapsDespachados=0`, `hasPendingTrade=false`, `orderState=NINGUNA`, `balance` intacto.
   - Activación de `AutoTradeMode.SHADOW` vía `/mode?name=SHADOW`.
   - Lanzamiento de monitorización continua por Hermes en `w1:p6`.
3. **Fase 3 (Hermes):**
   - Registro de telemetría de toda la sesión live.
   - Validación de guards (AssetMismatch, FeedStale, HealthSupervisor).
   - Verificación de ausencia de taps y preservación de balance.
   - Comparación Live ↔ Replay sobre decisiones reales.
   - Entrega de reporte y prompt a AGY.
