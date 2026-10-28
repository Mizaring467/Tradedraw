# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-27 06:34:01 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r25_validation/window_03/decisions.csv`  
**Fuente Replay:** `SYNTHETIC_FIXTURE_REPLAY`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `78` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `0` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`0`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `78` | Decisión registrada en Live no reproducida en Replay |
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
| `31a6f208-d8dd-3ae4-af3c-d9caf94c5b14` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `09fd63a0-3005-3f2b-a383-b0c160edf697` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `f417c938-ccd7-3a8f-9528-ee7bdeb4f916` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `48dac61b-4868-3047-a0da-54a35a2292a2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `59fb5f45-2c1d-3a1e-beff-201bbc6a841f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `d6e08d3a-8503-31f9-ba3a-fb164b09908a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `76f58aef-f438-38db-a4e4-fc631c0d7894` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `48cd95b6-d5a4-3134-9dc9-20e029309007` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `14f299fa-01ed-36ce-8ae1-9491d70ff685` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `BUY` / `-` |
| `7d45a593-f61b-3d1e-982c-89ce42fa4c39` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `c0c2f744-7bce-382c-acaf-5784a1fa1730` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `540be686-27eb-326a-a499-1b532d591dea` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `acbe58a8-342b-3b47-9301-29ba51cee9a1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `7880d7e9-3f7c-39a9-b02a-ffa299de0d18` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `f42c3f9b-bc18-3953-893e-692ed2875ddb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `61f47c4b-7062-3301-b533-016ad0b18dbe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `53fad526-b0f7-3c40-9a48-40c4cc2ff69a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `3f2a700a-226b-3233-8497-066cd10da989` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `771caccb-6b1f-3e11-b089-d77cc688621e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `95d53e7a-ab5e-35b5-aa72-010e2c9fb01a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `c6ce6a75-20ad-3f2f-8139-502252d18982` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `cfcdcd80-9d2f-3bb9-82a0-d1435799bd61` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d4b45259-97b9-377d-8909-3ae08ac5ab0e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `4127a681-edb5-3fe3-9dad-e0c193020c8b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `01689a61-d10e-391b-806e-0eda7cb2cd0f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `4a71314f-27a0-3eaa-add0-0b4b901323df` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `ae447743-2207-3427-bd52-c64c1314c490` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `0c0e7000-678c-30bf-9e09-95fc32baf277` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `e6797c8a-6e4c-3dee-a9a2-4928cb0dd6d6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `461e91b1-5f4f-30a0-ad0b-12a44113082d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `0a94d742-5281-3f72-9522-c3bd223683e3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `87499e64-7202-3e07-a71d-4351647abe54` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `ea17467f-b42b-3bd5-a973-58e1d6f2f3b4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `1c9f2fe5-3094-3b4d-8789-05ffeaa811f7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d7523ee6-afe5-3a99-9516-9d2ee888925d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `690eec18-5b2f-31a3-93ec-fe7253d6b61b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `f88f861f-8c21-3799-ab21-7c4919028dc7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `53171099-6741-3053-9613-92785df4d66e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `a7679ec8-1bb2-368c-9a58-93fcc3787c2c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `e5c61ae0-1533-3ba7-8d3b-65b84d26c747` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `ae16338a-75f2-3125-8e04-ed942c1b4d67` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `98ba1e10-ab14-3517-b228-e80f3c4a01dd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `83299626-11ba-318d-b0fd-a4e710d71946` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d0b6e56d-c380-3245-a20f-52b2fa49256d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `4bdb405d-5503-39c9-83d5-72035a517629` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `ee241b5b-81a6-3bdb-839f-2e4ab6dcadef` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `fb25b40b-6c23-3b57-b998-c7c35a2275c0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `4b061dc8-cc4c-3680-b1fb-146d6a12b5bb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `5b4214a9-b2e7-35d5-a91b-9a40d92ad215` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `2005ee8e-9bb8-3813-a448-e84a8cfbe9a5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `014d08ad-f475-3eb8-9ac3-162c056157f6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `562705b6-f38d-3683-b422-7456a289b63a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `298a110e-ec69-39ee-8236-9549fc511b9c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `06beda73-e167-33f8-a811-d3d6550d7c2e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `5c66595c-b7fa-3059-9a55-33d2e31c1a43` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `7d52862a-1b07-39e6-9c5d-21ae52eafa6f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `6d3bf77f-4465-3a38-9d9e-14347eed22d7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `6f4bd857-587f-3106-b89c-20eda33c6e21` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `9dcefa97-5a1b-392e-b8bc-19e907af42e3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `6ccffbb8-b2f3-33ec-865b-aa07ae6e3ccc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `54eb1606-1db1-3f3c-bc48-35d0aebb6392` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `ffb915fe-d5f7-30c0-a5ce-a7f18e6b91ff` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `33a761a9-c252-33a2-abf1-d00623d3e717` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `9993a0c0-ce4f-3437-8307-2e70988e01c3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `8ea32887-b2ba-30d8-8506-59a95b41b1df` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b417cf52-86ab-35bc-a475-ab77aacbc1df` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `8a8388d2-a749-3417-816f-96d41dff4608` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b8254908-4923-363d-826a-c127cbe538e2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `1b1c2d8a-f7f1-3ee4-a1f6-e23d52330d8e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b1915cb6-cc0e-3ec3-b95c-35b98b51f439` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `defaa83b-2797-3947-98cb-e72f3c4ba199` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `125673a2-744f-3656-9653-40f159e4b429` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `4c18df7e-1974-3116-aa7d-954e4d497cba` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `a1e54812-0d7f-3ccc-a413-f8f861d7079c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `c9dde4be-5635-3da5-b346-38142e88027a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `b23efaf6-73e8-36d1-9d19-2ca707216028` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d61edc9b-5052-3f15-9a53-7c35a199f549` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |
| `d3a79c92-3c85-3934-a415-daf765f200b1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `SELL` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).