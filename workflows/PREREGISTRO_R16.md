# Preregistro R16 — Retroactivo: cierre de los receptores de comando exportados

**Estado: PREREGISTRO RETROACTIVO.** Redactado `2026-10-14 14:00`, **después** de que el fix
`af71997` existiera y estuviera pusheado. Se declara explícitamente porque viola la regla 6 de la
misión (preregistrar ANTES de cambiar código). No se oculta ni se reformula como si hubiera sido
previo.

---

## 1. Declaración de la irregularidad

| hecho | detalle |
| :--- | :--- |
| El fix se implementó | `2026-10-14 13:24-13:38`, en la **misma sesión** que R15-A |
| Se commiteó y pusheó | `af71997`, sin preregistro ni informe propios |
| Motivo | El usuario eligió la opción «A) cerrar los receptores exportados» al cierre de R15-A, y se ejecutó de inmediato |
| Qué faltó | (a) `workflows/PREREGISTRO_R16.md` antes de tocar código; (b) informe `loops/R16_REPORTE.md`; (c) la enmienda a `R15_REPORTE.md` §5 y §8, que quedaron desactualizados |
| Alcance respecto a R15 | **Fuera** de `workflows/PREREGISTRO_R15.md` §5, que declaró los receptores exportados explícitamente **fuera de alcance** («no se corrige en R15-A») |
| Preregistro de R16-A | Sí existe [`workflows/PREREGISTRO_R16_A.md`](PREREGISTRO_R16_A.md), escrito **antes de compilar** el parche definitivo, con las 4 hipótesis y sus falsaciones |

**Lo que sí se hizo bien:** `PREREGISTRO_R16_A.md` se escribió antes de aplicar el parche final, y
documenta el hallazgo que cambió la solución (API 31 vs `RECEIVER_NOT_EXPORTED` de API 33). El
fix no fue improvisado.

**Lo que no se hizo bien:** no hubo informe de la ronda, no se verificó el impacto sobre el
tooling, y el informe de R15 quedó con dos secciones que dicen lo contrario de lo que ahora es
cierto.

---

## 2. Criterio de éxito, fijado a priori para la verificación de R16

Se fija **antes** de ejecutar la verificación de la Fase R16 (que es la parte de esta ronda que sí
es prospectiva). Los cuatro puntos deben cumplirse simultáneamente:

| # | criterio | cómo se mide | resultado |
| :--- | :--- | :--- | :--- |
| **(a)** | `adb shell` (**UID 2000**) queda **bloqueado**, tanto dirigido (`-p`) como implícito | `am broadcast … --es action AUTO` y comprobar que `mode` sigue `DISABLED` | **CUMPLIDO** (R16-A §3.2) |
| **(b)** | `root` (**UID 0**) **sigue funcionando** — control positivo que prueba que el receptor vive | `su -c 'am broadcast …'` y comprobar `AUTONOMOUS` | **CUMPLIDO** (R16-A §3.2) |
| **(c)** | Ninguna ruta interna de la app emite estos broadcasts | `grep` de `sendBroadcast` en todo el código Kotlin | **CUMPLIDO**: cero coincidencias |
| **(d)** | Cero cambios en decisiones de trading | `git show af71997 --stat` no toca `TradingEngine`, `RiskManager`, umbrales ni vetos | **CUMPLIDO** (sólo 2 registros + manifest) |

**Regla de atribución:** si (a) falla, el fix **NO funciona** y debe declararse así sin matices. Si
(b) falla, el receptor está muerto y (a) es un falso positivo: el fix estaría **ROTO de otra
forma**. Si (b) pasa y (a) falla, el permiso no se aplica y el fix **NO funciona**.

**Nota fijada a priori:** `adb shell` corre como UID 2000. `PREREGISTRO_R16_A.md` §3 anticipó que
podría **conservar** acceso (algunos builds conceden permisos declarados al shell). Si lo
conservara, (a) no se cumpliría y habría que declarar el fix **PARCIAL**: cerrado para apps de
terceros, abierto para el operador con ADB. En la medición **no** lo conservó.

---

## 3. Hipótesis y falsación (heredadas de R16-A, ahora con el criterio de §2)

| # | hipótesis | falsación | resultado |
| :--- | :--- | :--- | :--- |
| H1 | El parche cierra el vector de rearme por broadcast | si `mode` pasa a `AUTONOMOUS` desde UID 2000, FALLA | **SOSTENIDA** |
| H2 | El parche cierra el vector de taps | si se registra un tap real, FALLA | **SOSTENIDA por mecanismo compartido**, no por efecto directo (no se ejecutó un `TAP`: habría disparado un tap real) |
| H3 | El parche no rompe el registro de los receptores | si aparece `Error registrando`, FALLA | **SOSTENIDA** |
| H4 | El parche no altera ninguna decisión de trading | si toca lógica de trading, FALSA | **SOSTENIDA** |

---

## 4. Límite declarado de alcance

**El fix NO cierra a un emisor privilegiado.** Medido: `root` (UID 0) **sigue** pudiendo rearmar el
bot. Por tanto **no** quedan cubiertos:

- **Magisk / `su`** (instalado en el dispositivo: `com.topjohnwu.magisk`).
- **Shizuku** (`moe.shizuku.privileged.api`), que puede actuar con shell o root.
- **Termux con `su`** (`com.termux`, con el `claude-server-commander` detectado en R15-A).
- **ADB con `root`** o cualquier build con `adbd` privilegiado.

El fix protege contra **apps de terceros no privilegiadas**, que era el vector con evidencia
(cualquier app instalada podía rearmar el bot). **No** protege contra el propio operador ni contra
software con root: eso es exactamente lo que el control positivo demuestra, y es el
comportamiento **deseado** — si root no pudiera, se perdería el control operativo.

**Consecuencia para la investigación:** el emisor del rearme de R14 **sigue NO ATRIBUIDO**, y entre
los candidatos no descartados están precisamente los emisores privilegiados.
