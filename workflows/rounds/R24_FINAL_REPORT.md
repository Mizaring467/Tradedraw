# INFORME FINAL R24 · TradeDraw

**Fecha:** 2026-10-22 (America/Bogota)
**Rama:** `r10-night`
**T0 de la sesión:** 2026-10-22T15:39:40-05:00 · **tope 6 h:** 21:39:40
**Ejecutor:** Antigravity 2.0 con subagentes (ORQUESTADOR + IMPLEMENTADOR + AUDITOR + VALIDADOR)
**Estado global:** **PARCIAL** — Fases 0, A y B cerradas; Fases C, D y E parciales por tope de sesión y por dos incidentes de operación.

---

## 1. Estado por fase

| Fase | Alcance | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| **0** Arranque seguro | Serial ADB, forward, `/status`, modo seguro, T0 | **PASS-AUTOR** | `workflows/R24_PROGRESO.md` (cabecera) |
| **A** Cubo 2 + higiene + informe R23K | A1–A6 | **PASS-AUTOR** (firma independiente **REVOCADA**, ver §2.1) | `workflows/r24_tasks/A1_result.md`, `AUDIT_A_result.md`, `workflows/rounds/R23K_FINAL_REPORT.md` |
| **B** APK R24 | B0–B6 | **PASS-AUTOR** | `6fc51f4`, `7be78e8`, `7ec019a`, `543996a`, `0a728e5`, `69046d2`, `64f78f1`, `df1673a` |
| **C** Ventanas exploratorias | hasta 6 × 20 min | **NO EJECUTADA** | tope de sesión + preregistro exploratorio inexistente |
| **D** Instalar R24 + 4 ventanas | V1–V5 | **NO EJECUTADA** | requiere toque de `MediaProjection` (usuario) y ≥ 4 × 20 min |
| **E** Cierre | informes, push | **PASS-AUTOR** | este documento + `R24_PARA_REVISION_EXTERNA.md` |

---

## 2. Subagentes y modelos

| Rol | Subagente | Modelo | Resultado |
| :--- | :--- | :--- | :--- |
| ORQUESTADOR | agente principal | — | Coordinación, verificación contra evidencia, 6 refutaciones de afirmaciones de subagentes |
| IMPLEMENTADOR | `e277e71a-b7a1-4068-a442-22ad820ca03f` (`DeepCoder`) | `inherit` | A4, A5, B1, B2, B3, B4 |
| AUDITOR | `922d40a6-d927-4133-b161-d93631a31fae` (`DeepCoder`) | `inherit` | Firma Fase A (14 puntos, 13 PASS / 1 FAIL condicionado) |
| VALIDADOR | `81457cf9-88ca-4768-9f72-5be712715e9f` (`DeepInvestigator`) | `inherit` | A1 recálculo independiente |
| VALIDADOR B1 | `7d69150a` | `inherit` | **DESCARTADO**: colgado tras Gradle sin escribir resultado |
| AUDITOR v1/v2 | `2199e4dc`, `8b750adb` | `inherit` | **DESCARTADOS**: violaron regla 0.5 (crearon subagentes) |

**Limitación declarada (regla 0.3):** **MISMO MODELO** para IMPLEMENTADOR y AUDITOR. El selector de subagentes solo expone variantes de tamaño (`inherit`, `flash_lite`, `flash`, `pro`), no proveedores distintos. La independencia se garantiza por **contexto limpio** y **recálculo propio**, no por modelo.

### 2.1 ENMIENDA · La firma independiente de la Fase A está REVOCADA

**Fecha de la enmienda:** 2026-10-22T22:56-05:00 (posterior al cierre declarado; se anexa, no se reescribe el cuerpo).

La versión inicial de este informe afirmaba «A: PASS-INDEPENDIENTE» citando el commit `82f7289`. **Esa afirmación es falsa en el estado actual del repositorio.**

