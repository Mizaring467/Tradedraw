# AUDIT_D_DOCS — Consistencia Documental y Auditoría de Evidencia (TradeDraw)

**Fecha:** 2026-10-27 / 2026-10-28  
**Repositorio inspeccionado:** `/data/data/com.termux/files/home/Tradedraw` (Solo Lectura)  
**Rama activa:** `r10-night` (HEAD: `75f005e`)

---

## 1. Revisión de Documentos Clave (archivo:línea y citas textuales)

### 1.1 `workflows/R24_PROGRESO.md`
- **Línea 6:**
  > `**Rama:** r10-night · HEAD al inicio: 190f596 · build medida: 8cdb61f (versionCode 19)`
- **Línea 139:**
  > `- Fases C y D: **NO EJECUTADAS**. Declaradas PENDIENTES por tope de sesion y decision del usuario.`
- **Línea 143:**
  > `## ENMIENDA 2026-10-22T22:57-05:00 — Firma de Fase A REVOCADA (error propio O-8)`
- **Línea 158:**
  > `## ENMIENDA 2026-10-26T23:58:00-05:00 — Cierre de Fase C/D de R24 como NO EVALUABLE por caida de forward; transicion a R25`
  *Demuestra la trazabilidad de por qué R24 no ejecutó ventanas y cómo derivó en R25.*

### 1.2 `workflows/rounds/R24_FINAL_REPORT.md`
- **Línea 7:**
  > `**Estado global:** **PARCIAL** — Fases 0, A y B cerradas; Fases C, D y E parciales por tope de sesión y por dos incidentes de operación.`
- **Líneas 16–19:**
  > `| **A** Cubo 2 + higiene + informe R23K | A1–A6 | **PASS-AUTOR** (firma independiente **REVOCADA**, ver §2.1) | ... |`  
  > `| **C** Ventanas exploratorias | hasta 6 × 20 min | **NO EJECUTADA** | tope de sesión + preregistro exploratorio inexistente |`  
  > `| **D** Instalar R24 + 4 ventanas | V1–V5 | **NO EJECUTADA** | requiere toque de MediaProjection (usuario) y ≥ 4 × 20 min |`

### 1.3 `loops/r25_validation/R25_CIERRE.md`
- **Líneas 35–37:**
  > `> **V1–V4: PASS en las 3 ventanas válidas. V5: NO EVALUABLE por muestra insuficiente.**`  
  > `> Según PREREGISTRO_R25.md §7, PASS-INSTRUMENTACIÓN exige además V5 evaluable, por lo que el veredicto formal del cubo es **NO EVALUABLE (por V5)**, no PASS.`
- **Líneas 49–50:**
  > `- **Caducidad de AUTONOMOUS** si falta heartbeat del bridge (decisión 6 de R24): el intento 1 de W02 dejó el motor armado ~5 min sin red. No ocurrió ninguna operación, pero el mecanismo sigue ausente.`  
  > `- **V5 requiere volumen**: hace falta una ronda con ≥20 trades live ... preregistrada como R26.`
- **Líneas 58–60 (Adenda):**
  > `## ADENDA 2026-10-27 22:26 — el pendiente (a) queda cerrado en R26`  
  > `La caducidad de AUTONOMOUS que §5 declaraba ausente **ya está implementada y certificada en vivo**:`

### 1.4 `workflows/PREREGISTRO_R26.md`
- **Línea 3:**
  > `**Fecha de fijación:** 2026-10-27 22:20 (America/Bogota) — escrito **antes** de abrir cualquier ventana de R26`
- **Líneas 25–27:**
  > `> **CERTIFICAR QUE AUTONOMOUS ARMADO DESDE EL BRIDGE CADUCA SOLO CUANDO DEJA DE HABER CONTACTO, Y QUE NO CADUCA CUANDO SÍ LO HAY.**`  
  > `Cierra la decisión 6 de R24, pendiente desde el 2026-10-22 y reabierta por el incidente W02 de R25 ...`

### 1.5 `loops/r26_validation/R26_REPORTE.md`
- **Línea 82:**
  > `| Suite Kotlin completa | **513/513 PASS** = 507 de R24/R25 + 1 de la anomalía (f97abfa) + 5 del watchdog (f491ca4) |`
