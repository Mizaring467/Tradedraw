package com.example.tradedraw.core

enum class AutoTradeStrategy {
    AUTO_ADAPTIVE,          // Modo Automático Total: Multi-Estrategia Adaptativa (Recomendado)
    MT_MASTER_COMBO,        // Master Traders: Combo Acción del Precio (Mayor Probabilidad)
    MT_FALSE_BREAKOUT,      // Master Traders: Falso Rompimiento / Trampa en S/R
    MT_ENGULFING_SR,        // Master Traders: Patrón Envolvente en S/R
    MT_REJECTION,           // Master Traders: Mechas de Rechazo en S/R
    MT_CHOQUE_PULLBACK,     // Master Traders: Choque de Máximos/Mínimos (Breakout-Retest)
    MT_3_VELAS_AGOTAMIENTO, // Master Traders: Patrón 3 Velas y Agotamiento de Tendencia
    COLOR_TREND,            // Seguidor de Color / Tendencia
    STRIKE_BREAKOUT,        // Ruptura de Strike / Nivel
    AI_REMOTE,              // Análisis Remoto IA
    SUPPORT_RESISTANCE,     // Soportes y Resistencias Clásico
    CANDLE_PATTERNS,        // Patrón de Velas y Martillo
    TREND_FOLLOWING,        // Seguidor de Tendencia
    COMBINED                // Doble Confirmación
}
