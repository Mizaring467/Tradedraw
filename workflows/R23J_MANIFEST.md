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
| **Commit SHA** | `8cdb61f` | `git log --oneline -1` |
| **SHA-256 del comparador** | `4CB5082E4F8C52BCD775F0C5F3D53D992A3730007D1738D463A171F2C0794B5F` | `sha256sum scripts/validation/compare_live_replay.py` |
| `versionCode` | `19` | `/status` |
| `versionName` | `1.9-r23h-exhaustion` | `/status` |
| `BUILD_ID` | `R23H-BUY-EXHAUSTION` | `/status` |
| `gitSha` en BuildConfig | `8cdb61f` | `/status` + `BuildConfig.java` generado |

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
| **App de broker** | `com.marketly.trading` (Marketly / Pocket Option), en primer plano |
| **Estrategia** | `AUTO_ADAPTIVE` (congelada desde R23H) |
| **Política de veto activa** | `VETO_BUY_EXHAUSTION_CLIMAX` — BUY + UPTREND + callPower >= 70 |

El payout de `83%` no es supuesto: sale de `loops/r23i_validation/window_06_clean/LIVE_REPORT.md`
(`payout 0.83`) y de los créditos reales de W06 (+83.000,32 COP sobre stake de 100.000). El
`payoutConfigured` del binario es `0.85` como valor por defecto, pero el payout **resuelto en vivo**
manda para el cálculo de breakeven: `1/(1+0.83) = 54.64%`.

---

## 3. ESTADO DE SALUD DEL DISPOSITIVO

Telemetría literal de `GET http://localhost:8080/status` **tras instalar el build `8cdb61f`** (estado
vigente del binario sobre el que se medirán las ventanas). El estado previo en `9442c1f` era
`BLOCKED` con `SCREEN_FEED_MISMATCH` + `BALANCE_UNREADABLE` y `balance: 0.0`, causado por tener la
app del broker minimizada; se conserva documentado en §3.2.

```json
{
  "app": "TradeDraw",
  "versionCode": 19,
  "versionName": "1.9-r23h-exhaustion",
  "gitSha": "8cdb61f",
  "buildId": "R23H-BUY-EXHAUSTION",
  "isDemoAccount": true,
  "accessibilityActive": true,
  "mode": "DISABLED",
  "submode": "YOLO",
  "strategy": "AUTO_ADAPTIVE",
  "health": {
    "state": "READY",
    "reasons": []
  },
  "feed": {
    "source": "websocket",
    "connected": true,
    "isFresh": true,
    "lastTickAgeMs": 129,
    "activeAsset": "Z-CRY/IDX",
    "payoutResolved": 0.83,
    "volatilidadSuficiente": true
  },
  "balance": 39271736.32
}
```

### 3.1 Verificación de los siete campos exigidos

| Campo | Exigido | Observado | Veredicto |
|---|---|---|---|
| `isDemoAccount` | `true` | `true` | OK — no hay riesgo de cuenta real |
| `versionName` | — | `1.9-r23h-exhaustion` | OK |
| `versionCode` | — | `19` | OK |
| `BUILD_ID` | — | `R23H-BUY-EXHAUSTION` | OK |
| `gitSha` | `8cdb61f` | `8cdb61f` | OK — coincide |
| `accessibilityActive` | `true` | `true` | OK |
| feed conectado y fresco | sí | `connected=true`, `isFresh=true`, `lastTickAgeMs=129` | OK |

**Los siete campos pasan.** La cuenta es DEMO, así que no se activó el STOP de seguridad de §3 del
encargo.

### 3.2 Estado de salud — RESTABLECIDO A `READY`

**Corrección de un error de este manifiesto.** La primera emisión (commit `2d644ab`) afirmaba que en
el dispositivo «no hay app de broker instalada» y que por eso el motor estaba `BLOCKED`. **Eso era
falso.** La app de broker sí está instalada:

```
package:com.marketly.trading    (Marketly / Pocket Option)
```

Mi comando de búsqueda fue `pm list packages | grep -iE "binomo|quotex|pocket|trade|broker|binary|iq"`
y devolvió sólo `com.example.tradedraw`, aunque `com.marketly.trading` contiene la cadena `trade` y
debió aparecer. No tengo explicación verificada de por qué no salió, y no voy a inventarla. Lo que sí
está verificado, con dos comprobaciones independientes, es que **está instalada y corriendo**:
`pm list packages | grep marketly` la lista, y `dumpsys activity` muestra
`com.marketly.trading/.modules.trading.TradingActivity` como actividad resumida.

La lección operativa es la que importa: **una búsqueda por nombre de marca no es una verificación de
inventario.** El paquete se llama `marketly`, no `binomo` ni `quotex`. Un listado completo de
paquetes de terceros habría bastado; filtrar por marcas conocidas introdujo el punto ciego.

**Qué pasaba realmente.** `MainActivity` de TradeDraw quedó en primer plano tras la instalación, con
la app del broker minimizada. El veto de pantalla de la Ronda 20 (fail-closed) no podía confirmar el
activo visible y vetaba. Al reanudar `com.marketly.trading` al frente, la confirmación por
accesibilidad pasa y el motor sale del bloqueo.

