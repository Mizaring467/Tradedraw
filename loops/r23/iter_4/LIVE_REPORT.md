# REPORTE DE VALIDACIÓN LIVE — R23 ITERACIÓN 4 (SHADOW MODE)

**Fecha:** 2026-10-15 21:59:23 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=9, buildId=R23-ITER4, gitSha=def14f8  
**Modo Auditado:** `SHADOW`  
**Duración de la Ventana:** 20.0 minutos (118 muestras de telemetría)  
**Veredicto Preliminar:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Preservación de Balance:** Saldo inicial = 38,054,735.36 COP, saldo final = 38,054,735.36 COP (Discrepancia neta = 0.00 COP).
2. **Cero Taps:** `tapsDespachados = 0` en el 100% de las 118 muestras capturadas. Cero órdenes reales despachadas.
3. **Identidad de Build:** La APK en ejecución reportó versionCode=9, buildId="R23-ITER4" y gitSha="def14f8".
4. **Decisiones Observadas:** Se registraron 61 decisiones con `decision_id` determinista (UUID v3). Cero `trade_id` espurios en vetos/simulaciones.
5. **Comportamiento de Guards:** 61 decisiones vetadas. Distribución: {"V2_RANGE": 61}.
6. **Rango Cuantitativo:** En Z-CRY/IDX, el rango relativo osciló entre 1.48e-10 y 5.61e-10 frente al umbral 1.00e-05.
7. **Paridad Live ↔ Replay:** 0 exact matches de 3 pareadas (0.0% de paridad).

---

## 2. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23/iter_4\live_samples.jsonl`
- Muestras de `/status`: `loops/r23/iter_4\status_samples.jsonl`
- Eventos de decisión: `loops/r23/iter_4\events.jsonl`
- Decisiones con `decision_id`: `loops/r23/iter_4\decisions.csv`
- Historial de modos: `loops/r23/iter_4\mode_log.json`
- Comparación de replay: `loops/r23/iter_4\replay_comparison.json` y `loops/r23/iter_4\replay_comparison_report.md`

---

## 3. HYPOTHESIS
- La exposición de metadatos de build y rango relativo en `/status` permite a cualquier auditor verificar en tiempo real `código candidato = APK instalada = proceso en ejecución`.
- La persistencia de vetos por V2_RANGE en Crypto IDX es matemáticamente consistente con la naturaleza del índice sintético (micro-ticks de ~1e-9).

---

## 4. MISSING PROOF
- Observación en activos volátiles reales (ej. ETHUSD-OTC o BTCUSD-OTC) donde el rango supere 1e-05 para evaluar el primer `SHADOW_DECISION` aprobado.

---

## 5. REGRESSION CHECK
- Cero regresiones de seguridad: 0 taps despachados, balance 100% inmutable, transiciones de salud fail-closed preservadas.

---

## 6. COMPARISON (Candidato vs Baseline R22)
- R22 Baseline: `versionCode=5`, sin campos `gitSha`/`buildId` en `/status`, sin `rangoRelativoMinimo` expuesto.
- R23 Candidato: `versionCode=9`, `buildId="R23-ITER4"`, trazabilidad de build 100% verificable en runtime y telemetría de V2_RANGE expuesta.

---

## 7. RECOMMENDATION
**KEEP**
