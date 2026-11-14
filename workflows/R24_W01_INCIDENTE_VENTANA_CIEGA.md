# VENTANA R24 · W01 · INFORME DE INCIDENTE

**Fecha:** 2026-10-22 23:08:02 → 23:30:42 (America/Bogota)
**Ejecutor:** ORQUESTADOR (agente principal)
**Build:** versionCode 19, `gitSha=c0adea0` (`app/` idéntico a `64f78f1`)
**Modo:** `AUTONOMOUS` / `YOLO`, cuenta DEMO
**Veredicto del monitor:** `PASS` — **RECHAZADO**. Ver §3.

---

## 1. Cronología medida

| Hora local | Evento | Fuente |
| :--- | :--- | :--- |
| 23:07:15 | `.lock_window` creado | orquestador |
| 23:07:21 | `mode=AUTONOMOUS` (bridge) | `/mode_log` |
| 23:07:52 | `submode=YOLO` (bridge) | `/submode` |
| 23:08:02 | Monitor arranca, ventana nominal 20 min | `task-712` |
| 23:08:24 | Primer trade confirmado (SELL) | `trades.csv` |
| **23:11:53** | **Última lectura válida de `/status` (elapsed 231 s)** | `live_samples.jsonl` |
| 23:12:05 | Trade 2 (BUY WIN) — ya sin observación | journal |
| 23:15:22 | Trade 3 (BUY LOSS) — sin observación | journal |
| 23:19:15 | Trade 4 (SELL LOSS) — sin observación | journal |
| 23:21:17 | Trade 5 (SELL LOSS) — sin observación | journal |
| 23:23:07 | Trade 6 (SELL WIN) — sin observación | journal |
| 23:28:02 | Cierre nominal. Monitor no pudo ordenar `DISABLED` | `task-712` log |
| 23:30:42 | Orquestador apaga por bridge tras leer el resultado | `/mode_log` |

**El forward `tcp:8080` se cayó a las 23:11:53.** El monitor siguió corriendo 16 min más sin poder leer `/status`, sin poder ordenar `DISABLED` al cierre, y **el motor siguió operando a ciegas**.

## 2. Lo que el monitor reportó vs lo que pasó

| Métrica | Reporte `LIVE_REPORT.md` | Realidad medida |
| :--- | :--- | :--- |
| Muestras | 17 | 17 válidas + 53 `UNREADABLE` (70 filas) |
| Balance inicial | 37.465.735,68 | 37.565.736,96 (el reporte arrancó tras el primer trade) |
| Balance final | 37.547.735,04 | **37.329.735,68** |
| Trades | 2 | **6** |
| Taps | 4 (acumulado) | coherente |
| Drawdown | 0,48 % | **0,63 %** (37.565.736,96 → 37.329.735,68) |
| Paridad live↔replay | 1/1 = 100 % | **sobre 1 par de 6 trades** |
| Veredicto | `PASS` | **NO VÁLIDO** |

`live_samples.jsonl` muestra `mode=None`, `raw_balance=None`, `balance_quality=UNREADABLE` en 53 de 70 filas, desde `elapsed_sec=231` hasta `1198`.

## 3. Por qué el `PASS` es inválido

1. **Cobertura temporal 19 %.** 231 s observados de 1200 s nominales.
2. **4 de 6 trades ocurrieron fuera de la ventana observada.** El monitor solo vio 2.
3. **El cierre no pudo ejecutarse.** El log dice literalmente: `[CIERRE SEGURO] ⚠️ No se pudo ordenar DISABLED al bridge`.
4. **La paridad 100 % se calcula sobre 1 par.** El preregistro V5 exige `>= 20` trades Live. `NO EVALUABLE`.
5. **El balance final del reporte es de 23:12:27**, no del cierre.

El monitor no distingue «no medí» de «medí y salió bien». Es el mismo defecto que el preregistro advierte para V5 (`NO EVALUABLE` ≠ `FALLA`), pero aquí se resolvió como `PASS`.

## 4. Trades reales de la sesión R24 (fuente: journal del dispositivo)

Extraído con `adb shell "run-as com.example.tradedraw cat files/TradeDraw_Audits/trade_journal.csv"` → `loops/r24_validation/journal_after_w01.csv`.

| # | Hora | Acción | Resultado | Balance | ¿Observado por el monitor? |
| :-: | :--- | :--- | :--- | :--- | :--- |
| 1 | 23:09:21 | SELL | LOSS | 37.565.736,96 → 37.465.735,68 | Sí |
| 2 | 23:12:05 | BUY | WIN | 37.465.735,68 → 37.547.735,04 | Sí (última lectura 23:12:27) |
| 3 | 23:15:22 | BUY | LOSS | 37.547.735,04 → 37.447.736,32 | **No** |
| 4 | 23:19:15 | SELL | LOSS | 37.447.736,32 → 37.347.737,60 | **No** |
| 5 | 23:21:17 | SELL | LOSS | 37.347.737,60 → 37.247.736,32 | **No** |
| 6 | 23:23:07 | SELL | WIN | 37.247.736,32 → 37.329.735,68 | **No** |

