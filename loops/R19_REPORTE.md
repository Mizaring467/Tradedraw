# Ronda 19 — Informe: observación pasiva larga

**Tarea:** observar el bot sin tocarlo ≥2 h para estimar si se rearma solo con el vector de broadcast
cerrado.
**Árbol de partida:** `01618ab` (`HEAD == origin/main`).
**Inicio:** `2026-10-14 14:28:06` local · **Fin:** `16:34:45` · **Duración efectiva: 126 min.**
**Batería:** inicio **75 %** · final **83 %** (cargando todo el tiempo, `status: 2`).
**Serie cruda:** [`workflows/R19_observacion_pasiva.md`](../workflows/R19_observacion_pasiva.md)
(673 filas).

---

## 0. Resumen

1. **126 min** de observación efectiva, **673 muestras**, **0 fallos de bridge**, **0 huecos**.
2. **4 rearmes a `AUTONOMOUS`. LOS CUATRO por `showAutonomousSubModeDialog`** (ruta de UI).
3. **Cero** rearmes por `onReceive` no privilegiado, `handleClient` o `agentController`.
4. **0 taps, 0 operaciones, balance idéntico** `37.974.735,36` antes y después.
5. **Durante los 234 muestreos en `AUTONOMOUS` (~39 min), `orderState` fue `NINGUNA` y
   `hasPendingTrade` fue `false` en el 100 % de las muestras.** El motor **no operó**.
6. **83→167 vetos V2** por rango insuficiente: la gobernanza cuantitativa **bloqueó toda entrada**.
7. **H-CERRADO SOSTENIDA** (el vector de broadcast no privilegiado no se usó).
   **H-RESIDUAL SOSTENIDA** (queda un vector vivo: la UI).
   **H-BRIDGE SOSTENIDA** (0 fallos en 126 min).

---

## 1. Resultado cuantitativo

| métrica | valor |
| :--- | :--- |
| Duración efectiva | **126 min** (mínimo exigido: 120) |
| Muestras | **673** |
| Fallos de bridge | **0** |
| Huecos | **0** |
| Batería inicio → fin | `75 %` → `83 %` (cargando) |
| Muestras en `DISABLED` | **439** |
| Muestras en `AUTONOMOUS` | **234** |
| Muestras con `orderState != NINGUNA` | **0** |
| Muestras con `hasPendingTrade != false` | **0** |
| **Taps despachados** | **0** |
| `totalWins` / `totalLosses` | **0 / 0** |
| Vetos V2 acumulados | **167** |
| Balance inicio / fin | `37.974.735,36` / **`37.974.735,36`** |

---

## 2. Los 4 rearmes — tabla de atribución

Todos capturados por el **diario en disco** de R18, que es la única fuente que sobrevive a
reinicios.

| # | hora | transición | **origen** | vector |
| :--- | :--- | :--- | :--- | :--- |
| — | `14:23:34` | `DISABLED → AUTONOMOUS` | `overlayCommandReceiver.onReceive:161` | **prueba controlada R18.5 (root)** |
| 2 | **`14:26:11`** | `DISABLED → AUTONOMOUS` | `showAutonomousSubModeDialog$lambda$36:869` | **UI** |
| 3 | **`15:03:08`** | `DISABLED → AUTONOMOUS` | `showAutonomousSubModeDialog$lambda$36:869` | **UI** |
| 4 | **`15:32:23`** | `DISABLED → AUTONOMOUS` | `showAutonomousSubModeDialog$lambda$36:869` | **UI** |

**Los tres reseteos intermedios** (`14:27:01`, `15:26:51`, `15:53:23`) tienen origen
`TradeDrawHttpBridge.handleClient:169` — **son de este agente**, no del sistema.

**Lectura del patrón:**

> **De 4 rearmes, 4 son ruta de UI. El único `onReceive` de toda la ventana es la prueba controlada
> que yo mismo ejecuté con `root`. Cero rearmes por el vector que `af71997` cerró.**

### 2.1 Contexto de logcat de los tres no provocados

Captura en paralelo (job `pwsh-136`), y en los tres casos el patrón es **idéntico**:

