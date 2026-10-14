# Ronda 17 — Informe: auditoría de `BinomoAuthActivity` y superficie exportada

**Tarea:** auditar la única Activity con `exported="true"` sin filtro que maneja credenciales, y la
superficie de componentes expuestos de la app.
**Alcance:** **SOLO AUDITORÍA**. Cero cambios en código de producción.
**Árbol de partida:** `8950947` (`HEAD == origin/main`).
**Inicio:** `2026-10-14 14:18` · **Fin:** `2026-10-14 14:34` (16 min).
**Batería:** inicio **75 %** (cargando) · final **76 %**.

> **Ningún valor de token aparece en este informe.** Donde el código imprime un token, se cita el
> `archivo:línea` y se describe el canal, con el valor redactado como `***`.

---

## 0. Resumen

1. **H-INTENT FALSADA.** La actividad **no lee ningún extra** de `getIntent()`. Cero coincidencias
   de `getIntent`, `intent.` o `Intent(` en todo el fichero: no hay ninguna vía de que datos de un
   intent alteren el flujo.
2. **H-LEAK SOSTENIDA — es el hallazgo real.** **Tres** `Log.d` escriben el token en claro en
   logcat: `BinomoAuthActivity.kt:256`, `:322` y `:352`. En un dispositivo con `logd` vivo (como
   ahora), cualquier proceso con `READ_LOGS` o `logcat` por ADB puede leer el `authtoken`.
3. **H-REDIRECT FALSADA.** La URL es la constante literal `"https://binomo.com/es/trading"`
   (`:161`). No hay `intent-filter`, no hay esquema, no hay parámetro.
4. **H-BRIDGE SOSTENIDA parcialmente.** La `@JavascriptInterface` expone 2 métodos
   (`onWsUrl`, `onTick`) que **sólo reciben** datos y no ejecutan acciones privilegiadas. Riesgo
   **BAJO**, no ALTO: `onWsUrl` escribe en prefs y reasigna la URL del WS, lo cual es un vector de
   redirección del feed de precios, no de ejecución.
5. **H-MODE SOSTENIDA.** `/mode` acepta `AUTONOMOUS` sin autenticación
   (`TradeDrawHttpBridge.kt:165-175`). **Sólo alcanzable por loopback** (`:41`, binding explícito a
   `getLoopbackAddress()`) y por tanto sólo por `adb forward` (shell o root). Riesgo **MEDIO**:
   requiere ADB, pero ADB ya puede rearmar por broadcast con `su`.
6. **Hallazgo adicional no previsto: `usesCleartextTraffic="true"`** (`AndroidManifest.xml:30`).

---

## 1. Tabla de superficie exportada

Recorrido de **todos** los componentes del `AndroidManifest.xml` (73 líneas, sin
`<provider>` ni `<receiver>` declarados).

| componente | exported | permiso | qué hace | riesgo | evidencia |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `MainActivity` | `true` | — | Launcher. Verifica permisos (overlay, accesibilidad, MediaProjection) y **lanza `OverlayService`** desde un `OnClickListener` | **BAJO** (necesita `exported=true` para ser launcher; no acepta extras; arrancar el servicio sólo pide permisos) | `AndroidManifest.xml:32-44`, `MainActivity.kt:53-79` |
| **`BinomoAuthActivity`** | **`true`** | **—** | **Captura y persiste el `authtoken` de Binomo**; WebView con JS, DOM storage y `@JavascriptInterface` | **MEDIO** — `exported` innecesario (todo lanzamiento es explícito interno) **+ fuga de token por logcat** | `AndroidManifest.xml:46-51`, `BinomoAuthActivity.kt:28-388` |
| `OverlayService` | `false` | — | Servicio maestro: HUD, captura, motor, bridge | **NINGUNO** por `exported` (correcto); arranque interno ya resuelto en R15-A con `su` | `AndroidManifest.xml:53-57` |
| `AutoTradeAccessibilityService` | `true` | `BIND_ACCESSIBILITY_SERVICE` | **Ejecuta taps reales** vía `dispatchGesture` | **NINGUNO** — `exported=true` **exigido** por el framework; el permiso de sistema lo protege | `AndroidManifest.xml:59-70` |
| `<provider>` | — | — | **No existe ninguno** | NINGUNO | manifest completo |
| `<receiver>` estático | — | — | **No existe ninguno** (los 2 receptores son dinámicos, cerrados en `af71997`) | NINGUNO | manifest completo |

