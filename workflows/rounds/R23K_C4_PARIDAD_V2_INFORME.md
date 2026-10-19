# R23K C4 — PARIDAD v2 TICK A TICK: INFORME DEL SWEEP W05–W11

> **RETROSPECTIVO Y NO INDEPENDIENTE.** Estas ventanas ya se usaron para construir la memoria
> de anti-patrones del teléfono y para calibrar el arnés. El resultado detecta divergencias
> groseras; **no** valida el arnés. La validación real exige ventanas posteriores a su construcción.

**Generado por:** `scripts/validation/compare_live_replay_v2.py --sweep`
**Ejecutor:** HERMES (`w1:pA`)
**SHA comparador v2:** `535db9894dba3a01737f91c3ed7268c48dd9c96c6ab9e0af56777f3d65f1a9f9`
**SHA replay v2:** `a3f1c1d12a82cf4cfcfb6dde2f430ee19b2df5b81c5cf8c987680cd91fdc2fd2`
**Tabla cruda regenerable:** `R23K_C4_PARIDAD_V2_TABLA.md`

---

## 1. VEREDICTO

**El arnés v2 no alcanza los criterios de B5 en ninguna ventana.**

| Criterio | Resultado |
|---|---|
| B5(i) reproducción de entradas | `NO EVALUABLE` en las 7 ventanas (ninguna llega a 20 entradas Live) |
| B5(ii) reproducción del tipo de bloqueo | `NO EVALUABLE` en las 7 (ninguna llega a 200 decisiones comunes) |
| B5(iii) cobertura ≥ 70% | `CUMPLE` en 5 de 7, `FALLA` en W05 y W06 |

La concordancia de tipo va del **15% al 85%**, con mediana en torno al 50%. Muy por debajo del 90% exigido para considerar el arnés listo.

---

## 2. TABLA DEL BARRIDO

| Ventana | Live | Replay | Con par | Cobertura | Concordancia | B5(i) | B5(ii) | B5(iii) |
|---|---|---|---|---|---|---|---|---|
| W05 | 88 | 206 | 39 | 44,32% | 15,38% | NO EVALUABLE | NO EVALUABLE | FALLA |
| W06 | 99 | 393 | 59 | 59,60% | 84,75% | NO EVALUABLE | NO EVALUABLE | FALLA |
| W07 | 54 | 388 | 52 | 96,30% | 48,08% | NO EVALUABLE | NO EVALUABLE | CUMPLE |
| W08 | 90 | 385 | 69 | 76,67% | 21,74% | NO EVALUABLE | NO EVALUABLE | CUMPLE |
| W09 | 104 | 395 | 78 | 75,00% | 73,08% | NO EVALUABLE | NO EVALUABLE | CUMPLE |
| W10 | 45 | 383 | 39 | 86,67% | 58,97% | NO EVALUABLE | NO EVALUABLE | CUMPLE |
| W11 | 111 | 409 | 96 | 86,49% | 22,92% | NO EVALUABLE | NO EVALUABLE | CUMPLE |

**B5(i) no se puede evaluar en ninguna ventana** porque el Live registra 2-3 entradas por ventana, muy por debajo de las 20 exigidas. Ese umbral se fijó pensando en el cubo agregado, no en ventanas sueltas: para evaluarlo hay que acumular.

**B5(ii) tampoco:** el máximo de decisiones comunes es 96 (W11), menos de la mitad de las 200 exigidas. Igual que (i), es un criterio de cubo.

---

## 3. DIAGNÓSTICO: POR QUÉ LA CONCORDANCIA ES TAN BAJA

### 3.1 Matriz de confusión de W11 (la ventana con más datos)

| Tipo Live | Ocurrencias | Reproducidas | % |
|---|---|---|---|
| `ADAPTIVE_BLOCK` | 57 | **0** | **0,0%** |
| `VETO_BUY_EXHAUSTION_CLIMAX` | 31 | 18 | 58,1% |
| `V1` | 12 | **0** | **0,0%** |
| `RISK_LIMIT` | 8 | 4 | 50,0% |
| `ENTRADA` | 3 | **0** | **0,0%** |

Errores dominantes:

