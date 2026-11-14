# REPORTE DE VALIDACIÓN LIVE — R23B BASELINE (MODO YOLO · CUENTA DEMO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Orquestador:** AGY (`w1:p1`)
**Dispositivo:** Xiaomi Redmi A1 (`220733SL`, `UGU8KFUKM7E6CE8D`) — **conexión USB perdida a mitad de ventana**
**Build:** versionCode=12, buildId=R23-AUTO-ITER3, gitSha=bd7ef4e
**Submodo:** `YOLO` (activado vía `/submode?name=YOLO` → `{"success":true,"submode":"YOLO"}`) · **Modo Global:** `AUTONOMOUS`
**Ventana solicitada:** 20,0 min · **Ventana efectiva:** **≈11,3 min (58 / 120 muestras)** ⚠️
**Veredicto:** **KEEP (seguridad) / VENTANA TRUNCADA (eficacia no concluyente)**

> ⚠️ **Nota de integridad:** este reporte **reemplaza** la sección "DECISION PIPELINE" del
> `LIVE_REPORT.md` autogenerado por el monitor, que quedó con datos erróneos (`Evaluaciones motor: 0`,
> `Señales generadas: 0`) porque el `status` de la última iteración se perdió al cortarse la conexión.

---

## 1. FACT (Hechos Directamente Observados)

1. **Seguridad DEMO:** Cuenta DEMO verificada **58 / 58** veces. Cuentas reales observadas: **0**.
2. **Balance Inmutable:** Saldo inicial = final = **38.320.737,28 COP** (Δ = 0,00). No cambió en la ventana.
3. **Ejecución Autónoma:** **0 taps despachados en la ventana** (acumulado del proceso: 2). **0** operaciones.
4. **Identidad de Build:** versionCode=12, buildId="R23-AUTO-ITER3", gitSha="bd7ef4e".
5. **Decisiones Observadas:** **101** decisiones con `decision_id` determinista.
6. **Motor:** `signalsGenerated` 265 → 339 (**Δ 74** en ~11,3 min); `evaluatedCount` 1164 → 1303.
7. **Distribución de Vetos (101):** `RISK_LIMIT` **75**, `ADAPTIVE_BLOCK` **26**.
8. **Paridad Live ↔ Replay:** NO EVALUABLE (0 pares; el replay no corrió por falta de `/ticks`).

---

## 2. DECISION PIPELINE (corregido por el validador)

| Métrica | Valor |
|---|---|
| Evaluaciones motor (`evaluatedCount`) | 1164 → 1303 |
| Señales generadas (`signalsGenerated`) | 265 → **339** (Δ 74) |
| Supresión por confluencia (`suppressedByConfluence`) | 380 |
| Supresión por chop / micro-compresión | 0 / 0 |
| Decisiones registradas | 101 |
| Operaciones DEMO ejecutadas | **0** |
| Taps en ventana | **0** |
| Resoluciones | W=0, L=0 (WR = N/A) |

---

## 3. DISTRIBUCIÓN DE VETOS (objetivo de la auditoría R23B)

| Motivo de veto | Ocurrencias | % |
|---|---|---|
| `RISK_LIMIT` — Trailing Profit Lock | **75** | 74,3 % |
| `ADAPTIVE_BLOCK` — Anti-patrón perdedor recurrente | **26** | 25,7 % |
| `COOLDOWN` / `CHOP` / `CONFLUENCIA` | **0** | 0 % |
| **Total** | **101** | 100 % |

**El guard dominante es el `Trailing Profit Lock`** (`ultimoVeto`: *"Ganancia asegurada (+392000 COP pico)"*).

---

## 4. EVIDENCE (Rutas y Archivos de Prueba)

- Telemetría periódica: `loops/r23_yolo/baseline/live_samples.jsonl`
- Muestras de `/status`: `loops/r23_yolo/baseline/status_samples.jsonl`
- Decisiones con `decision_id`: `loops/r23_yolo/baseline/decisions.csv`
- Operaciones ejecutadas: `loops/r23_yolo/baseline/trades.csv` (sólo cabecera)
- Comparación de replay: `loops/r23_yolo/baseline/replay_comparison.json` y `_report.md`
- Log del monitor: `loops/r23_yolo/baseline/monitor_stdout.log`
- **Ausente:** `events.jsonl` (no se volcó por el corte) · `ticks_live.csv` (`/ticks` falló)

---

## 5. REGRESSION & SECURITY CHECK

- **Cuenta real:** 0 órdenes, 0 interacciones, 0 fondos comprometidos.
- **Fail-closed:** Guard de seguridad de cuenta DEMO activo en runtime (58/58).
- **Regresión de infraestructura:** el enlace ADB USB se perdió a mitad de ventana (ver §6).

---

## 6. INCIDENCIA DE INFRAESTRUCTURA (ventana truncada)

- El monitor alcanzó **676 s (11,3 min)** de los 1200 s (20 min) y el enlace **USB cayó**:
  `adb devices` → `UGU8KFUKM7E6CE8D offline` → luego `unauthorized`.
- Consecuencias: sólo **58 muestras**, `events.jsonl` no escrito, `/ticks` falló
  (`WinError 10061`), y el `LIVE_REPORT.md` autogenerado quedó con métricas de pipeline en 0.
- El enlace **WiFi** (`[fd10:...]:5555`) permaneció `device`; el validador recuperó el bridge por WiFi.
- **Acción requerida del usuario:** re-aceptar el diálogo de **"Depuración USB"** en el teléfono
  (el USB quedó `unauthorized`).

---

## 7. RECOMMENDATION

**KEEP** en seguridad (fail-closed intacto). **Ventana NO válida** para medición de baseline por el
corte de enlace → **repetir** tras restablecer USB estable o ejecutar sobre WiFi.
