# INCIDENTE 2 · TRADES FUERA DE VENTANA (R24)

**Fecha de detección:** 2026-10-22T20:46:23-05:00
**Detectado por:** ORQUESTADOR (sondeo `/status` de Fase E)
**Severidad:** ALTA — 38 trades reales en cuenta Demo, ninguno dentro de una ventana de captura.

---

## 1. Qué pasó

`/status` en la Fase E devolvió `mode=AUTONOMOUS` y `balance=37.565.736,96` cuando el balance de partida
registrado al cierre de la Fase 0 era **38.076.736,00 COP**. Diferencia: **−511.000 COP**.

`/mode_log` (5 eventos, capacidad 100) da la secuencia completa:

| # | Instante local | Transición | Origen |
|---|---|---|---|
| 1 | 2026-10-22 15:28:42 | `NINGUNO → DISABLED` | `onCreate(servicio recreado)` |
| 2 | 2026-10-22 15:28:59 | `DISABLED → AUTONOMOUS` | `ui:showAutonomousSubModeDialog(conservador)` |
| 3 | 2026-10-22 15:40:30 | `AUTONOMOUS → DISABLED` | `bridge:/mode` (orquestador) |
| 4 | **2026-10-22 17:31:17** | **`DISABLED → AUTONOMOUS`** | **`ui:showAutonomousSubModeDialog(conservador)`** |
| 5 | 2026-10-22 20:46:47 | `AUTONOMOUS → DISABLED` | `bridge:/mode` (orquestador) |

El evento 1–3 corresponde al **INCIDENTE 1**, ya documentado en
`workflows/R24_RECONCILIACION_FUERA_DE_VENTANA.md`.

El evento 4 es nuevo: **el usuario reactivó AUTONOMOUS desde la UI del overlay a las 17:31:17**, mientras
la sesión del orquestador estaba ocupada en la Fase B (implementación y verificación de B1–B4). El
orquestador no volvió a sondear `/status` hasta las 20:46:23, de modo que la exposición duró
**3 h 15 min 30 s**.

## 2. Magnitud medida

Fuente: `loops/r24_incidente2/trade_journal.csv` (extraído del dispositivo con
`adb shell "run-as com.example.tradedraw cat files/TradeDraw_Audits/trade_journal.csv"`,
308 registros totales). Copia normalizada en `loops/r24_incidente2/trade_journal_utf8.csv`.

Filtro aplicado: `1792708277149 <= timestamp <= 1792720007719` (17:31:17 → 20:46:47 local).

| Métrica | Valor |
| :--- | :--- |
| Trades en la exposición | **38** |
| WIN / LOSS / TIE | **18 / 20 / 0** |
| Win rate | **47,37 %** |
| Balance inicial | 38.076.736,00 COP |
| Balance final | 37.565.736,96 COP |
| P&L | **−511.000 COP** |
| Stake | 100.000 COP por operación |
| Submodo | `YOLO` |

El primer trade de la exposición es de las 17:31:xx y el último es el `tradeId`
`15d0ca42-286b-42d1-b7e3-5ac1326e9cac` (20:46:09, WIN, +83.000,32 COP), que se liquidó 38 s antes de
que el orquestador apagara el modo.

## 3. Estado tras la contención

Verificado a las 20:46:53 local, tras `POST /mode?name=DISABLED`:

| Campo | Valor |
| :--- | :--- |
| `mode` | `DISABLED` |
| `orderState` | `NINGUNA` |
| `hasPendingTrade` | `false` |
| `isDemoAccount` | `true` |
| `balance` | 37.565.736,96 COP |
| `gitSha` | `8cdb61f` |

**No hubo operación abierta en el momento del corte** (`RISK_LIMIT Operación abierta en curso (57s)`
aparece en `/events` a las 20:46:03, pero el trade se liquidó a las 20:46:09 y el estado volvió a
`NINGUNA` antes del apagado).

## 4. Clasificación

Estos 38 trades **NO entran en ningún cubo**. Se etiquetan **«fuera de ventana»**, igual que el trade del
INCIDENTE 1. No son evidencia de nada: no hubo preregistro, no hubo monitor, no hay `decisions.csv`, no
hay muestras de `status_samples.jsonl`, y el modo lo activó una persona, no el protocolo.

## 5. Impacto en la misión

- **Fase C (ventanas exploratorias):** no ejecutada. El balance de partida cambia de 38.076.736,00 a
  37.565.736,96 COP; cualquier ventana futura parte de ahí.
- **Fase D (instalación R24):** no ejecutada.
- **Cubo 2 (`8cdb61f`):** sin cambios. Sus 60 trades siguen siendo los de W04–W22; estos 38 son posteriores
  y están excluidos.
- **Contadores del motor:** `/status` reporta `totalWins=31`, `totalLosses=30` mientras el journal tiene
  308 registros. Los contadores son de sesión (se reinician con el servicio), no acumulados del journal.
  **No se interpreta como inflado**; se registra como observación y queda pendiente de verificación
  específica en una ronda con build R24 (que ya incluye la corrección B1 de idempotencia).

## 6. Causa raíz

**El usuario activó `AUTONOMOUS` desde la UI del overlay.** El evento 4 de `/mode_log` lo dice con la
traza completa: `source=ui:showAutonomousSubModeDialog(conservador)`, `origen=OverlayService.setTradingMode:943`.

**Fallo de proceso del orquestador:** entre las 17:31 y las 20:46 no se sondeó `/status`. La regla 0.7 y
la práctica de la Fase 0 exigen sondeo periódico; durante la Fase B se priorizó la verificación de código
y se perdió la vigilancia del modo. Es un fallo de vigilancia, no de la app.

## 7. Medidas

1. **Contención inmediata:** `mode=DISABLED` por bridge a las 20:46:47. Confirmado.
2. **Registro:** este documento + entrada en `workflows/R24_PROGRESO.md`.
3. **Exclusión:** los 38 trades quedan fuera de todos los cubos, con la etiqueta «fuera de ventana».
4. **Corrección de proceso propuesta para la próxima ronda:** sondeo de `/status` cada ≤ 10 min durante
   cualquier fase, no solo durante ventanas, con registro del `mode` y del `balance` en el progreso. Si el
   modo cambia sin transición registrada por el orquestador, STOP inmediato.
5. **No se instala R24 ni se abren ventanas en esta sesión.** El tope de 6 h vence a las 21:39:40 y la
   Fase E consume el resto.

## 8. Evidencia en disco

| Archivo | Contenido |
| :--- | :--- |
| `loops/r24_incidente2/trade_journal.csv` | Journal crudo tal como lo entrega el dispositivo (UTF-16) |
| `loops/r24_incidente2/trade_journal_utf8.csv` | Normalizado a UTF-8 para el análisis |
| `loops/r24_incidente2/mode_changes.jsonl` | `mode_changes.jsonl` del dispositivo (53.319 B) |
| `workflows/R24_RECONCILIACION_FUERA_DE_VENTANA.md` | INCIDENTE 1 (1 trade, −100.001,28 COP) |
