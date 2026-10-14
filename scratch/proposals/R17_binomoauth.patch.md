# Propuesta R17 — `BinomoAuthActivity`: cerrar exportación y redactar el token en logs

**ESTADO: PROPUESTA. NO APLICADA. NO COMMITEADA AL CÓDIGO DE PRODUCCIÓN.**
Fichero fuera del árbol de la app (`scratch/proposals/`). Ningún script lo aplica solo.

**Base:** `8950947` · **Autor:** agente R17 · **Fecha:** `2026-10-14 14:30`

---

## 1. Cambios propuestos (4, ordenados por relación seguridad/riesgo)

### C1 — Token en logs (PRIORIDAD ALTA, riesgo ~0)

**Por qué primero:** es el hallazgo con impacto real y el arreglo **no puede romper nada** — sólo
cambia texto de log.

`app/src/main/java/com/example/tradedraw/BinomoAuthActivity.kt`

```diff
@@ línea 255-258
                 if (extracted.isNotEmpty()) {
-                    Log.d(TAG, "Token detectado en URL: $extracted")
+                    // R17: NO imprimir el token. Longitud y origen bastan para depurar.
+                    Log.d(TAG, "Token detectado en URL (longitud=${extracted.length})")
                     mainHandler.post { etAuthToken.setText(extracted) }
                 }
```

```diff
@@ línea 320-324
             if (clean.isNotEmpty() && clean != "null" && clean != "undefined") {
-                Log.d(TAG, "Token en LocalStorage: $clean")
+                Log.d(TAG, "Token en LocalStorage (longitud=${clean.length})")
                 mainHandler.post { etAuthToken.setText(clean) }
             }
```

```diff
@@ línea 352
-        Log.d(TAG, "Sesión vinculada: token=$token, devId=$devId, cookiesLen=${cookies.length}")
+        // R17: nunca el valor del token ni el device_id. Sólo presencia y tamaños.
+        Log.d(TAG, "Sesión vinculada: token=${if (token.isEmpty()) "NO" else "SI"}, " +
+                   "devId=${if (devId.isEmpty()) "NO" else "SI"}, cookiesLen=${cookies.length}")
```

**Riesgo de romper algo: NINGUNO.** No cambia ninguna variable, ninguna llamada, ningún flujo.
Sólo el texto que se escribe en el log.

### C2 — `exported="false"` (riesgo BAJO, requiere prueba manual)

`app/src/main/AndroidManifest.xml`

```diff
@@ línea 46-51
         <activity
             android:name=".BinomoAuthActivity"
-            android:exported="true"
+            android:exported="false"
             android:label="Vinculación Binomo"
             android:theme="@style/Theme.TradeDraw"
             android:windowSoftInputMode="adjustResize" />
```

**Justificación medida:** la actividad **no tiene `<intent-filter>`**
(`AndroidManifest.xml:46-51`), así que `exported="true"` **no aporta nada** funcionalmente. Se
lanza **siempre** con `Intent` **explícito** desde el mismo paquete:

| llamador | línea |
| :--- | :--- |
| `MainActivity.kt` | `:69` |
| `OverlayService.kt` | `:1874` |

Un `Intent` explícito desde el mismo paquete **ignora** `exported` — sigue funcionando. Esto es
comportamiento documentado de Android, **pero se verifica en §3**, no se supone.

**Riesgo declarado de romper:** si algún flujo lanzara la actividad desde fuera del paquete (o vía
`am start` desde ADB, que corre como shell UID 2000), dejaría de funcionar. **No se encontró
ninguno** en el repo. El único `am start` del proyecto es sobre `MainActivity`.

### C3 — Endurecer el WebView (riesgo MEDIO, requiere prueba manual)

`BinomoAuthActivity.kt`, dentro de `setupMainWebView()`. Añadir al `webViewClient`:

```diff
+            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
+                // R17: sólo se permite navegar dentro de binomo.com. Cualquier otro host se
+                // abre fuera del WebView (o se bloquea), para que una redirección no deje
+                // el bridge JS disponible en un origen ajeno.
+                val host = request?.url?.host ?: return true
+                return if (host.endsWith("binomo.com")) {
+                    false
+                } else {
+                    Log.w(TAG, "Navegación bloqueada fuera de binomo.com: host=$host")
+                    true
+                }
+            }
```

Y desactivar los popups automáticos:

```diff
@@ línea 175
-        settings.javaScriptCanOpenWindowsAutomatically = true
+        // R17: el popup de OAuth se abre por gesto del usuario; no hace falta automático.
+        settings.javaScriptCanOpenWindowsAutomatically = false
```

**Riesgo de romper el login: MEDIO.** El flujo OAuth de Binomo puede usar **redirecciones a
dominios de terceros** legítimos (proveedores de identidad, CDNs, captcha). Si el login usa un
dominio fuera de `binomo.com`, el bloqueo lo rompe. **Por eso C3 exige prueba manual completa y
está marcado como el cambio más arriesgado.** Si el login falla, la lista blanca hay que ampliarla
— no eliminar el control.

**Recomendación:** aplicar C1 y C2 primero, verificar el login, y **sólo entonces** valorar C3 con
la lista de dominios reales observada en el flujo.

### C4 — Token efímero en el bridge (riesgo MEDIO, cambio de mayor alcance)

`TradeDrawHttpBridge.kt`. Generar un token aleatorio en `start()` y exigirlo en
`/mode`, `/strategy`, `/trade` y `/reset_stats`:

