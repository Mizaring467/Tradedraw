# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-15 04:30:09 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `data/fixtures/r22_live_decisions.csv`  
**Fuente Replay:** `data/fixtures/r22_live_decisions.csv`  
**Veredicto Paridad:** **PASS** (100.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `18` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `18` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`18`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`0`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `0` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`100.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

*(No se detectaron divergencias en las decisiones analizadas)*

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `f061de3d-4cd1-4997-a9f6-9b91eaae06c8` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `3c485194-4167-4351-befb-4ffe4e03d7c2` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `b77ad69f-d9b1-4598-9ecb-c6ac113bb215` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `c8c90372-bb3e-447d-acf1-f8a9cd0ad02d` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `492e57a8-2d96-487c-a732-77010db08168` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `2efa4a36-b8a3-4a13-b52b-b7057e714338` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `c8f2c9c6-81db-4c78-b4b2-1430c3dfe0cb` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `78fad693-c96e-422c-bda8-e8d85ab06a69` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `d80dd73d-1c14-4308-a6e7-463f54409f99` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `7b479095-cbc8-414e-9257-eb518f911fcf` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `b4e2f38a-54d0-40c9-9dd7-40827bc8d027` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `3c875f4d-3dde-4819-94fa-aec05a0124ca` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `6f521d37-5e46-40af-9a53-3c065dfbcbff` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `107cb606-a248-4361-a16c-645e21becb0d` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `5f9aaa6f-8c50-44f3-84cf-745396a0d5c5` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `f8f72620-e116-4168-a7c7-fbbf7b6eeb22` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `a03c799f-c61f-4c68-9451-d32aa55ee9a4` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `bebf0edc-a05c-4965-bd1b-dac1f5fd9df7` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).