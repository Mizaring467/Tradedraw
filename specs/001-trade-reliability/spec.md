# Feature Specification: Confiabilidad y Seguridad Cuantitativa de TradeDraw para Operativa Real

**Feature Branch**: `001-trade-reliability`
**Created**: 2026-09-15
**Status**: Ready for Implementation
**Input**: Planificación exhaustiva para aumentar la confiabilidad técnica y financiera de TradeDraw antes de operar con dinero real.

## User Scenarios & Testing

### User Story 1 - Freno de Mano Terminal y Protección de Capital Inviolable (Priority: P1)
Como operador que busca confiar su capital real a TradeDraw, requiero que el bot se detenga de manera permanente y no se reanude solo cuando ocurra una racha de pérdidas adversa o cuando el balance caiga por debajo de un umbral de seguridad, evitando descapitalizaciones rápidas.

**Why this priority**: Es la condición sine qua non para el trading real. Una estrategia sin parada dura puede perder el 100% de la cuenta durante una racha atípica de mercado.

**Independent Test**: Simular 3 pérdidas consecutivas y verificar que `canExecuteTrade()` retorna `false` indefinidamente aunque transcurran más de 10 minutos de reloj.

**Acceptance Scenarios**:
1. **Given** un stop loss por racha configurado en 3 pérdidas, **When** se acumulan 3 derrotas seguidas en el journal, **Then** el motor entra en estado `TERMINAL_HALT`, el HUD muestra `[PARADA DURA SL]` en rojo brillante y ninguna orden es enviada a Accesibilidad.
2. **Given** un balance inicial de 40.2M COP y un suelo de equity de 40.0M COP, **When** el balance cae a 39.99M COP, **Then** `canExecuteTrade()` retorna `false` permanentemente con log de alerta crítica.
3. **Given** una orden enviada donde el broker no cambia el balance tras 62 segundos, **When** se detecta diferencia de balance = 0.0, **Then** se marca como `VOID / NO_EJECUTADO`, NO incrementa la racha de pérdidas y NO escala el nivel de martingala.

---

### User Story 2 - Filtro Cuantitativo Anti-Choppiness y Anti-Sobreextensión (Priority: P1)
Como operador cuantitativo, requiero que el motor vete entradas de continuación en mercados laterales sucios (mercados de rango comprimido o clusters de dojis) y en zonas extremas sobreextendidas (techos ATH o suelos ATL).

**Why this priority**: Más del 70% de las pérdidas en opciones binarias de 60 segundos ocurren por operar rompimientos falsos en consolidaciones sin volumen o por comprar en el último segundo de una vela sobreextendida que se agota de inmediato.

**Independent Test**: Introducir una serie de 10 velas con Choppiness Index = 65.0% y verificar que cualquier señal generada por las estrategias 1 a 7 sea vetada con el mensaje `"🛡️ Filtro Anti-Chop Activo"`.

**Acceptance Scenarios**:
1. **Given** un mercado con CHOP >= 61.8% o rango relativo < 40% del promedio de 20 velas, **When** se genera una señal CALL o PUT, **Then** la orden es vetada inmediatamente tanto en modo Conservador como en modo YOLO.
2. **Given** el precio cotizando en un nuevo máximo histórico (ATH) o a menos de 0.15 de distancia de la resistencia más alta, **When** surge una señal de continuación CALL, **Then** el motor veta la compra por sobreextensión alcista y espera retroceso.

---

### User Story 3 - Niveles de Soporte y Resistencia Dinámicos por Pivotes Reales (Priority: P2)
Como analista técnico, requiero que los niveles de Soporte y Resistencia se basen en cúmulos de pivotes (fractales de Williams y zonas con múltiples toques verificados), permitiendo que `distanceToSupport` y `distanceToResistance` sean variables independientes reales (y no una suma forzada a 1.0).

**Why this priority**: La invariante anterior (`distS + distR == 1.0`) provocaba que al caer el precio el soporte se redefiniera en el precio actual, comprando cuchillos en caída.

