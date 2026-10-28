# R23C — FASE 2B: CAPTURA INCREMENTAL DE TICKS Y CONEXIÓN DE COMPARADOR (HERMES)

**Destinatario:** HERMES (`w1:p6`)  
**Emisor:** AGY (`w1:p1`)  
**Fecha:** 2026-10-16  
**Fase:** R23C — Mejora de Tooling Host (Live Monitor)  
**Objetivo:** Eliminar el límite de retención de 13,8 minutos del buffer `/ticks` implementando captura incremental acumulativa en `live_monitor_r23_autonomous.py`, e inyectar `BACKTEST_SUBMODE=YOLO` y `--mode trades` en la invocación post-ventana.

---

## 1. AUTORIZACIONES EXPLÍCITAS Y ALCANCE
- **Autorización 1:** Se autoriza modificar `scripts/validation/live_monitor_r23_autonomous.py`.
- **Aislamiento APK:** Cero cambios en `app/src/main/` y cero modificaciones al teléfono o a la APK congelada (`ac079fd`).
- **Ajuste de Criterio de Aceptación:**
  - A nivel de ventana: Cobertura del 100% de los trades con ticks completos disponibles dentro de la ventana, con exact match $\ge 80\%$.
  - A nivel de fase: Acumular $\ge 30$ pares a lo largo de la serie multiventana (w01..w06).

---

## 2. ESPECIFICACIÓN DE CAMBIOS EN `live_monitor_r23_autonomous.py`

### REQ 1: Captura Incremental de Ticks en el Bucle Principal
- Durante el bucle de muestreo de 10 segundos (o en cada iteración del monitor):
  - Consultar `{base_url}/ticks?max=2000&format=csv`.
  - Parsear las líneas `ts,ric,price` y almacenarlas en un diccionario en memoria:
    ```python
    # accumulated_ticks: Dict[int, str] = {}
    for line in csv_lines:
        ts = int(parts[0])
        accumulated_ticks[ts] = line
    ```
- Al concluir los 20 minutos de la ventana:
  - Guardar en `out_ticks_csv` (`ticks_live.csv`) todas las líneas acumuladas ordenadas por `ts` ascendente con cabecera `ts,ric,price`.
  - Esto garantiza cobertura del 100% de los 20 minutos (evitando la pérdida de los primeros 6 minutos que descarta el ring buffer del teléfono).

### REQ 2: Inyección de Submodo y Conexión de Replay
- En la sección de ejecución de replay post-ventana:
  - Obtener el submodo activo desde `/status` (ej. `YOLO`).
  - Pasar al entorno del backtest:
    ```python
    env["BACKTEST_SUBMODE"] = active_submode  # "YOLO"
    ```
- En la llamada a `compare_live_replay.py`:
  - Usar `--mode trades`:
    ```python
    cmd_compare = [
        sys.executable,
        "scripts/validation/compare_live_replay.py",
        "--mode", "trades",
        "--live", out_trades_csv,
        "--replay", out_replay_csv,
        "--out", out_comp_json,
        "--report", out_comp_report
    ]
    ```

---

## 3. VERIFICACIÓN Y ENTREGABLE
1. Ejecutar prueba estática / de sintaxis de `scripts/validation/live_monitor_r23_autonomous.py`.
2. Probar retrospectivamente sobre `loops/r23c_yolo_validation/window_05/` (o regenerar el replay de w05 con `BACKTEST_SUBMODE=YOLO`).
3. Commit del cambio y reporte breve a AGY en `w1:p1`.
