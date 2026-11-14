# B1 · VERIFICACIÓN INDEPENDIENTE (ORQUESTADOR)

**Fecha:** 2026-10-22 18:46 America/Bogota
**SHA verificado:** `7be78e8` (fix de ruta manual), padre `6fc51f4`
**Ejecutado por:** ORQUESTADOR (no por el VALIDADOR, que quedó colgado sin escribir resultado)

## 1. Compilación

```
.\gradlew.bat assembleDebug --no-daemon --no-configuration-cache
```
**BUILD SUCCESSFUL.** APK generado: `app/build/outputs/apk/debug/app-debug.apk`, 13.818.164 B, 18:33:15.

## 2. Suite completa

```
.\gradlew.bat testDebugUnitTest --no-daemon --no-configuration-cache
```

| Métrica | Valor |
| :--- | :--- |
| Tests totales | **488** |
| Fallos | **0** |
| Errores | **0** |
| Skipped | 2 |

Agregado sobre los 100+ XML de `app/build/test-results/testDebugUnitTest/`.

## 3. Tests de idempotencia (`LiquidationIdempotencyTest`)

Ejecución aislada, 9/9 PASS:

| Test | Resultado |
| :--- | :--- |
| `sinTradeIdSeUsaElTimestampDeApertura` | PASS |
| `rutaManualYAutomaticaConMismoTradeIdCuentanUnaVez` | PASS |
| `rutaManualYAutomaticaSinTradeIdCuentanUnaVez` | PASS |
| `incrementoManualSeEtiquetaYSeContabilizaAparte` | PASS |
| `flujoNormalIncrementaUnaVez` | PASS |
| `manualSinPendienteSigueIncrementando` | PASS |
| `tradeIdDistintoIncrementaDosVeces` | PASS |
| `tradesLossSinEvidenciaNoSeReiniciaConResetStats` | PASS |
| `dosHilosMismoTradeIdIncrementanUnaSolaVez` | PASS |

### Incidente de verificación (registrado)

La primera corrida del VALIDADOR (18:36:54) reportó **1 fallo** en
`rutaManualYAutomaticaSinTradeIdCuentanUnaVez`. Ese fallo era **real pero de un build previo**:
el fix `7be78e8` se commiteó a las 18:37:44, **después** de esa corrida. Re-ejecutado sobre el
código con el fix, el test pasa. No hubo regresión; hubo desfase de 50 s entre corrida y commit.

## 4. Estrategia congelada

```
git diff 82f7289 7be78e8 -- app/src/main/ | Select-String "0\.\d+f|SNIPER_|ADAPTIVE_BLOCK"
```
Única coincidencia: `riskManager.baseAmount = 10.0f` en el **setup del test nuevo**.
**Cero constantes de estrategia modificadas.**

## 5. Contador monótono

`lossSinEvidenciaCounter` (AtomicInteger) aparece solo en: declaración (L301), getter (L305),
incremento (L309). **Ninguna** referencia dentro de `resetStats:897`, `setStats:907`,
`correctStats:917`, `resetSession:983`, `resetSniperSession:1003`.

## 6. Bug encontrado por el AUDITOR y corregido

El AUDITOR independiente detectó que las rutas manuales (`OverlayService.kt:1274/1279`,
`TradeDrawHttpBridge.kt:377/385`) llamaban `recordTradeWin/Loss(force=true)` sin pasar por
`markLiquidated`. Como `recordTradeWins` termina en `clearPendingTrade()`, el fallback automático
hallaba la clave libre y contaba dos veces.

Corregido en `7be78e8` con `settleManual(origin, win)` (`RiskManager.kt:389-425`), que marca la
clave **siempre** (incluido `ts:<start>` para trades sin `tradeId`) y solo entonces incrementa.

El IMPLEMENTADOR encontró además dos defectos propios en el primer intento de fix:
(a) condicionar `markLiquidated` a `id.isNotEmpty()` dejaba abiertos los trades sin id;
(b) llamar `markManualIncrement` antes de ganar la clave hacía `totalWins - manualWins` negativa.

## Veredicto

**PASS-AUTOR.** Compilación verde, 488/488 tests, estrategia intacta, contador protegido,
bug del auditor cerrado con test de regresión.