```diff
+    // R17: token efímero por sesión del servicio. Se pierde al reiniciar; los scripts deben
+    // leerlo de /status (solo loopback) o reiniciar el forward. Cierra la puerta a que otra
+    // app del dispositivo rearme el motor por 127.0.0.1:8080.
+    private val controlToken: String = java.util.UUID.randomUUID().toString().take(16)
```

y en `handleClient`, antes del `when`:

```diff
+                    if (path in setOf("/mode", "/strategy", "/trade", "/reset_stats", "/reset_sl_tp")) {
+                        val k = params["k"] ?: ""
+                        if (k != controlToken) {
+                            sendJsonResponse(output, 403, """{"success":false,"error":"token requerido"}""")
+                            return@let   // ajustar al flujo real de handleClient
+                        }
+                    }
```

**Riesgo: MEDIO-ALTO.** Rompe **todos** los scripts actuales (`fast_trader_cli.py`,
`test_master_trader_suite.py`, y cualquier `curl` manual). Exige actualizarlos. **Se propone como
R20, no ahora**, y idealmente combinado con `LocalSocket` en lugar de TCP loopback.

---

## 2. Qué NO cubre esta propuesta

- **No** cierra el acceso desde `root`/`adb shell`: `/mode` sigue alcanzable con `adb forward`
  salvo que se aplique C4.
- **No** cifra las `SharedPreferences` (el token sigue en claro en disco).
- **No** cambia `allowBackup="true"` ni `usesCleartextTraffic="true"` (ver `R17_REPORTE.md` §4).
- **No** toca los receptores de comando ya cerrados en `af71997`.
- **No** modifica ninguna lógica de trading ni la gobernanza `master_traders_skill`.

---

## 3. Protocolo de prueba MANUAL

**Lo ejecuta el usuario, con el teléfono delante. El agente NO aplica ni prueba estos cambios.**

### Preparación

```powershell
cd C:\Users\heidy\Tradedraw
# Aplicar SOLO C1 + C2 (los seguros) y compilar
.\gradlew.bat cleanTest testDebugUnitTest assembleDebug --no-daemon --offline --no-configuration-cache --max-workers=1
# Verificar la suite: 314 tests, 0 fallos, 2 omitidos (línea base)
adb -s UGU8KFUKM7E6CE8D install -r app\build\outputs\apk\debug\app-debug.apk
```

### P1 — El login interno SIGUE funcionando (crítico para C2)

1. Abrir la app.
2. Pulsar el botón que abre la vinculación de Binomo.
3. **Verificar:** la pantalla «Vinculación Binomo» se abre y carga `binomo.com/es/trading`.
4. **Si NO se abre → C2 ROMPE el login → revertir el commit entero.**

### P2 — La actividad ya NO es lanzable desde fuera (verifica C2)

```powershell
# UID 2000 (shell): debe FALLAR con SecurityException / Permission Denial
adb -s UGU8KFUKM7E6CE8D shell "am start -n com.example.tradedraw/.BinomoAuthActivity"
# Esperado: "java.lang.SecurityException: Permission Denial: starting Intent ... not exported"
```

**Contraste (control positivo):** `am start -n com.example.tradedraw/.MainActivity` **sí** debe
funcionar (sigue `exported="true"` por ser launcher).

### P3 — El token ya NO aparece en el log (verifica C1)

1. Iniciar sesión en Binomo dentro de la actividad (flujo real).
2. Al capturar el token:

```powershell
adb -s UGU8KFUKM7E6CE8D shell "logcat -d | grep BinomoAuthActivity"
```

**Verificar:** aparecen líneas con `longitud=`, `token=SI`, `devId=SI` — y **ninguna** con el valor
del token. Comparar con la versión anterior si se guardó un log previo.

### P4 — El flujo de vinculación sigue guardando el token

1. Tras iniciar sesión, pulsar «Vincular sesión».
2. **Verificar** que aparece el Toast de éxito y que el feed de precios se activa.
3. **NO** ejecutar `/trade` ni taps.

### P5 — (Sólo si se decide aplicar C3) Lista blanca de dominios

1. Con C3 aplicado, repetir P1 y completar el login **con credenciales reales**.
2. Si falla, revisar en el log los `Navegación bloqueada fuera de binomo.com: host=<X>` y **añadir
   los hosts legítimos observados** a la lista.
3. **Nunca** eliminar el control para «que funcione»: ampliar la lista.

### Cierre obligatorio

```powershell
# Dejar el bot en DISABLED y verificar
curl.exe -s "http://127.0.0.1:8080/mode?name=DISABLED"
curl.exe -s "http://127.0.0.1:8080/status"
# Verificar: mode=DISABLED, orderState=NINGUNA, hasPendingTrade=false, tapsDespachados=0
# Balance debe seguir en 37.974.735,36
```

---

## 4. Compromiso de reversión

| si falla | acción |
| :--- | :--- |
| P1 (el login no abre) | **revertir C2** inmediatamente, commit de reversión |
| P4 (no guarda el token) | **revertir C1 y C2** y reportar |
| P5 (login OAuth roto) | **revertir C3**; C1 y C2 pueden quedarse |
| Cualquier discrepancia de balance | **parar todo** y reportar como INCIDENTE |

---

## 5. Orden de aplicación recomendado

1. **C1** (logs) — sin riesgo, alto valor. Puede aplicarse ya.
2. **C2** (`exported=false`) — tras P1/P2.
3. **C3** (WebView) — sólo con sesión supervisada y prueba del login completo.
4. **C4** (token del bridge) — **R20**, junto con la migración de los scripts.