**Conclusión de la tabla:** el **único** `exported="true"` **injustificado** es
`BinomoAuthActivity`. `MainActivity` lo necesita (launcher) y
`AutoTradeAccessibilityService` lo necesita (lo exige el framework para accesibilidad).

---

## 2. Hipótesis: veredicto con evidencia

### H-INTENT — **FALSADA**

```
grep "getIntent|intent\.|Intent\(" en BinomoAuthActivity.kt → 0 coincidencias
```

El fichero de 388 líneas **no menciona `intent` en ningún punto**. La actividad no lee extras, no
lee el `data` del intent, no comprueba la acción. Todo lo que hace en `onCreate` (`:109-156`) es
inflar la vista, leer prefs propias, cablear 6 botones y llamar a `setupMainWebView()` y
`loadTradingLoginUrl()`. **Un intent lanzado desde otra app no puede alterar el flujo**: lo único
que consigue es abrir la pantalla de login.

**Matiz, no exculpatorio:** `exported="true"` **sin filtro** significa que cualquier app puede
lanzar la actividad con un componente **explícito**. El efecto es abrir un WebView sobre
`binomo.com` en primer plano — una **interrupción de UI** (molesta, y con `taskAffinity` por
defecto puede colocar la actividad en la pila de la app llamante), pero **sin control de datos**.

### H-LEAK — **SOSTENIDA** (hallazgo principal)

Tres puntos escriben el token en claro en el log de Android:

| línea | instrucción | canal |
| :--- | :--- | :--- |
| **`BinomoAuthActivity.kt:256`** | `Log.d(TAG, "Token detectado en URL: $extracted")` | logcat, nivel `D`, tag `BinomoAuthActivity` |
| **`BinomoAuthActivity.kt:322`** | `Log.d(TAG, "Token en LocalStorage: $clean")` | logcat, nivel `D` |
| **`BinomoAuthActivity.kt:352`** | `Log.d(TAG, "Sesión vinculada: token=$token, devId=$devId, cookiesLen=…")` | logcat, nivel `D` — **imprime token Y device_id** |

Además, un cuarto punto escribe la **URL completa del WebSocket** (`:96`), que en protocolos de
broker suele incluir el token como parámetro de query. Se cita como **posible** fuga: no se
verificó que la URL contenga credenciales (haría falta capturarla, y eso implica abrir el login).

**Impacto medido del canal logcat ahora mismo:**

- `init.svc.logd = running` en este dispositivo (verificado en R16, y el buffer se usó para
  reconstruir el incidente de las 13:51).
- `logcat -d` **funciona sin root** por ADB: se ha usado repetidamente en R16/R17.
- En consecuencia: **cualquier sesión ADB lee el token de Binomo**, y en builds con
  `READ_LOGS` concedido a apps, también ellas. En Android 12 `READ_LOGS` es `signature|privileged`,
  así que el vector de app maliciosa es **limitado**; el vector real es **ADB / root / cualquier
  cosa con acceso físico al puerto de depuración**.

**Almacenamiento:** el token se persiste en `SharedPreferences` **sin cifrar**
(`TradeDraw_WSConfig`, `BinomoAuthActivity.kt:348` y `:374`), legible en un dispositivo rooteado o
vía backup. `android:allowBackup="true"` (`AndroidManifest.xml:24`) **con** reglas de backup
declaradas (`dataExtractionRules`, `fullBackupContent`), que no se auditaron en detalle: **NO
VERIFICADO** si excluyen `TradeDraw_WSConfig`.

**No verificado:** si el token viaja además en la URL del WS (`:96`) o en las cookies que se
guardan en `ws_cookie_header` (`:347`), que **incluyen `authtoken`** por construcción (`:337-348`
extrae el token **de** las cookies).

### H-REDIRECT — **FALSADA**

La URL es una **constante literal**:

```kotlin
// BinomoAuthActivity.kt:158-162
private fun loadTradingLoginUrl() {
    progressBar.visibility = View.VISIBLE
    webView.loadUrl("https://binomo.com/es/trading")   // ← literal, no parametrizable
}
```

No hay `intent-filter` (`AndroidManifest.xml:46-51`), así que no hay `VIEW`/`BROWSABLE` ni esquema
custom. No existe ningún `loadUrl` que reciba un valor externo: los 4 puntos que llaman a
`loadUrl` usan la misma constante.

**Nota (no es H-REDIRECT, pero se declara):** el `WebViewClient` **no restringe la navegación**, así
que un enlace dentro de la web de Binomo navega libremente. Eso es comportamiento normal de un
WebView y la navegación ocurre **dentro** del WebView, no en un intent externo. Riesgo **BAJO**.

