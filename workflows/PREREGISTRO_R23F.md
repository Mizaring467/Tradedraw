# PREREGISTRO R23F — RELAJACIÓN SELECTIVA DE ADAPTIVE_BLOCK (THRESHOLD 99.0%) + VALIDACIÓN LIVE EN DEMO

**Fecha de Preregistro:** 2026-10-17  
**Orquestador:** AGY (`w1:p1`)  
**Implementador:** HERMES (`w1:p4`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Rama Base:** `r10-night` | **Commit Base:** `6cb2226`  
**Regla Absoluta de Seguridad:** 100% CUENTA DEMO (`isDemoAccount == true`). Aborto fail-closed inmediato si deja de cumplirse.

---

## 1. CONTEXTO Y BASELINE DE PARTIDA (R23E)

En la auditoría contrafactual de R23E (120 minutos, 6 ventanas, 27.492 ticks en hardware real):
- **Población Contrafactual Resuelta:** $N = 38$ operaciones a 60s (21 Wins, 17 Losses).
- **WR Global Contrafactual:** $55.26\%$ vs Breakeven $WR_{\text{BE}} = 54.80\%$ ($b \approx 0.827$).
- **EV Teórico Global:** $+35.000,00\text{ COP}$.

### Hallazgo Empírico Fundamental (Bifurcación por Similitud):
1. **Bucket Similitud 98.0%:**
   - $N = 15$ operaciones (10 Wins / 5 Losses).
   - $WR = \mathbf{66.67\%}$ ($+11.87\%$ sobre breakeven).
   - $EV = \mathbf{+325.000,00\text{ COP}}$.
   - **Diagnóstico:** **SOBRE-RESTRICTIVO (`OVERRESTRICTIVE`)**. El guard veta señales con alta expectativa positiva y confluencia robusta.
2. **Bucket Similitud 99.0%:**
   - $N = 23$ operaciones (11 Wins / 12 Losses).
   - $WR = \mathbf{47.83\%}$ ($-6.97\%$ bajo breakeven).
   - $EV = \mathbf{-290.000,00\text{ COP}}$.
   - **Diagnóstico:** **CATEGÓRICAMENTE PROTECTOR (`PROTECTIVE`)**. El guard previene pérdidas severas de capital.

**Conclusión Metodológica:**
Prohibido desactivar `ADAPTIVE_BLOCK` de forma global (reintroduciría $-290.000\text{ COP}$ en pérdidas del 99%).  
Se activa formalmente el **CASO B del Preregistro R23E: RELAJACIÓN SELECTIVA DE SIMILITUD**.

---

## 2. HIPÓTESIS CENTRAL DE R23F

> *"Al elevar el umbral de similitud para el veto por anti-patrón de $0.90$ a $0.990$ (`ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99f`), las señales con similitud del 98% (que demostraron un WR del $66.67\%$ y un EV de $+325.000\text{ COP}$) serán liberadas para ejecución real con taps en cuenta DEMO, mientras que las señales con similitud del 99% (WR $47.83\%$, EV $-290.000\text{ COP}$) continuarán vetadas por `ADAPTIVE_BLOCK`, maximizando el balance neto sin asumir riesgo de anti-patrones exactos."*

---

## 3. ESPECIFICACIÓN TÉCNICA PARA HERMES (IMPLEMENTADOR)

### REQ 1: Umbral de Similitud en `AdaptiveLearningEngine.kt`
- Ubicación: `app/src/main/java/com/example/tradedraw/AdaptiveLearningEngine.kt` (L259).
- Declarar constante en `AdaptiveLearningEngine`:
  ```kotlin
  companion object {
      const val ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99f
  }
  ```
- Condición de Veto por Anti-patrón:
  ```kotlin
  if (highestSimilarity >= ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD && matchingLossSig != null &&
      isTightlyMatchingContext(candidateSignature, matchingLossSig!!)) {
      // Bloqueo estricto sólo ante cuasi-identidad (>= 99.0%)
  ```
- Las señales con `highestSimilarity < 0.99f` (incluyendo todo el bucket 98%) NO deben entrar en `AdaptiveDecision.Block`, procediendo a `AdaptiveDecision.Allow`.

### REQ 2: Trazabilidad y Configuración de Build
- Ubicación: `app/build.gradle.kts`.
- Configurar:
  - `versionCode = 18`
  - `versionName = "1.8-r23f-adaptive"`
  - `buildConfigField("String", "BUILD_ID", "\"R23F-ADAPTIVE\"")`

### REQ 3: Tests Unitarios en Kotlin
- Crear `app/src/test/java/com/example/tradedraw/AdaptiveBlockThresholdTest.kt`:
  - Test 1: Similitud calculada al 98% ($0.985f$) con firma en pérdidas devuelve `AdaptiveDecision.Allow`.
  - Test 2: Similitud calculada al 99% ($0.992f$) con firma en pérdidas devuelve `AdaptiveDecision.Block`.
  - Test 3: Similitud calculada al 100% ($1.000f$) devuelve `AdaptiveDecision.Block`.
- Ejecutar suite completa: `./gradlew.bat testDebugUnitTest --rerun-tasks` (debe pasar al 100%, 0 fallos).

### REQ 4: Protocolo de Build e Instalación
1. Código finalizado y tests verdes.
2. Git commit con mensaje formal `feat(r23f): relajar umbral de similitud en ADAPTIVE_BLOCK a 0.99f (liberar bucket 98%)`.
3. Compilar APK (`assembleDebug`) verificando que `gitSha` coincida exactamente con HEAD.
4. Instalar en dispositivo (`adb install -r app/build/outputs/apk/debug/app-debug.apk`).
5. Verificar `/status` (`versionCode = 18`, `buildId = "R23F-ADAPTIVE"`, `gitSha = <HEAD>`, `isDemoAccount = true`).

---

## 4. ESPECIFICACIÓN DE VALIDACIÓN PARA OPENCODE (VALIDADOR LIVE)

1. **Entorno:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`), 100% Cuenta DEMO.
2. **Campaña:** Mínimo 3 Ventanas consecutivas $\times$ 20.0 minutos (60 minutos de telemetría en vivo).
3. **Métricas a Monitorear:**
   - Cantidad de trades reales ejecutados ($N_{\text{real}} \ge 3$ esperados, a diferencia de N=1 en R23E).
   - Tasa de acierto real ($WR_{\text{real}}$) frente al breakeven $54.80\%$.
   - Confirmación de que los vetos `ADAPTIVE_BLOCK` restantes pertenecen **exclusivamente al bucket 99%** (`[sim:99%]` o `[sim:100%]`).
   - Cero vetos `[sim:98%]` observados en el telemetría.
   - Seguridad: `isDemoAccount == true` en 100% de las muestras. Drawdown acumulado $\le 3.0\%$.

---

## 5. CRITERIOS DE ÉXITO Y DECISIÓN DE FASE (R23F)

- **KEEP (Éxito Definitivo):**
  - $N_{\text{real}} \ge 6$ trades a lo largo de las ventanas.
  - $WR_{\text{real}} \ge 55.0\%$ (rentable en cuenta DEMO).
  - Veto 99% funcionando y bloqueando anti-patrones perdedores.
- **REVERT / TWEAK:**
  - Si el balance sufre drawdown $> 3.0\%$ o $WR_{\text{real}} < 45.0\%$, pausar inmediatamente y evaluar ajuste fino de confluencia o reincorporación parcial de filtros.
- **INCONCLUSIVE:**
  - $N_{\text{real}} < 3$ por condiciones anómalas de baja volatilidad en el mercado.
