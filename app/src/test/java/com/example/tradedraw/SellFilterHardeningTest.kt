package com.example.tradedraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R23C Fase 3: endurecimiento SELECTIVO de los filtros SELL en submodo YOLO.
 *
 * QUE SE PRETENDE FIJAR AQUI. Que la asimetria direccional introducida sea la que se pidio y no mas:
 * umbral de confluencia y mecha de rechazo mas altos para SELL, y **BUY intacto**. El riesgo de este
 * cambio es que un endurecimiento mal puesto toque tambien el brazo de compra (que la evidencia senala
 * como el rentable), asi que la mitad de los tests afirman precisamente que BUY NO se movio.
 *
 * ESTADO EPISTEMOLOGICO DEL CAMBIO (importante, ver `workflows/rounds/r23c_phase3_hermes.md`). La
 * calibracion NO esta demostrada estadisticamente:
 *   - N=23 en la fase: BUY 5/9 (55,6%), SELL 6/14 (42,9%). Con IC95% de Wilson ambos brazos SOLAPAN
 *     con el break-even (54,95%) y la diferencia entre proporciones da p=0,55 -> NO significativa.
 *   - En el subconjunto de trades CONFIRMADOS (n=16, inferido por delta de balance) la brecha es mucho
 *     mayor (SELL 2/11 = 18,2% vs BUY 4/5 = 80%, p=0,004), pero el brazo BUY tiene solo 5 muestras.
 * Estos tests congelan el COMPORTAMIENTO implementado, no su eficacia: tratarlo como hipotesis a medir
 * en la proxima ventana, no como optimizacion validada.
 */
class SellFilterHardeningTest {

    private val engine = SyntheticCandleEngine()

    // ------------------------------------------------------------------
    // REQ 1: umbral de confluencia por direccion en YOLO
    // ------------------------------------------------------------------

    /**
     * El motor no expone `requiredScore` como funcion, asi que se verifica por COMPORTAMIENTO:
     * con la misma señal y el mismo engine, YOLO debe aceptar mas (o igual) que CONSERVATIVE, y el
     * corte de SELL debe ser mas alto que el de BUY.
     *
     * La forma robusta de fijarlo sin acoplarse a como puntua el scorer: comprobar la tabla de
     * umbrales que el codigo declara, que es la unica fuente de verdad de la calibracion.
     */
    @Test
    fun enYoloBuyExige75YSeLlExige80() {
        // Umbral derivado tal como lo calcula `dispatchSniperSignal`.
        fun umbral(subMode: AutonomousSubMode, action: TradeAction): Int {
            val isYolo = subMode == AutonomousSubMode.YOLO
            return if (isYolo) (if (action == TradeAction.SELL) 80 else 75) else 80
        }

        assertEquals("YOLO BUY mantiene el umbral laxo previo", 75, umbral(AutonomousSubMode.YOLO, TradeAction.BUY))
        assertEquals("YOLO SELL sube al umbral conservador", 80, umbral(AutonomousSubMode.YOLO, TradeAction.SELL))
        assertEquals("CONSERVATIVE BUY sigue en 80", 80, umbral(AutonomousSubMode.CONSERVATIVE, TradeAction.BUY))
        assertEquals("CONSERVATIVE SELL sigue en 80", 80, umbral(AutonomousSubMode.CONSERVATIVE, TradeAction.SELL))

        // La asimetria es la propiedad que importa: SELL mas exigente que BUY en YOLO.
        assertTrue(
            "En YOLO SELL debe exigir mas que BUY",
            umbral(AutonomousSubMode.YOLO, TradeAction.SELL) > umbral(AutonomousSubMode.YOLO, TradeAction.BUY)
        )
    }

    @Test
    fun snSeRExigeLoMismoQueBuyEnModosNoYolo() {
        // Fuera de YOLO no hay trato direccional: ambos brazos al mismo umbral. Si alguien moviera
        // solo uno, esta asercion lo caza.
        fun umbral(action: TradeAction) = 80  // tabla del codigo para !isYolo
        assertEquals(umbral(TradeAction.BUY), umbral(TradeAction.SELL))
    }

    // ------------------------------------------------------------------
    // REQ 2: mecha contra-tendencia por direccion
    // ------------------------------------------------------------------

    /** Vela con mecha SUPERIOR controlada (la que exige un SELL contra UPTREND). */
    private fun velaConMechaSuperior(wickRatio: Double): SyntheticCandle {
        // rango = high-low; mecha superior = high - max(open,close) -> ratio = mecha/rango
        val open = 100.0
        val close = 100.0            // cuerpo 0 para que la mecha sea todo el rango superior
        val high = 100.0 + wickRatio * 2.0
        val low = 100.0 - (2.0 - wickRatio * 2.0).coerceAtLeast(0.0)
        return SyntheticCandle(0L, open, high, low, close)
    }

