# PREREGISTRO R23C FASE 3 — Refinamiento Asimétrico de Filtros SELL en Submodo YOLO (DEMO)

**Fecha de fijación:** 2026-10-16  
**Rama:** `r10-night`  
**Build congelada:** `e3fdba4` | `versionCode = 15` | `versionName = "1.5-r23c-phase3"` | `BUILD_ID = "R23C-PHASE3"`  
**Firmante:** AGY (Orquestador R23 / TradeDraw)  
**Implementador:** HERMES (`w1:p6`)  
**Validador Live:** OpenCode (`w1:pT`)  

---

## 1. Delimitación Epistemológica y Estado de la Hipótesis

### 1.1. Antecedente Empírico (Fase 1: 6 Ventanas, N=23)
En la Fase 1 bajo baseline `ac079fd`, se observó:
- **BUY:** $N=9$ | $5\text{ W} / 4\text{ L}$ | $WR = 55.56\%$ (IC95% Wilson: $[26.7\%, 81.1\%]$)
- **SELL:** $N=14$ | $6\text{ W} / 8\text{ L}$ | $WR = 42.86\%$ (IC95% Wilson: $[21.4\%, 67.4\%]$)
- **Subconjunto Confirmado por Balance ($n=16$):**
  - BUY: $n=5$ | $4\text{ W} / 1\text{ L}$ | $WR = 80.0\%$
  - SELL: $n=11$ | $2\text{ W} / 9\text{ L}$ | $WR = 18.2\%$ (IC95%: $[5.1\%, 47.7\%]$, excluye break-even $54.95\%$)

### 1.2. Advertencia Metodológica (Fijada en Auditoría de Hermes)
- En la muestra agregada $N=23$, la diferencia de 12.7 pp ($z=0.60, p=0.55$) **no es estadísticamente significativa** debido al tamaño muestral reducido.
- Por tanto, este endurecimiento direccional se declara formalmente como una **HIPÓTESIS EXPLORATORIA DIRIGIDA POR EVIDENCIA**, no como una optimización confirmada a priori.
- Cualquier variación en la tasa de acierto en las ventanas de Fase 3 debe contrastarse contra la banda de varianza muestral antes de declararla definitiva.

---

## 2. Parámetros Congelados de la Calibración (Inmutables Durante la Fase)

| Parámetro | Submodo YOLO (Antes) | Submodo YOLO (Fase 3 Congelado) | Modos Restantes |
|---|---|---|---|
| **Confluencia Mínima (BUY)** | 75 pts | **75 pts** (intacto) | 80 pts |
| **Confluencia Mínima (SELL)** | 75 pts | **80 pts** (endurecido) | 80 pts |
| **Mecha Contra-Tendencia (BUY)** | 30% | **30%** (intacto) | 50% |
| **Mecha Contra-Tendencia (SELL)** | 30% | **45%** (endurecido) | 50% |
| **Mecha Rechazo `MT_REJECTION` (CALL/BUY)** | 30% | **30%** (intacto) | 45% |
| **Mecha Rechazo `MT_REJECTION` (PUT/SELL)** | 30% | **40%** (endurecido) | 45% |

---

## 3. Plan de Medición y Criterios de Parada

1. **Serie de Validación:** 3 ventanas continuas de 20 minutos cada una (Ventanas 01, 02 y 03) en `loops/r23c_phase3_validation/`.
2. **Entorno de Ejecución:** Exclusivamente **CUENTA DEMO** en Xiaomi Redmi A1 (`220733SL`) sobre activo `Z-CRY/IDX`.
3. **Métricas a Contrastar:**
   - ¿Se reduce la sobre-representación de ventas ($60.9\%$ en Fase 1)?
   - ¿Asciende el $WR$ de SELL por encima de su baseline previo ($42.86\%$ global / $18.2\%$ confirmado)?
   - ¿Mantiene BUY su rendimiento rentable ($> 54.95\%$)?
   - ¿Qué impacto tiene sobre la frecuencia total de operaciones ($N$)?
4. **Condición de Aborto Inmediato (Fail-Closed):**
   - Si `isDemoAccount != true` en cualquier segundo.
   - Si se detecta desbalance de capital real o error crítico de runtime.
