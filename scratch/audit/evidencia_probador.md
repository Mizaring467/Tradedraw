# Evidencia del probador — `scripts/analyze_journal.py`

- **Fecha**: 2026-09-16
- **Repo**: `C:\Users\heidy\Tradedraw`
- **Canal**: `codigo` (+ verificación de datos sobre CSV de solo lectura)
- **Objeto**: `scripts/analyze_journal.py`, auditoría de la fase de validación del bot
- **Veredicto**: **PASA**

---

## 0. Hashes e integridad del CSV (requisito 9)

| Momento | SHA256 |
| :--- | :--- |
| ANTES de ejecutar | `FF8FD400060FA0DAECCF92929EA3520B4D760BA5F1B096F165DF1E4A96382D22` |
| DESPUÉS de ejecutar ambos comandos | `FF8FD400060FA0DAECCF92929EA3520B4D760BA5F1B096F165DF1E4A96382D22` |

- Hash idéntico ⇒ el script **no modifica** el CSV.
- Filas: 213 líneas de archivo = 1 header + **212 filas de datos** (medido antes y después).
- `LastWriteTime` del CSV: `2026-09-16 17:30:47` (sin cambios durante la prueba).
- Sin `scipy`/`numpy`: python 3.12.10, imports reales del módulo:
  `sys, csv, math, re, argparse, datetime, collections, os` (el `os` es local dentro de `main`).

---

## 1. Tabla requisito → veredicto

| # | Requisito | Veredicto | Evidencia (observado vs esperado) |
| :-: | :--- | :-: | :--- |
| 1 | Ejecutable sin argumentos, CSV por defecto | PASA | `python scripts/analyze_journal.py` → exit 0, cabecera `ANALISIS DE VENTANA - loops/journal/trade_journal_device_all.csv` |
| 2 | Métricas base + desglose action/submode/strategy/hora | PASA | Total 212; WIN 81 / LOSS 112 / TIE 19; WR total 38.2%, WR resueltos 42.0%. Desglose BUY/SELL, CONSERVATIVE/YOLO, AUTO_ADAPTIVE, 17 claves horarias `07..23` (suma 212) |
| 3 | Payout medido por regex `Diff` + break-even | PASA | Diff+ 81 (media +79,469.45) y Diff- 112 (media −98,214.39) vs independiente +79,469.45 / −98,214.39. Payout 80.91% (indep. 80.91%), BE 55.3% (100/(1+0.8091)=55.28%) |
| 4 | Wilson 95% solo con `math` | PASA | 50/100 → `[40.4%, 59.6%]` (esperado ≈[40.4,59.6]); verificación cruzada por dos fórmulas independientes: 40.38/59.62 ambas. 81/193 → [35.2%, 49.0%], indep. [35.23, 49.02] |
| 5 | Riesgo: drawdown, rachas, PnL neto | PASA | DD +2,157,998.08 COP (4.8%) vs indep. 2157998.08 (4.79%); racha LOSS 8 (indep. 8), racha WIN 4 (indep. 4); PnL neto acumulado +39,476,822.00 COP y saldo neto ventana −2,066,001.92 (indep. −2,066,001.92) |
| 6 | Veredicto con 3 estados exactos | PASA | Strings exactas presentes: `VEREDICTO: MUESTRA INSUFICIENTE (n<300)`, `VEREDICTO: EDGE PRESENTE`, `VEREDICTO: SIN EVIDENCIA DE EDGE`. Rama alcanzada con datos reales: MUESTRA INSUFICIENTE (n=212, faltan 88) |
| 7 | Modo comparativo por fecha | PASA | `python scripts/analyze_journal.py 2026-09-12` → exit 0, n=44 (indep. 44), filtra `iso_date >= 2026-09-12`; sin argumento analiza 212 |
| 8 | Solo stdlib | PASA | Imports: `sys, csv, math, re, argparse, datetime, collections, os` — ninguno fuera de stdlib; sin pandas/numpy |
| 9 | No modifica el CSV | PASA | Hash idéntico antes/después (ver §0) |

---

## 2. Conteos independientes (script propio `scratch/audit/verif_journal.py`, sin importar el módulo bajo prueba)

