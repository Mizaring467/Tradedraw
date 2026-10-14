# Preregistro R18 — `MODE_CHANGE` persistente y atribución del emisor

**Estado: PREREGISTRO PROSPECTIVO.** Redactado `2026-10-14 14:38`, **antes** de tocar código de
producción.

**Árbol de partida:** `a20bb95` (`HEAD == origin/main`).

---

## 1. Regla de §0.1 de `PREREGISTRO_R15`, vigente

> Sin evidencia medida, un emisor **NO SE ATRIBUYE**. Un origen vacío se lee como «no hay origen»,
> no como «no se pudo capturar», y esa diferencia es lo que se mide.

Se hereda sin cambios. Esta ronda **añade capacidad de medida**; no reinterpreta la de R15.

---

## 2. Fijado a priori: el cambio es SOLO observabilidad

| invariante | compromiso |
| :--- | :--- |
| Nada cambia una decisión de trading | el registro **no** consulta ni modifica `RiskManager`, vetos, confluencia ni dimensionamiento |
| `master_traders_skill` intacta | ventana sniper, CHOP, DojiRatio, quarter-Kelly: **no se tocan** |
| `/events` no cambia de forma | los campos `success`, `total`, `vetoV1`, `vetoV2`, `events` **deben seguir existiendo con el mismo significado**; `vetoV1`/`vetoV2` siguen contando por tipo |
| Persistencia **solo-append** | nunca se reescribe ni se borra una línea del JSONL; la rotación **renombra**, no trunca |
| `N` de operaciones supervisadas | **0** |

**Umbral de reversión:** si cualquier test de `TradingEngine`, `RiskManager` o `ModeChangeEventTest`
cae, se revierte el commit entero.

---

## 3. Hipótesis y falsación

| # | hipótesis | falsación |
| :--- | :--- | :--- |
| **H-RING** | Un ring dedicado de `MODE_CHANGE` **no es desplazado por 500 eventos `ASSET`** | si tras 500 `ASSET` el ring pierde entradas de modo, FALSA |
| **H-COMPAT** | Añadir el ring **no rompe** el JSON actual de `/events` ni los contadores | si `vetoV1`/`vetoV2`/`total` cambian de significado o desaparecen, FALSA |
| **H-PERSIST** | El JSONL **sobrevive** a un reinicio del servicio y a la rotación del ring | si un `MODE_CHANGE` registrado no se relee tras reiniciar, FALSA |
| **H-ROT** | La rotación por tamaño **no pierde** los eventos más recientes | si tras rotar faltan las últimas entradas, FALSA |
| **H-UID** | `BroadcastReceiver.getSentFromUid()` está disponible en **API 31** | si no lo está, la hipótesis queda **FALSADA** y hay que declarar «UID del emisor NO DISPONIBLE en API 31» **sin inventarlo** |

---

## 4. Decisión de diseño fijada antes de escribir código

- **Ring dedicado de 100 entradas** (≥100 como exige el enunciado), **independiente** del buffer
  compartido de 200. No se toca la capacidad de 200 ni la semántica de `ASSET`.
- **Exposición en el bridge por endpoint NUEVO** `/mode_log`, **no** modificando `/events`. Razón:
  el enunciado exige no romper `vetoV1`/`vetoV2`/contadores; un endpoint nuevo garantiza
  compatibilidad **por construcción**, no por cuidado.
- **Persistencia en JSONL** con `ts, de, a, origen` y, si está disponible, `uid`/`pid`. Fichero en
  el directorio de ficheros de la app. **Tope 256 KB con rotación simple** a `.1` (el Redmi A1 tiene
  poco espacio; `filesDir` es del sistema y no se audita, pero se mantiene acotado igual).
- **Sin dependencias nuevas.** `File`, `BufferedWriter`, `File.appendText`. Nada de Room ni de
  librerías de logging.
- **Sin `Context` en el `object`**: la persistencia recibe el `File` ya resuelto, lo que además la
  hace **testeable en JVM puro** sin emulador ni Robolectric.
- **`duration_sec` no se usa como evidencia** (regla 10, intacta).

---

## 5. Riesgos declarados

| riesgo | mitigación |
| :--- | :--- |
| Escribir en disco desde el hilo que cambia el modo puede bloquear la UI | escritura **append** de una línea corta, con `try/catch` que **nunca** propaga: un fallo de disco no puede impedir un cambio de modo |
| El JSONL crece sin límite | rotación a 256 KB |
| El ring dedicado ocupa memoria | 100 entradas de texto corto: irrelevante frente a los 200 del buffer actual |
| Romper `/events` | endpoint separado + test de compatibilidad explícito |

**Precedente que obliga a la cautela:** R13 declaró una causa raíz falsa por plausibilidad (carrera
del bypass del HUD). Esta ronda **mide**, no explica.

---

## 6. Prueba controlada permitida (la ÚNICA con `AUTONOMOUS` en toda la misión)

Con **root** (el permiso de firma ya cierra la vía sin privilegios):

```bash
# UNA SOLA VEZ
adb shell su -c 'am broadcast -a com.example.tradedraw.CMD --es action AUTO'
```

Verificaciones obligatorias:

1. El `MODE_CHANGE` aparece en el **ring dedicado** (`/mode_log`).
2. Aparece también en el **JSONL** con origen legible.
3. **Sobrevive a un reinicio del servicio** (arranque con
   `su -c 'am start-foreground-service -n com.example.tradedraw/.OverlayService'`, **sin tap**).
4. Se devuelve a `DISABLED` por `/mode`.
5. `orderState=NINGUNA`, `hasPendingTrade=false`, `tapsDespachados=0`.
6. Balance **idéntico**: `37.974.735,36`.

**Si cualquier punto falla**, se registra como fallo y **no** se maquilla.

---

## 7. Límite declarado de la atribución

Aunque H-UID se sostenga, el UID sólo atribuye **broadcasts que lleguen al receptor**. Un emisor que
use `root` aparecerá como **UID 0** — que es el operador, no un atacante. Y si `logd` está caído,
**no hay** `Permission Denial` que capturar: se declarará la limitación, no se rellenará el hueco.
**En ningún caso se levanta `logd` en caliente** (AGENTS.md §8.2).
