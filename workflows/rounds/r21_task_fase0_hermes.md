# TAREA HERMES: FASE 0 — AUDITORÍA READ-ONLY DEL INCIDENTE DE BALANCE (+80.000 COP)

**Fecha:** 2026-10-14  
**Asignado a:** HERMES (`w1:p6`)  
**Orquestador:** AGY (`w1:p1`)  
**Modo:** 100% READ-ONLY (ESTRICTAMENTE PROHIBIDO EJECUTAR TAPS, OPERACIONES O MODIFICAR CÓDIGO)

---

## 1. Contexto del Incidente
En la verificación final de R20 (dispositivo Redmi A1 conectado), el balance de la cuenta DEMO cambió de:
`37.974.735,36` → `38.054.735,36`  
Diferencia neta observada: **+80.000 COP**.

Hechos confirmados hasta ahora:
1. El bot NO ejecutó ninguna orden en la ventana (`tapsDespachados=0`, `W/L=0/0`).
2. El guard de desajuste vetó 61 intentos de disparo (`vetoActivoDesajustado=61`, todas `DESAJUSTE`).
3. El journal persistido `trade_journal.csv` no tiene filas del 14-Oct.
4. Los `WIN` habituales del bot acreditan +80.000 brutos pero neto real es +70.000 (retención 12.5%).
5. El cambio se observó tanto en `/status`, `/nodes` como en la captura de pantalla (`loops/screenshots/r20_balance.png`).

---

## 2. Objetivo de Hermes
Realizar una investigación forense profunda y exhaustiva, de **SOLO LECTURA**, para determinar la trazabilidad y procedencia del cambio de balance.

### Elementos a inspeccionar:
1. **Journals locales y externos:**
   - `app/src/main/assets/` o directorios de datos en dispositivo (`/sdcard/TradeDraw/` o similar).
   - `trade_journal.csv` y cualquier archivo de journal histórico o de respaldo.
   - Revisar `mtime` exacto (timestamp de modificación) de cada archivo de journal.
2. **Logs y Ring de Modos:**
   - Inspeccionar el archivo JSONL de `MODE_CHANGE` persistido en disco/dispositivo (`mode_changes.jsonl` o similar).
   - Consultar `/mode_log` y `/mode_journal` vía HTTP bridge (`curl.exe -s http://127.0.0.1:8080/mode_log`).
   - Consultar `/events` (`curl.exe -s http://127.0.0.1:8080/events`).
   - Consultar `/status` (`curl.exe -s http://127.0.0.1:8080/status`).
3. **Procesos y Ciclo de Vida en el Teléfono (ADB read-only):**
   - Comprobar si `OverlayService` fue recreado o si el proceso de TradeDraw (`com.example.tradedraw`) murió y reinició en esa ventana (ver PID y `ps` en adb).
   - Timestamps de procesos y logs del sistema si están disponibles.
   - Cualquier archivo de log o historial local existente en `/sdcard/` relacionado con TradeDraw o Binomo.
4. **Historial de balance:**
   - Verificar si existe algún registro de `balance_before` / `balance_after` en SharedPreferences o logs de debug.

---

## 3. Restricciones Críticas (Reglas de Exclusión Mutua)
- **Archivos que PUEDES crear/modificar:**
  ÚNICA Y EXCLUSIVAMENTE: `workflows/rounds/r21_balance_incident_hermes.md`.
- **Archivos que NO PUEDES TOCAR:**
  - NINGÚN archivo Kotlin (`*.kt`) ni Java.
  - NINGÚN archivo de Gradle ni configuración.
  - NINGÚN journal existente ni archivo en disco del repo.
  - `workflows/PREREGISTRO_*.md`, `workflows/R21_COORDINATION.md`.
- **Dispositivo:**
  - PROHIBIDO enviar broadcasts que cambien de modo.
  - PROHIBIDO ejecutar taps (`input tap`), swipes, o tocar botones de la interfaz.

---

## 4. Entregable Requerido
Crear el archivo:
`workflows/rounds/r21_balance_incident_hermes.md`

Estructura obligatoria del reporte:
1. **Resumen Ejecutivo**
2. **Tabla de Evidencia Forense Recolectada** (fuente, comando/archivo, valor observado)
3. **Análisis de Coherencia de Datos**
   - Separación estricta de:
     - `FACT` (hechos verificados sin lugar a duda)
     - `EVIDENCE` (datos brutos medidos que sustentan los hechos)
     - `HYPOTHESIS` (explicaciones plausibles pero no demostradas)
     - `MISSING PROOF` (pruebas que faltan para concluir)
4. **Clasificación Final Obligatoria (Elegir exactamente una):**
   - **A) ATRIBUIDO:** Existe prueba irrefutable de que fue causado por X (bot, acción humana, etc.).
   - **B) NO ATRIBUIDO PERO ACOTADO:** No se puede demostrar el agente causante exacto, pero se descartan tajantemente ciertas vías y se acota la ventana temporal y vector.
   - **C) EVIDENCIA INSUFICIENTE:** La evidencia recolectada no permite acotar ni atribuir el cambio.

---

## 5. Criterio PASS/FAIL
- **PASS:** El entregable `workflows/rounds/r21_balance_incident_hermes.md` está completamente redactado con la evidencia analizada, estructura formal, clasificación A, B o C, y se mantuvo integridad 100% read-only sin alterar el dispositivo ni el repo.
- **FAIL:** Se ejecutan modificaciones no autorizadas, taps en dispositivo, o se emite una conclusión sin sustentar la separación FACT/EVIDENCE/HYPOTHESIS/MISSING PROOF.