| Comprobación | Esperado | Observado independiente | Observado por el script | Coincide |
| :--- | :--- | :--- | :--- | :-: |
| Total filas de datos | 212 | 212 | 212 | ✅ |
| `result` declarado WIN/LOSS/TIE | 81 / 114 / 17 | 81 / 114 / 17 | 81 / 114 / 17 (LECTURA ALTERNATIVA) | ✅ |
| WR sobre resueltos (declarado) | 41.5% | 41.5% | 41.5% | ✅ |
| Delta saldo WIN/LOSS/TIE | 81 / 112 / 19 | 81 / 112 / 19 | 81 / 112 / 19 | ✅ |
| WR por delta de saldo | 42.0% | 42.0% | 42.0% | ✅ |
| BUY declarado | n=89, WR 46.8% | n=89, 37/42/10, 46.8% | n=89 WIN 37 LOSS 42 TIE 10, 46.8% | ✅ |
| SELL declarado | n=123, WR 37.9% | n=123, 44/72/7, 37.9% | n=123 WIN 44 LOSS 72 TIE 7, 37.9% | ✅ |
| Racha máxima LOSS global | 8 | 8 (delta) y 8 (declarado) | 8 (delta) y 8 (declarado) | ✅ |
| Racha máxima LOSS subconjunto SELL | 10 | 10 (delta) y 10 (declarado) | 10 (impreso en desglose por acción) | ✅ |
| Stakes | 117×`1.00`, 95×`2.00` | 117 / 95 | stake 1.00 → n 117 (37+70+10); stake 2.00 → n 95 (44+42+9) | ✅ |
| Diffs del `reason` | 81 positivos, 112 negativos, 19 ceros | 81 / 112 / 19 (0 sin Diff) | 81 / 112 / 19 | ✅ |

Extras verificados: suma de claves horarias = 212; fechas presentes en CSV = {2026-09-10, 2026-09-11, 2026-09-12} ⇒ el filtro `>= 2026-09-12` recorta a 44 filas, coherente con n=44.

---

## 3. Corrección del IC de Wilson (verificación independiente, requisito C)

- Caso de control 50 aciertos / 100 intentos: el script devuelve `[40.4%, 59.6%]`; mi implementación independiente (fórmula cerrada) da `[40.38, 59.62]`; segunda implementación independiente (resolución cuadrática `(p−p0)² = z²p(1−p)/n`) da `[40.38, 59.62]`. Coincide con el valor esperado ≈[40.4%, 59.6%].
- Caso real 81/193: script `[35.2%, 49.0%]` vs independiente `[35.23, 49.02]` — coincide.
- Sanity `n=10, w=10` → `[72.25%, 100.0%]` (límite superior acotado a 100, comportamiento correcto de Wilson frente al intervalo normal).
- El IC se calcula sobre **resueltos** (n = WIN+LOSS = 193), no sobre total — coherente con el criterio break-even.

---

## 4. Inspección de código: requisito 6 y 8 (requisito D)

- **Tres estados del veredicto** (líneas 457, 461, 464):
  - `VEREDICTO: MUESTRA INSUFICIENTE (n<300)` — rama `total < MIN_TRADES_EDGE`, imprime `Faltan 88 trades para poder declarar edge con este criterio.`
  - `VEREDICTO: EDGE PRESENTE` — rama `lo > be`
  - `VEREDICTO: SIN EVIDENCIA DE EDGE` — rama `else`
  - Texto de criterio explícito impreso antes: `limite inferior del IC Wilson 95% (35.2%) > break-even (55.3%)` y `y al menos 300 trades (hay 212)` — imposible de malinterpretar.
  - `MIN_TRADES_EDGE = 300`; `Z95 = 1.959963984540054`; `RE_DIFF = re.compile(r"Diff=\s*([+-]?\d+(?:[.,]\d+)?)")`.
- **Stdlib estricta**: único `import os` adicional (línea 490) dentro de `main()`, también stdlib. Sin dependencias externas.
- Nota: la rama `EDGE PRESENTE` no es alcanzable con los 212 trades actuales (requiere n≥300 y lo>be); su presencia se confirma por inspección del código, no por ejecución con datos reales.

---

## 5. Discrepancias conocidas y aceptadas (NO son fallo) — confirmadas

