# Task 4 Brief: HUD Transparency & Collapsible UI Controls (OverlayService.kt & layout_trading_hud.xml)

## Objective
Implement a clean, non-invasive HUD interface with multi-level opacity cycling (100% -> 60% -> 30%), a collapsible details drawer, and an updated strategy selection dialog containing all 6 Master Trader strategies in `OverlayService.kt` and `layout_trading_hud.xml`.

## Target Files
- Modify: `app/src/main/res/layout/layout_trading_hud.xml`
- Modify: `app/src/main/java/com/example/tradedraw/OverlayService.kt`

## Requirements
1. **Layout Redesign (`layout_trading_hud.xml`)**:
   - 2-line compact header containing:
     - Drag handle / App Title (`TradeDraw AI`)
     - Strategy Chip (`MT Master Combo`)
     - Opacity Toggle Button (`hud_btn_opacity`)
     - Collapse / Expand Toggle Button (`hud_btn_collapse`)
     - Close Button
   - Live Signal Strip:
     - Signal Badge (`CALL`, `PUT`, `ESPERANDO`)
     - Signal Thermometer / Strength percentages
     - Win / Loss counter and Martingale indicator
   - Collapsible Details Container (`hud_details_container`):
     - Strategy status & reason text
     - Quick Action Buttons (+WIN, +LOSS, ESTRATEGIA, CALIBRAR, RIESGO, DEBUG)
2. **OverlayService UI Logic**:
   - `cycleHUDOpacity()`: Transitions `hudAlpha` through `1.0f -> 0.60f -> 0.30f -> 1.0f` and applies alpha to root card.
   - `toggleHUDCollapse()`: Toggles `hud_details_container` between `VISIBLE` and `GONE`.
   - `showStrategySelectionDialog()`: Dialog displaying all Master Trader strategies:
     - `MT_MASTER_COMBO` ("🎯 Master Combo (Recomendado)")
     - `MT_FALSE_BREAKOUT` ("⚡ Falso Rompimiento / Trampa S/R")
     - `MT_ENGULFING_SR` ("⚡ Vela Envolvente en S/R")
     - `MT_REJECTION` ("⚡ Mechas de Rechazo en S/R")
     - `MT_CHOQUE_PULLBACK` ("⚡ Choque / Pullback tras Rompimiento")
     - `MT_3_VELAS_AGOTAMIENTO` ("⚡ Agotamiento de 3 Velas")
     - `COLOR_TREND` ("📈 Tendencia por Color")
     - `STRIKE_BREAKOUT` ("🎯 Rompimiento de Strike Price")