**Nota 2:** el `onCreateWindow` (`:190-225`) crea un WebView de popup con las **mismas** settings
(JS, DOM storage) y le añade un `webViewClient` que llama a `inspectUrlAndCookies`.
`javaScriptCanOpenWindowsAutomatically = true` (`:175`) **sin** `setSupportMultipleWindows` +
gesto de usuario permite popups automáticos. Es un vector de **abuso de consentimiento** clásico
(popup no solicitado), no de ejecución de código. Riesgo **BAJO** declarado.

### H-BRIDGE — **SOSTENIDA (parcial)**, riesgo BAJO

Métodos expuestos a JavaScript del WebView (`BinomoAuthActivity.kt:93-106`):

| método | qué recibe | qué hace | riesgo |
| :--- | :--- | :--- | :--- |
| `onWsUrl(url)` | string del JS | escribe en prefs `ws_endpoint_url` y reasigna `wsClient.wsUrl` | **MEDIO-BAJO**: redirige el feed de precios a un servidor arbitrario |
| `onTick(rawJson)` | string del JS | `processIncomingMessage(rawJson)` | **BAJO**: inyecta ticks falsos en el motor |

**Riesgo compartido con H-REDIRECT:** el JS que se ejecuta es el de `binomo.com`. Si esa web
fuera comprometida (o el tráfico interceptado — ver **cleartext** abajo), podría redirigir el
WebSocket de datos y alimentar ticks falsos.

**Por qué BAJO y no ALTO:** `onTick` y `onWsUrl` **no** ejecutan taps, **no** cambian el modo, y
**no** otorgan acceso a `RiskManager` ni a `TradingEngine`. A lo sumo corrompen la visión de
precios. Y las decisiones de trading siguen pasando por los vetos y la confluencia.

**Hueco encontrado:** no hay `WebViewClient.shouldOverrideUrlLoading` que restrinja dominios, y
`bridge` se añade **antes** de cargar la URL (`:178` antes de `:155 loadTradingLoginUrl()`), de modo
que **cualquier** navegación dentro del WebView tiene el bridge disponible. No hay lista blanca.

### H-MODE — **SOSTENIDA**, riesgo MEDIO

```kotlin
// TradeDrawHttpBridge.kt:165-175
"/mode" -> {
    val modeStr = params["name"]?.uppercase() ?: params["mode"]?.uppercase() ?: ""
    val m = AutoTradeMode.valueOf(modeStr)
    overlayService.tradingEngine.mode = m        // ← sin autenticación
    ...
}
```

- **Sin autenticación, sin token, sin comprobación de origen.**
- **Alcance real:** el socket se enlaza **explícitamente a loopback**
  (`TradeDrawHttpBridge.kt:41-43`):
  ```kotlin
  val addr = java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port)
  serverSocket = ServerSocket()
  serverSocket?.bind(addr)
  ```
  `getLoopbackAddress()` devuelve `::1`/`127.0.0.1`. **No hay** `Socket(port)` sin bind (el patrón
  que R13 corrigió). `handleClient` (`:68`) **no** valida `getInetAddress()`: confía en el binding.
- **Quién puede llegar:** sólo un proceso **del propio dispositivo** que conecte a `127.0.0.1:8080`.
  Desde el PC hace falta `adb forward tcp:8080 tcp:8080` → **shell (UID 2000) o root**.
- **Otras apps del dispositivo:** en Android, un socket ligado a loopback **sí** es alcanzable por
  otras apps del mismo dispositivo (no hay aislamiento de red por app para loopback, salvo que se
  use un `LocalSocket` con SELinux o `NetworkSecurityConfig`). **Esto es un hallazgo relevante:
  cualquier app instalada puede hacer `GET /mode?name=AUTONOMOUS` a `127.0.0.1:8080`.**
  **NO VERIFICADO empíricamente** con una app de prueba: se declara por modelo de plataforma, y
  como tal queda en NO VERIFICADO.
- **`/trade` es peor** (`:137-148`): llama a `performClickAt(x, y)` → **tap real**. Mismo alcance.

**Propuesta (NO aplicada, ver §3):** token efímero por sesión generado en el arranque y exigido en
cabecera, o mover el control a `LocalSocket` con permiso de firma.

---

## 3. Parche propuesto (NO aplicado)

En [`scratch/proposals/R17_binomoauth.patch.md`](../../scratch/proposals/R17_binomoauth.patch.md).
Contiene: cambio mínimo, riesgo de romper el login, y **protocolo de prueba MANUAL**. **No se
aplica, no se commitea al código de producción.** El fichero queda fuera del árbol de la app.

