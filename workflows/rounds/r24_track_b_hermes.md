# R24 TRACK B — REPLAY v2: INFRAESTRUCTURA ADAPTIVE_BLOCK / SETTLEMENT / V1

**Ejecutor:** HERMES (`w1:pA`, Implementador de Infraestructura)
**Fecha:** 2026-10-19
**SHA base:** `34f8fdb`
**Alcance:** `scripts/validation/replay_v2_tick.py` + tests. **Cero cambios en `app/`.**
**Estrategia:** CONGELADA. Ningún umbral, peso, confluencia, veto, stake ni política de trading tocado.

---

## 0. VEREDICTO EN UNA LÍNEA

B1–B4 implementados y verificados (54 tests + 61 vectores de self-test). El replay ya **reproduce
`ADAPTIVE_BLOCK` con la memoria real** y su **orden de guards coincide con el Live**, pero **la
concordancia de tipo NO alcanza el 90%** porque las dos piezas que quedan dependen de datos que hoy
**no existen en los artefactos**: la serie de distancias S/R reales (`--sr-context`) y un journal del
motor por ventana. Sin esos dos insumos el arnés está **correcto por construcción pero ciego**, y así
lo declara su sidecar. **No re-congelar el SHA como "arnés listo": no lo está.**

---

## 1. CAMBIOS (ANTES / DESPUÉS)

| # | Pieza | Antes | Ahora |
|---|---|---|---|
| B1 | Memoria ADAPTIVE_BLOCK | `memoria_perdidas` arrancaba **siempre vacía** y solo se llenaba con trades que el propio replay marcaba como perdidos — que nunca ocurría. `ADAPTIVE_BLOCK`: **0 de 57** en W11. | Carga `adaptive_learning_state.json` (`--adaptive-state` / `ADAPTIVE_STATE_JSON`) con las **mismas reglas de purga de `loadState`**: firmas genéricas en el 50% (`&&` de ds y dr con ±0.02) y TTL de 24 h **medido contra el inicio de la ventana**, no contra el reloj de pared. |
| B1 | Anti look-ahead | Inexistente. | Cada firma lleva `ts` y solo es visible para ticks con `ts >= ts_firma` (`memoria_visible`). Carga descarta firmas con `ts > inicio_ventana`. |
| B1 | Crecimiento de memoria | `resolver_vencidas` la alimentaba al vencer 60 s (reconstrucción). | Crece **solo al liquidar** un settlement con resultado `LOSS`, y la firma nace con el `ts` de **liquidación**, no el de entrada. |
| B2 | `lastTradeResolutionTime` | Se fijaba a `entrada + 60 s` (invento). | Se fija con el instante del **ledger real**: `--settlements` (CSV), `--settlements-journal` (journal del motor), `--settlements-from-samples` (transiciones `hasPendingTrade`). Sin ledger: **no se modela**, `cooldown_modelado=false`. |
| B2 | Cooldown | Se evaluaba junto con "orden abierta". | Guard **1** independiente, con `minSpacingMs` del motor: YOLO 8000 / racha>0 35000 / resto 12000. La racha se reconstruye del propio ledger (WIN resetea, LOSS suma, TIE conserva). |
| B3 | Compuerta V1 | **Ausente**. `V1`: 0 de 12 en W11. | Port literal de `HudCoherence.actionConflict` + `srStatus`/`plan` de `getEngineReasoning`, con el `detail` exacto `V1 SR="..." PLAN="..."`. Colocada entre la operación abierta y la entrada, igual que el Live. |
| B4 | Orden de guards | `RISK_LIMIT (orden abierta)` **antes** del adaptativo; cooldown mezclado con riesgo. | `ventana → COOLDOWN → agotamiento → ADAPTIVE_BLOCK → RISK_LIMIT → V1 → entrada`. |

### 1.1 Por qué el orden era la causa del 0%

El barrido W05–W11 reportó **23 casos** `ADAPTIVE_BLOCK (Live) → COOLDOWN (replay)` y **16**
`→ RISK_LIMIT`. Con la operación abierta evaluada antes del adaptativo, cualquier tick dentro de los
60 s de una orden salía como `RISK_LIMIT` aunque el Live lo hubiera vetado por anti-patrón en el
mismo instante. En el motor real `riskManager.canExecuteTrade` es el **paso 6** del headless, no el
guard de cooldown: son dos guards distintos que el replay había fusionado.

---

## 2. RESULTADO DE TESTS

### 2.1 Suite nueva (B4)

```
python scripts/validation/test_replay_v2_guards.py
Ran 54 tests — OK
```

