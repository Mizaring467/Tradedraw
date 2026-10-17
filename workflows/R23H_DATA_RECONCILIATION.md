# R23H — RECONCILIACIÓN FORENSE DE DATOS Y CANONICAL TRADE LEDGER

**Fecha:** 2026-10-17  
**Orquestador:** AGY (`w1:p1`)  
**Equipo:** AGY, HERMES (`w1:pA`), OPENCODE (`w1:pB`), CLAUDE CODE (`w1:pC`)  
**Modo:** 100% CUENTA DEMO EXCLUSIVAMENTE  
**Rama:** `r10-night`  

---

## 1. MOTIVACIÓN: INCONSISTENCIA MATEMÁTICA EN REPORTES R23G

Al cierre de la campaña R23G, el documento `AGGREGATE_REPORT.md` y `R23G_FINAL.md` registraron discrepancias aritméticas inaceptables:

| Métrica reportada | $N$ afirmado | $W$ | $L$ | $W + L$ | Discrepancia ($N - (W+L)$) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **R23G Muestra local** | 32 | 14 | 22 | **36** | **-4** |
| **R23F + R23G Combinado** | 47 | 23 | 28 | **51** | **-4** |
| **BUY R23G** | 31 | 13 | 22 | **35** | **-4** |
| **Proceso Global APK** | 88 | 49 | 38 | **87** | **+1** |

**Regla de Oro R23H:**
> Queda prohibido modificar la estrategia o asumir conclusiones tácticas (como "BUY es destructivo" o "SELL es rentable") hasta que los datos sean reconstruidos y reconciliados trade por trade desde las fuentes primarias.
> Condición matemática obligatoria: **$N = W + L + VOID$**.

---

## 2. ESQUEMA DEL CANONICAL TRADE LEDGER

Ruta: `loops/r23h_analysis/CANONICAL_TRADES.csv`  
Una fila = **UNA operación real confirmada en cuenta DEMO**.

### Columnas (18)
1. `trade_id`: UUID único del trade generado por el motor / monitor.
2. `decision_id`: UUID de la decisión cuantitativa asociada.
3. `window`: Ventana de ejecución (`r23f_w01`..`r23f_w03`, `r23g_w01`..`r23g_w08`).
4. `phase`: Fase (`R23F` o `R23G`).
5. `timestamp`: Epoch milisegundos de la orden confirmada.
6. `asset`: Par operado (`Z-CRY/IDX`).
7. `action`: `BUY` o `SELL`.
8. `entry`: Precio de entrada registrado en tick / orden.
9. `stake`: Monto arriesgado en COP (nominalmente 100.000 COP).
10. `payout`: Payout del broker (nominalmente 0.82).
11. `result`: `WIN`, `LOSS` o `VOID`.
12. `balance_before`: Balance DEMO inmediatamente antes de la deducción.
13. `balance_after`: Balance DEMO inmediatamente después de la acreditación / débito final.
14. `market_regime`: Régimen de mercado detectado (`TRENDING`, `SIDEWAYS`, `CHOP`, etc.).
15. `trend`: Dirección de tendencia detectada (`UPTREND`, `DOWNTREND`, `NEUTRAL`).
16. `adaptive_similarity`: Similitud calculada por el motor anti-patrón (0.0 a 1.0).
17. `score`: Puntuación de confluencia técnica asignada (pts).
18. `source_file`: Archivo fuente primario de donde se extrajo (`window_XX/trades.csv`, `events.jsonl`, etc.).

### Reglas de Depuración y Exclusión
- Excluir filas con estado `END root`.
- Excluir órdenes en estado `ENVIADA` sin confirmación de tap/balance.
- Excluir eventos con estado `NINGUNA`.
- Excluir taps de calibración o UI sin trade subyacente.
- Excluir duplicados de `trade_id` (cada trade debe aparecer exactamente una vez).
- Cada trade debe tener exactamente un único resultado (`WIN`, `LOSS` o `VOID`).
- Validar delta de balance:
  - $\text{WIN} \implies \Delta \text{balance} \approx +\text{stake} \times \text{payout}$ ($+82.000\text{ COP}$).
  - $\text{LOSS} \implies \Delta \text{balance} \approx -\text{stake}$ ($-100.000\text{ COP}$).
  - $\text{VOID} \implies \Delta \text{balance} \approx 0\text{ COP}$.

---

## 3. ASIGNACIÓN DE ROLES EN PANELES HERDR (FASE 0)

De acuerdo a la nueva regla obligatoria de Herdr, toda delegación se realiza hacia los paneles interactivos visibles:

### 1. HERMES (`hermes` / `w1:pA`)
- **Misión:** Auditoría de software del monitor (`scripts/validation/live_monitor_r23_autonomous.py`), `trades.csv` y lógica de resolución W/L.
- **Entregables:**
  - Identificar la causa raíz exacta de la discrepancia aritmética en los reportes de R23G.
  - Implementar script determinista de extracción y compilación de `CANONICAL_TRADES.csv`.
  - Generar informe técnico en `workflows/rounds/r23h_hermes_audit.md`.

### 2. OPENCODE (`opencode` / `w1:pB`)
- **Misión:** Auditoría forense de artefactos brutos en todas las ventanas de R23F (w01-w03) y R23G (w01-w08).
- **Entregables:**
  - Extraer y cruzar `trades.csv`, `events.jsonl`, `status_samples.jsonl` y `window_metadata.json`.
  - Contrastar cada trade reportado contra el salto de balance real en telemetría.
  - Generar `loops/r23h_analysis/OPENCODE_WINDOW_AUDIT.md`.

### 3. CLAUDE CODE (`claude` / `w1:pC`)
- **Misión:** Auditoría estadística independiente y validación de integridad del canonical ledger.
- **Entregables:**
  - Análisis independiente de conteos brutos vs canonical ledger.
  - Verificación matemática de la ecuación $N = W + L + VOID$.
  - Recálculo independiente de métricas (WR, EV, Wilson Score IC 95% para BUY y SELL).
  - Emisión de veredicto formal (`CONFIRMED` o `DATA_INCONSISTENT`) en `workflows/rounds/R23H_CLAUDE_AUDIT.md`.
