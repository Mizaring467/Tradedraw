# R21 — Asignación a HERMES: Frente B (Comparador Live ↔ Replay)

**Responsable:** Hermes (`w1:p6`)  
**Orquestador:** AGY (`w1:p1`)  
**Fecha:** 2026-10-14  
**Workspace:** `w1`  
**Directorio de trabajo:** `C:\Users\heidy\Tradedraw`  
**Archivos reservados para Hermes:** `scripts/validation/compare_live_replay.py`, `scripts/validation/*`, `workflows/rounds/r21_replay_comparison_report.md`  
**PROHIBIDO:** Editar archivos Kotlin (reservados por AGY).

---

## 1. Contexto y Objetivo

TradeDraw necesita una herramienta automática que tome una sesión DEMO real (o ticks/eventos registrados) y la reproduzca offline determinísticamente contra el replay.

Para cada `decision_id`:

Comparar los siguientes campos:

### En LIVE:
- timestamp
- asset
- precio
- candle second
- régimen
- tendencia
- dist_support
- dist_resistance
- confluencia
- estrategia
- dirección
- veto
- decisión

### En REPLAY:
Los mismos campos.

---

## 2. Salida por Decisión

- `MATCH`
- `DIVERGENCE`

Si diverge:
Identificar **LA PRIMERA condición conocida que difirió**.

Ejemplo:
```
decision_id=...
LIVE action=SELL
REPLAY action=NONE
first_divergence=market_regime
live=UPTREND
replay=SIDEWAYS
```

---

## 3. Entregables Obligatorios

1. Script Python reproducible:
   `scripts/validation/compare_live_replay.py`
   - Debe soportar CLI con argumentos (e.g., `--live-journal <path>`, `--ticks-csv <path>`, `--output-json <path>`, `--output-md <path>`).
   - Si no hay sesión live capturada con ticks completos para un `decision_id`, debe generar un fixture de prueba sintético determinista demostrando la comparación `MATCH` vs `DIVERGENCE`.
2. Salida estructurada machine-readable (JSON):
   - Con métricas de resumen:
     - `exact_matches`
     - `divergences`
     - `missing_live`
     - `missing_replay`
     - `unverifiable`
3. Reporte Markdown:
   `workflows/rounds/r21_replay_comparison_report.md`
4. NO modificar la estrategia de trading.

---

## 4. Instrucción de Cierre

Al terminar, reporta a AGY en el buffer de Herdr con el resumen de métricas y la ruta de los entregables.
