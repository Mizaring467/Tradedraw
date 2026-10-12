TAREA — RONDA 7 · AUDITORÍA DE MIGRACIÓN DE ACTIVO (agy)

Repo: C:\Users\heidy\Tradedraw. NO edites código. Audita y reporta.

=== Contexto ===
Se migró el activo de `Z-CRY/IDX` (línea muerta, rango rel 4e-10) a `ETHUSD-OTC` (Ethereum OTC,
rango rel 6.2e-5, ×160.000 más volátil). Descubriste el catálogo de 147 activos en assets.preferences_pb.

=== Cambios a auditar ===
1. `BinomoWebSocketClient.kt` (~L591-597): los umbrales de micro-impulso se hicieron RELATIVOS a la
   escala del precio (antes absolutos, válidos solo para IDX):
   impulseThreshold = isIdx ? 0.000008 : scale*0.00008
   minVelThreshold = isIdx ? 0.000003 : scale*0.00003
2. `TradeDrawHttpBridge.kt`: nuevo endpoint `/asset?name=<RIC>` que llama `updateActiveAsset`.
3. `AutoTradeAccessibilityService.kt` (~L498-506): el sync de accesibilidad mapea el texto de pantalla
   a un RIC. NOTA: "Ethereum (OTC)" NO está en el when{}, así que cae al else (`barraNombres.replace(" ","")`),
   que produciría "Ethereum(OTC)" — un RIC INVÁLIDO (el correcto es ETHUSD-OTC). HAY QUE CORREGIR ESE MAPEO.

=== PREGUNTAS ===
A) ¿Los umbrales relativos de impulso (0.008% y 0.003% del precio) son razonables para ETH (~1046)?
   ¿Detectarán micro-impulsos reales sin sobre-reaccionar al ruido de OTC?
B) ¿Qué OTROS lugares del código asumen la escala de IDX (~641) o usan literales absolutos que
   romperían con ETH (~1046) o SOL (~32.7)? Lista archivo:línea.
C) El mapeo de accesibilidad: propón la tabla correcta de nombres de pantalla -> RIC para al menos:
   Crypto IDX, Ethereum (OTC), Bitcoin (OTC), Solana (OTC), Litecoin, XAU/USD.
D) ¿El endpoint /asset es seguro (no rompe el estado si el RIC no existe/mercado cerrado)?

=== ENTREGABLE (español, máx 25 líneas) ===
- Veredicto PASS/FAIL de la migración
- Lista de puntos que asumen escala IDX y hay que revisar (archivo:línea)
- Tabla propuesta de mapeo pantalla->RIC
- Riesgos y correcciones
