# INFORME POST-HOC DE AUDITORÍA CONTRAFACTUAL: VENTANA 01

**Fecha de Ejecución:** 2026-10-16 22:06:15 UTC  
**Fecha de Reprocesamiento:** 2026-10-16 23:14:00 UTC  
**Base git del reprocesamiento:** `957ce13` (con extractor y fallback de acción)  
**Fuente de Telemetría:** `decisions.csv` (81 decisiones completas), `ticks_live.csv` (4.324 ticks), `status_samples.jsonl` (91 muestras)  
**Artefacto generado:** `loops/r23e_adaptive_audit/window_01/counterfactual_adaptive_block.csv`  

---

## 1. RESUMEN DE LA POBLACIÓN CONTRAFACTUAL

| Métrica | Valor | Notas |
| :--- | :---: | :--- |
| **Vetos Totales en Bruto (`ADAPTIVE_BLOCK`)** | **75** | Capturados íntegros en `decisions.csv` |
| **Vetos con Acción Identificada** | **75 (100.0%)** | Resueltos mediante correlación temporal con `engine.lastSignalAction` |
| **Contrafactuales Deduplicados** | **9** | Ventana de no-solapamiento de 60s por dirección (1 por vela/dirección) |
| **Contrafactuales Resueltos (con tick a 60s)** | **8** | Precios de entrada y salida exactos a 8 decimales |
| **Contrafactuales Anulados (`VOID`)** | **1** | Señal en $t = 1792189505514$ (a 60s del corte nominal; sin tick posterior) |
| **Wins Contrafactuales (`CF_W`)** | **4** | |
| **Losses Contrafactuales (`CF_L`)** | **4** | |
| **Win Rate Contrafactual (`CF_WR`)** | **50.00%** | Frente a Breakeven teórico $WR_{BE} = 54.95\%$ (payout = 82%) |
| **EV Teórico Acumulado (`CF_EV`)** | **-72,000.00 COP** | Stake nominal 100.000 COP por unidad contrafactual |

---

## 2. DESGLOSE POR DIRECCIÓN (BUY vs. SELL)

| Dirección | $N$ Deduplicados | Resueltos | $CF\_W$ | $CF\_L$ | $CF\_VOID$ | $CF\_WR$ | $CF\_EV$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **BUY** | 6 | 5 | 3 | 2 | 1 | **60.00%** | **+46,000.00 COP** |
| **SELL** | 3 | 3 | 1 | 2 | 0 | **33.33%** | **-118,000.00 COP** |
| **TOTAL** | **9** | **8** | **4** | **4** | **1** | **50.00%** | **-72,000.00 COP** |

---

## 3. SEGMENTACIÓN POR SIMILITUD DE CONTEXTO

| Bucket Similitud | $N$ Resueltos | $CF\_W$ | $CF\_L$ | $CF\_WR$ | $CF\_EV$ | Observación |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **98%** | 3 | 3 | 0 | **100.00%** | **+246,000.00 COP** | 1 SELL y 2 BUY ganadores |
| **99%** | 5 | 1 | 4 | **20.00%** | **-318,000.00 COP** | 2 SELL loss, 2 BUY loss, 1 BUY win |

---

## 4. SEGMENTACIÓN POR SCORE DE CONFLUENCIA

| Bucket Confluencia | $N$ Resueltos | $CF\_W$ | $CF\_L$ | $CF\_WR$ |
| :--- | :---: | :---: | :---: | :---: |
| **70 – 79 pts** | 1 | 0 | 1 | 0.00% |
| **80 – 89 pts** | 2 | 2 | 0 | 100.00% |
| **90 – 100 pts** | 5 | 2 | 3 | 40.00% |

---

## 5. TABLA COMPARATIVA: REALES VS. CONTRAFACTUALES (VENTANA 01)

| Métrica | Trade Real Aceptado | Contrafactuales Vetados (`ADAPTIVE_BLOCK`) |
| :--- | :---: | :---: |
| **Operaciones ($N$)** | 1 | 8 resueltos (1 void) |
| **Taps Despachados** | 2 | **0 (Cero)** |
| **Variación de Balance** | -18.001,92 COP | **0.00 COP (Cero mutación)** |
| **Wins / Losses** | 0 W / 1 L | 4 W / 4 L |
| **Win Rate** | **0.0%** | **50.00%** |
| **EV / Resultado** | Negativo | **-72.000 COP** |

---

## 6. CONCLUSIÓN DE VENTANA 01

1. **Aislamiento y Seguridad:** 100% verificado. Cero taps y cero riesgo de saldo durante la deduplicación contrafactual.
2. **Efectividad del Guard:** En términos globales ($WR = 50.0\%$ vs $54.95\%$), `ADAPTIVE_BLOCK` evitó un EV negativo neto de -72.000 COP.
3. **Señal de Discrepancia por Similitud:** En el subconjunto de similitud **98%**, las 3 señales habrían sido ganadoras (+246.000 COP), mientras que en similitud **99%** el veto fue categóricamente protector ($WR = 20.0\%$, -318.000 COP).
