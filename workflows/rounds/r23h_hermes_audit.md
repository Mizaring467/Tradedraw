# R23H FASE 0 — AUDITORÍA DE SOFTWARE DEL MONITOR Y CANONICAL TRADES

**Panel:** HERMES (`w1:pA`)
**Fecha:** 2026-10-17
**Rama:** `r10-night`
**Modo:** auditoría estática + reconstrucción determinista offline. Cero ejecuciones contra el dispositivo. 100% cuenta DEMO.
**Alcance:** `scripts/validation/live_monitor_r23_autonomous.py`, `trades.csv`, `events.jsonl`, cómputo de W/L, y reconciliación de `loops/r23f_validation/` + `loops/r23g_validation/`.

---

## 0. VEREDICTO EN UNA LÍNEA

La discrepancia `N=32` vs `W+L=36` **no es un bug de conteo de un script**: es la suma de trades confirmados de una fuente (`trades.csv`) con una **estimación de resoluciones del motor** de otra fuente (contadores `totalWins`/`totalLosses` de `/status`), sin que ninguna de las dos fuera cerrada contra el settlement real de balance. Los reportes nunca verificaron `N = W + L`.

---

## 1. CAUSA RAÍZ EXACTA

### 1.1 Las tres fuentes que se mezclaron

| Fuente | Qué mide realmente | Cardinalidad R23G |
| :--- | :--- | ---: |
| `trades.csv` | Órdenes **ejecutadas** con `CONFIRMADA <ACCION> saldo movido` | 34 filas (33 únicas) |
| `replay_journal.csv` | **Simulación offline** del motor sobre los ticks capturados — NO son operaciones reales | 61 resoluciones |
| `/status.risk.totalWins/totalLosses` | Contadores **acumulados del APK**, con arrastre entre ventanas y de trades fuera de ventana | 51 W / 38 L al cierre global |

El generador de reportes tomó `N` de `trades.csv` (trades confirmados) y `W`/`L` de los **deltas de contadores** o del **journal de replay**, que tienen cardinalidad distinta por construcción. De ahí salen las dos identidades imposibles:

- **`32 vs 14+22 = 36`** (R23G): se sumaron 32 órdenes confirmadas a 36 resoluciones que incluían *settlements de trades abiertos fuera de la ventana* y resoluciones del replay que nunca se ejecutaron.
- **`47 vs 23+28 = 51`** (combinado): se arrastró el mismo desfase de 4 unidades de R23G (R23F sí cuadraba: `15 = 9 + 6`), más 4 resoluciones extra.
- **`88 vs 49+38 = 87`** (proceso global): aquí `N` **sí** provino de los contadores `totalWins+totalLosses`, por eso difiere en +1 — es otra fuente distinta de `N` que en las filas anteriores. Dos `N` incompatibles en la misma tabla.

**El desfase constante de −4 en R23F+R23G es la firma de una fuente distinta, no de un trade perdido.** No hay un trade "que falta": hay 4 resoluciones de más.

### 1.2 Los tres defectos de software que lo permitieron

**Defecto A — `trades.csv` no tiene columna `result` (el monitor nunca resuelve el trade).**
En `live_monitor_r23_autonomous.py`, el bloque de captura (~L660-690) escribe:

```python
trade_fields = ["trade_id", "decision_id", "timestamp_ms", "asset", "action", "detail", "balance_at_trade"]
```

No existe resolución W/L en `trades.csv`. El monitor solo sabe que la orden **salió** (`detail="CONFIRMADA BUY saldo movido"`); no sabe si ganó. La resolución se infiere *a posteriori* leyendo los contadores de `/status`, que son acumulativos y no identifican qué trade resolvió cada incremento. **Sin identificador de trade en el settlement, todo conteo de W/L es una estimación por diferencia, no un hecho.**

**Defecto B — `events.jsonl` se trunca a los últimos 200 eventos y no es una fuente de settlement.**
La línea `events_final = fetch_json(f"{base_url}/events?max=200")` vuelca solo los 200 eventos más recientes del buffer del APK. Además, el volcado es por **ruta de salida** (`/events`), no acumulativo: si la ventana dura más de lo que cubren 200 eventos, se pierden los `ORDER_STATE` iniciales. Confirmado: R23G W01 y W02 tienen `trades.csv` con 3 filas, pero **W02 no tiene `events.jsonl` en absoluto** (la ventana se abortó por el modal de Binomo antes del volcado). Es decir: la fuente de settlement es incompleta y variable por ventana.

