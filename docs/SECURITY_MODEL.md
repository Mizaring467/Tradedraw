# Modelo de seguridad — TradeDraw · R29-A

**Alcance:** superficie de seguridad REAL del sistema, verificada por lectura de código y manifiesto
(Rondas 1–3). No se ejecutó nada; los riesgos se documentan, no se corrigen (regla 15: cualquier
cambio aquí altera comportamiento runtime/backups).
**Fuentes:** [R2 inventario](../loops/r29a/ROUND_02_COMPONENT_INVENTORY.md) §2.1/§2.5/§2.6,
[R3 aislamiento](../loops/r29a/ROUND_03_ISOLATION_AUDIT.md) §2.2/§2.4,
[R4 arquitectura](../loops/r29a/ROUND_04_TARGET_ARCHITECTURE.md) §4.

---

## 1. Credenciales en reposo

- **Token de sesión Binomo en claro en SharedPreferences**: `ws_auth_token` (36 caracteres, capturado
  por `BinomoAuthActivity` vía WebView de login, `:385-386`) almacenado en `TradeDraw_WSConfig` sin
  cifrar. Sin EncryptedSharedPreferences (grep → 0, R3 §2.7). Se envía como query `authtoken=`,
  header y Cookie al WS (`BinomoWebSocketClient.kt:530-547`). Logs de auth sanitizados
  (`BinomoAuthActivity.kt:107`, test `BinomoAuthSanitizationTest`) — el valor nunca se imprime.
- **`allowBackup="true"`** (`AndroidManifest.xml:24`) + **`backup_rules.xml` vacías** (plantilla
  default con excludes comentados) + `data_extraction_rules` sin exclusiones relevantes → **el
  token puede salir en backups del dispositivo**. [VERIFICADO hoy — lectura de manifest y XML]
- **`usesCleartextTraffic="true"`** (`AndroidManifest.xml:30`) → tráfico HTTP en claro permitido
  para toda la app. [VERIFICADO hoy]
- **NO existe** cifrado, keystore ni ofuscación de secretos en el código de producción.

## 2. El bridge HTTP loopback: única frontera de red del bot

- Micro-servidor HTTP propio (sin framework) sobre `ServerSocket` **enlazado solo a loopback**
  `::1:8080` (`TradeDrawHttpBridge.kt:40-44`; R13/B1). **Sin autenticación HTTP**: el loopback es la
  única frontera. Cualquier proceso local del dispositivo puede llamarlo; acceso externo requiere
  `adb forward tcp:8080 tcp:8080` (lo hace `fast_trader_cli.py` y el supervisor `td_supervisor.sh`).
- Toda petición alimenta `noteBridgeHeartbeat()` (`bridge:86`) → caducidad de AUTONOMOUS (R26).
- **Exposición vía `adb forward`**: quien tenga ADB (cable/redirector) alcanza los 28 endpoints como
  si fuera local. El supervisor (Termux) y el CLI de operador son los clientes legítimos; el vector
  es un proceso ADB no autorizado.

## 3. Endpoints peligrosos (28 en total, `bridge:130-530`)

| Endpoint | Qué hace | Guard real | Riesgo |
| :--- | :--- | :--- | :--- |
| `GET /trade` (`:141-159`) | Tap BUY/SELL en coordenadas calibradas | **Solo** `puedeDespacharTap(isDemoAccount)` (`ATAS:532`) | **Sin cascada de 13 vetos, sin `canExecuteTrade`, sin consulta de modo** — opera igual en DISABLED/SHADOW (R3 §2.4, D-7). Requiere accesibilidad viva (si no, HTTP 503). |
| `GET /click` (`:410-421`) | Tap vía `performClick` estático | `puedeDespacharTap` (`ATAS:200`) | **No requiere accesibilidad**: ejecuta root-tap primario + auto-heal (`ATAS:230-243`). |
| `GET /swipe` | Gesto de swipe vía `performSwipe` | `puedeDespacharTap` | Taps/gestos reales sin vetos de estrategia/feed/riesgo. |
| `GET /mode` | Arma `AUTONOMOUS` | Ninguno más allá del loopback | Re-arma el modo operativo (con caducidad por heartbeat, R26). |
| Lectura: `/status /events /stats /shadow_journal /mode_journal /adaptive_state /ticks…` | Telemetría | Ninguno | Exposición de estado, saldo y decisiones. |

