# Objetivo del Bucle: Corrección de Detección de Tendencia en el HUD

## Objetivo Principal
Corregir la anomalía en el HUD y motores de análisis (`SyntheticCandleEngine`, `VisionAnalyzer`, `TradingEngine`, `OverlayService`) donde la tendencia siempre se diagnostica o muestra como "LATERAL / RANGO", impidiendo el reconocimiento adecuado de tendencias ALCISTAS y BAJISTAS reales en el gráfico y en WebSocket Headless.

## Diagnóstico del Agente Revisor / Ideator (Causa Raíz)
1. **Umbral OLS/ATR Hiper-Restrictivo en `SyntheticCandleEngine.kt`**:
   - Para `sample.size >= 3`, el motor exige una pendiente OLS normalizada `normSlope = slope / atr >= 0.12`.
   - En activos reales (Crypto IDX, OTC), la volatilidad intrínseca y mechas hacen que el ATR de vela sea alto en relación al avance neto por barra. Un avance sostenido de 0.03 a 0.08 ATR por vela (que en 10 velas representa hasta el 80% del ATR) quedaba descartado arbitrariamente como `SIDEWAYS`.
   - El veto cruzado `if (vTrend == TrendDirection.DOWNTREND && normSlope < 0.20)` bloqueaba transiciones a alcista forzando `SIDEWAYS`.
2. **Inconsistencia en Estimación con Pocas Velas (< 3) en `SyntheticCandleEngine.kt`**:
   - `normTickSlope` dividía la pendiente por tick entre `(tickAtr / 60.0)`, mezclando unidades de tiempo con unidades de tick y exigiendo `>= 0.12`, haciendo que durante los primeros minutos o transiciones el motor se quedara congelado en `SIDEWAYS`.
   - No se empleaba el momentum directo de velas sintéticas en formación ni medias móviles de corto plazo (EMA rápida vs lenta / precio vs EMA).
3. **Fragilidad de Detección en `VisionAnalyzer.kt`**:
   - El cálculo de tendencia en `VisionAnalyzer.kt` dependía de una comparación de 2 puntos aislados (`priceNewest - priceOldest`) sujeto a ruido de mechas de la última vela, y filtros de conteo rígidos (`greenCount >= 3 && redCount <= 1`) que colapsaban ante cualquier vela de retroceso normal dentro de una tendencia.
   - Umbral fijo de `avgBodyHeight < 15.0` en `isSidewaysByCandles` que marcaba falso lateral en pantallas con escalado fino de velas.
4. **Sincronización en `OverlayService.kt` y `TradingEngine.kt`**:
   - La priorización y fallback entre `analysis.trend` y `wsTrend` dejaba como resultado por defecto `SIDEWAYS` cuando cualquiera de los dos estaba neutro o no inicializado, propagando la etiqueta al HUD, al badge de tendencia y a los filtros de trading.

## Criterios de Éxito Verificables
1. **Detección Dinámica de Tendencia Cuantitativa**:
   - `SyntheticCandleEngine` debe clasificar correctamente secuencias alcistas (`UPTREND`), bajistas (`DOWNTREND`) y consolidaciones estrechas (`SIDEWAYS`) usando un umbral balanceado (`0.04f` / EMA / acción del precio) tanto en velas sintéticas como en ventana temprana de ticks.
2. **Robustez en Visión y Velas Discretizadas**:
   - `VisionAnalyzer` debe evaluar la tendencia mediante regresión lineal ponderada o EMA de velas en lugar de diferencias frágiles de 2 extremos, soportando pullbacks normales sin degradarse a lateral.
3. **HUD y Razonamiento Desbloqueados**:
   - `OverlayService` y `TradingEngine` deben reflejar `UPTREND` (📈 Alcista), `DOWNTREND` (📉 Bajista) y `SIDEWAYS` (📊 Lateral) fielmente sin sesgo hacia lateral perpetuo.
4. **Suite de Pruebas Unitarias Aprobada**:
   - Pruebas en JUnit verificando secuencias alcistas, bajistas y laterales tanto para `SyntheticCandleEngine` como para `VisionAnalyzer`.
5. **Compilación Limpia**:
   - Gradle `assembleDebug` exitoso sin errores ni warnings críticos.

## Matriz de Herramientas del Proyecto
| Fase | Herramienta / Comando | Propósito |
| :--- | :--- | :--- |
| **Build** | `.\gradlew assembleDebug --no-daemon --no-configuration-cache` | Compilación completa de la aplicación Android |
| **Test** | `.\gradlew testDebugUnitTest --no-daemon --no-configuration-cache` | Ejecución de pruebas unitarias locales en JVM |
| **Lint** | Inspección sintáctica y de imports en Kotlin 1.9.24 / AGP 8.5.2 | Validación estática de código |
| **Smoke** | Validación de ejecución de métodos con casos de prueba sintéticos | Verificación empírica de algoritmos de tendencia |

## Restricciones y Supuestos
- Idioma estrictamente en español en reportes y comentarios.
- Mantener compatibilidad con modo Headless WebSocket y con visión de pantalla.
- Filosofía Ponytail: Solución directa, matemática y limpia, sin sobreingeniería.
