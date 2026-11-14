# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 01:27:12 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23_autonomous/iter_2\decisions.csv`  
**Fuente Replay:** `loops/r23_autonomous/iter_2\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `79` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `2` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`2`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `77` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `dist_support` | **1** | 50.0% |
| `trend` | **1** | 50.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `3e06b407-1016-3f4a-aaa7-93b15e94973b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0b71154b-a1a8-3267-908e-61ccad6f8a61` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `96161b56-0162-3629-9c5a-9d3cc2766b46` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `584850b4-e3cf-3605-b665-66cb58212eb1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1c8811c3-4a26-3d62-91ee-1812d9b5e97d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `02ff2517-a41c-3c73-a2da-a292ec0d69e1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f09bee60-df87-348d-babf-f3de1dc293a0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f94f7dce-140b-3ee6-ac5e-c7567fe6367c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `82efd643-b978-37bf-ae0d-696595f438f0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `51a2805c-d91c-3a3f-93f2-94037bab633b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9b095b2e-a904-3c5c-86e8-04013ffa22e0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `df606dfd-4036-350d-8212-759cf5b6d400` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1b101198-ac88-3076-a766-21f8cefcab8a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3bc17d92-cf13-3b3e-8d25-8650938451bf` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `773fa6de-1c03-3f80-a9ae-24ff8c10a626` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `733e71d8-4490-314a-b671-4ba8f2646cbc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ca344da9-d23f-3b29-bffd-dee210b4f56b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0a82a9d0-f820-30f3-9a0f-8a28caa66f3b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5bc50b8d-3827-38ee-a869-9fe0001b3a03` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `53db01df-c2d3-36c6-be3d-4d67d8aa69b4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `50f99f7f-8752-35d7-a5c4-1f514d0dcb64` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d6a8ce56-70dd-3dba-b7cb-5c43c0a2f03b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `80d2779d-6692-3d93-a733-8ddb4661bdcb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c4ec918d-dfa6-376d-b772-0356925f7e3e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `27bd1a32-b0e2-3cb0-ae61-22b46403e5e6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d5a2a69e-0c4c-3983-b325-b81b5d5c0a64` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dcf1a798-b385-3869-9708-7c1f58a2b0c7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2df04ea5-1ddb-3d1b-bdf7-25af0df33632` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `aa2769d5-e0bc-391b-bd34-74200065e51b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0e81e40c-c644-3a02-80a9-a124909b2d4d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `42b9f558-38e5-33ef-9bf6-c5ac3c0ee8b4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d4f675af-fe84-34c0-969c-80ca910651c2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3ebefe90-e8d0-3a76-b9e1-951e068ab5d5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3e1fd681-9712-37a0-90dc-810fde0f8da6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `52f8cf48-a8d0-315e-aaab-997b0f0d23de` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9898cac5-98a8-322b-8f7b-53dbaf96f3ce` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d116d6e3-2178-3470-ac19-24788c299e7f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5272018a-9912-3605-87b9-0ccbaf59104c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `05da83c5-4fb9-354d-a039-dbaf7ff15e23` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bab35d62-fe0a-3a2f-afa1-f1031195ca36` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5f71ccc9-826c-36b6-b4b1-b31d07e9253e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a88167ba-91fc-39b5-8277-7487ae3358df` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4931894b-b979-3a34-b01c-49f34b17b6ce` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6bfa40fd-99dd-35bd-8184-d568fafc6b19` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `59f4e848-28b3-3ba3-bb0e-3a90a819c835` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1d9f64e7-bfd7-38c6-8623-4b3db3a128d9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `26e86567-a5b1-3796-a80b-6bf9aeb03068` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f5a2dcf4-b847-3abf-bde0-ad9326679cf6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8f599c2e-4423-3f8f-8df8-75f67a78bd4d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8be13555-6508-3e9d-9a61-bcfd9ad3b3c9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `902252ed-fffa-3f84-8559-4d63bf4737aa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a11d529e-13fb-3ee3-8ca9-2a7c974b5e40` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3deabccf-edb8-3fa6-938a-5fb62be97d11` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8dbb7e5a-9e80-33e0-a580-90e7f445c1b1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4be605c8-e29c-3012-a1d6-606d6e394717` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `26e36b22-e96a-30b7-8ecc-611f7254f6cf` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `81551729-2fe9-3519-9302-922e93d7c152` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bfebb5a2-66d8-318f-b009-640da8afb246` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0e20e437-4b4a-305d-8ba3-b202a18baf27` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e42edcdc-59e8-3226-87a2-9ebaca7f5b3f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ec045e16-28aa-3a1d-8233-8e866841a674` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b8b04afe-c641-3e60-a7b6-b424ce81db92` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7abebf59-599e-393e-893a-56fa2f3a4999` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f644c454-6175-3d1c-b258-ecc27a831e1e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `61214cd7-2d0b-3ec7-b315-461257ca0daf` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `901390b3-8e9a-319c-8d7b-6d1138782a4d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ceb49bd0-d7f7-35fe-901c-3e9d919956e6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2444d540-cb27-30c5-b252-cfe2d5bc4fa0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a1d15e4d-cddb-3ef5-9d52-5ef5083bc853` | `DIVERGENCE` | `dist_support` | `0.0` | `0.22` | `NONE` / `BUY` |
| `c93fd4da-8012-3a53-b4f1-c2b1f1c70be1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `414a677c-75a9-3a5b-9a5f-928647add069` | `DIVERGENCE` | `trend` | `UPTREND` | `SIDEWAYS` | `NONE` / `BUY` |
| `b5328b2c-0d4b-388c-82ba-8930c07410ce` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d033a1b2-06a9-3514-968d-d98a6dd3ae82` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a4d8d4fe-58c0-3795-8b82-02b5ea92b45a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4a59d629-1cff-35ed-b4b6-2d795278a5b8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b442b7d3-2792-3a54-93b2-4055c609627d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c57817ad-35b8-3b81-98d3-61451436005b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `24306e3c-3af6-3916-a399-1f623ac3f6e7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0cc23a52-7b15-33ff-8c56-0df279f15aef` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).