# TAREA A1: SCRIPT DE RECÁLCULO INDEPENDIENTE DE CUBOS
## Asignado a: OPENCODE (`w1:pB`)
## Orquestador: AGY (`w1:p1`)
## Rama: r10-night · Repositorio: C:\Users\heidy\Tradedraw

### OBJETIVO
Crear y ejecutar el script versionado `scripts/validation/recompute_cubes.py` de forma completamente determinista e independiente.
REGLA ESTRICTA: OPENCODE calcula SIN leer antes las cifras de ningún informe previo. Lee EXCLUSIVAMENTE los archivos crudos de datos.

### ENTRADAS
1. Todos los archivos `loops/**/window_*/trades.csv` (y subdirectorios de validación: `r23c_yolo_validation`, `r23h_validation`, `r23i_validation`, `r23j_validation`, etc., que contengan las ventanas de estudio W01 a W11).
2. El trade de frontera de W10 documentado con sus datos crudos en `workflows/rounds/R23J_W10_BORDER_TRADE_RECONCILIATION.md`:
   - `tradeId`: `205eed81-fd3b-47d7-8efa-4b3fb7058499`
   - `decisionId`: `0d6d32fa-eead-3b4f-996c-731a1a2b40f8`
   - `timestamp_despacho`: `1792385045288` (dentro de W10, 23:44:05)
   - `action`: `BUY`
   - `resultado`: `LOSS`
   - `delta`: `-100001.28` COP DEMO
   - `saldo_pre`: `39103736.32`
   - `saldo_post`: `39003735.04`
   (Nota: este trade aparece en el `trades.csv` de W11 debido al cierre abrupto del monitor v1 a los 20:00 sin gracia de liquidación. Debe pertenecer a W10 por su timestamp de despacho y NO contarse dos veces).

### ESPECIFICACIÓN DEL SCRIPT `scripts/validation/recompute_cubes.py`
- Python 3 determinista.
- Semilla de bootstrap fija: `20261019`. 10.000 remuestreos.
- Deduplicar estrictamente por `trade_id`.
- Asignar cada trade a su ventana por timestamp de DESPACHO.
- Verificación de cadena de saldos:
  - Saldo final de cada ventana = saldo inicial de la siguiente ventana (salvo el trade de frontera de W10 documentado).
  - Suma de deltas = saldo final − saldo inicial.
  - Cualquier hueco o diferencia no explicada debe listarse como `INCIDENTE`.
- Fórmulas:
  - $N = W + L + VOID$
  - $\text{Win Rate (WR)} = W / (W + L)$ (o sobre $N$ si VOID = 0)
  - Intervalo de confianza de Wilson al 95% para proporciones binomiales (fórmula exacta).
  - Breakeven: $\text{BE} = 1 / (1 + \text{payout})$ (82% para W01–W05 $\implies 54.95\%$; 83% desde W06 $\implies 54.64\%$).
  - EV (Expected Value por trade) con IC bootstrap al 95% (percentiles 2.5 y 97.5 con 10.000 remuestreos, seed 20261019).
  - Drawdown máximo de balance por ventana y por cubo.
  - Desglose BUY / SELL por separado para cada ventana y cubo.
- Agrupación por Cubos:
  - **Cubo 1**: Paridad post-congelado (ventanas W07 en adelante).
  - **Cubo 2**: Desempeño homogéneo con veto (W04 en adelante, incluyendo trade de frontera W10).
  - **Cubo 3**: Histórico heterogéneo pre-veto (W01–W03).
- Sensibilidades obligatorias (informativas):
  - Cubo 2 sin W04.
  - Solo W07+ (post-congelado).
  - Solo la 2.ª mitad cronológica de trades.
- Validación con caso sintético:
  - Incluir una función o self-test `--test` que valide el cálculo de Wilson, Bootstrap y agregación con un dataset sintético conocido y verifique su exactitud.

### SALIDAS REQUERIDAS
1. `workflows/rounds/R23K_CUBES_RECOMPUTED.md` (tabla detallada por ventana, resumen por cubos, sensibilidades, auditoría de cadena de saldos).
2. `workflows/rounds/R23K_CUBES_RECOMPUTED.json` (estructura JSON completa con todas las métricas para consumo programático).

### INSTRUCCIONES DE EJECUCIÓN
1. Implementa `scripts/validation/recompute_cubes.py`.
2. Ejecuta los self-tests de validación sintética.
3. Ejecuta el script sobre todos los `trades.csv` de las ventanas W01 a W11 de TradeDraw.
4. Genera los archivos de salida en `workflows/rounds/`.
5. Reporta tu salida y veredicto en tu panel para que AGY y Claude procedan al contraste (A2).
