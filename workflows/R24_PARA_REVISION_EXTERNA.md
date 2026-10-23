# R24 · PAQUETE PARA REVISIÓN EXTERNA

**Generado:** 2026-10-22T20:52-05:00 · **Rama:** `r10-night` · **HEAD:** `5c6d1d2`
**Estado:** Fases 0, A, B cerradas. Fases C y D **NO EJECUTADAS**. Fase E = este paquete.

---

## 1. Qué revisar, en este orden

| # | Documento | Qué contiene | Por qué importa |
| :--- | :--- | :--- | :--- |
| 1 | [`workflows/rounds/R24_FINAL_REPORT.md`](R24_FINAL_REPORT.md) | Informe completo: fases, cifras, incidentes, NO VERIFICADO, riesgos | Punto de entrada único |
| 2 | [`workflows/R24_INCIDENTE_2_FUERA_DE_VENTANA.md`](R24_INCIDENTE_2_FUERA_DE_VENTANA.md) | 38 trades, −511.000 COP, 3 h 15 min de exposición | Lo más grave de la sesión |
| 3 | [`workflows/R24_RECONCILIACION_FUERA_DE_VENTANA.md`](R24_RECONCILIACION_FUERA_DE_VENTANA.md) | INCIDENTE 1: 1 trade, −100.001,28 COP | Antecedente del mismo patrón |
| 4 | [`workflows/PREREGISTRO_R24.md`](PREREGISTRO_R24.md) | V1–V5, regla de parada, lista cerrada de cambios | Contrato que la Fase D debe cumplir |
| 5 | [`workflows/R24_MANIFEST.md`](R24_MANIFEST.md) | Manifiesto del cubo + §7 artefacto APK | Condición de arranque del cubo R24 |
| 6 | [`workflows/rounds/R23K_FINAL_REPORT.md`](rounds/R23K_FINAL_REPORT.md) | Cierre del Cubo 2 | Cubo anterior, ya cerrado |
| 7 | [`workflows/r24_tasks/A1_result.md`](r24_tasks/A1_result.md) | Recálculo independiente de cubos | Cifras canónicas |
| 8 | [`workflows/r24_tasks/AUDIT_A_result.md`](r24_tasks/AUDIT_A_result.md) | Auditoría de Fase A (17 puntos) | **Termina en FAIL** (puntos 9 y 13). La firma PASS de `82f7289` fue revocada por la re-auditoría de `4f609f3`. Ver `R24_FINAL_REPORT.md` §2.1 |
| 9 | [`workflows/R24_PROGRESO.md`](R24_PROGRESO.md) | Bitácora con T0, sub-pasos e incidentes | Trazabilidad temporal |

---

## 2. Afirmaciones que NO están probadas

Léanse como pendientes, no como resultados:

1. Que la corrección B1 (idempotencia) funcione en el dispositivo. **507 tests verdes no son evidencia de campo.**
2. Que V1–V5 sean evaluables. **Ninguna ventana R24 se ejecutó.** Los cinco criterios están `NO EVALUABLE`.
3. Que el arnés v2 sea útil. Sigue ciego sin S/R reales y sin volcado de memoria; B2/B3 los instrumentan pero no se probaron end-to-end.
4. Que las 6 ventanas con 0–1 trades tengan una causa conocida. **No la tienen.**
5. Que W17 `NO_PROCESADA` esté diagnosticada. **No lo está**; la taxonomía B4 está compilada pero nunca se ejecutó.
6. Que el inflado de contadores de `8cdb61f` fuera ×2 global. **No lo era**; es condicional y de magnitud desconocida.

---

## 3. Lo que un revisor debe intentar romper