| incidente | ventana de foco observada |
| :--- | :--- |
| 2 (`14:26:11`) | `binomo TradingActivity` → `Window{99e301 tradedraw}` (`14:26:08`) → `Window{50e0f73 tradedraw}` (`14:26:10`) → rearme (`14:26:11`). Antes: `START` de Binomo con `bnds=[528,560][696,801]` desde uid 10051 y 4 `onScroll` |
| 3 (`15:03:08`) | `Binomo TradingActivity` → `Window{d80c5b0 tradedraw}` (`15:03:06`) → `Window{615bc5e tradedraw}` (`15:03:07`) → rearme (`15:03:08`) → cierre `EXITING` (`15:03:09`) |
| 4 (`15:32:23`) | mismo patrón de dos ventanas overlay en secuencia de ~1 s |

`showAutonomousSubModeDialog` (`OverlayService.kt:843-874`) exige **dos selecciones del usuario**
(menú de modo → submenú). **No hay ninguna ruta de código que la invoque automáticamente** — se
verificó en R16/R17 leyendo sus llamadores (`showModeDialog`, invocado sólo desde `OnClickListener`
y desde dos ramas de UI).

**Clasificación:** **rearme por ruta de UI con confusor humano → NO cuenta como espontáneo**
(preregistro §4). **NO ATRIBUIDO con certeza absoluta**: no se descarta inyección de gestos por una
app con accesibilidad, y no hay registro de eventos táctiles crudos.

---

## 3. EL HALLAZGO MÁS IMPORTANTE: la gobernanza aguantó

Durante **234 muestras (~39 min) el motor estuvo en `AUTONOMOUS`**, sin supervisión, y:

| comprobación | resultado |
| :--- | :--- |
| `orderState` distinto de `NINGUNA` | **0 / 234** |
| `hasPendingTrade = true` | **0 / 234** |
| Taps despachados | **0** |
| Operaciones | **0** |
| Balance | **sin cambios** |

**¿Por qué no operó?** Porque sus propios filtros lo bloquearon:

```
VETO: V2 rango=2.882e-10 umbral=1e-05 activo=Crypto IDX
VETO: V2 rango=2.726e-10 umbral=1e-05 activo=Crypto IDX
VETO: V2 rango=2.337e-10 umbral=1e-05 activo=Crypto IDX
… (167 en total)
```

El rango medido del activo es `~2.9e-10`, **cinco órdenes de magnitud por debajo** del umbral
`1e-05`. El activo `Crypto IDX` estaba **plano** durante toda la ventana.

> **`AUTONOMOUS` no significa «operar»: significa «evaluar». Y la evaluación dijo que no.**
>
> Un rearme no supervisado **no produjo ninguna operación** porque `master_traders_skill` hizo
> exactamente su trabajo: el veto V2 bloqueó todas las entradas y `RiskManager` nunca llegó a
> despachar. **Este es el resultado de seguridad más valioso de R16–R19.**

**Esto también explica por qué R14 no produjo operaciones** a pesar de sus dos rearmes: el mismo
mecanismo. No es suerte; es diseño.

---

## 4. Hipótesis: veredicto

| # | hipótesis | veredicto | evidencia |
| :--- | :--- | :--- | :--- |
| **H-CERRADO** | No hay rearmes espontáneos por el vector de broadcast no privilegiado | **SOSTENIDA** | **0 rearmes por `onReceive`** en 126 min, con el permiso de firma activo |
| **H-RESIDUAL** | Quedan rearmes por otros vectores | **SOSTENIDA** | **4 rearmes por ruta de UI**. El vector de UI sigue plenamente vivo |
| **H-BRIDGE** | El bridge aguanta horas sin falsos positivos | **SOSTENIDA** | **673/673 muestras OK, 0 huecos, 0 JSON malformado** |

**Sobre la potencia estadística (preregistro §2):** con λ≈1,3/h, **P(0 eventos en 2 h) ≈ 7 %**. No
se observaron 0 eventos, así que la prueba de potencia **no es la que decide aquí**: se observaron
**4 eventos, todos atribuidos a UI**. La conclusión no depende de un resultado negativo.

**Nota de rigor:** **no** se puede concluir «no hay rearmes espontáneos» en términos absolutos.
Lo medido es: **con el vector de broadcast cerrado, los rearmes observados vinieron de la UI**, con
indicio fuerte de interacción humana en los tres casos.

