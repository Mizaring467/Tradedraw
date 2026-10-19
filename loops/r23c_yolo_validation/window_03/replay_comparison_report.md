# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 06:47:46 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23c_yolo_validation/window_03\decisions.csv`  
**Fuente Replay:** `loops/r23c_yolo_validation/window_03\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `116` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `2` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`2`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `1` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `113` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `trend` | **1** | 50.0% |
| `dist_support` | **1** | 50.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `de71e655-a1c0-359f-a288-7f3b66a85f21` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a6946a8a-affd-39b6-80c1-f59e0a39ab34` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e969b1a7-b49b-321d-b680-5345be3f31d4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cd576f51-862f-38a3-aea6-e1991602f858` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `66de29aa-3831-3f9c-955e-ff1862a00033` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4d61cc82-7025-351f-a943-ec9aa8364788` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `efaf2f8a-9ec4-38e6-9f83-d3ae039531f0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `db0b2012-9c53-3e8a-a54e-af21a66437d4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `29a4e4c4-5cad-3321-b148-bb729a95d9d4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `69fa4857-07a4-3734-b1ac-4b55d536e5ad` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `57f66d66-3933-39a4-ba3c-dcf51c635e81` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0a6012fe-5e5b-330b-b069-2ac87c9e22de` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `475fb695-7d3c-300d-986a-a70b73f52384` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7f67ed06-e71d-3ac5-a1a3-78fbf8a5485e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `791176c6-d02c-3d9d-a4e0-d418471f426a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `57c2d2df-c5ca-363a-8d31-744620e2486a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b8797679-5ee5-37c8-a39e-92fbb8a9f2db` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a787442c-71b1-3bd8-ac19-0289cb109b02` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7f78caea-acac-36b8-84db-035cd8043401` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0941e46e-8085-34c9-9205-2c41a747fc92` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `27f73028-f8aa-344e-bc2c-7a44261409c9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4420cc15-a922-3388-88b3-e9e04bc0ca99` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a293f216-681a-308b-9cb9-2741eb181abe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d0d3905e-765e-3d14-85e6-849f8e10935b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `47d1ff41-ced0-3634-8ce3-2eb9bfa908ff` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `44ce38d7-da84-381e-a02e-8122bf664984` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4e9dc930-1782-3cd1-9a02-2ecbd607e706` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `15fe4f6f-5074-3b06-8bbe-407a137f8315` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7136cc0a-77c0-3108-ab7c-53bcb836c3ee` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c310ac64-9d24-3d69-bfec-27653fc8c148` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d26d540d-7570-39a1-a43b-1e96f709b5a6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e94abcb7-71df-3a8d-b421-d9bbd882fe30` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d1e881bf-8da1-30e8-b326-5062e4fee79c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d0e16bff-7a62-3eba-810e-5d4848edf448` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0787d945-647e-36b4-822d-4621dc1db551` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `7fd5ba0a-17b3-3f8c-af9e-eb68b3c306fd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c8b98d58-6ed3-3b14-9219-27a9a988ad68` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `92519c94-4516-3d2a-93d3-b0f6804a2ea7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b2af3995-1546-3229-b1b0-993b4ee5fd12` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ee128de3-8a4c-3b30-86fa-767677bf8f21` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a275f26a-811c-3e53-b743-4af4ec84c0c8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b9a1d9fc-947d-33b6-9dd3-951a9a4d2cd9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `22026f2e-6744-3dcd-b67e-cd02b600711f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ab2095b7-69a7-39c7-94cd-b005fb9b4be0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e8d306e9-ea66-36c2-918a-c2fcbba4f6a3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fba77161-ea88-340b-8756-f9ae06a83afb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0a16bc98-075f-3b91-abbb-0884d94e811d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `397f0c5f-fa7a-3df3-a6d0-aba210799154` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `caa7cc74-b915-3545-b7d5-68724cedbed9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7c9dc8d2-22b6-3b5f-83ac-6df6db5c2c6d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b5c819f8-899b-359b-b810-69228e48d20d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `87a87092-3e90-3d9e-84e8-3d5c1467fd2d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `844339a4-7d58-3473-af13-197205e95981` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `11a4d615-999e-3c85-a275-0af1814f72b4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4e59efbd-69d7-3db5-9888-34792fe8f98a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6abb07a1-e78c-3fe1-989f-679e8fe1e17c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ee18bca1-b9da-3541-b50e-91d763a59023` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ca7b3e6c-b7f9-3661-8be7-b5edc0214291` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c3177c30-c583-3a7e-9a63-c9f519facf2a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3770db5d-60bd-361b-8e46-1b9793566080` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7e593560-f9ff-3a31-a3fd-d25bfe2ee31c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a87cc4f3-5802-3330-9950-92d56a2b390d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `13e7b098-23cb-37ed-b183-44376ff7a546` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2adb5f40-40f8-3015-bf59-2734d7ef6d09` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `928dee38-e35f-37e3-a377-b64ff049afee` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3af177ad-f597-31bc-b2d4-599a017d1d7a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b91e5cd8-bde9-3a65-bcb0-5829ff2ac2d4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7dab7e70-f95e-30aa-84db-2fc15eb096ed` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `1bc616c7-3735-3928-80ee-482c85a9181a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `12a6507e-58c4-3c04-8e59-c2a52d6ce9f5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f23ad030-5049-3b6b-9c98-7cae7f061c51` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a7c7621d-3356-352c-bb20-e525ee687df5` | `DIVERGENCE` | `trend` | `DOWNTREND` | `SIDEWAYS` | `NONE` / `SELL` |
| `27ac50ff-845f-3367-97dc-058c49bd0c35` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dde74968-3c3c-33a5-9f84-76fed8d77dcf` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b9d06471-824e-3479-9bc7-013f0c15a47a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fef0e2f1-bf1b-329c-aa7b-7fe7d9ef65fa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6afe9dcd-407e-374f-af7d-8556d1297a71` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a73dcc82-33d8-3e9d-ae03-893fac4863bb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `02198b89-434b-37e6-8b17-2d918e7c0664` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dbe2d998-1459-3f21-8c20-7a3bd4f06027` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9c43964a-ea3b-36b9-80c8-0fd40246a2a9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e728700e-7723-3304-af0e-caa6bd2e9cd3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `17142738-b72c-3631-9303-200a35ec5370` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `384a895a-8e22-35d3-983a-7f53be54e9bb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9286d579-3256-3465-bb12-b1d8c24ee946` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `428c1c02-a9d2-3efa-b962-18254e8f006a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `12da57a7-35f9-3a95-be86-0816d2e27522` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8a02ce80-c4be-3a53-9f09-2dd61cbe6c81` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a6f2fd3f-b54b-3cc2-930d-9f268ca1e5a5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `33d25078-b110-3070-a408-51d877ce3a81` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `265f6130-407a-3d59-a805-543e7e14891c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `af8cf7b0-8910-3805-9c54-4515498dfb1f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b76b21d8-1341-3170-b4a3-390f79da95fe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b54674a0-e7f8-3a8b-85dc-2c153f5bece0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `daa4250b-a170-391a-804f-1d321e7598b8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7287dcf9-ee41-3386-bb51-def9d77a947d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5b37f3f4-3df4-34a6-b4e0-12a4e2db9714` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f9d4f787-890d-32ae-a064-72c487ef35c8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `02b2ed58-dde0-3f22-bf18-743988c08d72` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a759a573-dd16-34bd-ba6d-ed1e79b984ff` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c85521a9-a48b-38b4-8ec1-9e4c19d4978e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5ed36dc6-b566-3ff8-9f8e-187d6bf23f45` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c7cbcd40-8e85-3fed-96cb-4ddd4bbca3e1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `baf33328-38bf-339d-98ba-c38896233783` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `06bff459-ccf4-3e21-a795-b14878127f4a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9439508a-5439-3d1f-be80-c70375d1923f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `009b4f94-22c8-3254-8bf8-b6b22e1db0ca` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `946ed230-45d6-3bb4-a63f-b5311b28e474` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ef165280-4170-3404-8e70-d185442e3ad1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1a6061b5-9bcc-3139-9cca-affca7d454ef` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `14804f80-4f34-339c-ae0e-ed9810fa0f66` | `DIVERGENCE` | `dist_support` | `0.0` | `0.15` | `NONE` / `BUY` |
| `19e462ca-badc-3817-826b-5f5d496f298f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `41475164-84d3-3c30-95ff-6c3c8cb416f9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `843b0270-a24b-339d-86b0-b27275e7fdfc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b1defc43-9e64-378d-b321-20a66011f42b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0ec1a7a7-b3f8-3414-ad92-2c4aff9bd42f` | `MISSING_LIVE` | `presence` | `-` | `PRESENT` | `-` / `BUY` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).