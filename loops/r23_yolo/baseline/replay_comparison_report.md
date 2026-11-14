# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 03:44:06 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23_yolo/baseline\decisions.csv`  
**Fuente Replay:** `SYNTHETIC_FIXTURE_REPLAY`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `101` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `0` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`0`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `101` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

*(No se detectaron divergencias en las decisiones analizadas)*

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `d043152f-46f3-387f-b0ee-9e7426b0f055` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `24bfe00c-e370-30a4-b2b8-be79d9f6a501` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `67485814-0eef-3b41-a8af-cb999bb77438` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bb9b1f96-6a55-3632-98b7-c04a7a22c62b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `eab65100-3ded-3c92-aaa2-43fbcfa89da2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0132d5e6-6c61-3ab0-814f-b96e6c2f9a0e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `427abd3a-9388-360e-a73c-e5875f905bbf` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8ba81e1a-5db1-3ba5-a0e5-d6ddcb63124f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `45e8aa9a-7a5f-3387-aeb5-3df97c67a9cb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `22bef825-5e05-302e-b276-544b51256726` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `aa8f2e7f-0c41-3be1-89d8-ce3d3952a945` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c9d118c3-1911-3083-b072-f19f3ff4a41f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8268b7da-3aad-359b-86bd-cbbbcba8ac32` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a0dcc23e-4ab9-3091-97a6-190229c98a7d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b06cecaf-61de-38e5-a625-e642386108f0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ae5bc78c-a067-38da-8352-8148763ab23e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `123e7f1b-8665-3507-b18a-79f624ef58e4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `53c4ecd1-26be-334b-9786-b096b7b38da3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1cfa7810-5c0c-324e-b5f3-988a1e87b799` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9e900018-fc35-3043-b4d0-88efbe1cfa11` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `86a49585-0b95-31d2-bab6-99c478802f23` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e8e4befe-0595-3c6f-b8c1-059229355cb4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c5014aec-f584-30a5-811c-caebdd639539` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `00b8899a-3034-377d-8d96-a713f75026be` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `80cce9ca-a74c-309d-99cb-31134d47ed9f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6528449c-2b75-3b7b-882c-e2e52bd8062c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `57537917-eba3-374e-9f30-0ba2ce9ffa18` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e3a0e589-658a-3436-a8bf-82a671eb0e02` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `37bc5b5d-e8e8-3143-9d2d-4ae54488d719` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8911af9f-a112-30d9-a1d6-23e7b99ad3e2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `49bab0ac-b67f-3a83-938b-a23c9242ded8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `936ebe78-6de2-3b61-851a-d4d8d50b86b8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c0cea4c1-d504-3584-94ab-151536efc892` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c943b384-cf29-3eff-a2f7-570e4932ef44` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0efdcab5-42af-36fe-ad1f-65f559391af0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0fe12262-fdd9-3a7f-b1f2-adb4219f9914` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bf7be880-180d-3d5a-955b-1f07a974e43d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5714a603-49b1-3628-b1b7-ef78dd3934e4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2c6d4fcf-c861-37ef-8855-04639107823f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fdf44f3c-c7b3-3052-ac34-91335d521758` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e5ecf3b0-1c6b-3894-82f2-9939fbf2b2b0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ac8bd947-940b-3511-84dc-44f571e0e0a4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e5f5ae70-a4e8-3c41-a14c-68c92dd6a58d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `abc46ec4-6404-3b46-a924-8bed6535d818` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9b78773d-dfe4-3b73-85a8-92eeba3fa228` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `710771d2-e769-34e6-9371-1256bba02db2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `de0168d9-a7c5-34d8-81c5-3c75ded780a5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `52947ab1-41fe-3b02-b243-8b4eceda58da` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `78907f52-9cdc-3b08-9c86-429e5fc83981` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e13511a5-5e70-323f-9bd4-5474b0a29c18` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cd8efd19-af1d-3d7f-a741-cb8e5774cf71` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `70566f70-567c-3a9e-8131-aa95e9c5911a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `45b2e0e0-c019-322f-8f3e-6f6a1b632c9c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b49b4763-25e5-30c3-815c-6e866615c154` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f2479dcb-ff8e-3f28-aea3-acddea07a1a9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ae6431bd-3d1b-3c6f-97bb-31bc0e88a54e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c2b22039-fa88-3309-b347-0afedb10fee7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9d822709-5592-3928-92ca-6adde0077b7d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ca9b9eaf-5e60-3742-a6da-9d658424a7b5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c6eb52a8-69b8-39d5-a781-200ccd66d701` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `18d69071-32ea-3862-98bf-676fc081cfeb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dbd7f4af-3c25-3a56-839a-0fc809220c69` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b325b836-9f42-337a-bd3e-ebd080f56051` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f6719a6f-4f9e-33e7-b638-63c35f123102` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4fd12dc6-d18d-30df-a554-0ac0796e1b3e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d1c8a2c5-1f70-3889-a7db-46e39a9add6b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e01914ef-6248-381b-b6fa-41ef96e545e0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ad4c9226-00e5-39e0-b233-eda32d48a832` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f1323a3b-0940-3475-862f-0a6b7796733b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `43c8ffe2-d9c4-309d-9848-33c8e5b204c1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a529ecef-800f-31e6-b07e-61dc09dce7a9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `20aef9dd-15f4-3416-8e75-767c7add0a07` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `48f68715-a28d-39b7-a8da-3e4e32d6e09b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `284c38a6-e2f5-30b4-b133-1e8b4aa0afb3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `97d548f3-36cf-3131-8377-d91c55017a2e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e42dbcdc-4637-3df0-acd1-823b84858ab1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4f315f5c-d9e6-3a26-8339-52b1401f40cc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f6debb2f-ccf9-30ff-a319-0577f4112ddf` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b29a6ba0-bd17-3a42-8d24-23057a202429` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dd4056ed-b5d9-3ed0-a3b3-c43019529594` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7cd34e0e-a09a-36e2-b3c8-f3382c69593e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9be613fc-c613-3fbb-a1a4-a6046b22bcbe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2e3a56a6-03f8-3b93-adff-1f6bcebe7ead` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `89c5f016-1c34-346e-b97f-3c565831f5dd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `47146999-b665-305f-a731-cf16b9ee3216` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e1d7813f-9522-31e4-8d03-921d41a25e9f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4c5c5cd9-7fb0-37f3-85fc-4beba5455370` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c6e10e74-5692-3026-a550-1fa26213b579` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bbdb1ad1-6c49-3cf6-86d6-aeb16fb50eae` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `acbef156-8516-35bf-8925-ae71c30188ff` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8f74ef33-a3ff-34d7-b63a-a3a06f29d76c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `391a0f7c-0a44-3242-a82a-132dac8a7e4d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `329d194e-ef9f-3009-819a-f5cbf4cea110` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `023fbe22-64e2-31c8-9fd3-50a4a4334ca1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `551f3375-e45b-3d6f-815f-57d3d3da36e1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7f55c71f-78cf-3088-bbd1-1c468142811f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0c066d49-4141-3068-940a-f7394850998a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `339c05ef-8f24-347e-b743-487bf4d8c183` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `572a7e7f-1d20-3997-88d0-92cdb06dae41` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `df8751a5-4136-3d11-9609-e7a6da238031` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ac35c02b-4246-3619-b3fe-0a9eeb7014e8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).