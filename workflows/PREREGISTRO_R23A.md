# PREREGISTRO R23A — Enmienda de Escala de Volatilidad para Familia IDX, Modo Autónomo en Cuenta DEMO y Protocolo de Blindaje Fail-Closed

**Fecha de fijación:** 2026-10-15 (formalizado durante R23A Iteración 1, previo a la consolidación de la validación live).  
**Estado:** Umbrales congelados para la fase autónoma. No se modifican tras observar resultados; cualquier alteración posterior exige un nuevo preregistro.  
**Alcance:** Enmienda formal a `workflows/PREREGISTRO_R12.md §2`. Aplica al pipeline de ejecución autónoma en cuenta DEMO de la serie R23A. NO modifica `workflows/PREREGISTRO_R10.md` ni altera el umbral `1e-5` para pares no sintéticos (ETHUSD-OTC, BTCUSD-OTC, SOLUSD-OTC).  
**Firmante:** AGY (Orquestador R23 / TradeDraw).

---

## 1. Justificación Técnica de la Enmienda a Veto V2 (Volatilidad de Feed)

En `PREREGISTRO_R12.md §2`, el veto V2 congeló:
```
RANGO_RELATIVO_MINIMO = 1e-5 (0,001 %)
```
bajo la premisa: *"No se aplica a un activo concreto: es un veto por condición de feed, no por nombre"*.

### Evidencia Empírica Observada en Hardware Real (Redmi A1 — R23 Iteraciones 1 a 4)
Durante las cuatro iteraciones de infraestructura de R23 con Binomo en primer plano sobre `Crypto IDX` (`Z-CRY/IDX`), se registraron mediciones continuas del feed WebSocket:
1. **Paso de tick:** En `Z-CRY/IDX`, el paso elemental de tick es del orden de `~1,6e-6` sobre un precio base de `~641,86`.
2. **Rango relativo en ventana de 16 ticks:** Oscila entre `1,4e-10` y `6,6e-10` (mediana: `~2,9e-10`).
3. **Rango relativo global (2000 ticks de Iteración 4):** `1,885e-09`.
4. **Efecto del umbral 1e-5:** El umbral universal de R12 se encuentra **cuatro a cinco órdenes de magnitud por encima** del rango observable de `Z-CRY/IDX`. En consecuencia, vetó el 100% de las 61 evaluaciones de la Iteración 4 y el 100% de las evaluaciones de las iteraciones 1 a 3.

### Enmienda de Escala por Familia de Activo
La premisa de un único umbral escalar universal colapsa cuando se pasa de pares con alta dispersión relativa (`ETHUSD-OTC` con rango relativo `~4,16e-2`) a índices sintéticos computados algorítmicamente (`IDX` con paso en `1e-6`).

Se enmienda formalmente la regla de V2 introduciendo la función pura:
```kotlin
fun rangoRelativoMinimoParaActivo(asset: String): Double {
    return if (asset.contains("IDX", ignoreCase = true)) {
        RANGO_RELATIVO_MINIMO_IDX // 1e-11
    } else {
        RANGO_RELATIVO_MINIMO // 1e-5
    }
}
```

**Consistencia Interna:** Esta diferenciación no es una invención ad-hoc, sino una alineación arquitectónica con el criterio ya presente y validado en `BinomoWebSocketClient.isPriceFrozen` (`1e-11` para índices sintéticos IDX, frente a `MarketTickFilters.FROZEN_RANGE_RATIO = 1e-6` para el resto).

---

## 2. Declaración Metodológica de Carácter EXPLORATORIO OPERATIVO

> [!IMPORTANT]
> **DELIMITACIÓN EPISTEMOLÓGICA FORMAL:**
> La reducción del umbral de volatilidad a `1e-11` para `Z-CRY/IDX` **NO CONSTITUYE NI SE INTERPRETA COMO PRUEBA DE EDGE ESTADÍSTICO (ALPHA) O RENTABILIDAD CUANTITATIVA**.

1. **Naturaleza del Activo:** Como demostró el análisis de 2000 ticks de Hermes, `Z-CRY/IDX` presenta un 89% de precios repetidos en ventanas de 16 ticks. Desbloquear V2 permite que el motor evalúe y despache órdenes en DEMO, pero no altera la microestructura de tick ni garantiza un expectancy positivo.
2. **Propósito Único de R23A:** Validar de extremo a extremo la **viabilidad operativa del pipeline autónomo y la efectividad de sus protecciones fail-closed en cuenta DEMO**:
   - `WebSocket feed` → `Cálculo de indicadores` → `Filtros y Guards` → `Generación de Señal` → `Decisión determinista v3` → `Despacho de tap en coordenadas DEMO` → `Confirmación de orden en UI` → `Resolución de trade a 60s` → `Registro en TradeJournalLogger` → `Replay offline emparejado`.
