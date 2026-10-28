# AUDITORÍA INDEPENDIENTE DE PRE-VUELO R23H (TRACK A)
**Fecha:** 2026-10-18  
**Auditor Independiente:** OPENCODE (`w1:pB`)  
**Orquestador:** AGY (`w1:p1`)  
**Documentos Base:** `workflows/rounds/r23h_track_a_preflight_opencode.md` y `workflows/R23H_REVALIDATION_MANIFEST.md`  
**Estado:** APROBADO (GO / LISTO PARA LANZAMIENTO)

---

## 1. RESUMEN EJECUTIVO

Se ejecutó la verificación independiente de pre-vuelo para la revalidación formal de la ventana `window_04_clean` de la ronda R23H. No se realizaron modificaciones de código en el repositorio. Todas las condiciones de infraestructura, integridad de datos, políticas de arnés y conectividad física del dispositivo fueron auditadas y validadas con evidencia empírica directa.

| Ítem Auditado | Criterio Requerido | Estado | Evidencia / Observación |
| :--- | :--- | :--- | :--- |
| **Manifiesto R23H** | Coherencia de parámetros y build | **CONFORME** | Manifiesto validado en `workflows/R23H_REVALIDATION_MANIFEST.md`. |
| **Directorio de Ventana** | Cero artefactos residuales en `window_04_clean` | **CONFORME** | Directorio no existe previamente; entorno limpio 100%. |
| **Poda de Ticks Pre-Ventana** | Filtrado `ts >= window_start_ts_ms` sin efectos colaterales | **CONFORME** | Poda implementada en volcado a `ticks_live.csv`; buffer incremental en memoria intacto para contrafactuales. |
| **Arnés Replay Submodo YOLO** | Respeto a `BACKTEST_SUBMODE=YOLO` y paridad | **CONFORME** | `submodoDeEntorno()` resuelve `AutonomousSubMode.YOLO` (cooldown 8s); paridad con `calcularPoderesDeMercado`. |
| **Conectividad ADB & Dispositivo** | Redmi A1 conectado, batería >= 20%, demo safe | **CONFORME** | Serial `UGU8KFUKM7E6CE8D` conectado; batería al 57%; paquete versión `1.9-r23h-exhaustion` (code 19). |

---

## 2. HALLAZGOS DETALLADOS POR TAREA

### Tarea 1: Inspección del Manifiesto (`R23H_REVALIDATION_MANIFEST.md`)
- **Rama Git auditada:** `r10-night`, commit base `aac1df3`.
- **Identificación de Build:**
  - `versionCode`: 19
  - `versionName`: `1.9-r23h-exhaustion`
  - `BUILD_ID`: `R23H-BUY-EXHAUSTION`
- **Parámetros Operativos Verificados:**
  - Broker / Cuenta: DEMO obligatoria (`isDemoAccount == true`, fail-closed).
  - Activo: Nominal `Crypto IDX` / RIC feed `Z-CRY/IDX`.
  - Modo: `AUTONOMOUS` | Submodo: `YOLO` (Cooldown post-resolución: 8,000 ms, ventana sniper :01–:05).
  - Estrategia: `AUTO_ADAPTIVE` (11 patrones Master Traders, umbral de confluencia 75/80 pts).
  - Veto R23H: `VETO_BUY_EXHAUSTION_CLIMAX` activo (veto de BUY en UPTREND con `callPower >= 70%`).
  - Filtro adaptativo: `ADAPTIVE_BLOCK` activo (umbral de similitud `0.99f`).
  - Stake: 100,000 COP DEMO (1.0% Soros) / Payout nominal 82%.
  - Ventana: `window_04_clean` (20.0 minutos nominales + 75s settlement grace).
  - Comparador Replay: `temporal_tolerance_ms = 6000` ms, modo `--mode trades`.

### Tarea 2: Estado del Directorio `loops/r23h_validation/window_04_clean/`
- **Inspección de Filesystem:**
  - Se inspeccionó el árbol de directorios bajo `loops/r23h_validation/`.
  - Contenido existente: `window_01/`, `window_02/`, `window_03/`.
  - El directorio `loops/r23h_validation/window_04_clean/` **NO existe** actualmente en el sistema de archivos.
- **Veredicto:** Cero estado previo o archivos residuales. La ventana iniciará en un espacio de trabajo 100% limpio y homogéneo.

### Tarea 3: Verificación de Poda de Ticks en `live_monitor_r23_autonomous.py`
- **Localización del Código:** Líneas 951–979 en `scripts/validation/live_monitor_r23_autonomous.py`.
- **Implementación Analizada:**
  ```python
  window_start_ts_ms = int(start_time * 1000.0)
  ticks_in_window = {
      ts: linea
      for ts, linea in accumulated_ticks.items()
      if ts >= window_start_ts_ms
  }
  pruned_ticks = len(accumulated_ticks) - len(ticks_in_window)
  ```