**Independent Test**: Verificar mediante prueba unitaria un escenario de mercado donde tanto `distanceToSupport` como `distanceToResistance` sean simultáneamente menores a 0.25 (precio comprimido en canal estrecho) o simultáneamente mayores a 0.60 (precio en medio del rango).

**Acceptance Scenarios**:
1. **Given** una serie histórica de 30 velas, **When** se identifican pivotes fractales de 5 períodos, **Then** se agrupan en niveles por tolerancia de precio y se priorizan los niveles con >= 2 toques históricos.
2. **Given** un precio rompiendo un soporte con volumen fuerte, **When** el soporte anterior es traspasado, **Then** pasa a registrarse como resistencia dinámica por principio de polaridad.

---

### User Story 4 - Telemetría de Latencia y Ejecución Confiable (Priority: P2)
Como usuario, requiero que cada orden disparada tenga verificación de entrega al broker móvil vía servicio de accesibilidad, midiendo latencia de red de WebSocket (< 25ms) y confirmando el registro de la orden sin jank en el overlay.

**Why this priority**: Evita deslizamientos de segundo (:00) y garantiza que el botón en pantalla sea presionado en la coordenada exacta.

**Independent Test**: Ejecutar ciclo de disparo de prueba en Demo y medir el tiempo desde la señal hasta el gesto de accesibilidad (< 40ms totales).

---

### User Story 5 - Replay y Backtesting Estadísticamente Robusto (Priority: P3)
Como investigador, requiero un sistema de backtesting que tome el journal real de trades y valide out-of-sample la esperanza matemática (EV > 0.05 por trade con payout del 82%), calculando el tamaño de muestra mínimo para significancia estadística.

**Acceptance Scenarios**:
1. **Given** el historial real de 35 trades, **When** se corre la simulación con slippage y payout de 82.2%, **Then** el simulador reproduce la trayectoria real sin sesgo de anticipación.
2. **Given** una configuración candidata, **When** se evalúa en out-of-sample de 100 eventos, **Then** debe demostrar una tasa de acierto (Win Rate) >= 56.0% (superando el breakeven de 54.9%).

## Requisitos Funcionales

- **FR-001**: El sistema DEBE mantener un estado de parada dura en `RiskManager` que bloquee cualquier trade cuando `currentLossStreak >= stopLossStreak` hasta un reinicio explícito del usuario.
- **FR-002**: El sistema DEBE suspender la operativa permanentemente si el saldo cae por debajo de `absoluteEquityFloor` (por defecto 40,000,000 COP en Demo).
- **FR-003**: El sistema DEBE clasificar trades con cambio de saldo = 0.0 tras expiración como `VOID` y detener el bot si ocurren 3 `VOID` consecutivos.
- **FR-004**: El sistema DEBE calcular el Choppiness Index (CHOP) matemáticamente y vetar trades cuando CHOP >= 61.8%.
- **FR-005**: El sistema DEBE vetar compras en techos ATH (distancia a resistencia < 0.15) y ventas en suelos ATL (distancia a soporte < 0.15).
- **FR-006**: El sistema DEBE garantizar que `distS` y `distR` sean métricas independientes basadas en ATR y pivotes reales.
- **FR-007**: El sistema DEBE ejecutar trades estrictamente en la ventana sniper (:58s a :02s de vela).

## Criterios de Éxito Medibles

- **SC-001**: 100% de los tests unitarios en verde (>= 95 tests pasando en CI sin excepciones).
- **SC-002**: 0 órdenes fantasma procesadas como pérdidas en el journal en vivo.
- **SC-003**: 0 trades ejecutados durante fases de Choppiness Index >= 61.8%.
- **SC-004**: Win Rate medido en Demo >= 56.5% en una muestra continua de >= 40 operaciones.
- **SC-005**: Latencia de disparo desde señal hasta gesto táctil < 50 milisegundos.
