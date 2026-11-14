# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 05:08:06 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23_yolo/iter_2\decisions.csv`  
**Fuente Replay:** `loops/r23_yolo/iter_2\replay_journal.csv`  
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
| `candle_second` | **2** | 100.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `e06a7dab-ce86-3d77-95f0-9eefe98ab290` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7d4d05ce-38f8-332e-9a5b-b71495ad745a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bab36f16-34af-338a-b242-baaa19d36dea` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `98cac416-797c-3848-89a4-642fc41e032e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `08c179c2-e44e-3029-92f1-b1a7fff06327` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `996ec42f-9fd5-39e2-a454-b06b791de26e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6fdc2268-fe04-3931-bb19-79ddcdd5b3ae` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c2b21965-aa6a-35fa-aea8-4b86cd331939` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fc9c2e4e-81ae-32e5-bfd4-7f6c5eef3330` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a0f097ec-20d0-331f-bca2-98be2e6d736a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7c32c1ec-d909-3474-89f6-72f74cb8e25a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b891c20e-6b50-3ca3-b431-f05fa99386fe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `16ee1eda-ecda-3e65-a826-38ad724c4939` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `27783485-975a-3c18-b621-abbac1f5f6a9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `af80d982-7c6a-31ad-9bfa-bb31eb967097` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a8fe8aa4-434c-37dd-9dfd-4e026087b48a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `61992b76-e355-343d-b850-0559db79f5fd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dac5dc90-fdec-3a43-af85-ffc4bb383b75` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f384b682-e635-3fc2-9803-0ebd0f86120e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `321a60af-21c1-31db-9fbe-a99965a89862` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9f853b5c-a350-34f9-b3c4-f70d78fc0b27` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1125a638-97b5-3d7d-b2b4-0d0d7988ed52` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2742e84e-7494-39c5-96a1-4701b88f32f1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `87aba06d-8a48-3906-aa15-8338e0bffb24` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ce038143-0e2c-3385-974c-f066c6cc1d1e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8a8448ad-fecb-3076-9f5a-0077098c0e80` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `669c79b6-8de9-33ff-b357-d51b8cd620aa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `93e411d7-9aa0-325a-9aaa-89ead9c564b0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bd6c86a9-7253-3ed5-b305-3ce38b7f2f54` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9fbdc583-eb84-3ad1-bd4c-9f443137864a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cdd242d1-ffb3-3e19-9506-a8cacc74fd1c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9cf6964c-5958-3f5a-a24e-d1426577c9fc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6db0fffb-b444-35ac-abdf-6f4aba4c12fc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3527be60-2d10-3dba-a167-67aac57e735c` | `DIVERGENCE` | `candle_second` | `2` | `1` | `BUY` / `BUY` |
| `f79c73b0-5998-310d-a89a-96f842a5888d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c16abe07-a439-3084-94ce-a628d791a435` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7a907473-0691-345c-b04d-5348d5b0c20e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b4a77ab1-08c4-3a6b-af52-274cc375b941` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `64913dad-48ba-3aae-adfd-98854fc374f6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ace95d04-b328-3fb2-98c6-b03b812fd561` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9b72d1fb-f087-3ee8-adf4-5936a3694a3a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `02a9d866-f02e-34f3-a5b1-7636d11af779` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `79db7e06-f0fa-31e9-964c-9e3d40559f99` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `32141f03-355c-3688-9cbb-e877f8d355e7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ea729fc3-c977-34e4-9324-04b1fe7391ab` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2836e6db-1e3a-3b77-94ce-d3248ff2c12f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7d70888a-d68c-3026-87df-e83ad2a6b9bc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `18e0cb5a-0f21-3d5a-a032-8fbe00dcdb58` | `DIVERGENCE` | `candle_second` | `3` | `1` | `SELL` / `SELL` |
| `f066f64b-51f8-3bf3-8813-3648d5f0c769` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3442b4ea-2a66-37e0-abac-f7e94b226231` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5297e836-32a8-392b-bd63-bd5a78cd9ec0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `59afc208-dbeb-32f0-85eb-e50f668af484` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b691d77d-9ee9-3e26-841b-898ddc90a6fd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cd232a27-5778-392f-b536-c85606c8768a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d3cac39b-5d3d-3f0b-abf5-59dc91154733` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `99bc4068-3f33-3f36-9718-3b115ad43e3d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b44b98b4-772b-3ddb-8634-891ea8558dd3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e31a8036-1826-3de2-b706-42e9a7db85b4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4b2e0b1a-cccb-3811-bcf1-04b5064f6a75` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `c4532fbe-c794-3a69-90a5-71f5708d0239` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `137d436f-1751-313c-9d6e-27e2d1cd227e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5d7c3807-ef52-38df-b315-950e279b6dd1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f1340f4f-323a-339e-b36e-262cde028286` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).