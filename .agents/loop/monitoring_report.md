# Reporte de Auditoría en Tiempo Real · Master Traders Skill

**Dispositivo:** `5PPFAACU6H7XHEY9`  
**Tiempo Transcurrido:** `30m 1s` / `30m 00s` (`100.0%`)  
**Última Actualización:** `2026-09-19 01:52:21`  
**Estado de Conexión:** 🟢 Activo (Monitoreo continuo ADB)

---

## 1. Métricas Clave de Auditoría Cuantitativa

| Métrica | Valor Observado | Criterio de la Skill | Estado |
| :--- | :---: | :---: | :---: |
| **Cumplimiento Cronológico Sniper (:58-:03)** | **65.0%** | 100% obligatorio | 🔴 VIOLACIÓN |
| **Órdenes Disparadas en Ventana Veto (:06-:57)** | **7** | 0 órdenes (Veto Universal) | 🔴 VIOLACIÓN |
| **Vetos Anti-Choppiness / Micro-Rango** | **0** | Bloqueo si CHOP > 61.8 o Dojis | 🟢 ACTIVO |
| **Vetos por Temporización Fuera de Sniper** | **0** | Veto Universal :06-:57 | 🟢 ACTIVO |
| **Señales MT_REJECTION (Mecha >= 45%)** | **0** | Ratio mecha >= 0.45, cuerpo <= 0.40 | 🟢 REGISTRADO |
| **Señales MT_3_VELAS_AGOT** | **0** | Paridad color, C3 <= 45% C1 | 🟢 REGISTRADO |
| **Señales MT_CHOQUE_PULLBACK** | **0** | Retest <= 3px en :01-:05 | 🟢 REGISTRADO |
| **Total de Señales Evaluadas** | **0** | Algoritmos de acción del precio | 🟢 PROCESANDO |
| **Total Órdenes Táctiles Confirmadas** | **20** | Solo con confluencia >= 80 pts | 🟢 EN REGLA |
| **Puntaje Promedio de Confluencia** | **100.0 pts** | Mínimo 80 pts para trade | 🟢 AUDITADO |

---

## 2. Telemetría de Mercado y HUD

- **Último Precio Observado:** `UNKNOWN`
- **Tendencia del HUD:** `UNKNOWN`
- **Probabilidad / Termómetro:** `UNKNOWN`

---

## 3. Últimos Eventos Registrados (Historial Reciente)

- **[1801s] ✅ `MONITOR_END`**: Sesión de auditoría concluida con éxito.
- **[1778s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:52:00.279 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (11s)
- **[1778s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:52:00.259 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (11s)
- **[1662s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:50:04.326 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (127s)
- **[1662s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:50:04.325 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (127s)
- **[1662s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:50:03.920 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (127s)
- **[1662s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:50:03.915 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (127s)
- **[1662s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:50:03.508 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (128s)
- **[1662s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:50:03.506 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (128s)
- **[1661s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:50:03.096 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (128s)
- **[1661s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:50:03.094 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (128s)
- **[1660s] ✅ `VETO_SR_OR_MOMENTUM`**: 09-19 01:50:01.976 D/TradingEngine(15828): Headless bloqueado por riesgo: Pausa Anti-Tilt tras fallo Martingala (129s)
