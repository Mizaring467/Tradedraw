# PREREGISTRO R25 — VENTANAS DE INSTRUMENTACIÓN CON TRANSPORTE ASEGURADO

**Fecha de fijación:** 2026-10-26 23:58 (America/Bogota) — escrito **antes** de abrir cualquier ventana de R25
**Rama:** `r10-night` · **Commit del repositorio al fijarse:** `692d6f3`
**Build bajo medición:** `692d6f3` — versionCode **19**, versionName `1.9-r23h-exhaustion`, buildId `R23H-BUY-EXHAUSTION` (APK compilada e instalada el 2026-10-26, SHA-256 `c2c03234f9a4993bc7164139a36eae0369c14ecec41af21c0d752357ff718331`)
**Dispositivo:** Redmi A1 `220733SL`, Android 12, serial ADB `192.168.1.196:5555`
**Redactado por:** DSH (orquestador de la sesión visible)
**Estado:** VIGENTE

---

## 0. REGLA DE ORO

Este documento se fija antes de observar un solo dato nuevo de R25. Cualquier cambio de criterio,
tolerancia o duración posterior a la primera ventana medida **invalida el ciclo completo** y se
declara con el número de ventanas afectadas. La estrategia **no se toca**: R25 no es una ronda de
mejora, es una ronda de **verificación de que la instrumentación mide lo que dice medir**.

---

## 1. OBJETIVO

> **CERTIFICAR LA INSTRUMENTACIÓN END-TO-END CON EL TRANSPORTE ADB ASEGURADO. NO demostrar edge.**

R24 no pudo emitir veredicto porque W01 y W02 quedaron ciegas al caerse el reenvío `adb forward`
(ver `workflows/R24_W02_FALLO_TRANSPORTE.md`). R25 repite la medición con esa causa raíz cerrada.
Cualquier cifra de win rate, IC o EV que se publique de R25 va etiquetada
`INFORMATIVO — FUERA DE ALCANCE DE R25`.

**Build congelada:** prohibido modificar `app/` durante el cubo. Si hace falta un cambio de código,
el cubo se detiene, se declara, y se abre R26.

---

## 2. CONDICIÓN DE ARRANQUE (obligatoria, por ventana)

Ninguna ventana se abre si falla cualquiera de estos puntos. Se registran en
`loops/r25_validation/window_NN/window_metadata.json`.

| # | Condición | Cómo se comprueba |
| :--- | :--- | :--- |
| 1 | Dispositivo `device` en ADB | `adb -s 192.168.1.196:5555 get-state` |
| 2 | Reenvío presente | `adb forward --list` contiene `tcp:8080 tcp:8080` |
| 3 | **Watchdog del transporte corriendo** | `pgrep -f adb_forward_watchdog` devuelve PID |
| 4 | Reenvío **certificado 60 s seguidos** | 12 lecturas OK del watchdog antes de armar |
| 5 | `/status` responde y `gitSha == 692d6f3` | payload de `/status` |
| 6 | `isDemoAccount == true` | payload de `/status` |
| 7 | `health.state == READY` | payload de `/status` |
| 8 | `balance > 0` y fresco | payload de `/status` |
| 9 | Broker (`com.marketly.trading`) en primer plano | `dumpsys window \| grep mCurrentFocus` |
| 10 | Accesibilidad enlazada | `dumpsys accessibility` |
| 11 | Batería ≥ 25 % | `dumpsys battery` |
| 12 | `mode == DISABLED` antes de armar | payload de `/status` |

**Armado:** `GET /mode?name=AUTONOMOUS` (submodo `CONSERVATIVE`).
**Cierre:** `GET /mode?name=DISABLED` **antes** de escribir los artefactos; si falla, se reintenta y
se marca el fallo en el reporte.

---

## 3. VENTANAS

- **Duración:** 20 min nominales + gracia de liquidación de hasta 150 s (la del monitor v2).
- **Máximo:** **3 ventanas válidas**. No se para antes por resultado favorable ni se extiende por
  resultado desfavorable. No se reinicia el cubo tras una ventana mala.
