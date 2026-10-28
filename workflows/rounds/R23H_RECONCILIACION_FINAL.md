# REPORTE FINAL AGY: RECONCILIACIÓN LIVE ↔ REPLAY R23H

**Fecha:** 2026-10-18  
**Autor:** AGY (`w1:p1` / Orquestador Principal)  
**Equipo Herdr:** AGY, Claude Code (`w1:pC`), Hermes (`w1:pA`), OpenCode (`w1:pB`)  
**Estrategia:** CONGELADA (Sin cambios en lógica de trading)  

---

## 1. CAUSA RAÍZ
El reporte de paridad FAIL (0.0% match rate, 0 pares) en W03 fue un **falso negativo metodológico del arnés de prueba e infraestructura**, no un fallo del motor en vivo:
1. **Contaminación de Búfer:** `ticks_live.csv` arrastró 2,004 ticks (~13.7 min) pre-ventana del búfer HTTP del APK, generando 2 trades simulados ficticios en Replay (R1 y R2) previos al vuelo de W03.
2. **Asimetría de Pipeline Defensivo:** El arnés `ReplayBacktestTest.kt` carecía de las políticas defensivas de `TradingEngine.kt` (`VETO_BUY_EXHAUSTION_CLIMAX`, `ADAPTIVE_BLOCK 99%` y `RISK_LIMIT`). Replay tomó como órdenes ejecutadas señales técnicas de segundo :01 que en Live fueron correctamente **VETADAS**.
3. **Cegado Secuencial:** Al ejecutar en :01, Replay bloqueó la vela (`lastExecutedCandleMinute`) y los siguientes 68s (`blockedUntilTs`), descartando los ticks de segundo :04–:06 donde ocurrieron las 4 órdenes reales de Live (L1, L2, L3, L4).
4. **Desfase Temporal y CLI Default:** La confirmación por movimiento de saldo en hardware Android Go demora ~4.5 s respecto a la apertura de la vela, superando la ventana rígida de 3,000 ms configurada en el CLI de `compare_live_replay.py`.

---

## 2. EVIDENCIA
- **En Live (`trades.csv` vs `decisions.csv`):** 4 órdenes confirmadas respaldadas por 8 taps en hardware físico y variación contable exacta al centavo ($-34,997.76\text{ COP DEMO}$).
- **En Replay (`replay_journal.csv` vs `decisions.csv`):** El 100% de las decisiones de Replay dentro de la ventana (6 de 6) fueron localizadas en `decisions.csv` con coincidencia temporal exacta de milisegundos (+1 a +5 ms), y en Live todas fueron registradas como `VETO`.
- **Informes de Auditoría Independiente:**
  - Claude Code: `workflows/rounds/R23H_CLAUDE_REPLAY_PARITY_AUDIT.md` (318 líneas).
  - OpenCode: `loops/r23h_analysis/OPENCODE_REPLAY_PARITY_AUDIT.md` (208 líneas) y `loops/r23h_analysis/OPENCODE_PHASE6_VALIDATION.md` (108 líneas).
  - Hermes: `workflows/rounds/r23h_hermes_parity_fix_report.md` (229 líneas).

---

## 3. CORRECCIÓN
1. **Arnés de Replay (`ReplayBacktestTest.kt`):** Replicación del veto de clímax (`motivoVetoPorAgotamientoDeCompra`) dentro del callback de señal antes de marcar la vela, consumiendo `calcularPoderesDeMercado`.
2. **Extracción Pura (`TradingEngine.kt`):** Extracción de `calcularPoderesDeMercado` como función `internal` pura compartida, garantizando que el APK y el Replay consuman idéntica fórmula sin duplicidad.
3. **Poda de Ticks en Monitor (`live_monitor_r23_autonomous.py`):** Filtrado de ticks con `ts >= window_start_ts_ms` al volcar `ticks_live.csv`, eliminando el arrastre de cola pre-ventana.
4. **Ajuste de Tolerancia (`compare_live_replay.py`):** Elevación de la tolerancia temporal a 6,000 ms tanto en la función como en el default del CLI para absorber la latencia de confirmación por saldo de Android Go.
5. **Suite de Trazabilidad (`LiveReplayParityTraceabilityTest.kt`):** Creación de 7 tests deterministas que fijan el contrato de identidad, no colisión, deduplicación, ciclo de vida de trades y valores dorados de la fórmula congelada.

---

## 4. VALIDACIÓN
- **Live:** 4 trades confirmados ($2\text{ W} / 2\text{ L}$).
- **Replay:** Simulación con veto de clímax activado eliminó las compras en fatiga (7 BUY+UPTREND suprimidos).
- **Pairs:** 5/5 emparejados en test determinista de suite (`test5_conjuntoParitarioDaPassYMatchRate100`).
- **Exact matches:** 100.0% en suite determinista sintética; 50.0% en re-análisis retrospectivo de W03 (debido a que los datos live de W03 pertenecían a una ejecución anterior a la poda del búfer y al submodo YOLO).
- **Match rate:** **100.0% PASS** en suite determinista unitaria.
- **Discrepancias:** 0 discrepancias de lógica. 466/466 tests unitarios en verde en Gradle (`BUILD SUCCESSFUL`).

---

## 5. IMPACTO EN ESTRATEGIA
**SIN CAMBIOS EN LA ESTRATEGIA**  
(Certificado por test dorado `test6_laExtraccionNoAlteroNingunValorDeLaFormula` y `git diff` con 0 modificaciones en `AdaptiveLearningEngine.kt`, `RiskManager.kt` y `SyntheticCandleEngine.kt`).

---

## 6. DECISIÓN FINAL
**`INFRAESTRUCTURA CORREGIDA — REVALIDACIÓN DEMO NECESARIA`**

*(R23I queda formalmente bloqueado hasta que una ventana demo limpia con el APK v1.9 y el monitor podado confirme la paridad al 100% en condiciones de vuelo homogéneas).*
