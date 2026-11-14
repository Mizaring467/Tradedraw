# R23B ITERACIÓN 1 — TAREA PARA HERMES: DESBLOQUEO DE LIMITES DE SESIÓN EN SUBMODO YOLO Y EXPOSICIÓN DE SUBMODE EN /STATUS

**Destinatario:** Hermes (`w1:p6`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-15  
**Fase:** R23B (Modo YOLO en Cuenta DEMO)  
**Base SHA:** `002cc6d` (HEAD de `r10-night`)  
**APK Previa:** `versionCode = 12`, `BUILD_ID = R23-AUTO-ITER3`, SHA `bd7ef4e`  

---

## 1. CONTEXTO Y DIAGNÓSTICO DEL BASELINE

En la auditoría (`workflows/R23B_YOLO_AUDIT.md` §2.3) y en la validación live del Baseline de R23B (`loops/r23_yolo/baseline/LIVE_REPORT.md` y `VALIDATOR_FINDINGS.md`), OpenCode confirmó que de 74 señales generadas en ~11,3 minutos, **el 100% fueron vetadas**:
- **75 vetos por `RISK_LIMIT Trailing Profit Lock` (74,3%)**
- **26 vetos por `ADAPTIVE_BLOCK` (25,7%)**
- **0 taps despachados**.

### Causa Raíz
En `RiskManager.kt:465-491` y `506-508`:
1. `sessionFloor` (`Stop Loss Sesion Alcanzado`)
2. `Trailing Profit Lock` (`Trailing Profit Lock: Ganancia asegurada (+392000 COP pico)`)
3. `stopLossStreak` (`Stop Loss alcanzado (X derrotas)`)
se evalúan **antes** de la bifurcación `if (subMode == AutonomousSubMode.YOLO)`.
Dado que en una sesión previa hubo una ganancia pico y luego un retroceso menor, el `Trailing Profit Lock` mantiene al motor en bloqueo perpetuo, haciendo que la frecuencia del modo YOLO sea **inobservable**.

Además, `/status` no expone `submode`, impidiendo que la telemetría audite de forma continua el submodo activo.

---

## 2. ESPECIFICACIÓN DE REQUERIMIENTOS

### REQ 1: Desbloqueo de Límites de Sesión para YOLO (`RiskManager.kt`)
En `canExecuteTrade(subMode: AutonomousSubMode? = null, confidence: Float = 1.0f)`:
Determinar el submodo efectivo: `val effectiveSubMode = subMode ?: currentSubMode`.
- Si `effectiveSubMode == AutonomousSubMode.YOLO`:
  1. **Omitir `sessionFloor`** y **omitir `Trailing Profit Lock`** (líneas 467-490).
  2. **Omitir `stopLossStreak`** (línea 506).
  3. **MANTENER INTACTO** el suelo de equity absoluto `currentBal < absoluteEquityFloor` (20.000.000 COP) para evitar vaciar la cuenta demo.
  4. **MANTENER INTACTO** el guard de operación en curso (`hasPendingTrade`).
  5. **MANTENER INTACTA** la pausa anti-tilt tras fallo de martingala (`martingaleEnabled && currentLossStreak > maxMartingaleLevel`), que en YOLO usa `yoloLossCooldownSeconds` (35s).
  6. **MANTENER INTACTO** el cooldown YOLO tras pérdida (`getRemainingCooldown(subMode)`).
  7. **MANTENER INTACTO** el filtro de Martingala M1 selectiva (confianza $\ge 85\%$).
- Para `CONSERVATIVE` y `SNIPER`:
  `sessionFloor`, `Trailing Profit Lock` y `stopLossStreak` permanecen **100% activos y sin alteraciones**.

### REQ 2: Exposición de `submode` en `/status` (`TradeDrawHttpBridge.kt`)
En `buildStatusJson()`:
Exponer el campo:
```json
"submode": "${te.autonomousSubMode.name}",
```
inmediatamente debajo de `"mode": "$mode",`.
**Regla estricta:** Cumplir RFC 8259. CERO comentarios `//` dentro del template multilínea de string.

### REQ 3: Bump de Versión (`app/build.gradle.kts`)
- `versionCode = 13`
- `versionName = "1.4-r23-yolo-iter1"`
- `BUILD_ID = "R23-YOLO-ITER1"`

### REQ 4: Tests Unitarios y Verificación Negativa
1. En `RiskManagerTest.kt`:
   - Test: `canExecuteTrade_yoloIgnoresTrailingProfitLockAndSessionFloor()`:
     Configurar un pico de sesión y saldo por debajo de `trailingFloor`. Comprobar que con `AutonomousSubMode.YOLO` retorne `true` (permitido), mientras que con `AutonomousSubMode.CONSERVATIVE` retorne `false` con razón `Trailing Profit Lock`.
   - Test: `canExecuteTrade_yoloIgnoresStopLossStreak()`:
     Configurar `currentLossStreak >= stopLossStreak`. Comprobar que en YOLO retorne `true` (permitido si pasó el cooldown), mientras que en CONSERVATIVE retorne `false`.
   - Test: `canExecuteTrade_yoloRespectsAbsoluteEquityFloor()`:
     Comprobar que si el balance cae por debajo de `absoluteEquityFloor` (20M COP), YOLO también se detiene por seguridad.
2. En `StatusJsonStrictSyntaxTest.kt`:
   - Verificar que `/status` contenga `"submode"` y continúe siendo un JSON estrictamente válido.
3. Ejecutar la suite completa:
   `./gradlew.bat testDebugUnitTest --console=plain`
   Todos los tests (414+) deben pasar sin fallos.

---

## 3. PROTOCOLO DE ENTREGA
Hermes debe:
1. Implementar los cambios en `RiskManager.kt`, `TradeDrawHttpBridge.kt` y `app/build.gradle.kts`.
2. Añadir y pasar los tests unitarios.
3. Verificar con parser JSON externo (`python -c "import json; ..."`).
4. Commitear y reportar el SHA candidato y resumen a AGY (`w1:p1`).
