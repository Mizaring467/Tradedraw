# TAREA OPENCODE — TRACK A: VALIDACIÓN INDEPENDIENTE DE PRE-VUELO

**Fecha:** 2026-10-18  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** OPENCODE (`w1:pB` / Validador Independiente)  
**Misión:** Revisión independiente de condiciones de vuelo, estado de dispositivo y dataset limpio.

---

## TAREAS DE VALIDACIÓN
1. Inspeccionar el manifiesto en `workflows/R23H_REVALIDATION_MANIFEST.md`.
2. Verificar que `loops/r23h_validation/window_04_clean/` esté limpio (no exista o no contenga archivos residuales).
3. Verificar que el script `live_monitor_r23_autonomous.py` aplique la poda de ticks pre-ventana sin efectos colaterales.
4. Verificar que el arnés de Replay tome `BACKTEST_SUBMODE=YOLO` sin desviarse a `CONSERVATIVE`.
5. Comprobar conectividad ADB con el dispositivo Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`) y confirmar que las salvaguardas de cuenta DEMO estén listas.

Entrega tus hallazgos en `loops/r23h_analysis/OPENCODE_PREFLIGHT_AUDIT.md`.
