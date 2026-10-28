# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-15 21:59:23 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23/iter_4\decisions.csv`  
**Fuente Replay:** `loops/r23/iter_4\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `61` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `3` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`3`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `58` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `asset` | **3** | 100.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `9b8c1ca3-69d9-3ffe-b455-ce846fa418cc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5d70dcde-809a-3b11-a1bc-e33e99565a5c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ec03dadd-a4ec-3b64-9e50-38b73fd0e9ed` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `203cd29f-b47e-38f9-b930-8a39973109d3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `df3f1ca1-6926-318e-b691-9494f60d7a42` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `68f8da8b-7cd6-3390-bbb0-21401e17ea5c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6e822723-5f5d-323d-8469-398de06bb2d4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d2f9a783-d25b-39ec-b415-530b748e9159` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `59a9f743-b7f5-3f59-bba1-e6b490ff9d90` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `18b66d1f-aa78-344e-8e87-2ab4a6767150` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d90e5507-e931-3214-a8fc-1e99867c9dcc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f8ef3714-b707-3f5b-9978-308998b9f328` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9a2ede55-9d1a-3f7b-b641-e48cbda51468` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7481fa77-af32-358c-b265-5177ddc80f61` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `804e5a98-749f-3762-a1c4-e1c6390e656a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5cdeffc8-f20f-3f28-8257-cdb1b6405294` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `01b5504b-b245-30de-8844-7204613ec9a6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `472a586d-59f3-3ab1-80a4-4c5473587971` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b49724b8-1ee3-360b-9061-1c6fee7a1ca5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `68a33553-23cc-3131-8986-8452976e7444` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `895aa7c8-ecad-39d0-a41f-f5935a5b85a9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2511bf0d-3c02-3004-a52d-4e64ff8de2df` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ac9c6908-61f5-3bfe-8167-29309b9da008` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `39baace2-3aa7-387b-a65e-a3f91ba9c92f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ca8e1f3d-6665-36e1-8845-e72a2b596f7e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ccb32c4d-731c-3a0b-959d-de8b503aed2d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fd87ec3c-003f-39a9-8e86-42c2fafbefcc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `74b38e87-74f6-367f-868a-1f59bc9bc32e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e67d9fb7-5be4-3e20-b6d1-554408ad4c16` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `18e7a94b-15a9-3e1e-ba5a-7129b6d8662c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bbd3f576-6ace-3af8-9e94-e107e5ecd061` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `27fb4ef4-b834-3b17-9dc1-14d14daa7100` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fe4a1d1f-5b15-3716-9a0a-63272091ae89` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `af68cf2f-c3ff-3c1f-b1a3-a2107fa9f8ca` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `070718d6-8955-355d-99f7-28c90abf0165` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e00647ab-3e1b-3f73-8748-90ff48f8cd63` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `edffbf10-e562-3953-9950-d9e772a49bbe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cbf3e319-00c9-3974-b48b-6436189e192e` | `DIVERGENCE` | `asset` | `Z-CRY/IDX` | `ETHUSD-OTC` | `NONE` / `BUY` |
| `bfbd4568-7a01-3e73-9cc7-314928822850` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c91e20b7-af3a-3403-8025-29dd03b38b3c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5917334a-bd23-31fa-bec1-d3acddb2f3c7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ca310cc7-6729-33f5-866a-83fef2e8182a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bdae6467-e969-314f-8853-d8a05ac206bb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3d23efdd-aeac-30af-a2b1-696218d85033` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b270c692-c6da-3c35-9299-17feeb26749f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `87ca3321-774a-367e-ad17-2528f3c9f3d5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b69ba252-0b2c-37f6-8d27-b64888e3c0ce` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e857f4f2-eaa2-32cd-937c-3b604dc2576c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e6921d56-d1fe-3060-babb-c26baebe34ca` | `DIVERGENCE` | `asset` | `Z-CRY/IDX` | `ETHUSD-OTC` | `NONE` / `BUY` |
| `900040f0-79d5-34ad-8ba3-d01b7429806b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cd6e0529-f88d-373b-80d4-c9fd0d2efc15` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6ac374f6-11e8-3948-b6bd-eedfec0d8c6f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1ca02f90-9275-3a2b-b6a7-c36b782adae6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `27a50bc9-c038-36c4-bf32-734b49fc1605` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c212e003-3a39-3721-8e54-aebd017638bb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `56d57931-9f17-3fd8-9276-fc27a01c2891` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `16ff6cac-7a79-31a4-a511-6fa3a469f1d5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `08a9a33d-fac4-3924-9903-58d895de02ec` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `74f87bc9-b4c3-3153-8021-cf68528a0c55` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3f024332-fed8-355a-a484-1de8048f10db` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9aff66fc-d154-30fc-baad-c497147349d5` | `DIVERGENCE` | `asset` | `Z-CRY/IDX` | `ETHUSD-OTC` | `NONE` / `SELL` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).