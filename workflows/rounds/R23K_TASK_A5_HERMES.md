# TAREA A5: MONITOR V2 CON GRACIA DE LIQUIDACIÓN Y CIERRE SEGURO
## Asignado a: HERMES (`w1:pA`)
## Orquestador: AGY (`w1:p1`)
## Rama: r10-night · Repositorio: C:\Users\heidy\Tradedraw

### OBJETIVO
Actualizar `scripts/validation/live_monitor_r23_autonomous.py` para blindar el fin de ventana contra el defecto de infraestructura observado en W10 (cierre abrupto sin gracia que contaminó W11 con el trade de frontera).
REGLA ESTRICTA: SOLO arnés de medición. NO tocar el comparador (`compare_live_replay.py`) ni la aplicación Android.

### ESPECIFICACIÓN DE MONITOR V2
1. **Transición a DISABLED al fin nominal:**
   - Al cumplirse la hora nominal de fin de la ventana (`now >= end_time`):
   - Inmediatamente realizar petición a `/mode?name=DISABLED` (o `/mode?mode=DISABLED`) para evitar que el bot despache nuevas entradas.
2. **Espera activa de liquidación (Grace Period):**
   - Verificar inmediatamente el `/status` fresco del bridge.
   - Si `orderState != "NINGUNA"` o `hasPendingTrade == True` (o hay un trade despachado $\le$ fin nominal sin resolver):
     - Esperar hasta que `orderState == "NINGUNA"` y `hasPendingTrade == False`.
     - Tope máximo de espera: **150 segundos**.
   - Registrar la duración exacta de la espera en milisegundos (`grace_wait_ms`) y en segundos en `window_metadata.json` y consola.
3. **Criterio de pertenencia de trades:**
   - Un trade pertenece a la ventana si y solo si su timestamp de DESPACHO es $\le$ fin nominal (`nominal_window_end_ts`), independientemente de que liquide durante el periodo de gracia.
4. **Self-tests incorporados (`--test`):**
   - Crear una suite de self-tests ejecutables con `python scripts/validation/live_monitor_r23_autonomous.py --test` con secuencias simuladas de respuestas de `/status`:
     - **Caso A (Trade abierto al final):** Simula un trade en curso al fin nominal que liquida a los 60s -> el monitor espera, captura la resolución, registra `grace_wait_ms` y finaliza con éxito.
     - **Caso B (Trade abierto que no liquida):** Simula que `orderState` permanece en `CONFIRMADA` sin resolver -> el monitor agota los 150s de tope, no crashea, registra el timeout en metadata y finaliza de forma segura.
     - **Caso C (Bridge caído):** Simula fallo de conexión HTTP al bridge al cierre nominal -> reintentos controlados sin excepciones no controladas.

### ENTREGABLES
- `scripts/validation/live_monitor_r23_autonomous.py` actualizado con Monitor v2 y self-tests.
- Ejecución limpia de `python scripts/validation/live_monitor_r23_autonomous.py --test` (100% PASS).
- Reporte conciso en el panel `w1:pA` detallando los cambios y la salida de los tests.
