# R15-A Fase 4 - observacion pasiva
inicio=2026-10-14 12:51:59 duracion=1500s intervalo=10s

[12:51:59] >>> CAMBIO DE MODO: None -> DISABLED | orderState=NINGUNA pend=False taps=0 bal=0.0
[12:52:20] >>> CAMBIO DE MODO: DISABLED -> AUTONOMOUS | orderState=NINGUNA pend=False taps=0 bal=0.0
[12:52:20] REARME DETECTADO -> devolviendo a DISABLED
[12:52:22] tras desactivar: mode=DISABLED orderState=NINGUNA pend=False
[12:52:42] >>> CAMBIO DE MODO: DISABLED -> AUTONOMOUS | orderState=NINGUNA pend=False taps=0 bal=0.0
[12:52:42] REARME DETECTADO -> devolviendo a DISABLED
[12:52:44] tras desactivar: mode=DISABLED orderState=NINGUNA pend=False
[12:52:54] n=6 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[12:53:55] n=12 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[12:54:56] n=18 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[12:55:56] n=24 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[12:56:57] n=30 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[12:57:57] n=36 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[12:58:58] n=42 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[12:59:58] n=48 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:00:59] n=54 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:01:59] n=60 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:03:00] n=66 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:04:09] n=72 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:05:19] n=78 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:06:28] n=84 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:07:36] n=90 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:08:46] n=96 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:09:54] n=102 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:11:03] n=108 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:12:10] n=114 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:13:17] n=120 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:14:26] n=126 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:15:33] n=132 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX
[13:16:42] n=138 modo=DISABLED os=NINGUNA pend=False taps=0 activo=Z-CRY/IDX

fin=2026-10-14 13:17:03 muestras=139 fallos_bridge=0
cambios_de_modo_observados=3
  ('12:51:59', None, 'DISABLED')
  ('12:52:20', 'DISABLED', 'AUTONOMOUS')
  ('12:52:42', 'DISABLED', 'AUTONOMOUS')

## MODE_CHANGE registrados (con ORIGEN)
  atMs=1792000276616 de=NINGUNO a=DISABLED origen=onCreate(servicio recreado)
  atMs=1792000335778 de=DISABLED a=AUTONOMOUS origen=TradingEngine.modeChangeOrigin:79<TradingEngine.setMode:59<OverlayService.setTradingMode:901<OverlayService$overlayCommandReceiver$1.onReceive:153<LoadedApk$ReceiverDispatcher$Args.lambda$getRunnable$0$LoadedApk$ReceiverDispatcher$Args:1697
  atMs=1792000339741 de=AUTONOMOUS a=DISABLED origen=TradingEngine.modeChangeOrigin:79<TradingEngine.setMode:59<TradeDrawHttpBridge.handleClient:169<TradeDrawHttpBridge.start$lambda$1$lambda$0:48<TradeDrawHttpBridge.$r8$lambda$Ydz80wqhl_6ciJqVN3iPfSx-r88:0
  atMs=1792000355863 de=DISABLED a=AUTONOMOUS origen=TradingEngine.modeChangeOrigin:79<TradingEngine.setMode:59<OverlayService.setTradingMode:901<OverlayService$overlayCommandReceiver$1.onReceive:153<LoadedApk$ReceiverDispatcher$Args.lambda$getRunnable$0$LoadedApk$ReceiverDispatcher$Args:1697
  atMs=1792000362117 de=AUTONOMOUS a=DISABLED origen=TradingEngine.modeChangeOrigin:79<TradingEngine.setMode:59<TradeDrawHttpBridge.handleClient:169<TradeDrawHttpBridge.start$lambda$1$lambda$0:48<TradeDrawHttpBridge.$r8$lambda$Ydz80wqhl_6ciJqVN3iPfSx-r88:0
  atMs=1792000411010 de=DISABLED a=AUTONOMOUS origen=TradingEngine.modeChangeOrigin:79<TradingEngine.setMode:59<OverlayService.setTradingMode:901<OverlayService$overlayCommandReceiver$1.onReceive:153<LoadedApk$ReceiverDispatcher$Args.lambda$getRunnable$0$LoadedApk$ReceiverDispatcher$Args:1697
  atMs=1792000413366 de=AUTONOMOUS a=DISABLED origen=TradingEngine.modeChangeOrigin:79<TradingEngine.setMode:59<TradeDrawHttpBridge.handleClient:169<TradeDrawHttpBridge.start$lambda$1$lambda$0:48<TradeDrawHttpBridge.$r8$lambda$Ydz80wqhl_6ciJqVN3iPfSx-r88:0

estado final: mode=DISABLED