| Bloque | Tests | Qué congelan |
|---|---|---|
| `TestB1MemoriaInicial` | 4 | sin memoria no bloquea / con memoria bloquea / el `detail` clasifica como `ADAPTIVE_BLOCK` en el comparador / no muta la lista del llamador |
| `TestB1CargaDesdeEstadoReal` | 6 | mapeo de campos, purga del 50% con **`&&`** (no `||`), borde, TTL contra inicio de ventana, determinismo |
| `TestB1SinLookAhead` | 5 | firma futura no veta / visibilidad por `ts` / recorte a 20 / purga de futuras en carga / memoria crece al liquidar y con `ts` de liquidación |
| `TestB2SettlementCooldown` | 11 | cooldown desde settlement / borde exacto 8000 ms / sin ledger no se reconstruye / cierre de orden abierta / `entry_ts` casa la orden correcta / racha / `TIE` conserva racha / idempotencia / `NO_PROCESADA` ignorada |
| `TestB2JournalDelMotor` | 4 | `timestamp` del journal = liquidación / descarta resultados desconocidos / **ignora `replay_journal.csv`** (circularidad) / mezcla ordenada |
| `TestB3CompuertaV1` | 12 | caso literal del dispositivo / inverso / coincidencia / neutral / plan con ambas direcciones / formato del `detail` / clasificación `V1` / cortes 0.15–0.30 / anti-chop solo fuera de YOLO / orden abierta nombra dirección |
| `TestB4OrdenDeGuards` | 7 | cada par consecutivo aislado: cooldown>agotamiento, agotamiento>adaptive, **adaptive>orden abierta**, abierta>V1, V1>entrada, fuera de ventana>todos, cadena limpia deja pasar |
| `TestDeterminismo` | 3 | dos corridas byte a byte idénticas / sidecar declara qué infraestructura actuó / esquema de salida estable |

### 2.2 Verificación por inyección (los tests muerden)

Cada defecto se inyectó, se corrió la suite y se restauró el archivo:

| Defecto inyectado | Resultado |
|---|---|
| Eliminar el guard de cooldown | **CAZA** — `test_cooldown_gana_al_veto_de_agotamiento` |
| Eliminar el guard de orden abierta | **CAZA** — `test_settlement_cierra_la_operacion_abierta...`, `test_operacion_abierta_gana_a_v1` |
| Quitar el filtro temporal de `memoria_visible` | **CAZA** — `test_firma_futura_no_entra_en_la_decision`, `test_memoria_visible_respeta_el_orden...` |
| Reconstruir cooldown con `entrada + 60 s` | **CAZA** — `test_sin_ledger_el_cooldown_no_se_reconstruye` |
| Quitar `!planWantsCall` del `actionConflict` | **CAZA** — `test_plan_que_nombra_ambas_direcciones_no_dispara` |
| `&&` → `\|\|` en la purga del 50% | **CAZA** — `test_purga_del_50_exige_AMBOS_ratios` |
| Firma de pérdida con `ts` de entrada | **CAZA** — `test_la_memoria_crece_al_liquidar_no_al_entrar` |
| Borde `<` → `<=` en el cooldown de 8000 ms | **CAZA** — `test_cooldown_yolo_bloquea_dentro_de_8s...` |

**Desviación de método declarada:** el borde numérico `<` vs `<=` de la purga del 50% **no es
fijable con floats** (`0.52 - 0.50 = 0.020000000000000018`, así que ambos operadores dan el mismo
veredicto). Se sustituyó por el defecto que sí discrimina: la conjunción `&&` frente a `||`. Queda
escrito en el docstring del test.

### 2.3 Suites existentes (sin regresión)

| Comando | Antes | Ahora |
|---|---|---|
| `replay_v2_tick.py --self-test` | PASS (37 vectores) | **PASS (61 vectores)** — +24 de B1–B4 |
| `compare_live_replay_v2.py --self-test` | PASS | **PASS** (sin cambios) |
| `test_action_telemetry.py` | PASS (8/8) | **PASS (8/8)** |

---

## 3. EVIDENCIA DE PUNTA A PUNTA SOBRE VENTANA REAL (W11)

**B1 — la memoria dispara el bloqueo.** Misma firma, misma ventana, solo cambia la visibilidad:

| Corrida | `vetos_adaptive_block` | entradas |
|---|---|---|
| memoria vacía | 0 | 21 |
| memoria con la firma del primer tick evaluado, visible desde `inicio` | **409** | **0** |

**B2 — el reloj de cooldown** (W11, 1 settlement derivado de `live_samples`):

