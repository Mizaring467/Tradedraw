package com.example.tradedraw.core

enum class AutonomousSubMode {
    CONSERVATIVE,    // Con gestión de riesgo estándar (Stop Loss, Take Profit, Cooldown)
    YOLO,            // Sin Stop Loss ni límites, operativa continua y desatendida
    SNIPER           // Modo Francotirador: Stake plano (M0), SL 2 derrotas, TP 2 victorias, máx 3 ops por sesión
}
