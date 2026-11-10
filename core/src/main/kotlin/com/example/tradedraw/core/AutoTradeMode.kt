package com.example.tradedraw.core

enum class AutoTradeMode {
    AUTONOMOUS,      // Ejecuta clics automáticos
    SEMIAUTOMATIC,   // Alerta y prepara dibujos, usuario opera
    DISABLED,        // Apagado
    SHADOW           // Ronda 21 (Frente E): Análisis y decisiones reales con decision_id, sin taps ni alteración de saldo/W-L
}