**Defecto C — `replay_journal.csv` se usó como si fuera evidencia en vivo.**
El monitor genera el journal offline llamando al motor de replay sobre `ticks_live.csv` (bloque ~L1000-1020). Ese journal **re-simula** las señales con su propio reloj de expiración y siempre produce W/L porque opera sobre ticks cerrados. En R23G W07 el journal produjo 6 resoluciones (3W/3L) mientras `trades.csv` tenía 3 trades (3 L): los reportes citaron el journal. El propio `replay_comparison.json` de W03 declara `match_rate_pct: 0.0` y `pass: false` — el comparador ya estaba gritando que live y replay no coincidían, y aun así se usó el journal para las cifras del reporte.

### 1.3 Por qué la suma es imposible aritméticamente

`trades.csv` y contadores responden a preguntas distintas:

- Un trade puede abrirse en W06 y **settlear en W07** → `trades.csv` lo cuenta en W06, el contador incrementa en W07. `N` y `W+L` se desalinean por ventana y **no se realinean al acumular**, porque el acumulado hereda el desfase.
- Un trade **fuera de toda ventana** (entre ventanas, operando de fondo) incrementa contadores sin aparecer en ningún `trades.csv`. Los propios `WINDOW_REPORT.md` lo documentan: W02 dice *"el registro previo `492af1ef` en trades.csv correspondió a la orden SELL que se ejecutó entre ventanas"*, y W07 anota *"el motor ejecutó de fondo una racha ganadora de 7 WINS consecutivos"*. Esos 7 wins están en el contador global (`88`) pero **no** en los 32 de R23G ni en los 47 combinados.
- Rutas de cierre anómalas (aborto por modal en W02, stop de hardware en W01) dejan ventanas cuyo `trades.csv` cierra con más órdenes que resoluciones observadas.

Resultado: `Σ N` ≠ `Σ (W+L)` y ninguna verificación lo detectó porque **no existía un assert de la identidad**.

### 1.4 Defecto D — el semáforo de resolución final es ciego

`hay_trade_sin_resolver()` (L107-124) usa `trades_confirmados > eventos_resueltos` para detectar un trade pendiente. Pero `eventos_resueltos` compara contra la **misma** fuente de contadores, así que la condición solo puede disparar si los contadores van *por detrás* de `trades.csv`. Cuando van **por delante** (settlements de trades ajenos a la ventana), el semáforo calla y la ventana se declara cerrada con más resoluciones que trades. Es exactamente el caso que produjo el `−4`.

---

## 2. RECONSTRUCCIÓN DETERMINISTA (script nuevo)

`scripts/analysis/reconcile_canonical_trades.py` — sin red, sin reloj de pared, sin aleatoriedad. Recorre las 11 ventanas (`r23f_validation/window_01..03`, `r23g_validation/window_01..08`), cruza `trades.csv` + `live_samples.jsonl` + `decisions.csv` y emite los dos artefactos.

### 2.1 Regla de settlement (lo nuevo que hacía falta)

`live_samples.jsonl` es el único artefacto con **timestamp + balance validado + contadores** en la misma fila. De ahí se construye la serie de transiciones de balance del broker. Con payout `0.82` y stake `100.000 COP`:

| Resultado | Salto de balance esperado | Verificado |
| :--- | :--- | :--- |
| WIN | `+stake × (1 + payout)` = **+182.000** | `+181.998 / +182.001 / +183.002` (68 saltos medidos) |
| LOSS | `0` (solo el débito de −100.000, que es la apertura) | 44 saltos `−100.000` |
| VOID | `+stake` = **+100.000** (reembolso) | no observado; los 2 VOID del ledger son `unresolved` (sin evidencia) |

Latencia de settlement medida: **55–66 s** (percentil completo del corpus), con dos casos de 151–156 s al cruzar cierre de ventana. Por eso la banda de búsqueda es `[t+45s, t+115s]` — un horizonte de 200 s (mi primer intento) hacía que un LOSS "reclamara" el crédito del trade siguiente; se detectó y se corrigió por asignación global al trade no resuelto más reciente.

### 2.2 Resultados

```
filas crudas            : 49
trades confirmados      : 48
duplicados eliminados   : 1
filas rechazadas        : 0
unresolved              : 2
W / L / VOID            : 20 / 26 / 2
N == W + L + VOID       : 48 = 20 + 26 + 2   OK
```

| Fase | N | W | L | VOID | WR | PnL (COP) |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| R23F | 15 | 9 | 6 | 0 | 60.00% | +138.000 |
| R23G | 33 | 11 | 20 | 2 | 33.33% | −1.088.000 |
| **TOTAL** | **48** | **20** | **26** | **2** | **41.67%** | **−950.000** |

| Dirección | N | W | L | VOID | WR | PnL (COP) |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| BUY | 31 | 11 | 19 | 1 | 35.48% | −993.000 |
| SELL | 17 | 9 | 7 | 1 | 52.94% | +43.000 |

