# R23C — ANÁLISIS DE BRECHA ESTRUCTURAL LIVE ↔ REPLAY (GAP ANALYSIS)

**Fecha:** 2026-10-16  
**Autor:** AGY (Orquestador)  
**Destinatario:** HERMES (`w1:p6`), OpenCode (`w1:pT`)  
**Objetivo:** Mapear detalladamente los dos pipelines de ejecución (Live vs Replay), identificar con precisión matemática y arquitectónica por qué la paridad se mantiene en 0% (2/63 pares), y definir el contrato de diseño para que Hermes implemente la paridad sin sobreingeniería.

---

## 1. MAPEO COMPARATIVO DE LOS DOS PIPELINES

```mermaid
flowchart TD
    subgraph LIVE_PIPELINE [Pipeline de Ejecución en Vivo - Hardware Real]
        L_WS[WebSocket Ticks Z-CRY/IDX] --> L_FEED[BinomoWebSocketClient]
        L_FEED --> L_ENG_TICK[SyntheticCandleEngine.onNewTick]
        L_ENG_TICK --> L_SIG[SyntheticCandleEngine.onSignalGenerated]
        L_SIG --> L_TE[TradingEngine.executeHeadlessTrade]
        
        L_TE --> L_WIN{isInEntryWindow :00-:05?}
        L_WIN -- NO --> L_V_WIN[Veto Ventana]
        L_WIN -- SI --> L_1C{1 orden por vela?}
        L_1C -- NO --> L_V_1C[Veto Vela Repetida]
        L_1C -- SI --> L_RISK{RiskManager.canExecuteTrade}
        
        L_RISK -- BLOQUEO --> L_V_RISK[Veto RiskManager: Cooldown / PendingTrade / Tilt]
        L_RISK -- OK --> L_ADAPT{AdaptiveLearningEngine.shouldBlock}
        L_ADAPT -- SI --> L_V_ADAPT[ADAPTIVE_BLOCK Anti-patrón S/R]
        L_ADAPT -- NO --> L_VETO1{Veto V1 Conflicto S/R?}
        L_VETO1 -- SI --> L_V_V1[Veto V1]
        L_VETO1 -- NO --> L_VETO2{Veto V2 Rango/Volatilidad?}
        L_VETO2 -- SI --> L_V_V2[Veto V2]
        L_VETO2 -- NO --> L_TAP[TapDispatcher en DEMO]
        
        L_TAP --> L_ORDER[OrderState: ENVIADA -> CONFIRMADA]
        L_V_WIN & L_V_1C & L_V_RISK & L_V_ADAPT & L_V_V1 & L_V_V2 --> L_EVENT_LOG[TradeEventLog Buffer en Memoria]
        L_ORDER --> L_EVENT_LOG
        L_EVENT_LOG --> L_HTTP[/events y decisions.csv del Monitor: 60+ filas]
    end

    subgraph REPLAY_PIPELINE [Pipeline de Replay Offline - ReplayBacktestTest.kt]
        R_CSV[CSV Ticks ticks_live.csv] --> R_LOAD[loadTicks y deduplicar]
        R_LOAD --> R_SCE[SyntheticCandleEngine Aislado]
        R_SCE --> R_SUB[subMode = CONSERVATIVE hardcoded]
        R_SUB --> R_SIG[SyntheticCandleEngine.onSignalGenerated]
        R_SIG --> R_CHECK{candleMinute != lastExecuted?}
        R_CHECK -- SI --> R_TRADE[SimulatedTrade directo]
        R_CHECK -- NO --> R_DROP[Descartado en silencio]
        R_TRADE --> R_JOURNAL[replay_journal.csv: Solo 2 filas de Trades]
    end
```

---

## 2. LAS 4 CAUSAS RAÍZ DEL DESACOPLAMIENTO LIVE ↔ REPLAY

### Causa 1: Disparidad de Universos (Decisiones/Vetos vs Trades Simulados)
- **En Live:** El monitor (`live_monitor_r23_autonomous.py`) captura de `/events` **todas las decisiones y vetos** emitidos por el motor vivo. En una ventana típica de 20 minutos, genera entre **60 y 70 registros en `decisions.csv`**, de los cuales $\approx 55\text{--}65$ son **VETOS** (`ADAPTIVE_BLOCK`, `COOLDOWN`, `OPERACION_EN_CURSO`) y solo $5\text{--}6$ son trades reales.
- **En Replay:** `ReplayBacktestTest.kt` **NO modela ni emite vetos**. Únicamente escribe en `replay_journal.csv` las operaciones simuladas que superaron el filtro de señal ($1\text{--}2$ filas).
- **Consecuencia:** `compare_live_replay.py` toma las 63 decisiones de `decisions.csv` e intenta emparejarlas contra las 2 de `replay_journal.csv`. Resultado inevitable: **61 `missing_replay`** y paridad $\approx 0\%$.

