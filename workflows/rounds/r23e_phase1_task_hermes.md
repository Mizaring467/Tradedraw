# TAREA HERMES: FASE 0 Y FASE 3 DE R23E — AUDITORÍA CONTRAFACTUAL DE ADAPTIVE_BLOCK

**Fecha:** 2026-10-16  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** HERMES (`w1:p4` / Implementador Android & Python)  
**Rama:** `r10-night`  
**Base git:** `98656f9`  
**Preregistro:** [`workflows/PREREGISTRO_R23E.md`](file:///C:/Users/heidy/Tradedraw/workflows/PREREGISTRO_R23E.md)  
**Auditoría Técnica:** [`workflows/R23E_ADAPTIVE_BLOCK_AUDIT.md`](file:///C:/Users/heidy/Tradedraw/workflows/R23E_ADAPTIVE_BLOCK_AUDIT.md)  
**Regla de Seguridad Absoluta:** 100% CUENTA DEMO (`isDemoAccount == true`). Aborto inmediato si se vulnera.

---

## 1. OBJETIVO DE LA TAREA
Implementar el mecanismo pasivo de **Contrafactual Shadow para `ADAPTIVE_BLOCK`** y garantizar la **Trazabilidad de Build Estricta** (Fase 0).
**IMPORTANTE:** NO modificar la lógica de trading de `ADAPTIVE_BLOCK` ni relajar ningún guard. El bot real continuará operando en cuenta DEMO exactamente igual que en R23D, mientras que el pipeline contrafactual registrará y resolverá en paralelo lo que habría sucedido con las señales vetadas.

---

## 2. REQUISITOS TÉCNICOS ESPECÍFICOS

### REQ 1: Enriquecimiento de Telemetría de `ADAPTIVE_BLOCK` (Android)
En `app/src/main/java/com/example/tradedraw/TradingEngine.kt` (alrededor de L2305):
Asegurar que cuando se emita el veto `ADAPTIVE_BLOCK` a `TradeEventLog`, el `detail` contenga de forma parseable y explícita:
- La similitud porcentual (`sim=NN%`)
- La razón del autoaprendizaje (`reason`)
- El puntaje real de confluencia (`[Confluencia: NNpts]` o equivalente)
Ejemplo de formato en `TradeEventLog`:
`ADAPTIVE_BLOCK [sim:98%] [Confluencia: 85pts] Anti-patrón perdedor recurrente...`

### REQ 2: Pipeline Contrafactual Shadow en Monitor Live (Python)
En `scripts/validation/live_monitor_r23_autonomous.py`:
1. **Detección y Registro:**
   Cuando se observe un evento `VETO` cuyo `detail` contenga `ADAPTIVE_BLOCK`:
   - Extraer `decision_id`, `atMs`, `action` (`BUY` o `SELL`), `price_at_signal` ($P_{entry}$), `similarity` (int o float), `score` de confluencia real, `market_regime` (`TRENDING`/`SIDEWAYS`), `trend` (`UPTREND`/`DOWNTREND`/`SIDEWAYS`), y `payout` actual.
2. **Deduplicación Estricta (Unidad Experimental del Preregistro):**
   - **Ventana de Ocupación Emulada:** No registrar un nuevo contrafactual en la misma dirección (`action`) si ya existe uno activo cuyo horizonte de 60s no ha concluido (emulando la condición real de `OPERACION_EN_CURSO`).
   - **Máximo 1 contrafactual por vela de 60s por dirección.**
3. **Resolución Contrafactual a 60 Segundos:**
   - Mantener una cola de contrafactuales pendientes.
   - En cada ciclo del monitor (y al cierre de la ventana con settlement grace):
     Para cada contrafactual cuyo $t_{now} \ge t_{entry} + 60,000\text{ ms}$:
     Buscar en `accumulated_ticks` el precio $P_{exit}$ del tick más cercano a $t_{entry} + 60\text{s}$.
     Evaluar:
     - `BUY`: $P_{exit} > P_{entry} \to \text{COUNTERFACTUAL\_WIN}$, $P_{exit} < P_{entry} \to \text{COUNTERFACTUAL\_LOSS}$, igual $\to \text{COUNTERFACTUAL\_VOID}$.
     - `SELL`: $P_{exit} < P_{entry} \to \text{COUNTERFACTUAL\_WIN}$, $P_{exit} > P_{entry} \to \text{COUNTERFACTUAL\_LOSS}$, igual $\to \text{COUNTERFACTUAL\_VOID}$.
     - Calcular `theoretical_ev`:
       Si WIN: $+b \times \text{stake} = +0.82 \times 100000 = +82000.0$
       Si LOSS: $-\text{stake} = -100000.0$
       Si VOID: $0.0$
4. **Artefacto de Salida:**
   Generar en cada ventana: `counterfactual_adaptive_block.csv`
   Columnas obligatorias:
   ```csv
   counterfactual_id,decision_id,asset,action,decision_ts,entry_price,expiry_ts,exit_price,result,score,similarity,market_regime,trend,payout,theoretical_ev
   ```
5. **Reporte `LIVE_REPORT.md`:**
   Añadir sección dedicada a métricas contrafactuales:
   - $N_{blocked\_evaluated}$, $N_{cf\_deduplicated}$, $CF\_W$, $CF\_L$, $CF\_VOID$, $CF\_WR$, $CF\_EV$.
   - Segmentación por buckets de similitud ($90-93\%$, $93-96\%$, $96-98\%$, $98-100\%$).

### REQ 3: Suite de Pruebas Unitarias
Crear o extender tests unitarios en Kotlin (`app/src/test/java/...`) y/o tests en Python para validar:
1. Deduplicación de contrafactuales (mismo candle no genera duplicados).
2. Resolución contrafactual matemática de WIN/LOSS/VOID a 60s.
3. Formato y consistencia de `counterfactual_adaptive_block.csv`.

---

## 3. FASE 0 — PROTOCOLO ESTRICTO DE BUILD Y DESPLIEGUE

Secuencia **OBLIGATORIA E INMUTABLE**:
1. Finalizar cambios de código en Kotlin y Python.
2. Ejecutar y pasar suite completa de tests:
   ```bash
   ./gradlew.bat testDebugUnitTest --rerun-tasks
   ```
3. Realizar Git Commit del código probado:
   ```bash
   git add app/ scripts/
   git commit -m "feat(r23e): contrafactual shadow de adaptive_block y telemetria enriquecida"
   ```
4. Obtener el Git SHA exacto del commit:
   ```bash
   git rev-parse --short HEAD
   ```
5. Configurar `app/build.gradle.kts`:
   - `versionCode = 17`
   - `versionName = "1.7-r23e-shadow"`
   - `BUILD_ID = "R23E-SHADOW"`
   - Asegurar que `gitSha` embeba el commit probado del paso 4.
6. Compilar APK:
   ```bash
   ./gradlew.bat assembleDebug
   ```
7. Instalar en el Xiaomi Redmi A1:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
8. Verificar en runtime vía HTTP bridge (`/status`):
   - `versionCode == 17`
   - `versionName == "1.7-r23e-shadow"`
   - `buildId == "R23E-SHADOW"`
   - `gitSha` coincide con el commit.
   - `isDemoAccount == true`
   - `health.state == "READY"`
9. Redactar reporte de entrega en `workflows/rounds/r23e_phase1_hermes.md`.
10. Notificar a AGY (`w1:p1`) para autorizar a OpenCode a iniciar la validación live (Fase 4).
