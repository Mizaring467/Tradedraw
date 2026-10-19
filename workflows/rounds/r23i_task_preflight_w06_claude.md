# TRADEDRAW — AUDITORÍA DE PREFLIGHT WINDOW_06_CLEAN (FASE 2 LOOP R23I)
## DESTINATARIO: CLAUDE CODE (`w1:pC` / Auditor Principal)
## REMITENTE: AGY (`w1:p1` / Orquestador)

Estimado Claude Code,

Con la **Fase 1 formalmente aprobada**, procedemos a la **FASE 2 — PARIDAD CON MUESTRA SUFICIENTE** del Loop R23I (`workflows/NEXT_LOOP_R23I.md`).

Antes de lanzar la ventana operativa `window_06_clean`, ejecuta la auditoría de prevuelo:

### PUNTOS DE PREFLIGHT A CERTIFICAR
1. **Git SHA y Build Instalado en Dispositivo:**
   - Confirmar en `/status` del dispositivo físico que `gitSha == "aac1df3"`, `versionCode == 19`, `buildId == "R23H-BUY-EXHAUSTION"`.
2. **Paridad de Configuración Live ↔ Replay:**
   - Confirmar que Live y Replay consumirán el mismo RIC (`Z-CRY/IDX`), mismo submodo (`YOLO`), misma fórmula pura `calcularPoderesDeMercado`, mismo veto por agotamiento, cooldown de 8s, poda de ticks `ts >= window_start_ts_ms` y tolerancia temporal unificada en 6,000 ms.
3. **Aislamiento de Directorio `loops/r23i_validation/window_06_clean/`:**
   - Certificar que no existe estado heredado ni archivos previos en esa ruta.
4. **Salvaguarda DEMO:**
   - Confirmar que `isDemoAccount == true` en `/status` y que el mecanismo fail-closed `emergency_shutdown` está activo.
5. **Invarianza Absoluta de Estrategia:**
   - Confirmar que la estrategia se mantiene 100% congelada.

### ENTREGABLE
Genera el informe en:
`workflows/rounds/R23I_CLAUDE_PREFLIGHT_W06.md`

Concluye con:
`PREFLIGHT W06 = READY` o `PREFLIGHT W06 = BLOCKED`.
