# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-16 03:13:57 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23_autonomous/iter_3\decisions.csv`  
**Fuente Replay:** `loops/r23_autonomous/iter_3\replay_journal.csv`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `71` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `1` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`1`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `3` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `67` | Decisión registrada en Live no reproducida en Replay |
| **No Verificables** | `0` | Registros con precio <= 0 o NaN |
| **Tasa de Paridad (Match Rate)** | **`0.0%`** | Objetivo de Gobernanza >= 80.0% |

---

## 2. Desglose de Primera Condición Divergente

Cuando una decisión diverge, el comparador reporta **la primera condición causal** que difirió en el pipeline:

| Campo Divergente (Causa Raíz) | Ocurrencias | % del Total Divergencias |
|---|---|---|
| `dist_resistance` | **1** | 100.0% |

---

## 3. Log Detallado por `decision_id`

| Decision ID | Estado | Primera Divergencia | Valor Live | Valor Replay | Acción Live / Replay |
|---|---|---|---|---|---|
| `7dd22c35-2a03-3692-a871-64580f468510` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bc581a3a-ae8c-3496-9abe-c66712e6ce9b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `745cfe88-9641-37b5-81ea-e8c6d4b39eed` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f25834e4-4700-33c6-91a6-5a20d0d90afe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9fbf05dd-99c6-3efa-a3cf-6329055434a6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c856e1ac-48c2-3dec-835b-f08be9915e08` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `22b8eace-8e13-344e-9702-39af76e2fad2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d5d45db7-f70c-3519-a6c0-d56795828b03` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7b9b2436-e0d3-38ed-bc2b-41f1d858b0b4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8eba34c5-af1a-3749-8fc6-e104e6ac3d94` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `38e7638b-8cfe-336e-ad7a-c25a2ea04148` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2ea434a3-da63-3c8f-8f3b-474e2104cf12` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1a51f88c-d4de-3e0c-b434-1d5ebc056d95` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `015dc926-04cc-30ef-80a3-d1fa8dc7dcd0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `08e934f4-dd1f-3869-a2cd-dbc4c471f7d4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d8c76a1b-48f9-38fb-8885-b0e2f508d302` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6c8482fa-8e01-3f6e-a2ad-74d2c29b1b5d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5aa6e856-3e92-3644-bf56-9e6db7b66ea5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d4cd4fc5-ea49-3c0e-985f-e7faeeb2cbc5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d88f2795-e6d4-3c96-a1f0-aedb5618cb42` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c69c8638-eac1-3ab6-a3d6-bc6449bee7e0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c247bc75-f6a3-3091-9f86-5638c5ae2985` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3b92fd29-8355-3f1f-9e7e-4c2b3702a4c0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `754b9b3a-168c-3db5-a287-fcefad10907c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `da80883e-aeab-3da5-b295-2335a6ba81d8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `42232da8-693c-3562-8022-cc7f0e9e5b7b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f35e6273-f9e9-3486-9acd-7aba14c5ed5a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `660ec8de-fd54-305a-aa37-8e096829efc5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6568ccec-1c55-3d0b-b744-2083a3823f99` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0900e931-0774-3096-b799-31b907bad384` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c2b39e7f-9d24-34f3-b876-0aa4474fc6ed` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f04a5422-a0f1-38c1-a53b-9d58d7e34128` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1f9acc2e-026d-37be-8db5-87ffd80f100c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `92b0973b-b593-380a-aaab-569ca34d8d5d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d4a46ca7-a569-3e12-aedb-f829180def19` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `52e9d918-4401-3185-b5f6-660f7c575d45` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9c445b71-08bd-32b0-83e0-0268ae07bda3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7531a782-722b-3801-9690-d941c6457bcb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `618cc4c4-47ed-389f-aa76-3081aab32816` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b2d12247-1c3a-33b8-a7b5-df787bd06051` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `56b2345c-2557-3021-8018-f5f5e3081ed3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7a7640e8-0037-3483-8983-4b27341f8ec2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ef1ce915-24de-3e39-8247-3c386fe2fdc6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `04663ce8-68d2-3afd-bc57-329900add6e8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `753d3caa-06aa-3de6-a27c-c5b316fb33ee` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `301c583d-0f8b-3436-8f9d-27e3bdc48bb7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `14060e7b-2662-3a62-9d72-25543e790b5a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `04060ac5-940a-3c89-bc57-3a43149ab151` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `54571ae0-9c6a-3793-85f7-0eff9eae0b05` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `15912c32-c6ac-39aa-9e16-3fa4edd7b5ef` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c55acd8c-b2e4-3dd9-88bb-ffa2ed17c7fc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `02811a5c-ec9c-3023-9cdd-46bd9bc63e66` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c66ae79c-a200-3a81-bf54-4f84e3816845` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d5beea5e-477a-327b-9501-59ce061837d0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `db854bc5-0613-3472-97d9-8f185417b011` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e892f69e-c3a4-3761-82a4-acc9074baa34` | `DIVERGENCE` | `dist_resistance` | `0.0` | `0.05` | `NONE` / `BUY` |
| `3e2d7cd2-fee3-3b41-8f14-db1da8df4108` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4dc618c2-fff1-39fc-a780-49af5ba4bfb7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `97e8da2f-0a8c-3f5b-975a-58b471aceea3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3f3ce6d0-d746-390a-9dc8-e3220ab093ff` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f95968d8-7fde-3cc7-93fd-a9bbd962642d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a1d359c2-55b3-3028-a98d-376ece4ceaab` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f2310204-ca99-38ee-8662-383102e1fc7e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ba5a1884-f096-3cb9-9b3f-9b46bcc4705b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e7a347ae-9f67-3819-b749-14f73bd408bc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cce3785b-6d5d-3f38-a6f5-5a38b3d088f3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `156a9f1f-84a1-3030-8147-55241e701785` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0b7d0149-2db3-3c01-996b-1211f6fc62a4` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `66fd75f2-cef6-3204-a2de-80518978becf` | `MISSING_LIVE` | `presence` | `-` | `PRESENT` | `-` / `BUY` |
| `f39276e9-0f5d-33f9-9ec3-f279d5405d93` | `MISSING_LIVE` | `presence` | `-` | `PRESENT` | `-` / `BUY` |
| `72cdf013-1efd-3192-a660-d8ef2eb14014` | `MISSING_LIVE` | `presence` | `-` | `PRESENT` | `-` / `BUY` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).