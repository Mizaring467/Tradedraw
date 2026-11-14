package com.example.tradedraw.core

/**
 * Modo de validación para gobernanza estricta de datasets (FASE 6 R31).
 *
 * VALIDATION_REAL: Modo de evaluación de estrategia real. Preserva todos los filtros,
 * ventanas y cooldowns. Sus resultados computan para métricas de rentabilidad (WR, EV, drawdown).
 *
 * FAST_DEMO: Modo exclusivo de ingeniería de infraestructura (taps, WebSocket, reconciliación,
 * watchdog). Sus resultados NUNCA se mezclan con VALIDATION_REAL ni cuentan para rentabilidad.
 */
enum class ValidationMode {
    VALIDATION_REAL,
    FAST_DEMO
}
