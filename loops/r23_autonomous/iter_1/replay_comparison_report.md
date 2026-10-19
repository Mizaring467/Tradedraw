# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-15 22:48:24 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23_autonomous/iter_1\decisions.csv`  
**Fuente Replay:** `loops/r23_autonomous/iter_1\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `28` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `4` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`4`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `24` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `market_regime` | **3** | 75.0% |
| `candle_second` | **1** | 25.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `7e849159-1fcc-30cd-923a-eb7afc83b77c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6c5f633a-620e-32d5-8585-bedd60dfae04` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8c3cc524-21ea-38e3-ab69-f3832be0ef61` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0d93cf45-0f2d-3a2e-ad2b-71e17a6ecb16` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e69b3441-a64a-33cc-a1dc-0f112b78fd93` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1551c87f-44a9-3112-999b-4bc0174fd3f4` | `DIVERGENCE` | `candle_second` | `5` | `3` | `NONE` / `BUY` |
| `754b78d2-d226-364e-a9c5-4a6b1d17278a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d54581d4-6295-32e4-8dd2-8dac3dfe88e8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `04f20f91-df20-3725-af48-15ecdf1499cf` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1e4333e0-0c10-3563-b097-dabc559898cd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f36be6c9-da86-3363-a6e4-0fd06fc2b088` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `955fa53a-a5fa-3df2-94e1-652a5047ddd9` | `DIVERGENCE` | `market_regime` | `UPTREND` | `TRENDING` | `NONE` / `BUY` |
| `ecf4d03c-9315-3321-b7c3-dec9fd92be4b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f328bca4-f3b4-3a13-9341-a495326d47e7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9cfc89c6-1670-301e-8da0-d3342d1e5e6d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `812fe16d-877c-32f9-a152-543337e08482` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dfafd87a-a947-3ff5-a8a4-4c25ab85915c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `eef97302-dba1-3473-9b45-1beb5aa9bd22` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6bbc762e-dd4d-3262-84af-a934beb6c5ef` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9edf96ed-39c9-3e08-a7a0-dd8b1722c351` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9ed56b67-5af4-3489-9715-94a2e69a42e1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cf11d139-883e-3aaa-b0da-8317836f3321` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `eef1c362-43f2-3f2b-b067-bc6b33c60950` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `30a16e18-22d9-3752-8cb6-ea16d524d9f4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8c85c7cf-dd42-3cae-b97d-9d06aa849379` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c818605e-17db-3a29-8a59-f92f47b7d311` | `DIVERGENCE` | `market_regime` | `UPTREND` | `TRENDING` | `NONE` / `BUY` |
| `5f234dca-e3a5-3be0-96ac-b3acd1817f77` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `02063655-b190-30c0-98ba-f9c98884fbad` | `DIVERGENCE` | `market_regime` | `UPTREND` | `TRENDING` | `NONE` / `BUY` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).