| Ledger | `settlements_aplicados` | `vencidas_sin_settlement` |
|---|---|---|
| sin ledger | 0 | 10 |
| con ledger | 1 | 9 |

En W11 el settlement cae en `s=1792386735736`, segundos `:16–:23`. **Ningún tick de ese intervalo está
en la ventana sniper**, así que el cooldown no llega a vetar nada: `vetos_cooldown = 0` es el resultado
correcto, no un fallo. El guard está probado en los tests con timestamps sintéticos.

**B3 — V1 no dispara en las ventanas porque no hay insumo.** Ver §5.

---

## 4. SWEEP W05–W11 ANTES / DESPUÉS (concordancia de tipo)

| Ventana | Antes | Ahora | Δ |
|---|---|---|---|
| W05 | 15,38% | **58,97%** | +43,59 |
| W06 | 84,75% | **86,44%** | +1,69 |
| W07 | 48,08% | **40,38%** | −7,70 |
| W08 | 21,74% | **36,23%** | +14,49 |
| W09 | 73,08% | **79,49%** | +6,41 |
| W10 | 58,97% | **58,97%** | 0,00 |
| W11 | 22,92% | **37,50%** | +14,58 |

### 4.1 Desglose por tipo (reproducciones Live, `old` → `new`)

| Ventana | Tipo | Live | old | new |
|---|---|---|---|---|
| W05 | `RISK_LIMIT` | 45 | 6 | **23** |
| W08 | `RISK_LIMIT` | 16 | 9 | **15** |
| W08 | `VETO_BUY_EXHAUSTION_CLIMAX` | 15 | 6 | **10** |
| W09 | `RISK_LIMIT` | 23 | 11 | **22** |
| W11 | `VETO_BUY_EXHAUSTION_CLIMAX` | 31 | 18 | **29** |
| W11 | `RISK_LIMIT` | 8 | 4 | **7** |
| **todas** | `ADAPTIVE_BLOCK` | **234** | **0** | **0** |
| **W11** | `V1` | 12 | 0 | **0** |

### 4.2 Las dos caídas y por qué NO son una regresión

- **W07 (−7,70):** `COOLDOWN` pasa de 4 reproducidos a **0**. Antes el replay vetaba por cooldown
  usando el reloj inventado (`entrada + 60 s`); ese veto **coincidía por casualidad** con el Live en
  las mismas ventanas de tick. Ahora, sin ledger en W07, el cooldown no se modela y la fila cae a
  entrada o a otro veto. Es la sustitución de un acierto espurio por una ausencia declarada. Lo mismo
  en W09 (COOLDOWN 6 → 0). **Un acierto por coincidencia de reloj inventado no es paridad.**
- **`ADAPTIVE_BLOCK` sigue en 0, y no es un defecto del código:** ver §5.1. El cableado está probado
  (§3), el insumo no existe.

---

## 5. LO QUE FALTA Y NO SE PUEDE RESOLVER DESDE EL REPLAY

Es el hallazgo central de esta ronda. Está **medido**, no supuesto.

### 5.1 Sin `--sr-context` no hay `ADAPTIVE_BLOCK` reproducible

El replay evalúa con `dist_sup = dist_res = 0.50` (placeholder). Las 41 firmas del teléfono tienen
`ds ≈ 0.000–0.065` / `dr ≈ 0.000–0.075`. **La similitud S/R contra un placeholder 0.5/0.5 es como
máximo ~0,66** (peso 0.30 con `1-|0.5-0.0|`), muy por debajo del umbral 0.99. Es aritmética, no un
bug: con distancias inventadas el anti-patrón **no puede** disparar, y forzarlo exigiría relajar el
umbral — prohibido por estar congelado.

### 5.2 Los ratios S/R **no son reconstruibles offline** (medido)

`live_samples.jsonl` publica `dynamicSupportPrice` / `dynamicResistancePrice`, pero **no** los ratios
normalizados por ATR que usa el motor. Intenté reconstruirlos desde los ticks (velas M1 + ATR-14 +
`atrNorm = max(atr*2, |precio|*1e-12)`, réplica de `SyntheticCandleEngine`):

| Muestra | `ds` reconstruido | `dr` reconstruido | Lo que el journal del dispositivo registró |
|---|---|---|---|
| W11 `s=1792386600916` | 0,995 | 1,000 | `0.14` / `0.65` (trade 1792386541598) |
| W11 `s=1792386601486` | 0,995 | 1,000 | `0.22` esperado por el `srStatus` del propio Live |