**Totales:** 6 trades, **2W / 4L**, WR **33,33 %**, P&L **−236.001,28 COP**.
Drawdown 0,63 % del balance de partida. Dentro del límite de 3,5 %.

**Estos 6 trades SÍ son de ventana** (preregistro creado, monitor corriendo, cuenta Demo verificada). Pero la ventana está **incompleta en observación**, así que no sirven para V1–V5 en su forma actual. Se conservan como evidencia del comportamiento del motor, no como muestra de validación.

## 5. V1–V4 evaluados sobre lo que sí hay

| Criterio | Resultado | Evidencia |
| :--- | :--- | :--- |
| **V1** Contadores | **NO EVALUABLE** | `Δ(W+L)` de `/status` = **3** (W 0→1, L 0→2) frente a **2** filas en `trades.csv` y **6** trades reales en el journal. Discrepancia estructural, no de ±1. El reporte ya la marca: «AVISO: DISCREPANCIA de +1 resolución». Con 6 trades reales y 3 contados, V1 no mide lo que declara. |
| **V2** S/R exportados | **PASS** | `decisions.csv` tiene `distanceToSupportRatio,distanceToResistanceRatio`; **15/15 filas** con valor numérico parseable = **100 %** ≥ 99 %. **B2 funciona.** |
| **V3** Memoria adaptativa | **NO EVALUABLE** | No se generó `adaptive_state.json` en la ventana. El monitor no lo vuelca. B3 existe (`/adaptive_state`) pero nadie lo llamó. |
| **V4** NO_PROCESADA | **NO EVALUABLE** | `events.jsonl` no se capturó en la ventana (0 filas). Sin ocurrencias registradas no se puede distinguir «no hubo» de «no se midió». |
| **V5** Paridad v2 | **NO EVALUABLE** | 1 par de 6 trades. Mínimo preregistrado: 20. |

**Único resultado positivo: V2, y es de instrumentación, no de estrategia.**

## 6. Causa raíz

**El forward ADB `tcp:8080` no sobrevive.** Se cayó tres veces en esta sesión:

| # | Momento | Detectado por |
| :-: | :--- | :--- |
| 1 | entre 20:46 y 23:02 | sondeo de Fase E |
| 2 | 23:02:14 | preflight de instalación |
| 3 | **23:11:53** | `live_samples.jsonl` (53 filas `UNREADABLE`) |

El transporte es ADB inalámbrico sobre `[fe80::…%4]:5555` (IPv6 link-local). El dispositivo tiene `loadavg` ~15 y 1,8 GB de RAM; el `adbd` se recicla bajo presión. **No es la app: es el transporte.**

**Fallo de proceso del orquestador:** no verifiqué el forward durante la ventana. El sondeo programado a los 5 min (23:13:16) encontró `/status` vacío y en vez de tratarlo como STOP, reintenté el forward y seguí. Debí abortar la ventana en ese instante.

## 7. Estado tras contención

Verificado 23:30:42:

| Campo | Valor |
| :--- | :--- |
| `mode` | **`DISABLED`** |
| `submode` | `YOLO` (irrelevante con modo apagado) |
| `orderState` | `NINGUNA` |
| `hasPendingTrade` | `false` |
| `isDemoAccount` | **`true`** |
| `health` | `READY`, `reasons: []` |
| Balance | **37.329.735,68 COP** |
| `.lock_window` | eliminado |

**0 trades después de las 23:30:42.** No hubo operación en vuelo al apagar.

## 8. Correcciones obligatorias antes de otra ventana

1. **Watchdog de forward en el monitor.** Si una muestra sale `UNREADABLE` dos veces seguidas → abortar la ventana y ordenar `DISABLED`. No seguir a ciegas.
2. **El monitor debe negarse a emitir `PASS` con cobertura < 90 %.** Cambiar el veredicto a `INCOMPLETA` cuando haya muestras `UNREADABLE` consecutivas.
3. **Verificar el forward cada 60 s desde el orquestador**, no cada 5 min.
4. **Volcar `/adaptive_state` al cierre** para que V3 sea evaluable.
5. **Capturar `/events` completo** durante la ventana para V4.
6. **Investigar la discrepancia V1** (3 contadores vs 6 trades reales). B1 no la cerró; puede que el journal escriba más filas que las que el motor cuenta, o al contrario.

## 9. Evidencia en disco

| Archivo | Contenido |
| :--- | :--- |
| `loops/r24_validation/window_01/` | Todos los artefactos del monitor (12 archivos) |
| `loops/r24_validation/window_01/live_samples.jsonl` | 70 filas; 53 `UNREADABLE` desde `elapsed_sec=231` |
| `loops/r24_validation/window_01/window_metadata.json` | `initial_balance=37465735.68`, `final_balance_validated=37547735.04` |
| `loops/r24_validation/journal_after_w01.csv` | Journal del dispositivo (314 filas) |
| `loops/r24_validation/mode_changes_after_w01.jsonl` | Registro de cambios de modo |
