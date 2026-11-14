# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-27 05:45:17 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r25_validation/window_02/decisions.csv`  
**Fuente Replay:** `SYNTHETIC_FIXTURE_REPLAY`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `75` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `0` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`0`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `75` | Decisión registrada en Live no reproducida en Replay |
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
| `8d7f2fe8-a493-347e-893f-91c5c2ea6e28` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `766d1aa4-71db-3edd-bf37-7e566689179c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `767af06f-e5d5-3d91-8807-9d410b558bf4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `21596741-b810-398e-8acf-5930ffa56848` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `23fcf37a-8ce1-3442-aad5-639970eade52` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `f8fcf725-be04-3b73-897d-640b04189144` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `cf685140-68ef-3831-8dde-0c22d54e080f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `c6280277-fa5e-385c-ad42-ab37868d3e20` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `7b4318fc-ff6f-371c-8582-623ab239239f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `2d6beac0-c6b7-3925-9344-a93195f52b0f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `e9be973a-e92a-3d40-aff5-65fa9214bd42` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `6a791317-c5d8-3fda-a8a7-1f4503e19a1f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `6f4112b6-ca8d-36bf-aefc-bba75a46d8e1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `e9ee2ee7-a968-3ec3-ba7c-160337a93237` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `850500c2-01b5-30f8-9b15-25b656965976` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `1e68289b-ba6a-3168-a2e8-5067b7e67938` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `25e5be7f-10dc-3105-8f35-f509049f0c22` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d09cd670-01bc-3afc-8df3-1b93ede3e423` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `df73dd61-2817-3cc7-886f-679d44ff730c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `8ace6143-085e-3ef8-9d8c-a83300bcc971` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `5a761be8-58bf-3941-8d1c-304fd03d0404` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `a134c6d1-29b1-3e9e-b779-4a93deb39b55` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d2e9e628-75e7-391e-8d19-9ef6ca755034` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b012f9da-50f8-3714-942f-1f0f29096922` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `59a54dd2-0ea7-34f4-adcd-8c43b2204d3d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `86b0d030-0f95-3d81-9eb5-08bf59da6270` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `5f7494a5-a9c5-3a04-a201-9cb19c7bdc0a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `e99ed7fe-1797-3c5a-8c8b-45da6bcf6b01` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `454cc0f3-3e22-3830-a541-5053c1cbbc19` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `0a318389-e8fb-35c5-9742-d8d12675e320` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `554e8460-6b4a-3ea2-a63a-2795937680f5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `1f2db727-aae5-315f-9d74-2e131b99f964` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `f277f9ec-d72d-3a65-b306-9d7388011a41` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `eca626a2-fd3d-35db-8af2-8817820e429b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b6477f38-2929-3393-8eb0-2c03234286f2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `7958d1be-e9a1-3511-965e-42404f27c6f1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `c7e3445f-4e79-3da1-bac6-ab5620c30fcf` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `bd125692-fdaa-36c3-9dbd-3640f5e252af` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `ca1e81f1-7d4c-360b-869d-a426802f471a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `f89ca6c7-dce4-3051-9f8a-6a3780c48324` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `a9d58573-b8e8-348d-a092-f94eb1ebc432` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `5a10c153-cfa6-34ee-8885-0a6a5f8eac58` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d606a7f7-fcd6-39e1-b58c-a6a9f75df650` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `a5eb6e1d-f59c-3fef-ab96-0a38cf391452` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `9888d2d2-6b5c-3a7a-b7d3-fe8d8ec05d8c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `ad3caa97-3625-36ad-a3ba-6daa922b77c2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `5c2201ab-a3d6-356d-b12c-90fef4562dde` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `b4e7ac04-7fff-38f7-a096-6e5149fc701d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `c7e59607-f396-308d-81e4-45a7ae94d392` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `121be100-7591-306c-b358-238f44277daa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `a9113bb3-f408-35e1-9a66-a27d25ae96c7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `ba153897-e056-3218-b643-5440dd8df38b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `5926a5ac-4074-3d25-add0-f7d1970cf1a4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `1dfad62a-bc79-38b4-890d-21403a920726` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `8e06b387-c8ac-3036-bfd9-83d69bece3de` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `3e87d676-ab60-32f5-b1c0-c369d853a276` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `d258e462-7fb3-3f29-8b22-9f6c3c7b8ff7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `16eccb32-3a7e-3327-be6b-e0ff5c249335` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `2775d4a5-6f86-3c5d-a6eb-e2bc5d90608a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `455d4032-c907-3453-859a-60696864c061` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `83a6c6e1-d338-3ddc-9eb3-8187888146ab` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `c0dbe788-75ae-3db7-bc64-68216f366f02` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `97909824-27e0-3626-adf7-b7bdd5dc42f8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `617f3a70-72d4-3010-8fe1-dd6ecc09882e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `32a0ee1a-7bbe-3db4-a0b5-7384d07443f9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `c208c44b-9469-3150-b637-59af1e329408` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `7caa2d1c-9803-3d39-ab0f-70d74a479123` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b8dd9478-0658-3366-962c-bd1200d89827` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d1cb81e9-8f1d-3a42-b075-6ad610e09c47` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `323912ca-b3f2-3c1d-80e9-e67ec274e75b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `53a7c3d1-8e95-31ac-ba9a-a1443f2398a5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `428cfd13-4548-3fc4-8af5-9bd053ee8efa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `239308c3-4656-3406-8365-cea70249042e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `cc4abd2b-9cef-30b9-a1e0-4864321786cb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `13a2f69b-a660-3e7e-afd5-9d9c2e4202d4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).