# R22 — Tarea de Auditoría y Monitorización Live para HERMES

**Objetivo:** Monitoreo independiente continuo del runtime real de TradeDraw en el Redmi A1 (`UGU8KFUKM7E6CE8D`) durante la sesión en `SHADOW MODE`, verificación de guards, recolección de eventos y redacción de `workflows/rounds/R22_LIVE_VALIDATION_HERMES.md`.

---

## 1. Parámetros de la Sesión
- **Dispositivo:** Redmi A1 (`UGU8KFUKM7E6CE8D`)
- **APK Instalada:** `versionCode = 5`, `versionName = "1.4-r22"`, commit `54b22a2` (compilada e instalada por AGY vía ADB).
- **Endpoint Bridge:** `http://127.0.0.1:8080/status`, `/events`, `/mode_log`
- **Modo de Operación:** `SHADOW` (CERO taps, CERO órdenes enviadas, CERO riesgo de saldo).
- **Duración de la Ventana:** Mínimo 15-20 minutos continuos (o hasta recolectar una muestra representativa de decisiones y ticks).

---

## 2. Puntos Críticos de Observación
1. **Verificación de Instalación Real:**
   - Confirmar en `/status`: `"versionCode": 5`, `"versionName": "1.4-r22"`, objeto `"health"`.
   - Confirmar vía ADB `dumpsys package com.example.tradedraw`.
2. **Pipeline y Decision ID:**
   - Monitorear `/events` y `/status`.
   - Verificar que cada decisión contenga un `decision_id` monotónico (UUID).
   - Verificar que en `SHADOW` **NO** se emita `trade_id` ni se incremente `tapsDespachados`.
   - Verificar que se emitan eventos `SHADOW_DECISION`.
3. **Guards y Salud:**
   - Observar transiciones de `health.state` (`READY`, `DEGRADED`, `BLOCKED`).
   - Si ocurre desajuste pantalla↔feed, verificar emisión de `vetoActivoDesajustado` con 0 taps.
   - Si el feed se queda sin ticks, verificar reacción del guard.
4. **Preservación Incondicional del Balance:**
   - Saldo inicial: **38.054.735,36 COP**.
   - Confirmar que ningún evento de TradeDraw altera el saldo ni contadores W/L.
5. **Comparación Live ↔ Replay:**
   - Extraer las decisiones observadas durante la sesión.
   - Ejecutar `python scripts/validation/compare_live_replay.py` contrastando las decisiones en vivo contra el replay determinista.
   - Reportar exact matches, divergencias y `first_divergence`.

---

## 3. Entregables Obligatorios
1. Documento: `workflows/rounds/R22_LIVE_VALIDATION_HERMES.md` (con clasificación epistémica `FACT`, `EVIDENCE`, `HYPOTHESIS`, `MISSING PROOF`).
2. Notificación final a AGY:
   `herdr agent prompt agy "<resumen + workflows/rounds/R22_LIVE_VALIDATION_HERMES.md + hallazgos>"`
