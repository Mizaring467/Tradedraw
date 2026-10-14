# Preregistro R19 — Observación pasiva larga

**Estado: PREREGISTRO PROSPECTIVO.** Redactado `2026-10-14 15:28`, **antes** de iniciar la
observación.

**Árbol de partida:** `d8ea309` (`HEAD == origin/main`).
**Hora prevista de inicio:** `15:33` local.

---

## 1. Qué es exactamente este experimento

Observar el bot **sin tocarlo** durante **≥2 h** (objetivo 3 h si la batería lo permite) para
estimar si el motor se rearma solo a `AUTONOMOUS` con el vector de broadcast **cerrado** para apps
no privilegiadas.

- **Modo de partida: `DISABLED`** (verificado antes de empezar).
- **Interacción: NINGUNA.** Ni taps, ni `/trade`, ni `/mode`, ni diálogos.
- **Sin** reiniciar el servicio, **sin** abrir la app, **sin** tocar el teléfono.

---

## 2. Potencia estadística, fijada A PRIORI

La tasa de referencia procede de R14: **~2 rearmes en 1,5 h ≈ λ = 1,3 /h**.

Con un proceso de Poisson de tasa λ:

| duración | P(observar 0 eventos) | P(observar ≥1) |
| :--- | :--- | :--- |
| 1 h | 27 % | 73 % |
| **2 h** | **≈ 7 %** | **93 %** |
| **3 h** | **≈ 2 %** | **98 %** |

**Compromiso fijado a priori:** si ocurren **0 eventos**, se declarará literalmente
**«sin evidencia de rearme espontáneo con el vector cerrado, NO prueba de ausencia»**. Un resultado
negativo con 2 h reduce la tasa compatible, no la descarta. Con 3 h la restricción es más fuerte,
pero **sigue sin ser** una prueba de imposibilidad.

**Error a evitar (y que ya ocurrió en R15):** convertir «no vi nada en 25 min» (58 % de
probabilidad de no ver nada por puro azar) en «no pasa nada».

---

## 3. Sondeo, fijado a priori

| qué | cada | por qué |
| :--- | :--- | :--- |
| `GET /status` | **10 s** | modo, `orderState`, `hasPendingTrade`, feed |
| `GET /mode_log` | **10 s** | ring dedicado (R18) — eventos de modo **dentro** de la sesión |
| `GET /mode_journal` | **30 s** | **disco** (R18) — la única fuente que sobrevive a un reinicio del servicio |
| logcat `Permission Denial\|tradedraw.CMD\|tradedraw.TAP` | continua, a fichero del PC | atribución de broadcasts denegados, **solo si `logd` está vivo** |

**Doble fuente deliberada:** `/mode_log` (memoria) y `/mode_journal` (disco) cubren el caso de que
el servicio se recree a mitad de la observación. En R15-A la evidencia de modo se perdió por
confiar en una sola fuente.

---

## 4. Qué cuenta como «rearme espontáneo»

Un `MODE_CHANGE` cuyo destino sea `AUTONOMOUS` o `YOLO` **y** que no haya sido provocado por el
observador en la ventana. Como el observador **no interactúa**, cualquier transición a
`AUTONOMOUS` cuenta — **salvo** que el `origen` la explique por sí mismo:

| origen observado | ¿rearme espontáneo? |
| :--- | :--- |
| `overlayCommandReceiver` | **SÍ** — broadcast externo |
| `showAutonomousSubModeDialog` / `showModeDialog` | **SÍ** — UI sin humano |
| `TradeDrawHttpBridge.handleClient` | **SÍ** — `/mode` externo |
| `agentController` / `resumeAutonomousTrading` | **SÍ** — ruta automática |
| `onCreate(servicio recreado)` con destino `DISABLED` | **NO** — es el arranque normal |
| `onCreate` con destino **`AUTONOMOUS`** | **SÍ y GRAVE** — el modo vendría persistido, cosa que hoy no ocurre |

---

## 5. Protocolo si aparece un rearme

1. Registrar `origen`, instante exacto y el contenido de `/mode_journal`.
2. **Devolver a `DISABLED` de inmediato** por `/mode?name=DISABLED`.
3. Verificar `orderState=NINGUNA`, `hasPendingTrade=false`, **0 taps**.
4. Comparar el balance con el de partida.
5. **NO OPERAR BAJO NINGÚN CONCEPTO.** La observación se reanuda tras el reseteo, anotándolo.

---

## 6. Progreso y huecos

- Línea en `loops/R16-R19_PROGRESO.md` **cada 10 min**: hora, batería, muestras acumuladas, fallos
  de bridge, nº de `MODE_CHANGE`.
- **Si el bridge cae:** registrar el **hueco** con hora de inicio y fin, reintentar con
  `adb forward`, y **NO contar el hueco como «sin rearme»**. Un hueco es ausencia de dato, no
  ausencia de evento. **Este es el error metodológico que se debe evitar.**
- Si la batería baja del **20 %** y no carga: detener, dejar `DISABLED`, informe parcial.

---

## 7. Criterio de éxito

| # | criterio |
| :--- | :--- |
| (a) | ≥2 h de observación efectiva, con huecos cuantificados |
| (b) | Serie cruda versionada en `workflows/R19_observacion_pasiva.md` |
| (c) | Cierre con `mode=DISABLED`, `orderState=NINGUNA`, `hasPendingTrade=false`, 0 taps |
| (d) | Si hay 0 eventos: declarar «no prueba de ausencia», **no** «no ocurre» |
| (e) | Batería y huecos reportados por fase |

---

## 8. Hipótesis

| # | hipótesis | falsación | resultado esperado |
| :--- | :--- | :--- | :--- |
| **H-CERRADO** | Con el permiso de firma, **no hay rearmes espontáneos** por el vector de broadcast no privilegiado | un rearme con `origen` de `onReceive` sin humano | por determinar |
| **H-RESIDUAL** | **Sí** hay rearmes de origen **UI** u otro vector no cubierto | 0 eventos en ≥3 h → H-RESIDUAL queda **sin apoyo**, no refutada | por determinar |
| **H-BRIDGE** | El bridge se mantiene vivo y sin falsos positivos durante horas | caídas repetidas o JSON malformado | por determinar |

**Nota de rigor:** H-CERRADO y H-RESIDUAL no son excluyentes. Un rearme por UI no falsa H-CERRADO
(que habla del vector de broadcast) pero sí sería un hallazgo importante por sí mismo — es
exactamente el fenómeno del incidente de las `13:51:02`, que quedó **NO ATRIBUIDO**.

---

## 9. Restricciones inviolables

- **Nada** cambia una decisión de trading. `master_traders_skill` intacta.
- **Ningún** tap, `/trade`, ni cambio de modo manual.
- Bridge **solo** `127.0.0.1:8080` por `adb forward`.
- **No** levantar `logd` en caliente. **No** tocar ajustes del teléfono.
- **No** imprimir ni commitear tokens (regla 5).
