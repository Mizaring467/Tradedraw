# HALLAZGOS DEL VALIDADOR INDEPENDIENTE — R23B ITERACIÓN 2 (MODO YOLO · CUENTA DEMO)

**Fecha:** 2026-10-16 (UTC)
**Validador:** OPENCODE (`w1:pT`)
**Commit auditado:** `ac079fd` (R23B iter2)
**Build:** versionCode=14, versionName=1.4-r23-yolo-iter2, buildId=R23-YOLO-ITER2, gitSha=ac079fd
**Dispositivo:** Redmi A1 (`220733SL`) · enlace **WiFi** `[fd10:...]:5555`
**Ventana:** 20,0 min completos · 118 muestras de `/status`

---

## 1. VEREDICTO

**KEEP (técnico)** — La hipótesis de telemetría determinística BUY/SELL se cumple **al 100 %**. La
rentabilidad (que cayó a −316.999,68 COP en esta ventana) **no es concluyente** por el tamaño
muestral; **no justifica REVERT** ni un cambio de código.

| Criterio | Resultado | Evidencia |
|---|---|---|
| `isDemoAccount == true` en cada muestra | ✅ | **118 / 118** (reales: 0) |
| `submode == "YOLO"` en cada muestra | ✅ | **118 / 118** |
| `action = UNKNOWN` == 0 | ✅ | acciones {BUY:3, SELL:2} |
| Sin `END root` en `trades.csv` | ✅ | 0 / 5 |
| Firma `"CONFIRMADA BUY/SELL saldo movido"` | ✅ | 5/5 |
| Ventana completa | ✅ | 118/120 muestras |

---

## 2. HIPÓTESIS 2 (R23B iter2) — VERIFICADA