Secuencia real de `workflows/r24_tasks/AUDIT_A_result.md`:

| Commit | SHA auditado | Firma emitida |
| :--- | :--- | :--- |
| `82f7289` | `dd56136` | PASS-INDEPENDIENTE |
| `4f609f3` | `6fc51f4` | **FAIL — no se firma** |

La re-auditoría de `4f609f3` **sustituyó** el PASS por un FAIL sobre el código ya corregido. El archivo en disco hoy termina en `FAIL — no se firma`. El orquestador citó el commit antiguo sin verificar el estado vigente del archivo: **error propio O-8**.

**Puntos del FAIL:**

| # | Punto | Estado actual |
| :--- | :--- | :--- |
| 9 | Doble conteo W/L alcanzable por ruta manual | **CORREGIDO** en `7be78e8`; test `rutaManualYAutomaticaConMismoTradeIdCuentanUnaVez` PASS (9/9 en `B1_verify_result.md`) |
| 13 | Preregistro R23K posterior a W04 y W07 | **IRREPARABLE** — hecho histórico. Ya declarado en `fde4a07` (Cubo 2 = EXPLORATORIO) |

**Consecuencia:** la Fase A **no tiene firma independiente vigente**. Su estado real es **PASS-AUTOR**. El contenido (cifras, cierre del Cubo 2, higiene, informe R23K) no cambia: lo que falta es la firma, no el trabajo. Un revisor externo debe emitirla o rechazarla.

**Hallazgo de herramienta:** el subagente VALIDADOR `81457cf9` (`DeepInvestigator`) quedó en bucle infinito de reintento tras un error permanente `503 No configured model is currently available` en su 8º hijo. `DeepInvestigator` es un «pipeline coordinator» que delega siempre y no tiene tope de reintentos. Para trabajo que deba escribir archivos: usar `DeepCoder`.

---

## 3. Fase A — Cubo 2 cerrado

### 3.1 Cifras canónicas (de `scripts/validation/recompute_cubes.py`, verificadas por el VALIDADOR y recalculadas por el ORQUESTADOR)

| Métrica | Cubo 2 (`8cdb61f`, W04–W22) |
| :--- | :--- |
| N / resueltos | 60 / 59 |
| W / L / VOID | 30 / 29 / 1 |
| WR | **50,85 %** |
| Wilson 95 % | **[38,44 ; 63,16]** |
| Breakeven (payout 83 %) | **54,64 %** |
| EV | **−0,0710 R** [−0,3002 ; +0,1567] |
| P&L (balances) | −427.000,32 COP |
| Max DD | 1.260.999,68 COP (3,1729 %) |

### 3.2 Veredicto literal del Cubo 2

> **SIN EVIDENCIA DE EDGE** (WR cercano al breakeven; punto estimado negativo; N insuficiente para concluir).

**Nota sobre el texto del script.** `recompute_cubes.py:652-661` imprime
`NO CONCLUYENTE (N=60 < 40 y límite inf Wilson 38.44% <= BE 54.64%)`. La condición «N=60 < 40» es **falsa**:
el ternario emite el mensaje de «N insuficiente» siempre que `edge_detected` sea falso, sin comprobar la
condición. La conclusión (no hay edge) es correcta; el motivo impreso no. Defecto cosmético detectado por el
VALIDADOR en `workflows/r24_tasks/A1_result.md:225`, **no corregido** (el script es el canónico congelado y
tocarlo invalidaría la comparación con el Cubo 2).

### 3.3 Futilidad demostrada (enmienda A2, `8e2d149`)

Con W=30 sobre 59 resueltos, N=80 deja 21 huecos. Aunque los 21 fueran ganadores: 51/80 → Wilson inferior **52,81 % < 54,64 %**. El criterio B2 es **matemáticamente inalcanzable** a N=80. Sin tope de N harían falta **25 aciertos consecutivos** (55/84 → 54,83 %).

### 3.4 Hallazgos de la Fase A

