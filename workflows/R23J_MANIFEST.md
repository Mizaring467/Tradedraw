# MANIFIESTO R23J — CONFIGURACIÓN CONGELADA DEL CICLO

**Fecha de emisión:** 2026-10-18
**Rama:** `r10-night`
**Emitido por:** HERMES (`w1:pA` / Implementador)
**Estado:** VIGENTE — este manifiesto describe el binario instalado y la configuración con la que se
medirán todas las ventanas de R23J

---

## 1. IDENTIDAD DEL BUILD

| Campo | Valor | Verificación |
|---|---|---|
| **Commit SHA** | `9442c1f` | `git log --oneline -1` |
| **SHA-256 del comparador** | `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F` | `sha256sum scripts/validation/compare_live_replay.py` |
| `versionCode` | `19` | `/status` |
| `versionName` | `1.9-r23h-exhaustion` | `/status` |
| `BUILD_ID` | `R23H-BUY-EXHAUSTION` | `/status` |
| `gitSha` en BuildConfig | `9442c1f` | `/status` + `BuildConfig.java` generado |

El `gitSha` que reporta la app coincide con el commit del árbol sobre el que se compiló. No hay
deriva entre el código medido y el binario instalado.

---

## 2. CONFIGURACIÓN OPERATIVA

| Parámetro | Valor |
|---|---|
| **Activo** | Crypto IDX |
| **RIC** | `Z-CRY/IDX` |
| **Modo** | `AUTONOMOUS` |
| **Submodo** | `YOLO` |
| **Cooldown** | `8000 ms` (`YOLO_COOLDOWN_MS = 8_000L`) |
| **Stake** | `100.000 COP` (cuenta **DEMO**) |
| **Payout observado** | `83%` (0.83) |
| **Dispositivo** | `UGU8KFUKM7E6CE8D` |
| **Estrategia** | `AUTO_ADAPTIVE` (congelada desde R23H) |
| **Política de veto activa** | `VETO_BUY_EXHAUSTION_CLIMAX` — BUY + UPTREND + callPower >= 70 |

El payout de `83%` no es supuesto: sale de `loops/r23i_validation/window_06_clean/LIVE_REPORT.md`
(`payout 0.83`) y de los créditos reales de W06 (+83.000,32 COP sobre stake de 100.000). El
`payoutConfigured` del binario es `0.85` como valor por defecto, pero el payout **resuelto en vivo**
manda para el cálculo de breakeven: `1/(1+0.83) = 54.64%`.

---

## 3. ESTADO DE SALUD AL MOMENTO DE LA INSTALACIÓN

Telemetría literal de `GET http://localhost:8080/status` tras instalar y arrancar `9442c1f`:

```json
{
  "app": "TradeDraw",
  "versionCode": 19,
  "versionName": "1.9-r23h-exhaustion",
  "gitSha": "9442c1f",
  "buildId": "R23H-BUY-EXHAUSTION",
  "isDemoAccount": true,
  "accessibilityActive": true,
  "mode": "AUTONOMOUS",
  "submode": "YOLO",
  "strategy": "AUTO_ADAPTIVE",
  "health": {
    "state": "BLOCKED",
    "reasons": [
      "SCREEN_FEED_MISMATCH: DESAJUSTE pantalla=DESCONOCIDA feed='Z-CRY/IDX'",
      "BALANCE_UNREADABLE"
    ]
  },
  "feed": {
    "source": "websocket",
    "connected": true,
    "isFresh": true,
    "lastTickAgeMs": 233,
    "activeAsset": "Z-CRY/IDX",
    "volatilidadSuficiente": true
  },
  "balance": 0.0
}
```

### 3.1 Verificación de los siete campos exigidos

| Campo | Exigido | Observado | Veredicto |
|---|---|---|---|
| `isDemoAccount` | `true` | `true` | OK — no hay riesgo de cuenta real |
| `versionName` | — | `1.9-r23h-exhaustion` | OK |
| `versionCode` | — | `19` | OK |
| `BUILD_ID` | — | `R23H-BUY-EXHAUSTION` | OK |
| `gitSha` | `9442c1f` | `9442c1f` | OK — coincide |
| `accessibilityActive` | `true` | `true` | OK |
| feed conectado y fresco | sí | `connected=true`, `isFresh=true`, `lastTickAgeMs=233` | OK |

