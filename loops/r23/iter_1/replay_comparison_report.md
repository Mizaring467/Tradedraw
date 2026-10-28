# R21 — REPORTE DE COMPARACIÓN LIVE ↔ REPLAY (FRENTE B)

**Fecha:** 2026-10-15 20:01:56 UTC  
**Herramienta:** `scripts/validation/compare_live_replay.py`  
**Fuente Live:** `loops/r23/iter_1/decisions.csv`  
**Fuente Replay:** `SYNTHETIC_FIXTURE_REPLAY`  
**Veredicto Paridad:** **FAIL** (0.0% exact match sobre pares analizados)  

---

## 1. Resumen Cuantitativo de Comparación

| Métrica | Valor | Definición / Criterio |
|---|---|---|
| **Decisiones Totales Auditadas** | `140` | Universo total de IDs únicos procesados |
| **Pares Evaluables (Live & Replay)** | `0` | IDs con presencia concurrente en ambas series |
| **Exact Matches (MATCH)** | **`0`** | Paridad 100% en todos los campos según jerarquía |
| **Divergencias (DIVERGENCE)** | **`0`** | Al menos un campo difirió dentro de la jerarquía |
| **Missing en Live** | `0` | Señal generada en Replay pero ausente en Live |
| **Missing en Replay** | `140` | Decisión registrada en Live no reproducida en Replay |
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
| `ba4aa203-6fa2-4a9e-901e-b2541cd057f0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dbb7a7ac-0715-4fa9-aa11-44a135eda3b8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8b0bf5c4-03df-40d3-9160-af84e09eea22` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f0424401-5f91-488b-ad61-a5a32c0d8db8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a69dfd81-493a-461a-90c2-efc5f8b0c33d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c9d480b1-7487-47d7-b998-423beb0a1e4f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `43150558-1177-48fa-b1b7-8d28e85f170d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fc52aba1-7fdb-4081-9085-98ad52185fde` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7dd8acd2-8c29-4956-bb13-2f415624621e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `35dd8a65-4fa8-437d-aa50-2a30578e487a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `791bedd2-a145-4bdf-a63a-8f60a000e20c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0dae8085-dfcd-4d05-b403-a886c98e7e0f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b58bf3a7-0aa2-45da-8759-1aedf6714cca` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `430071a0-0712-474f-b000-9930b34f8f5b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bf6acc67-4da3-4318-9a69-254b386df792` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a7297763-78fc-429f-b6a4-87757341ed63` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `34bea6f0-edab-4d6a-8bdd-eae0d53c0fec` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f7648fcb-c42f-4eee-9327-420e3777909b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f6a7c6cb-25cf-4b05-80fd-d8cd03809414` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0ce4d740-3b6a-4cd1-b4f9-0406c8fb3107` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `016c172e-a0c7-490a-99f6-fe730e69fbf9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `982ba80f-e1cb-4acc-b5c1-a2a8b3df3a32` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `77ad9ebb-b180-4d19-b826-93b8e1bdf768` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6bbcfab6-e1a3-4805-971f-1db8a2c2f595` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d4aa6397-5722-41a2-859f-b97a1cd0b495` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `462fea6e-140a-4d61-b1df-71b3140d8a01` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6be1e686-fc74-4fe0-9d8b-02f6fb9fcb5d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `60f182c9-3abb-4407-8d80-5671624afb47` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e4cee6ba-486c-450a-a0f7-1b6cb7141112` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b9c52b14-63fb-4227-ba87-b6f69c2454af` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `90e27cf7-b971-4945-a84c-eab7a6cee213` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9d490377-88c2-48fe-85ab-9a2f957e514c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5c1d1920-fe7e-420b-b3c1-ce7cad4e5edd` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7a1a4568-bbed-4fac-a256-90fe460771ec` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `771231d0-0f68-4074-baf0-065137883390` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f98de420-7c2a-45e9-8de8-37118dff6d5a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e62cf0b5-09e7-464a-9d46-3a458fa04ccc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bb96358b-41d0-4774-9ec4-9b82e3c7fd60` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `61b9dc21-866f-48fb-8920-0134ea661458` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e0264e92-5123-4f77-8308-99713c85eef7` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5e612269-769d-44cd-becf-409a32c10775` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bee0c9f3-809d-4e47-ac57-fb48eb507d4e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `be339b8c-4e97-45b8-9559-c67abc6df5e9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `666513fd-af51-4e2d-ba0f-ffb68942bcb5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fa1a761b-2773-418f-a121-209c1be635d8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `72a39eac-95ea-4931-ae5c-819b86b06b9d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `78a9211e-50bf-47a5-a05d-4c503843264e` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f260a574-c152-42ab-b8df-89e8c8f5a00b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3836f0c1-8db4-464c-bd34-a55669ae8cec` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d5d7bed3-45f7-469f-98a9-f9609e726606` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f814df7d-9df8-4691-8e33-56b04c61c2cc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b780be8c-cf31-4b66-ae58-6476b93ef371` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bcd4a049-f9fe-4ca2-acd9-30d444a97b67` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bd6268e9-e5a1-4912-9eb9-8b3f99804156` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3cbb77c8-82ce-44d4-9d71-242d7c5c1e42` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `651e0ce3-b9ee-4bcb-a5bc-dbbccdd4c5c2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `804f7767-41bf-4cf7-9068-76fc28fa2c47` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `4647d3c6-f289-4e8c-822f-e630ca26da29` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e36d7a52-27dc-4774-8c8c-da78da8f2fc0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `152d6147-7ad6-481b-8e63-12583f12c476` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c0c5a85d-7f15-4294-bd83-0bf813f90632` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8c0ab328-7859-49c6-adb8-495ada363dfe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1ef8c061-19fb-496c-8da8-8ec2cf3ad81a` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `0d12dc1e-2a2d-42f2-9a9c-8f4255242d22` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f1cf8cb2-c9e5-4fcc-b1df-ee637fad4ca0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `dde15fae-da98-48ca-9639-5655157e9bb8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `3fbd6d2c-73d3-4636-999a-368fd95993a8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2ae8cca4-c1e2-4dff-beb5-7694700cb841` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `613c0562-1ae8-4e8a-b321-5d4ec00fb820` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `37aacbb2-8243-451a-8b52-3391459bc1fe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `829920e8-a4ef-428c-b33c-6c18c428a23d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `f8038b13-0d15-46c6-bb3f-a1cd439aeb41` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b2558056-b476-4da6-9f93-1b2fbb073152` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `495626dc-7f88-4695-b3d2-7d7c93623226` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `256e8f2a-e688-450f-8917-9c00f750e47f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `faf6516c-844d-4f80-b759-a50453039dc8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7335655e-9325-4181-8491-5c3539aebd61` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `b37863e5-5f40-442a-843c-41165a90a308` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `86196dcf-01b1-47d2-9406-64f6afac6910` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9c0a1217-a454-46eb-b5c9-98d925530974` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `a9cb3745-39b8-498f-9f51-0a5ae8fb877f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e84dd431-b95b-4f46-a455-3ca7be322630` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `33dfbaf7-b218-4a5f-aaf5-b7e1d88d3f85` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `35651b04-2a5b-4560-a91c-a74239d4d220` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7680bd06-49d8-4ce3-becc-cf1854ddfabe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `084f3890-85b5-43ec-bcdb-7567f56c5df6` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bd30acc2-c963-40b3-813e-92d755db9110` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6d2fff72-0e0f-4b5c-ba66-e0fa695e926f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `192b314a-fd96-4e09-88f6-1187f6904603` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `959006c8-5991-4f55-94c5-9183799fad57` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `31797572-8f3a-455b-ac32-985716b1cc19` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e9faab97-c7cf-488f-82ac-51808a580d5d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `6fb30623-397d-4b45-b566-7f61ea55a064` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `7a759cfd-beb9-45f6-a00b-b27586f3a9be` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bd7e6e80-8831-401b-9e33-57bb4d6efd99` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `af6318f3-6779-4ef8-9e58-b3deb8403855` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8bc80ef2-a1be-4cac-923b-cbdfd19f260b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `19e63346-364f-4c8d-876e-98a147e1e7b9` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `8082bc19-6776-4ece-8a99-8b6bc90971c2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `420c15d8-3a06-4360-9509-1e24283ebf31` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `35766f42-92b7-40f4-9b7a-0883fd2590f1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `342f53c4-1f8c-4c56-8b1c-0ed8f3b5909f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `74ff3b11-2855-434a-8fe0-80bc3b60a2b2` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `29852bef-8303-429a-b523-45933b27acfe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `cc04badc-5312-4d1c-8fe2-78fcba8afd8f` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `96637ccd-8c89-4b7f-91cc-3abf246301cc` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `66d34a10-4f76-4c1d-a27a-188eb633cb4c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `09bfeb32-2c2e-4995-b53d-d15973b4a16c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9f6247fc-b6e0-40d8-abd6-ee2b732e4c26` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `bea4fb59-c38f-4bb7-ab80-ebd476f96b89` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `36c5c957-4001-42da-94c1-33bc379bd7c5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `87740701-c4ee-4491-ac08-67f020e59b81` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fe913f65-bfb3-49e0-bb99-701308ce3b1d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `78b3a014-4644-469e-a4f3-ec7680cda1af` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `2389dfce-5dc1-40b2-9769-98755f4ede4b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `fc46cc3e-6fa6-42eb-a454-dcaa9985845d` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `1f69150c-bdfe-46b2-b7be-0647492e4876` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e7955f06-1a6f-42c1-bbb1-716f94fbbfda` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `9352dadd-7916-41b5-8e43-63d7d48adbd8` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `87f3c652-4f2d-449d-b7f0-791e328fa6eb` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `e110fc19-080d-4c70-871f-d7bb5fc6da46` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `eb5e7139-7c1f-45da-89a4-660bbae2f8b1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `aedb448b-da71-4b70-9712-49cf321df637` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ba9d455a-3a6f-4100-9b93-3643d494627b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `57c0797d-88a0-4295-94cf-3407705ef438` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `82bf91bf-3a53-4626-9e7d-b0fc314cf0a3` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `14b79bee-f366-4b6e-b613-f390afb1b9fa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `249ab4d4-4076-4fd1-a1e8-e9cba4759655` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `326e31e5-3057-4b6d-bc0d-9171c2facbbe` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `994afcc6-a958-4b9c-b50e-d0ebef5ed8c5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ccb35180-b63c-4993-8509-a1840928476c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `98f5276b-e3cc-4b06-b204-324bbb47a9d5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `26292a86-5d54-4b4b-9ffb-2fe31e5a0cd1` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `5b03b0e1-cfe6-4227-9204-f3e5eb0967c5` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d1687eaf-1d9e-42a1-8934-1bd13f8201c0` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `c274c7f1-eb28-4864-8f92-f74a2173526b` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `d56b66b7-dbdf-4c79-ba87-64c8169127aa` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `39608419-02c6-40fa-ba08-2017c47bdf72` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `48acf5fd-dffb-4872-8d16-85d24d96af7c` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |
| `ccc99263-8074-4785-af08-3bd634f28f43` | `MISSING_REPLAY` | `presence` | `PRESENT` | `-` | `NONE` / `-` |

---

## 4. Criterio de Aceptación y Gobernanza

- [x] **B.1:** Herramienta toma sesión y compara offline determinísticamente contra replay.
- [x] **B.2:** Compara campos: `timestamp`, `asset`, `price`, `candle_second`, `market_regime`, `trend`, `dist_support`, `dist_resistance`, `confluence`, `strategy`, `direction`, `veto`, `decision`.
- [x] **B.3:** Identifica y reporta explícitamente el **primer campo divergente** causal.
- [x] **B.4:** Emite reporte Markdown y salida JSON con contadores (`exact_matches`, `divergences`, `missing_live`, `missing_replay`, `unverifiable`).