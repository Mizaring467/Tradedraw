# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-27 05:19:25 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r25_validation/window_01/decisions.csv`  
**Fuente Replay:** `SYNTHETIC_FIXTURE_REPLAY`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `38` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `0` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`0`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `38` | Decisión registrada en Live no reproducida en Replay |
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
| `f0888945-b403-36d8-9e31-b3c9b3294cc9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `259ed060-d2ca-3129-9db1-9f291e035267` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `3eef63f0-089b-31fa-8cc8-a3b2bfe60efe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `3e4d44e3-7cd7-351d-af77-c212bc74e0eb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `6a3d8f03-bba3-3faf-80dd-74e7fd532a08` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `0a8148cf-0002-3df2-bbbe-561fa6c32cba` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `0aebaced-a779-301f-ac41-b65ab9f678c9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `4e9a4a16-0c5f-3ef6-8cb4-cc9ed25d6248` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `9cb87c0e-2cd6-3f26-add8-bd5382639d5f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `2abbcffc-3980-357f-89e9-17f5e50255ad` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `101a01ac-505f-3d90-b67d-bbc61348531a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `61001b79-a23e-3844-8ba7-cccfda3445ed` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `5252e1ac-f867-3ca6-8687-f37a840bebc7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `2e5c1a17-3054-3554-94af-3bc00454bca7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `659df522-851a-32bf-8024-241564ccfc1a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `71eb8b28-e3d9-3bd0-a9e3-819585927ac4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `2b242611-c92a-36fe-b01b-56deaf7801ba` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `6565003f-1310-3509-beb5-df593860846a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `11581585-8985-37c2-918a-3f1c1a95f632` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `3d77a243-811d-38c6-a279-f389dd068afc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `0b7813b9-9eb4-30fc-8699-77ac9cdc94fc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `7f631107-00ea-3645-ba0b-2d5868c87786` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `6a791317-c5d8-3fda-a8a7-1f4503e19a1f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `724a5162-229f-3e21-b54b-1aefb8d22282` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `5a9dc0d1-941a-3744-9913-deb944f7eb28` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `481cbdbc-6c9f-3801-8787-f397342a2fd9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b342c0f9-55d5-3f71-966b-196979d58af8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `f8fcf725-be04-3b73-897d-640b04189144` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `cf685140-68ef-3831-8dde-0c22d54e080f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `c6280277-fa5e-385c-ad42-ab37868d3e20` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `7b4318fc-ff6f-371c-8582-623ab239239f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `2d6beac0-c6b7-3925-9344-a93195f52b0f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `e9be973a-e92a-3d40-aff5-65fa9214bd42` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `8d7f2fe8-a493-347e-893f-91c5c2ea6e28` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `766d1aa4-71db-3edd-bf37-7e566689179c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `767af06f-e5d5-3d91-8807-9d410b558bf4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `21596741-b810-398e-8acf-5930ffa56848` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `23fcf37a-8ce1-3442-aad5-639970eade52` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).