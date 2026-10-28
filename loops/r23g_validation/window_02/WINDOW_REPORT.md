# INFORME DE AUDITORÍA: R23G — VENTANA 02 DE 10 (CUENTA DEMO, INTERRUPCIÓN POR SEGURIDAD)

**Fecha:** 2026-10-17 03:47:05 UTC  
**Orquestador:** AGY (`w1:p1`)  
**Validador Live Independiente:** OPENCODE (`w1:p7`)  
**Dispositivo Auditado:** Xiaomi Redmi A1 (`UGU8KFUKM7E6CE8D`)  
**Build Identificada:** `versionCode = 18` | `versionName = "1.8-r23f-adaptive"` | `buildId = "R23F-ADAPTIVE"` | `gitSha = "ac6b0b2"`  
**Modo Auditado:** `AUTONOMOUS` | **Submodo:** `YOLO` (100% Cuenta DEMO)  
**Duración:** 8.5 minutos (511 segundos / 41 muestras periódicas)  
**Cierre:** Aborto preventivo fail-closed por modal superpuesto de Binomo ("Way up") que tapó el distintivo "Cuenta Demo".  
**Veredicto Global:** **PASS DE SEGURIDAD (FAIL-CLOSED IMPECABLE)**  

---

## 1. RESUMEN EJECUTIVO Y VEREDICTO DE SEGURIDAD

1. **Activación Rigurosa del Fail-Closed:**
   - Durante 40 muestras consecutivas (400 segundos), `isDemoAccount == true` fue verificado con éxito.
   - En la muestra 41 (segundo 511), la aplicación Binomo desplegó un banner promocional modal ("Way up: Cuanto más opere, más obtendrá") que cubrió la cabecera del gráfico donde reside la etiqueta "Cuenta Demo".
   - El monitor autónomo detectó de inmediato la ausencia de la etiqueta y ejecutó el **apagado de emergencia instantáneo** (`setTradingMode(DISABLED)`).
   - Fondos reales comprometidos: **0 (0.0%)**. Cero órdenes no autorizadas.
2. **Dinámica de Balance en la Ventana:**
   - Saldo inicial: **38,619,735.04 COP**.
   - Saldo final validado: **38,419,737.60 COP** (Δ = -199,997.44 COP, -0.52%).
   - Drawdown máximo en la ventana: **0.52%**.
   - Outliers de telemetría: 0.
3. **Operaciones Reales Ejecutadas:**
   - 2 órdenes BUY ejecutadas y resueltas antes del evento del modal:
     - Trade 1: `ee594696-31ee-4fa6-bb5a-b0c9db2b8f6c` (BUY) $\rightarrow$ **LOSS** (-99,998.72 COP).
     - Trade 2: `2635b07c-a6ff-4bdb-9b1a-37dd3d523cf2` (BUY) $\rightarrow$ **LOSS** (-99,997.44 COP).
   - Wins: 0 | Losses: 2 | Win Rate: **0.00%**.
4. **Protección Preservada en el Bucket 99% (Shadow Pipeline):**
   - 1 evento resuelto en shadow: `BUY #792208285915` (sim=99.0%, score=78.0pts) $\rightarrow$ **COUNTERFACTUAL_LOSS** (-100,000 COP evitados). Cero bloqueos observados en el bucket 98%.

---

## 2. DETALLE DE OPERACIONES REALES (VENTANA 02)

| # | Trade ID | Timestamp | Acción | Balance Post-Entrada | Resultado | Delta Balance |
| :-: | :--- | :---: | :---: | :---: | :---: | :---: |
| **1** | `ee594696-31ee...` | 1792208447812 | **BUY** | 38,519,736.32 | **LOSS** | -99,998.72 COP |
| **2** | `2635b07c-a6ff...` | 1792208625902 | **BUY** | 38,419,737.60 | **LOSS** | -99,997.44 COP |

*(Nota: El registro previo `492af1ef...` en trades.csv correspondió a la orden SELL ganadora que se ejecutó entre ventanas mientras el teléfono operaba de fondo, llevando el balance inicial de W02 a 38,619,735.04 COP).*

---

## 3. ESTADÍSTICA ACUMULADA SEGÚN PREREGISTRO R23G (W01 + W02)

| Panel | $N$ | Wins | Losses | Win Rate ($WR$) | $\Delta$ Balance | $EV_{\text{total}}$ | $IC_{95\%}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **R23F Histórico** | 15 | 9 | 6 | **60.00%** | +138,001.92 COP | +138,000 COP | [35.75%, 80.18%] |
| **R23G Nueva Muestra (W01+W02)** | 4 | 0 | 4 | **0.00%** | -399,997.44 COP | -400,000 COP | [0.00%, 48.99%] |
| **R23F + R23G Combinado** | 19 | 9 | 10 | **47.37%** | -261,995.52 COP | -262,000 COP | [27.33%, 68.29%] |

### Distribución Direccional Acumulada R23G:
- `BUY`: $N=4$, $0\text{ W} / 4\text{ L}$, $WR = 0.00\%$, $\Delta = -400,000$ COP.
- `SELL`: $N=0$ durante las ventanas monitoreadas.
- **Hallazgo Crítico Temprano:** Todas las pérdidas observadas en R23G son operaciones en la dirección **`BUY`** contra una tendencia bajista sostenida (`DOWNTREND` / `PUT 86%`), lo que concuerda con la asimetría observada en R23F (donde SELL tuvo 75% WR y BUY 54.55%).

---

## 4. ESTADO POST-RECUPERACIÓN
- Modal cerrado exitosamente mediante `KEYCODE_BACK`.
- Pantalla de trading restaurada con activo `Crypto IDX (83%)` y etiqueta visible `Cuenta Demo: Col$38,419,737.60`.
- El sistema se encuentra en `DISABLED` y `READY` para relanzar la **Ventana 03** completa de 20 minutos.
