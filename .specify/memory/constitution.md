# TradeDraw Constitution · Principios Fundamentales del Sistema

## Core Principles

### I. Preservación Estricta de Capital & Frenos de Mano (Risk First)
El objetivo primordial de TradeDraw antes de buscar rentabilidad es **proteger el balance contra descapitalización catastrófica**:
- **Stop-Loss por Racha Terminal**: Una vez alcanzado el límite de pérdidas consecutivas (ej. 3 o 4 L), el motor entra en estado de parada dura no reanudable automáticamente. Requiere intervención explícita o reinicio de sesión supervisado.
- **Suelo de Equity Absoluto**: Se define un balance mínimo inviolable (en COP). Si el equity actual cae por debajo, cualquier intento de orden es bloqueado de inmediato con log de nivel ERROR.
- **Trailing Profit Lock**: Al alcanzar un pico de ganancias en la sesión, si el equity retrocede un porcentaje configurable (ej. 35%), se bloquea la operativa para asegurar las ganancias acumuladas.
- **Control Estricto de Martingala**: Prohibida la martingala descontrolada. Límite máximo de nivel (M1 o M2) con cooldown mandatorio de desaceleración psicológica/mercado.

### II. Generación de Señales por Ventaja Matemática Real (Anti-Vibe / Anti-Ruido)
Prohibido el trading por intuición o condiciones algebraicamente inertes:
- **S/R Dinámico por Pivotes & Fractales**: Los soportes y resistencias deben derivarse de cúmulos de máximos/mínimos reales (toques validados), jamás como variables complementarias dependientes (distS + distR == 1.0) de una caja estática.
- **Filtro Anti-Choppiness Cuantitativo Obligatorio**: Si el Choppiness Index (CHOP) es >= 61.8%, existe cluster de dojis o micro-rango lateral, el sistema VETA toda entrada de continuación. Este filtro no se desactiva en ningún submodo.
- **Filtro Anti-Sobreextensión**: Veto explícito a compras CALL en techos históricos o ATH, y veto a ventas PUT en suelos ATL o sobre soportes directos (riesgo de rebote violento).
- **Timing Sniper (:58s - :02s)**: La expiración de binarias en broker móvil es a segundo :00 de vela fija. Toda orden debe dispararse en la ventana milimétrica de apertura de vela.

### III. Integridad de Ejecución & Cero Operaciones Fantasma
- **Distinción Estricta entre VOID y LOSS**: Si una orden no es acreditada ni tomada por el broker (latencia, clic fallido, desincronización), se cataloga como VOID / NO_EJECUTADO. No incrementa la racha de pérdidas ni escala martingala. Si ocurren >= 3 VOIDs consecutivos, parada dura por fallo de enlace.
- **Verificación Bidireccional de Accesibilidad**: Coordenadas de disparo calibradas independientemente para Portrait y Landscape. Disparo validado mediante dispatchGesture.
- **Headless WebSocket de Ultra-Baja Latencia**: Recepción de ticks nativa en < 15ms. Heartbeat activo y watchdog de reconexión si no hay ticks por > 3 segundos.

### IV. Validación Basada en Pruebas (Test-Driven & Sin Regresiones)
- **Suite de Pruebas Unitaria Mandatoria**: Cada filtro de riesgo, lógica cuantitativa y cálculo matemático debe contar con cobertura de tests en JUnit.
- **Cero Regresiones en CI**: El comando `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` debe pasar al 100% (todas las pruebas en verde) antes de cualquier commit o despliegue.

### V. Integración Continua y Despliegue Directo a Producción
- **Flujo Directo a main**: Commits directos a main para activar el workflow de GitHub Actions.
- **Instalación Inmediata por ADB**: Ante cada compilación exitosa, el APK de depuración se transfiere e instala de forma automática en el dispositivo conectado (adb install -r).

## Gobernanza y Cumplimiento
Esta constitución prevalece sobre cualquier decisión heurística. Todo cambio arquitectónico o de lógica de trading debe satisfacer estas restricciones antes de ser considerado listo para operar con dinero real.

**Versión**: 1.0.0 | **Ratificada**: 2026-09-15 | **Autor**: TradeDraw Quantitative Team
