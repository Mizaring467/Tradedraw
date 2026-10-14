# Preregistro R17 — Auditoría de `BinomoAuthActivity` y superficie exportada

**Estado: PREREGISTRO PROSPECTIVO.** Redactado `2026-10-14 14:18`, **antes** de completar la
auditoría y **antes** de proponer ningún parche.

**Alcance: SOLO AUDITORÍA.** Esta ronda **no modifica código de producción**. Las únicas
modificaciones al árbol son documentos `.md` y una propuesta de parche **fuera** del árbol de
producción (`scratch/proposals/`). Regla 2: nada puede cambiar una decisión de trading; y el
enunciado prohíbe aplicar el parche sin el usuario presente.

**Motivo:** `BinomoAuthActivity` está declarada `exported="true"` en el manifest
(`AndroidManifest.xml:38`) sin filtro de intents, y es la única Activity de la app que **maneja
credenciales**: captura y persiste el `authtoken` de Binomo.

---

## 1. Hipótesis y falsación, fijadas a priori

| # | hipótesis | qué la falsaría | estado esperado |
| :--- | :--- | :--- | :--- |
| **H-INTENT** | Un intent **de otra app** abre la actividad y **extras controlados por el atacante alteran el flujo** (URL a cargar, token precargado, acción ejecutada) | leer el código y comprobar que **no se lee ningún extra** de `intent` y que la URL es una constante | por determinar |
| **H-LEAK** | El token sale **en claro** por algún canal observable: logs, intents, `SharedPreferences` sin cifrar, URL, `WebView` | no encontrar ninguna escritura del token en claro | por determinar |
| **H-REDIRECT** | La actividad acepta **URLs o esquemas arbitrarios** (parámetro, `intent-filter`, redirección del `WebView` sin lista blanca) | comprobar que la URL es constante y que no hay `intent-filter` | por determinar |
| **H-BRIDGE** | La `@JavascriptInterface` expone más de lo necesario y permite a JS del `WebView` ejecutar acciones privilegiadas | revisar los métodos anotados y comprobar que sólo **reciben** datos | por determinar |
| **H-MODE** | El endpoint `/mode` del bridge HTTP permite rearmar el motor y es alcanzable por algo más que loopback | leer el binding del socket y las condiciones de acceso | por determinar |

**Regla de atribución (heredada de R15 §0.1, vigente):** sin evidencia medida → **NO ATRIBUIDA**.
No se declara un hallazgo por plausibilidad. Cada fila de la tabla final exige `archivo:línea`.

**Compromiso de redacción:** si un token aparece literalmente en una línea de código, se cita el
**fichero y la línea** con el valor **redactado como `***`**. No se copia, no se imprime, no se
commitea ningún valor real.

---

## 2. Criterio de éxito de esta ronda

Al no haber cambio de código, el éxito no se mide en «tests verdes» sino en **cobertura de la
superficie**:

| # | criterio |
| :--- | :--- |
| (a) | **Tabla completa** de todo componente `exported` de la app (Activities, Services, Receivers, Providers) con permiso, función y riesgo |
| (b) | Las **5 hipótesis** de §1 clasificadas con evidencia `archivo:línea`, sin ninguna en «por determinar» |
| (c) | Endpoint `/mode` analizado: quién puede alcanzarlo y por qué |
| (d) | **Parche propuesto** escrito en `scratch/proposals/`, **no aplicado** y **no commiteado** al código de producción |
| (e) | **Protocolo de prueba MANUAL** para el parche, ejecutable por el usuario cuando esté presente |
| (f) | **Cero valores de token** en el informe y en la propuesta |

---

## 3. Declaración de riesgo del parche

Si H-INTENT o H-REDIRECT se sostienen, el parche mínimo (`exported="false"`) **no rompe el login**
en el uso normal: la actividad se lanza **siempre** con `Intent` explícito desde
`MainActivity.kt:69` y `OverlayService.kt:1874`, ambos del mismo paquete. Un cambio a
`exported="false"` sólo afecta a lanzamientos **desde fuera**.

**Pero** hay que verificarlo, no suponerlo: se declara como **riesgo a comprobar** en el protocolo
manual de §4, no como hecho.

Si H-LEAK se sostiene, el parche es distinto y **también de bajo riesgo**: redactar los
`Log.*` que imprimen el token. No cambia ningún comportamiento funcional.

**Umbral de reversión fijado a priori:** si al aplicar el parche el login deja de funcionar, se
revierte el commit entero. El parche se propone con ese compromiso explícito.

---

## 4. Protocolo de prueba MANUAL (para cuando el usuario esté presente)

**No lo ejecuta el agente.** Se documenta en
[`scratch/proposals/R17_binomoauth.patch.md`](../../scratch/proposals/R17_binomoauth.patch.md) §4.
Resumen de los pasos que exigirá:

1. Compilar e instalar el APK con el parche.
2. Abrir la app → pulsar el botón que abre el login de Binomo (**camino interno**, debe funcionar).
3. Comprobar con logcat que **no aparece** ningún valor de token (usar el propio `grep` de la
   propuesta).
4. Desde una app de terceros, intentar `startActivity` con el componente explícito
   `com.example.tradedraw/.BinomoAuthActivity` → debe fallar con `SecurityException`.
5. Iniciar sesión y verificar que el token **se sigue capturando y guardando** (el flujo de
   vinculación de sesión no se rompe).

---

## 5. Qué NO hace esta ronda

- **No** aplica el parche.
- **No** commitea código de producción.
- **No** ejecuta taps reales ni `/trade`.
- **No** abre la pantalla de login (requeriría credenciales del usuario y un tap real).
- **No** cambia `targetSdk` ni `minSdk`.
- **No** imprime ningún valor de token: la auditoría se hace por **lectura de código**.
