# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-27 06:12:46 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r25_validation/window_02/decisions.csv`  
**Fuente Replay:** `SYNTHETIC_FIXTURE_REPLAY`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `56` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `0` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`0`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `56` | Decisión registrada en Live no reproducida en Replay |
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
| `08c05e92-ff0d-3961-9e1c-13dc5f19b02e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `daefc40e-552c-308c-bd6b-b967bd1d6888` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `bfe00b10-4578-3fb0-9603-3e3972b67999` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `4533709e-309f-30a1-8d7e-1059dc9da5d7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `5bf634ec-e217-3264-aace-b791340ca08f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `770b145f-de55-3f63-b847-ece1213a818d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `70a91695-3a9f-379a-9dfe-bf9c658ff097` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `bde1f918-e38a-37dd-9da7-849ad4353971` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `a9e7e98e-10ca-3608-9670-c6ea1a7547de` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `3ee1ab5b-493e-3019-aad9-e4c5750d8ac3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b439bbe3-6b2a-39eb-acc6-14316624a6e0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `402000a7-2f65-3aae-8b0c-2d7ff306e38f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `9f1af063-45bb-3439-bfd1-93d465d80749` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `cadd4a3a-4846-33f1-86fe-1e5e16ae30ba` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `0ac24559-836a-31e9-bed3-4a6e64108302` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `026bc581-fbd9-34d2-8a6c-9bb425809687` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `376f5c76-f6e5-37f0-a6fc-f2ec688fbfea` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `4500ede6-911d-3861-a7ab-f1ebaa9cd2ab` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `45433871-40af-3ba5-bea3-3420240dce73` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `81b48da2-a2dd-390a-ad83-c4c1419a8c12` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `418017ed-6acf-30f1-a344-82e4e0955241` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `122c1c8e-93e5-332c-9cb7-cd9ac52e3d38` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `52a9789d-c10e-327e-8ac5-66b2b934c596` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `8c1fc8c3-0ad1-354f-a6bd-676c884a9a49` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `25ea7966-75d9-3cc5-a612-892fad960c28` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `d209fa96-0635-31d0-bc77-32492ba4a5bd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `2d806347-6f19-31c3-8466-60d8b9559a63` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `dab1f343-e198-3291-aa64-3ec40d3259ef` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `d32d775a-8cab-3924-8d25-390f46c54442` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `10d940dc-3907-3043-bebc-5adb58b1d4ab` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `5afbe88e-2747-33a1-8488-43335270f407` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `30644cac-3785-3c1e-aced-b0aefda1c55d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `d94ec9aa-e947-3d04-8eff-f20a65b48694` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `e9f16960-a935-3166-9487-7e534d16174f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `89247d5e-65e6-3c46-af38-5f71e1524606` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `f1cb02bc-4d5c-384a-b234-67f5987d5b2c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `6bc42de0-c1a6-36d7-85eb-795e9c7497d0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `e2b7568b-09da-3db2-a5b3-7447e0b3e734` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `36d5046a-de22-3317-89c0-4c1d9ee962f0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `da134736-88c8-3e5f-8329-ad6c4f2c5a22` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `3ef37b66-583d-3a81-907b-850d6d1999e8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `0c8feb33-0b23-367e-9f9d-fc28fcf1289b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `0f1ac466-09fb-3dc2-8c44-e7e06c41c663` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `5b138dab-ba95-37c1-a5eb-fb572b7d3cc4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d91fbc58-906d-3733-8003-3e68b96b0500` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `44432645-6813-3455-bc21-0b308bdeffc4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `7c17415b-d618-3a07-9215-14803cb8a324` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `31a6f208-d8dd-3ae4-af3c-d9caf94c5b14` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `09fd63a0-3005-3f2b-a383-b0c160edf697` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `f417c938-ccd7-3a8f-9528-ee7bdeb4f916` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `48dac61b-4868-3047-a0da-54a35a2292a2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `59fb5f45-2c1d-3a1e-beff-201bbc6a841f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `d6e08d3a-8503-31f9-ba3a-fb164b09908a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `76f58aef-f438-38db-a4e4-fc631c0d7894` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `48cd95b6-d5a4-3134-9dc9-20e029309007` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `14f299fa-01ed-36ce-8ae1-9491d70ff685` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).