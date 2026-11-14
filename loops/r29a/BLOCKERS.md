# BLOCKERS — TradeDraw R29-A

| # | Ronda | Descripción | Impacto | Estado | Mitigación |
| :-- | :--- | :--- | :--- | :--- | :--- |
| B-1 | cierre | **OD-6**: trabajo R27/R28 + R29-A vive solo en el working tree (27 entradas, 0 commits). | Pérdida permanente ante cualquier incidente; bloquea F3/F6 de R29-B. | ABIERTO — decisión del operador (regla 10 prohíbe commitear sin autorización). | Opción recomendada (a): commit único en `r10-night` (P-1 de R6). |
| B-2 | cierre | **P-2**: CI no ejecuta `testDebugUnitTest` y solo cubre `main`. | Suite JVM sin verificación continua; "565 vigentes" solo por parseo. | ABIERTO — tarea T-0.2 de R29-B. | Añadir paso de tests + trigger `r10-night` al yml existente. |
| B-3 | cierre | **P-3/P-4**: autorizaciones de cambio de comportamiento (re-ruta `/trade` por modo; SHADOW→ciclo simulado). | F3 queda parcial (solo rate-limit) y F4 no inicia sin ellas. | ABIERTO — decisión del operador. | Flags off por defecto + esquema versionado si P-4. |
| B-4 | cierre | Heredados NO VERIFICADOS: suite Python 43, APK v21 en dispositivo, paridad live↔replay, heurística demo en cuentas reales, filtrado MIUI. | Evidencia incompleta documentada honestamente. | ABIERTO — fuera del alcance R29-A (prohibido por misión). | Delegados a R29-B / ventanas de validación con autorización. |
