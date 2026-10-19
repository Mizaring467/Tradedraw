# R24 — MANIFIESTO DEL CUBO (SIGUIENTE CONGELAMIENTO)

**Estado:** PREPARADO, NO VIGENTE
**Fecha de preparación:** 2026-10-19
**Preparado por:** HERMES (`w1:pA`)
**Vigencia:** al instalarse el build de R24 y congelarse su SHA. Hasta entonces, el cubo activo es el **Cubo 2** de `8cdb61f` bajo `workflows/PREREGISTRO_R23K.md`.

---

## 1. REGLA DE SEPARACIÓN

Este manifiesto **abre un cubo nuevo** en cuanto se instale un build distinto de `8cdb61f`.

| | Cubo 2 (activo) | Cubo R24 (este manifiesto) |
|---|---|---|
| Build | `8cdb61f` | pendiente de compilar |
| Comparador | `compare_live_replay.py` v1, SHA `4cb5082e…4b5f` | `compare_live_replay_v2.py`, SHA `535db989…a9f9` |
| Replay | arnés v1 (1 señal por vela) | `replay_v2_tick.py`, SHA `a3f1c1d1…fd2` |
| Estado | **abierto, acumulando** | preparado, sin ventanas |

**Los trades de un cubo NO se suman al otro.** Ni para alcanzar `N=80`, ni para consolidar WR. Un reporte que mezcle cubos se considera inválido y se rehace.

---

## 2. ARTEFACTOS DEL ARNÉS v2

| Componente | Ruta | SHA-256 |
|---|---|---|
| Replay tick-a-tick | `scripts/validation/replay_v2_tick.py` | `a3f1c1d12a82cf4cfcfb6dde2f430ee19b2df5b81c5cf8c987680cd91fdc2fd2` |
| Comparador v2 | `scripts/validation/compare_live_replay_v2.py` | `535db9894dba3a01737f91c3ed7268c48dd9c96c6ab9e0af56777f3d65f1a9f9` |
| Telemetría de acción | `scripts/validation/test_action_telemetry.py` | (tests de no-regresión, no se congela) |

**El comparador v1 queda intacto.** Su SHA `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` sigue siendo el congelado para el Cubo 2 y no se modifica retroactivamente.

---

## 3. CRITERIOS DE PARIDAD v2 (B5 del preregistro R23K)

| # | Criterio | Umbral |
|---|---|---|
| (i) | Reproducción de **entradas** | `>= 90%` sobre `>= 20` trades Live, misma acción en `±6 s` |
| (ii) | Reproducción del **tipo de bloqueo** | `>= 90%` sobre `>= 200` decisiones comunes |
| (iii) | **Cobertura** de decisiones Live | `>= 70%` |

Tipos distinguidos: `ENTRADA`, `ADAPTIVE_BLOCK`, `VETO_BUY_EXHAUSTION_CLIMAX`, `V1`, `V2`, `RISK_LIMIT`, `COOLDOWN`.

**Sin muestra suficiente un criterio es `NO EVALUABLE`, no `FALLA`.** Son estados distintos y confundirlos es el error que el preregistro intenta evitar.

---

## 4. CONFIGURACIÓN CONGELADA

Igual que el Cubo 2, salvo lo que cambie el build:

| Parámetro | Valor |
|---|---|
| Activo / RIC | Crypto IDX / `Z-CRY/IDX` |
| Modo / Submodo | `AUTONOMOUS` / `YOLO` |
| Cooldown | 8000 ms |
| Stake | 100.000 COP DEMO |
| Payout | 0.83 (breakeven 54.64%) |
| Estrategia | `AUTO_ADAPTIVE` con `VETO_BUY_EXHAUSTION_CLIMAX` |
| `ADAPTIVE_BLOCK` | 0.99f |
| Dispositivo | `UGU8KFUKM7E6CE8D`, broker `com.marketly.trading` |
| Monitor | v2 (gracia 150 s, `DISABLED` al cierre) |

---

## 5. CONDICIÓN DE ARRANQUE

Antes de la primera ventana de R24, verificar **con medición**:

```
git diff --stat <sha_r24> HEAD -- app/     -> vacío
/status.gitSha                             -> <sha_r24>
/status.isDemoAccount                      -> true
/status.health.state                       -> READY, reasons []
com.marketly.trading                       -> primer plano
/status.mode, /status.submode              -> AUTONOMOUS, YOLO
sha256sum compare_live_replay_v2.py         -> 535db989…a9f9
sha256sum replay_v2_tick.py                 -> a3f1c1d1…fd2
```

Si cualquiera falla, la ventana no arranca.

---

## 6. ADVERTENCIA SOBRE EL ESTADO MEDIDO DEL ARNÉS v2

El barrido retrospectivo W05–W11 (`R23K_C4_PARIDAD_V2_SWEEP.md`) dio coberturas del 44–96% pero **concordancias de tipo del 15–85%**, muy por debajo del 90% exigido. El desglose muestra que el replay v2 (a) no modela `ADAPTIVE_BLOCK` con la memoria real del teléfono y (b) clasifica como `COOLDOWN`/`RISK_LIMIT` decisiones que el Live resolvió como `ADAPTIVE_BLOCK`.

**Conclusión que debe quedar escrita:** el arnés v2 **no está listo** para declarar paridad. Está listo como instrumento de diagnóstico. Abrir el Cubo R24 con este arnés produciría un veredicto de paridad `NO EVALUABLE` en los tres criterios, con razón.

Corregir las dos causas exige tocar el replay v2, lo que **re-congela** su SHA y obliga a re-etiquetar cualquier medición previa. Ese trabajo pertenece a un ciclo posterior, no a R23K.