El único veto aguas abajo de `/trade`//`/click`//`/swipe` es la heurística demo (§4) — fail-open si
falla. La pareja `/trade`+`/click` constituye una vía de orden completa sin vetos de
estrategia/feed/riesgo, sin confirmación humana y sin requerir el servicio de accesibilidad.

## 4. El guard demo: cuadruplicado y heurística fail-open

- **4 guard sites con alcances distintos**: motor `TE:2183-2196` (solo AUTONOMOUS, fail-closed vía
  `puedeOperarAutonomoEnDemo`) + las 3 primitivas de tap `ATAS:200/:302/:532` (siempre, vía
  `puedeDespacharTap`).
- **Fuente única de `isDemoAccount`: heurística de TEXTO de la UI del broker** (`ATAS:701-707/:841-847`,
  matchea solo "cuenta demo"/"cuenta real" en español), con **default `true`** (`ATAS:65`) → si la
  heurística no observa (idioma no español, label ausente, accesibilidad caída), el sistema ASUME
  demo: **fail-open de facto** para los taps. Amortiguado en AUTONOMOUS por el suelo de equity demo
  (20M COP, `RiskManager.kt:129-131`) — pero no en `/trade`//`/click`.
- **Segunda fuente de verdad**: `RiskManager` infere demo por saldo ≥1M (`:724-725`) — dualidad
  reconocida en el propio código (`TE:2167-2177`).
- **Demo ≠ sandbox** (R3 §2.2): el feed es el broker real con token real; no hay entorno de prueba
  en ninguna capa. Ver `docs/SYSTEM_BOUNDARIES.md` §5.

## 5. Caducidad de AUTONOMOUS y rearme automático

- **Caducidad por heartbeat (R26, verificado en dispositivo esa ronda):** cada petición al bridge
  estampa el heartbeat; `AutonomousWatchdog` (55 LOC) hace caducar AUTONOMOUS sin beats. Evidencia
  R26: `loops/r26_validation/` (P1 caducó a los 630 s; P2 no caducó con beats).
- **`AUTO_REARM=1` del supervisor externo**: `scripts/td_supervisor.sh` (sin rastrear, 293 líneas)
  tiene re-arme automático de AUTONOMOUS por defecto, con **re-arme real documentado el
  2026-11-06T08:08:55Z** (`td_supervisor.log`; R1 §6.6). Inactivo desde 2026-11-07T18:27:41Z; lock
  obsoleto no eliminado (regla 7). Riesgo: si se relanza, re-arma AUTONOMOUS en el dispositivo demo
  sin acción humana.

## 6. Broadcasts internos protegidos por permiso de firma

- Permiso custom `com.example.tradedraw.permission.INTERNAL_COMMAND` con
  **`protectionLevel="signature"`** (`AndroidManifest.xml:19-21`) [VERIFICADO hoy].
- Los receptores dinámicos TAP/SWIPE/BACK/CMD se registran con ese permiso (`ATAS:444-451`,
  `OverlayService:382-389`). Cierra el vector R15-A (rearme del bot por broadcast de terceros): en
  API 31 un receptor dinámico sin permiso queda exportado por defecto — comentario del propio
  manifest. Solo apps firmadas con la misma clave pueden emitirlos.

## 7. Autorización del propietario y su estado en R29-A

- **Autorización Binomo-demo (2026-11-05)**: el propietario autorizó pruebas con AUTONOMOUS en
  cuenta demo (`AGENTS.md`, bloque "AUTORIZACIÓN DE PRUEBAS EN BINOMO (CUENTA DEMO)", diff +14 del
  working tree, R1 §2.1). El guard de cuenta REAL se mantiene.
- **Suspensión en R29-A**: esta misión **suspende expresamente** esa autorización para la fase
  (reglas 3–5 de la misión; `DECISIONS.md` D-4): no se activa AUTONOMOUS, no se conecta simulación
  con broker, no se toca el dispositivo ni el broker.
- **Nota documental (R1 §6.7)**: R28-B fundó parte de su bloqueo en un texto normativo anterior a
  esa autorización; contradicción integrada aquí (no invalida su auditoría técnica).

---

*Documento creado por la Ronda 5 de R29-A. Etiquetado por claim: VERIFICADO (con ruta:línea) /
PARCIALMENTE VERIFICADO / NO VERIFICADO / PROPUESTA-R29-B. El cifrado del token y la unificación
del guard demo son NO-corto-plazo (R4 §7: solo se envuelven, no se reescriben).*