| ID | Hallazgo | Consecuencia |
| :--- | :--- | :--- |
| H-2 | El Cubo 2 mezcla **dos builds**: W04–W06 → `aac1df3`; W07–W22 → `8cdb61f`. `git diff --stat 8cdb61f 190f596 -- app/` = vacío; 11 constantes idénticas → refactor puro | El cubo es homogéneo en comportamiento |
| H-5 | El preregistro R23K (`38e54e8`, 19-oct 01:22:57) es **posterior** a 8 de 19 ventanas (W04 = 18-oct 20:08:26) | El Cubo 2 es **EXPLORATORIO**, no confirmatorio (`fde4a07`) |
| H-6 | La paridad depende del umbral: cruce real en **4500 ms**, no 6000 ms. El preregistro nunca fija la tolerancia | El «PASS 90,91 %» es frágil |
| H-7 | W06 irreproducible: fresh `2/2/0` vs commiteado `2/1/1` | Ventana no auditable |
| H-8 | `trades.csv` **no tiene columna `result`**; se deriva de la cadena de saldos. VOID = movimiento **+100.000 COP positivo** | Corrige 2 errores previos del orquestador |

### 3.5 Cobertura del Cubo 1

22 pares / 80 trades Live = **27,50 %** de cobertura. 20 matches → 90,91 % [72,19 ; 97,47]. **NO EVALUABLE** como criterio de paridad por muestra insuficiente.

---

## 4. Fase B — APK R24

| Campo | Valor |
| :--- | :--- |
| Ruta | `app/build/outputs/apk/debug/app-debug.apk` |
| Tamaño | 13.820.761 B |
| SHA-256 | `6E1B068483515F282A535B1755762C0535DD0750A7E18E67C027FDAC431B0A35` |
| versionCode / versionName | 19 / `1.9-r23h-exhaustion` |
| Commit | `64f78f1` |
| Suite | **507 tests, 0 fallos, 0 errores** |
| `test_decisions_sr_columns.py` | 11/11 PASS |
| `live_monitor_r23_autonomous.py --test` | PASS 4/4 |
| Instalación | **NO INSTALADO** |

### 4.1 Cambios (lista cerrada, `workflows/PREREGISTRO_R24.md` §5)

1. **B1** idempotencia de liquidación: `settleTradeOnce`, `settleManual`, `markLiquidated`, `tradesLossSinEvidencia` (`AtomicInteger`, fuera de las 5 funciones de reset). `/status` expone `tradesLossSinEvidencia`, `manualWins`, `manualLosses`, `manualIncrements`. **Cambio de comportamiento confirmado** (evita Stop Loss prematuro por doble registro).
2. **B2** columnas S/R: `DECISION_FIELDNAMES` a 16 columnas (`live_monitor_r23.py:29-33`), `ratio_celda` con `math.isfinite`.
3. **B3** endpoint `/adaptive_state` (solo lectura).
4. **B4** diagnóstico NO_PROCESADA: `noProcesadaDuracionMaxMs = maxOf(maxCerrada, actualMs)`.

**Estrategia congelada:** verificado — ninguna señal, ponderación, veto, umbral, stake ni `RiskManager` modificado.

---

## 5. V1–V5 — estado

| Criterio | Estado | Motivo |
| :--- | :--- | :--- |
| **V1** Contadores | **NO EVALUABLE** | No hubo ventana R24. Además el APK R24 no está instalado. |
| **V2** S/R exportados | **NO EVALUABLE** | Implementado (B2), sin ventana que lo mida. |
| **V3** Memoria adaptativa | **NO EVALUABLE** | Implementado (B3), sin ventana. |
| **V4** NO_PROCESADA | **NO EVALUABLE** | Implementado (B4), sin ocurrencia registrada en R24. |
| **V5** Paridad v2 | **NO EVALUABLE** | Sin ventanas; mínimos de muestra (`>= 20` trades, `>= 200` decisiones) no alcanzados. |

