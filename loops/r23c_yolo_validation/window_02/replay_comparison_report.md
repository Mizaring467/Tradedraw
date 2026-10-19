# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 06:24:14 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23c_yolo_validation/window_02\decisions.csv`  
**Fuente Replay:** `loops/r23c_yolo_validation/window_02\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `95` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `5` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`5`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `90` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `dist_support` | **3** | 60.0% |
| `trend` | **1** | 20.0% |
| `dist_resistance` | **1** | 20.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `79ba3ef0-b4cd-3de9-99d3-e663f4bc1d61` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9298a424-c377-3036-8ae3-650c1f1322d1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `54e132ac-17c4-32be-9106-2418fc022a68` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `939f0dbd-bff6-36e1-97b5-28bf4b49711e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `30793f96-7c48-30b7-90c7-5be91c6b934c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `54e8bbd7-a445-32f1-96f1-df6912078be7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `86bbfb0f-97d5-3e2b-a812-3e60b61980c4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `87eea043-4a09-3596-8cf5-b360aee229ec` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a2ad680f-4cf7-31fd-bf39-c144697413e9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `00ec8b95-2587-396a-8e85-eb5493e1da73` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4011e137-d7a4-39f8-aad7-a16cd705e3b9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `264c7003-86db-36fa-a37c-57d62ae18cdd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bcca6fd3-f827-3206-9c80-ddb1d14c40a2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `410cd0ab-9c0f-3076-8efb-b14e7aff12bc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ffc4487b-7f06-35e8-b9d8-b220be2ea306` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3270779f-2922-33e3-ba72-0ef5e3df6c3e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3d7cd15e-7682-35f2-a8ca-fa680cf1c9b6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cc6145cb-e9c1-31ca-80a7-e635f5fb39bb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d4554b5b-30a2-334f-b03c-b323252dd905` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `933d02bb-5271-3134-bfd4-861d1b1a5390` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `248e04cd-d8db-36ec-939d-d2c6b0038485` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6f573413-891c-30ea-abc6-1b752147ec1f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `f8155c39-e194-3114-b84f-0ec1df70c557` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bd3f3b3c-c973-3f56-9e30-f389d5f96180` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5c3e45db-2311-3cd3-aff5-1b72137b8c44` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0d9e19ee-5ede-3593-8104-bd70c74c7aa4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5e661da4-66e0-33a2-a651-82312d0cae34` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fbddb7ac-5b9c-3f3f-be52-729e8d0af4f9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `099bfb84-5662-3314-b19b-5191e1926190` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2e1eac41-1d22-3760-a290-1e3f87a88ea9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `41408ca9-95e3-3c5d-aaad-d0680a4d79ba` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `49fe13e5-a667-3748-b7ea-923b80076aa6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cb3399b9-6ee4-34ef-ae68-4fd44a7d78c4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e146ebe9-8873-3e10-9b72-f30d041f16f3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `28abb3cb-b345-31d3-9410-757aa5760395` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `02c590b9-e140-337f-a5e2-43d2e1fb56e3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `08cdf266-9668-36fb-93f9-8d3662ce4370` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6083154e-8314-3f8c-8d25-a8d171d6867d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `207c23df-3d51-32d8-a7a2-77f269897eb5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `528ff6a1-ca8c-3cfd-9fad-b6bbee2682a5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b997bed3-038f-32db-be80-829abe373159` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `643dfe49-9196-3353-bf7b-365de9eb3649` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `de9d5cc7-42ca-33e1-8cfc-273e4b850541` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `c21e21e2-44af-3d69-b434-79034a516ccb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cdff81b9-3798-3926-9ee9-999802fd6bf5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `377da348-d12c-3fde-b628-915c45d53ad9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `439850d0-7c21-3c6b-b77d-f8db28111aae` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `55844f56-1395-3491-863c-c21de5048490` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ac978f90-cce8-398f-942e-9ae3e6d071d0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6f976532-a519-3dc5-89c2-c039cf890bc6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6ae60a38-ca9b-3ec0-b789-58d93c1c78b3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e735ed0e-abd8-305f-b15e-361f222b8b69` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1e9f30cf-f75b-3875-8e19-27231e8f17fc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8330f2e3-c75c-32e0-8419-66fa480ca148` | `DIVERGENCE` | `trend` | `UPTREND` | `DOWNTREND` | `NONE` / `SELL` |
| `38653e1b-b76d-3e28-bc62-488cf9273f57` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bbc22697-c45b-3de9-af96-5833bde26f2a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1c010e2e-a772-3f34-a1bc-19a88d184c7e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2830b460-a3e8-3e46-a72a-827f38c175fe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4b40505c-9800-3976-981b-a22995f2e068` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cbea36e7-eb23-3a35-89bf-52cba1567636` | `DIVERGENCE` | `dist_support` | `0.0` | `0.03` | `NONE` / `BUY` |
| `b6494080-b46e-39e1-b11d-6599e40c0e97` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `8f56fdfe-731d-37a7-9fc6-c625c48d5003` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `033ce74e-ba09-3604-a45b-5014f17324fa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5c668f21-c4d8-35dc-a33d-e4bd787685da` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a55725b8-32d3-32dc-a8f2-36c21016f0ba` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `05088155-0bf4-3d79-a0f0-788bda8b94f8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9688756d-6649-367e-a3c4-4e1f6ee85a9e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0718fd21-356b-31d3-ae3f-7c8f61950287` | `DIVERGENCE` | `dist_support` | `0.0` | `0.03` | `NONE` / `BUY` |
| `3b598c6b-aa8b-3552-9501-e3317fc79d0c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c660c706-63b9-390a-beae-75d1a2b8fa2c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `800865f2-c1ac-3fcc-bafa-9858c25436d1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `72f543bf-297b-334e-abb2-3397f882e95f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `341e8918-ecfb-3529-9ee1-75f9435b8659` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `72aed646-dc32-3eab-b3ae-07c5d289923f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0364fd3f-98f7-3a0c-a246-538690aaee0a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ee5957b4-be00-35b8-a7a8-6acc26c2115f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `808dae41-2aa9-3161-b5b7-d8693e27d831` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3e2b7bf3-80ee-375a-9b17-23f0562e1577` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d89498c4-c3a3-39b1-a4ff-006393d6ee5f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `45892374-d109-3ff6-8f03-ec1f30c587aa` | `DIVERGENCE` | `dist_support` | `0.0` | `0.23` | `NONE` / `BUY` |
| `feedc3c8-32d7-37df-85ad-15bd8836d931` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2b7c26d2-c0dc-3afe-9fd5-f61518f0e1f9` | `DIVERGENCE` | `dist_resistance` | `0.0` | `0.75` | `NONE` / `BUY` |
| `cdd05f3a-2cb4-30fb-8587-01d1bbff7418` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6b56c82f-0286-3795-be82-7300f54ffaba` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f7514fa8-6808-38dd-933e-2ca7ac699e9f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d52a1a8a-3b8a-37bf-b32e-e5beab37a4e4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7b25cd93-8fbf-302a-980a-e2cfdfc33eec` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `3c2acd7c-df60-37d0-8d3a-35da66895378` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ed428107-e22d-3f03-82be-4b8de2ee7574` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cd1e8ac8-46bb-37f9-82fb-e3dc2022d9af` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e789ae3c-de92-3807-840d-2e96ec375cc8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9b657d98-e8e6-3b23-b4d9-b0f5b9cd338b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3dae0992-32f0-36ea-9265-2c94ca7510c0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `986c8138-b792-3141-957f-2299b3256ca3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8bfdd83c-36fb-399c-ae14-7ed53ada11b9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).