3. **Criterio de Validación:** Cualquier análisis de rentabilidad o win rate en R23A se clasifica estrictamente como **prueba de integración operativa en entorno simulado (DEMO)**. No se extrapolará ningún resultado como garantía para trading en cuenta real sin un estudio específico previo sobre el activo.

---

## 3. Protocolo de Blindaje y Seguridad Fail-Closed (Modo DEMO Exclusivo)

La operación autónoma está estrictamente restringida a cuentas DEMO. Se congelan los siguientes tres niveles de protección:

### Nivel 1: Guard en el Motor de Trading (`TradingEngine.kt`)
- Función pura de verificación:
  ```kotlin
  fun puedeOperarAutonomoEnDemo(isDemoAccount: Boolean): Boolean = isDemoAccount
  ```
- Si `isDemoAccount == false`:
  - Toda orden se veta de forma inmediata con razón `SEGURIDAD_NO_DEMO`.
  - El motor se auto-desactiva de inmediato transicionando a `EngineMode.DISABLED`.
  - Queda terminantemente prohibido cualquier reintento automático.

### Nivel 2: Triple Bloqueo en Accesibilidad (`AutoTradeAccessibilityService.kt`)
- La función pura `puedeDespacharTap(isDemoAccount: Boolean)` gobierna las TRES puertas de entrada a la inyección de eventos táctiles:
  1. `performClick(target: TapTarget)`: Bloqueo directo fail-closed.
  2. `performClickAt(x: Int, y: Int)`: Bloqueo directo fail-closed (cierra el endpoint `/trade` y botones UI).
  3. `executeRootTap(x: Int, y: Int)`: Bloqueo directo fail-closed (cierra llamadas fallback de bajo nivel `input tap`).
- Si `isDemoAccount == false`, ninguna coordenada ni selector de tap puede alcanzar el sistema operativo.

### Nivel 3: Observabilidad e Invarianza de Saldo Real
- El balance de la cuenta REAL debe permanecer rigurosamente inmutable (`38.054.735,36 COP`).
- El monitor de validación (`live_monitor_r23_autonomous.py`) aborta la ejecución con señal de error crítico si detecta `isDemoAccount == false` o si se observa variación alguna no atribuible al saldo DEMO.

---

## 4. Hipótesis Falsables de la Ronda R23A

- **H1 (Aislamiento Incondicional de Cuenta Real):**  
  `isDemoAccount == true` en el 100% de las muestras de telemetría recogidas durante las ventanas de validación. Cero pesos reales arriesgados. Cero llamadas a métodos de tap fuera de cuenta DEMO.
- **H2 (Despacho Autónomo Operativo):**  
  En presencia de condiciones técnicas válidas (estrategia y filtros superados), el sistema es capaz de emitir una orden autónoma y ejecutar el tap físico en las coordenadas correspondientes de la interfaz de Binomo en DEMO.
- **H3 (Ciclo Completo de Orden):**  
  Tras el tap autónomo, la UI de Binomo confirma la orden activa, se completa el periodo de maduración de 60 segundos, se determina el resultado (WIN/LOSS), y queda asentado en el journal (`TradeDraw_Audits`).
- **H4 (Trazabilidad y Paridad Determinista Live ↔ Replay):**  
  Las decisiones emitidas en vivo y las reproducidas en el replay comparten `decision_id` determinista v3 derivado de `(ric, timestampMs, action)`, garantizando concordancia exacta a nivel de activo (`live.asset == replay.asset`).

---

## 5. Reglas de Veredicto

Al concluir cada iteración de validación de 20 minutos realizada por OpenCode:

| Veredicto | Condición | Acción Siguiente |
| :--- | :--- | :--- |
| **KEEP** | H1 cumplida al 100% (cero riesgo real), estabilidad de telemetría, `decision_id` deterministas verificados y progreso demostrable en el pipeline autónomo. | Consolidar en `git`, actualizar `R23_AUTOMEJORA_COORDINATION.md` y formular la siguiente hipótesis. |
| **REVERT** | Violación de H1 (`isDemoAccount == false`), intento de tap indebido, regresión en estabilidad (crash, ANR) o corrupción de datos. | Volver inmediatamente al SHA base anterior (`83b8f95`). |
| **MODIFY** | H1 cumplida pero el runtime queda estrangulado por filtros de estrategia (confluencia, régimen, payout) impidiendo el disparo de órdenes DEMO para validar el ciclo. | Diagnosticar con evidencia empírica el filtro limitante y ordenar ajuste acotado a Hermes. |
| **INCONCLUSIVE** | Interrupción de red externa, caída de sesión de Binomo o desconexión ADB durante la ventana de prueba. | Reiniciar la ventana de medición sin modificar código. |

---

**Firma:**  
*AGY — Orquestador R23*  
*Commit Base: 7a37032*  
*Hardware: Xiaomi Redmi A1 (Android 12 Go Edition)*
