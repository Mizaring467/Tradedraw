# R28-B — AUDITORÍA DE AUTONOMÍA: BLOQUEO POR AISLAMIENTO NO DEMOSTRABLE

**Fecha:** 2026-11-06 · **Rama:** `r10-night` · **HEAD:** `c934835` (sin mover)
**Agente:** OpenCode · **Dispositivo:** `duchamp` / Android 16 / SDK 36
**Modo durante toda la ronda:** DISABLED. **AUTONOMOUS NO se activó.**

---

## VEREDICTO

> **Pregunta:** ¿TradeDraw puede operar autónomamente en DEMO, desde la recepción de datos hasta la
> finalización de una orden simulada?
>
> **Respuesta: NO, y el aislamiento exigido no puede demostrarse.**
>
> TradeDraw **sí genera decisiones de forma autónoma** (eso está probado y no se discute). Lo que
> **no puede** hacer, con el código actual, es **completar una orden simulada**, porque no existe
> ningún simulador de órdenes. Y **no se puede activar AUTONOMOUS** porque su único mecanismo de
> ejecución son gestos táctiles reales sobre la pantalla del dispositivo, que es exactamente el
> canal que el encargo prohíbe conectar a un broker.

**Conforme a la instrucción del encargo: "Si el aislamiento no puede demostrarse, no activar
AUTONOMOUS: documentar el bloqueo y corregir primero la arquitectura de pruebas."**

---

## 1. Los cuatro requisitos de aislamiento, uno por uno

El encargo exige demostrar cuatro cosas **antes** de habilitar AUTONOMOUS. Ninguna se cumple.

### 1.1 "No existe conexión operativa con un broker real" — **NO DEMOSTRADO**

| Evidencia | Resultado |
| :--- | :--- |
| `TradeDraw_WSConfig.xml` en el dispositivo | `ws_endpoint_url = wss://as.binomo.com` — **infraestructura real de Binomo** |
| `ws_auth_token` | **PRESENTE, 36 caracteres** |
| `ws_cookie_header`, `ws_device_id` | Presentes |
| `BinomoWebSocketClient.kt:25` | Valor por defecto: `wss://ws.binomo.com/` — el broker real |

La conexión de **datos** apunta a Binomo con una sesión autenticada. No es un fixture: es el feed
real del broker. Aunque el feed sea solo de precios, la conexión operativa al broker **existe**.

### 1.2 "Las órdenes se procesan exclusivamente mediante mocks, fixtures o un simulador interno" — **FALSO**

**No existe simulador de órdenes en el código de producción.** Verificado:

```
grep -rn "interface .*Broker\|interface .*Executor\|interface .*OrderGateway" app/src/main/java/
→ NINGUNA abstracción de broker/ejecución
```

Cadena de ejecución real (`TradingEngine.kt:2551-2566`):

```kotlin
AutoTradeAccessibilityService.performClick(x, y, decisionId = ..., tradeId = ...) { success, error ->
    if (!success) triggerManualFallback(finalAction, error ?: "Auto-clic headless falló en 300ms")
    ...
}
```

y `performClick` termina en `AutoTradeAccessibilityService.kt:576`:

```kotlin
dispatchGesture(gesture, object : GestureResultCallback() { ... })
```

**`dispatchGesture` son toques reales en la pantalla del dispositivo.** Si Binomo está en primer
plano, esos toques pulsan los botones de compra/venta de una cuenta. No hay ninguna capa
sustituible: el motor llama directamente al servicio de accesibilidad, sin punto de inyección.

### 1.3 "No se utilizan credenciales reales ni endpoints de ejecución real" — **FALSO**

Las credenciales del punto 1.1 son reales (token de sesión de 36 caracteres) y el endpoint es
`as.binomo.com`. No son fixtures.

### 1.4 "Los resultados no modifican ninguna cuenta ni saldo real" — **NO GARANTIZABLE**

El aislamiento de saldo depende de un **flag que la app lee del broker**, no de un sandbox:

- `AutoTradeAccessibilityService.isDemoAccount` se determina leyendo la UI.
- La cuenta demo de Binomo es una cuenta **del broker**, servida por su infraestructura real.
- En los datos actuales el saldo observado es `300000.0` con `isDemoAccount=true`.

