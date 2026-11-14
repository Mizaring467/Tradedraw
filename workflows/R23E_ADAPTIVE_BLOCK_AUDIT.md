# AUDITORÍA TÉCNICA Y MATEMÁTICA DE ADAPTIVE_BLOCK (R23E)

**Fecha:** 2026-10-16  
**Autores:** AGY (`w1:p1`) & HERMES (`w1:p4`)  
**Archivos Auditados:**
- [`app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt`](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt)
- [`app/src/main/java/com/example/tradedraw/TradingEngine.kt`](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/TradingEngine.kt#L2286-L2321)
- [`app/src/main/java/com/example/tradedraw/TradeEventLog.kt`](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/TradeEventLog.kt)

---

## 1. PREGUNTA CLAVE: ¿POR QUÉ ADAPTIVE_BLOCK ALCANZÓ 96.8% DE VETO EN R23D?

Durante la campaña R23D se registraron **848 vetos de 876 decisiones evaluadas** ($96.8\%$).  
El análisis exhaustivo del código fuente revela la causa exacta:

En activos de rango micrométrico como Crypto IDX (`Z-CRY/IDX`), el precio se mueve frecuentemente en rangos dinámicos comprimidos donde las distancias relativas calculadas a Soporte y Resistencia (`distanceToSupportRatio`, `distanceToResistanceRatio`) y la tendencia del motor sintético (`UPTREND` o `DOWNTREND`) permanecen casi idénticas durante minutos enteros.

Cuando se produce una pérdida (LOSS), `AdaptiveLearningEngine.recordTradeOutcome(false)` almacena la firma contextual perdedora. En los ciclos posteriores en la misma tendencia:
- La acción es idéntica ($+0.20$ peso).
- La tendencia es idéntica ($+0.20$ peso).
- La proximidad S/R es casi idéntica ($+0.30$ peso).
- El régimen e impulsos coinciden ($+0.30$ peso).
- **Similitud calculada: $95\% - 99\%$**.
- Cumple la compuerta estricta `isTightlyMatchingContext`.

Al ser vetada, la orden **nunca se envía al broker**. Al no enviarse, **nunca puede generar un WIN**. Al no haber WINs para desplazar o limpiar la memoria, y al no existir decay temporal en memoria volátil, **el bot entra en un bucle cerrado de veto perpetuo** hasta que el mercado cambia drásticamente de régimen o entra una pérdida en la dirección contraria.

---

## 2. ANÁLISIS DETALLADO DEL COMPONENTE

### 2.1. Features Utilizadas en la Firma (`TradeContextSignature`)
Definido en [`AdaptiveLearningEngine.kt#L12-L30`](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt#L12-L30):
```kotlin
data class TradeContextSignature(
    val action: TradeAction,
    val trend: TrendDirection,
    val distanceToSupportRatio: Float,
    val distanceToResistanceRatio: Float,
    val isNearSupportZone: Boolean,
    val isNearResistanceZone: Boolean,
    val tickVelocityNormalized: Float,
    val isBullishImpulse: Boolean,
    val isBearishImpulse: Boolean,
    val isDojiOrLowVolume: Boolean,
    val isConsolidationTight: Boolean,
    val isMarketSideways: Boolean,
    val strategyName: String,
    val candleSecond: Int,
    val timestampMs: Long = System.currentTimeMillis(),
    val isContinuationTrade: Boolean = false,
    val isReversionTrade: Boolean = false
)
```

### 2.2. Cálculo Matemático de la Similitud (`calculateSignatureSimilarity`)
Ubicado en [`AdaptiveLearningEngine.kt#L301-L331`](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt#L301-L331).  
La similitud es una función lineal acotada en $[0.0, 1.0]$ estructurada en 5 dimensiones ponderadas:

| Dimensión | Ponderación | Criterio de Similitud |
| :--- | :---: | :--- |
| **1. Misma Acción** | **0.20** | `if (a.action == b.action) score += 0.20f` |
| **2. Misma Tendencia** | **0.20** | `if (a.trend == b.trend) score += 0.20f` |
| **3. Proximidad a S/R** | **0.30** | `srSim = ((1.0 - |ΔdistSup|) + (1.0 - |ΔdistRes|)) / 2.0 * 0.30` |
| **4. Impulso y Velocidad**| **0.15** | Si ambos coinciden en impulsos alcistas/bajistas: `+0.15`. De lo contrario: penalización por diferencia de velocidad. |
| **5. Régimen Lateral** | **0.15** | `if (a.isMarketSideways == b.isMarketSideways && a.isConsolidationTight == b.isConsolidationTight) score += 0.15f` |
| **TOTAL** | **1.00** | Máximo teórico $= 1.00$ ($100\%$) |

### 2.3. Memoria, Ventana Histórica y Capacidad
- **Estructura:** `lossSignatures = ArrayList<TradeContextSignature>(64)` ([`L54`](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt#L54)).
- **Capacidad Máxima:** 50 firmas. Cuando se alcanza la capacidad máxima, se descarta la más antigua mediante FIFO:
  ```kotlin
  if (lossSignatures.size >= 50) {
      lossSignatures.removeAt(0)
  }
  lossSignatures.add(sig)
  ```
- **Ventana de Consulta:** En cada evaluación (`evaluateSignalSuitability`), se consulta únicamente la sub-lista de las últimas 20 pérdidas:
  ```kotlin
  val recentLosses = lossSignatures.takeLast(20)
  ```

### 2.4. ¿Qué es un "Anti-Patrón Perdedor Recurrente"?
Es una señal candidata cuya similitud contextual frente a cualquiera de las últimas 20 pérdidas cumple simultáneamente dos condiciones:
1. Similitud matemática $\ge 0.90$ ($90\%$):
   `highestSimilarity >= 0.90f` ([`L259`](file:///C:/Users/heidy/Tradedraw/app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt#L259)).
2. Compuerta de contexto estricta (`isTightlyMatchingContext`):
   ```kotlin
   private fun isTightlyMatchingContext(a: TradeContextSignature, b: TradeContextSignature): Boolean {
       if (a.action != b.action) return false
       if (a.trend != b.trend) return false
       if (a.isMarketSideways != b.isMarketSideways) return false
       val ds = Math.abs(a.distanceToSupportRatio - b.distanceToSupportRatio)
       val dr = Math.abs(a.distanceToResistanceRatio - b.distanceToResistanceRatio)
       return ds <= 0.10f && dr <= 0.10f
   }
   ```
   *Nota sobre Inversión Adaptativa (H3):* Históricamente el motor invertía la orden (`AdaptiveDecision.Invert`). Por regla de governance de Master Traders (H3), la inversión fue deshabilitada permanentemente, forzando un veto total (`AdaptiveDecision.Block`) para evitar compras en techos o ventas en pisos.

### 2.5. Preguntas Clave de Dominio
- **¿Distingue BUY / SELL?**  
  **SÍ.** La dimensión 1 aporta 0.20 a la similitud si coinciden, y la compuerta `isTightlyMatchingContext` exige `a.action == b.action` como condición estricta e indispensable.
- **¿Distingue Régimen?**  
  **SÍ.** Exige `a.trend == b.trend` y `a.isMarketSideways == b.isMarketSideways`.
- **¿Distingue Activo?**  
  **NO.** `TradeContextSignature` no almacena el activo (`asset`). Una pérdida ocurrida en cualquier par se proyecta a todos los pares si el contexto matemático coincide. En la práctica actual, al operar exclusivamente en `Z-CRY/IDX`, no existe contaminación multi-activo.
- **¿Cuándo expira? ¿Existe Decay?**  
  - **En memoria de ejecución (RAM):** **NO existe decay temporal.** Las firmas no pierden peso con el paso de los minutos ni de las horas. Una firma permanece en memoria indefinidamente hasta que ingresen 50 nuevas pérdidas que la desplacen o se reinicie la app.
  - **En disco (persistencia JSON):** Al reiniciar la app (`loadState`), existe un TTL de 24 horas (`maxTtlMs = 24L * 60L * 60L * 1000L`). Toda firma con más de 24 horas es purgada al cargar.

---

## 3. CONCLUSIÓN Y JUSTIFICACIÓN DE LA FASE R23E

La alta selectividad de `ADAPTIVE_BLOCK` fue el factor clave que permitió a R23D mantener un drawdown microscópico de **$0.87\%$** y un balance plano ($\Delta -0.06\%$).  
Sin embargo, al vetar el **$96.8\%$** de las decisiones, existe el riesgo latente de que esté vetando tanto operaciones perdedoras legítimas (protección real) como operaciones que habrían sido ganadoras contundentes (estrangulamiento de oportunidades).

La auditoría contrafactual de R23E es el único mecanismo estadísticamente riguroso que permitirá discernir la tasa real de éxito ($CF\_WR$) y valor esperado ($CF\_EV$) de esas señales bloqueadas sin arriesgar un solo centavo de saldo.
