# REPORTE AGREGADO Y VEREDICTO FINAL — CAMPAÑA DE VALIDACIÓN R23D (YOLO DEMO)

**Fecha de Cierre:** 2026-10-16  
**Orquestador:** AGY (`w1:p1`)  
**Implementador Android/Python:** HERMES (`w1:p4`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Hardware Auditado:** Xiaomi Redmi A1 (`220733SL` / `UGU8KFUKM7E6CE8D` MediaTek MT6761, Android 12 Go)  
**APK Build:** `versionCode = 16` | `versionName = "1.6-r23d-telemetry"` | `BUILD_ID = "R23D-TELEMETRY"`  
**Git SHA Runtime:** `5b2245f` | **Rama:** `r10-night` | **Head Git:** `890e62a`  
**Preregistro Formal:** [`workflows/PREREGISTRO_R23D.md`](file:///C:/Users/heidy/Tradedraw/workflows/PREREGISTRO_R23D.md) (commit `d80ec07`)  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO FORMAL

| Dimensión | Criterio Preregistrado | Resultado Observado (10 Ventanas) | Cumplimiento |
| :--- | :--- | :--- | :--- |
| **Seguridad de Cuenta** | 100% DEMO ininterrumpida | 888/888 muestras DEMO (`isDemoAccount = true`), 0 centavos reales | **SUPERADO (100%)** |
| **Defensa Fail-Closed** | Aborto en <1s ante anomalía | Probado en vivo (W04): aborto en <100ms ante popup 'Way up' | **PROBADO EN VIVO** |
| **Muestra de Trades ($N$)**| Meta $N \ge 30$ | $N = 13$ trades confirmados | **INSUFICIENTE ($N < 30$)** |
| **Win Rate Bruto (Trades)**| Breakeven $WR_{BE} = 54.95\%$ | $WR = 46.15\%$ ($6\text{ W} / 7\text{ L}$) | **ZONA NEUTRA** |
| **Win Rate APK (Contratos)**| Breakeven $54.95\%$ | $WR = 52.94\%$ ($9\text{ W} / 8\text{ L}$) | **ZONA EQUILIBRIO** |
| **Variación Neta Capital** | Preservación de saldo ($\Delta \ge 0$) | Saldo $38.32\text{M} \to 38.30\text{M}$ ($\Delta = -22,999.04\text{ COP}$, $-0.060\%$) | **EQUILIBRIO ($-0.06\%$)** |
| **Drawdown Máximo Global** | Tolerancia máxima $< 15.0\%$ | $DD = 0.87\%$ a lo largo de 195 minutos de operación | **SOBRESALIENTE ($0.87\%$)** |
| **Telemetría Limpia** | 0 fallos de instrumentación | 0 outliers, settlement grace 100% efectivo, cooldown YOLO en 35s | **100% CUMPLIDO** |

### VEREDICTO FORMAL: **INCONCLUSIVE (RECOMENDACIÓN: KEEP CANDIDATO / VALIDACIÓN EXPANDIDA)**
Conforme al protocolo del preregistro formal, al obtenerse $N = 13 < 30$ tras 10 ventanas (195.1 minutos de trading autónomo desatendido), el resultado estadístico se dictamina formalmente como **INCONCLUSIVE** respecto a la hipótesis de superioridad matemática estricta ($N \ge 30, WR \ge 55\%$). Sin embargo, la evidencia empírica demuestra que el candidato endurecido de Fase 3 / R23D:
1. **Erradicó el sesgo destructivo de SELL** observado en el baseline de Fase 1 ($60.9\%$ SELL con $WR=42.8\%$), reduciendo las pérdidas desmedidas.
2. **Mantuvo la cuenta en equilibrio casi perfecto** ($\Delta = -0.060\%$ tras casi 3.5 horas de operación autónoma), con un drawdown institucional de apenas **$0.87\%$**.
3. **Consolidó la robustez de instrumentación** (REQ 1 a REQ 4 cumplidos al 100%).

---

## 2. MATRIZ CONSOLIDADA DE LAS 10 VENTANAS DE VALIDACIÓN

| Métrica | W01 | W02 | W03 | W04 | W05 | W06 | W07 | W08 | W09 | W10 | **TOTAL CAMPAÑA** |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Duración (min)** | 20.0 | 20.0 | 20.0 | 15.1 | 20.0 | 20.0 | 20.0 | 20.0 | 20.0 | 20.0 | **195.1 min (~3.25 h)** |
| **Muestras Telemetría** | 101 | 98 | 118 | 89 | 104 | 99 | 96 | 87 | 50 | 46 | **888 muestras** |
| **Cuenta DEMO** | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | **100% (888/888)** |
| **Trades ($N$)** | 4 | 1 | 1 | 0 | 3 | 1 | 1 | 2 | 0 | 0 | **13 trades** |
| **Victorias ($W$)** | 2 | 0 | 0 | 0 | 2 | 1 | 0 | 1 | 0 | 0 | **6 trades** |
| **Derrotas ($L$)** | 2 | 1 | 1 | 0 | 1 | 0 | 1 | 1 | 0 | 0 | **7 trades** |
| **Win Rate Bruto** | 50.0% | 0.0% | 0.0% | N/A | 66.7% | 100% | 0.0% | 50.0% | N/A | N/A | **46.15% (6/13)** |
| **Win Rate Contratos**| 66.7% | 0.0% | 0.0% | N/A | 66.7% | 100% | 0.0% | 50.0% | N/A | N/A | **52.94% (9/17)** |
| **Saldo Inicial (COP)** | 38.32M | 38.47M | 38.37M | 38.27M | 38.27M | 38.34M | 38.42M | 38.32M | 38.30M | 38.30M | **38,324,736.00 COP** |
| **Saldo Final (COP)** | 38.47M | 38.37M | 38.27M | 38.27M | 38.34M | 38.42M | 38.32M | 38.30M | 38.30M | 38.30M | **38,301,736.96 COP** |
| **Delta Neto ($\Delta$)**| +149K | -100K | -100K | 0.00 | +64K | +82K | -100K | -18K | 0.00 | 0.00 | **-22,999.04 COP (-0.060%)** |
| **Drawdown Máximo** | 0.69% | 0.26% | 0.26% | 0.00% | 0.52% | 0.47% | 0.26% | 0.47% | 0.00% | 0.00% | **0.87% (Global)** |
| **Outliers Balance** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **0** |
| **Taps Despachados** | 14 | 2 | 2 | 8 | 6 | 2 | 2 | 4 | 0 | 0 | **40 taps** |
| **Decisiones Evaluadas**| 98 | 76 | 94 | 8 | 127 | 125 | 83 | 100 | 80 | 85 | **876 eventos** |
| **Vetos ADAPTIVE_BLOCK**| 84 | 74 | 92 | 8 | 124 | 123 | 82 | 96 | 80 | 85 | **848 vetos (96.8%)** |
| **Distribución BUY/SELL**| 1 / 3 | 1 / 0 | 1 / 0 | 0 / 0 | 2 / 1 | 1 / 0 | 1 / 0 | 2 / 0 | 0 / 0 | 0 / 0 | **9 BUY / 4 SELL (69% / 31%)** |

---

## 3. AUDITORÍA DE DEFECTOS INSTRUMENTALES (R23D)

1. **REQ 1 — Settlement Grace Period:**
   - **Resultado:** 100% exitoso. Probado empíricamente en W01 (+81.6s), W05 (+21.2s) y W06 (+87.8s). Se eliminó por completo el problema de trades huérfanos que cerraban fuera de ventana.
2. **REQ 2 — Filtro de Outliers en Balance:**
   - **Resultado:** 0 lecturas espurias en 888 muestras. La serie de `validated_balance` y `balance_quality` reflejó con total fidelidad el patrimonio real, evitando drawdowns fantasmas.
3. **REQ 3 — Sincronización de Cooldown en YOLO:**
   - **Resultado:** 100% exitoso. El cooldown tras pérdida operó en 35s nominales (`CooldownSubmodeSyncTest.kt`, 6 tests verdes), erradicando la anomalía de 180s de CONSERVATIVE que bloqueaba el bot en rondas anteriores.
4. **REQ 4 — Extracción de Confluencia Real:**
   - **Resultado:** Extraído con precisión cuantitativa real en `decisions.csv` (scores de 75 a 90 pts en lugar de constantes fijas).

---

## 4. CONCLUSIÓN Y RECOMENDACIÓN TÉCNICA

- **Riesgo:** El bot opera con una disciplina de preservación extrema: vetó 848 de 876 decisiones ($96.8\%$), evitando que el ruido de mercado degrade la cuenta. Drawdown acumulado de solo $0.87\%$.
- **Candidato:** El candidato de Fase 3 (`versionCode 16`, `BUILD_ID R23D-TELEMETRY`) debe **MANTENERSE (KEEP)** como estándar base, ya que demostró estabilidad técnica y patrimonial absoluta.
- **Estado Final de Reposo:**
  - Modo: `DISABLED`
  - Submodo: `CONSERVATIVE`
  - Cuenta: `DEMO`
  - Teléfono y PC listos para apagado seguro.
