# Objetivo Técnico: Modo de Estrategia en Automático con Detección de Régimen de Mercado (Regime-Aware Auto Strategy)

**Fecha:** 2026-09-19  
**Proyecto:** TradeDraw (Android Native / POCO X6 Pro `5PPFAACU6H7XHEY9`)  
**Rama:** `main`  
**Objetivo Principal:**  
Implementar un motor de selección y detección de régimen de mercado en 2 etapas para el modo automático (`AUTO_ADAPTIVE`) de TradeDraw, evitando operaciones erráticas o en cada señal marginal. El motor debe clasificar primero el contexto estructural del gráfico (Rango S/R, Tendencia Fuerte, Rompimiento/Retest o Ruido/Standby) y habilitar únicamente la estrategia especializada correspondiente bajo las reglas cuantitativas de `master_traders_skill`.

---

## Criterios de Éxito Verificables
1. **Clasificador de Régimen Dedicado (`MarketRegimeClassifier.kt`)**:
   - `RANGING_CHANNEL`: S/R respetados, distancia $\ge 25\text{ px}$, $38.2 \le \text{CHOP} \le 61.8$. Estrategias autorizadas: `MT_REJECTION_WICK` y Sobreextensión RSI en S/R.
   - `STRONG_TREND`: Estructura unívoca de máximos/mínimos, momentum claro. Estrategias autorizadas: `MT_PULLBACK_SNIPER` y `MT_ENGULFING_SR` exclusivamente a favor de la tendencia.
   - `BREAKOUT_RETEST`: Ruptura confirmada >50% cuerpo fuera y retesteo de nivel en :01s-:05s. Estrategia autorizada: `MT_CHOQUE_RETEST`.
   - `CHOPPY_NOISE`: CHOP > 61.8 o DojiRatio $\ge 0.35$ o compresión lateral estrecha (< 15 px). **Standby absoluto: Cero órdenes disparadas**.
2. **Refactorización de `AUTO_ADAPTIVE` en `TradingEngine.kt`**:
   - Eliminar el `when` plano desordenado.
   - Ejecutar la toma de decisiones en 2 fases: 1) Clasificación de Régimen, 2) Disparo de la estrategia especializada correspondiente con confluencia $\ge 85$ pts y timing sniper :58s-:03s.
3. **Visibilidad en HUD (`OverlayService.kt` / `TradeHUDView`)**:
   - Mostrar el régimen de mercado detectado en vivo (ej. `[Rég: RANGO S/R]`, `[Rég: TENDENCIA]`, `[Rég: STANDBY/RUIDO]`).
4. **Validación y Suite de Pruebas**:
   - Suite de pruebas unitarias (`MarketRegimeClassifierTest.kt`) con 100% de éxito cubriendo los 4 regímenes y los bloqueos por ruido.
   - Compilación exitosa del APK debug (`./gradlew assembleDebug`).
   - Despliegue e instalación física en el dispositivo POCO X6 Pro vía ADB (`5PPFAACU6H7XHEY9`).
   - Smoke test en runtime con captura de pantalla confirmando el HUD con régimen activo en vivo.

---

## Matriz de Herramientas del Proyecto
```json
{
  "stack": "Android / Kotlin 1.9.24 / Gradle 9.5 / ADB",
  "build_command": ".\\gradlew.bat assembleDebug --no-daemon --no-configuration-cache",
  "test_command": ".\\gradlew.bat testDebugUnitTest --no-daemon",
  "install_command": "adb -s 5PPFAACU6H7XHEY9 install -r app/build/outputs/apk/debug/app-debug.apk",
  "device_serial": "5PPFAACU6H7XHEY9",
  "recommended_skills": ["master_traders_skill", "android-verify-checklist", "project-verify-protocol"],
  "recommended_mcps": ["android-vision", "artemis"]
}
```