**La reconstrucción no es fiel.** El motor usa `cachedAtr` de las velas cerradas con pivotes fractales
y polaridad dinámica; mis 0,995/1,0 donde el HUD del teléfono decía 14%/65% prueban que replicar esa
máquina offline introduce error de bulto. Por eso V1 **no se evalúa** sin `--sr-context`: inventar las
distancias produciría vetos `V1` que el Live nunca emitió.

**Corolario para el orquestador:** el build debe **exportar** `distanceToSupportRatio` y
`distanceToResistanceRatio` (2 floats por muestra) en `/status.analysis` o en una serie propia. Sin
eso, ni `ADAPTIVE_BLOCK` ni `V1` son reproducibles, por correcta que sea la infraestructura.

### 5.3 No hay journal del motor por ventana

`settlements_desde_journal` está implementado y probado, y su `timestamp` **es** el
`lastTradeResolutionTime` (verificado contra `TradeJournalLogger.buildLine`, que sella con
`currentTimeMillis()` en el bloque de settlement). Pero las 17 ventanas del repo traen
`replay_journal.csv` (del arnés) y **no** el `trade_journal_*.csv` del motor. El único global
(`loops/journal/trade_journal_device_all.csv`) termina en `1789238883156`, **muy anterior** a W12–W17
(`1792392…`). Con una sola transición `hasPendingTrade` por ventana, el cooldown queda modelado pero
**sin muestra**.

### 5.4 Las 41 firmas están fuera del TTL

`adaptive_learning_state.json` está fechado `2026-09-15/16`; las ventanas son de `2026-10-16/19`.
**41 de 41 firmas purgadas por TTL de 24 h** contra el inicio de ventana — correcto según `loadState`,
y aun así significa que **hoy la memoria real está vacía para cualquier ventana del repo**. Un replay
de una ventana nueva sí las tendría (si se exporta el state el mismo día).

---

## 6. RESTRICCIONES VERIFICADAS (no "afirmadas")

| Restricción | Prueba |
|---|---|
| **Estrategia congelada** | `git show HEAD:…/replay_v2_tick.py` vs árbol: `ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD=0.99`, `VETO_BUY_UPTREND_MAX_CALL_POWER=70`, `YOLO_COOLDOWN_MS=8_000`, `CONSERVATIVE_COOLDOWN_MS=35_000`, `DEFAULT_COOLDOWN_MS=12_000`, `TRADE_DURATION_MS=60_000`, `SNIPER_WINDOWS=((0,5),(58,59))` — **idénticos**. |
| **`app/` intacto** | `git status --short -- app/` → **vacío**. `git diff --stat -- app/` → **vacío**. |
| **Comparador v2 congelado** | `compare_live_replay_v2.py` sin modificar (no aparece en `git status`); su `--self-test` sigue PASS. SHA `535db989…a9f9` del manifiesto R24 intacto. |
| **Umbrales nuevos = port literal, no calibración** | `SR_TOUCH_LIMIT=0.15` y `SR_NEAR_LIMIT=0.30` son los de `HudCoherence.TOUCH_RATIO_LIMIT` y `getEngineReasoning`; no son puntos de ajuste. |
| **Sin reloj de pared** | El TTL se mide contra `ventana_inicio_ms`. El determinismo está probado (2 corridas byte a byte). |
| **Sin red, sin azar** | `ejecutar_replay` no importa red ni `random`; el único `time` es el del intérprete. |

---

## 7. ARTEFACTOS

| Archivo | SHA-256 |
|---|---|
| `scripts/validation/replay_v2_tick.py` | `44f77d890142fffc1a9026b59beb2c2e4562a20d4548f25faa52b32734c60845` |
| `scripts/validation/test_replay_v2_guards.py` | `7a755d575b6b401657a16491ea051fbcf5ec529bbe270d61a125664372b0c39a` |

**El SHA del replay cambia: `a3f1c1d1…fd2` → `44f77d89…845`.** Toda medición previa del arnés v2
queda **obsoleta** y así debe etiquetarse en `R24_MANIFEST.md` §2 y §5 (línea `sha256sum
replay_v2_tick.py`).

### 7.1 Nuevo sidecar de salida

`replay_v2_stats.json` junto a `decisions_replay.csv`:

```json
{"contadores_estado": {"entradas": 15, "perdidas_agregadas_a_memoria": 0,
  "settlements_aplicados": 1, "vencidas_sin_settlement": 13},
 "cooldown_modelado": true, "filas": 3288, "memoria_inicial_firmas": 0,
 "settlements_en_ledger": 1, "submode": "YOLO", "ticks": 3288,
 "v1_evaluado": false, "vetos_adaptive_block": 0, "vetos_cooldown": 0, "vetos_v1": 0}
```

