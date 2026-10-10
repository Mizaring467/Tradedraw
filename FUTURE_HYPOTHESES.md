# HIPÓTESIS FUTURAS DE INVESTIGACIÓN Y MEJORA — TRADEDRAW

**Documento:** Hipótesis de Investigación Cuantitativa y Operativa Futura  
**Origen:** Diagnóstico Forense de Pérdidas (`LOSS_FINDINGS.json` y `LOSS_ANALYSIS_REPORT.md`)  
**Estatus:** Hipótesis Teóricas Documentadas — NO IMPLEMENTADAS EN CÓDIGO (Estrategia Congelada)  
**Fecha:** 3 de Octubre de 2026  

---

> [!IMPORTANT]
> **COMPROMISO DE NO INTERVENCIÓN:**  
> Ninguna de las hipótesis planteadas en este documento ha sido aplicada al código de producción ni utilizada para optimizar parámetros sobre datos históricos o sobre el conjunto Holdout. Se formulan exclusivamente como líneas de investigación científica falsificables para ser probadas a futuro bajo el protocolo del `SimulationValidationEngine`.

---

## HIPÓTESIS 1: ELIMINACIÓN DE LA FRONTERA :58–:59 (ALINEACIÓN DE EXPIRACIÓN)

### 1.1 Formulación de la Hipótesis
La ventana de entrada actual autoriza disparos al segundo `:58` y `:59`. Debido a la latencia acumulada (renderizado UI de Android 50–100ms, servicio de accesibilidad 200–400ms, red móvil 100–250ms), la orden llega a los servidores de Binomo cruzando el segundo `:00.000`. Al cruzar la frontera de la vela, el broker extiende forzosamente la expiración al cierre del siguiente minuto (62–72s de duración real), desalineando la predicción del ciclo analizado.

* **Hipótesis nula ($H_0$):** Restringir la entrada a la ventana `:01.000` a `:03.000` (una vez abierta y estabilizada la nueva vela) no altera la tasa de pérdida de estas operaciones.
* **Hipótesis alternativa ($H_1$):** Eliminar entradas en `:58–:59` erradicará el 100% de tasa de fallo observado en ese intervalo específico ($N = 23$ pérdidas consecutivas observadas en telemetría real).

### 1.2 Protocolo de Prueba Experimental
1. Ejecutar en cuenta Demo mediante WebSocket con captura de `broker_ack_timestamp_ms`.
2. Registrar la duración exacta asignada por Binomo (`contract_duration_sec`).
3. Evaluar $N \ge 50$ operaciones para verificar que la duración contractual sea estrictamente $57 \le t \le 59$ segundos y nunca $\ge 62$ segundos.

---

## HIPÓTESIS 2: VETO OBLIGATORIO DE CHOP / MERCADO LATERAL EN TODOS LOS SUBMODOS

### 2.1 Formulación de la Hipótesis
Actualmente, el modo YOLO desactiva explícitamente los filtros de consolidación y choppiness (`TradingEngine.kt:1199`). La telemetría forense demostró que el **100% de las 41 firmas de pérdida detalladas** en `adaptive_learning_state.json` ocurrieron en régimen `trend == SIDEWAYS`, y el **95.1%** ocurrieron con el precio atrapado simultáneamente cerca de soporte y resistencia (`nearSup == true` y `nearRes == true`).

* **Hipótesis nula ($H_0$):** Reactivar el filtro de choppiness en modo YOLO no altera la expectativa matemática (EV).
* **Hipótesis alternativa ($H_1$):** Vetar operativas cuando `isMarketSideways == true` o `cachedChoppiness == true` eliminará el 33.3% de las pérdidas totales registradas ($N = 38$), reduciendo drásticamente el drawdown de la cuenta sin degradar el Win Rate.

### 2.2 Protocolo de Prueba Experimental
1. Probar en el `SimulationValidationEngine` con replay de datos tick a tick limpios de Crypto IDX.
2. Comparar el ratio de Sharpe y el Max Drawdown entre:
   - Grupo A: Sin filtro anti-chop (Modo YOLO actual).
   - Grupo B: Con veto estricto de chop (mínimo ratio de canal S/R $\ge 0.0005$ del precio base).

---

## HIPÓTESIS 3: ELIMINACIÓN DE LA INVERSIÓN ADAPTATIVA DE SEÑALES

### 3.1 Formulación de la Hipótesis
`AdaptiveLearningEngine.kt` incluye una regla de "inversión de anti-patrones" (`canInvert`, líneas 258–270) que invirtió 2,558 señales en la historia de la aplicación. Cuando el motor detectaba una pérdida previa en soporte, invertía la siguiente señal de BUY a SELL. Esto provocaba ventas forzadas en mínimos del canal y compras en máximos, persiguiendo el ruido blanco y exacerbando rachas perdedoras.