```
ADAPTIVE_BLOCK  -> COOLDOWN                     23 casos
ADAPTIVE_BLOCK  -> RISK_LIMIT                   16 casos
V1              -> VETO_BUY_EXHAUSTION_CLIMAX   12 casos
VETO_BUY_EXHAUSTION_CLIMAX -> COOLDOWN          11 casos
```

### 3.2 Las dos causas, aisladas

**Causa A — El replay v2 no modela `ADAPTIVE_BLOCK` con la memoria real.**

El replay arranca con `memoria_perdidas` vacía y solo la alimenta con trades que él mismo resuelve, pero **el replay v2 no resuelve trades**: mide decisiones. La memoria nunca crece, y `ADAPTIVE_BLOCK` es estructuralmente imposible que dispare. De ahí el 0% en las 57 ocurrencias de W11.

No es un defecto del comparador: es una carencia del replay. El mecanismo para inyectar la memoria real ya existe (`ADAPTIVE_STATE_JSON` en el arnés v1), pero no se conectó al v2.

**Causa B — El orden de guards produce un tipo distinto al del Live.**

El replay evalúa `COOLDOWN` antes que `VETO_BUY_EXHAUSTION_CLIMAX`, y su máquina de estados avanza el reloj con cada entrada simulada. En el Live, el cooldown depende de `lastTradeResolutionTime`, que solo se actualiza al **liquidar** un trade real. Sin settlement simulado, el reloj del replay avanza de otra forma y enmascara el bloqueo verdadero.

Consecuencia: 23 `ADAPTIVE_BLOCK` del Live aparecen como `COOLDOWN` en el replay. El replay no "se equivoca de motivo": su máquina de estados no tiene la misma noción de tiempo que el motor real.

### 3.3 Lo que sí funciona

`VETO_BUY_EXHAUSTION_CLIMAX` se reproduce al 58% cuando el par se forma, y `RISK_LIMIT` al 50%. Ambas son **funciones puras sin estado**: el replay las calcula con el mismo `calcularPoderesDeMercado` y la misma compuerta de umbral.

**La parte determinista del motor sí se replica.** Lo que falla es todo lo que depende de estado acumulado — y ese es justo el terreno que B5 pretende medir.

---

## 4. LO QUE ESTE SWEEP DEMUESTRA Y LO QUE NO

**Demuestra:**

- El comparador v2 funciona: alinea por timestamp, clasifica tipos, calcula cobertura y concordancia, y aplica B5 distinguiendo `NO EVALUABLE` de `FALLA`.
- La cobertura del 44–96% indica que el replay produce decisiones en los mismos instantes que el Live: el problema no es de alineación temporal.
- Las funciones puras replicadas coinciden con Kotlin, verificado con 37 vectores dorados en `replay_v2_tick --self-test`.

**No demuestra:**

- Que el arnés v2 esté listo para declarar paridad. No lo está.
- Nada sobre ventanas nuevas: todo es retrospectivo sobre ventanas que ya participaron en la calibración.

---

## 5. QUÉ HARÍA FALTA PARA QUE EL ARNÉS v2 SEA VÁLIDO

1. **Conectar la memoria real de anti-patrones** al replay v2 (mismo `ADAPTIVE_STATE_JSON` del v1). Sin eso, `ADAPTIVE_BLOCK` nunca dispara.
2. **Modelar el reloj de cooldown como el Live**: `lastTradeResolutionTime` se actualiza al liquidar, no al entrar. Hay que inyectar los instantes de resolución desde `trades.csv`.
3. **Implementar la compuerta V1** (exclusividad S/R ↔ anti-chop), hoy ausente: explica los 12 casos de W11.
4. **Re-congelar el SHA** del replay v2 tras esos cambios y re-etiquetar como obsoleta cualquier medición previa.

Ninguno está en el alcance de R23K. Son el contenido mínimo de un ciclo posterior.

---

## 6. ESTADO DEL CUBO

Este barrido **no abre ni cierra cubos**. No contiene ventanas nuevas: analiza W05–W11, que ya pertenecen al Cubo 2 de `8cdb61f` y ya están contabilizadas en su `N`.

`R24_MANIFEST.md` queda preparado pero **no debe activarse** mientras el arnés v2 no supere los criterios. Abrir R24 con este arnés produciría `NO EVALUABLE` en los tres criterios, con razón.