---

## 4. Hallazgo adicional: `usesCleartextTraffic="true"`

`AndroidManifest.xml:30` declara `android:usesCleartextTraffic="true"`, lo que **permite HTTP sin
TLS** en toda la app (`targetSdk 34` lo bloquea por defecto; esta línea lo reabre).

**Por qué importa aquí y no en general:** la app carga `https://binomo.com` (correcto), pero la
`@JavascriptInterface` acepta `onWsUrl(url)` **con cualquier esquema**. Si la web de Binomo
cambiara su WS a `ws://` (o si un atacante en la misma red pudiera inyectar contenido — pero el
tráfico de la web es HTTPS, así que esto requiere compromiso del lado servidor o del certificado),
el feed llegaría en claro. **Riesgo bajo en la práctica dado que el origen es HTTPS**, pero la
bandera es innecesaria para esta app: se declara como recomendación de endurecimiento, no como
vulnerabilidad explotable. **NO VERIFICADO** si algo de la app depende de HTTP en claro;
desactivarla requiere probar el flujo completo (login, bridge, IA remota) antes.

---

## 5. Verificación

| comprobación | resultado |
| :--- | :--- |
| Hipótesis sin clasificar | **0** (las 5 con veredicto) |
| Tabla de componentes `exported` | **completa** (4 componentes + ausencia de providers/receivers) |
| Código de producción modificado | **NINGUNO** |
| Valores de token en el informe | **NINGUNO** (redactados `***`) |
| Parche fuera del árbol de producción | **SÍ** (`scratch/proposals/`) |
| Protocolo de prueba manual incluido | **SÍ** |
| Taps ejecutados / `/trade` | **0** |
| `master_traders_skill` intacta | **SÍ** (no se tocó código) |
| Balance | sin cambios (`37.974.735,36`) |
| `N` de operaciones supervisadas | **0** |
| Suite | **no ejecutada**: no hay cambio de código que validar (sólo `.md`) |

---

## 6. NO VERIFICADO

1. **Si `/mode` y `/trade` son alcanzables desde otra app del dispositivo.** Se declara por modelo
   de plataforma (loopback no está aislado por app), **no medido**. Requeriría compilar una app de
   prueba y desplegarla — fuera de alcance y con riesgo de ejecutar `/trade`.
2. **Si `ws_cookie_header` / `ws_auth_token` quedan fuera del backup.** `allowBackup="true"` con
   reglas no inspeccionadas en detalle.
3. **Si la URL del WebSocket (`:96`) contiene el token.** No se capturó (implicaría abrir el login).
4. **Si el login sigue funcionando con `exported="false"`.** Es el riesgo del parche propuesto; se
   comprueba en el protocolo manual, no aquí.
5. **`dataExtractionRules` / `backup_rules`**: existen (`AndroidManifest.xml:25-26`) pero su
   contenido no se auditó.
6. **Cifrado en reposo:** no se comprobó si `SharedPreferences` usa `EncryptedSharedPreferences`
   (a la vista del código, **no**).
7. **La `taskAffinity` por defecto** de `BinomoAuthActivity` no se declaró; su efecto sobre la pila
   de tareas de la app llamante no se probó.

---

## 7. Recomendaciones para R18/R20

1. **Redactar los 3 logs del token** (`:256`, `:322`, `:352`) — cambio de bajo riesgo, sin efecto
   funcional. **Prioridad alta.**
2. **`exported="false"` en `BinomoAuthActivity`** — requiere la prueba manual de §3.
3. **Considerar `allowBackup="false"`** o verificar que la exclusión cubre `TradeDraw_WSConfig`.
4. **Valorar `usesCleartextTraffic="false"`** tras probar los flujos.
5. **Token efímero en el bridge** para `/mode` y `/trade`, o `LocalSocket` con permiso de firma.
6. **Añadir `shouldOverrideUrlLoading`** con lista blanca de dominios y desactivar
   `javaScriptCanOpenWindowsAutomatically`.
7. **Nada de esto cambia decisiones de trading** ni la gobernanza `master_traders_skill`.

---

## 8. Estado al cierre

| campo | valor |
| :--- | :--- |
| `mode` | **`DISABLED`** |
| `orderState` | `NINGUNA` |
| `hasPendingTrade` | **`false`** |
| `tapsDespachados` | `0` |
| `balance` | `37.974.735,36` (idéntico a R13/R14/R15/R16 → **0 operaciones**) |
| Batería final | **76 %** (cargando) |
| Veredicto | **PASS-AUTOR** (verificación independiente pendiente: agy/hermes) |
