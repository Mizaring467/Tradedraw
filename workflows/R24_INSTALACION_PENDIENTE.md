# R24 — INSTALACIÓN PENDIENTE (EJECUTAR EN EL HUECO ENTRE VENTANAS)

**Estado:** PENDIENTE DE EJECUCIÓN
**Motivo del aplazamiento:** REGLA 14 — prohibido correr Gradle durante la captura de una ventana.

---

## 1. POR QUÉ ESTÁ APLAZADO

La REGLA 14 prohíbe ejecutar Gradle (build, tests, `assembleDebug`) mientras hay una ventana de captura en curso. `assembleDebug` levanta la JVM de Gradle, consume CPU y puede pausar el hilo que atiende `/status` en el teléfono vía adb. En una ventana de 20 min eso contamina la telemetría.

Este documento deja el procedimiento listo para ejecutarlo **en el hueco** entre ventanas, cuando no haya captura activa.

---

## 2. QUÉ SE VA A COMPILAR

Los cambios de R23K **no modifican la lógica de la estrategia**. Ningún archivo de `app/src/main/java/` cambió en esta fase:

```
git diff --stat 8cdb61f HEAD -- app/src/main/java/   -> vacío
```

Lo que cambió hasta ahora:

| Archivo | Tipo | Toca la app |
|---|---|---|
| `scripts/validation/test_action_telemetry.py` | tests Python | no |
| `scripts/validation/replay_v2_tick.py` | replay offline | no |
| `scripts/validation/compare_live_replay_v2.py` | comparador offline | no |
| `workflows/PREREGISTRO_R23K.md` | documento | no |

**Consecuencia:** si no hay cambios en `app/`, **no hay nada que compilar para R23K**. El build `8cdb61f` sigue siendo el vigente y el Cubo 2 no se rompe.

Un build de R24 solo tiene sentido si se decide cambiar algo en la app (p.ej. telemetría nueva de estado de orden). Esa decisión no está tomada.

---

## 3. PROCEDIMIENTO, SI SE DECIDE COMPILAR

Ejecutar **solo** cuando no haya monitor corriendo. Verificar primero:

```bash
ps aux | grep live_monitor | grep -v grep     # debe salir vacío
curl -s http://localhost:8080/status | python -c "import json,sys; d=json.load(sys.stdin); print(d['risk']['orderState'], d['risk']['hasPendingTrade'])"
# debe imprimir: NINGUNA False
```

Luego:

```bash
cd ~/Tradedraw
./gradlew.bat testDebugUnitTest        # 479 tests deben pasar
./gradlew.bat assembleDebug
adb -s UGU8KFUKM7E6CE8D install -r app/build/outputs/apk/debug/app-debug.apk
adb -s UGU8KFUKM7E6CE8D shell am start -n com.example.tradedraw/.MainActivity
# devolver el broker al primer plano
adb -s UGU8KFUKM7E6CE8D shell monkey -p com.marketly.trading -c android.intent.category.LAUNCHER 1
```

Verificar después:

```bash
curl -s http://localhost:8080/status
# gitSha       = <sha nuevo>
# health.state = READY, reasons []
# isDemoAccount= true
```

**Si el reinicio del servicio devuelve el submode a `CONSERVATIVE`, reenviar:**
```bash
curl -s "http://localhost:8080/submode?name=YOLO"
```

Ese reseteo está documentado en `R23J_MANIFEST.md` §6 y ocurre en cada instalación.

---

## 4. CHECKLIST DE CIERRE

- [ ] Sin monitor corriendo al iniciar
- [ ] `testDebugUnitTest` verde
- [ ] `assembleDebug` exitoso
- [ ] Instalación exitosa
- [ ] App arrancada y broker al primer plano
- [ ] `/status.gitSha` == SHA compilado
- [ ] `/status.health.state` == `READY` con `reasons == []`
- [ ] `/status.isDemoAccount` == `true`
- [ ] `submode` == `YOLO` (reenviar si se reseteó)
- [ ] SHA nuevo anotado en `R24_MANIFEST.md` §1
- [ ] Ventana siguiente arranca con el arnés congelado correspondiente

---

## 5. NOTA SOBRE EL CUBO

Si el build cambia, **el Cubo 2 se cierra** y las ventanas posteriores pertenecen al Cubo R24. No se suman a `N=80`. El cierre del Cubo 2 debe emitirse **antes** del cambio de build, con su `N` final y su veredicto, aunque sea `NO CONCLUYENTE (Muestra insuficiente)`.

Si el build **no** cambia —el escenario más probable dado que R23K no tocó `app/`— este documento se archiva sin ejecutar y el Cubo 2 continúa.
