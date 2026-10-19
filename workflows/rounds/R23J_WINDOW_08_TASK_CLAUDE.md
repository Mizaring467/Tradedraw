# TAREA AUDITORÍA CLAUDE CODE: VENTANA 08 LIMPIA (MISIÓN R23J)
## CONTROL DE INTEGRIDAD, LEDGER Y PARIDAD DE REPLAY CON COMPARADOR CONGELADO
## EQUIPO AUTORIZADO: AGY (Orquestador), CLAUDE CODE (Auditor), OPENCODE (Validador), HERMES (Implementador)

**Fecha:** 2026-10-18  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** CLAUDE CODE (`w1:pC` / Auditor Independiente)  
**Directorio de Evidencia:** `loops/r23j_validation/window_08_clean/`  
**Comparador Congelado:** `scripts/validation/compare_live_replay.py` (SHA-256: `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`)

---

## 1. REQUERIMIENTOS DE AUDITORÍA
1. **Invarianza Metodológica:**
   - Verificar SHA-256 del comparador: debe coincidir byte a byte con `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`.
   - Verificar que no hubo cambios en `TradingEngine.kt`, `RiskManager.kt` ni `AdaptiveLearningEngine.kt`.
2. **Seguridad y Telemetría:**
   - Confirmar `isDemoAccount == true` en el 100% de las 118 muestras.
   - Confirmar ausencia total de fondos reales y 0 errores de escape.
3. **Auditoría Forense de Balance y Ledger:**
   - Saldo inicial: `39,337,735.68 COP DEMO`.
   - Saldo final validado: `39,120,737.28 COP DEMO`.
   - Delta real observado: `-216,998.40 COP DEMO`.
   - Reconciliar trade por trade (1 W / 3 L) y verificar discrepancia contable ($0.00 COP).
   - Verificar que el drawdown máximo observado (1.02%) no excedió el límite pre-registrado de 3.5%.
4. **Auditoría de Paridad Replay (Preregistro R23J §2):**
   - Verificar `replay_parity.json` y `REPLAY_PARITY_REPORT.md` generados en `window_08_clean/`.
   - Evaluar los 4 pares: 3 exact matches / 1 divergencia (75.0% match rate).
   - Analizar la divergencia (Live BUY vs Replay SELL).
   - Declarar el dictamen de paridad bajo Preregistro §2:
     * Acumulado post-congelado (W07 + W08): $N_{\text{pares}} = 5 < 10$ pares mínimos $\implies$ **`NO CONCLUYENTE`**.
5. **Consolidado de los 3 Cubos Epistémicos:**
   - Cubo 1 (Paridad post-congelado W07-W08): $N=5$, 4 matches / 1 div $\implies$ NO CONCLUYENTE.
   - Cubo 2 (Homogéneo W04-W08): $N=24$ trades (16 W / 8 L $\implies WR = 66.67\%$, Wilson 95% = [46.71%, 82.03%]) $\implies$ NO CONCLUYENTE ($N < 40$).
   - Cubo 3 (Histórico heterogéneo W01-W03): $N=11$ trades (7 W / 4 L $\implies WR = 63.64\%$).
   - Consolidado informativo total (W01-W08): 35 trades reales (23 W / 12 L $\implies WR = 65.71\%$, beneficio neto +695,001.60 COP DEMO).
6. **Entregable:**
   - Emitir informe formal de auditoría en `workflows/rounds/R23J_CLAUDE_W08_AUDIT.md`.