- **Auditoría de Efectos Colaterales:**
  1. *Aislamiento temporal:* La poda se ejecuta únicamente en el volcado a disco (`out_ticks_csv`), descartando la cola residual del buffer circular del APK que corresponde a ticks previos al inicio de la ventana (`ts < window_start_ts_ms`).
  2. *Integridad de contrafactuales:* El diccionario en memoria `accumulated_ticks` no se altera durante el bucle en vivo, permitiendo que `find_nearest_tick_price` localice con precisión el tick de entrada y salida para decisiones cercanas al inicio sin provocar excepciones `KeyError` ni lecturas fuera de rango.
  3. *Respaldo en caso de captura vacía:* Se mantiene el bloque condicional `else` que consulta directamente `/ticks?max=2000&format=csv` si la acumulación incremental no capturó datos, evitando un crash total por falta de archivo.
  4. *Garantía para el Replay:* Al no incluir ticks pre-ventana en `ticks_live.csv`, el motor de Replay offline no arranca con velas o tendencias desfasadas respecto a lo que el motor vivo procesó.
- **Veredicto:** Poda limpia, robusta y libre de efectos colaterales.

### Tarea 4: Arnés de Replay (`BACKTEST_SUBMODE=YOLO`) y Paridad
- **Análisis de `ReplayBacktestTest.kt`:**
  - Líneas 460–467: `submodoDeEntorno()` lee la variable de entorno `BACKTEST_SUBMODE`:
    ```kotlin
    val raw = System.getenv("BACKTEST_SUBMODE") ?: System.getProperty("BACKTEST_SUBMODE") ?: "CONSERVATIVE"
    return try {
        AutonomousSubMode.valueOf(raw.trim().uppercase())
    } catch (_: Exception) {
        AutonomousSubMode.CONSERVATIVE
    }
    ```
  - Línea 161: `val cooldownMs = if (subMode == AutonomousSubMode.YOLO) YOLO_COOLDOWN_MS else CONSERVATIVE_COOLDOWN_MS` donde `YOLO_COOLDOWN_MS = 8_000L` (8 segundos).
  - Líneas 193–213: Replicación de la política de veto R23H mediante `calcularPoderesDeMercado(...)` y `motivoVetoPorAgotamientoDeCompra(...)`. Esto garantiza que el replay vete compras en clímax bajo las mismas condiciones exactas del runtime real.
- **Análisis de Invocación en `live_monitor_r23_autonomous.py`:**
  - Línea 599: `active_submode` se actualiza dinámicamente desde el endpoint `/status`:
    ```python
    active_submode = status.get("submode", active_submode) or active_submode
    ```
  - Líneas 1018–1021: El monitor exporta explícitamente `env["BACKTEST_SUBMODE"] = active_submode` al invocar `./gradlew testDebugUnitTest --tests *ReplayBacktestTest*`.
  - Al estar el bot configurado en `YOLO`, `active_submode` adquiere el valor `"YOLO"`, previniendo cualquier desviación silenciosa hacia `CONSERVATIVE`.
- **Análisis de Tolerancia en `compare_live_replay.py`:**
  - Línea 320 y 1040: `temporal_tolerance_ms` ajustado a 6000 ms por defecto para absorber la latencia de confirmación física en Android Go.
  - Invocación con `--mode trades` habilitada cuando existe `replay_journal.csv`.
- **Veredicto:** Paridad de arnés 100% alineada con el manifiesto.

### Tarea 5: Conectividad ADB y Salvaguardas DEMO
- **Verificación de Dispositivo Físico:**
  - Dispositivo detectado vía ADB: `UGU8KFUKM7E6CE8D` (`device`).
  - Modelo: Xiaomi Redmi A1 (`220733SL`, Android 12 Go).
  - Nivel de batería: **57%** (umbral de seguridad `>= 20%` ampliamente superado; cargando por USB).
  - Temperatura de batería: 32.1°C (normal).
- **Verificación de Build Instalada en el Dispositivo:**
  - Comando: `adb shell dumpsys package com.example.tradedraw | grep -iE 'versionCode|versionName'`
  - Salida:
    - `versionCode=19`
    - `versionName=1.9-r23h-exhaustion`
  - Coincidencia exacta byte a byte con el manifiesto.
- **Estado de Procesos y Servicios en el Dispositivo:**
  - Proceso `com.example.tradedraw` activo (PID 2340).
  - Servicio de accesibilidad `AutoTradeAccessibilityService` activo y enlazado con el sistema (`BIND_ACCESSIBILITY_SERVICE`).
  - Pantalla encendida (`mWakefulness=Awake`).
- **Salvaguardas de Cuenta DEMO:**
  - En `TradingEngine.kt`, la validación de cuenta DEMO es fail-closed (`isDemoAccount == true`); ante cuenta real, cualquier despacho táctil es vetado de forma inmediata e irrevocable.
  - En `live_monitor_r23_autonomous.py`, las métricas de pre-vuelo exigen `real_account_seen == 0` y `demo_verified_count > 0` para validar la ventana.
  - *Nota operativa para el Orquestador:* Asegurar que la aplicación Binomo (`com.marketly.trading`) se encuentre en primer plano en cuenta DEMO y el servicio de superposición (`OverlayService`) esté activo para habilitar el listener HTTP en el puerto local 8080 previo al inicio del cronómetro de 20 minutos.

---

## 3. CONCLUSIÓN Y RECOMENDACIÓN DE PRE-VUELO

Todos los 5 puntos de la verificación independiente han resultado **SATISFACTORIOS**.
No existen discrepancias de configuración, no hay código modificado indebidamente, el dataset previo está limpio y el arnés de Replay está blindado contra divergencias de submodo y de políticas de veto.

**Veredicto Oficial:** **GO / APROBADO PARA LANZAMIENTO DE LA VENTANA `window_04_clean`**.
