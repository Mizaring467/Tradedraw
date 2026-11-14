# R23 — ITERACIÓN 1: REPORTE DE IMPLEMENTACIÓN DE HERMES

**Implementador:** HERMES (`w1:p6`)
**Orquestador:** AGY
**Fecha:** 2026-10-15
**SHA base:** `e3253c0`
**Estado:** COMPLETADO — LISTO PARA BUILD/ADB DE AGY
**Hipótesis:** Trazabilidad de Build en Runtime + Exposición Cuantitativa de `V2_RANGE`

---

## 1. Resumen de Cambios Aplicados

### A. `app/build.gradle.kts`
| Campo | Antes | Después |
|---|---|---|
| `versionCode` | `5` | **`6`** |
| `versionName` | `"1.4-r22"` | **`"1.4-r23-iter1"`** |
| `BuildConfig.GIT_SHA` | — | **añadido** (`git rev-parse --short HEAD`) |
| `BuildConfig.BUILD_ID` | — | **añadido** (`"R23-ITER1"`) |
| `BuildConfig.BUILD_TIMESTAMP` | — | **añadido** (`System.currentTimeMillis()`) |

### B. `app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt`
En `buildStatusJson()`:

1. Raíz del JSON (junto a `versionCode`/`versionName`):
   ```
   "gitSha": "${BuildConfig.GIT_SHA}",
   "buildId": "${BuildConfig.BUILD_ID}",
   "buildTimestamp": ${BuildConfig.BUILD_TIMESTAMP},
   ```
2. Bloque `"feed"` (junto a `recentPriceRangeRatio`):
   ```
   "rangoRelativoMinimo": ${TradingEngine.RANGO_RELATIVO_MINIMO},
   "rangoRelativoActual": ${ws?.recentPriceRangeRatio() ?: -1.0},
   ```

---

## 2. DESVIACIÓN ÚNICA RESPECTO A LA ESPECIFICACIÓN (justificada, funcionalmente idéntica)

La spec entregada usaba `project.exec { ... standardOutput = stdout }`. **Gradle 9.5.0 eliminó la API `Project.exec`** (fallo de compilación: `Unresolved reference 'exec'` / `Unresolved reference 'io'`).

Sustitución aplicada por la API vigente `ProviderFactory.exec`, con el mismo resultado (SHA corto de HEAD, fallback `e3253c0`):

```kotlin
val gitSha = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim().ifBlank { "e3253c0" }
} catch (e: Exception) {
    "e3253c0"
}
```

Añadido además el guard `ifBlank { "e3253c0" }` para el caso de repo git ausente/sin commits (comportamiento garantizado del fallback).

**Restricciones respetadas:** no se tocó lógica de trading, órdenes, risk management, guards ni `RANGO_RELATIVO_MINIMO`. El umbral se **expone**, no se modifica.

---

## 3. Evidencia de BuildConfig Generado

`app/build/generated/source/buildConfig/debug/com/example/tradedraw/BuildConfig.java`:
```
public static final int VERSION_CODE = 6;
public static final String VERSION_NAME = "1.4-r23-iter1";
public static final String BUILD_ID = "R23-ITER1";
public static final Long BUILD_TIMESTAMP = 1792085187733L;
public static final String GIT_SHA = "e3253c0";
```

---

## 4. Estado de Tests Locales

Comando: `./gradlew.bat testDebugUnitTest --console=plain`

```
BUILD SUCCESSFUL in 54s
25 actionable tasks: 13 executed, 12 up-to-date
```

| Métrica | Valor |
|---|---|
| Archivos de resultados (clases de test) | 37 |
| **Tests ejecutados** | **366** |
| Fallos | **0** |
| Errores | **0** |

Compatibilidad total mantenida (baseline R22: 345 tests; sin regresiones, +21 tests ya presentes en el árbol).

---

## 5. Contrato `/status` entregado a OpenCode

Campos nuevos disponibles para validación live:
- `gitSha` (string)
- `buildId` (string)
- `buildTimestamp` (long, epoch ms)
- `feed.rangoRelativoMinimo` (double, `1.0E-5`)
- `feed.rangoRelativoActual` (double; `-1.0` si no hay feed)

JSON resultante estrictamente válido — sin comentarios ni artefactos de plantilla.

---

## 6. Handoff a AGY

**Pendiente de AGY:** compilar APK, instalar por ADB (`220733SL` / `UGU8KFUKM7E6CE8D`) e iniciar validación live de ~20 min con OpenCode.

**Verificación obligatoria de trazabilidad post-instalación:** `GET /status` debe devolver
`versionCode=6`, `versionName="1.4-r23-iter1"`, `buildId="R23-ITER1"` y `gitSha` = SHA del commit que AGY compila.

> ⚠️ Nota: `GIT_SHA` se captura en **tiempo de configuración de Gradle**, es decir con el HEAD que exista al correr el build. Tras commitear estos cambios, el SHA embebido cambiará a ese nuevo commit; AGY debe registrar el `gitSha` que reporte `/status` como identidad del candidato, no `e3253c0`.