| Discrepancia | Estado | Evidencia |
| :--- | :-: | :--- |
| Lectura principal por delta de saldo: LOSS=112 / TIE=19 / WR 42.0% | Confirmada | Salida real, coincide con mi conteo delta (81/112/19, 42.0%) |
| "LECTURA ALTERNATIVA" por campo `result`: LOSS=114 / TIE=17 / WR 41.5% | Confirmada | Bloque impreso por el script; coincide con mi conteo declarado (81/114/17, 41.5%). Causa declarada en código: 2 trades marcados LOSS con `Diff=0.0` (sin movimiento de saldo) |
| Racha global LOSS = 8; racha del lado SELL = 10 | Confirmada | Global 8 (delta y declarado, mi conteo independiente); SELL 10 impreso en el desglose por acción, coincide con mi conteo independiente del subconjunto SELL |

---

## 6. Salida textual completa — Comando 1

```
cd C:\Users\heidy\Tradedraw; python scripts/analyze_journal.py
```

```
================================================================
ANALISIS DE VENTANA - loops/journal/trade_journal_device_all.csv
================================================================
Operaciones totales : 212
  WIN  : 81
  LOSS : 112
  TIE  : 19   (9.0% del total)
----------------------------------------------------------------
Winrate sobre total   : 38.2%
Winrate sobre resueltos: 42.0%
----------------------------------------------------------------
LECTURA ALTERNATIVA (campo `result` declarado por el bot)
  WIN 81 / LOSS 114 / TIE 17   -> WR sobre resueltos 41.5%
  Peor racha perdedora declarada: 8   (vs 8 por delta de saldo)
  La diferencia son trades marcados LOSS con Diff=0.0 (sin movimiento de saldo).
----------------------------------------------------------------
PAYOUT MEDIDO (extraido del campo `reason` del broker)
  Victorias con Diff+  : 81   media +79,469.45 COP
  Derrotas con Diff-   : 112   media -98,214.39 COP
  Payout real medido   : 80.91%
  Break-even implicado : 55.3%
----------------------------------------------------------------
INTERVALO DE CONFIANZA (Wilson 95%, sobre resueltos)
  n resueltos          : 193
  IC 95%               : [35.2%, 49.0%]
  Break-even           : 55.3%
  Brecha vs break-even : limite inferior -20.0 pp
----------------------------------------------------------------
RIESGO
  Drawdown maximo      : +2,157,998.08 COP  (4.8% desde el pico)
  Peor racha perdedora : 8
  Mejor racha ganadora : 4
  PnL neto acumulado   : +39,476,822.00 COP  (suma de deltas de saldo)
  Saldo neto de la ventana : -2,066,001.92 COP
  (inicio 44,961,812.48 -> fin 42,895,810.56)
================================================================

DESGLOSE POR NIVEL DE MARTINGALA
   Stake |  WIN |  LOSS |  TIE |      WR
----------------------------------------
    1.00 |   37 |    70 |   10 |   34.6%
    2.00 |   44 |    42 |    9 |   51.2%

FILAS SOSPECHOSAS (8) — datos que envenenan el aprendizaje
  2026-09-10T11:51:10 declarado=LOSS efectivo=TIE base=44,961,812.48 settled=44,961,812.48 -> DECLARADO_SIN_MOVIMIENTO
  2026-09-10T12:00:03 declarado=LOSS efectivo=TIE base=44,961,812.48 settled=44,961,812.48 -> DECLARADO_SIN_MOVIMIENTO
  2026-09-10T17:50:00 declarado=WIN efectivo=WIN base=1.00 settled=44,907,811.84 -> BALANCE_CORRUPTO
  2026-09-10T22:30:02 declarado=LOSS efectivo=LOSS base=44,463,810.56 settled=1.00 -> BALANCE_CORRUPTO
  2026-09-12T12:09:02 declarado=LOSS efectivo=LOSS base=43,163,811.84 settled=183,000.00 -> BALANCE_CORRUPTO
  2026-09-12T12:11:03 declarado=TIE efectivo=TIE base=1.00 settled=1.00 -> BALANCE_CORRUPTO
  2026-09-12T12:14:03 declarado=WIN efectivo=WIN base=1.00 settled=43,246,812.16 -> BALANCE_CORRUPTO
  2026-09-12T12:18:02 declarado=WIN efectivo=WIN base=1.00 settled=43,329,812.48 -> BALANCE_CORRUPTO

DESGLOSE POR ACCION (BUY vs SELL)
 Accion |    n |  WIN |  LOSS |  TIE |      WR
----------------------------------------------
    BUY |   89 |   37 |    42 |   10 |   46.8%
          peor racha perdedora del lado BUY: 8
   SELL |  123 |   44 |    70 |    9 |   38.6%  <-- POR DEBAJO DEL AZAR
          peor racha perdedora del lado SELL: 10

 Accion | (lectura por `result` declarado)
----------------------------------------------
    BUY | n=89   WIN 37  LOSS 42  TIE 10  WR  46.8%
   SELL | n=123  WIN 44  LOSS 72  TIE 7   WR  37.9%

DESGLOSE POR SUBMODE
    Clave |    n |  WIN |  LOSS |  TIE |      WR
----------------------------------------------
CONSERVATIVE |    6 |    2 |     2 |    2 |   50.0%
     YOLO |  206 |   79 |   110 |   17 |   41.8%

DESGLOSE POR STRATEGY
    Clave |    n |  WIN |  LOSS |  TIE |      WR
----------------------------------------------
AUTO_ADAPTIVE |  212 |   81 |   112 |   19 |   42.0%

DESGLOSE POR HORA DEL DIA (iso_date)
    Clave |    n |  WIN |  LOSS |  TIE |      WR
----------------------------------------------
       07 |   12 |    2 |     6 |    4 |   25.0%
       08 |   15 |    7 |     8 |    0 |   46.7%
       09 |   20 |    5 |    13 |    2 |   27.8%
       10 |   32 |   12 |    19 |    1 |   38.7%
       11 |   20 |    9 |     8 |    3 |   52.9%
       12 |   12 |    4 |     6 |    2 |   40.0%
       13 |   12 |    5 |     6 |    1 |   45.5%
       14 |   13 |    5 |     8 |    0 |   38.5%
       15 |   14 |    6 |     8 |    0 |   42.9%
       16 |   20 |   11 |     7 |    2 |   61.1%
       17 |   11 |    5 |     3 |    3 |   62.5%
       18 |    7 |    1 |     6 |    0 |   14.3%
       19 |    8 |    2 |     6 |    0 |   25.0%
       20 |    4 |    1 |     3 |    0 |   25.0%
       21 |    1 |    0 |     0 |    1 |    0.0%
       22 |    9 |    5 |     4 |    0 |   55.6%
       23 |    2 |    1 |     1 |    0 |   50.0%

================================================================
VEREDICTO
================================================================
  Criterio: limite inferior del IC Wilson 95% (35.2%) > break-even (55.3%)
            y al menos 300 trades (hay 212)
----------------------------------------------------------------
  VEREDICTO: MUESTRA INSUFICIENTE (n<300)
  Faltan 88 trades para poder declarar edge con este criterio.
  (El IC actual [35.2%, 49.0%] es demasiado ancho para decidir.)
================================================================
```