Es decir: "DEMO" aquí significa **"el broker dice que es demo"**, no **"no hay broker"**. Si el flag
se leyera mal, o si el usuario cambiara a la cuenta real en la app del broker, el mismo código
pulsaría los mismos botones sobre la cuenta real sin ningún cambio de configuración. **Eso no es
aislamiento: es confianza en una etiqueta.**

---

## 2. Por qué el modo SHADOW no resuelve el problema

`AutoTradeMode.SHADOW` (R21, Frente E) es lo más parecido a un entorno aislado que existe. Su
contrato está bien diseñado:

> *"todas las defensas y cálculos corren exactamente igual, pero NUNCA se despacha un tap al broker
> ni se altera el saldo ni el W/L real"* — `TradingEngine.kt:2490-2492`

Verificado en código: el bloque `if (mode == AutoTradeMode.SHADOW)` en `TradingEngine.kt:2493`
registra el evento, escribe el journal y hace `return` **antes** de cualquier tap (`:2521`).

### Pero SHADOW no puede completar un ciclo de orden

`awk` sobre el bloque SHADOW (`2493-2522`) confirma que **no** llama a:

- `recordTradeSent` → no se crea el estado de orden
- `hasPendingTrade` → nunca pasa a `true`
- `readCurrentBalance` → no hay balance base
- Cualquier ruta de liquidación

**Consecuencia:** en SHADOW una señal produce una **decisión registrada**, y ahí termina. No hay
orden, no hay pendiente, no hay respuesta, no hay liquidación. Es exactamente la distinción que el
encargo pide no confundir: *"No confundir una señal generada con una operación completada."*

Esto **no es un defecto de SHADOW**: es su diseño. SHADOW existe para medir decisiones, no para
simular órdenes. El problema es que **no existe el segundo componente** — un simulador de órdenes —
que el encargo da por supuesto.

### El test que lo confirma

`ShadowModeTest.kt` verifica como contrato obligatorio (`:18-20`):

> *"3. NO marca una orden real como ENVIADA (orderState permanece NINGUNA).
> 4. NO altera hasPendingTrade (permanece false)."*

Los tests de SHADOW **afirman justamente que el ciclo de orden no ocurre**. No son evidencia de
simulación de órdenes: son evidencia de su ausencia.

---

## 3. Lo que sí está demostrado (sin inflarlo)

| Propiedad | Evidencia | Estado |
| :--- | :--- | :--- |
| El motor genera decisiones sin intervención manual | 573 tests JVM · `ShadowModeTest` · `TradingEngineTest` | **VERIFICADO en JVM** |
| Las estrategias se evalúan y aplican vetos | 573 tests · suites de vetos y confluencias | **VERIFICADO en JVM** |
| El gestor de riesgo calcula stake/cooldown/martingala | `RiskManagerTest` | **VERIFICADO en JVM** |
| El journal registra decisiones con `decision_id` | `ShadowModeTest` (escritura real a fichero temporal) | **VERIFICADO en JVM** |
| SHADOW no despacha taps | Código `:2493-2521` + contrato testeado | **VERIFICADO** |

**Nada de esto equivale a "operar autónomamente de principio a fin".** Cubren la mitad del ciclo: la
decisión. La mitad de la orden no existe fuera del dispositivo real.

---

## 4. Qué haría falta para poder responder SÍ

Ordenado por dependencia. Sin esto, R28-B no puede aprobarse.

### 4.1 Abstracción del despacho (prerrequisito de todo lo demás)

Hoy: `TradingEngine` → `AutoTradeAccessibilityService.performClick` → `dispatchGesture`.

Debería ser: `TradingEngine` → `OrderGateway` (interfaz) → `{AccessibilityOrderGateway, SimulatedOrderGateway}`.

Sin este punto de inyección **no hay forma** de ejecutar el motor sin tocar la pantalla. Es también
la única manera de que el ciclo de orden se pueda **probar**, en lugar de solo razonarse.

### 4.2 `SimulatedOrderGateway` con ciclo completo

Debe poblar el mismo estado que hoy pobla el camino real, para que el motor recorra su lógica
completa: `recordTradeSent`, `hasPendingTrade=true`, respuesta de la orden, temporizador de
expiración, y liquidación (`resolvePendingTradeWin/Loss` / `settleTradeOnce`).

### 4.3 Fuente de datos inyectable

`wsUrl` ya es configurable (`ws_endpoint_url`), lo que es un buen punto de partida. Falta un
generador de ticks determinista que sustituya al feed durante la prueba, sin abrir socket al broker.

