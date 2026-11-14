$files = @(
  "app/src/main/java/com/example/tradedraw/TradingEngine.kt",
  "app/src/main/java/com/example/tradedraw/RiskManager.kt",
  "app/src/main/java/com/example/tradedraw/TradeEventLog.kt",
  "app/src/main/java/com/example/tradedraw/TradeDrawHttpBridge.kt",
  "app/src/main/java/com/example/tradedraw/OverlayService.kt",
  "app/src/test/java/com/example/tradedraw/LiquidationIdempotencyTest.kt"
)
foreach ($f in $files) {
  $t = Get-Content $f -Raw
  $open = ([regex]::Matches($t, '\{')).Count
  $close = ([regex]::Matches($t, '\}')).Count
  Write-Output ("{0} open={1} close={2} delta={3}" -f (Split-Path $f -Leaf), $open, $close, ($open - $close))
}
