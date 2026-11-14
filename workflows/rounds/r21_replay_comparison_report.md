# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-15 02:46:22 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `SYNTHETIC_FIXTURE_LIVE`  
**Fuente Replay:** `SYNTHETIC_FIXTURE_REPLAY`  
**Veredicto Paridad:** **FAIL** (40.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `7` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `5` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`2`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`3`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `1` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `1` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`40.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `market_regime` | **1** | 33.3% |
| `dist_support` | **1** | 33.3% |
| `direction` | **1** | 33.3% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `DEC-R21-001-MATCH-REJECTION` | `MATCH` | `-` | `-` | `-` | `BUY` / `BUY` |
| `DEC-R21-002-MATCH-VETO-CHOP` | `MATCH` | `-` | `-` | `-` | `NONE` / `NONE` |
| `DEC-R21-003-DIV-REGIME` | `DIVERGENCE` | `market_regime` | `TRENDING` | `CHOP` | `BUY` / `NONE` |
| `DEC-R21-004-DIV-SUPPORT` | `DIVERGENCE` | `dist_support` | `0.01` | `0.18` | `BUY` / `BUY` |
| `DEC-R21-005-DIV-VETO-MISMATCH` | `DIVERGENCE` | `direction` | `NONE` | `SELL` | `NONE` / `SELL` |
| `DEC-R21-007-MISSING-REPLAY` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `DEC-R21-006-MISSING-LIVE` | `MISSING_LIVE` | `presence` | `-` | `PRESENT` | `-` / `BUY` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).