**Los siete campos pasan.** La cuenta es DEMO, así que no se activó el STOP de seguridad de §3 del
encargo.

### 3.2 Advertencia operativa — `health = BLOCKED`

El feed está sano y la accesibilidad activa, pero el motor se declara `BLOCKED` por dos razones que
**no son defectos del build**:

1. **`SCREEN_FEED_MISMATCH`**: el veto de la Ronda 20 es fail-closed — exige confirmar por
   accesibilidad que el activo que sirve el feed es el que está visible en pantalla. En este
   dispositivo **no hay app de broker instalada** (verificado: `pm list packages` no encuentra
   Binomo ni Quotex; sólo `com.example.tradedraw` como paquete de trading). Sin pantalla de broker,
   la confirmación es imposible por diseño y el veto veta.
2. **`BALANCE_UNREADABLE`**: no hay UI de broker de la que leer el saldo, así que `balance = 0.0` y
   `risk.observedStake = 0.0`. El stake efectivo cae al mínimo (`1.0`) por la lógica de degradación
   segura.

**Consecuencia para la campaña:** en este estado el bot **no despachará taps**, y por tanto las
ventanas de R23J producirán 0 trades hasta que se resuelva. Esto es correcto y esperado: el sistema
falla cerrado, no abierto. Pero significa que **A3 no valida capacidad operativa**, sólo la correcta
instalación y el fail-closed.

El panel de la app muestra "SESIÓN BINOMO: VINCULADA ✓", lo que corresponde a la **sesión web**
autenticada vía WebSocket (de ahí que el feed de precios funcione con 67 ticks), no a una app nativa
instalada. Los ticks llegan; los taps no tienen destinatario.

---

## 4. TRAZABILIDAD DE LA CONSTRUCCIÓN

```
./gradlew.bat cleanTest testDebugUnitTest --rerun-tasks
  -> EXIT=0
  -> TESTS=468  FAILURES=0  ERRORS=0  SKIPPED=2  (50 suites)

./gradlew.bat assembleDebug
  -> EXIT=0  BUILD SUCCESSFUL in 15s
  -> app-debug.apk 13.815.195 bytes

adb -s UGU8KFUKM7E6CE8D install -r app/build/outputs/apk/debug/app-debug.apk
  -> Performing Streamed Install / Success
```

Base de tests: 466 antes de R23J + 2 de `PoderesDeMercadoEquivalenciaTest` = **468**. Los 2 skipped
son `ReplayBacktestTest` con `assumeTrue` sobre `BACKTEST_CSV`, preexistentes y ajenos a este ciclo.

---

## 5. CONGELACIÓN Y ALCANCE

1. **El comparador no se toca.** Su SHA-256 está fijado en `workflows/PREREGISTRO_R23J.md` §1 y
   reproducido aquí. Cualquier cambio invalida las ventanas medidas.
2. **La estrategia no se toca.** Este manifiesto describe un binario; no autoriza cambios en BUY,
   SELL, `callPower`, `ADAPTIVE_BLOCK`, stake, payout ni filtros.
3. **Los criterios de veredicto viven en el preregistro**, no aquí. Este documento sólo fija QUÉ se
   ejecutó y CON QUÉ configuración.
4. Si se recompila, este manifiesto queda obsoleto y debe emitirse uno nuevo con el SHA
   correspondiente.

---

## 6. PENDIENTE ANTES DE MEDIR VENTANAS

Las ventanas de R23J no son interpretables hasta resolver el bloqueo de §3.2. Sin app de broker en
pantalla no hay taps, no hay trades, y una ventana sin trades produce `pares_formales = 0`, que bajo
el preregistro §2.3 es **`NO CONCLUYENTE`** por definición.

Opciones, en orden de preferencia:

1. **Instalar y abrir el broker** (Binomo/Quotex) en el dispositivo con sesión DEMO, y repetir la
   verificación de `/status` hasta `health = OK`.
2. **Revisar el diseño fail-closed del veto de pantalla** si se quiere operar sin UI de broker
   visible — pero eso es un cambio de estrategia y **requiere ciclo propio**, no puede colarse en
   R23J.
3. **Declarar R23J bloqueado en fase de ejecución** y no medir ventanas hasta resolver (1).