**Telemetría verificada tras el restablecimiento** (`GET /status`, medición propia):

| Campo | Valor |
|---|---|
| `health.state` | **`READY`** |
| `health.reasons` | `[]` (vacío) |
| `balance` | **`39.271.736,32 COP`** (DEMO) |
| `risk.observedStake` | `100000.0` |
| `risk.effectiveStake` | `100000.0` |
| `feed.payoutResolved` | `0.83` |
| `feed.payoutConfigured` | `0.85` (default; manda el resuelto) |
| `feed.connected` / `isFresh` | `true` / `true` |
| `feed.lastTickAgeMs` | `129` |
| App de broker al frente | `com.marketly.trading/.modules.trading.TradingActivity` |

El balance visible (`39.271.736,32`) coincide **exactamente** con `final_balance_validated` de
`loops/r23i_validation/window_06_clean/window_metadata.json`. Es la misma cuenta DEMO que cerró W06,
sin aportes ni retiros entre ventanas — condición necesaria para que las ventanas sean acumulables
bajo el preregistro §3.4.

Con el balance legible, `observedStake` sube a `100000.0` y el stake efectivo deja de degradarse al
mínimo: **la configuración operativa de §2 es ahora efectiva**, no sólo configurada.

**Estado del modo al momento de esta enmienda:** `mode = DISABLED`, puesto en seguro preventivo. El
submodo se conserva en `YOLO`. Reanudar la operativa es una acción explícita (`POST /mode?name=AUTONOMOUS`),
no ocurre sola.

### 3.3 Requisito operativo para medir ventanas

Para que una ventana R23J sea medible deben cumplirse **las tres** condiciones a la vez:

1. `health.state == READY` con `reasons == []`.
2. `com.marketly.trading` en primer plano (no basta con que esté instalada y minimizada).
3. `mode == AUTONOMOUS` con `submode == YOLO`.

Si la app del broker se minimiza durante la captura, el veto vuelve a activarse y la ventana se
llena de vetos `SCREEN_FEED_MISMATCH` en lugar de trades. Es un modo de fallo silencioso que produce
una ventana vacía con apariencia de normalidad: conviene vigilar `risk.vetoActivoDesajustado` y
`health.state` durante la captura, no sólo al inicio.

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

### 4.1 Rebuild post-Fase B (build vigente)

Tras los commits B1 (`47a34f4`) y B2/B3 (`8cdb61f`), se recompiló e instaló desde el commit limpio:

```
./gradlew.bat assembleDebug
  -> EXIT=0  BUILD SUCCESSFUL in 20s

BuildConfig.java generado
  -> GIT_SHA = "8cdb61f"

adb -s UGU8KFUKM7E6CE8D install -r app/build/outputs/apk/debug/app-debug.apk
  -> Performing Streamed Install / Success
```

Verificación en `/status` del binario instalado:

| Criterio | Exigido | Observado | Veredicto |
|---|---|---|---|
| `gitSha` | `8cdb61f` | `8cdb61f` | **OK** |
| `health.state` | `READY` | `READY` (`reasons: []`) | **OK** |
| `isDemoAccount` | `true` | `true` | **OK** |

Tests del commit `8cdb61f`: **479** (468 + 11 de `AdaptiveBlockParityTest`), 0 fallos, 2 skipped.


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

## 6. ESTADO PARA MEDIR VENTANAS

El bloqueo de la primera emisión está **resuelto**. No queda ningún impedimento técnico para capturar
ventanas:

```
health.state = READY   reasons = []   balance legible   app de broker al frente
```

Antes de iniciar cada captura, confirmar los tres requisitos de §3.3. El único riesgo operativo real
es que la app del broker pierda el primer plano a mitad de ventana: el veto de pantalla volvería a
activarse y la ventana se llenaría de vetos en vez de trades, produciendo un artefacto vacío con
apariencia de normalidad. Vigilar `health.state` y `risk.vetoActivoDesajustado` **durante** la
captura, no sólo al arrancar.

`mode` está en `DISABLED` como seguro preventivo. Reactivar es explícito:

```
curl -s "http://localhost:8080/mode?name=AUTONOMOUS"
curl -s "http://localhost:8080/submode?name=YOLO"
```

**Detalle que se pierde al reinstalar:** el reinicio del servicio devuelve el submode a
`CONSERVATIVE` (su valor por defecto), aunque el manifiesto fije `YOLO`. Verificado en esta
instalación: tras arrancar el build `8cdb61f` el `/status` reportaba `submode = CONSERVATIVE` y hubo
que reenviar el `POST /submode?name=YOLO`. Quien arranque una ventana debe comprobar el submode
**después** de instalar, no confiar en el que tenía la sesión anterior.

Recordatorio del preregistro: una ventana con menos de 10 pares formales se declara **`NO
CONCLUYENTE`**, por bien que haya operado. El PASS de paridad exige `pares_formales >= 10` **y**
`tasa_match >= 90%`.