---

## 5. Riesgo residual expuesto por esta ronda

| riesgo | evidencia | severidad |
| :--- | :--- | :--- |
| **La ruta de UI sigue siendo una puerta funcional** | 4 rearmes, todos por `showAutonomousSubModeDialog` | **MEDIA** — por diseño exige dos toques, pero **un toque fantasma o una app con accesibilidad la usaría** |
| **`AUTONOMOUS` puede alcanzarse sin supervisión** | 3 veces en 126 min | **MEDIA** |
| **…pero no operó** | 0 taps, 0 operaciones, 167 vetos | la gobernanza **absorbió** el riesgo |

**Conclusión operativa:** el riesgo real **no** es «una operación no supervisada» — eso lo bloquea
`master_traders_skill`, medido. El riesgo real es **«una orden se despacha cuando el mercado SÍ
tenga rango»** en un rearme no supervisado. Ese escenario **no ocurrió** en esta ventana y **no se
puede descartar**. Es la recomendación principal para R20.

---

## 6. Verificación del preregistro

| # | criterio | resultado |
| :--- | :--- | :--- |
| (a) | ≥2 h efectivas con huecos cuantificados | **SÍ** — 126 min, **0 huecos** |
| (b) | Serie cruda versionada | **SÍ** — `workflows/R19_observacion_pasiva.md`, 673 filas |
| (c) | Cierre `DISABLED`/`NINGUNA`/`false`/0 taps | **SÍ** |
| (d) | Si 0 eventos, declarar «no prueba de ausencia» | **N/A** — hubo 4 eventos, todos atribuidos |
| (e) | Batería y huecos reportados | **SÍ** |

**Cierre verificado:** `mode=DISABLED`, `orderState=NINGUNA`, `hasPendingTrade=false`,
`totalWins=0`, `totalLosses=0`, **0 taps**, balance **`37.974.735,36` idéntico**. Bridge en
`127.0.0.1:8080` por `adb forward`. `logd` seguía `running` al cierre.

---

## 7. NO VERIFICADO

1. **La causa de los rearmes no está atribuida con certeza absoluta.** Hay indicio fuerte de
   interacción humana; una app con accesibilidad no se descarta.
2. **No hubo ventana con mercado operable.** El activo estuvo plano 126 min: **no se probó** qué
   haría el motor en `AUTONOMOUS` no supervisado **con rango suficiente**. Es el hueco de evidencia
   más importante de la misión.
3. **`balance=0.0` durante la ventana**: leído como `0.0` mientras Binomo no estaba en primer plano
   (`readCurrentBalance()` → `null`). El valor real `37.974.735,36` se leyó **al final**, con la
   pantalla del broker activa. No es discrepancia.
4. **No se verificaros los 3 rearmes con grabación de eventos táctiles crudos** (haría falta
   `getevent`, que no se usó para no interferir).
5. **El diario no se probó a través de un reinicio del teléfono**, sólo del servicio (R18).
6. **La rotación real de 256 KB** no se ejercitó en dispositivo.

---

## 8. Recomendaciones para R20

1. **PRIORITARIO — repetir la observación cuando el mercado TENGA rango.** Es el único modo de
   saber si un rearme no supervisado llega a despachar. Sin eso, el hueco de evidencia persiste.
2. **Considerar un límite de sesión en `AUTONOMOUS`** cuando se alcance sin una confirmación
   explícita reciente (p. ej. auto-`DISABLED` a los N minutos sin interacción). Mitiga el vector de
   UI sin quitarle control al humano.
3. **Aplicar C1 de R17** (redactar los 3 logs del token): riesgo nulo, hallazgo real.
4. **Aplicar C2** (`exported="false"` en `BinomoAuthActivity`) con su protocolo manual.
5. **Aplicar C4** (token efímero en el bridge) para cerrar `/mode` a otras apps.
6. **Mantener el diario de R18 en producción**: en esta ronda demostró su valor atribuyendo 4
   rearmes que en R14 habrían quedado sin explicación. Es la pieza de observabilidad más útil que
   se ha añadido en toda la misión.

---

## 9. Veredicto

**PASS-AUTOR** (verificación independiente pendiente: agy/hermes).