Exit code: **0**

---

## 7. Salida textual completa — Comando 2

```
cd C:\Users\heidy\Tradedraw; python scripts/analyze_journal.py 2026-09-12
```

```
================================================================
ANALISIS DE VENTANA - loops/journal/trade_journal_device_all.csv
Filtro de ventana     : trades con iso_date >= 2026-09-12
================================================================
Operaciones totales : 44
  WIN  : 11
  LOSS : 27
  TIE  : 6   (13.6% del total)
----------------------------------------------------------------
Winrate sobre total   : 25.0%
Winrate sobre resueltos: 28.9%
----------------------------------------------------------------
PAYOUT MEDIDO (extraido del campo `reason` del broker)
  Victorias con Diff+  : 11   media +67,001.11 COP
  Derrotas con Diff-   : 27   media -96,296.36 COP
  Payout real medido   : 69.58%
  Break-even implicado : 59.0%
----------------------------------------------------------------
INTERVALO DE CONFIANZA (Wilson 95%, sobre resueltos)
  n resueltos          : 38
  IC 95%               : [17.0%, 44.8%]
  Break-even           : 59.0%
  Brecha vs break-even : limite inferior -42.0 pp
----------------------------------------------------------------
RIESGO
  Drawdown maximo      : +1,056,998.40 COP  (2.4% desde el pico)
  Peor racha perdedora : 6
  Mejor racha ganadora : 3
  PnL neto acumulado   : +41,732,816.88 COP  (suma de deltas de saldo)
  Saldo neto de la ventana : -1,056,998.40 COP
  (inicio 43,952,808.96 -> fin 42,895,810.56)
================================================================

DESGLOSE POR NIVEL DE MARTINGALA
   Stake |  WIN |  LOSS |  TIE |      WR
----------------------------------------
    1.00 |    6 |    18 |    3 |   25.0%
    2.00 |    5 |     9 |    3 |   35.7%

FILAS SOSPECHOSAS (4) — datos que envenenan el aprendizaje
  2026-09-12T12:09:02 declarado=LOSS efectivo=LOSS base=43,163,811.84 settled=183,000.00 -> BALANCE_CORRUPTO
  2026-09-12T12:11:03 declarado=TIE efectivo=TIE base=1.00 settled=1.00 -> BALANCE_CORRUPTO
  2026-09-12T12:14:03 declarado=WIN efectivo=WIN base=1.00 settled=43,246,812.16 -> BALANCE_CORRUPTO
  2026-09-12T12:18:02 declarado=WIN efectivo=WIN base=1.00 settled=43,329,812.48 -> BALANCE_CORRUPTO

DESGLOSE POR ACCION (BUY vs SELL)
 Accion |    n |  WIN |  LOSS |  TIE |      WR
----------------------------------------------
    BUY |   15 |    6 |     7 |    2 |   46.2%
          peor racha perdedora del lado BUY: 2
   SELL |   29 |    5 |    20 |    4 |   20.0%  <-- POR DEBAJO DEL AZAR
          peor racha perdedora del lado SELL: 7

 Accion | (lectura por `result` declarado)
----------------------------------------------
    BUY | n=15   WIN 6   LOSS 7   TIE 2   WR  46.2%
   SELL | n=29   WIN 5   LOSS 20  TIE 4   WR  20.0%

DESGLOSE POR SUBMODE
    Clave |    n |  WIN |  LOSS |  TIE |      WR
----------------------------------------------
     YOLO |   44 |   11 |    27 |    6 |   28.9%

DESGLOSE POR STRATEGY
    Clave |    n |  WIN |  LOSS |  TIE |      WR
----------------------------------------------
AUTO_ADAPTIVE |   44 |   11 |    27 |    6 |   28.9%

DESGLOSE POR HORA DEL DIA (iso_date)
    Clave |    n |  WIN |  LOSS |  TIE |      WR
----------------------------------------------
       09 |    9 |    0 |     7 |    2 |    0.0%
       10 |   13 |    3 |     9 |    1 |   25.0%
       11 |   10 |    4 |     4 |    2 |   50.0%
       12 |   11 |    4 |     6 |    1 |   40.0%
       13 |    1 |    0 |     1 |    0 |    0.0%

================================================================
VEREDICTO
================================================================
  Criterio: limite inferior del IC Wilson 95% (17.0%) > break-even (59.0%)
            y al menos 300 trades (hay 44)
----------------------------------------------------------------
  VEREDICTO: MUESTRA INSUFICIENTE (n<300)
  Faltan 256 trades para poder declarar edge con este criterio.
  (El IC actual [17.0%, 44.8%] es demasiado ancho para decidir.)
================================================================
```

Exit code: **0**

---

## 8. Limitaciones de esta verificación

- La rama `EDGE PRESENTE` no se pudo ejercitar con datos reales (requiere n≥300 y `lo > be`); verificación por inspección de código, no por ejecución.
- Ventana 2: n=44 resueltos=38, IC [17.0%, 44.8%] coincide con mi cálculo independiente `[17.0, 44.76]` redondeado a 1 decimal.
- Sin flakiness observada: ambos comandos deterministas, exit 0 en la primera ejecución.

## 9. Artefactos generados

- `scratch/audit/verif_journal.py` — script de verificación independiente (no importa el módulo bajo prueba).
- `scratch/audit/evidencia_probador.md` — este informe.