*"Con la inyección de `pendingTradeAction` en `setOrderState` y el filtro de `ORDER_STATE`/`saldo
movido` en el monitor, el 100 % de los trades tendrán acción determinada (0 UNKNOWN) y los `END root`
quedarán excluidos."*

| Propiedad | Iter 1 (v13) | **Iter 2 (v14)** | Estado |
|---|---|---|---|
| `action=UNKNOWN` en trades | **1** (de 6) | **0** (de 5) | ✅ Erradicado |
| Evento `END root` como trade | **1** | **0** | ✅ Excluido |
| Firma `"CONFIRMADA BUY/SELL saldo movido"` | ausente | **presente (5/5)** | ✅ |
| Reposo limpio `"NINGUNA ... sin operacion en curso"` | — | ✅ | ✅ |

**Conclusión:** el objetivo técnico de la iteración se logra. El contrato de telemetría de dirección
es ahora determinista y auditable.

---

## 3. RENTABILIDAD — NO CONCLUYENTE (varianza con n pequeño)

⚠️ **Hallazgo de honestidad estadística:** las dos ventanas YOLO arrojan resultados de **signo
opuesto** con muestras diminutas:

| | Iter 1 (v13) | Iter 2 (v14) |
|---|---|---|
| Taps / Trades | 12 / 6 | 10 / 5 |
| W / L | 4 / 2 | 1 / 4 |
| **Win Rate** | **66,7 %** | **20,0 %** |
| Δ Balance | **+131.998,72** | **−316.999,68** |
| Máx. drawdown | −300.001,28 | **−400.000,00** |
| Máx. racha pérdida | 2 | **3** |

Con **n = 6** y **n = 5** trades, la diferencia 66,7 % vs 20 % **no es estadísticamente
distinguible** (intervalos de confianza enormes). **No hay evidencia de que v14 sea peor que v13**:
la caída de WR es compatible con varianza normal en muestras tan pequeñas. Se requiere **acumular
más ventanas** antes de cualquier afirmación sobre la rentabilidad de YOLO.

> Lo que **sí** es robusto y medible: YOLO **opera** (13-14 trades/40 min combinados), el guard de
> sesión está desbloqueado, y la telemetría es correcta. La *calidad* de la señal sigue indeterminada.

---

## 4. CONTROLES NEGATIVOS

| Control | Esperado | Observado | Estado |
|---|---|---|---|
| `ADAPTIVE_BLOCK` | Sigue bloqueando | **18 vetos** | ✅ Intacto |
| `Operación abierta en curso` | Impide solapes | **40** | ✅ Intacto (benigno) |
| Cooldown YOLO | Pausa entre ops | Activo | ✅ |
| V1/V2, ventana sniper, 1 orden/vela | Intactas | Sin violaciones | ✅ |
| Trailing Profit Lock / sessionFloor | Desbloqueados en YOLO | **0** | ✅ (por diseño) |

---

## 5. DEFECTOS Y LIMITACIONES

1. **Paridad Live↔Replay sigue 0 %** (cobertura 2/63; 61 `missing_replay`). Sin avance en 2
   iteraciones: los motores no son isomorfos (el replay no modela el pipeline/guards vivos).
2. **`events.jsonl` (curl) es un objeto `{"events":[...]}`** con `total` del buffer circular (60
   eventos); el volcado del monitor es JSONL de 200. Formatos divergentes para un artefacto homónimo
   → confusión de auditoría (aunque el monitor ya filtra bien `END root`).
3. **`decisions.csv`**: `score` fijo, sin `dist_support`/`dist_resistance` (arrastrado de R23A).
4. **n pequeño**: 5-6 trades por ventana no permiten cerrar el juicio de rentabilidad (§3).

---

## 6. COMPARATIVA

| Métrica | R23B baseline (v12) | R23B iter1 (v13) | **R23B iter2 (v14)** |
|---|---|---|---|
| Ventana | 11,3 min (truncada) | 20 min | **20 min** |
| Taps / Trades | 0 / 0 | 12 / 6 | **10 / 5** |
| WR | N/A | 66,7 % | **20,0 %** |
| Δ Balance | 0,00 | +131.998,72 | **−316.999,68** |
| Trailing Lock vetos | 75 | 0 | 0 |
| `action=UNKNOWN` | N/A | 1 | **0** |
| `END root` como trade | N/A | 1 | **0** |

---

## 7. ARTEFACTOS AUDITADOS (`loops/r23_yolo/iter_2/`)

| Artefacto | Estado |
|---|---|
| `live_samples.jsonl` | ✅ 118 muestras |
| `status_samples.jsonl` | ✅ 118 volcados |
| `events.jsonl` (curl) | ✅ 60 eventos (objeto) |
| `ticks.jsonl` (curl) / `ticks_live.csv` | ✅ |
| `decisions.csv` | ✅ 63 decisiones |
| `trades.csv` | ✅ 5 filas (BUY/SELL determinados) |
| `replay_journal.csv` | ✅ 2 filas |
| `replay_comparison.json` / `_report.md` | ✅ 2 pares |
| `LIVE_REPORT.md` | ✅ KEEP (técnico) |
| `monitor_stdout.log` | ✅ 20 min completos |

---

## 8. RECOMENDACIÓN

- **Veredicto:** **KEEP** — objetivo técnico cumplido (0 UNKNOWN, sin END root, firma correcta);
  seguridad DEMO intacta; controles negativos preservados. La rentabilidad no es atribuible a v14
  (varianza con n≤6).
- **Próximos pasos:**
  1. **Acumular más ventanas** (o intervalos hasta n≥30-50 trades) antes de juzgar rentabilidad de YOLO.
  2. **Atacar la paridad Live↔Replay** (2/63): sigue bloqueada por no isomorfismo de motores — es el
     pendiente estructural más antiguo y sin resolver.
  3. **Unificar `events.jsonl`** (objeto vs JSONL) entre monitor y descarga curl.
  4. Considerar si el **drawdown de −400 k COP** con racha de 3 pérdidas debe tener un guard de sesión
     **mínimo** también en YOLO (hoy desbloqueado por completo) — decisión de producto, no de bug.