Sin él, un `0%` de `ADAPTIVE_BLOCK` es indistinguible de "memoria vacía" y un `0%` de `V1` de
"compuerta no evaluada". Es la diferencia entre `NO EVALUABLE` y `FALLA`, que el preregistro exige
distinguir.

---

## 8. DESVIACIONES NUMERADAS

1. **No se añadió `--sr-context` como obligatorio ni se reconstruyen los ratios.** El encargo pedía
   implementar la compuerta V1; se implementó, pero se niega a evaluarla con distancias inventadas.
   Medido: la reconstrucción offline da `ds≈0.995` donde el journal del dispositivo dice `0.14`.
   Implementarla con el ratio reconstruido habría producido vetos falsos. **Se prefiere un `0%`
   declarado a un `x%` fabricado.**
2. **El cooldown no se modela sin ledger.** El encargo pedía que el cooldown empezara "cuando Live
   resuelve". Sin fuente de resolución, la alternativa era `entrada + 60 s`, que es exactamente la
   reconstrucción artificial que se prohibía. `cooldown_modelado=false` y a la vista.
3. **`settlements_desde_journal` ignora `replay_journal.csv` por nombre.** Usarlo sería circular
   (el replay comparándose consigo mismo). Va con aviso en stdout y test propio.
4. **Añadido `--settlements-from-samples`, no pedido.** Es la única fuente de instante de liquidación
   disponible en las ventanas actuales. Mueve el reloj de cooldown pero **no** la memoria ni la racha
   (no trae desenlace). Declarado en el docstring y cubierto por dos tests.
5. **`_firma` ahora lleva `ts`.** Aditivo; las funciones puras portadas (`similitud_de_firmas`,
   `contexto_estrictamente_coincidente`, `mejor_coincidencia_en_antipatron`) **no se tocaron** y sus
   vectores dorados siguen PASS.

---

## 9. HANDOFF AL ORQUESTADOR

**Suyo (no lo toqué):**
- `workflows/R24_MANIFEST.md` — actualizar el SHA del replay (§2 y §5) y marcar obsoletas las
  mediciones previas. También el aviso de §6 del manifiesto: sigue siendo válido, con la causa
  corregida y el motivo restante cambiado (ahora es falta de insumo, no falta de código).
- `workflows/rounds/R23K_C4_PARIDAD_V2_INFORME.md` — etiquetar su §5 como "atendido en R24 Track B,
  puntos 1–3 implementados; 4 pendiente del insumo".
- Compilación/instalación: **no aplica**, `app/` está intacto. No hay nada que compilar.

**Del build (decisión de producto, no infraestructura):**
- Exportar `distanceToSupportRatio` / `distanceToResistanceRatio` reales por muestra (§5.2). **Es el
  desbloqueo de B1 y B3, y no lo puede resolver el replay.**
- Exportar el `trade_journal_*.csv` del motor por ventana (§5.3), o exponer `lastTradeResolutionTime`
  en `/status`.
- Exportar `adaptive_learning_state.json` **al cierre de cada ventana**, no con días de retraso (§5.4).

### Forma de invocación con los insumos completos

```
python scripts/validation/replay_v2_tick.py \
  --ticks   <ventana>/ticks_live.csv \
  --samples <ventana>/live_samples.jsonl \
  --adaptive-state <state_exportado_al_inicio_de_la_ventana>.json \
  --settlements-journal <ventana>/trade_journal.csv \
  --sr-context <ventana>/sr_context.csv \
  --out <salida>
```

`sr_context.csv` = `ts,dist_sup,dist_res[,trend]`.

---

## 10. ANCLAS INEQUÍVOCAS DE VERIFICACIÓN

```
sha256sum scripts/validation/replay_v2_tick.py      -> 44f77d890142fffc1a9026b59beb2c2e4562a20d4548f25faa52b32734c60845
sha256sum scripts/validation/test_replay_v2_guards.py -> 7a755d575b6b401657a16491ea051fbcf5ec529bbe270d61a125664372b0c39a
python scripts/validation/replay_v2_tick.py --self-test              -> RESULTADO: PASS (61 [OK])
python scripts/validation/test_replay_v2_guards.py                   -> Ran 54 tests — OK
python scripts/validation/compare_live_replay_v2.py --self-test      -> RESULTADO: PASS
python scripts/validation/test_action_telemetry.py                   -> RESULTADO: PASS (8/8)
git diff --stat -- app/                                              -> vacío
grep -n "^ADAPTIVE_BLOCK_SIMILARITY_THRESHOLD = 0.99" scripts/validation/replay_v2_tick.py  -> línea 79
```
