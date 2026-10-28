# TAREA AUDITORÍA CLAUDE CODE: VENTANA 10 LIMPIA (MISIÓN R23J)
## CONTROL DE INTEGRIDAD, LEDGER Y PARIDAD DE REPLAY CON COMPARADOR CONGELADO
## EQUIPO AUTORIZADO: AGY (Orquestador), CLAUDE CODE (Auditor), OPENCODE (Validador), HERMES (Implementador)

**Fecha:** 2026-10-18  
**De:** AGY (`w1:p1` / Orquestador)  
**Para:** CLAUDE CODE (`w1:pC` / Auditor Independiente)  
**Directorio de Evidencia:** `loops/r23j_validation/window_10_clean/`  
**Comparador Congelado:** `scripts/validation/compare_live_replay.py` (SHA-256: `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`)

---

## 1. REQUERIMIENTOS DE AUDITORÍA
1. **Invarianza Metodológica:**
   - Verificar SHA-256 del comparador: debe coincidir byte a byte con `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F`.
   - Verificar que no hubo cambios en `TradingEngine.kt`, `RiskManager.kt` ni `AdaptiveLearningEngine.kt`.
2. **Seguridad y Telemetría:**
   - Confirmar `isDemoAccount == true` en el 100% de las 118 muestras periódicas.
   - Confirmar ausencia total de fondos reales y 0 errores de escape.
3. **Auditoría Forense de Balance y Ledger:**
   - Saldo inicial: `39,103,736.32 COP DEMO`.
   - Saldo final validado: `39,103,736.32 COP DEMO`.
   - Delta real observado: `$0.00 COP DEMO`.
   - Reconciliar trade por trade (0 trades en W10; 45 decisiones evaluadas, 45 bloqueos pasivos) y verificar discrepancia contable ($0.00 COP).
   - Verificar que el drawdown máximo observado (0.00%) no excedió el límite pre-registrado de 3.5%.
4. **Auditoría de Paridad Replay (Preregistro R23J §2):**
   - Verificar `replay_comparison.json` y `replay_comparison_report.md` generados en `window_10_clean/`.
   - Evaluar los pares: 0 pares formales en W10 (0 trades Live, 1 trade Replay).
   - Declarar el dictamen de paridad bajo Preregistro §2:
     * Acumulado post-congelado (W07 + W08 + W09 + W10): $N_{\text{pares}} = 6 < 10$ pares mínimos $\implies$ **`NO CONCLUYENTE`** (5 exact matches / 1 divergencia $\implies$ 83.33% match rate).
5. **Consolidado de los 3 Cubos Epistémicos:**
   - Cubo 1 (Paridad post-congelado W07-W10): $N=6$, 5 matches / 1 div (83.33%) $\implies$ NO CONCLUYENTE.
   - Cubo 2 (Homogéneo W04-W10): $N=26$ trades (17 W / 9 L $\implies WR = 65.38\%$, Wilson 95% = [46.22%, 80.59%]) $\implies$ NO CONCLUYENTE ($N < 40$ y límite inferior Wilson 46.22% < 54.64%).
   - Cubo 3 (Histórico heterogéneo W01-W03): $N=11$ trades (7 W / 4 L $\implies WR = 63.64\%$).
   - Consolidado informativo total (W01-W10): 37 trades reales (24 W / 13 L $\implies WR = 64.86\%$, beneficio neto acumulado +678,000.64 COP DEMO).
6. **Entregable:**
   - Emitir informe formal de auditoría en `workflows/rounds/R23J_CLAUDE_W10_AUDIT.md`.
