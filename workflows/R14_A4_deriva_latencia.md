# A4 - Deriva de precio por latencia (offline, `data/ticks/*_2026-10-12.csv`)

Punto de equilibrio `p_BE = 1/(1+b)`:

| payout b | `p_BE` | ventaja necesaria sobre 50 % |
| ---: | ---: | ---: |
| 0.80 | 55.56 % | +5.56 pp |
| 0.82 | 54.95 % | +4.95 pp |
| 0.83 | 54.64 % | +4.64 pp |

**Metrica principal: tasa de volteo.** Para cada instante de senal `t`, se compara el
resultado del trade con entrada en `t` (lo que la senal asume) contra el resultado con
entrada en `t + latencia` (lo real, el tap tarda 255-1205 ms). Ambos se liquidan en el
MISMO `t + 60 s`, aislando el efecto de la latencia sobre el precio de entrada.

## ETHUSD-OTC

- Ticks: **34260**, ventana 8.34 h
- Intervalo entre ticks: mediana **897 ms**, p95 907 ms, max 3699935 ms
- Cambios de precio: **7871** en 34260 ticks

### Deriva de precio en la ventana de latencia

| ventana | mediana \|dP\| | p90 \|dP\| | p99 \|dP\| | max \|dP\| | P(dP=0) | P(dP<0) |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0.5 s | 0.0000 | 0.1200 | 0.4700 | 3.0650 | 80.8 % | 9.7 % |
| 1.0 s | 0.0000 | 0.1600 | 0.5200 | 3.0650 | 75.6 % | 12.2 % |
| 1.2 s | 0.0000 | 0.2300 | 0.6100 | 3.0650 | 63.0 % | 18.5 % |

Escala de referencia: |dP| a 60 s (tenencia real) mediana **0.6465**, p90 1.8165

### Tasa de volteo (la latencia cambia el signo del resultado)

| latencia | N senales | volteos | tasa | IC95 % (aprox) |
| ---: | ---: | ---: | ---: | :--- |
| 0.5 s | 34247 | 473 | **1.381 %** | [1.258, 1.505] % |
| 1.0 s | 34247 | 592 | **1.729 %** | [1.591, 1.867] % |
| 1.2 s | 34247 | 928 | **2.710 %** | [2.538, 2.882] % |

## BTCUSD-OTC

- Ticks: **48910**, ventana 7.27 h
- Intervalo entre ticks: mediana **798 ms**, p95 900 ms, max 287964 ms
- Cambios de precio: **23091** en 48910 ticks

### Deriva de precio en la ventana de latencia

| ventana | mediana \|dP\| | p90 \|dP\| | p99 \|dP\| | max \|dP\| | P(dP=0) | P(dP<0) |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0.5 s | 0.0000 | 1.2495 | 12.6617 | 104.4540 | 52.9 % | 23.6 % |
| 1.0 s | 0.0105 | 3.3971 | 14.9845 | 104.4540 | 32.7 % | 33.5 % |
| 1.2 s | 0.1610 | 6.4101 | 20.1821 | 138.8990 | 6.4 % | 46.8 % |

Escala de referencia: |dP| a 60 s (tenencia real) mediana **15.5690**, p90 46.8383

### Tasa de volteo (la latencia cambia el signo del resultado)

| latencia | N senales | volteos | tasa | IC95 % (aprox) |
| ---: | ---: | ---: | ---: | :--- |
| 0.5 s | 48908 | 517 | **1.057 %** | [0.966, 1.148] % |
| 1.0 s | 48908 | 734 | **1.501 %** | [1.393, 1.609] % |
| 1.2 s | 48908 | 1350 | **2.760 %** | [2.615, 2.905] % |

## SOLUSD-OTC

- Ticks: **32228**, ventana 7.27 h
- Intervalo entre ticks: mediana **899 ms**, p95 908 ms, max 288085 ms
- Cambios de precio: **5633** en 32228 ticks

### Deriva de precio en la ventana de latencia

| ventana | mediana \|dP\| | p90 \|dP\| | p99 \|dP\| | max \|dP\| | P(dP=0) | P(dP<0) |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0.5 s | 0.0000 | 0.0100 | 0.0200 | 0.1800 | 86.7 % | 6.7 % |
| 1.0 s | 0.0000 | 0.0100 | 0.0300 | 0.1800 | 81.9 % | 9.1 % |
| 1.2 s | 0.0000 | 0.0100 | 0.0300 | 0.1800 | 73.7 % | 13.2 % |

Escala de referencia: |dP| a 60 s (tenencia real) mediana **0.0400**, p90 0.0900

### Tasa de volteo (la latencia cambia el signo del resultado)

| latencia | N senales | volteos | tasa | IC95 % (aprox) |
| ---: | ---: | ---: | ---: | :--- |
| 0.5 s | 30170 | 282 | **0.935 %** | [0.826, 1.043] % |
| 1.0 s | 30170 | 385 | **1.276 %** | [1.149, 1.403] % |
| 1.2 s | 30170 | 575 | **1.906 %** | [1.752, 2.060] % |

---

## Resumen

| activo | latencia | N | volteos | tasa | IC95 % |
| :--- | ---: | ---: | ---: | ---: | :--- |
| ETHUSD-OTC | 0.5 s | 34247 | 473 | 1.381 % | [1.258, 1.505] % |
| ETHUSD-OTC | 1.0 s | 34247 | 592 | 1.729 % | [1.591, 1.867] % |
| ETHUSD-OTC | 1.2 s | 34247 | 928 | 2.710 % | [2.538, 2.882] % |
| BTCUSD-OTC | 0.5 s | 48908 | 517 | 1.057 % | [0.966, 1.148] % |
| BTCUSD-OTC | 1.0 s | 48908 | 734 | 1.501 % | [1.393, 1.609] % |
| BTCUSD-OTC | 1.2 s | 48908 | 1350 | 2.760 % | [2.615, 2.905] % |
| SOLUSD-OTC | 0.5 s | 30170 | 282 | 0.935 % | [0.826, 1.043] % |
| SOLUSD-OTC | 1.0 s | 30170 | 385 | 1.276 % | [1.149, 1.403] % |
| SOLUSD-OTC | 1.2 s | 30170 | 575 | 1.906 % | [1.752, 2.060] % |
