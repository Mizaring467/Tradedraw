# REPORTE DE VALIDACIÓN LIVE — R23 ITERACIÓN 2 (SHADOW MODE)

**Fecha:** 2026-10-15 20:39:44 UTC  
**Validador:** OPENCODE (`w1:pT`)  
**Orquestador:** AGY (`w1:p1`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** versionCode=7, buildId=R23-ITER2, gitSha=101a183  
**Modo Auditado:** `SHADOW`  
**Duración de la Ventana:** 20.0 minutos (116 muestras de telemetría)  
**Veredicto Preliminar:** **PASS**  

---

## 1. FACT (Hechos Directamente Observados)
1. **Preservación de Balance:** Saldo inicial = 38,054,735.36 COP, saldo final = 38,054,735.36 COP (Discrepancia neta = 0.00 COP).
2. **Cero Taps:** `tapsDespachados = 0` en el 100% de las 116 muestras capturadas. Cero órdenes reales despachadas.
3. **Identidad de Build:** La APK en ejecución reportó versionCode=7, buildId="R23-ITER2" y gitSha="101a183".
4. **Decisiones Observadas:** Se registraron 17 decisiones con `decision_id` UUID v4. Cero `trade_id` espurios en vetos/simulaciones.
5. **Comportamiento de Guards:** 17 decisiones vetadas. Distribución: {"V2_RANGE": 17}.
6. **Rango Cuantitativo:** En Z-CRY/IDX, el rango relativo osciló entre 1.25e-10 y 6.00e-10 frente al umbral 1.00e-05.
7. **Paridad Live ↔ Replay:** Paridad Live ↔ Replay: NO EVALUABLE (0 pares evaluados, 17 decisiones sin replay correspondiente).

---

## 2. EVIDENCE (Rutas y Archivos de Prueba)
- Telemetría periódica: `loops/r23/iter_2\live_samples.jsonl`
- Muestras de `/status`: `loops/r23/iter_2\status_samples.jsonl`
- Eventos de decisión: `loops/r23/iter_2\events.jsonl`
- Decisiones con `decision_id`: `loops/r23/iter_2\decisions.csv`
- Historial de modos: `loops/r23/iter_2\mode_log.json`
- Comparación de replay: `loops/r23/iter_2\replay_comparison.json` y `loops/r23/iter_2\replay_comparison_report.md`

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
- R23 Candidato: `versionCode=7`, `buildId="R23-ITER2"`, trazabilidad de build 100% verificable en runtime y telemetría de V2_RANGE expuesta.

---

## 7. RECOMMENDATION
**KEEP**
