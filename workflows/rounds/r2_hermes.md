TAREA — RONDA 2 · TESTER EN VIVO + TELEMETRÍA H5 (hermes)

Dispositivo: UGU8KFUKM7E6CE8D. Repo: C:\Users\heidy\Tradedraw
Estado ya preparado: modo AUTONOMOUS, submode CONSERVATIVE, accesibilidad activa, Binomo Demo
al frente, bridge tcp:8080, balance ~39.31M COP. NO edites código Kotlin.

NOVEDAD: el bridge ahora expone telemetría de motor en `/status`:
  "engine": { signalsGenerated, lastSignalTimestampMs, lastSignalAction,
              evaluatedCount, suppressedByConfluence, suppressedByChop, suppressedByMicroCompression }

=== Pasos ===
1. Estado inicial: curl.exe -s --max-time 6 http://127.0.0.1:8080/status
   Anota engine.* (deben estar en 0 tras reset_stats).
2. Lanza la sesión de 25 min (bloqueante, timeout >= 1700000 ms):
   python scripts\monitor_25m_session.py
3. MUESTREO EN PARALELO: cada ~20s durante la sesión, guarda el bloque "engine" y "feed"
   de /status en un archivo .agents/loop/r2_engine_samples.jsonl (una línea JSON por muestra).
   Para automatizar: puedo dartelo como bucle PowerShell, o hazlo por pasos.
4. Al terminar: reporte final de /status + report json + logcat.

=== Objetivo de la medición ===
Cuantificar CUÁNTAS señales produce el motor y POR QUÉ se suprimen, sobre el feed Z-CRY/IDX:
- evaluatedCount: nº de ticks en ventana :01-:05 (oportunidades evaluadas)
- signalsGenerated: señales que pasaron la confluencia >= 80
- suppressedByConfluence / suppressedByChop / suppressedByMicroCompression

=== ENTREGABLE (español, máx 30 líneas) ===
- engine.* inicial vs final (tabla)
- Nº de trades de la sesión y resultado (del report json)
- Tasa de conversión: signalsGenerated vs evaluatedCount
- Motivo dominante de supresión
- Interpretación: ¿el motor tiene oportunidades y las filtra, o el feed casi no genera oportunidades?
- Rutas de evidencia (jsonl, json, png)
