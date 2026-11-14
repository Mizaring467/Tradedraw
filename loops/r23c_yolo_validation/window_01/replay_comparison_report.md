# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 06:00:49 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23c_yolo_validation/window_01\decisions.csv`  
**Fuente Replay:** `loops/r23c_yolo_validation/window_01\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `76` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `3` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`3`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `73` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `dist_resistance` | **1** | 33.3% |
| `dist_support` | **1** | 33.3% |
| `candle_second` | **1** | 33.3% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `6e5a990e-3514-3bc9-acb5-752a5c7af66c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a69c222c-a0a1-3ce2-8a2c-13177dbe7616` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b7eb7a86-727c-3a0e-b8f0-ec03f2f63cc1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `25842f05-cd45-31be-af5d-bf4cfd78291b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7ed5c2e8-769f-3692-872a-e53f0f73eb64` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b5481c98-b886-3103-a378-30f464ba6bc2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b6e3bb37-7e8e-3d9b-a4b4-7553ed1f8449` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `793da4d8-5801-3842-b970-2bf6b052b7e2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `456b40e7-9f91-3650-8d61-550aa9468ce2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e8088027-311a-3d07-842d-76dbd61bcd3a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `95e91b34-07f6-3fb3-854c-a64561b6d2d5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c80e4210-547a-3ecf-b9df-941e22cf5bcb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3c5cd842-a7f4-3a7b-a9ae-b700dd7512be` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `19e32e1d-7920-301d-aad2-7abfa53e3ead` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e0d9bdaa-058a-33d2-bc22-01cb46f4fb5a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `cc0f7e90-39ac-3543-a2b6-a1e1f246518c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ac299f75-19a8-3484-96ee-fb62cb2bb877` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b843b18c-ad85-30e2-a801-67639023b6c0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `93615588-ff21-3219-b18d-92bf2e81c344` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `82e3daa6-1de6-3f58-abb5-54b184a9c3bd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ab212bee-6ea0-3ba4-865a-5be8712ebb27` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d3545b15-8f0d-3a8c-8e50-ee41c080866b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `58827f88-049d-39cd-a715-24ac4cc3a6b8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9ae51505-c848-31de-9207-9591b878ca0e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4042861b-88af-317c-b460-c5e2f0355321` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e137a2ab-4c02-3594-b68e-60a1b89e1d00` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `52b4022f-9767-3e7d-9b22-b0ff00922dfb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `42feb8cf-2c16-3dfc-9b34-7585117f7e93` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `afdf7617-1946-3d39-afc8-5b92377ebf90` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7132ea30-bf4e-3bad-af37-d97ce4afb5df` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a3564c0b-d885-36c6-8a5c-b6cd2366be49` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3892f0f2-fce0-35d9-b206-6f2fa8d1104f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0f4f77b5-3feb-3eba-9ae7-a370e74fa3ca` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cf833034-2ba4-32a3-9473-bfb930c62d1b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c3add214-8cfe-30e1-9dda-55ff66270b10` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b537241d-fb89-3bc6-8c75-6a2728a14858` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `40f2d90c-2479-3dce-890e-2cb833c086a1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ed6a6ff4-1a46-30de-8849-d9041ef55a79` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `793bc08f-c467-364b-b3ac-6b32a7b5c17b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `872b047a-54b2-3ab7-ae40-f5675da778ec` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `56b97737-31b1-379d-8ce2-827983c1a50d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `25c73819-a4e6-3add-a306-8b715bc30b5e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3e8062b2-6a80-362c-9394-382fbf3e99d9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dfaa710a-28ca-36a5-a2fe-3ef8af5a337c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f5adfaf4-5520-3fe2-9202-24a9067ed22f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `aedc01cb-7293-3ea8-95ec-1ef8fd9ec9c6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6a0bdcad-bf0c-328c-a49a-0f008c721378` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `849015b4-10c6-3f9c-8acb-abb582aac3fb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `48d191d0-01bf-3949-b3b5-accd3f9fac5e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ab0f4c98-8084-3aba-83d3-f9a400ed0f66` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `508ff1bd-762e-3dbc-afb7-8a4885f33f7d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9839dbea-80ef-375f-ae35-d186401d6dfe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `03a8a6f7-9e42-386c-9b31-296fe4f232e0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dae5eec3-359d-3be9-ba97-f5c388a9190e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `835c3dc3-f756-304e-850c-6e3253a0e04d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4e5d58b9-eabd-3334-9316-3194b95f432a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d64a8908-94b5-31fa-8427-5cfb48fa37e1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `acbdfdb3-f296-301d-bf71-f36c4f29c9ad` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8ab76f32-c88e-3b09-9707-f93b8b1d995b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3848581d-e050-3757-816f-c65c10c64ffd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `facd359a-71d8-31d5-aa93-3bde74932566` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `864ca972-7fd0-3368-a6bd-a5cae2fe3920` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ba452572-ae61-3cb1-a393-26185c028c1a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e22d3e3f-3929-36ba-8825-53f000582232` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5d36c788-eec5-30d9-a17a-189da7b0aa8a` | `DIVERGENCE` | `dist_resistance` | `0.0` | `0.75` | `NONE` / `BUY` |
| `831b0d7f-a7e0-3f22-9c4c-c39f6cfe3e70` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `aee345be-89d0-3f86-97d8-2af848019803` | `DIVERGENCE` | `dist_support` | `0.0` | `0.2` | `NONE` / `BUY` |
| `da0f46c7-a83b-35bc-8aa5-235f51207d66` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b017bb88-42fb-3f4e-a438-b2b2e436812f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `91660a45-31d1-30e0-a3fc-a59154c556df` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a8216e9e-f82b-311d-b20e-c12af0d5d914` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `600367d2-9928-3712-8d36-c700d20ad048` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `214e7e9c-2822-3508-a5ad-7a60bf28938d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a3a72089-177b-3b76-89b3-a08989b9a05f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cfa705ac-158d-3073-8e40-5fc3388f2b96` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9298a424-c377-3036-8ae3-650c1f1322d1` | `DIVERGENCE` | `candle_second` | `6` | `5` | `SELL` / `SELL` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).