### Causa 2: Desalineación de Submodo (YOLO en Vivo vs CONSERVATIVE en Replay)
- **En Live:** El dispositivo opera en `submode = YOLO`. Los umbrales técnicos en `SyntheticCandleEngine` están relajados:
  - Confluencia mínima: $75\text{ pts}$ (vs $80\text{ pts}$).
  - Mecha de rechazo: $30\%$ (vs $50\%$).
  - Filtro de caída de volatilidad en Choppiness: **Desactivado**.
- **En Replay:** En `ReplayBacktestTest.kt:151`:
  ```kotlin
  subMode: AutonomousSubMode = AutonomousSubMode.CONSERVATIVE
  ```
  El submodo está fijado por defecto a `CONSERVATIVE` y no existe variable de entorno para sobreescribirlo (`BACKTEST_SUBMODE` no existe).
- **Consecuencia:** El motor de Replay utiliza los umbrales estrictos de CONSERVATIVE, por lo que suprime la mayoría de las señales que el motor vivo sí disparó en YOLO.

### Causa 3: Ausencia Total de los Guards de Runtime en Replay
En `ReplayBacktestTest.kt`:
1. **`AdaptiveLearningEngine` no existe:** Replay no tiene memoria de anti-patrones. En vivo, `ADAPTIVE_BLOCK` vetó 18 operaciones en la iteración 2; en replay, esas 18 operaciones se convierten en trades simulados.
2. **`RiskManager` no existe:** Replay no modela el cooldown post-operación ($8\text{ s}$ en YOLO), ni bloquea nuevas entradas mientras hay una operación abierta ($60\text{ s}$).
3. **Ventana Sniper :00-:05 no existe:** Replay admite señales en cualquier segundo si la vela no fue operada.

### Causa 4: Timestamp y Hash Determinista de Decisión
- El `decision_id` se calcula mediante:
  `TradingEngine.generateDeterministicDecisionId(asset, timestampMs, action)`
- Si el timestamp del tick en el que evaluó el motor en vivo difiere por milisegundos del tick que Replay asocia (debido a deduplicación o latencia de socket), el hash SHA-256 / UUID determinista resulta completamente distinto y el comparador no los puede enlazar.

---

## 3. ESPECIFICACIÓN DE LA SOLUCIÓN MÍNIMA PARA HERMES (FASE 2)

Para lograr `paired_decisions >= 30` y `exact_match_rate >= 90%` sin sobreingeniería (filosofía Ponytail), Hermes deberá implementar una solución en dos partes:

### Parte A: En `ReplayBacktestTest.kt` (Soporte de Submodo y Runtime Guards)
1. **Soporte de Submodo Dinámico:**
   Leer variable de entorno `BACKTEST_SUBMODE`:
   ```kotlin
   val subModeEnv = System.getenv("BACKTEST_SUBMODE") ?: "CONSERVATIVE"
   val subMode = AutonomousSubMode.valueOf(subModeEnv.uppercase())
   ```
2. **Modelado de Guards Críticos en el Bucle de Replay:**
   En lugar de escribir un trade ciego para cada señal, Replay debe consultar los mismos guards del motor:
   - Respetar ventana sniper (:00-:05 / :58-:59).
   - Respetar espaciado de operación abierta (60s de duración de trade sin solapes).
   - Respetar cooldown post-trade ($8\text{ s}$ en YOLO).
3. **Emisión de Registro de Decisiones de Replay:**
   Si una señal es vetada en replay por cooldown u operación abierta, registrar el evento con su `decision_id` determinista, para que el universo de eventos de Replay sea isomorfo al de Live.

### Parte B: En `compare_live_replay.py` y `live_monitor_r23_autonomous.py`
1. **Separación de Comparación de Trades vs. Comparación de Decisiones:**
   Permitir dos modos de comparación:
   - **Modo Trades (Foco Operativo):** Emparejar `trades.csv` de Live contra los trades de `replay_journal.csv` por ventana de tiempo ($\pm 2\text{ s}$).
   - **Modo Decisiones (Foco Señales):** Emparejar `decisions.csv` contra las decisiones evaluadas de Replay.
2. Pasar `BACKTEST_SUBMODE=YOLO` en el comando que ejecuta `ReplayBacktestTest` dentro del monitor.

---

## 4. CRITERIO DE ACEPTACIÓN
- Cobertura de pares: $\ge 30$ decisiones/trades pareados.
- Exact Match Rate: $\ge 90\%$.
