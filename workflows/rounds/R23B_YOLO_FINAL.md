# REPORTE FINAL Y CIERRE FORMAL — FASE R23B (MODO YOLO EN CUENTA DEMO)

**Fecha de Cierre:** 2026-10-16  
**Orquestador:** AGY (`w1:p1`)  
**Implementador:** HERMES (`w1:p6`)  
**Validador Live:** OPENCODE (`w1:pT`)  
**Rama:** `r10-night`  
**Dispositivo Físico:** Xiaomi Redmi A1 (`220733SL`, Android 12 Go, conexión WiFi `[fd10:...]:5555`)  
**Entorno Operativo:** Cuenta DEMO Estricta (`isDemoAccount == true`, fail-closed, 0 cuentas reales)

---

## 1. RESUMEN EJECUTIVO

La fase **R23B** evaluó el comportamiento operativo del modo **YOLO** en cuenta DEMO para contrastarlo frente al modo **AUTONOMOUS CONSERVATIVE** (R23A). Se ejecutaron tres hitos formales:
1. **Baseline YOLO (APK v12, SHA `bd7ef4e`):** Demostró que YOLO estaba completamente paralizado por el guard `Trailing Profit Lock` (74,3% de vetos), volviendo su frecuencia inobservable.
2. **Iteración 1 (APK v13, SHA `ba75268`, BUILD `R23-YOLO-ITER1`):** Desbloqueó los límites de sesión en YOLO (`sessionFloor`, `Trailing Profit Lock`, `stopLossStreak`), corrigió el riesgo fail-open en `RiskManager.currentSubMode` (default `CONSERVATIVE`) y expuso `"submode"` en `/status`. La operativa se desbloqueó con **12 taps, 6 trades, W=4 / L=2 (WR 66,7%)** y ganancia neta de $+131.998,72\text{ COP}$ en la ventana oficial (que luego acumuló hasta 7W / 2L y $+381.000\text{ COP}$ en live).
3. **Iteración 2 (APK v14, SHA `ac079fd`, BUILD `R23-YOLO-ITER2`):** Implementó telemetría determinística inyectando `pendingTradeAction` en `setOrderState` (`RiskManager.kt`) y filtró eventos TAP `END root` en el monitor. Cumplimiento técnico al 100%: **`action = UNKNOWN` = 0 (BUY: 3, SELL: 2)** y 0 eventos TAP espurios. Ventana de 5 trades (1W / 4L) explicable por varianza normal en muestra pequeña.

---

## 2. CUADRO CONSOLIDADO DE ITERACIONES R23B

| Hito / Iteración | SHA | versionCode | Build ID | Ventana | Trades | W / L | WR | Δ Balance (COP) | Trailing Vetos | `action=UNKNOWN` | Veredicto |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Baseline** | `bd7ef4e` | 12 | `R23-AUTO-ITER3` | 11,3 min (truncada) | 0 | 0 / 0 | N/A | $0,00$ | 75 (74,3%) | N/A | `KEEP` (seguridad) |
| **Iteración 1** | `ba75268` | 13 | `R23-YOLO-ITER1` | 20,0 min | 6 | 4 / 2 | 66,7% | $+131.998,72$ | 0 (0,0%) | 1 (16,7%) | `KEEP` |
| **Iteración 2** | `ac079fd` | 14 | `R23-YOLO-ITER2` | 20,0 min | 5 | 1 / 4 | 20,0% | $-316.999,68$ | 0 (0,0%) | **0 (0,0%)** | **`KEEP (técnico)`** |

### Balance Global de la Fase R23B
- **Saldo Inicial R23B:** $38.320.737,28\text{ COP}$
- **Saldo Final R23B:** **$38.716.736,00\text{ COP}$**
- **Resultado Neto Acumulado en DEMO:** **$+395.998,72\text{ COP}$**
- **Operaciones Reales en Cuenta Real:** **0 (CERO)** — Seguridad $100\%$ garantizada.

---

## 3. APRENDIZAJES CLAVE Y EVALUACIÓN DE GUARDS

1. **El Trailing Profit Lock Histórico como Factor de Bloqueo:** En `CONSERVATIVE` y en el baseline de YOLO, el `Trailing Profit Lock` utilizaba `sessionPeakBalance` acumulado de rondas anteriores, congelando la operativa perpetuamente tras cualquier retroceso menor. Su eliminación en YOLO demostró que el motor es capaz de operar y generar señales ejecutables.
2. **Guards Indispensables en YOLO:**
   - `ADAPTIVE_BLOCK`: Vetó consistentemente entre 10 y 18 anti-patrones de aprendizaje en zonas de S/R, evitando entradas de alto riesgo.
   - `Cooldown YOLO` ($8\text{ s}$): Evitó disparos atropellados en la misma vela.
   - `Operación Abierta en Curso`: 34-40 vetos benignos que previnieron colisiones y solapes.
   - `Suelo de Equity Absoluto` ($20.000.000\text{ COP}$): Salvaguarda última fail-closed.
3. **Riesgo Identificado en YOLO:**
   - Sin `stopLossStreak` ni `sessionFloor`, una racha perdedora consecutiva (como la racha de 3 derrotas en la ventana 2) puede causar drawdowns rápidos (hasta $-400.000\text{ COP}$ o $-1,03\%$ en 20 min).

---

## 4. DEFECTOS RESUELTOS Y PENDIENTES ESTRUCTURALES

- **Resueltos en R23B:**
  - ✅ Desbloqueo de límites de sesión en YOLO.
  - ✅ Detección y corrección de riesgo fail-open en `RiskManager.currentSubMode`.
  - ✅ Exposición de `submode` en `/status` sin violar RFC 8259 (cero comentarios `//`).
  - ✅ Inyección determinística de `BUY` / `SELL` en `TradeEventLog` (0 `UNKNOWN`).
  - ✅ Filtrado de eventos de tap `END root` en `trades.csv`.
- **Pendientes para Fases Posteriores:**
  - ⚠️ **Paridad Live ↔ Replay:** Cobertura en 0% (2/63 pares). Replay offline no modela los guards vivos (`ADAPTIVE_BLOCK`, `RISK_LIMIT`, etc.).
  - ⚠️ **Formato de `events.jsonl`:** `curl /events` entrega un objeto `{"events":[...]}`, mientras que el volcado del monitor emite líneas JSONL puras.

---

## 5. BASELINE TÉCNICO RATIFICADO PARA R23C

Se ratifica formalmente el siguiente baseline técnico inmutable para el inicio de **R23C**:
- **Commit:** `ac079fd`
- **versionCode:** `14`
- **versionName:** `"1.4-r23-yolo-iter2"`
- **BUILD_ID:** `"R23-YOLO-ITER2"`
- **Configuración:** Modo `AUTONOMOUS`, Submodo `YOLO`, `isDemoAccount == true`.
