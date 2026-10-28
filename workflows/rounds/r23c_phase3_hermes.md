# R23C — FASE 3: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY (`w1:p1`)
**Fecha:** 2026-10-16
**Rama:** `r10-night`
**SHA base:** `b08459e`
**Estado:** IMPLEMENTADO, TESTS VERDES, APK COMPILADO E INSTALADO Y VERIFICADO EN DISPOSITIVO

---

## 1. ADVERTENCIA CIENTÍFICA: la evidencia NO sostiene la premisa

**Esto va primero porque es lo más importante del informe.** La fase se justifica con una asimetría
"BUY 55,56% vs SELL 42,86%" y concluye que hay que endurecer SELL. Calculé la potencia de esos números
antes de tocar código:

| Brazo | WR reportado | IC95% Wilson | ¿Solapa break-even (54,95%)? |
|---|---|---|---|
| BUY | 5/9 = 55,56% | **[26,7%, 81,1%]** | **SÍ** |
| SELL | 6/14 = 42,86% | **[21,4%, 67,4%]** | **SÍ** |
| TOTAL | 11/23 = 47,83% | [29,2%, 67,0%] | SÍ |

Diferencia BUY−SELL = 12,70 pp → **z = 0,60, p = 0,55 → NO significativa al 5%.**

Traducción: con N=23, ese 12,7 pp está **dentro del ruido**. El IC del 26,7%-81,1% del brazo BUY llega a
decir que BUY podría ser tan malo como 27%. La premisa "BUY es rentable, SELL es destructivo" **no está
establecida** con los datos de la fase.

### 1.1. Un subconjunto SÍ es significativo — pero apunta en otra dirección

Los `trades.csv` tienen 25 filas, no 23: w01 trae dos que **no son trades confirmados**
(`BUY NINGUNA sin operacion en curso` y `SELL ENVIADA tap despachado`). Filtrando por
`CONFIRMADA ... saldo movido` quedan 22, y clasificando por delta de balance (n=16):

```
TODOS                          n=16  W=6  L=10  WR= 37.5%
BUY (todas)                    n= 5  W=4  L= 1  WR= 80.0%   IC95% [37.6%, 96.4%]
SELL (todas)                   n=11  W=2  L= 9  WR= 18.2%   IC95% [ 5.1%, 47.7%]
--- por régimen cruzado con dirección ---
SELL contra-tendencia (UPTREND) n= 7  W=2  L=5   WR= 28.6%
SELL a favor (DOWNTREND)        n= 4  W=0  L=4   WR=  0.0%  <-- lo peor
BUY contra-tendencia (DOWNTREND)n= 3  W=2  L=1   WR= 66.7%
BUY a favor (UPTREND)           n= 2  W=2  L=0   WR=100.0%
```

Aquí la brecha es grande y significativa (SELL 2/11 vs BUY 4/5, z=2,90, **p=0,004**) y el IC de SELL
**[5,1%, 47,7%] EXCLUYE el break-even**. Pero:

1. **El brazo peor es "SELL a favor de tendencia" (0/4)**, no "SELL contra-tendencia". La spec endurece
   precisamente el filtro de **contra**-tendencia, que es el caso que rindió 28,6% — mejor que el de
   a favor. El cambio apunta a la mitad menos mala del problema.
2. El brazo BUY tiene **n=5**: la inferencia por balance es sólida en el agregado, pero estimar la
   diferencia entre dos brazos con 5 y 11 muestras sigue siendo frágil.

### 1.2. No hay preregistro para esta fase

Este repo tiene `PREREGISTRO_R10..R21` y `PREREGISTRO_R23A`, y **ninguno cubre R23C Fase 3**. En R23A
tuviste que firmar uno precisamente porque un cambio de umbral posterior a ver resultados invalida la
comparabilidad. **Falta la misma salvaguarda aquí**, y esta calibración es exactamente el caso que la
exige: se ajustan tres umbrales *después* de ver los resultados de 6 ventanas.

### 1.3. Qué implica

**Implementé lo que se me pidió**, pero se trata como **HIPÓTESIS A MEDIR, no como optimización
validada**. Si la próxima ventana mejora, no podrá atribuirse al cambio sin un preregistro previo; y
con la muestra actual, un resultado mejor también sería compatible con varianza.

---

## 2. Cambios Implementados

### A. `SyntheticCandleEngine.kt`
| REQ | Antes | Después | Contexto |
|---|---|---|---|
| 1 — confluencia | `isYolo -> 75 : 80` (único) | `isYolo -> SELL 80 / BUY 75`; resto 80 | `dispatchSniperSignal` |
| 2 — mecha contra-tendencia | `isYolo -> 0.30f : 0.50f` (único) | `isYolo -> SELL 0.45f / BUY 0.30f`; resto 0.50f | `isTrendDirectionAllowed` |
| 3 — mecha `MT_REJECTION` | `wickLimit` **compartido** CALL/PUT | `sellWickLimit = 0.40f` (YOLO) para PUT; `wickLimit = 0.30f` para CALL | `evaluateSniperOpportunity` |

### B. `app/build.gradle.kts`
| Campo | Antes | Después |
|---|---|---|
| `versionCode` | `14` | **`15`** |
| `versionName` | `"1.4-r23-yolo-iter2"` | **`"1.5-r23c-phase3"`** |
| `BUILD_ID` | `"R23-YOLO-ITER2"` | **`"R23C-PHASE3"`** |