- **Ventana inválida (no consume cupo):** se declara en el acto y se vuelve a intentar si ocurre
  cualquiera de estas condiciones del arnés:
  1. **3 muestras consecutivas** con `status=null` (hoy el monitor sigue 20 min a ciegas: se corrige
     con aborto temprano);
  2. cambio de PID de `com.example.tradedraw` durante la ventana;
  3. `health.state != READY` durante más de 60 s continuos;
  4. watchdog muerto o reenvío ausente durante más de 30 s.
- Un fallo **del motor** (crash, cierre inesperado, orden fuera de ventana) **sí** consume cupo y se
  reporta como dato: eso es comportamiento, no arnés.

---

## 4. CRITERIOS V1–V5

Se reutilizan **literalmente** las definiciones del `PREREGISTRO_R24.md` §3 (V1 contadores y
exclusión de incrementos manuales; V2 columnas S/R ≥ 99 %; V3 memoria adaptativa con volcado y
contador; V4 taxonomía `NO_PROCESADA` cerrada; V5 paridad live↔replay con el comparador congelado
`scripts/validation/compare_live_replay.py`, SHA-256 `4cb5082e4f8c52bcd775f0c5f3d53d992a3730007d1738d463a171f2c0794b5f` y tolerancia única ±6000 ms). No se redefinen aquí para no abrir la puerta a
reinterpretaciones; cualquier discrepancia entre este documento y R24 §3 se resuelve **a favor de
R24 §3**.

- Evaluación **por ventana** y **acumulada**; veredicto global `SÍ` solo si todos los criterios
  aplicables dan `SÍ` en todas las ventanas válidas.
- **V5** se evalúa **una sola vez** al cierre del cubo, con los mínimos de muestra de R24 §3
  (≥ 20 trades live y ≥ 200 decisiones comunes). Si no se alcanzan, `NO EVALUABLE`.
- Toda cifra se recalcula con `scripts/validation/` — **prohibido** transcribir números a mano.

---

## 5. EVIDENCIA POR VENTANA

`loops/r25_validation/window_NN/`:

`status_samples.jsonl`, `live_samples.jsonl`, `decisions.csv`, `trades.csv`, `ticks_live.csv`,
`mode_log.json`, `events.jsonl`, `replay_comparison.json`, `LIVE_REPORT.md`, `window_metadata.json`
y el `~/forward_watchdog.log` recortado a la ventana.

---

## 6. GUARDAS DE SEGURIDAD DURANTE LA VENTANA

1. **Demo obligatoria:** si el monitor observa `isDemoAccount == false`, ordena `DISABLED` y aborta.
2. **Sin supervisión no hay ventana:** el orquestador sondea `/status` cada ≤ 2 min y registra
   `mode`, `balance`, `orderState` y `hasPendingTrade`. El fallo de proceso del Incidente 2 de R24
   fue precisamente 3 h 15 min sin sondeo.
3. **Batería:** si baja de 20 %, se cierra la ventana y se detiene el cubo.
4. **Rearme espontáneo:** cualquier `MODE_CHANGE` con `source` distinto de `bridge(/mode)` se reporta
   como incidente y detiene el cubo.

---

## 7. CRITERIO DE CIERRE DEL CUBO

| Resultado | Condición |
| :--- | :--- |
| `PASS-INSTRUMENTACIÓN` | V1–V4 `SÍ` en **todas** las ventanas válidas y V5 evaluable con sus tres umbrales |
| `NO EVALUABLE` | falta de muestra para V5, o cualquier ventana inválida por arnés que no se pudo repetir |
| `NO` | cualquier discrepancia de V1, caída de V2 por debajo del 99 %, V3 incoherente o V4 sin registrar |

Ningún resultado de este cubo autoriza a operar con dinero real: el propio `PREREGISTRO_R24.md` §1
deja claro que 3 ventanas no dimensionan edge.