| Ataque | Dónde mirar |
| :--- | :--- |
| ¿Las cifras del Cubo 2 salen del script o están escritas a mano? | `python scripts/validation/recompute_cubes.py` vs `A1_result.md` |
| ¿El preregistro R23K es anterior o posterior a las ventanas? | `git log --diff-filter=A -1 --format="%h %ad" -- workflows/PREREGISTRO_R23K.md` vs fecha de `loops/r23h_validation/window_04_clean/trades.csv` |
| ¿El Cubo 2 mezcla builds? | `git diff --stat 8cdb61f 190f596 -- app/` debe estar vacío |
| ¿La estrategia está congelada en R24? | `git diff 8cdb61f..64f78f1 -- app/src/main/java/com/example/tradedraw/TradingEngine.kt RiskManager.kt AdaptiveLearningEngine.kt` |
| ¿Hay credenciales en el historial? | `git log -p 190f596..HEAD \| Select-String "authtoken\|cookie\|secret\|password\|Bearer\|sk-\|ghp_\|AKIA"` |
| ¿El APK corresponde al commit declarado? | Recompilar `64f78f1` y comparar SHA-256; un APK debug no es reproducible byte a byte, así que comparar tamaño y versión |
| ¿Los incidentes están completos? | `loops/r24_incidente2/trade_journal_utf8.csv` filtrado por `1792708277149..1792720007719` debe dar 38 filas, 18W/20L |
| ¿El balance declarado cuadra? | `base_balance` del primer trade del incidente = 38.076.736,00; `settled_balance` del último = 37.565.736,96 |

---

## 4. Decisiones que requieren criterio humano

1. **¿Se emite firma independiente para la Fase A?** La que existía fue revocada (FAIL en puntos 9 y 13). El punto 9 está corregido en `7be78e8`; el 13 es histórico e irreparable. **Nadie ha firmado la Fase A en su estado actual.**
2. **¿Se acepta el Cubo 2 como exploratorio?** Su preregistro es tardío (8 de 19 ventanas previas). El informe lo declara exploratorio; un revisor podría exigir descartarlo entero.
3. **¿Se acepta la independencia «mismo modelo, contexto limpio»?** La regla 0.3 pedía modelos distintos. La herramienta no lo permite. Se declaró como limitación.
4. **¿Se acepta que la Fase C no se ejecutara?** El tope de 6 h y la ausencia de preregistro exploratorio lo impedían. La alternativa era violar la regla 7 (preregistro antes de datos).
5. **¿Se acepta el gasto de −611.001,28 COP en Demo?** Es cuenta Demo, no dinero real. Pero revela que el motor puede quedar armado sin supervisión.
6. **¿Debe añadirse una salvaguarda automática** (p. ej. `AUTONOMOUS` caduca a los N minutos sin monitor)? **No se implementó**: sería un cambio de comportamiento fuera de la lista cerrada del preregistro R24.

---

## 5. Reproducir la verificación

```powershell
cd C:\Users\heidy\Tradedraw

# Cubos
python scripts/validation/recompute_cubes.py

# Tests del arnés
python scripts/validation/test_decisions_sr_columns.py
python scripts/validation/live_monitor_r23_autonomous.py --test

# Suite Android (NO durante ventana de captura)
.\gradlew.bat test --no-daemon --no-configuration-cache

# Estado del dispositivo
adb devices
adb forward tcp:8080 tcp:8080
curl.exe -s http://127.0.0.1:8080/status
```

**Nunca** usar `http://192.168.1.x:8080`: el bridge solo escucha en loopback.

---

## 6. Resumen en cinco líneas

1. **Cubo 2 cerrado:** WR 50,85 %, Wilson [38,44 ; 63,16], breakeven 54,64 % → **SIN EVIDENCIA DE EDGE**, y el criterio de N=80 era matemáticamente inalcanzable.
2. **APK R24 compilado y verificado** (507 tests verdes, SHA-256 `6E1B0684…0A35`), **no instalado**.
3. **V1–V5 = `NO EVALUABLE`** los cinco: no hubo ventanas.
4. **Dos incidentes de operación:** 39 trades fuera de ventana, −611.001,28 COP en Demo, por activación humana de `AUTONOMOUS` sin monitor.
5. **Falta:** instalar R24, 4 ventanas de 20 min, evaluar V1–V5 con el script canónico, y sondear `/status` cada ≤ 10 min.
