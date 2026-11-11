# Verificación de traslado — 2026-11-11

Se compararon cuerpos completos antes/después excluyendo únicamente líneas de package/import y líneas vacías. Igualdad exacta comprobada por script antes de escribir. Parámetros e interiores sin cambios. VisionAnalysisResult y enums/DTO contiguos se separan de VisionAnalyzer conservando literalmente el bloque.

| Archivo | SHA-256 del cuerpo conservado |
|---|---|
| SyntheticCandleEngine | c0c0c8796b15170b87033938e9e0739616d4fb1555ec5b9b5a2af9d9fda7385d |
| MarketRegimeClassifier | a54d0658d091d473170cadcd4625ec518f8aa834b80f2dba2c07d83ede8f226b |
| AdaptiveLearningEngine | 2b9330962620dae1610c633d8114370ba1b928cefab2d6a2b97fe87bc5374319 |
| MarketInoperabilityDetector | e7ede224530499663f2da8fb76defc4ae80b64b81ee6842673b353e73432892c |
| TradeEventLog | fbf47e42c7f377aa9c844668970c8dba27c5c5dc242f8717130a40dca35a4182 |
| ModeEventJournal | 82fbcf44785cb90e99954e692c31a659a910e693b9987d69f78a730bb047ebb9 |
| CrashLogger | 5e2ae5f6b2955671329c3ec532d8e2d75d6b3c0df2b1cd15da70fba3e97d110c |

## Reglas compartidas con research

Las funciones calcularPoderesDeMercado y motivoVetoPorAgotamientoDeCompra se trasladan a strategy conservando su cuerpo exacto; solo cambia la referencia al contenedor de la constante70, comprobada por sustitución inversa. App conserva delegados de compatibilidad y research llama a StrategyReplayPort. decisionId usa directamente la misma función core a la que delegaba TradingEngine. SHA-256 de cuerpos originales: fe8139b70edc15b533fd6a3bd412157092695d63da566618721f5ece47e6861c y 00efc763b5b621a9437a4944ad34129afbeaa787df8f93cc49d22c25caa37c42. No se duplica la fórmula ni se recalibran valores.