- **Líneas 128–130:**
  > `- Armado por bridge a las **22:01:38.172** (source=bridge:/mode).`  
  > `- Caducidad observada a las **22:12:08.187** → **silencio = 630,0 s = 10 min 30 s** ...`

---

## 2. Aritmética de Tests (513 = 507 + 1 + 5 vs XML en disco)

- **Afirmación:** `R26_REPORTE.md:82` desglosa:
  - 507 tests base de R24/R25 (reportados en commits `64f78f1` y `df1673a`).
  - +1 test de regresión de la anomalía de conteo en `f97abfa` (`victoriaNoDuplicaContadorDeSieteANueve`).
  - +5 tests de `AutonomousWatchdogTest` en `f491ca4`.
  - Total teórico: 513.
- **Inspección de artefactos XML (`app/build/test-results/testDebugUnitTest/*.xml`):**
  - Se localiza **únicamente 1 archivo XML**:
    `app/build/test-results/testDebugUnitTest/TEST-com.example.tradedraw.LiquidationIdempotencyTest.xml`
  - Contenido exacto del archivo:
    `<testsuite name="com.example.tradedraw.LiquidationIdempotencyTest" tests="10" skipped="0" failures="0" errors="0" ...>`
  - Suma total de tests en XMLs existentes: **10 tests**.
- **Veredicto:** **NO VERIFICABLE en disco**. La suma de 513 es una deducción aritmética documental heredada, pero no existe la evidencia de ejecución consolidada de la suite completa de 513 pruebas en los reportes XML del directorio `app/build/test-results/testDebugUnitTest/` en este entorno.

---

## 3. Discrepancia «9 pruebas Kotlin PASS» vs Versión Actual (10 vs 9)

- **En reporte previo (`workflows/r24_tasks/B1_verify_result.md:31`):**
  Se afirmaba: `Ejecución aislada, 9/9 PASS` para `LiquidationIdempotencyTest`.
- **Conteo en commit previo (`f97abfa^:app/src/test/java/com/example/tradedraw/LiquidationIdempotencyTest.kt`):**
  - Conteo de anotaciones `@Test`: **9**.
- **Conteo en versión actual (`f97abfa` y `HEAD`):**
  - Conteo de anotaciones `@Test`: **10**.
- **Explicación de la diferencia:**
  En el commit `f97abfa` (`test(r26): verifica que una victoria no duplica el contador (7->8, nunca 7->9)`), se agregó la prueba:
  `@Test fun victoriaNoDuplicaContadorDeSieteANueve()` (líneas 227–248).
  Esta décima prueba verifica explícitamente que tras una resolución manual (`settleManual`) el fallback del motor (`settleTradeOnce`) descarte el segundo incremento, manteniendo el marcador en 8 y no en 9.

---

## 4. Resolución de Enlaces Relativos

### 4.1 En `loops/r25_validation/R25_CIERRE.md`
- **Línea 65 (Adenda):** `[loops/r26_validation/R26_REPORTE.md](../r26_validation/R26_REPORTE.md)`
  - Directorio base: `loops/r25_validation/`
  - Ruta resuelta: `loops/r26_validation/R26_REPORTE.md`
  - **Estado:** **EXISTE**.
- **Línea 65 (Adenda):** `[workflows/PREREGISTRO_R26.md](../../workflows/PREREGISTRO_R26.md)`
  - Directorio base: `loops/r25_validation/`
  - Ruta resuelta: `workflows/PREREGISTRO_R26.md`
  - **Estado:** **EXISTE**.

### 4.2 En `loops/r26_validation/R26_REPORTE.md`
- **Línea 8:** `[workflows/PREREGISTRO_R26.md](../../workflows/PREREGISTRO_R26.md)`
  - Directorio base: `loops/r26_validation/`
  - Ruta resuelta: `workflows/PREREGISTRO_R26.md`
  - **Estado:** **EXISTE**.
- **Rutas mencionadas en texto plano / tablas (no enlaces MD estándar):**
  - Línea 16: `loops/R1x_REPORTE.md` → **ROTO / NO EXISTE**.
  - Línea 18: `loops/R21_REPORTE.md` → **ROTO / NO EXISTE** (los informes reales de R21 residen bajo `workflows/rounds/r21_*`).
  - Línea 19: `loops/R22_REPORTE.md` → **ROTO / NO EXISTE**.
  - Líneas 129 y 140: `scripts/validation/r26_p1_silence.sh` y `scripts/validation/r26_p2_heartbeat.sh` → Si se resuelven contra el directorio del reporte (`loops/r26_validation/scripts/...`), no existen; existen únicamente relativos a la raíz del repositorio.