### C. `SellFilterHardeningTest.kt` (nuevo, 8 tests)

---

## 3. Desviación con impacto real: REQ 3 no era implementable como estaba escrita

La spec pide *"Exigir `sellWickLimit = if (isYolo) 0.40f else 0.45f` (en lugar de `0.30f`). Para BUY
(CALL), mantener `0.30f`"*, como si `sellWickLimit` ya existiera. **No existía**: la variable real es
`wickLimit`, y es **una sola, compartida por las dos ramas** (CALL en línea 1225, PUT en 1238).

Implementar la letra de la spec (cambiar `wickLimit` a 0.40) habría **endurecido también las compras** —
el brazo que la evidencia señala como el bueno. Eso va en contra del objetivo declarado ("preservando
intactos los umbrales rentables de BUY").

**Solución aplicada:** desdoblar la variable. `wickLimit` (0.30f) queda solo para CALL y se añade
`sellWickLimit` (0.40f) para PUT, como la spec pretendía.

---

## 4. Tests

`./gradlew.bat testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL in 1m 31s**

| Métrica | Valor |
|---|---|
| **Tests ejecutados** | **434** |
| Fallos | **0** |
| Errores | **0** |

Delta: **426 → 434** (+8). Sin regresiones.

La mitad de los tests nuevos afirman precisamente que **BUY NO se movió** (mecha 35% aún aceptada en
BUY contra-tendencia, umbral de confluencia de BUY intacto en 75, `MT_REJECTION` de CALL intacto en
0.30), más un test que recorre los tres pares de umbrales y falla si alguno de SELL quedara por debajo
del de BUY — guard contra un endurecimiento que relaje algo por accidente.

---

## 5. Compilación, instalación y verificación en dispositivo

1. **APK:** `./gradlew.bat assembleDebug` → BUILD SUCCESSFUL, 13.408.878 bytes.
   Verificado **antes** de instalar que embebe la build correcta:
   `R23C-PHASE3` presente, `1.5-r23c-phase3` presente, `R23-YOLO-ITER2` **ausente**.
2. **Instalación:** `adb -s [fd10:d508:34cf:10:e8c5:80ff:fef0:d5f9]:5555 install -r ...` → **Success**.
3. **Verificación por paquete:** `versionCode=15`, `versionName=1.5-r23c-phase3`.
4. **Verificación en runtime** (`/status` tras lanzar la app y redirigir el puerto):

```
versionCode     = 15
versionName     = '1.5-r23c-phase3'
buildId         = 'R23C-PHASE3'
gitSha          = 'b08459e'
submode         = 'CONSERVATIVE'
mode            = 'DISABLED'
isDemoAccount   = True
strategy        = 'AUTO_ADAPTIVE'
```

**Trazabilidad cerrada:** `código candidato (b08459e) = APK instalada (15/R23C-PHASE3) = proceso en
ejecución`. `isDemoAccount = true` confirmado.

### 5.1. YOLO NO se activó (decisión consultada)

La spec pedía `POST /submode?name=YOLO`. **No lo hice**: el motor quedó en `DISABLED`/`CONSERVATIVE`
porque activarlo pondría al bot a operar en DEMO antes de que definieras el arranque de la ventana, y
eso contaminaría el conteo de muestras. Consulté y la decisión confirmada fue **dejar el motor en
DISABLED**: AGY/OpenCode arrancan la ventana cuando corresponda.

Para arrancar cuando toque:
```
curl.exe -s "http://127.0.0.1:8080/submode?name=YOLO"
```

---

## 6. Lo que este cambio NO puede medir retroactivamente

El `decisions.csv` del monitor guarda `score = 85.0` **hardcodeado** para todas las filas, no el puntaje
real de confluencia (que sí viaja en el `reason` del journal del motor). Por eso **no es posible calcular
cuántas de las SELL observadas habrían sido filtradas por el umbral nuevo de 80 pts**. El impacto solo
se medirá en la próxima ventana, contando SELL suprimidas por confluencia.

---

## 7. Recomendaciones

1. **Firmar un `PREREGISTRO_R23C_F3` antes de arrancar la ventana** (o declararla exploratoria, como se
   hizo en R23A). Con la muestra actual, cualquier mejora será indistinguible de varianza.
2. **Considerar el eje correcto en una fase propia.** El hallazgo del subconjunto es que el peor brazo
   es **SELL a favor de tendencia (0/4)**, no contra-tendencia. Endurecer el filtro contra-tendencia
   ataca la mitad que rindió 28,6%. Un filtro que penalice SELL cuando la tendencia ya es bajista
   apuntaría al caso medido como peor.
3. **Revisar también BUY contra-tendencia (2W/1L, n=3).** Quedó deliberadamente sin tocar para no mover
   dos variables en el mismo paso; es candidato si la próxima ventana lo respalda.
4. **No leer la próxima ventana como confirmación.** El criterio debería fijarse antes: p. ej. "SELL
   mejora su WR por encima del break-even con n≥30 en SELL".

---

## 8. Handoff

**Notificación:** enviada por Herdr a `w1:p1`.

> ⚠️ El motor está en **DISABLED** y el submode en **CONSERVATIVE**. La ventana NO ha empezado.
> Para arrancar: `curl.exe -s "http://127.0.0.1:8080/submode?name=YOLO"`.
