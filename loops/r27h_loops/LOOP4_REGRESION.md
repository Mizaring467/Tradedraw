# LOOP 4 — Regresión global

**Fecha:** 2026-11-05 · **Rama:** `r10-night` · **HEAD:** `c934835` (sin commits)
**Agente:** OpenCode · **Modo:** DEMO/DISABLED

---

## 1. Suite completa

```bash
./gradlew testDebugUnitTest --no-daemon --no-configuration-cache
```

**Resultado real:** `BUILD SUCCESSFUL in 28s`

| Métrica | Valor |
| :--- | :--- |
| Clases con XML de resultados | **58** |
| Tests | **566** |
| Skipped | 2 |
| **Failures** | **0** |
| **Errors** | **0** |

### Evolución

| Hito | Tests | Clases |
| :--- | :--- | :--- |
| R26 (referencia) | 513 | 55 |
| R27-H (health) | 528 | 55 |
| **R27-H loops (esta ronda)** | **566** | **58** |

Delta de esta ronda: **+38 tests** en 3 clases nuevas
(`ActiveInstanceTest` 13, `HealthDataSourceTest` 12, `BalanceFreshnessSeparationTest` 13).

### Suite Python (heredada, sin cambios en esta ronda)

```bash
PYTHONPATH=.:validation_engine /data/data/com.termux/files/usr/bin/python3 -m unittest discover -s tests
→ Ran 43 tests · OK
```

---

## 2. `git diff --check`

```
app/src/main/java/com/example/tradedraw/OverlayService.kt:116..144,268..271: trailing whitespace
```

**Causa raíz verificada, no asumida:**

| Archivo | Fin de línea | Líneas |
| :--- | :--- | :--- |
| `OverlayService.kt` | **CRLF** | 2466 de 2466 |
| `MainActivity.kt` | LF | 0 CRLF |
| `RiskManager.kt` | LF | 0 CRLF |
| `AutoTradeAccessibilityService.kt` | LF | — |

`OverlayService.kt` es CRLF **íntegro y preexistente**. `git diff --check` interpreta el `\r` final
como espacio en blanco sobrante en **cada línea añadida**. No hay espacios reales que limpiar.

**Comprobado que los demás archivos tocados están limpios:**
```
git diff --check AutoTradeAccessibilityService.kt TradeDrawHttpBridge.kt ActiveInstance.kt
→ sin salida (limpio)
```

**Decisión:** NO se normaliza el archivo. Reescribir 2466 líneas para alinear saltos de línea sería
un cambio masivo fuera del alcance de loops mínimos, y haría ilegible la revisión del diff real
(33 líneas añadidas). Se documenta como deuda de estilo, con propuesta en §6.

---

## 3. Diff acumulado

### Código de producción

```
AutoTradeAccessibilityService.kt  |  58 +++++-
OverlayService.kt                 |  33 ++++
TradeDrawHttpBridge.kt            |   5 +-
health/HealthSupervisor.kt        | 211 +++++++++++++++++++++-----
4 files changed, 252 insertions(+), 55 deletions(-)

ActiveInstance.kt (nuevo)         |  ~80 líneas
```

### Pruebas nuevas

```
ActiveInstanceTest.kt                  | 13 tests
HealthDataSourceTest.kt                | 12 tests
BalanceFreshnessSeparationTest.kt      | 13 tests
health/HealthSupervisorTest.kt         | 20 tests (reescrito en R27-H)
```

### Documentación

```
loops/r27_validation/*        (R27-H, 5 archivos)
loops/r27h_loops/*            (esta ronda, 5 archivos)
docs/agents/*                 (ronda previa, preservados)
```

---

## 4. Verificación de que los guards siguen intactos

| Guard | Verificación | Estado |
| :--- | :--- | :--- |
| `TradingEngine.isBalanceFresh` 3000 ms | `sed -n '925,926p'` → `nowMs - lastReadMs < 3000L` | **INTACTO** |
| `TradingEngine.motivoVetoPorDesajusteDeActivo` | `grep -c` → 1 (sigue existiendo, sin cambios) | **INTACTO** |
| `RiskManager.kt` | `git diff --quiet` → sin cambios | **INTACTO** |
| Estrategias / señales / parámetros | Ningún archivo de estrategia tocado | **INTACTO** |
| Ventanas operativas | Sin cambios | **INTACTO** |
| Permisos del receptor de comandos | `com.example.tradedraw.permission.INTERNAL_COMMAND` sin tocar | **INTACTO** |
| `PREREGISTRO_R27.md` | **No creado** — R27 sigue reservado a V5 | **INTACTO** |
| Cambios preexistentes (`CLAUDE.md`, `docs/agents/`) | Presentes en `git status` | **PRESERVADOS** |

### Confirmación por prueba

- `desajuste real con pantalla activa sigue vetando` (HealthSupervisorTest) — el veto no se relajó.
- `guard del motor sigue en 3000 ms y no se toco` (BalanceFreshnessSeparationTest) — el umbral
  contable no se movió.
- `una ventana diagnostica holgada no implica ventana contable holgada` — la asimetría es intencional
  y está fijada.

---

## 5. Verificación en dispositivo — **BLOQUEADA**

| Aspecto | Estado |
| :--- | :--- |
| ADB real 35.0.2, `emulator-5554` | VERIFICADO |
| APK instalado | `versionCode=20`, `1.10-r26-autonomous-expiry` — **es el de R27-H, no contiene estos cambios** |
| `/status` del APK instalado | Al inicio de la ronda respondía; al final **el bridge ya no estaba escuchando** |
| `OverlayService` en `dumpsys activity services` | **0** — el servicio ya no estaba activo al cierre |
| Batería | 37 % al inicio → **33 %** al cierre |

**Por qué no se validó en dispositivo:**

1. Instalar el APK requiere autorización expresa (§3 del prompt: *"No instalar APK ni reiniciar
   servicios sin autorización expresa"*). **No se solicitó ni se asumió.**
2. Reiniciar el `OverlayService` para recuperar el bridge es exactamente "reiniciar servicios".
   **Prohibido.**
3. Batería descendiendo (33 %) y §8.3 de `AGENTS.md` indica detener pruebas bajo cierto umbral.

**Consecuencia honesta: ningún resultado de esta ronda está validado en dispositivo.** Todo es JVM
determinista sobre datos ficticios, tal como exige la sección 3.

---

## 6. Propuesta (no aplicada): normalizar fin de línea

`.gitattributes` no existe en el repo. Propuesta para una ronda futura, con autorización:

```
* text=auto
*.kt text eol=lf
*.md text eol=lf
```

Aplicarlo normalizaría `OverlayService.kt` en un commit dedicado, de modo que `git diff --check`
deje de dar falsos positivos. **No se aplicó**: cambia 2466 líneas de un archivo ajeno al alcance
de estos loops y ensuciaría el `git status` que el prompt pide preservar.

---

## 7. Siguiente acción recomendada

1. **Decisión del usuario:** autorizar instalación para validar en dispositivo los Loops 1–2.
2. **Decisión del usuario:** ¿bloqueo estricto de `BALANCE_STALE`? (§Loop 3 §6)
3. **Decisión del usuario:** aplicar `.gitattributes` y normalizar `OverlayService.kt`.
4. Nueva auditoría de siguiente impacto (ver `SIGUIENTE_AUDITORIA.md`).
