# R23K — AUDITORÍA CONTRAFACTUAL DEL VETO `BUY_EXHAUSTION_CLIMAX`

**Generado por:** `scripts/validation/counterfactual_exhaustion_climax.py`
**Ejecutor:** HERMES (`w1:pA`)
**Naturaleza:** análisis contrafactual sobre datos históricos — **no** es evidencia
out-of-sample de rentabilidad de la estrategia, ver §5.

---

## 1. VEREDICTO

### `NO CONCLUYENTE (IC cruza el breakeven)`

WR contrafactual 59.26% con IC95% [40.73%, 75.49%], que contiene el breakeven 54.64%. N = 29 supera el mínimo pero la evidencia no alcanza para decidir en ninguna dirección.

- Breakeven vigente (payout 0.83): **54.64%**
- Señales únicas out-of-sample: **29** (mínimo exigido: 20)
- Señales únicas in-sample: **0**

---

## 2. QUÉ MIDE ESTE SCRIPT

Responde una pregunta contable, no una pregunta de rentabilidad: **si las entradas
que el veto suprimió se hubieran ejecutado, ¿habrían ganado o perdido a 60 s?**

- **WIN contrafactual** = el precio a 60 s subió → el veto impidió una ganancia.
- **LOSS contrafactual** = el precio a 60 s bajó → el veto impidió una pérdida.

Un veto es útil si produce **más LOSS que WIN**, es decir, si su WR contrafactual
queda **por debajo** del breakeven. Es contraintuitivo y conviene tenerlo presente
al leer la tabla: aquí un win rate alto es una **mala** noticia para el veto.

---

## 3. RESULTADOS POR SEGMENTO

| Segmento | Ventanas | Filas veto crudas | Señales únicas | W | L | TIE | Sin resolver | WR contrafactual | IC95% Wilson |
|---|---|---|---|---|---|---|---|---|---|
| In-sample (R23G) | 7 | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| Out-of-sample (W04–W11) | 8 | 189 | 29 | 16 | 11 | 2 | 0 | 59.26% | [40.73%, 75.49%] |
| **CONSOLIDADO** | 15 | 189 | 29 | 16 | 11 | 2 | 0 | 59.26% | [40.73%, 75.49%] |

### 3.1 Desglose por ventana

| Ventana | Segmento | Vetos crudos | Señales únicas | W | L | TIE | Sin resolver | WR | IC95% |
|---|---|---|---|---|---|---|---|---|---|
| R23G-W01 | in-sample | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| R23G-W03 | in-sample | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| R23G-W04 | in-sample | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| R23G-W05 | in-sample | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| R23G-W06 | in-sample | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| R23G-W07 | in-sample | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| R23G-W08 | in-sample | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| W04 | out-of-sample | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| W05 | out-of-sample | 0 | 0 | 0 | 0 | 0 | 0 | n/a | n/a |
| W06 | out-of-sample | 58 | 8 | 4 | 4 | 0 | 0 | 50.00% | [21.52%, 78.48%] |
| W07 | out-of-sample | 10 | 1 | 1 | 0 | 0 | 0 | 100.00% | [20.65%, 100.00%] |
| W08 | out-of-sample | 15 | 4 | 3 | 1 | 0 | 0 | 75.00% | [30.06%, 95.44%] |
| W09 | out-of-sample | 51 | 5 | 4 | 0 | 1 | 0 | 100.00% | [51.01%, 100.00%] |
| W10 | out-of-sample | 24 | 7 | 3 | 3 | 1 | 0 | 50.00% | [18.76%, 81.24%] |
| W11 | out-of-sample | 31 | 4 | 1 | 3 | 0 | 0 | 25.00% | [4.56%, 69.94%] |

---

## 4. DEDUPLICACIÓN: POR QUÉ N ES TAN PEQUEÑO

El motor reevalúa la señal en **cada tick** dentro de la ventana sniper, así que una
sola intención de compra en techo deja entre 2 y 11 filas de veto separadas por
milisegundos. Contarlas todas multiplicaría N por 5–10 y fabricaría significancia
estadística donde no la hay.

Se colapsa **por vela M1** (`ts // 60_000`) conservando el **primer** instante de la
ráfaga: es el momento en que el veto actuó y el que un operador habría visto.

Colapso medido: **189 filas crudas → 29 señales únicas**
(factor 6.5×).

---

## 5. REGLA EPISTÉMICA Y ALCANCE

1. **N mínimo 20 fuera de muestra.** Por debajo, dictamen obligatorio
   `NO CONCLUYENTE (Muestra insuficiente, N < 20)`.
2. **El IC de Wilson manda, no la WR puntual.** Un 70% con IC [45%, 88%] no sostiene
   nada: contiene el breakeven.
3. **In-sample ≠ out-of-sample.** R23G es la muestra que originó la hipótesis del veto;
   sirve de referencia, nunca de confirmación.
4. **Esto no mide rentabilidad de la estrategia.** Mide el signo de las decisiones
   suprimidas. Un veto con buen contrafactual puede convivir con una estrategia sin
   edge, y al revés.
5. **Sin ajuste post hoc.** Los parámetros (t+60 s, ±4 s, payout 0.83, N≥20) están
   congelados en el código antes de ver ningún resultado de esta corrida.

---

## 6. LIMITACIONES DECLARADAS

- **R23G no contiene vetos de agotamiento.** El veto se implementó en R23H, posterior a
   R23G. Por eso el segmento in-sample sale con las ventanas presentes pero **N = 0**
   salvo que existan filas con el token, y así se reporta: no se rellena el hueco.
- **El contrafactual ignora el spread y el slippage del broker.** Usa precios del feed
  WebSocket, que no incluyen la horquilla de ejecución real.
- **El contrafactual asume que la entrada habría ocurrido al precio del tick del veto.**
  En vivo, el tap llega ~4-5 s después por la latencia de Android Go.
- **TIE se excluye del denominador** del WR, igual que hace el motor con los empates.

---

## 7. REPRODUCIBILIDAD

```bash
python scripts/validation/counterfactual_exhaustion_climax.py
python scripts/validation/counterfactual_exhaustion_climax.py --self-test
```

Determinista: mismo input → mismo output. Sin red, sin reloj de pared, sin azar.
