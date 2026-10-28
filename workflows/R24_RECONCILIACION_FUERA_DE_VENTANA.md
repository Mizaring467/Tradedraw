# R24 · RECONCILIACIÓN DE TRADES FUERA DE VENTANA

**Fecha:** 2026-10-22 15:41 America/Bogota
**Orquestador:** Antigravity 2.0
**Build medida:** `8cdb61f` (versionCode 19, `R23H-BUY-EXHAUSTION`), ancestro de HEAD `190f596` (rama `r10-night`).
**Serial ADB medido:** `[fe80::e8c5:80ff:fef0:d5f9%4]:5555`

## 1. Discrepancia detectada

| Concepto | Valor |
| :--- | :--- |
| Balance de cierre W22 (declarado) | 38.176.737,28 COP |
| Balance real en `/status` (15:40) | 38.076.736,00 COP |
| Delta | **−100.001,28 COP** |

## 2. Causa atribuida (verificada, no supuesta)

Fuente primaria: `files/TradeDraw_Audits/trade_journal.csv` del dispositivo, extraído a
`loops/r24_reconcile/trade_journal_live.csv` (271 líneas).

Última fila del journal:

```
1792701554862,2026-10-22T15:39:14,AUTO_ADAPTIVE,CONSERVATIVE,SELL,0.43,641.867310,100000.00,38176737.28,LOSS,38076736.00,72,DOWNTREND,0.09,0.28,0.00,NEUTRAL,TRENDING,1,LOSS,"SALDO (-) Pérdida confirmada tras liquidación: Diff=-100001.28000000119 COP (72s)",1792701481889,1792701482035,1792701482277,2,641.867304,641.867304,dfee56d1-0a3d-3894-bb98-c0f9a177aa2d,8226652e-6326-4c00-b805-7d79fba1c33a
```

- `tradeId`: `8226652e-6326-4c00-b805-7d79fba1c33a`
- `decisionId`: `dfee56d1-0a3d-3894-bb98-c0f9a177aa2d`
- Acción: SELL · Resultado: LOSS · Stake 100.000 COP · liquidación 72 s
- Despacho: `1792701481889` = 2026-10-22T15:38:01,889 (hora local)
- Balance previo 38.176.737,28 → posterior 38.076.736,00

**Es exactamente UN trade fuera de ventana.** El delta cuadra al centavo con el journal.

## 3. Contexto del suceso (de `/mode_log` y `/events`)

```
MODE_CHANGE de=NINGUNO a=DISABLED source=onCreate(servicio recreado)   atMs=1792700922975
MODE_CHANGE de=DISABLED a=AUTONOMOUS source=ui:showAutonomousSubModeDialog(conservador) atMs=1792700939058
MODE_CHANGE de=AUTONOMOUS a=DISABLED source=bridge:/mode            atMs=1792701630758
```

- `1792700922975` = 15:28:42,975 — servicio recreado (arranque de app).
- `1792700939058` = 15:28:59,058 — **el usuario activó AUTONOMOUS conservador desde la UI del overlay**.
- `1792701630758` = 15:40:30,758 — **el orquestador puso DISABLED por bridge** (dirección segura, Fase 0.c).

Ventana de exposición: **15:28:59 → 15:40:30 (≈ 11,5 min)**. Dentro de ella se generaron 7 señales
(`engine.signalsGenerated=7`), se despacharon 2 taps (`risk.tapsDespachados=2`) y se confirmó y liquidó
**1 trade** (el anterior). El segundo tap no llegó a confirmar trade.

Además: `feed.priceFrozen=false` pero `recentPriceRangeRatio = 3,5e-10` y los cuatro últimos trades del
journal comparten `priceY = 641.867310` con `entryPrice == exitPrice`. El activo `Z-CRY/IDX` está
prácticamente plano; el motor operó SELL contra un precio sin rango.

## 4. Clasificación y tratamiento

- **Etiqueta:** «FUERA DE VENTANA». **No entra en ningún cubo confirmatorio** (ni Cubo 2, ni Cubo E, ni Cubo R24).
- **No se borra ni se reescribe** ninguna fila del journal. El journal es append-only.
- El balance de partida de la primera ventana exploratoria (Fase C) es **38.076.736,00 COP**, no 38.176.737,28.
  Cualquier ventana que arranque con otro saldo inicial es INCIDENTE.
- El Cubo 2 se cierra con N=60 (W04–W22) **sin** este trade, tal y como estaba preregistrado.

## 5. Estado de seguridad tras la intervención

| Campo `/status` | Valor | Correcto |
| :--- | :--- | :--- |
| `mode` | `DISABLED` | Sí |
| `orderState` | `NINGUNA` | Sí |
| `hasPendingTrade` | `false` | Sí |
| `hasConfirmedTrade` | `false` | Sí |
| `isDemoAccount` | `true` | Sí |
| `gitSha` | `8cdb61f` | Sí |

## 6. Riesgo residual

`mode=AUTONOMOUS` puede reactivarse **desde la UI del overlay** sin que el orquestador lo sepa. El bridge
solo puede apagarlo, no impedir que se encienda. Mitigación adoptada para R24: el preflight de cada ventana
verifica `mode=DISABLED` inmediatamente antes de que el VALIDADOR arme el monitor, y el cierre de cada ventana
vuelve a poner `DISABLED` por bridge antes de soltar el candado.
