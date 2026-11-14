# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 08:00:50 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23c_yolo_validation/window_05\decisions.csv`  
**Fuente Replay:** `loops/r23c_yolo_validation/window_05\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `63` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `2` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`2`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `61` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `candle_second` | **1** | 50.0% |
| `dist_support` | **1** | 50.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `558ba716-3993-39ae-8b80-fe7268921a64` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d6129f38-c2c6-3549-a88e-0d813a9bb196` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ed08e9ed-4c7f-303c-8edd-16e53bb48452` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b367ecd2-162d-322d-9b9e-cb950459ac86` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ea7a0f5c-de9c-304f-a221-41763a504275` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dff4c203-92e7-3888-b4d3-ff277f6ad442` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4f8f5265-0322-3728-a6eb-b3d6b4889976` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2682ec1b-0ff6-36fa-aa6a-3db6f04d83f4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3d92ab30-7dcb-3861-896e-bbcb5ceebd4e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a5c2b72b-080f-3119-a226-7ff38d39265c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7e141009-9100-3601-aef4-9ea7c2fb541d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1caf926d-3ec6-3d94-9c2e-3b25cff5ca25` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `adf9a438-c9ee-3b31-9b4a-c5922a2c2ee7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `eecef026-fff6-304c-a358-f24cbcc85d06` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7bcc9066-720a-306d-9a37-f85dcd858318` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c5a15dc3-f251-3b19-a1ed-889641d921d3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `ac8c97a6-b760-355f-873f-f15c9fddb81c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4bd8a145-4c54-31c1-8be1-8feb49dda5e0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8ace4e5f-6820-377d-b2a2-fda7d254c913` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d9ef7817-892e-3815-857a-2ae5384bec6d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3be26b24-e6b1-3d36-a635-9090146b1c60` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dbd02602-ef27-390f-8cd4-356ba94d8fe6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2b5b681d-8632-3cbf-8fac-fbfeafdef0a0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e49cf130-1964-3241-b9db-7e7eb1a8fa92` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `35d90863-a576-3a57-a5bc-e1796207561b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ad9e7984-0260-3938-b74d-8bac73e9ac2f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `972a05e3-99b1-3f3c-9266-1572f02c02ed` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `30b5fa3e-fe1e-3f2b-92c7-dc822e150226` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ecbc7b58-3774-3848-85d9-7c207b926024` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `997c5559-54fb-3a00-9e67-fe28f61c32a1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `20382217-1621-3394-85c5-e50046c3b33d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dbf544e0-68f9-3ed8-a02c-d46fab40a103` | `DIVERGENCE` | `candle_second` | `4` | `2` | `SELL` / `SELL` |
| `574b2bfd-0b18-3953-92aa-ad622063fa34` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e24a4111-9ed9-3bec-bb23-437dcd50b018` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1778a616-ee08-3788-8ae5-9d678d5678b5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7359d813-5d3a-3751-80fb-ccaf6472b4ba` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1a64e303-6575-3c21-8a82-f6854a59efaf` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6e2a38ce-4ea2-353e-b298-463e2d43ddea` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ee7c3c5d-49e4-3f76-814a-ba3ddc8bb700` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f9e8fae2-2e8f-304e-99b7-1d930d8782de` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `db12f97a-62dd-3276-ac4c-a84b0b68307b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2a9a4836-ac76-309c-90dc-41ff280db7bc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a474a086-35ee-3458-86d7-633fc9784e59` | `DIVERGENCE` | `dist_support` | `0.0` | `0.75` | `NONE` / `SELL` |
| `a34b41f3-6e99-31cb-b9a9-e30a7941f442` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `6889f8ee-1570-3579-a553-08a63cc96498` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dd95cf93-0b86-3542-a6f2-9bd3cddbe362` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0ddc34a1-29e8-3cb1-8de0-2a384e2f4f51` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `44c609b6-b840-35d3-9782-fa81542a799c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c3ba83a2-305d-3ac3-9bb8-00878e5134f5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c72360b3-6a45-3696-bdd6-1ade0ff308b0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `918e861a-560f-3ad0-907e-f56e9ba6ce0c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `12052492-05fc-365b-8d7b-8ab671a4f87c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bb5ad5d5-4d54-3241-b56b-a64b40c93d65` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a218723e-072b-3d60-be2b-e9387ca07eed` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a37d8e16-c789-3ac3-bb23-7c9f6b635d3a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6cc7fb7e-a25a-30c6-822f-a5b3778a7810` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `19766218-44a9-3e24-a0d0-248e25338fae` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bb086c5c-a6d2-397d-bcde-356813b5519e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cd36001b-f990-3c49-8394-6502c5983cf9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e8737e8c-f653-3198-9f27-842852b8bc5f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b14f234f-ec4b-3433-bc5f-defc736c08aa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `afbd3a30-ecd9-3e4b-9174-518f4a845b0a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1c6f1545-aa4c-3ed7-95a5-1e8eda0843b6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).