### 2.3 Correcciones al relato de R23G

- **`N` real de R23G es 33, no 32.** El trade `3417933c` aparece en `r23f/window_03/trades.csv` **y** en `r23g/window_01/trades.csv` (mismo `trade_id`, mismo timestamp `1792203848235`, balances distintos `38.239.736,32` vs `38.421.736,96`). Es un **doble conteo de frontera**: el monitor de R23G W01 arrancó antes de que el de R23F W03 cerrara. El ledger lo cuenta **una vez**, en R23F (primera aparición cronológica).
- **W real de R23G es 11, no 14** (−3) y **L es 20, no 22** (−2). La asimetría BUY/SELL se mantiene en dirección pero cambia de magnitud: SELL 52.94% (N=17) vs BUY 35.48% (N=31), contra el 62.50%/37.14% reportado. **La conclusión "SELL altamente rentable" NO se sostiene**: SELL queda en 52.94%, por debajo del breakeven de 54.95%, y su PnL de +43.000 COP solo es positivo porque algunos wins pagaron 0.83 en vez de 0.82. Está en breakeven estadístico, no en rentabilidad demostrada. BUY sí es destructivo y con holgura (−993.000 COP).
- **`N_comb` real es 48, no 47**, y el Win Rate combinado cae a **41.67%** (reportado 45.10%), por debajo del breakeven. El PnL real es **−950.000 COP** (reportado −750.000). El veredicto `INCONCLUSIVE` de R23G se mantiene, pero por una razón más fuerte: el sistema completo está bajo breakeven, no solo BUY.
- **2 trades quedan `unresolved`** (`492af1ef` en R23G W02, `c80f17ba` en R23G W08): no tienen ni crédito ni incremento de contador dentro de la banda. Se contabilizan como VOID para preservar la identidad, **no** se les inventa un resultado. Ambos coinciden con ventanas de cierre anómalo (aborto por modal / pre-ventana).

### 2.4 Cobertura de campos

| Campo | Cobertura | Nota |
| :--- | ---: | :--- |
| `entry` | 48/48 | `price_at_signal`, fallback tick vivo |
| `score` | 22/48 | el APK no escribe score en la fila `ORDER_STATE`/`TAP` |
| `market_regime` / `trend` | 46/48 | 2 sin muestra de `/status` |
| `adaptive_similarity` | 0/48 | `[sim:NN%]` solo existe en vetos, no en trades liberados |

El `score` al 45.8% **no es un fallo del reconciliador**: es una carencia de telemetría del APK. Rellenarlo por interpolación habría contaminado cualquier análisis de confluencia. Se recupera por señal puntuada de la misma acción a ≤5 s cuando existe, y se deja vacío cuando no.

---

## 3. DEFECTOS A CORREGIR EN EL MONITOR (para R23H fase 1)

1. **Añadir `result` a `trades.csv`**, resuelto en vivo por el propio monitor contra el salto de balance, no por diferencia de contadores. Con `balance_at_trade` + payout ya alcanza para clasificar. Esto elimina la clase entera de bug.
2. **Persistir `settlement_ts` y `balance_after`** por trade. Hoy no existe el enlace trade→settlement en ningún artefacto.
3. **Asertar `N == W + L + VOID` al cierre de cada ventana** y abortar el reporte si falla. La regla de oro R23H existe en el documento pero no en el código.
4. **`events.jsonl` acumulativo**, no `?max=200` por ruta. Guardar el cursor/last-ts y pedir solo lo nuevo.
5. **Prohibir el `replay_journal.csv` como fuente de cifras de campaña.** Solo contraste, y solo si `replay_comparison.json` da `pass: true`.
6. **Detección de solape entre ventanas**: rechazar un `trade_id` ya visto en la campaña al abrir `trades.csv`. Esto habría cazado el doble conteo de `3417933c`.
7. **`hay_trade_sin_resolver()` debe comparar contra trades con settlement enlazado**, no contra el delta de contadores (que arrastra trades ajenos).

---

## 4. REPRODUCIBILIDAD

```
python scripts/analysis/reconcile_canonical_trades.py
```

Verificado determinista: dos ejecuciones consecutivas producen `CANONICAL_TRADES.csv` byte-idéntico. `--root <ruta>` audita una copia. El script no escribe fuera de `loops/r23h_analysis/` ni modifica artefactos fuente.

**Artefactos:**
- `loops/r23h_analysis/CANONICAL_TRADES.csv` — 48 filas, 18 columnas exactas del §2 del spec.
- `loops/r23h_analysis/DATA_INTEGRITY_REPORT.md` — reporte de integridad con la identidad verificada.
