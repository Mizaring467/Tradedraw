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
| Replay tick-a-tick (B1..B4) | `scripts/validation/replay_v2_tick.py` | `44f77d890142fffc1a9026b59beb2c2e4562a20d4548f25faa52b32734c60845` |
| Comparador v2 | `scripts/validation/compare_live_replay_v2.py` | `535db9894dba3a01737f91c3ed7268c48dd9c96c6ab9e0af56777f3d65f1a9f9` |
| Suite de guards v2 (B1..B4) | `scripts/validation/test_replay_v2_guards.py` | `7a755d575b6b401657a16491ea051fbcf5ec529bbe270d61a125664372b0c39a` |
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
sha256sum compare_live_replay_v2.py         -> 535db9894dba3a01737f91c3ed7268c48dd9c96c6ab9e0af56777f3d65f1a9f9
sha256sum replay_v2_tick.py                 -> 44f77d890142fffc1a9026b59beb2c2e4562a20d4548f25faa52b32734c60845
sha256sum test_replay_v2_guards.py          -> 7a755d575b6b401657a16491ea051fbcf5ec529bbe270d61a125664372b0c39a
```

Si cualquiera falla, la ventana no arranca.

---

## 6. ADVERTENCIA SOBRE EL ESTADO MEDIDO DEL ARNÉS v2 TRAS B1..B4

En R24 Track B, Hermes (`w1:pA`) completó la implementación B1..B4 (`workflows/rounds/r24_track_b_hermes.md`):
- Memoria `ADAPTIVE_BLOCK` real sin look-ahead (B1).
- Settlement y cooldown modelados desde ledger real (B2).
- Compuerta V1 portada de `HudCoherence` / `getEngineReasoning` (B3).
- Orden de guards corregido (`ventana -> COOLDOWN -> agotamiento -> ADAPTIVE_BLOCK -> RISK_LIMIT -> V1 -> entrada`) (B4).
- Suite determinista con 54 tests OK y 61 vectores dorados en `replay_v2_tick --self-test`.
- Las mediciones previas de W05–W11 del manifiesto original quedan **obsoletas**.

**Hallazgo empírico crítico:**
El arnés es correcto por construcción, pero **está ciego sin dos insumos del dispositivo**:
1. **Ratios S/R reales en telemetría:** Reconstruir $ds/dr$ offline desde ticks da $0.995/1.000$ donde el HUD del teléfono registra $0.14/0.65$. Con placeholder $0.50$, la similitud máxima contra firmas reales no supera $0.66$ (imposible alcanzar el umbral $0.99$). Se requiere que el APK exporte `distanceToSupportRatio` y `distanceToResistanceRatio` por muestra.
2. **Exportación de memoria al cierre:** El TTL de 24h de `loadState` purga las firmas históricas. Se requiere volcar `adaptive_learning_state.json` al cierre de cada ventana.

**Conclusión regulatoria vinculante:**
El arnés v2 **NO se congela como "arnés listo"**. Abrir el Cubo R24 con este estado produciría `NO EVALUABLE` en los tres criterios de B5 con razón.
R24 permanece **PREPARADO, NO VIGENTE**. El único cubo activo y acumulando es el **Cubo 2** bajo `8cdb61f`.
