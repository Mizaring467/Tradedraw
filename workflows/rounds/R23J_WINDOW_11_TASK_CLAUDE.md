# TAREA AUDITORÍA CLAUDE CODE: VENTANA 11 LIMPIA (MISIÓN R23J)
## CONTROL DE INTEGRIDAD, LEDGER Y PARIDAD DE REPLAY CON COMPARADOR CONGELADO
## EQUIPO AUTORIZADO: AGY (Orquestador), CLAUDE CODE (Auditor), OPENCODE (Validador), HERMES (Implementador)

**Fecha:** 2026-10-19  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** CLAUDE CODE (`w1:pC` / Auditor Independiente)  
**Directorio de Evidencia:** `loops/r23j_validation/window_11_clean/`  
**Comparador Congelado:** `scripts/validation/compare_live_replay.py` (SHA-256: `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`)

---

## 1. REQUERIMIENTOS DE AUDITORÍA
1. **Invarianza Metodológica:**
   - Verificar SHA-256 del comparador: debe coincidir byte a byte con `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`.
   - Verificar que no hubo cambios en `TradingEngine.kt`, `RiskManager.kt` ni `AdaptiveLearningEngine.kt`.
2. **Seguridad y Telemetría:**
   - Confirmar `isDemoAccount == true` en el 100% de las 119 muestras periódicas.
   - Confirmar ausencia total de fondos reales y 0 errores de escape.
3. **Auditoría Forense de Balance y Ledger:**
   - Saldo inicial certificado: `39,003,735.04 COP DEMO` (según `R23J_W10_BORDER_TRADE_RECONCILIATION.md`).
   - Saldo final validado: `39,085,736.96 COP DEMO`.
   - Delta real observado: `+82,001.92 COP DEMO`.
   - Reconciliar trade por trade (1 trade real en W11: `7476bd5b-fc43-460b-8cbf-a6f04e7b90d8`, BUY WIN, payout 83% $\to$ +82,001.92 COP) y verificar discrepancia contable ($0.00 COP).
   - Verificar que el drawdown máximo observado (0.47%) no excedió el límite pre-registrado de 3.5%.
4. **Auditoría de Paridad Replay (Preregistro R23J §2):**
   - Verificar `replay_parity.json` y `REPLAY_PARITY_REPORT.md` generados en `window_11_clean/`.
   - Evaluar los pares: 1 par formal evaluado en W11 (1 divergencia de acción, $\Delta t = -3,856$ ms).
   - Declarar el dictamen de paridad bajo Preregistro §2:
     * Acumulado post-congelado (W07 a W11): $N_{\text{pares}} = 7 < 10$ pares mínimos $\implies$ **`NO CONCLUYENTE`** (5 exact matches / 2 divergencias $\implies$ 71.43% match rate).
5. **Consolidado de los 3 Cubos Epistémicos:**
   - Cubo 1 (Paridad post-congelado W07-W11): $N=7$, 5 matches / 2 div (71.43%) $\implies$ NO CONCLUYENTE.
   - Cubo 2 (Homogéneo W04-W11): $N=27$ trades (18 W / 9 L $\implies WR = 66.67\%$, Wilson 95% = [47.82%, 81.36%]) $\implies$ NO CONCLUYENTE ($N < 40$ y límite inferior Wilson 47.82% < 54.64%).
   - Cubo 3 (Histórico heterogéneo W01-W03): $N=11$ trades (7 W / 4 L $\implies WR = 63.64\%$).
   - Consolidado informativo total (W01-W11): 38 trades reales (25 W / 13 L $\implies WR = 65.79\%$, beneficio neto acumulado +760,002.56 COP DEMO).
6. **Entregable:**
   - Emitir informe formal de auditoría en `workflows/rounds/R23J_CLAUDE_W11_AUDIT.md`.
