# R23H — DATA INTEGRITY REPORT (CANONICAL TRADE LEDGER)

**Generado por:** `scripts/analysis/reconcile_canonical_trades.py`
**Modo de ejecucion:** determinista, offline, solo lectura de artefactos primarios
**Salida:** `C:/Users/heidy/Tradedraw/loops/r23h_analysis/CANONICAL_TRADES.csv`

---

## 1. VEREDICTO DE INTEGRIDAD

| Metrica | Valor |
| :--- | ---: |
| `raw_rows` (filas crudas en todos los `trades.csv`) | 49 |
| `confirmed_trades` (unicos y confirmados) | 48 |
| `duplicates_removed` | 1 |
| `rejected_rows` (excluidas por reglas §2) | 0 |
| `unresolved` (sin settlement localizable) | 2 |
| **W** (WIN) | **20** |
| **L** (LOSS) | **26** |
| **VOID** | **2** |
| **N = W + L + VOID** | **48 = 20 + 26 + 2** |
| Identidad `N == W + L + VOID` | **CUMPLE** |
| Win Rate canonico | 41.67% |
| Delta de balance agregado | -950,000.00 COP |

La ecuacion obligatoria **N = W + L + VOID** se cumple sobre el ledger reconstruido.

---

## 2. CONTEO POR FASE

| Fase | N | W | L | VOID | WR | Delta balance (COP) |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| R23F | 15 | 9 | 6 | 0 | 60.00% | +138,000.00 |
| R23G | 33 | 11 | 20 | 2 | 33.33% | -1,088,000.00 |
| **TOTAL** | **48** | **20** | **26** | **2** | **41.67%** | **-950,000.00** |

## 3. CONTEO POR DIRECCION

| Direccion | N | W | L | VOID | WR | Delta balance (COP) |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| BUY | 31 | 11 | 19 | 1 | 35.48% | -993,000.00 |
| SELL | 17 | 9 | 7 | 1 | 52.94% | +43,000.00 |

## 4. CONTEO POR VENTANA

| Ventana | N | W | L | VOID | WR |
| :--- | ---: | ---: | ---: | ---: | ---: |
| r23f_w01 | 4 | 2 | 2 | 0 | 50.00% |
| r23f_w02 | 5 | 5 | 0 | 0 | 100.00% |
| r23f_w03 | 6 | 2 | 4 | 0 | 33.33% |
| r23g_w01 | 2 | 0 | 2 | 0 | 0.00% |
| r23g_w02 | 3 | 0 | 2 | 1 | 0.00% |
| r23g_w03 | 6 | 3 | 3 | 0 | 50.00% |
| r23g_w04 | 7 | 3 | 4 | 0 | 42.86% |
| r23g_w05 | 5 | 3 | 2 | 0 | 60.00% |
| r23g_w06 | 4 | 1 | 3 | 0 | 25.00% |
| r23g_w07 | 3 | 0 | 3 | 0 | 0.00% |
| r23g_w08 | 3 | 1 | 1 | 1 | 33.33% |

---

## 5. TRAZABILIDAD DE LA DEPURACION

### 5.1 Filas descartadas por reglas de exclusion

Ninguna fila descartada por reglas de exclusion.

### 5.2 Duplicados eliminados (mismo `trade_id`)

| trade_id | fuente conservada | fuente descartada | mismo ts | mismo balance |
| :--- | :--- | :--- | :---: | :---: |
| `3417933c-888` | r23f_validation/window_03/trades.csv/r23f_w03 | r23g_validation/window_01/trades.csv | si | no |

### 5.3 Trades sin settlement localizable (`unresolved`)

| trade_id | ventana | accion | balance_at_trade | motivo |
| :--- | :--- | :--- | ---: | :--- |
| `492af1ef-951` | r23g_w02 | SELL | 38,619,735.04 | sin credito ni incremento de contador en la banda de settlement |
| `c80f17ba-454` | r23g_w08 | BUY | 38,226,736.64 | sin credito ni incremento de contador en la banda de settlement |

Estos trades se contabilizan como `VOID` para preservar la identidad `N = W + L + VOID`; se listan aqui para que ningun resultado se de por cierto sin evidencia.

---

## 6. CONTRAPOSICION CON LAS CIFRAS REPORTADAS EN R23G

| Metric | Reportado R23G | Canonico | Desvio |
| :--- | ---: | ---: | ---: |
| N (muestra R23G) | 32 | 33 | +1 |
| W | 14 | 11 | -3 |
| L | 22 | 20 | -2 |
| W + L reportado | 36 | 33 (W+L+VOID) | — |

### 6.1 Cobertura de campos enriquecidos

| Campo | Poblado | Cobertura | Fuente |
| :--- | ---: | ---: | :--- |
| `entry` | 48/48 | 100.0% | `decisions.csv:price_at_signal`, fallback tick vivo |
| `score` | 22/48 | 45.8% | fila de decision; fallback señal puntuada a <=5s |
| `market_regime` / `trend` | 46/48 | 95.8% | `decisions.csv`; fallback `/status` en el instante de la orden |
| `adaptive_similarity` | 0/48 | 0.0% | `[sim:NN%]` del detail (solo vetos) |

El `score` queda vacio en 26 trades porque el APK no lo escribe en la fila `ORDER_STATE`/`TAP` que origina la orden: solo las reevaluaciones `VETO` de la misma señal lo llevan. No se rellena por interpolacion: un score inventado contaminaria cualquier analisis de confluencia.

---

## 7. REGLAS DE RESOLUCION APLICADAS

1. **Universo:** una fila por `trade_id` unico con `detail` = `CONFIRMADA <ACCION> saldo movido`.
2. **`balance_at_trade` es POST-deduccion** (el stake ya fue descontado cuando el APK emite la confirmacion).
   Por tanto `balance_before = balance_at_trade + stake`.
3. **WIN:** salto observado `+182000 COP` (= `stake x (1 + payout)`) en `[t+45s, t+115s]`.
4. **VOID:** salto observado `+100000 COP` (= reembolso del stake) en la misma banda.
5. **LOSS:** sin credito acreditable + incremento del contador `totalLosses` del APK dentro de la banda.
6. **Cada salto se consume una sola vez**, asignado al trade no resuelto mas reciente cuya banda
   contenga el salto: dos trades seguidos no pueden reclamar el mismo credito de balance.
7. **Los `replay_journal.csv` NO son evidencia de resultado en vivo:** son la simulacion del motor
   offline sobre los ticks. Solo se usan como contraste, nunca como fuente de `result`.

---

## 8. REPRODUCIBILIDAD

```
python scripts/analysis/reconcile_canonical_trades.py
```

Sin argumentos usa la raiz del repo. `--root <ruta>` permite auditar una copia.
El script no escribe fuera de `loops/r23h_analysis/` y no modifica ningun artefacto fuente.