### 4.4 Separación explícita "entorno" vs "flag del broker"

`isDemoAccount` hoy se lee del broker. Para pruebas, el entorno debe ser una propiedad del proceso
(`TEST_SIMULATED`), no algo que dependa de la UI de una app de terceros.

### 4.5 Alcance del cambio

Es una refactorización **arquitectónica**, no un parche. Toca el corazón del motor y por tanto
requiere su propia ronda con preregistro, y validación de que los 573 tests siguen verdes.

---

## 5. Por qué NO se intentó la vía alternativa

Existe una tentación obvia: activar AUTONOMOUS con Binomo abierto en su cuenta demo, dejar que
pulse botones demo y observar el ciclo real. **Se descartó deliberadamente**, por tres razones:

1. **El encargo lo prohíbe explícitamente:** *"No se autoriza operar en REAL ni conectar el flujo de
   ejecución a un broker real."* Una cuenta demo de Binomo es servida por la infraestructura real
   del broker (`wss://as.binomo.com`), con credenciales reales. No es un entorno aislado.
2. **No sería reproducible ni determinista:** dependería de latencia de red, de la UI de un tercero y
   de la hora del día. El encargo pide "datos de prueba válidos o un flujo DEMO controlado".
3. **El aislamiento no sería demostrable, solo afirmable.** Activar taps y luego declarar "era demo"
   es precisamente confiar en la etiqueta, no demostrar el aislamiento.

Activarlo habría producido capturas y logs, y un veredicto falso.

---

## 6. Lo que NO se hizo

| Acción | Motivo |
| :--- | :--- |
| Activar AUTONOMOUS | Aislamiento no demostrable |
| Pulsar botones del broker | Prohibido |
| Conectar el flujo de ejecución al broker | Prohibido |
| Abrir Binomo | Evitado; no se interactuó con ninguna app ajena |
| Instalar APK | No autorizado en esta ronda |
| Cambiar estrategia/señales/parámetros/riesgo | Prohibido y no necesario |
| Modificar código | Este bloqueo es arquitectónico; requiere decisión, no un parche silencioso |

**Cambios de código en esta ronda: NINGUNO.** Archivos tocados: solo este informe.

---

## 7. Estado del entorno al cierre

| Señal | Valor |
| :--- | :--- |
| HEAD | `c934835` (sin mover, sin commits) |
| Batería | 49 % |
| Modo de la app | No se arrancó; el autoinforme previo era `DISABLED` |
| `AUTONOMOUS` | **Nunca activado** |
| Órdenes | 0 |
| Taps | 0 |
| Cuentas o saldos modificados | Ninguno |

---

## 8. Recomendación

**Recomiendo aprobar el bloqueo y abrir una ronda de arquitectura** (`R29-A`) centrada en el punto
4.1: introducir `OrderGateway` como interfaz, con la implementación de accesibilidad como una de
dos, y `SimulatedOrderGateway` como la otra. Ese cambio:

- Hace el ciclo de orden **testeable** por primera vez.
- Permite responder SÍ o NO a la pregunta de R28-B con evidencia.
- **No** altera estrategias, señales, parámetros ni gestión de riesgo.
- Deja el camino de producción intacto (la implementación de accesibilidad sigue siendo la activa
  fuera de pruebas).

Sin ese cambio, cualquier futura petición de "demostrar autonomía de punta a punta" volverá a
chocar con el mismo muro.

---

## 9. Respuesta directa a la pregunta del encargo

> ¿TradeDraw puede operar autónomamente en DEMO, desde la recepción de datos hasta la finalización
> de una orden simulada?

**NO — no con la arquitectura actual, y no puede demostrarse que sea seguro intentarlo.**

- Recepción de datos: funciona, pero contra el broker **real** con credenciales **reales**.
- Decisión autónoma: funciona y está probada (SHADOW, 573 tests).
- Orden simulada: **no existe**. SHADOW se detiene en la decisión por diseño, y el camino que sí
  ejecuta órdenes usa gestos reales sobre la pantalla.
- Ciclo completo con estados, respuestas y liquidación: **inalcanzable** sin un gateway simulado.

El bloqueo no es una limitación de herramientas ni de permisos: es **arquitectura de pruebas
ausente**. Corregirla es el siguiente paso lógico, y es una decisión de producto y de diseño, no algo
que deba resolverse improvisando en una ronda de verificación.
