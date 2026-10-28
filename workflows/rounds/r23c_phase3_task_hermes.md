# R23C — FASE 3: ESPECIFICACIÓN TÉCNICA DE REFINAMIENTO DE YOLO CON EVIDENCIA

**Destinatario:** HERMES (`w1:p6`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-16  
**Fase:** R23C — Fase 3 (Refinamiento Guiado por Evidencia Cuantitativa)  
**Objetivo:** Eliminar la asimetría direccional observada en la validación empírica ($55.56\%\text{ BUY}$ vs $42.86\%\text{ SELL}$, con SELL representando el 60.9% del volumen y el 100% de la pérdida neta), endureciendo selectivamente los filtros de confluencia y mecha para ventas (SELL) en submodo `YOLO`, preservando intactos los umbrales rentables de compra (BUY).

---

## 1. EVIDENCIA EMPÍRICA DISPONIBLE (Fase 1: 6 Ventanas, 23 Trades)
- **Total Trades:** $N=23$ | $11\text{ W} / 12\text{ L}$ ($WR = 47.83\%$).
- **BUY:** 9 trades | 5 W / 4 L | **$WR = 55.56\%$** (Rentable frente al break-even de $54.95\%$).
- **SELL:** 14 trades | 6 W / 8 L | **$WR = 42.86\%$** (Sub-óptimo, arrastra el portafolio con $-250\text{k COP}$).
- **Conclusión de Datos:** El mercado en `Z-CRY/IDX` castiga severamente las ventas con confirmación laxa. Es necesario elevar el estándar de exigencia cuantitativa exclusivamente para `SELL`.

---

## 2. REQUERIMIENTOS DE IMPLEMENTACIÓN (FILOSOFÍA PONYTAIL)

### REQ 1: Endurecimiento de Confluencia para SELL en `SyntheticCandleEngine.kt`
En `dispatchSniperSignal`:
```kotlin
val isYolo = (subMode == AutonomousSubMode.YOLO)
// R23C Fase 3: BUY mantiene 75 pts (rentable 55.6% WR); SELL sube a 80 pts para filtrar falsos giros bajistas
val requiredScore = if (isYolo) {
    if (action == TradeAction.SELL) 80 else 75
} else 80
```

### REQ 2: Endurecimiento de Mecha Contra-Tendencia para SELL en `SyntheticCandleEngine.kt`
En `isTrendDirectionAllowed`:
```kotlin
val isYolo = (subMode == AutonomousSubMode.YOLO)
// R23C Fase 3: exigir mecha de rechazo superior de al menos 45% en ventas contra UPTREND (vs 30% anterior)
val minWick = if (isYolo) {
    if (action == TradeAction.SELL) 0.45f else 0.30f
} else 0.50f
```

### REQ 3: Filtro de Mecha de Rechazo en `MT_REJECTION`
En `evaluateSniperOpportunity` (bloque MT_REJECTION para PUT / SELL):
Exigir `sellWickLimit = if (isYolo) 0.40f else 0.45f` (en lugar de `0.30f`). Para BUY (CALL), mantener `0.30f`.

### REQ 4: Bump de Versión en `app/build.gradle.kts`
- `versionCode = 15`
- `versionName = "1.5-r23c-phase3"`
- `buildConfigField("String", "BUILD_ID", "\"R23C-PHASE3\"")`

### REQ 5: Tests Unitarios y Verificación
- Añadir tests unitarios que verifiquen:
  - En `YOLO`: `BUY` requiere 75 pts de confluencia; `SELL` requiere 80 pts.
  - En `YOLO`: `SELL` en `UPTREND` requiere mecha de rechazo $\ge 45\%$.
- Ejecutar `./gradlew.bat testDebugUnitTest` y confirmar 0 fallos.

---

## 3. COMPILACIÓN Y DESPLIEGUE EN DISPOSITIVO REAL
1. Compilar APK debug: `./gradlew.bat assembleDebug`.
2. Instalar en Xiaomi Redmi A1 vía ADB WiFi (`[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`).
3. Reestablecer submodo `YOLO`: `curl.exe -s "http://127.0.0.1:8080/submode?name=YOLO"`.
4. Verificar salud del servicio:
   `curl.exe -s http://127.0.0.1:8080/status`
   Confirmar: `versionCode: 15`, `buildId: "R23C-PHASE3"`, `submode: "YOLO"`, `isDemoAccount: true`.

---

## 4. ENTREGABLE REQUERIDO
Generar reporte en: `workflows/rounds/r23c_phase3_hermes.md` documentando:
- Diff de código y racionalidad técnica.
- Resultado de tests unitarios (`./gradlew.bat testDebugUnitTest`).
- SHA generado, verificación de build e instalación exitosa en el teléfono.
- Notificación a AGY en `w1:p1`.
