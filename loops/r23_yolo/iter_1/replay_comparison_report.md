# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 04:27:45 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23_yolo/iter_1\decisions.csv`  
**Fuente Replay:** `loops/r23_yolo/iter_1\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `62` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `1` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`1`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `1` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `60` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `dist_support` | **1** | 100.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `c1b24f83-762c-3d19-a495-9886085592dd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e00fda03-754e-3476-92f5-84074a57e7ae` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f3d32938-4993-336b-8652-8a811736d823` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d9805228-aa33-3db1-8482-645a6cb22036` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ac8056a4-effb-3ebe-bf69-7ef76c7542d3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1ce82fcb-75a1-3d82-b159-855e93d44241` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3f4cdb04-8152-3797-a79b-3bce48b4d774` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ba0633ad-402f-366c-a58d-8c49e4e1b383` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2c8023a6-8c6d-3422-bf5e-c3229d02103e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `597d261f-0516-30bf-8fee-72f770c4dc73` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `de9f0d49-3b90-324a-beae-eb53c11d12b7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c61284fb-ec58-35e7-9fc7-e86c3a86498d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `15c12313-a012-3516-bfbd-c287d6219c45` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `38a65b98-c49b-3bac-a446-6d494b2a5668` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1a7ff46f-6bcd-36de-8bbe-ce0d4fb101cb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `adff2e59-f3da-3586-8fb7-41d22a4b8787` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `221fca07-802d-36db-a05a-0b43025eb4bc` | `DIVERGENCE` | `dist_support` | `0.0` | `0.75` | `NONE` / `SELL` |
| `264394aa-a14d-301e-ac9f-a6749f4236d4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4f43d9e0-b9a4-31e1-a4f8-d9e18d3da1f3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `899a2b38-97d4-3a74-bd2a-be31dfa02f47` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bb4e83c2-beae-344e-bb68-04c78da3c380` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2d9ac21f-2c86-3f0a-92c8-3b68a6c1a0a0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7f3e10cf-b559-37ac-91e4-a31e9cfd6351` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `85441c53-a784-34a5-95b3-bc0f22ebc798` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ad0dd53e-4167-3e71-82ee-6a39e632cde9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8ba3de23-81bb-3303-87e2-33933078f28e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `34838c34-48c8-37c8-976b-50ead369d325` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2b21ed7f-6f60-3a59-8526-4ea65330530b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `27eec08b-eab3-3e45-a057-f5dcb2c3ca63` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9f788702-2398-3c8d-8bee-d8c16791a942` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `59db0d5f-1c1d-30bb-b7d7-f9ce4a91abd2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `71a78d7f-fce1-3d7c-b0d3-31bb8df14e9f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5ad07461-17ff-3f4c-880e-fffc76f052f6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3f447637-5ea3-354d-bac0-8b2f8e3de04a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2939dd08-9b19-3da7-82d8-ad2a41a8aced` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `02742313-d1d5-3881-9ca6-338ed89f7138` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d0e769bf-62eb-3ff4-a415-788bdf5a35c8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `94f9e87f-1916-385a-81f3-a927a544d40a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `40bca262-63ad-397b-968f-eeb073723ad4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2cbfd074-a275-35ee-b5b6-52f33a6d60fb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1db21533-88cf-39f0-9449-5ac83c4fe43d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `af29561a-382a-3689-8e67-a4ab2524a037` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `325603a8-5ea2-3f34-9a63-e78eb7f8180b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7a1fa32a-de51-3863-b9da-98eb214cd474` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `52ab0325-8d2f-354a-af2b-7caf8e1a47b6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `30aa8c21-88c4-3515-8d18-2c68cded478d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9707f256-d0f1-302b-9024-b95d998da955` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7408acda-10c6-3349-9270-5d4c75e089e4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2c0c94e5-97ba-3b73-9dc5-ba49bc57ff28` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `aa963c88-31bd-374b-b6cd-d2fa86ec6c2f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a529d332-938e-3873-a49d-88b73d0098d0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f6dd486b-c06b-3a2e-95ab-c20720d9e54f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dadc91c8-f996-3df4-99a7-5e709503e14c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5cf76e1c-06df-38c6-8b64-ca4973a744ca` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `00c09a4b-b1cd-3084-b570-c3ea913cfea0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `da7366ea-c106-37ac-92b9-d9f5100d9715` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c502a323-2bc7-3698-ab3a-ff0a25fba338` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0014f8f8-436f-31b9-bf91-405881fb30ff` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bd32770b-0670-3b78-b373-36eefc8ab632` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9baf2ec0-f3c2-365a-be4e-79a91f5faf7d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `409758ef-ee92-37c6-be25-526aa3a7ff23` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0b88ccf9-5d47-329b-a9e9-b6850d7ee812` | `MISSING_LIVE` | `presence` | `-` | `PRESENT` | `-` / `SELL` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).