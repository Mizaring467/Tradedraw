# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 07:35:47 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23c_yolo_validation/window_04\decisions.csv`  
**Fuente Replay:** `loops/r23c_yolo_validation/window_04\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `71` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `2` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`2`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `2` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `67` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `candle_second` | **1** | 50.0% |
| `dist_support` | **1** | 50.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `ce9f59df-e8f5-3cca-bbd3-3364070d0a65` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `858ed2a9-c8e5-3c85-84cd-6536562bca5e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3da76c8d-8378-3d9c-b204-47fa1ebdf09e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8146893a-d6ea-31d8-a6ac-a5828db3e871` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7f03a78f-c9cb-3c92-95e6-fa5a0bd69363` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `995e7229-67f8-3d72-8d1c-e0ebbdcbb453` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3751a729-b48e-3121-8aec-13d49fc0052a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `357a94eb-d87d-3079-b288-b59b7dd5c628` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `14669f40-aace-3a83-91fd-40cbbd4eda12` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `cb77788d-5678-39fa-a408-2bc04c2acb75` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3ba9cb46-11ea-39e9-b7b1-a4868a77eabc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0cced960-4912-3217-a307-f3eef81d3801` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `23e3b0f2-e029-3eba-9f34-0d0164a59f59` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3ee97c34-d084-35d8-a215-44a34e8f7b02` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f6e5bc45-786c-3ecf-8ccf-aa9ffa0df6ad` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5e0e168f-be74-3edb-a1f2-dfc5d70772d1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0abc2553-17c7-3241-8014-989a8e7b2696` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7e1a11eb-3bec-3035-b1da-07527bb4bb96` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2a795305-1dba-3162-8757-13acce15b92f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `32091185-ce77-37c1-9fcf-163a9c401db3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2590acb0-a642-3b0b-9047-4773c5bd2a25` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `227d1de5-1c14-338d-86f6-e8a17ec5587d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3ddc2b50-e033-3419-97b5-d12298c8d07d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `50c500d7-dbee-36c7-b235-ba551f01064b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `23eb9cfb-061f-3d8f-aa8e-c9e87df9aed7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `07784f43-405e-3578-afa3-89e7c11119f4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dad6b411-652f-3173-812d-b2e6015facce` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `653123c9-c014-3a7d-b7fa-fcf0dc13f184` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `012fd1be-1bd3-335c-89db-eba58fcedfee` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1002c50e-5981-3673-b774-a946f434a467` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9826b8d4-c8cd-3d87-bf05-e8f73028d90e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d9114f88-14de-3008-b369-e16e66a06392` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c96c169a-f2e3-3697-94fc-e32949802c41` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6fec3143-cfc9-3591-b728-e2179101c139` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9f5ac6c0-738f-3250-ac8c-717ad7a3b467` | `DIVERGENCE` | `candle_second` | `3` | `1` | `BUY` / `BUY` |
| `201845e3-6e60-312c-8de0-ec0c29dc6a81` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9acd9d12-d433-3bfe-aa1a-187a124104ff` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9b58eec5-d3db-3a37-895b-17e262124d4e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6c35d8e5-ba48-3add-9dfd-5190cec0a234` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `50dedd37-5636-3874-969f-e56d8d495221` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `becce1c2-6988-30ee-b59f-ba2dc5d70b1e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c595f420-2495-30c8-94a9-27fd9aa4fd9d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `49a8c8b6-b2a9-3643-a9a5-ea536e587a5c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `39d336cd-b6a5-3521-baa4-d3a05e966ffa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6b17cce7-dd47-3a30-99ab-843a8562a5f1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3e81d17e-90e9-3887-b1c9-02ea5ec66304` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a195a82d-cd98-3cc2-8ce9-bddb1dbe768e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a3b3a263-04f5-3646-a6fb-63f423fef4e4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2ae84ac7-cdd6-3322-bedc-c5dbecd95ffb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b35819d1-28a9-3bb0-af89-79a17ec9ce92` | `DIVERGENCE` | `dist_support` | `0.0` | `0.06` | `NONE` / `SELL` |
| `285682bd-2852-35c5-b4a9-c9115c40db6a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5485d4f4-6125-3f9c-84b1-bf73dc62897b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cb60cd70-ad12-3b6f-988d-edc61ffe9b60` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4515bdb4-e661-37d5-8c68-0e9cd300ebab` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ef735db2-960d-3962-b844-920219927dfe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d015f353-6a00-36d3-a583-cfcb181751eb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `eb11236f-0b07-3f21-b38b-785ed88abe97` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2424bd74-2ad8-36ca-9c9c-7d7462149d09` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `65a2af2a-4e45-312e-9cf7-fbd915d3d87e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3d7b76e0-11a2-381a-a6d8-b332234e2a1e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `034ea6f2-c281-32e3-883a-c5096ff7afaa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `33d4c43e-c112-33cf-81dc-8196cb6e698b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cb55ea85-43c7-3538-90e2-96ade51418de` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `2c9c35a8-e7de-3e05-a293-65b413119960` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `00b8c2ff-0610-3a1a-be2b-ba11d3cc62cd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8cd143ab-89d3-3694-bb27-ffffc2ffd8d1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f278e6cd-2e25-3a1f-b904-d06b806c7386` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1a8c358c-82e9-3154-9ff0-62c85ff8f0f8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `670d6f8f-d1bb-341d-af24-1b4bcb767567` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ae6174d8-edde-35f0-9bc6-ce23595ae13a` | `MISSING_LIVE` | `presence` | `-` | `PRESENT` | `-` / `SELL` |
| `695c0924-8612-3630-a2c5-a02068ac1159` | `MISSING_LIVE` | `presence` | `-` | `PRESENT` | `-` / `SELL` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).