# Auditoría de validez de simulación — TradeDraw

Fecha de auditoría: 2026-10-03

## Conclusión

Los resultados del modelo cuantitativo no deben presentarse como estimaciones fiables del comportamiento real. La documentación actual describe un modelo sintético, mientras que el rendimiento empírico proviene de un journal distinto. No son la misma fuente de datos y no deben mezclarse como si fueran una validación equivalente.

## Procedencia identificada

- scripts/quant_crypto_idx_model.py: el informe loops/CRYPTO_IDX_QUANT_RESEARCH.md declara una simulación de 10.000 velas sintéticas de 60 segundos y 700.000 micro-ticks generados por un modelo de difusión con saltos y regímenes simulados.
- loops/journal/quant_research_summary.json: contiene estadísticas calculadas a partir de journals registrados por la aplicación. Es telemetría observada, no la misma serie sintética que alimenta el modelo.
- El informe publicado muestra muestras OOS por ventana de apenas 2 a 9 operaciones en varias ventanas. Esas muestras son demasiado pequeñas para respaldar conclusiones fuertes sobre rendimiento futuro.
- La documentación disponible no demuestra que los ticks sintéticos reproduzcan la distribución, latencia, precio de ejecución, expiración y resultados del entorno real. Esa equivalencia no debe darse por supuesta.

## Salvaguardas para los informes

1. Mostrar siempre la etiqueta de origen: SINTÉTICO, JOURNAL OBSERVADO o NO VERIFICADO.
2. No describir resultados sintéticos como rendimiento real ni como prueba de rentabilidad.
3. No emitir un veredicto de validación cuando falten datos de origen, tamaño de muestra suficiente o trazabilidad de cada observación.
4. Mantener separados los conjuntos sintéticos y observados; no combinarlos para aumentar artificialmente el tamaño de muestra.
5. Registrar las limitaciones junto a las métricas, no solo en una nota al final.
6. Tratar cualquier discrepancia entre simulación y observación como un fallo de validez del modelo que requiere investigación, no como evidencia para cambiar reglas operativas.

## Alcance de esta auditoría

Este documento es una salvaguarda de integridad y comunicación de resultados. No modifica estrategias, señales, parámetros operativos ni automatización. Antes de considerar válida una simulación, debe existir evidencia independiente y reproducible de que los datos de entrada y las condiciones simuladas representan adecuadamente el sistema que se pretende estudiar.