**Ningún criterio se declara PASS ni FAIL.** `NO EVALUABLE` es un resultado válido y distinto de `FALLA` (preregistro §3).

---

## 6. Incidentes de operación (fuera de ventana)

Dos activaciones de `AUTONOMOUS` desde la **UI del overlay**, sin monitor ni ventana, con trades reales en cuenta Demo.

| | INCIDENTE 1 | INCIDENTE 2 |
| :--- | :--- | :--- |
| Activación | 2026-10-22 15:28:59 (`ui:showAutonomousSubModeDialog`) | 2026-10-22 **17:31:17** (`ui:showAutonomousSubModeDialog`) |
| Contención | 15:40:30 (`bridge:/mode`) | 20:46:47 (`bridge:/mode`) |
| Exposición | 11 min 31 s | **3 h 15 min 30 s** |
| Trades | **1** (SELL LOSS) | **38** (18W / 20L / 0T, WR 47,37 %) |
| P&L | −100.001,28 COP | **−511.000 COP** |
| Documento | `workflows/R24_RECONCILIACION_FUERA_DE_VENTANA.md` | `workflows/R24_INCIDENTE_2_FUERA_DE_VENTANA.md` |

**Balance:** 38.176.737,28 → 38.076.736,00 (Inc. 1) → **37.565.736,96 COP** (Inc. 2).

**Total fuera de ventana: 39 trades, −611.001,28 COP.** Excluidos de todos los cubos con la etiqueta «fuera de ventana».

**Causa raíz común:** activación humana desde la UI. **Fallo de proceso del orquestador (Incidente 2):** no se sondeó `/status` entre 17:31 y 20:46 durante la Fase B. Registrado como error propio.

---

## 7. Errores propios del ORQUESTADOR (autocontrol)

| # | Error | Corrección |
| :--- | :--- | :--- |
| O-1 | «VOID es débito» | Es +100.000 COP positivo (`recompute_cubes.py:270`) |
| O-2 | «LOSS por defecto → NO EVALUABLE» citando el `else` externo | Solo la rama interna (289-292) |
| O-3 | Acepté «6000 ms post hoc» sin verificar | `577a9fa` (18-oct 20:04) precede W07–W22 |
| O-4 | «Doble registro confirmado» en `TradingEngine:637-639` | FALSO: es `if (!resolved)` fallback guardado |
| O-5 | 3 funciones de reset | Son 5 (`resetStats:897`, `setStats:907`, `correctStats:917`, `resetSession:983`, `resetSniperSession:1003`) |
| O-6 | «PASS depende de 6000 ms» | Cruce real en 4500 ms |
| O-7 | **No sondeé `/status` durante 3 h 15 min en Fase B** | INCIDENTE 2, −511.000 COP. Corregido con contención inmediata y regla de sondeo ≤ 10 min |
| O-8 | **Declaré «Fase A: PASS-INDEPENDIENTE» citando `82f7289` sin comprobar el archivo vigente** | La re-auditoría `4f609f3` lo revocó con FAIL. Corregido en §2.1 |

**Lección:** leer números de línea sin leer el flujo de control produce hallazgos falsos. Y vigilar el modo del motor es parte del trabajo, no un extra.

---

## 8. NO VERIFICADO