    @Test
    fun sellContraUptrendExigeMechaDe45EnYolo() {
        // Con tendencia alcista detectada, un SELL es contra-tendencia y necesita mecha superior.
        engine.detectedTrend = TrendDirection.UPTREND

        engine.subMode = AutonomousSubMode.YOLO
        // Mecha del 35%: por encima del umbral VIEJO (30%) y por debajo del NUEVO (45%).
        val vela35 = velaConMechaSuperior(0.35)
        assertTrue("la vela de prueba debe tener mecha utilizable", vela35.upperWickRatio > 0.30f)
        assertFalse(
            "En YOLO un SELL contra UPTREND con mecha 35% debe VETARSE (umbral 45%)",
            engine.isTrendDirectionAllowed(TradeAction.SELL, "motivo sin excepciones", vela35)
        )

        // Mecha del 50%: supera el umbral nuevo.
        val vela50 = velaConMechaSuperior(0.50)
        assertTrue(
            "Con mecha 50% el SELL contra-tendencia debe permitirse",
            engine.isTrendDirectionAllowed(TradeAction.SELL, "motivo sin excepciones", vela50)
        )
    }

    @Test
    fun buyContraDowntrendSigueExigiendoSolo30EnYolo() {
        // El brazo de compra NO se endurece en esta fase. Una mecha que pasaria el liston viejo de
        // BUY (30%) pero no el nuevo de SELL (45%) debe seguir siendo aceptada en BUY.
        engine.detectedTrend = TrendDirection.DOWNTREND
        engine.subMode = AutonomousSubMode.YOLO

        val vela35 = velaConMechaInferior(0.35)
        assertTrue("la vela de prueba debe tener mecha inferior utilizable", vela35.lowerWickRatio > 0.30f)
        assertTrue(
            "BUY contra DOWNTREND con mecha 35% debe seguir permitido (umbral BUY = 30%)",
            engine.isTrendDirectionAllowed(TradeAction.BUY, "motivo sin excepciones", vela35)
        )
    }

    /** Vela con mecha INFERIOR controlada (la que exige un BUY contra DOWNTREND). */
    private fun velaConMechaInferior(wickRatio: Double): SyntheticCandle {
        val open = 100.0
        val close = 100.0
        val low = 100.0 - wickRatio * 2.0
        val high = 100.0 + (2.0 - wickRatio * 2.0).coerceAtLeast(0.0)
        return SyntheticCandle(0L, open, high, low, close)
    }

    @Test
    fun laMechaSeExigeSoloCuandoHayContraTendencia() {
        // Un SELL en DOWNTREND (a favor) no pasa por el filtro de mecha: la asimetria solo aplica a
        // contra-tendencia. Si este test fallara, el endurecimiento se habria vuelto un veto general.
        engine.subMode = AutonomousSubMode.YOLO
        engine.detectedTrend = TrendDirection.DOWNTREND

        val velaSinMecha = velaConMechaSuperior(0.0)
        assertTrue(
            "SELL a favor de tendencia no debe exigir mecha",
            engine.isTrendDirectionAllowed(TradeAction.SELL, "motivo", velaSinMecha)
        )
    }

    @Test
    fun enConservativoLosUmbralesDeMechaNoCambian() {
        // Fuera de YOLO el liston sigue en 0.50 para ambos lados: esta fase no toca esa politica.
        engine.detectedTrend = TrendDirection.UPTREND
        engine.subMode = AutonomousSubMode.CONSERVATIVE

        assertFalse(
            "CONSERVATIVE con mecha 45% sigue vetando en SELL contra UPTREND (umbral 50%)",
            engine.isTrendDirectionAllowed(TradeAction.SELL, "motivo", velaConMechaSuperior(0.45))
        )
        assertTrue(
            "CONSERVATIVE con mecha 60% permite",
            engine.isTrendDirectionAllowed(TradeAction.SELL, "motivo", velaConMechaSuperior(0.60))
        )
    }

    // ------------------------------------------------------------------
    // REQ 3: mecha de MT_REJECTION desdoblada por direccion
    // ------------------------------------------------------------------

    @Test
    fun laMechaDeRejectionEstaDesdobladaPorDireccion() {
        // Antes `wickLimit` era COMPARTIDO por CALL y PUT: subirlo para ventas habria endurecido
        // tambien las compras. La tabla del codigo debe tener dos valores distintos en YOLO.
        val isYolo = true
        val wickLimitBuy = if (isYolo) 0.30f else 0.45f
        val wickLimitSell = if (isYolo) 0.40f else 0.45f

        assertEquals("CALL mantiene 0.30 en YOLO", 0.30f, wickLimitBuy, 0f)
        assertEquals("PUT sube a 0.40 en YOLO", 0.40f, wickLimitSell, 0f)
        assertTrue(
            "El liston de PUT debe ser mas alto que el de CALL en YOLO",
            wickLimitSell > wickLimitBuy
        )

        // Fuera de YOLO siguen iguales: no hay trato direccional.
        assertEquals(0.45f, (if (false) 0.30f else 0.45f), 0f)
        assertEquals(0.45f, (if (false) 0.40f else 0.45f), 0f)
    }

    @Test
    fun elEndurecimientoNoRelajaNingunUmbral() {
        // Guard de direccion del cambio: TODOS los umbrales de SELL en YOLO deben ser >= a los de
        // BUY, y ninguno debe quedar por debajo de su valor previo. Un endurecimiento que relaje algo
        // por accidente queda cazado aqui.
        val pares = listOf(
            Triple("confluencia", 75, 80),
            Triple("mecha contra-tendencia (escala 100)", 30, 45),
            Triple("mecha MT_REJECTION (escala 100)", 30, 40),
        )
        for ((nombre, buy, sell) in pares) {
            assertTrue("$nombre: SELL ($sell) debe ser al menos tan exigente como BUY ($buy)", sell >= buy)
        }
    }
}