---

## 5. PASS sin Evidencia y Contradicciones Temporales / de Estado

1. **Inconsistencia temporal crítica en el Preregistro R26:**
   - `workflows/PREREGISTRO_R26.md:3` declara:  
     `Fecha de fijación: 2026-10-27 22:20 (America/Bogota) — escrito antes de abrir cualquier ventana de R26`.
   - `loops/r26_validation/R26_REPORTE.md:128-130` reporta:  
     La prueba en vivo P1 inició a las **22:01:38** y concluyó a las **22:12:08**.
   - **Contradicción:** La prueba P1 ocurrió entre 8 y 19 minutos **antes** de la hora de fijación declarada para el preregistro, violando la regla de oro estipulada en el §0 de dicho documento ("escrito antes de observar un solo dato").
2. **Afirmaciones PASS sin artefactos verificables:**
   - La tabla de `R26_REPORTE.md` cita `PASS (loops/R21_REPORTE.md)` y `PASS (loops/R22_REPORTE.md)` cuyos archivos destino no existen en el árbol.
   - El resultado `513/513 PASS` en Kotlin carece de reportes XML completos en `app/build/test-results/`.
3. **Estado del pendiente estructural de R25:**
   - `R25_CIERRE.md §5` listaba la caducidad como pendiente estructural ("el mecanismo sigue ausente"). Esto fue enmendado mediante la adenda de las 22:26 enlazando a `R26_REPORTE.md`, mitigando la contradicción directa dentro de R25, pero el segundo pendiente (V5 con ≥ 20 trades) sigue abierto.

---

## 6. `main` vs `r10-night` y Contradicción con `AGENTS.md`

- **Instrucción en `AGENTS.md` (líneas 28–31, 193):**
  > `Regla de Oro para Agentes (Flujo Directo a main): NO abrir Pull Requests innecesarios ... commit y push DIRECTAMENTE a la rama main.`
- **Criterio en `workflows/tradedraw-loop.md` (línea 24):**
  > `Push a main: solo ante avance significativo (PASS de hipótesis con evidencia + build verde). El orquestador lo decide y lo reporta.`
- **Estado real en Git:**
  - Rama `main`: congelada en el commit `4d433ed` (`docs(r20): verificacion EN VIVO del guard...`).
  - Rama de trabajo activa: `r10-night` (HEAD: `75f005e`), donde se han acumulado todos los desarrollos de R21, R22, R23, R24, R25 y R26.
  - `main` **no posee** ninguno de los commits de R25 ni R26.
- **Veredicto:** Existe una contradicción directa y no resuelta entre la política de `AGENTS.md` (que exige flujo directo a `main`) y la práctica operativa real del proyecto (confinada a `r10-night` y restringida por `tradedraw-loop.md`).

---

## 7. Higiene del Repositorio

- **`README.md`:** **Completamente ausente** en la raíz del proyecto (`ls README*` retorna código 2).
- **`.gitignore`:**
  - Contiene reglas para ignorar `backups/` (línea 92), `data/null/`, logs, capturas y archivos de runtime.
- **`backups/`:** Directorio presente localmente; ignorado correctamente por git gracias a la regla en `.gitignore:92`.
- **Artefactos sueltos en raíz:**
  - Existen más de **430 archivos PNG** versionados y rastreados directamente en git dentro del repositorio, muchos de ellos en la raíz (ej. `audit_cycle_*.png`, `comp_*.png`, `screen_*.png`).
  - Existen scripts sueltos en raíz (`_probe_asset_volatility.js`, `_probe_binomo_ws.js`).
  - Archivos de informe de fases previas acumulados en la raíz (`AUDIT_REPORT.md`, `FUTURE_HYPOTHESES.md`, `LOSS_ANALYSIS_REPORT.md`, `VALIDATION_REPORT.md`, `LOSS_FINDINGS.json`, `LOSS_DIAGNOSTIC_DATA.csv`), compitiendo con la jerarquía modular definida bajo `loops/` y `workflows/`.
