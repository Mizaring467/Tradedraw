# NEXT LOOP — R23H -> R23I / VALIDACIÓN CONTROLADA

Objetivo: continuar el loop sin modificar la estrategia por el resultado de una sola ventana. R23H está cerrado, pero la evidencia de paridad formal es débil porque W05 solo produjo 1 par evaluado. El siguiente paso es aumentar evidencia, no optimizar sobre 6 operaciones.

REGLAS OBLIGATORIAS
- Ejecutar TODO en paneles/sesiones visibles. PROHIBIDO headless/detached/silent/background como sustituto.
- Si un agente no tiene sesión visible: STOP + BLOCKED.
- Equipo: AGY (orquestador), Hermes (implementación), Claude Code (auditoría), OpenCode (validador independiente).
- Estrategia congelada inicialmente. No tocar señales, ponderaciones, veto, stakes ni RiskManager salvo que una auditoría encuentre un defecto reproducible.
- 100% DEMO; verificar isDemoAccount=true antes de cualquier ventana.
- No usar resultados de Arena.ai como verdad; solo como evidencia a reconciliar.
- No avanzar por WR de una ventana aislada.

FASE 1 — AUDITORÍA DE CIERRE
AGY coordina una revisión breve de R23H_FINAL_CLOSURE_REPORT.md, R23H_CLAUDE_PREFLIGHT_W05.md y los artefactos window_05_clean.
Claude Code debe comprobar:
1) ledger y balance,
2) coherencia de 88 decisiones / 80 bloqueos / 6 ejecuciones,
3) trazabilidad Live↔Replay,
4) que el único par formal no esté artificialmente seleccionado,
5) que no haya cambios de estrategia disfrazados de infraestructura.
OpenCode valida independientemente y reporta discrepancias.

FASE 2 — PARIDAD CON MUESTRA SUFICIENTE
Si la Fase 1 pasa, preparar una nueva ventana DEMO limpia con la misma build/configuración, y capturar suficientes decisiones ejecutadas para que la comparación Live↔Replay no dependa de N=1 par.
No alterar la estrategia para conseguir más trades. Si hay pocos pares, registrar explícitamente la limitación y repetir ventanas limpias.
Requisitos: misma versión/config/submodo, poda temporal correcta, IDs deterministas, tolerancia 6s, mismo pipeline defensivo.

FASE 3 — VALIDACIÓN ESTADÍSTICA
Acumular ventanas homogéneas y separar:
- evidencia de integridad/paridad;
- desempeño de estrategia;
- evidencia histórica heterogénea.
No mezclar campañas antiguas con R23H/R23I.
Reportar N, W, L, VOID, WR, EV, DD y payout/breakeven. No declarar edge con muestras pequeñas.

FASE 4 — DECISIÓN DEL LOOP
- Si hay defecto de infraestructura reproducible: Hermes implementa SOLO el fix, Claude audita, OpenCode valida; luego nueva ventana limpia.
- Si infraestructura está correcta: NO cambiar estrategia; continuar recolección homogénea.
- Si aparece una señal de mejora/peor desempeño: no optimizar todavía; exigir muestra y auditoría.
- Cerrar con reporte de ronda y criterio explícito para el siguiente loop.

FORMATO DE ENTREGA
AGY debe mantener un reporte de estado con:
Estado: RUNNING / BLOCKED / PASS / FAIL
Agente responsable por cada fase
Evidencia/archivos
Cambios realizados
Tests
N de ventanas y N de pares
Decisión del siguiente loop

IMPORTANTE: no crear una falsa sensación de PASS por una métrica aislada. La prioridad es integridad reproducible + evidencia homogénea.
