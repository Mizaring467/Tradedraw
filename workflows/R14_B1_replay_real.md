# B1 - Replay del detector sobre ticks REALES (K=0.25, N=30 s)

Reimplementacion exacta de `MarketInoperabilityDetector.kt` sobre `data/ticks/*`, con el
guard de la mediana (enmienda 1 del preregistro). El test sintetico comprueba la logica;
esto comprueba que los umbrales elegidos producen una tasa de avisos sensata con datos reales.

> **Nota:** una version anterior de esta medicion dio 0 avisos en los cuatro activos. Era un
> error del modelo de medida, no del detector: el modelo reseteaba el contador de avisos al
> cruzar un hueco de feed y el hueco de 3.700 s de ETH borraba los avisos previos. El codigo
> Kotlin no resetea `activationCount` en `reset()`. Esta tabla es la correcta.

- ETHUSD-OTC: 34260 ticks, 8.34 h, R no-nan 34245
- BTCUSD-OTC: 48910 ticks, 7.27 h, R no-nan 48895
- SOLUSD-OTC: 32228 ticks, 7.27 h, R no-nan 32213
- Z-CRY/IDX: 458 ticks, 0.24 h, R no-nan 443

## Avisos por hora

| K | N(s) | ETHUSD-OTC | BTCUSD-OTC | SOLUSD-OTC | Z-CRY/IDX | agregado/h |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0.25 | 20 | 6 | 12 | 2 | 0 | 0.865 |
| 0.25 | 30 | 2 | 1 | 0 | 0 | 0.130 **← preregistrado** |
| 0.25 | 45 | 0 | 0 | 0 | 0 | 0.000 |
| 0.25 | 60 | 0 | 0 | 0 | 0 | 0.000 |
| 0.35 | 20 | 17 | 21 | 2 | 0 | 1.731 |
| 0.35 | 30 | 2 | 5 | 0 | 0 | 0.303 |
| 0.35 | 45 | 0 | 0 | 0 | 0 | 0.000 |
| 0.35 | 60 | 0 | 0 | 0 | 0 | 0.000 |
| 0.5 | 20 | 35 | 31 | 41 | 0 | 4.629 |
| 0.5 | 30 | 8 | 10 | 16 | 0 | 1.471 |
| 0.5 | 45 | 0 | 2 | 2 | 0 | 0.173 |
| 0.5 | 60 | 0 | 0 | 0 | 0 | 0.000 |
| 0.6 | 20 | 50 | 36 | 54 | 0 | 6.057 |
| 0.6 | 30 | 18 | 13 | 22 | 0 | 2.293 |
| 0.6 | 45 | 3 | 4 | 4 | 0 | 0.476 |
| 0.6 | 60 | 1 | 0 | 0 | 0 | 0.043 |
| 0.75 | 20 | 67 | 46 | 61 | 0 | 7.528 |
| 0.75 | 30 | 40 | 16 | 25 | 0 | 3.504 |
| 0.75 | 45 | 10 | 5 | 5 | 0 | 0.865 |
| 0.75 | 60 | 2 | 0 | 0 | 0 | 0.087 |
| 0.9 | 20 | 82 | 66 | 61 | 0 | 9.042 |
| 0.9 | 30 | 52 | 28 | 27 | 0 | 4.629 |
| 0.9 | 45 | 20 | 11 | 5 | 0 | 1.558 |
| 0.9 | 60 | 5 | 2 | 0 | 0 | 0.303 |

## Lectura

- Los valores preregistrados (`K=0.25`, `N=30 s`) disparan **3 veces en 23,11 h** de datos
  reales: 2 en ETH, 1 en BTC, 0 en SOL y 0 en Z-CRY/IDX. La tasa agregada es **0,13/h**,
  muy por debajo del techo de 1 aviso/hora declarado en el preregistro.
- Ningun umbral mas laxo se adopta: `K=0.5, N=20` daria 4,6 avisos/h, que es ruido, y
  optimizar sobre el mismo dia que se eligio el umbral seria sobreajuste.
- **Limitacion declarada:** un solo dia de datos (12-oct), ~7-8 h por activo. No hay
  evidencia de variacion entre dias ni de comportamiento en fin de semana. La tasa es un
  orden de magnitud, no una garantia. Z-CRY/IDX solo aporta 0,24 h: su 0 no es informativo.