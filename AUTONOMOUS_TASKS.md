# Plan de Iteración Autónoma para TradeDraw (AGY Loop)

Este documento define la hoja de ruta para la ejecución autónoma en bucle (`/goal`) sobre el proyecto TradeDraw en el POCO X6 Pro conectado por ADB inalámbrico (`192.168.1.119:5555`).

---

## 🎯 Objetivo General
Eliminar la fragilidad en la ejecución de órdenes, automatizar la auditoría de resultados W/L mediante balance real, y robustecer la visión computacional en orientación horizontal y vertical.

---

## 📋 Tareas Priorizadas para el Bucle Autónomo

### Tarea 1: Detección Robusta de Resultado (Ganancia/Pérdida W/L) por Saldo
- **Problema actual**: El resultado de la operación se calcula comparando `entryY` vs `exitY` en píxeles. Si el usuario hace zoom o el gráfico se desplaza, la comparación de píxeles falla.
- **Solución requerida**:
  - Implementar en `VisionAnalyzer.kt` o `RiskManager.kt` la lectura del balance en la esquina superior izquierda (ej. `Col$50,112,911.36`).
  - Al abrir un trade, registrar el saldo base.
  - Al expirar la operación (60s), re-escanear el saldo: si aumentó -> `recordTradeWin()`; si disminuyó -> `recordTradeLoss()`.
- **Verificación**: Simular cambios de saldo y comprobar que el HUD actualiza `W` y `L` sin tocar nada manual.

---

### Tarea 2: Calibración Dinámica de Botones SUBE / BAJA (Multirresolución)
- **Problema actual**: En modo Horizontal (2712x1220) o Vertical (1220x2712), las posiciones de los botones SUBE y BAJA pueden variar según la barra de navegación o la escala de la UI de Binomo.
- **Solución requerida**:
  - En `VisionAnalyzer.findBrokerButtonCoordinates()`:
    - Escanear el cuadrante inferior derecho en Horizontal y la franja inferior en Vertical.
    - Localizar el centroide del botón verde `#00E676` / `#22C55E` para SUBE y el botón rojo `#FF5252` / `#EF4444` para BAJA.
    - Si la visión no encuentra el botón con certeza >90%, recurrir a `CalibrationManager`.
- **Verificación**: Enviar evento de prueba por broadcast `TEST` y verificar que el toque cae con precisión milimétrica en el centro del botón.

---

### Tarea 3: Filtro Anti-Mercado Lateral (Sideways & Dojis)
- **Problema actual**: En horarios de bajo volumen o activos OTC (como CHF/JPY o Crypto IDX nocturno), aparecen micro-velas y dojis seguidos que generan señales falsas.
- **Solución requerida**:
  - En `VisionAnalyzer.kt`:
    - Medir la altura promedio del cuerpo de las últimas 10 velas. Si la altura promedio es menor a 15 píxeles o más del 40% de las velas son dojis, marcar `isMarketSideways = true`.
    - En `TradingEngine.kt`: Si `isMarketSideways == true`, suspender temporalmente las operaciones y mostrar en el HUD: `⚠️ Mercado Lateral / Dojis detectados: Esperando volatilidad`.
- **Verificación**: Comprobar en logcat que no se abran órdenes durante consolidaciones estrechas.

---

### Tarea 4: Resiliencia del Servicio de Accesibilidad (`AutoTradeAccessibilityService`)
- **Problema actual**: Cuando la app se recompila o actualiza por ADB, Android 14 puede desvincular el servicio colocándolo en estado `crashed`.
- **Solución requerida**:
  - En `OverlayService.kt`:
    - Añadir un monitor periódico (cada 10s) que verifique `AutoTradeAccessibilityService.instance != null`.
    - Si es nulo, ejecutar internamente el comando de re-enlace seguro vía shell o emitir una notificación emergente visible con botón directo a los Ajustes de Accesibilidad.
- **Verificación**: Forzar un reinicio del proceso y verificar que se recupera automáticamente.

---

### Tarea 5: Estabilización del Buffer de Captura en Rotación (`ScreenCaptureManager`)
- **Problema actual**: Al girar el teléfono físicamente, el `VirtualDisplay` recrea el buffer y puede arrojar 1 o 2 excepciones de tamaño antes de sincronizarse.
- **Solución requerida**:
  - En `ScreenCaptureManager.kt`:
    - Proteger la lectura del `ImageReader` con mutex o semáforo durante `refreshVirtualDisplay()`.
    - Descartar frames con dimensiones discrepantes antes de enviarlos al `VisionAnalyzer`.
- **Verificación**: Girar el teléfono 3 veces consecutivas entre vertical y horizontal sin caídas ni congelamiento en el HUD.

---

## 🔄 Protocolo de Cada Iteración (Criterios de Parada)
1. **Edición**: Modificar el archivo objetivo aplicando filosofía minimalista (Ponytail).
2. **Compilación**: Ejecutar `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug --quiet`. Debe compilar con código de salida 0.
3. **Despliegue**: Instalar vía `adb -s 192.168.1.119:5555 push app\build\outputs\apk\debug\app-debug.apk /data/local/tmp/app-debug.apk; adb -s 192.168.1.119:5555 shell pm install -r -t -d /data/local/tmp/app-debug.apk`.
4. **Validación en Vivo**: Capturar pantalla por ADB (`screencap`) y verificar que el HUD y el motor responden fielmente.