1. **Causa de las 6 ventanas con 0–1 trades** (W10=0, W15=0, W12=1, W14=1, W17=1, W22=1). No atribuida.
2. **Causa de W17 `NO_PROCESADA`** (~9,5 min, saldo inmóvil). H3 (FEED_MUDO) refutada (feed 13–337 ms). Taxonomía B4 implementada pero **sin ejecutar**.
3. **Si `totalWins`/`totalLosses` se inflaban** en `8cdb61f`. El journal vivo (WIN 136 / LOSS 128 / TIE 6 = 270) no muestra inflado ×2 global; el inflado es condicional y de magnitud desconocida. B1 lo corrige pero **no se ha medido su efecto**.
4. **Paridad v2 real** con S/R exportados y memoria volcada. Arnés ciego según `R24_MANIFEST.md` §6.
5. **W06**: fresh `2/2/0` vs commiteado `2/1/1`. Sin causa.
6. **La corrección B1 en dispositivo real.** 507 tests verdes no equivalen a comportamiento verificado en el teléfono.
7. **`/adaptive_state` en producción.** Endpoint compilado, nunca consultado desde el dispositivo.
8. **`journalDuplicatesRejected=22`** observado en `/status` con el build `8cdb61f`: no se investigó si esos 22 son el defecto o el filtro funcionando.

---

## 9. RIESGOS RESIDUALES

1. **El motor puede quedar en `AUTONOMOUS` sin ventana.** Dos veces en esta sesión, 39 trades, −611.001 COP. No hay salvaguarda automática: depende de que un humano o el orquestador lo apague. **Riesgo alto.**
2. **El balance Demo ya no coincide con el cierre de W22.** Cualquier comparación futura debe partir de 37.565.736,96 COP.
3. **El Cubo 2 es exploratorio.** Su veredicto no es confirmatorio por preregistro tardío.
4. **El arnés v2 sigue ciego** sin los insumos del dispositivo (S/R reales, volcado de memoria). B2/B3 los instrumentan pero no se ha probado end-to-end.
5. **`logd` se cae durante el uso** en este dispositivo. Sin `logcat`, la observabilidad depende de `/events` y `/mode_log`, cuyo ring se satura de eventos `ASSET` (200/200 en W17).
6. **`duration_sec` del journal no es evidencia** (regla 13), pero sigue presente y es tentador usarlo.
7. **Un solo teléfono, carga media ~15.** Cualquier ventana futura compite con la CPU del dispositivo.

---

## 10. Estado del repositorio

- HEAD: `5c6d1d2`. Rama `r10-night`. Árbol limpio.
- **Sin push.** `origin/r10-night` está ~30 commits por detrás.
- `.lock_window` ausente. Sin Gradle corriendo.
- Basura versionada por error en el pasado: ninguna pendiente; `.gitignore` actualizado en `1307913` y `7d46fb0`.

## 11. Estado del dispositivo

| Campo | Valor |
| :--- | :--- |
| Serial | `[fe80::e8c5:80ff:fef0:d5f9%4]:5555` |
| Forward | `tcp:8080 → tcp:8080` activo |
| `mode` | **`DISABLED`** |
| `orderState` | `NINGUNA` |
| `hasPendingTrade` | `false` |
| `isDemoAccount` | **`true`** |
| `gitSha` | `8cdb61f` (build instalada; R24 **no instalado**) |
| Balance | **37.565.736,96 COP** |
| Batería | ~89 %, sin cargar (última medición) |

---

## 12. Qué falta para cerrar la misión

1. **Instalar el APK R24** (`adb install -r app/build/outputs/apk/debug/app-debug.apk`) con el usuario presente para conceder `MediaProjection`.
2. **Verificar la condición de arranque** del `R24_MANIFEST.md` §5 con medición.
3. **Ejecutar 4 ventanas de validación** de 20 min (regla de parada, máximo 4, fijado a priori).
4. **Evaluar V1–V5** con `scripts/validation/recompute_cubes.py` y el comparador congelado.
5. **Sondear `/status` cada ≤ 10 min durante todo el proceso**, no solo en ventanas.

---

**Firma:** PASS-AUTOR (ORQUESTADOR, agente principal). **No hay PASS-INDEPENDIENTE de este informe ni de la Fase A**: la firma que el AUDITOR emitió en `82f7289` fue **revocada** por su propia re-auditoría en `4f609f3` (FAIL, puntos 9 y 13). Ver §2.1. Ningún subagente revisó este documento.
