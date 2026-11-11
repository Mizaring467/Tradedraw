# R29-B — Corrección 2: ExecutionPolicy productiva

Fecha: 2026-11-10 (America/Bogota). Codex, sesión visible. Base: 82afad5.

`executeHeadlessTrade` llama ahora a `ExecutionPolicy.evaluate` y aplica el resultado a eventos, contador de desajuste, desarme SNIPER y toast/HUD. El flag sin consumidores `executionPolicyEnabled=false` se retiró. La duplicación productiva de la cascada se sustituye por la política; el sitio BuyExhaustion de handleSignal permanece fuera de esta cascada, según el carve-out aprobado.

Se preservan las etapas con efectos: análisis/agotamiento en etapa10, adaptativo11, capital12 y razonamiento S/R13. Los suppliers se ejecutan solo al alcanzar su etapa; el análisis sintético es lazy. El contexto captura los datos puros y el reloj para una evaluación coherente. No cambian estrategias ni umbrales.

## Evidencia local

- Snapshot exacto de las 268 líneas anteriores: LEGACY_HEADLESS_CASCADE_82afad5.txt.
- Oráculo de prueba: copia congelada del evaluador de 82afad5, con adaptación únicamente de smart-cast nullable entre módulos. No es un segundo evaluador de producción.
- Prueba diferencial determinista (semilla2929): 3.000 contextos; igualdad de Allowed/Vetoed completa, incluidos mensajes de eventos, evento secundario, contador, desarme, toast y confianza. Verificación del orden y ausencia de llamadas a suppliers posteriores a un veto. Se conservan los casos dirigidos de cada uno de los 13 vetos.
- Suite: 639 app + 4 core, 0 fallos/errores/skipped, replay incluido; assembleDebug PASS (1m6s). Python43 pasó en corrección1; esta ronda solo mueve la cascada Kotlin.
- Primer intento de compilación detectó razonamiento S/R fuera de contexto; se convirtió en supplier de etapa13. Oráculo test necesitó adaptación de smart-cast por frontera test/main. Ambos corregidos antes del PASS.

## Límites y siguiente gate

El oráculo congela el evaluador previo y la revisión lo contrasta con el snapshot inline; no es una reproducción instrumentada del motor Android histórico. Los metadatos de efectos se comparan automáticamente; su aplicación Android todavía requiere trazas de integración de señal completa en dispositivo/dobles. No se usa esta suite para declarar cerrado el recorrido señal→riesgo ni todos los gates R29-B.

Pendiente de esta ronda al crear el reporte: commit/push, CI, APK con SHA del commit e instalación/arranque. La simulación durable y T-5.2..4/F6 continúan pendientes.


## Actualización de verificación

Commit de política `735f420` publicado. CI push 42959102967: success (https://github.com/Mizaring467/Tradedraw/actions/runs/42959102967).

Antes de la instalación se detectó en el SDK local que Process.waitFor(timeout, TimeUnit) requiere API26, frente a minSdk24. Corrección adicional: espera acotada con exitValue/nanoTime/sleep (API1), sin cambiar los límites de 1 s/2 s. Dos pruebas con procesos reales comprueban salida normal y timeout. Suite posterior: **641 app + 4 core**, 0 fallos/errores/skipped, replay y assembleDebug PASS (1m8s). Se instala el candidato que incluye esta corrección, no el intermedio.
