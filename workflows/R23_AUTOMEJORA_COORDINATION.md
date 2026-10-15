# R23 — COORDINACIÓN DE AUTOMEJORA CONTROLADA CON VALIDACIÓN LIVE

## 1. Identificación de la Ronda y Baseline
- **Ronda:** R23
- **Baseline inicial:** R22 (`e3253c0`)
- **Dispositivo Físico:** Xiaomi Redmi A1 (`220733SL`, Serial Hardware: `UGU8KFUKM7E6CE8D`)
- **Conexión ADB:** Inalámbrica TCP/IP sobre IPv6 (`[fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555`)
- **Modo Operativo Obligatorio:** `SHADOW MODE` (Cuenta DEMO, 0 taps, 0 órdenes reales, balance inmutable)

---

## 2. Registro de Iteraciones

| Iteración | SHA base | Hipótesis | Cambio | SHA candidato | Resultado live | Decisión |
|---|---|---|---|---|---|---|
| (Inicial) | `e3253c0` | Baseline R22: Infraestructura y paridad live-replay validadas al 100%, pero 18/18 vetos por V2_RANGE en Crypto IDX (sin SHADOW_DECISION aprobada). | N/A (Punto de partida) | `e3253c0` | 95 muestras, 18 VETO V2, 0 SHADOW_DECISION, 0 taps | BASELINE |
| **Iter 1** | `e3253c0` | Trazabilidad de Build en Runtime (gitSha, buildId, buildTimestamp en /status) + Telemetría Cuantitativa de V2_RANGE (rangoRelativoMinimo y rangoRelativoActual) para auditar la distancia al umbral durante 20 min live sin relajar guards. | Exponer metadatos de build y rango relativo en /status, bump a versionCode 6 (v1.4-r23-iter1) | `f5f9eaf` | 116 muestras (20 min), balance intacto 38.054.735,36 COP, taps=0, 140/140 vetos por V2_RANGE (rango 1.09e-10 a 6.62e-10 vs umbral 1.0e-05), trazabilidad de build 100% verificada en runtime. Reporte: `loops/r23/iter_1/LIVE_REPORT.md` | **KEEP** |
| **Iter 2** | `2ef2b72` | Exposición de `/shadow_journal` (lectura directa en disco) y booleano `volatilidadSuficiente` en `/status.feed` para auditoría inmediata de persistencia física de decisiones shadow sin extraer archivos por ADB ni depender de ring buffers de memoria. | Endpoint `/shadow_journal`, campo `volatilidadSuficiente` en `/status`, bump a versionCode 7 (v1.4-r23-iter2) | `101a183` | 116 muestras (20 min), balance intacto 38.054.735,36 COP, taps=0, 17 decisiones (todas VETO V2_RANGE). Control negativo 100% consistente (9/9 vetos correlacionados con volatilidadSuficiente=false). `/shadow_journal` verificado operativo (0 entradas). Reporte: `loops/r23/iter_2/LIVE_REPORT.md` y `VALIDATOR_FINDINGS.md` | **KEEP** |

---

## 3. Matriz de Reservas de Archivos Activas (Exclusividad Multi-Agente)

| Agente | Rol | Archivos Reservados | Propósito | Estado |
|---|---|---|---|---|
| AGY | Orquestador / Auditor / Build / ADB | `workflows/R23_AUTOMEJORA_COORDINATION.md`, `scripts/validation/*` | Coordinación, decisiones, build y auditoría | ACTIVO |
| HERMES | Implementador | Ninguno | Iteración 2 completada (KEEP). Esperando asignación de Iteración 3 | IDLE |
| OPENCODE | Validador LIVE (~20 min) | Ninguno | Validación Iteración 2 completada (KEEP). Esperando Iteración 3 | IDLE |

---

## 4. Principios y Protocolo de Iteración
1. **UNA HIPÓTESIS PRINCIPAL → UNA MODIFICACIÓN CONTROLADA → UNA VALIDACIÓN LIVE.**
2. **Exposición de Trazabilidad:** Cada APK candidata debe exponer inequívocamente su identidad (`gitSha`, `versionCode`, `versionName`) en `/status` para que OpenCode verifique `código candidato = APK instalada = proceso en ejecución`.
3. **Validación Live Obligatoria:** Mínimo ~20 minutos continuos por candidato. Sin prueba en hardware real, no hay decisión.
4. **Seguridad Absoluta:** Cualquier orden real o tap despachado aborta la iteración inmediatamente con veredicto FAIL.