* **Hipótesis nula ($H_0$):** La inversión adaptativa de señales produce un Win Rate superior o igual a la señal directa original.
* **Hipótesis alternativa ($H_1$):** Eliminar la lógica de inversión (limitándose a bloquear señales en lugar de invertirlas) evitará las 39 pérdidas catalogadas como ventas en soporte y compras en techos.

### 3.2 Protocolo de Prueba Experimental
1. Correr simulación paralela en 500 operaciones sintéticas no sesgadas:
   - Rama 1: Estrategia pura (sin inversión).
   - Rama 2: Estrategia con autoaprendizaje inversor activo.
2. Calcular el p-valor de dos colas para la diferencia de proporciones binomiales.

---

## HIPÓTESIS 4: ASIMETRÍA DIRECCIONAL Y FILTRO MACRO DE TENDENCIA (SELL BIAS)

### 4.1 Formulación de la Hipótesis
En la telemetría real analizada, las órdenes SELL sufrieron un Win Rate de sólo **37.50%** ($N = 112$, 70 derrotas vs 42 victorias), mientras que las órdenes BUY obtuvieron **46.75%** ($N = 77$, 41 derrotas vs 36 victorias). Esta asimetría de casi 10 puntos porcentuales sugiere un sesgo macro alcista (drift estructural positivo en Crypto IDX durante la ventana observada).

* **Hipótesis nula ($H_0$):** La diferencia de rendimiento entre BUY y SELL se debe al azar binomial ($p > 0.05$).
* **Hipótesis alternativa ($H_1$):** Existe una deriva alcista no capturada por las velas de 1 minuto que penaliza sistemáticamente las operaciones PUT. Un filtro de media móvil o regresión lineal de 15 minutos mejorará la coherencia direccional.

### 4.2 Protocolo de Prueba Experimental
1. Medir la pendiente acumulada del activo subyacente en marcos de 15m y 1h.
2. Prohibir operaciones PUT cuando la media exponencial EMA(200) en velas sintéticas de 1m tenga pendiente positiva estadísticamente significativa ($t > 2.0$).

---

## HIPÓTESIS 5: TELEMETRÍA DE RED Y LATENCIA END-TO-END

### 5.1 Formulación de la Hipótesis
La aplicación carece de marcas de tiempo intermedias para aislar los componentes de la latencia de ejecución.

* **Hipótesis:** Al incorporar telemetría con tres timestamps independientes:
  - $t_0$: Timestamp de generación de señal en motor (`signal_ts_ms`).
  - $t_1$: Timestamp de finalización de gesto en accesibilidad (`click_ts_ms`).
  - $t_2$: Timestamp de confirmación de orden recibida en WebSocket (`ws_ack_ts_ms`).
  Se podrá calcular de manera continua el retardo de despacho ($\Delta t = t_2 - t_0$) y abortar dinámicamente cualquier orden si $\Delta t > 250\text{ ms}$, eliminando las pérdidas por ejecución desfasada.

---

## MATRIZ DE PRIORIZACIÓN DE INVESTIGACIÓN

| Hipótesis | Impacto Potencial en Reducción de Pérdidas | Complejidad de Implementación | Grado de Evidencia Previa | Prioridad |
| :--- | :--- | :--- | :--- | :--- |
| **H1: Desfase :58-:59** | 20.18% de las pérdidas ($N = 23$) | Baja (Ajuste de ventana de disparo) | **Muy Alta** ($0\%$ WR observado en :58-:59) | **1 (Inmediata)** |
| **H2: Veto Anti-Chop** | 33.33% de las pérdidas ($N = 38$) | Muy Baja (Reactivar filtro en YOLO) | **Muy Alta** (100% lossSignatures en SIDEWAYS)| **2 (Inmediata)** |
| **H3: Eliminar Inversión**| 34.21% de las pérdidas ($N = 39$) | Baja (Retirar método `canInvert`) | **Alta** (2,558 inversiones registradas) | **3 (Alta)** |
| **H4: Filtro Macro SELL**| 9.25% de delta en WR | Media (Requiere buffer multi-timeframe)| **Media** ($Z = 1.28$, no concluyente aún) | **4 (Media)** |
| **H5: Telemetría Latencia**| Diagnóstico preventivo continuo | Media (Integración WebView/Accessibility) | **Alta** (Requerida por ExecutionModel) | **5 (Técnica)** |
