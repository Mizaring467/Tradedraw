# Preregistro R16-A — cierre de los receptores de comando exportados

**Fecha de redacción:** `2026-10-14 13:30` (antes de compilar el parche definitivo).
**Origen:** hallazgo colateral de R15-A, elevado a tarea por decisión del usuario
(«A) cerrar los receptores exportados»).
**Árbol de partida:** `afa296c` (`HEAD == origin/main`, árbol limpio).

---

## 1. Qué se corrige y por qué

R15-A identificó y **reprodujo** dos receptores de broadcast dinámicos registrados con
`RECEIVER_EXPORTED`:

| receptor | archivo | acciones | consecuencia |
| :--- | :--- | :--- | :--- |
| `overlayCommandReceiver` | `OverlayService.kt` | `AUTO`, `YOLO` | **rearma el bot a `AUTONOMOUS`** |
| `commandReceiver` | `AutoTradeAccessibilityService.kt` | `TAP`, `SWIPE`, `BACK` | **ejecuta taps y gestos reales** |

Ambos son alcanzables por cualquier app del dispositivo. El segundo esquiva el modo, los vetos y
`RiskManager` por completo: un bot en `DISABLED` **no estaba protegido** frente a taps inyectados.

---

## 2. Hallazgo que cambia la solución (corrección de la corrección)

**El dispositivo de pruebas es API 31** (`ro.build.version.sdk = 31`, Android 12).
**`RECEIVER_NOT_EXPORTED` existe únicamente desde API 33.**

Por tanto, la forma obvia de arreglarlo —que es la que R15-A recomendó— habría sido:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
} else {
    registerReceiver(r, f)          // <-- en API 31 esto queda EXPORTADO
}
```

Es decir: **la rama `else` habría dejado el receptor exportado justo en el teléfono a proteger.**
El parche habría sido un **no-op silencioso** que además se habría declarado como arreglado. Se
documenta porque es el error más peligroso posible en esta ronda.

### 2.1 Solución adoptada

Sobrecarga de **4 argumentos** de `registerReceiver(receiver, filter, broadcastPermission, handler)`,
que existe **desde API 1** y funciona en todas las versiones:

```kotlin
registerReceiver(
    receiver, filter,
    "com.example.tradedraw.permission.INTERNAL_COMMAND",   // permiso exigido al EMISOR
    null
)
```

Y en el manifest, permiso de **firma**:

```xml
<permission
    android:name="com.example.tradedraw.permission.INTERNAL_COMMAND"
    android:protectionLevel="signature" />
```

Con esto, **sólo una app firmada con la misma clave puede emitir esos broadcasts**. En un
dispositivo con builds de depuración eso significa: la propia app de depuración y `adb shell`
(UID 2000, que tiene el permiso concedido por ser shell). **Ninguna app de terceros.**

---

## 3. Hipótesis y criterio de falsación

| # | hipótesis | criterio de falsación |
| :--- | :--- | :--- |
| **H1** | El parche cierra el vector: `adb shell am broadcast -a com.example.tradedraw.CMD --es action AUTO` **deja de rearmar** el bot | si el modo pasa a `AUTONOMOUS`, el parche **FALLA** |
| **H2** | El parche cierra el vector de taps: `am broadcast -a com.example.tradedraw.TAP --ef x 249 --ef y 1340` **deja de ejecutar el tap** | si se registra un tap real, el parche **FALLA** |
| **H3** | El parche NO rompe ninguna funcionalidad interna: los receptores siguen registrados y sin error | si aparece `Error registrando ...Receiver` repetido en cada arranque, el parche **FALLA** |
| **H4** | El parche no altera ninguna decisión de trading | cualquier cambio en `master_traders_skill`, umbrales, ventanas o dimensionamiento **FALSA** la ronda |

**Nota de honestidad sobre H1/H2, fijada antes de medir:** `adb shell` corre como UID 2000 y **sí**
tiene permiso para emitir broadcasts protegidos por permiso de firma (es el comportamiento
documentado: `shell` recibe los permisos declarados por las apps). Por tanto **es posible que
`adb` siga pudiendo rearmar el bot después del parche**, y eso **no** significa que el parche falle:
el vector que importa es *otra app instalada*, no el operador con ADB. El criterio real de H1/H2 es
que **la emisión deja de ser posible sin el permiso**, lo cual se comprueba por **lectura del
registro** (permiso exigido) y, si `adb` sigue pudiendo, se declara explícitamente que
`adb`/`shell` queda como emisor privilegiado por diseño de Android.

## 4. Umbral de decisión

- **H1/H2 se consideran SOSTENIDAS** si el registro exige el permiso de firma y no queda ninguna
  rama de código que registre sin permiso.
- **FALSADAS** si tras el parche sigue existiendo una ruta de registro sin permiso en el código.
- **NO CONCLUYENTE** si el dispositivo no permite determinar si el emisor fue rechazado
  (p. ej. `Broadcast completed` sin información de entrega).
- **Prohibido** declarar el vector cerrado basándose sólo en que «el código parece correcto»: hace
  falta **una prueba de emisión** en el dispositivo.

## 5. Alcance y límites

- **NO** se toca ninguna lógica de trading. Cero cambios en umbrales, ventanas, vetos o
  `RiskManager`.
- **NO** se cambia quién puede cambiar el modo desde la UI ni desde el chat: esas rutas exigen
  toque humano y siguen intactas.
- **NO** se aborda el resto de la superficie de seguridad (ver §6).
- **SÍ** se conserva la capacidad de emitir estos comandos por ADB para el tooling del proyecto,
  si Android lo permite (ver §3).

## 6. Fuera de alcance, declarado (no se arregla aquí)

| punto | motivo |
| :--- | :--- |
| `BinomoAuthActivity` exportada sin filtro de intent | Actividad lanzable por terceros. Captura el `authtoken` de Binomo en un `WebView` y lo guarda en `SharedPreferences`. **Merece su propia ronda**: puede ser explotable como fuga de credenciales, y tocarlo exige verificar que la app sigue abriéndola (se lanza desde `MainActivity` y desde el HUD). |
| Modo no persistido (`TradingEngine.mode` es sólo memoria) | No es un vector de rearme; es un problema de auditabilidad. |
| Buffer de eventos de 200 compartido con `ASSET` | R15-A perdió casi toda la evidencia de modo por esto. Mejora de observabilidad, no de seguridad. |
| `/mode` del bridge HTTP acepta `AUTONOMOUS` | Loopback + `adb forward`. Es el mismo rearme por otra puerta, pero exige acceso local al puerto. |
